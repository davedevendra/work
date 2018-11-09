/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.enterprise;

import com.oracle.iot.client.DeviceModelAttribute;
import com.oracle.iot.client.DeviceModelFormat;
import com.oracle.iot.client.HttpResponse;
import com.oracle.iot.client.RestApi;
import com.oracle.iot.client.impl.DeviceModelImpl;
import com.oracle.iot.client.impl.StorageConnectionBase;
import com.oracle.iot.client.impl.TimeManager;
import com.oracle.iot.client.impl.VirtualDeviceBase;
import com.oracle.iot.client.impl.http.HttpSecureConnection;
import com.oracle.iot.client.message.StatusCode;
import oracle.iot.client.AbstractVirtualDevice;
import oracle.iot.client.enterprise.UserAuthenticationException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitor monitors data messages for started devices and processes
 * messages by calling the virtualDevice notify handlers.
 */
class Monitor implements Runnable {

    interface NotifyHandler {
        void notifyOnChange(VirtualDeviceImpl virtualDevice,
                            VirtualDeviceAttributeImpl<?>[] fields);
        void notifyOnAlert(VirtualDeviceImpl virtualDevice,
                           String format,
                           long eventTime,
                           AbstractVirtualDevice.NamedValue<?> namedValues);
        void notifyOnData(VirtualDeviceImpl virtualDevice,
                           String format,
                           long eventTime,
                           AbstractVirtualDevice.NamedValue<?> namedValues);
    }

    static class MonitoredDevice {
        final WeakReference<VirtualDeviceImpl> deviceRef;
        final NotifyHandler handler;
        MonitoredDevice(VirtualDeviceImpl device, NotifyHandler handler) {
            this.deviceRef = new WeakReference<VirtualDeviceImpl>(device);
            this.handler = handler;
        }
    }

    // %1$s - app id, %2$s- device id, %3$s - model urn
// not used
//    private static final String ATTRIBUTES_RESOURCE_FORMAT =
//        "/iot/api/v2/apps/%1$s/devices/%2$s/deviceModels/%3$s/attributes";

    // %1$s - app id, %2$s- device id, %3$s - model urn
    private static final String BULKDATA_RESOURCE_FORMAT =
        RestApi.V2.getReqRoot()+"/apps/%1$s/devices/data?formatLimit=%2$d";

    private static final Object MONITOR_LOCK = new Object();

    private static Monitor monitor;
    private volatile boolean running;
    private AtomicLong lastUntil = new AtomicLong();
    // Devices can have more than one model per endpoint. Add 
    // device to the list as a MonitoredDevice, which associates
    // a device and a handler.
    private final List<MonitoredDevice> monitoredDeviceList;

    // Map devices by appid.
    private final Map<String, List<MonitoredDevice>> appDeviceMap;

    private static final String MONITOR_POLLING_INTERVAL =
            "oracle.iot.client.enterprise.monitor_polling_interval";

    // Delay polling for messages in milliseconds, default 3 seconds
    private final int pollingInterval;

    private static final String MONITOR_MAX_FORMATS =
            "oracle.iot.client.enterprise.monitor_max_formats";

    // The maximum number of alerts to return in a bulk data request.
    private final int maxFormats;

    private Monitor() {
        this.appDeviceMap = new HashMap<String, List<MonitoredDevice>>();
        this.monitoredDeviceList = new ArrayList<MonitoredDevice>();
        // this is deliberate to avoid autoboxing
        Integer val = Integer.getInteger(MONITOR_POLLING_INTERVAL);
        this.pollingInterval = (val != null && val.intValue() > 0 ?
                val.intValue() : 3000);
        val = Integer.getInteger(MONITOR_MAX_FORMATS);
        this.maxFormats = (val != null && val.intValue() > 0 ?
                val.intValue() : 10);
    }

