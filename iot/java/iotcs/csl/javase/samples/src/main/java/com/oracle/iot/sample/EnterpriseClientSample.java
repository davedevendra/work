/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.sample;

import oracle.iot.client.AbstractVirtualDevice;
import oracle.iot.client.DeviceModel;

import oracle.iot.client.StorageObject;
import oracle.iot.client.enterprise.Device;
import oracle.iot.client.enterprise.EnterpriseClient;
import oracle.iot.client.enterprise.Filter;
import oracle.iot.client.enterprise.Pageable;
import oracle.iot.client.enterprise.VirtualDevice;

import javax.naming.Name;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * This sample of listing, monitoring, and setting the attrubutes of multiple
 * simple sensors using an IoT server.
 *
 * Note that the code is Java SE 1.5 compatible.
 */
public class EnterpriseClientSample {
    private static final String HUMIDITY_SENSOR_MODEL_URN =
        "urn:com:oracle:iot:device:humidity_sensor";
    private static final String MAX_THRESHOLD_ATTRIBUTE = "maxThreshold";
    private static final String TOO_HUMID_ALERT =
        HUMIDITY_SENSOR_MODEL_URN + ":too_humid";

    private static final String TEMPERATURE_SENSOR_MODEL_URN =
        "urn:com:oracle:iot:device:temperature_sensor";
    private static final String MIN_THRESHOLD_ATTRIBUTE = "minThreshold";
    private static final String TOO_COLD_ALERT =
        TEMPERATURE_SENSOR_MODEL_URN + ":too_cold";
    private static final String TOO_HOT_ALERT =
        TEMPERATURE_SENSOR_MODEL_URN + ":too_hot";

    private static final String MOTION_ACTIVATED_CAMERA_MODEL_URN =
            "urn:com:oracle:iot:device:motion_activated_camera";

    private static final String MOTION_ACTIVATED_CAMERA_RECORDING =
            MOTION_ACTIVATED_CAMERA_MODEL_URN + ":recording";

    public static boolean isUnderFramework = false;
    public static boolean exiting = false;

    // Map model format URN to its name
    private static final Map<String,String> formatNames;
    static {
        formatNames = new HashMap<String, String>();
        formatNames.put(TOO_HUMID_ALERT, "tooHumidAlert");
        formatNames.put(TOO_HOT_ALERT, "tooHotAlert");
        formatNames.put(TOO_COLD_ALERT, "tooColdAlert");
    }

    public static void main(String[] args) {

        EnterpriseClient enterpriseClient = null;
        int exitCode = -1;

        try {
            if (args.length < 2) {
                display("\nIncorrect number of arguments.");
                throw new IllegalArgumentException("Incorrect number of arguments.");
            }
            final String trustStorePath = args[0];
            final String trustStorePassword = args[1];
            enterpriseClient = EnterpriseClient.newClient(trustStorePath, trustStorePassword);

            if (args.length == 2) {
                listDevices(enterpriseClient);
            } else {
                // The third arg is a comma separated list of endpoint ids,
                // e.g., <endpointId-1>[,<endpointId-2>]*
                final String deviceIds = args[2];
                final List<VirtualDevice> virtualDevices = createVirtualDevices(enterpriseClient, deviceIds);

                if (args.length == 3) {
                    monitorDevices(virtualDevices);

                } else if (args.length == 4) {
                    // fourth arg is either an action or the max threshold setting
                    if (args[3].equals("reset")) {
                        reset(virtualDevices);
                    } else if (args[3].equals("on")) {
                        powerOn( virtualDevices);
                    } else if (args[3].equals("off")) {
                        powerOff(virtualDevices);
                    } else {
                        String maxThreshold = args[3];
                        setThresholds(virtualDevices, maxThreshold, null);
                    }

                } else if (args.length == 5) {

                    if (args[3].equalsIgnoreCase("record")) {
                        int duration = Integer.valueOf(args[4]);
                        recordVideo(virtualDevices, duration);
                    } else {
                        // if there is a fifth arg, then it is the min threshold
                        // and the fourth is the max threshold.
                        String maxThreshold = args[3];
                        String minThreshold = args[4];
                        setThresholds(virtualDevices, maxThreshold, minThreshold);
                    }

                } else {
                    throw new IllegalArgumentException("Incorrect number of arguments.");
                }
            }

            exitCode = 0;
        } catch (Throwable e) {             
            displayException(e);
            if (isUnderFramework) throw new RuntimeException(e);
        } finally {
            // Dispose of the enterprise client
            if (enterpriseClient != null) {
                try {
                    enterpriseClient.close();
                } catch (IOException ignored) {
                }
            }
        }
        if (isUnderFramework && !exiting) {
            // when not monitoring there is no infinite loop which needs 'stop <bundle>' command
            // throw exception to stop bundle
            throw new RuntimeException("exiting with " + exitCode);
        }
    }

    /**
     * Print a list of active devices and whether a device is a temperature sensor or a humidity sensor.
     * @param enterpriseClient the EnterpriseClient instance to use
     * @throws GeneralSecurityException thrown by EnterpriseClient API
     * @throws IOException thrown by EnterpriseClient API
     */
    public static void listDevices(EnterpriseClient enterpriseClient)
            throws GeneralSecurityException, IOException {
        String[] deviceModelUrns = new String[] {
                TEMPERATURE_SENSOR_MODEL_URN,
                HUMIDITY_SENSOR_MODEL_URN,
                MOTION_ACTIVATED_CAMERA_MODEL_URN
        };
        for (String deviceModelUrn : deviceModelUrns) listDevices(enterpriseClient, deviceModelUrn);
    }

    // Detailed implementation of listDevices(EnterpriseClient)
    private static void listDevices(EnterpriseClient enterpriseClient, String deviceModelUrn)
            throws GeneralSecurityException, IOException {

        final Pageable<Device> devices =
                enterpriseClient.getActiveDevices(deviceModelUrn);

        if (!devices.hasMore()) {
            display("No active devices for device model \"" + deviceModelUrn + "\"");

        } else {

            final DeviceModel deviceModel = enterpriseClient.getDeviceModel(deviceModelUrn);

            while (devices.hasMore()) {
                devices.next();
                for (Device d : devices.elements()) {
                    display(d.getId() + " [" + deviceModel.getName() + "]");
                }
            }
        }

    }

    // This callback will be invoked when the client library receives an change to an attribute
    // of a device the application is monitoring. The callback simply prints out the
    // attributes and values that have changed. The same callback is used for all of the
    // VirtualDevice instances that are being monitored.
    private final static VirtualDevice.ChangeCallback<VirtualDevice> changeCallback =
            new VirtualDevice.ChangeCallback<VirtualDevice>() {

                public void onChange(VirtualDevice.ChangeEvent<VirtualDevice> event) {

                    VirtualDevice virtualDevice = event.getVirtualDevice();

                    StringBuilder msg =
                            new StringBuilder(new Date().toString())
                                    .append(" : ")
                                    .append(virtualDevice.getEndpointId())
                                    .append(" : onChange : ");

                    char sep = '\0';
                    VirtualDevice.NamedValue<?> namedValue = event.getNamedValue();
                    while(namedValue != null) {
                        String attributeName = namedValue.getName();
                        Object attributeValue = namedValue.getValue();

                        if (sep != '\0') msg.append(sep);
                        else sep = ',';
                        msg.append('\"');
                        msg.append(attributeName);
                        msg.append("\"=");
                        msg.append(String.valueOf(attributeValue));

                        if (attributeValue instanceof StorageObject) {
                            // The client library gives control to the application to decide
                            // whether and where to download content from the Storage Cloud Service.
                            final StorageObject storageObject = (StorageObject)attributeValue;
                            try {
                                storageObject.setOutputPath("./downloads/" + storageObject.getName());
                                // sync happens in the background and calls the syncCallback when done
                                storageObject.setOnSync(syncCallback);
                                storageObject.sync();
                            } catch (FileNotFoundException e) {
                                displayException(e);
                            }
                        }

                        namedValue = namedValue.next();
                    }

                    display(msg.toString());
                }
            };