    /**
     * Register a {@code NotifyHandler} with the monitor that is called
     * if there are updates to the device fields.
     * @param virtualDevice the device to monitor
     * @param handler the {@code NotifyHandler} must not be null
     * @throws IllegalArgumentException if virtualDevice or handler are null.
     * @throws IOException if attributes cannot be retrieved from the cloud 
     * service
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid
     */
    public static void startMonitor(VirtualDeviceImpl virtualDevice, 
            NotifyHandler handler)
            throws IOException, GeneralSecurityException {
        if (virtualDevice == null) {
            throw new IllegalArgumentException(
                "The virtualDevice argument cannot be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException(
                "The handler argument cannot be null");
        }

        synchronized(MONITOR_LOCK) {
            if (monitor == null) {
                monitor = new Monitor();
                monitor.running = true;

                Thread monitorThread = new Thread(monitor);
                // Allow the VM to exit if this thread is still running
                monitorThread.setDaemon(true);
                monitorThread.start();
            }
        }

        final String appid = virtualDevice.getEnterpriseClient()
            .getApplication().getId();
        try {
            // Get all the last known values for the device before putting
            // it in the polling queue.
            getLogger().log(Level.FINEST, "Getting last known values for device " +
                virtualDevice.getEndpointId() + " model " +
                virtualDevice.getDeviceModel().getURN());

            monitor.processInitialBulkData(appid, virtualDevice, handler);

        } catch (IOException ioe) {
            getLogger().log(Level.SEVERE, "Cannot get attributes for virtual " +
                "device " + virtualDevice.getEndpointId() + " model " +
                virtualDevice.getDeviceModel().getURN());
            throw ioe;
        } catch (GeneralSecurityException gse) {
            getLogger().log(Level.SEVERE, "Cannot get attributes for virtual " +
                "device " + virtualDevice.getEndpointId() + " model " +
                virtualDevice.getDeviceModel().getURN());
            throw gse;
        }
        
        getLogger().log(Level.FINEST, "Starting to monitor virtualDevice " +
            virtualDevice.getDeviceModel().getURN() +
            " " + virtualDevice.getEndpointId());


        synchronized(MONITOR_LOCK) {
            List<MonitoredDevice> appdevices =
                monitor.appDeviceMap.get(appid);
            if (appdevices == null) {
                appdevices = new ArrayList<MonitoredDevice>();
                monitor.appDeviceMap.put(appid, appdevices);
            }

            MonitoredDevice monitoredDevice =
                    new MonitoredDevice(virtualDevice, handler);

            appdevices.add(monitoredDevice);
            monitor.monitoredDeviceList.add(monitoredDevice);
        }
    }

    /**
     * Stop servicing the devices of a given enterprise client. If this
     * is the last client then stop the monitor.
     *
     * @param appId
     */
    private static void ecClosed(String appId) {
        synchronized(MONITOR_LOCK) {
            if (monitor == null) {
                return;
            }

            List<MonitoredDevice> appdevices =
                monitor.appDeviceMap.get(appId);
            if (appdevices == null) {
                return;
            }

            for (MonitoredDevice device: appdevices) {
                monitor.monitoredDeviceList.remove(device);
            }

            monitor.appDeviceMap.remove(appId);

            if (monitor.appDeviceMap.isEmpty()) {
                stop();
            }
        }
    }

    public static void stop() {
        synchronized(MONITOR_LOCK) {
            if (monitor != null) {
                getLogger().log(Level.FINEST, "Stopping Monitor");
                monitor.running = false;
                monitor = null;
            }
        }
    }

    /**
     * Create the bulk data request payload.
     * The json format is
     * <pre>
     * {
     *   "ep0" : [ "ep0_model0", ... , "ep0_modelN" ], ... ,
     *   "epN" : [ "epN_model0", ... , "epN_modelM" ]
     * }
     * </pre>
     * @return the payload as a byte array or null in case of an exception.
     */
    private byte[] bulkDataPayload(Object[] deviceList) {
        Map<String, JSONArray> jsonModels = 
            new HashMap<String, JSONArray>();
        for (Object device : deviceList) {
            final VirtualDeviceImpl virtualDevice = (VirtualDeviceImpl)device;
            final String endpointId = virtualDevice.getEndpointId();
            // There may be more than one device with the same 
            // endpoint but different model. Check the json payload object
            // to see if the endpoint has already been added. If it
            // has, add this device model to the same endpoint model array.
            final String modelUrn = virtualDevice.getDeviceModel().getURN();

            JSONArray models = jsonModels.get(endpointId);
            if (models == null) {
                models = new JSONArray().put(modelUrn);
                jsonModels.put(endpointId, models);
            } else {
                models.put(modelUrn);
            }
        }

        try {
            final JSONObject payload = new JSONObject();
            Set<Map.Entry<String,JSONArray>> endpoints =
                jsonModels.entrySet();
            for (Map.Entry<String,JSONArray> entry : endpoints) {
                payload.put(entry.getKey(), entry.getValue());
            }
            return payload.toString().getBytes("UTF-8");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Exception converting json to bytes.", e);
            return null;
        }
    }