    // This callback will be invoked when the client library receives an alert on an attribute
    // of a device the application is monitoring. The callback simply prints out the
    // attributes and values that have been alerted. The same callback is used for all of the
    // VirtualDevice instances that are being monitored.
    private final static VirtualDevice.AlertCallback alertCallback =
            new VirtualDevice.AlertCallback() {

                public void onAlert(VirtualDevice.AlertEvent event) {

                    VirtualDevice virtualDevice = event.getVirtualDevice();

                    StringBuilder msg =
                            new StringBuilder(new Date().toString())
                                    .append(" : ")
                                    .append(virtualDevice.getEndpointId())
                                    .append(" : onAlert : ");

                    char sep = '\0';
                    VirtualDevice.NamedValue<?> namedValue = event.getNamedValue();
                    while(namedValue != null) {
                        String name = namedValue.getName();
                        Object value = namedValue.getValue();


                        if (sep != '\0') msg.append(sep);
                        else sep = ',';
                        msg.append('\"');
                        msg.append(name);
                        msg.append("\"=");
                        msg.append(String.valueOf(value));

                        if (value instanceof StorageObject) {
                            // The client library gives control to the application to decide
                            // whether and where to download content from the Storage Cloud Service.
                            final StorageObject storageObject = (StorageObject)value;
                            storageObject.setOnSync(syncCallback);
                            try {
                                storageObject.setOutputPath("./downloads/" + storageObject.getName());
                                // sync happens in the background and calls the syncCallback when done
                                storageObject.sync();
                            } catch (FileNotFoundException e) {
                                displayException(e);
                            }
                        }

                        namedValue = namedValue.next();
                    }

                    final String alertName = formatNames.get(event.getURN());
                    if (alertName != null) {
                        msg.append(" (").append(alertName).append(")");
                    }

                    display(msg.toString());
                }
            };

    // This callback will be invoked when the client library receives an alert on an attribute
    // of a device the application is monitoring. The callback simply prints out the
    // attributes and values that have been alerted. The same callback is used for all of the
    // VirtualDevice instances that are being monitored.
    private final static VirtualDevice.ErrorCallback<VirtualDevice> errorCallback=
            new VirtualDevice.ErrorCallback<VirtualDevice>() {

                public void onError(VirtualDevice.ErrorEvent<VirtualDevice> event) {
                    VirtualDevice virtualDevice = event.getVirtualDevice();
                    StringBuilder msg =
                            new StringBuilder(new Date().toString())
                                    .append(" : ")
                                    .append(virtualDevice.getEndpointId())
                                    .append(" : onError : \"")
                                    .append(event.getMessage())
                                    .append("\"");
                /*
                 * Normally some error processing would go here,
                 * but since this a sample, notify the main thread
                 * to end the sample.
                 */
                    display(msg.toString());
                }
            };

    // This callback will be set on a StorageObject when this application receives an
    // onChange event containing a StorageObject. The callback simply prints out the
    // status of synchronizing the content with the Oracle Storage Cloud Service.
    // The same callback is used for all of the StorageObject instances.
    private static final StorageObject.SyncCallback<VirtualDevice> syncCallback =
            new StorageObject.SyncCallback<VirtualDevice>() {
                @Override
                public void onSync(StorageObject.SyncEvent<VirtualDevice> event) {
                    VirtualDevice virtualDevice = event.getVirtualDevice();
                    StorageObject storageObject = event.getSource();
                    StringBuilder msg =
                            new StringBuilder(new Date().toString())
                                    .append(" : ")
                                    .append(virtualDevice.getEndpointId())
                                    .append(" : onSync : ")
                                    .append(storageObject.getName())
                                    .append("=\"")
                                    .append(storageObject.getSyncStatus())
                                    .append("\"");

                    if (storageObject.getSyncStatus() == StorageObject.SyncStatus.IN_SYNC) {
                        msg.append(" (")
                                .append(storageObject.getLength())
                                .append(" bytes)");
                    }
                    display(msg.toString());
                }
            };

    /**
     * Monitor the devices and print onChange and onAlert events.
     * @param virtualDevices a {@code List<VirtualDevice>}
     */
    public static void monitorDevices(List<VirtualDevice> virtualDevices) {

        for (VirtualDevice virtualDevice: virtualDevices) {
            virtualDevice.setOnChange(changeCallback);
            virtualDevice.setOnAlert(alertCallback);
        }

        waitForExit(isUnderFramework);

    }

    /**
     * If a device has the "urn:com:oracle:iot:device:temperature_sensor" model, reset the device.
     * @param virtualDevices a {@code List<VirtualDevice>}
     */
    public static void reset(List<VirtualDevice> virtualDevices) {

        for (VirtualDevice virtualDevice : virtualDevices) {

            final StringBuilder msg =
                    new StringBuilder(new Date().toString())
                            .append(" : ")
                            .append(virtualDevice.getEndpointId())
                            .append(" : Call : reset");

            final String modelUrn = virtualDevice.getDeviceModel().getURN();

            if (TEMPERATURE_SENSOR_MODEL_URN.equals(modelUrn)) {
                virtualDevice.setOnError(errorCallback);
                // Control the device through the virtual device
                display(msg.toString());
                virtualDevice.call("reset");

            } else {
                msg.append(" not supported for device model ")
                        .append("\"")
                        .append(modelUrn)
                        .append("\"");

                display(msg.toString());
            }
        }

    }

    /**
     * If a device has the "urn:com:oracle:iot:device:temperature_sensor" model, turn the device on.
     * @param virtualDevices a {@code List<VirtualDevice>}
     */
    public static void powerOn(List<VirtualDevice> virtualDevices) {

        for (VirtualDevice virtualDevice : virtualDevices) {

            final StringBuilder msg =
                    new StringBuilder(new Date().toString())
                            .append(" : ")
                            .append(virtualDevice.getEndpointId())
                            .append(" : Call : \"power\"=true");

            final String modelUrn = virtualDevice.getDeviceModel().getURN();

            if (TEMPERATURE_SENSOR_MODEL_URN.equals(modelUrn)) {
                virtualDevice.setOnError(errorCallback);
                // Control the device through the virtual device
                display(msg.toString());
                virtualDevice.call("power", true);

            } else {
                msg.append(" not supported for device model ")
                        .append("\"")
                        .append(modelUrn)
                        .append("\"");

                display(msg.toString());
            }
        }
    }

    /**
     * If a device has the "urn:com:oracle:iot:device:temperature_sensor" model, turn the device off.
     * @param virtualDevices a {@code List<VirtualDevice>}
     */
    public static void powerOff(List<VirtualDevice> virtualDevices) {

        for (VirtualDevice virtualDevice : virtualDevices) {

            final StringBuilder msg =
                    new StringBuilder(new Date().toString())
                            .append(" : ")
                            .append(virtualDevice.getEndpointId())
                            .append(" : Call : \"power\"=false");

            final String modelUrn = virtualDevice.getDeviceModel().getURN();

            if (TEMPERATURE_SENSOR_MODEL_URN.equals(modelUrn)) {
                virtualDevice.setOnError(errorCallback);
                // Control the device through the virtual device
                display(msg.toString());
                virtualDevice.call("power", false);

            } else {
                msg.append(" not supported for device model ")
                        .append("\"")
                        .append(modelUrn)
                        .append("\"");

                display(msg.toString());
            }
        }

    }