    /**
     * The polling message loop.
     * For each device make one request to get messages.
     * This will return at most 10 messages.
     * Messages are received in descending order.
     */
    public void run() {
        getLogger().log(Level.FINEST, "Starting Monitor");
        while(running) {

            Map<String,VirtualDeviceImpl[]> monitorMap =
                    new HashMap<String, VirtualDeviceImpl[]>(appDeviceMap.size());

            synchronized(MONITOR_LOCK) {

                Set<Map.Entry<String,List<MonitoredDevice>>> entrySet = appDeviceMap.entrySet();
                for (Map.Entry<String,List<MonitoredDevice>> entry : entrySet) {

                    final String appid = entry.getKey();
                    final List<MonitoredDevice> monitoredDevices = entry.getValue();

                    if (monitoredDevices == null || monitoredDevices.isEmpty()) {
                        continue;
                    }

                    final List<VirtualDeviceImpl> virtualDevices =
                            new ArrayList<VirtualDeviceImpl>(monitoredDevices.size());

                    Iterator<MonitoredDevice> iterator = monitoredDevices.iterator();
                    while (iterator.hasNext()) {
                        MonitoredDevice md = iterator.next();
                        WeakReference<VirtualDeviceImpl> ref = md.deviceRef;
                        VirtualDeviceImpl vd = ref.get();
                        if (vd == null) {
                            iterator.remove();
                            continue;
                        }
                        virtualDevices.add(vd);
                    }

                    if (!virtualDevices.isEmpty()) {
                        monitorMap.put(
                                appid,
                                virtualDevices.toArray(new VirtualDeviceImpl[virtualDevices.size()])
                        );
                    }
                }
            }

            // This loop is outside of MONITOR_LOCK!
            Set<Map.Entry<String,VirtualDeviceImpl[]>> entrySet = monitorMap.entrySet();
            for (Map.Entry<String,VirtualDeviceImpl[]> entry : entrySet) {

                String appid = entry.getKey();
                VirtualDeviceImpl[] devices = entry.getValue();
                VirtualDeviceImpl virtualDevice = devices[0];

                // Get the necessary information from the first device in the
                // list. It will be "good" for all devices in this list.
                final HttpSecureConnection secureConnection =
                        virtualDevice.getSecureConnection();
                if (secureConnection.isClosed()) {
                    ecClosed(appid);
                    continue;
                }

                final byte[] payload = bulkDataPayload(devices);
                if (payload != null) {
                    String resource = String.format(BULKDATA_RESOURCE_FORMAT,
                                                    appid, maxFormats);

                    final long since = lastUntil.get();
                    if (since != 0) {
                        // Need '&' because always sending '?formatLimit'
                        resource = resource.concat("&since=")
                            .concat(Long.toString(since));
                    }
                    try {
                        final JSONObject jsonAttributes = request(
                            secureConnection, "POST", resource,
                            payload);
                        // If jsonAttributes is null the request most
                        // likely failed and logged a message.
                        if (jsonAttributes != null) {
                            processBulkData(jsonAttributes);
                        }
                    }catch (UserAuthenticationException ue){
                        getLogger().log(Level.FINEST,
                            "Session Expired, User Authentication Exception thrown", ue);
                        entrySet.remove(entry);
                        throw new RuntimeException(ue);
                    } catch (Exception e) {
                        // exception may be because EC closed
                        if (secureConnection.isClosed()) {
                            ecClosed(appid);
                        } else {
                            getLogger().log(Level.WARNING,
                                    "Exception processing bulk json data", e);
                        }
                    }
                }

                try {
                    Thread.sleep(pollingInterval);
                } catch (InterruptedException e) {
                    getLogger().log(Level.SEVERE,
                        "Stopping monitor due to exception.", e);
                    running = false;
                    Thread.currentThread().interrupt();
                }
            }
        }
        getLogger().log(Level.FINEST, "Monitor ending");
    }