    /**
     * Set the minimum and maximum threshold for the virtual humidity and temperature sensor devices.
     * @param virtualDevices a {@code List<VirtualDevice>} that have the
     * "urn:com:oracle:iot:device:humidity_sensor" or "urn:com:oracle:iot:device:humidity_sensor" model.
     * @param maxThreshold the value to which the maximum threshold will be set
     * @param minThreshold the value to which the minimum threshold will be set
     */
    public static void setThresholds(List<VirtualDevice> virtualDevices,
                                     String maxThreshold, String minThreshold) {

        // This will be used to count how many virtual devices have had set called on them.
        // We listen for onChange of max/minThreshold and decrement this count for each
        // onChange. When count hits zero, we have all the onChange events and can exit.
        final AtomicInteger count = new AtomicInteger(0);

        // First, set up a callback for max/minThreshold. When we get a notification
        // that the max/minThreshold of the virtual device is changed, we know the
        // value on the actual device has been set.
        for (VirtualDevice virtualDevice : virtualDevices) {

            final String modelUrn = virtualDevice.getDeviceModel().getURN();

            if (TEMPERATURE_SENSOR_MODEL_URN.equals(modelUrn) ||
                    HUMIDITY_SENSOR_MODEL_URN.equals(modelUrn)) {

                // minThreshold only applies to a temperature sensor.
                boolean setMin = (minThreshold != null) && (TEMPERATURE_SENSOR_MODEL_URN.equals(modelUrn));

                // count one for maxThreshold, two for max and min.
                count.addAndGet(setMin ? 2 : 1);

                virtualDevice.setOnError(errorCallback);

                // use the existing changeCallback to pretty print the event
                virtualDevice.setOnChange(changeCallback);

                // this change callback will also be invoked for the maxThreshold attribute.
                virtualDevice.setOnChange(MAX_THRESHOLD_ATTRIBUTE,
                        new VirtualDevice.ChangeCallback<VirtualDevice>() {
                            @Override
                            public void onChange(AbstractVirtualDevice.ChangeEvent<VirtualDevice> event) {
                                final int remaining = count.decrementAndGet();
                                display(remaining + " requests remain.");
                                if (remaining == 0) {
                                    exiting = true;
                                }
                            }
                        });

                // this error callback will  be invoked for the maxThreshold attribute if there is an error
                virtualDevice.setOnError(MAX_THRESHOLD_ATTRIBUTE,
                        new VirtualDevice.ErrorCallback<VirtualDevice>() {
                            @Override
                            public void onError(AbstractVirtualDevice.ErrorEvent<VirtualDevice> event) {
                                final int remaining = count.decrementAndGet();
                                display(remaining + " requests remain.");
                                if (remaining == 0) {
                                    exiting = true;
                                }
                            }
                        });

                if (setMin) {
                    // this change callback will also be invoked for the minThreshold attribute.
                    virtualDevice.setOnChange(MIN_THRESHOLD_ATTRIBUTE,
                            new VirtualDevice.ChangeCallback<VirtualDevice>() {
                                @Override
                                public void onChange(AbstractVirtualDevice.ChangeEvent<VirtualDevice> event) {
                                    final int remaining = count.decrementAndGet();
                                    display(remaining + " requests remain.");
                                    if (remaining == 0) {
                                        exiting = true;
                                    }
                                }
                            });

                    // this error callback will  be invoked for the minThreshold attribute if there is an error
                    virtualDevice.setOnError(MIN_THRESHOLD_ATTRIBUTE,
                            new VirtualDevice.ErrorCallback<VirtualDevice>() {
                                @Override
                                public void onError(AbstractVirtualDevice.ErrorEvent<VirtualDevice> event) {
                                    final int remaining = count.decrementAndGet();
                                    display(remaining + " requests remain.");
                                    if (remaining == 0) {
                                        exiting = true;
                                    }
                                }
                            });
                }
            }
        }

        // Next, actually invoke the set.
        for (VirtualDevice virtualDevice : virtualDevices) {

            final StringBuilder msg =
                    new StringBuilder(new Date().toString())
                            .append(" : ")
                            .append(virtualDevice.getEndpointId())
                            .append(" : Set : ");

            final String modelUrn = virtualDevice.getDeviceModel().getURN();


            if (TEMPERATURE_SENSOR_MODEL_URN.equals(modelUrn) ||
                    HUMIDITY_SENSOR_MODEL_URN.equals(modelUrn)) {

                msg.append("\"").append(MAX_THRESHOLD_ATTRIBUTE).append("\"=").append(maxThreshold);

                // Send max and min in one data message by using 'update()' and 'finish()'
                virtualDevice.update()
                        .set(MAX_THRESHOLD_ATTRIBUTE, Integer.parseInt(maxThreshold));

                // minThreshold only applies to a temperature sensor.
                if (minThreshold != null) {
                    if (TEMPERATURE_SENSOR_MODEL_URN.equals(modelUrn)) {
                        msg.append(",\"").append(MIN_THRESHOLD_ATTRIBUTE).append("\"=").append(minThreshold);
                        virtualDevice.set(MIN_THRESHOLD_ATTRIBUTE, Integer.parseInt(minThreshold));
                    } else {
                        msg.append(",\"").append(MIN_THRESHOLD_ATTRIBUTE).append("\"=").append(minThreshold)
                                .append(" not supported for device model \"" + modelUrn).append("\"");
                    }
                }

                display(msg.toString());

                // commit the set() that were done in the update()
                virtualDevice.finish();

            } else {
                msg.append("\"").append(MAX_THRESHOLD_ATTRIBUTE).append("\"")
                        .append(" or \"").append(MIN_THRESHOLD_ATTRIBUTE).append("\"")
                        .append(" not supported for device model ")
                        .append("\"").append(modelUrn).append("\"");

                display(msg.toString());
            }
        }

        if (count.get() > 0) waitForExit(isUnderFramework);
    }