    /**
     * Get the device attributes that have been changed since the timestamp
     * {@code since}.
     * @return the device attributes as a JSONObject.
     */
// not used
//    private JSONObject getDeviceAttributes(VirtualDeviceImpl virtualDevice)
//            throws IOException, GeneralSecurityException {
//
//        final SecureHttpConnection secureConnection =
//                virtualDevice.getSecureConnection();
//        final String endpointId = virtualDevice.getEndpointId();
//        final String modelUrn = virtualDevice.getDeviceModel().getURN();
//        final String appid = virtualDevice.getEnterpriseClient()
//                                          .getApplication().getId();
//        final String resource = String.format(ATTRIBUTES_RESOURCE_FORMAT,
//                                              appid, endpointId, modelUrn);
//
//        return request(secureConnection, "GET", resource, null);
//    }

    /**
     * Invoke the {@code virtualDevice} notify method for each of the
     * attributes in {@code jsonAttributes}.
     */
// not used
//    private void processAttributes(VirtualDeviceImpl virtualDevice,
//                                   NotifyHandler handler,
//                                   JSONObject jsonAttributes) {
//        // deviceAttributesFromJson may return null!
//        final VirtualDeviceAttributeImpl[] attributes =
//            deviceAttributesFromJson(virtualDevice, jsonAttributes);
//        if (attributes != null && attributes.length > 0) {
//            executor.execute(new OnChangeNotifier(handler, virtualDevice, attributes));
//        }
//    }

    /**
     * Convert the json response into VirtualDeviceAttributes and notify
     * the respective devices of the attribute changes. The bulk json data
     * has the following format.
     * <pre>
     *   {
     *       "data":{
     *           "0-AI":{
     *               "urn:com:oracle:iot:device:thermometer":{
     *                   "attributes":{
     *                       "scale":1.0,
     *                       "temperature":20.935437036685517
     *                   }
     *                   "formats": {
     *                       "format1" : [{
     *                           "eventTime": 1234,
     *                            "severity": "HIGH",
     *                            "fields": {
     *                                 "name1": "value1",
     *                                 "name2": "value2"
     *                            },...,
     *                            {...}
     *                       ]
     *                   }
     *               }, .... ,
     *               "urn:com:oracle:iot:device:YY":{
     *                   "attributes":{
     *                       "M": W,
     *                       "N": Z
     *                   }
     *               }
     *           }, ... ,
     *           "0-NN":{
     *               "urn:com:oracle:iot:device:XX":{
     *                   "attributes":{
     *                       "X":J,
     *                       "Y":K
     *                   }
     *                   "formats": {...}
     *               }
     *           }
     *       }
     *   }
     * </pre>
     * @param jsonData the json formatted bulk data
     */
    // Iterate over the list of known devices and pull data from
    // the response.
    private void processBulkData(JSONObject jsonData) {
        Object until = jsonData.opt("until");
        if (until == null) {
            getLogger().log(Level.FINEST, "Monitor: until data in response");
        } else {
            lastUntil.set(((Number)until).longValue());
        }

        JSONObject jsonDevices = jsonData.optJSONObject("data");
        if (jsonDevices == null) {
            getLogger().log(Level.FINEST, "Monitor: no bulk data in response");
            return;
        }

        // Create separate list of MonitoredDevices so we don't hold
        // MONITOR_LOCK while processing model data.
        List<MonitoredDevice> monitoredDevices =
                new ArrayList<MonitoredDevice>(monitoredDeviceList.size());

        synchronized(MONITOR_LOCK) {

            Iterator<MonitoredDevice> iterator = monitoredDeviceList.iterator();
            while (iterator.hasNext()) {
                MonitoredDevice md = iterator.next();

                WeakReference<VirtualDeviceImpl> ref = md.deviceRef;
                VirtualDeviceImpl vd = ref.get();
                if (vd == null) {
                    iterator.remove();
                    continue;
                }

                monitoredDevices.add(md);
            }
        }

        for (MonitoredDevice md : monitoredDevices) {

            // it is possible the VirtualDeviceImpl has been GC'd
            WeakReference<VirtualDeviceImpl> ref = md.deviceRef;
            VirtualDeviceImpl vd = ref.get();
            if (vd == null) {
                continue;
            }

            NotifyHandler handler = md.handler;
            if (handler == null) {
                // do nothing, no handler for this device.
                continue;
            }

            String endpoint = vd.getEndpointId();
            JSONObject jsonDevice = jsonDevices.optJSONObject(endpoint);
            if (jsonDevice == null) {
                // no data for this device
                continue;
            }
            String model = vd.getDeviceModel().getURN();
            JSONObject jsonModel = jsonDevice.optJSONObject(model);
            if (jsonModel != null) {
                processModelData(vd, jsonModel, handler);
            } else {
                getLogger().log(Level.WARNING, "Monitor: virtual device " + endpoint +
                            " does not implement model " + model);
            }
        }

    }