    /**
     * If a device has the "urn:com:oracle:iot:device:motion_activated_camera" model,
     * call the "record" action
     * @param virtualDevices a {@code List<VirtualDevice>}
     */
    public static void recordVideo(List<VirtualDevice> virtualDevices, int duration) {

        // This will be used to count how many virtual devices have had set called on them.
        // We listen for onChange of max/minThreshold and decrement this count for each
        // onChange. When count hits zero, we have all the onChange events and can exit.
        final AtomicInteger count = new AtomicInteger(0);

        for (VirtualDevice virtualDevice : virtualDevices) {

            final StringBuilder msg =
                    new StringBuilder(new Date().toString())
                            .append(" : ")
                            .append(virtualDevice.getEndpointId())
                            .append(" : Call : \"record\"")
                            .append(duration);

            final String modelUrn = virtualDevice.getDeviceModel().getURN();

            if (MOTION_ACTIVATED_CAMERA_MODEL_URN.equals(modelUrn)) {
                virtualDevice.setOnError(errorCallback);
                // Control the device through the virtual device
                display(msg.toString());

                // this callback will  be invoked when data is received for the format
                // urn:com:oracle:iot:device:motion_activated_camera:recording
                virtualDevice.setOnData(MOTION_ACTIVATED_CAMERA_MODEL_URN + ":recording", new VirtualDevice.DataCallback() {
                    @Override
                    public void onData(VirtualDevice.DataEvent event) {

                        VirtualDevice device = event.getVirtualDevice();
                        StringBuilder msg =
                                new StringBuilder(new Date().toString())
                                        .append(" : ")
                                        .append(device.getEndpointId())
                                        .append(" : onData : ");
                        VirtualDevice.NamedValue<?> namedValue = event.getNamedValue();

                        boolean first = true;
                        while (namedValue != null) {
                            String attributeName = namedValue.getName();
                            Object attributeValue = namedValue.getValue();

                            if (first) first = false;
                            else msg.append(',');

                            msg.append('\"');
                            msg.append(attributeName);
                            msg.append("\"=");
                            msg.append(String.valueOf(attributeValue));

                            if (attributeValue instanceof StorageObject) {
                                // The client library gives control to the application to decide
                                // whether and where to download content from the Storage Cloud Service.
                                final StorageObject storageObject = (StorageObject)attributeValue;
                                try {
                                    storageObject.setOutputPath("./downloads/" + storageObject.getName());
                                    // sync happens in the background and calls the syncCallback when done
                                    storageObject.setOnSync(syncCallback);
                                    storageObject.sync();
                                } catch (FileNotFoundException e) {
                                    displayException(e);
                                }
                            }

                            namedValue = namedValue.next();
                        }

                        display(msg.toString());

                        final int remaining = count.decrementAndGet();
                        if (remaining == 0) {
                            exiting = true;
                        }
                    }
                });

                count.incrementAndGet();
                virtualDevice.call("record", duration);

            } else {
                msg.append(" not supported for device model ")
                        .append("\"")
                        .append(modelUrn)
                        .append("\"");

                display(msg.toString());
            }
        }

        if (count.get() > 0) waitForExit(isUnderFramework);

    }