    // TODO: Remove when each alert is dispatched when discovered
    static final class CustomMessage {
        final String formatUrn;
        final DeviceModelFormat.Type type;
        final long eventTime;
        final AbstractVirtualDevice.NamedValue<?> namedValues;

        CustomMessage(String formatUrn, DeviceModelFormat.Type type,
            long eventTime, AbstractVirtualDevice.NamedValue<?> namedValues) {

            this.formatUrn = formatUrn;
            this.type = type;
            this.eventTime = eventTime;
            this.namedValues = namedValues;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("format: ").append(formatUrn).append("\n")
              .append("type: ").append(type).append("\n")
              .append("eventTime: ").append(eventTime).append("\n")
              .append("fields:\n");
            for (AbstractVirtualDevice.NamedValue<?> namedValue = namedValues;
                 namedValue != null;
                 namedValue = namedValue.next()) {
                sb.append("  ").append(namedValue.getName()).append(" : ")
                  .append(String.valueOf(namedValue.getValue())).append("\n");
            }
            return sb.toString();
        }
    }

// not used
//    private void dumpCustomMessages(CustomMessage[] messages) {
//        System.out.println("\n####  Dumping Custom Messages ####\n");
//        for (CustomMessage cm : messages) {
//            System.out.println(cm.toString());
//        }
//        System.out.println("\n####  DONE Custom Messages ####\n");
//    }

    /**
     * Arrays of custom messages are grouped by format:
     *
     * "formats": {
     *    "format1" : [{    
     *        "eventTime": 1234,
     *        "severity": "HIGH",
     *        "fields": {
     *            "name1": "value1",
     *            "name2": "value2"
     *        }},...,
     *        {
     *        "eventTime": 1234,
     *        "severity": "HIGH",
     *        "fields": {
     *            "name1": "value1",
     *            "name2": "value2"
     *        }}
     *        ]
     *    }, ...,
     *    "formatN" : {    
     *        "eventTime": 1234,
     *        "severity": "HIGH",
     *        "fields": {
     *            "name1": "value1",
     *            "name2": "value2"
     *        }
     *
     * }
     */
    private CustomMessage[] customMessagesFromJson(VirtualDeviceImpl vd,
                                                   JSONObject jsonModelFormats) {

        if (jsonModelFormats == null) {
            return null;
        }

        ArrayList<CustomMessage> customMessages = new ArrayList<CustomMessage>();
        Map<String, DeviceModelFormat> deviceFormats =
            ((DeviceModelImpl)vd.getDeviceModel()).getDeviceModelFormats();
        for (DeviceModelFormat dmf : deviceFormats.values()) {
            JSONArray jsonAlerts =
                jsonModelFormats.optJSONArray(dmf.getURN());
            if (jsonAlerts == null) {
                continue;
            }

            for (int i = 0, size = jsonAlerts.length(); i < size; ++i) {
                JSONObject jsonAlert = jsonAlerts.optJSONObject(i);
                Number jsonNumber = (Number)jsonAlert.opt("eventTime");
                long eventTime = jsonNumber.longValue();
                // Can fields be null or empty ?
                JSONObject jsonFields = jsonAlert.optJSONObject("fields");
                List<DeviceModelFormat.Field> dmffields = dmf.getFields();
                AbstractVirtualDevice.NamedValue<?> fields = null;
                VirtualDeviceBase.NamedValueImpl<Object> last = null;
                for (DeviceModelFormat.Field dmff : dmffields) {
                    String fieldName = dmff.getName();
                    if (!jsonFields.has(fieldName)) {
                        continue;
                    }

                    Object value = jsonValueToDMAType(fieldName, jsonFields, 
                        dmff.getType(), vd);
                    VirtualDeviceBase.NamedValueImpl<Object> field =
                        new VirtualDeviceBase.NamedValueImpl<Object>(
                        fieldName,value);
                    if (last != null) {
                        last.setNext(field);
                        last = field;
                    } else {
                        fields = last = field;
                    }
                }
                customMessages.add(new CustomMessage(dmf.getURN(),
                    dmf.getType(), eventTime, fields));
            }
        }
        return customMessages.toArray(
            new CustomMessage[customMessages.size()]);
    }