    /**
     * Returns a {@code List<VirtualDevice>} for the given {@code deviceIds}. The VirtualDevice instances
     * will implement the "urn:com:oracle:iot:device:humidity_sensor" or "urn:com:oracle:iot:device:temperature_sensor"
     * device models. If the device does not implement one of these models, an {@code IllegalArgumentException} is
     * thrown.
     *
     * @param enterpriseClient the enterprise client
     * @param deviceIds one or more device endpoint ids, comma delimited
     * @return a {@code List<String>} of device model URNs, or {@code null}
     * @throws IllegalArgumentException if a device is not found, or does not have the device model
     * "urn:com:oracle:iot:device:humidity_sensor" or "urn:com:oracle:iot:device:humidity_sensor"
     * @throws GeneralSecurityException is thrown from the EnterpriseClient API
     * @throws IOException is thrown from the EnterpriseClient API
     */
    public static List<VirtualDevice> createVirtualDevices(EnterpriseClient enterpriseClient, String deviceIds)
            throws IllegalArgumentException, GeneralSecurityException, IOException {

        final String[] ids = deviceIds.split(",");
        final List<VirtualDevice> virtualDevices = new ArrayList<VirtualDevice>();
        for (String id: ids) {
            boolean found = false;
            List<String> modelUrns = getDeviceModelUrnsForDevice(enterpriseClient, id);
            for (String modelUrn : modelUrns) {
                if (HUMIDITY_SENSOR_MODEL_URN.equals(modelUrn) ||
                        TEMPERATURE_SENSOR_MODEL_URN.equals(modelUrn) ||
                        MOTION_ACTIVATED_CAMERA_MODEL_URN.equals(modelUrn)) {
                    // Create a virtual device for this model
                    DeviceModel deviceModel = enterpriseClient.getDeviceModel(modelUrn);
                    VirtualDevice virtualDevice = enterpriseClient.createVirtualDevice(id, deviceModel);
                    virtualDevices.add(virtualDevice);
                    found = true;

                }
            }

            if (!found) {
                throw new IllegalArgumentException(id + " does not have device model \"" +
                        TEMPERATURE_SENSOR_MODEL_URN + "\", \"" +
                        HUMIDITY_SENSOR_MODEL_URN + "\" or \"" +
                        MOTION_ACTIVATED_CAMERA_MODEL_URN + "\""+ "\"");
            }
        }

        return virtualDevices;
    }