    private final Executor executor =
            Executors.newCachedThreadPool(new NotifierThreadFactory());

    static class NotifierThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        NotifierThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
            namePrefix = "notifier-" +
                    poolNumber.getAndIncrement() +
                    "-thread-";
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                    namePrefix + threadNumber.getAndIncrement(),
                    0);
            // this is opposite of what the Executors.DefaultThreadFactory does
            if (!t.isDaemon())
                t.setDaemon(true);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
    private static class OnChangeNotifier implements Runnable {
        private final NotifyHandler handler;
        private final VirtualDeviceImpl virtualDevice;
        private final VirtualDeviceAttributeImpl[] attributes;

        private OnChangeNotifier(NotifyHandler handler,
                                 VirtualDeviceImpl virtualDevice,
                                 VirtualDeviceAttributeImpl[] attributes) {
            this.handler = handler;
            this.virtualDevice = virtualDevice;
            this.attributes = attributes;
        }
        @Override
        public void run() {
            synchronized (handler) {
                handler.notifyOnChange(virtualDevice, attributes);
            }
        }
    }

    private static class OnAlertNotifier implements Runnable {
        private final NotifyHandler handler;
        private final VirtualDeviceImpl virtualDevice;
        private final String formatURN;
        private final long eventTime;
        private final AbstractVirtualDevice.NamedValue<?> namedValues;

        private OnAlertNotifier(NotifyHandler handler,
                                VirtualDeviceImpl virtualDevice,
                                String formatURN,
                                long eventTime,
                                AbstractVirtualDevice.NamedValue<?> namedValues) {
            this.handler = handler;
            this.virtualDevice = virtualDevice;
            this.formatURN = formatURN;
            this.eventTime = eventTime;
            this.namedValues = namedValues;
        }
        @Override
        public void run() {
            synchronized (handler) {
                handler.notifyOnAlert(virtualDevice, formatURN, eventTime, namedValues);
            }
        }
    }

    private static class OnDataNotifier implements Runnable {
        private final NotifyHandler handler;
        private final VirtualDeviceImpl virtualDevice;
        private final String formatURN;
        private final long eventTime;
        private final AbstractVirtualDevice.NamedValue<?> namedValues;

        private OnDataNotifier(NotifyHandler handler,
                VirtualDeviceImpl virtualDevice,
                String formatURN,
                long eventTime,
                AbstractVirtualDevice.NamedValue<?> namedValues) {
            this.handler = handler;
            this.virtualDevice = virtualDevice;
            this.formatURN = formatURN;
            this.eventTime = eventTime;
            this.namedValues = namedValues;
        }

        @Override
        public void run() {
            synchronized (handler) {
                handler.notifyOnData(virtualDevice, formatURN, eventTime,
                    namedValues);
            }
        }
    }

    // May return null!
    private VirtualDeviceAttributeImpl[] deviceAttributesFromJson(
            VirtualDeviceImpl virtualDevice, JSONObject jsonAttributes) {

        Iterator<String> keys =
                jsonAttributes != null ? jsonAttributes.keys() : null;

        if (keys == null || !keys.hasNext()) {
            return null;
        }

        final DeviceModelImpl dm =
            (DeviceModelImpl)virtualDevice.getDeviceModel();
        final ArrayList<VirtualDeviceAttributeImpl> attributes =
            new ArrayList<VirtualDeviceAttributeImpl>();
        while (keys.hasNext()) {
            String attribute = keys.next();
            DeviceModelAttribute dma =
                dm.getDeviceModelAttributes().get(attribute);
            if (dma == null) {
                getLogger().log(Level.WARNING, "Remote attribute " + attribute +
                               " is not an attribute of model " + dm.getURN());
                return null;
            }
            final VirtualDeviceAttributeImpl<?> vda =
                deviceAttributeFromJson(virtualDevice, dma, jsonAttributes);
            if (vda != null) {
                attributes.add(vda);
            } else {
                getLogger().log(Level.WARNING, "Failed to create VirtualDeviceAttribute " +
                               "for attribute " + attribute + " for model " +
                               dm.getURN());
            }
        }

        return attributes.toArray(
            new VirtualDeviceAttributeImpl[attributes.size()]);
    }

    /**
     * Convert json attribute representation to a 
     * {@link VirtualDeviceAttributeImpl} instance
     * @return a VirtualDeviceAttributeImpl instance or null if the
     * json cannot be converted.
     */
    private VirtualDeviceAttributeImpl<?> deviceAttributeFromJson(
            VirtualDeviceImpl virtualDevice, DeviceModelAttribute dma,
            JSONObject jsonAttributes) {
        if (dma == null)
            return null;
        try {
            Object value = jsonValueToDMAType(dma.getName(), jsonAttributes,
                dma.getType(), virtualDevice);
            return new VirtualDeviceAttributeImpl(virtualDevice, dma, value);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE,
                    "Cannot create VirtualDeviceAttribute " +
                    "from json attribute for field " + dma.getName() +
                    " in model " + virtualDevice.getDeviceModel().getURN(), e);
        }
        return null;
    }

    private Object jsonValueToDMAType(String fieldName, JSONObject jsonObjects, 
            DeviceModelAttribute.Type type, VirtualDeviceImpl virtualDevice) {
        Object value = null;
        try {
            switch (type) {
                case NUMBER:
                    value = Double.valueOf(jsonObjects.getDouble(fieldName));
                    break;

                case STRING:
                    value = jsonObjects.getString(fieldName);
                    break;

                case BOOLEAN:
                    value = Boolean.valueOf(jsonObjects.getBoolean(fieldName));
                    break;

                case INTEGER:
                    value = Integer.valueOf(jsonObjects.getInt(fieldName));
                    break;

                case DATETIME:
                    value = new Date(jsonObjects.getLong(fieldName));
                    break;
                    
                case URI:
                    String uri = jsonObjects.getString(fieldName);
                    if (StorageConnectionBase.isStorageCloudURI(uri)) {
                        try {
                            StorageObjectImpl storageObjectImpl =
                                    (StorageObjectImpl)virtualDevice.getEnterpriseClient().createStorageObject(uri);
                            storageObjectImpl.setSyncEventInfo(virtualDevice, fieldName);
                            value = storageObjectImpl;
                            break;
                        } catch (Exception e) {
                            //If SCS object in inaccessable, log it and create ExternalObject value instead.
                            getLogger().log(Level.WARNING, "Storage CS object access failed: " + e.getMessage());
                        }
                    } 
                    value = new oracle.iot.client.ExternalObject(uri);
                    break;
            }

            return value;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Cannot determine type from " +
                "from json attribute for field " + fieldName, e);
            return null;
        }
    }

    /**
     * Make an http request to the cloud service.
     * @param secureConnection the secure connection to the cloud
     *                         service
     * @param method the http method one of "GET" or "POST"
     * @param resource the REST resource including query parameters
     * @param payload the request payload, can be null.
     * @return the json formatted response 
     */
    private JSONObject request(HttpSecureConnection secureConnection,
            String method, String resource, byte[] payload)
            throws IOException, GeneralSecurityException {

        // Is this response paginated ?
        HttpResponse response;

        if ("GET".equals(method)) {
            response =
                secureConnection.get(resource);
        } else if ("POST".equals(method)) {
            response =
                secureConnection.post(resource, payload);
        } else {
            throw new IOException(method + " " + resource +
                ": method not supported.");
        }

        int status = response.getStatus();

        JSONObject jsonAttributes = null;
        if (status == StatusCode.OK.getCode()) {
            getLogger().log(Level.FINEST, "Monitor " + method + " " + resource +
                " received 'HTTP " + status + ", data.length = " +
                response.getData().length);
            final byte[] data = response.getData();

            // if data.length == 2, then it is an empty json array and
            // there are no values in the message.
            if (data != null && data.length > 2) {
                String json = new String(data, "UTF-8");
                try {
                    jsonAttributes = new JSONObject(json);
                } catch(JSONException je) {
                    if (getLogger().isLoggable(Level.WARNING)) {
                        getLogger().log(Level.WARNING,
                            "Monitor " + method  + " " + resource +
                            " could nor parse json response", je);
                    }
                    return null;
                }
            }
        } else if (status == StatusCode.NOT_FOUND.getCode()) {
            getLogger().info(response.getVerboseStatus(method,
                resource));
            throw new IOException(method + " " + resource +
                ": Endpoint cannot be virtualized");
        } else if (status == StatusCode.FORBIDDEN.getCode()) {
            getLogger().info(response.getVerboseStatus(method,
                resource));
            throw new IOException(method + " " + resource +
                ": Invalid virtual device");
        } else {
            throw new IOException(response.getVerboseStatus(method, resource));
        }

        return jsonAttributes;
    }


    private void processInitialBulkData(String appid,
            VirtualDeviceImpl virtualDevice, NotifyHandler handler)
            throws IOException, GeneralSecurityException {

        // Get the necessary information from the first device in the
        // list. It will be "good" for all devices in this list.
        final HttpSecureConnection secureConnection =
            virtualDevice.getSecureConnection();

        final byte[] payload = bulkDataPayload(new Object[] { virtualDevice });
        if (payload != null) {
            // Because there is no alert handler on the virtual device
            // at this time, do no request any alerts, formatLimit == 0
            String resource =
                String.format(BULKDATA_RESOURCE_FORMAT, appid, 0);

            long since = lastUntil.get();
            if (since == 0) {
                since = TimeManager.currentTimeMillis() - pollingInterval;
            }

            // Need '&' because always sending '?formatLimit'
            resource = resource.concat("&formatSince=")
                .concat(Long.toString(since));

            final JSONObject jsonBulkData = request(
                secureConnection, "POST", resource, payload);
            // If jsonAttributes is null the request most
            // likely failed and logged a message.
            if (jsonBulkData != null) {
                processInitialBulkData(virtualDevice, jsonBulkData, handler);
            } else {
                throw new IOException("POST " + resource + ": No data for " +
                    virtualDevice.getEndpointId() + " model " +
                    virtualDevice.getDeviceModel().getURN());
            }
        }
    }

    private void processModelData(VirtualDeviceImpl vd,
            JSONObject jsonModel, NotifyHandler handler) {
        JSONObject jsonAttributes =
           jsonModel.optJSONObject("attributes");
        // deviceAttributesFromJson may return null!
        VirtualDeviceAttributeImpl[] attributes =
           deviceAttributesFromJson(vd, jsonAttributes);
        // TODO: notifier should only be called once for all attributes.
        if (attributes != null && attributes.length > 0) {
            executor.execute(new OnChangeNotifier(handler, vd, attributes));
        }

        JSONObject jsonCustomMessages =
           jsonModel.optJSONObject("formats");
        // customMessagesFromJson may return null!

        // TODO: Since we are only going to dispatch one alert at a
        // time, then the executor should be called within
        // modelformatsFromJson and the name of the method should be
        // changed.
        CustomMessage[] customMessages = customMessagesFromJson(vd,
                jsonCustomMessages);
        //if (customMessages != null) {
        //    dumpCustomMessages(customMessages);
        //}
        if (customMessages != null) {
            for (CustomMessage cm : customMessages) {
                if (cm.type == DeviceModelFormat.Type.ALERT) {
                    executor.execute(
                        new OnAlertNotifier(
                                handler,
                                vd,
                                cm.formatUrn,
                                cm.eventTime,
                                cm.namedValues));
                } else {
                    executor.execute(
                        new OnDataNotifier(
                                handler,
                                vd,
                                cm.formatUrn,
                                cm.eventTime,
                                cm.namedValues));
                }
            }
        }
    }

    private void processInitialBulkData(VirtualDeviceImpl vd,
                                        JSONObject jsonData,
                                        NotifyHandler handler) {

        JSONObject jsonDevices;
        try {
            jsonDevices = jsonData.getJSONObject("data");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        JSONObject jsonDevice = jsonDevices.optJSONObject(vd.getEndpointId());

        if (lastUntil.get() == 0) {
            Number until = (Number)jsonData.opt("until");
            if (until != null) {
                lastUntil.set(until.longValue());
            }
        }

        if (jsonDevice == null) {
            // If there is no data and the device has attributes, then
            // the device is not ready
            if (!(((DeviceModelImpl)vd.getDeviceModel())
                  .getDeviceModelAttributes().isEmpty())) {
                throw new IllegalArgumentException(
                    vd.getEndpointId() + " is not ready for virtualization");
            }
            // Otherwise, jsonDevice is null because the device has
            // no attributes
            return;
        }

        String model = vd.getDeviceModel().getURN();
        JSONObject jsonModel = jsonDevice.optJSONObject(model);
        if (jsonModel != null) {
            processModelData(vd, jsonModel, handler);
        } else {
            throw new IllegalArgumentException(
                vd.getEndpointId() + " does not implement " + model);
        }
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