    /**
     * Returns a list of device model URNs for the device.
     * @param enterpriseClient the enterprise client
     * @param endpointId then device endpoint id
     * @return a {@code List<String>} of device model URNs, or {@code null} if there are no models for the device
     * @throws IllegalArgumentException if the device is not found
     * @throws GeneralSecurityException is thrown from the EnterpriseClient API
     * @throws IOException is thrown from the EnterpriseClient API
     */
    public static List<String> getDeviceModelUrnsForDevice(EnterpriseClient enterpriseClient, String endpointId)
            throws IllegalArgumentException, GeneralSecurityException, IOException {
        Device device = null;

        Filter filter = Filter.eq(Device.Field.ID.alias(), endpointId);
        Pageable<Device> devices = enterpriseClient.getDevices(null, filter);
        while (device == null && devices.hasMore()) {
            devices.next();
            for (Device d: devices.elements()) {
                if (d.getId().equals(endpointId)) {
                    device = d;
                    break;
                }
            }
        }

        if (device == null) {
            throw new IllegalArgumentException("Cannot find device " + endpointId);
        }

        // Get the device model instance.
        List<String> urns = device.getDeviceModels();
        if (urns == null || urns.isEmpty()) {
            throw new IllegalArgumentException("No device models for " + endpointId);
        }

        // The device model formats.
        return urns;
    }

    //
    // Utilities for this sample.
    //

    // This method waits for the exiting flag to be set, or for a keypress.
    private static void waitForExit(boolean flagged) {

        if (flagged) {
            try {
                for(;;) {
                    Thread.sleep(100);
                    if (exiting) {
                        break;
                    }
                }
            } catch (InterruptedException ignored) {
                // reset interrupt status
                Thread.currentThread().interrupt();
            }

        } else {
            display("\n\tPress enter to exit.\n");

            try {
                System.in.read();
            } catch (IOException ignored) {

            } finally {
                exiting = true;
            }
        }
    }


    private static void display(String string) {
        System.out.println(string);
    }

    private static void showUsage() {
        Class<?> thisClass = new Object() { }.getClass().getEnclosingClass();
        display("Usage:\n"
                + "java " + thisClass.getName()
                + " <trusted assets file> <trusted assets password>\n"
                + "    List all temperature sensors, humidity sensors, and motion activated cameras.\n\n"
                + "java " + thisClass.getName()
                + " <trusted assets file> <trusted assets password>"
                + " <deviceId>[,<deviceId>]\n"
                + "    Monitor virtual device(s) and print its attributes every"
                + " time they change, until return key is pressed.\n\n"
                + "java " + thisClass.getName()
                + " <trusted assets file> <trusted assets password>"
                + " <deviceId> reset|on|off\n"
                + "    Reset the thermometer, turn on the thermometer, or "
                + " turn off the thermometer.\n\n"
                + "java " + thisClass.getName()
                + " <trusted assets file> <trusted assets password>"
                + " <deviceId> <maxThreshold>\n"
                + "    Set the maximum threshold of a temperature sensor or a humidity sensor.\n\n"
                + "java " + thisClass.getName()
                + " <trusted assets file> <trusted assets password>"
                + " <deviceId>  <maxThreshold>  <minThreshold>\n"
                + "    Set the maximum and minimum thresholds of a temperature sensor.\n"
                + "java " + thisClass.getName()
                + " <trusted assets file> <trusted assets password>"
                + " <deviceId>  record  <duration>\n"
                + "    Record at least <duration> seconds of video from a motion activated camera.\n");
    }

    private static void displayException(Throwable e) {
        StringBuilder builder = new StringBuilder(e.getClass().getName());
        if (e.getMessage() != null) {
            builder.append(" : message= \"").append(e.getMessage()).append("\"");
        }
        if (e.getCause() != null) {
            builder.append(" : cause= \"").append(e.getCause()).append("\"");
        }
        display('\n' + builder.toString() + '\n');
        showUsage();
    }
}
