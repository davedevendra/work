/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.enterprise;


import com.oracle.iot.client.DeviceModelAction;
import com.oracle.iot.client.DeviceModelAttribute;
import com.oracle.iot.client.RestApi;
import com.oracle.iot.client.HttpResponse;
import com.oracle.iot.client.VirtualDeviceAttribute;
import com.oracle.iot.client.impl.http.HttpSecureConnection;
import com.oracle.iot.client.impl.DeviceModelImpl;
import com.oracle.iot.client.impl.VirtualDeviceAttributeBase;
import com.oracle.iot.client.impl.VirtualDeviceBase;
import com.oracle.iot.client.impl.enterprise.Monitor.NotifyHandler;
import com.oracle.iot.client.impl.util.Pair;
import com.oracle.iot.client.message.StatusCode;

import oracle.iot.client.AbstractVirtualDevice;
import oracle.iot.client.DeviceModel;
import oracle.iot.client.StorageObject;
import oracle.iot.client.enterprise.VirtualDevice;
import oracle.iot.client.enterprise.EnterpriseClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 *
 */
public final class VirtualDeviceImpl
        extends VirtualDevice
        implements VirtualDeviceBase.Adapter<VirtualDevice> {

    private final    HttpSecureConnection secureConnection;
    private final    VirtualDeviceBase base;
    private final    EnterpriseClient enterpriseClient;
    private final    Map<String, VirtualDeviceAttributeBase<VirtualDevice, Object>> attributeMap;
	private final    Object isDeviceAppLock = new Object();
    private volatile Boolean isDeviceApp;

    private Map<String, AlertCallback> alertCallbackMap;

    private Map<String, DataCallback> dataCallbackMap;

    private NotifyHandler notifyHandler =
        new NotifyHandler() {

            public void notifyOnChange(VirtualDeviceImpl virtualDevice,
                                       VirtualDeviceAttributeImpl<?>[] attributes) {

                if (attributes == null || attributes.length == 0) {
                    // should not create a notify handler if there
                    // are no attributes
                    assert false;
                    return;
                }

                // Get the onChange handler that was set on the VirtualDevice.
                // May be null.
                final AbstractVirtualDevice.ChangeCallback<VirtualDevice> globalCallback =
                        base.getOnChangeCallback();

                NamedValue<?> root = null;
                VirtualDeviceBase.NamedValueImpl<?> last = null;

                for (VirtualDeviceAttributeImpl<?> attribute : attributes) {

                    final String attributeName =
                            attribute.getDeviceModelAttribute().getName();
                    final VirtualDeviceAttributeBase<VirtualDevice, ?>
                            currentField = getAttribute(attributeName);
                    final Object newValue = attribute.get();
                    final Object oldValue = currentField.get();

                    // Do not update the value, or call onChange, if the value hasn't changed.
                    if (oldValue != null ? oldValue.equals(newValue) : newValue == null) {
                        continue;
                    }

                    final VirtualDeviceBase.NamedValueImpl<?> nameValue =
                            new VirtualDeviceBase.NamedValueImpl<Object>(attributeName, newValue);

                    try {
                        currentField.update(newValue);

                            final ChangeCallback<VirtualDevice>
                                attributeChangeCallback =
                                    currentField.getOnChange();

                            if (globalCallback == null &&
                                    attributeChangeCallback == null) {
                                continue;
                            }

                            // Attribute's onChange callback is invoked with
                            // the single attribute.
                            if (attributeChangeCallback != null) {
                                attributeChangeCallback.onChange(
                                        new VirtualDeviceBase.ChangeEvent<VirtualDevice>(
                                                virtualDevice,
                                                nameValue));
                            }

                           if (last != null) {
                               last.setNext(nameValue);
                               last = nameValue;
                           } else {
                               root = last = nameValue;
                           }


                    } catch (Exception e) {
                        // The onChange method may throw exceptions. Since the CL has no
                        // knowledge of what these might be, Exception is caught here.
                        getLogger().log(Level.SEVERE, e.getMessage(), e);
                    }

                }

                if (globalCallback != null && root != null) {
                    try {
                        // invoke the global callback
                        globalCallback.onChange(
                            new VirtualDeviceBase.ChangeEvent<VirtualDevice>(virtualDevice, root));
                    } catch (Exception e) {
                        // The onChange method may throw exceptions. Since the CL has no
                        // knowledge of what these might be, Exception is caught here.
                        getLogger().log(Level.SEVERE, e.getMessage(), e);
                    }
                }
            }

            @Override
            public void notifyOnAlert(VirtualDeviceImpl virtualDevice,
                                      String format,
                                      long eventTime,
                                      NamedValue<?> namedValues) {

                if (alertCallbackMap == null ||
                        (!alertCallbackMap.containsKey(format) &&
                        !alertCallbackMap.containsKey("*"))) {
                    getLogger().log(Level.FINEST, "no callbacks for alerts");
                    return;
                }

                try {

                    // callback takes date for event time
                    final Date when = new Date(eventTime);

                    AlertCallback callback = virtualDevice.alertCallbackMap.get(format);

                    if (callback != null) {
                        callback.onAlert(new AlertEventImpl(virtualDevice, format,
                                namedValues, when));
                    }

                    callback = virtualDevice.alertCallbackMap.get("*");
                    if (callback != null) {
                        callback.onAlert(new AlertEventImpl(virtualDevice, format,
                                namedValues, when));
                    }

                } catch (Exception e) {
                    // catch exceptions thrown by callback and log
                    getLogger().log(Level.SEVERE, e.getMessage(), e);
                }
            }

            @Override
            public void notifyOnData(VirtualDeviceImpl virtualDevice,
                                      String format,
                                      long eventTime,
                                      NamedValue<?> namedValues) {

                if (dataCallbackMap == null ||
                        (!dataCallbackMap.containsKey(format) &&
                        !dataCallbackMap.containsKey("*"))) {
                    getLogger().fine("no callbacks for custom data");
                    return;
                }

                try {

                    // callback takes date for event time
                    final Date when = new Date(eventTime);

                    DataCallback callback = virtualDevice.dataCallbackMap.get(format);

                    if (callback != null) {
                        callback.onData(new DataEventImpl(virtualDevice, format,
                                namedValues, when));
                    }

                    callback = virtualDevice.dataCallbackMap.get("*");
                    if (callback != null) {
                        callback.onData(new DataEventImpl(virtualDevice, format,
                                namedValues, when));
                    }

                } catch (Exception e) {
                    // catch exceptions thrown by callback and log
                    getLogger().log(Level.SEVERE, e.getMessage(), e);
                }
            }
        };

    /**
     * Initialize a new {@code VirtualDeviceImpl}
     * @param enterpriseClient the client using this virtual device
     * @param secureConnection the secure connection to be used by this virtual device
     * @param endpointId the identifier of the device (or deviceApp)
     * @param model the model implemented by the device
     */
    public VirtualDeviceImpl(EnterpriseClient enterpriseClient,
                             HttpSecureConnection secureConnection,
                             String endpointId,
                             DeviceModelImpl model) {
        super();
        this.secureConnection = secureConnection;
        this.base = new VirtualDeviceBase(this, endpointId, model);
        this.enterpriseClient = enterpriseClient;
        this.attributeMap = new HashMap<String, VirtualDeviceAttributeBase<VirtualDevice, Object>>();

        try {
            Monitor.startMonitor(this, notifyHandler);
        } catch (IOException ioe) {
            getLogger().log(Level.WARNING, "Failed to create virtual device", ioe);
            throw new IllegalArgumentException("Failed to create virtual device", ioe);
        } catch (GeneralSecurityException gse) {
            getLogger().log(Level.WARNING, "Failed to create virtual device", gse);
            throw new RuntimeException("Failed to create virtual device", gse);
        }
    }

    /**
     * This method is used for unit testing and is not intended for general use.
     */
    VirtualDeviceImpl(HttpSecureConnection secureConnection,
                      DeviceModelImpl model) {
        super();
        this.enterpriseClient = null;
        this.secureConnection = secureConnection;
        this.base = new VirtualDeviceBase(this, secureConnection.getEndpointId(), model);
        this.attributeMap = new HashMap<String, VirtualDeviceAttributeBase<VirtualDevice, Object>>();
    }

        /**
         * Return the EnterpriseClient instance.
         * @return the EnterpriseClient instance
         */
    public EnterpriseClient getEnterpriseClient() {
        return enterpriseClient;
    }

    HttpSecureConnection getSecureConnection() {
        return secureConnection;
    }

    /**
     * VirtualDeviceBase.Adapter API
     * {@inheritDoc}
     */
    @Override
    public VirtualDeviceAttributeBase<VirtualDevice, Object> getAttribute(String attributeName) {
        VirtualDeviceAttributeBase<VirtualDevice, Object> virtualDeviceAttribute = null;
        if (!attributeMap.containsKey(attributeName)) {
            virtualDeviceAttribute = createAttribute(attributeName);
            attributeMap.put(attributeName, virtualDeviceAttribute);
        } else {
            virtualDeviceAttribute = attributeMap.get(attributeName);
        }

        if (virtualDeviceAttribute == null) {
            throw new IllegalArgumentException("no such attribute '" +
                attributeName + "'");
        }
        return virtualDeviceAttribute;
    }

    /**
     * VirtualDeviceBase.Adapter API
     * {@inheritDoc}
     */
    @Override
    public void setValue(VirtualDeviceAttributeBase<VirtualDevice, Object> deviceAttribute, Object value) {
        if (deviceAttribute == null) {
            throw new NullPointerException("attribute cannot be null");
        }
        remoteSet(deviceAttribute, value);
    }

    @Override
    public DeviceModel getDeviceModel() {
        return base.getDeviceModel();
    }

    @Override
    public String getEndpointId() {
        return base.getEndpointId();
    }

    @Override
    public <T> T get(String attributeName) {
        VirtualDeviceAttributeBase<VirtualDevice, ?> attribute = getAttribute(attributeName);
        return (T)attribute.get();
    }

    @Override
    public <T> T getLastKnown(String attributeName) {
        VirtualDeviceAttributeBase<VirtualDevice, ?> attribute = getAttribute(attributeName);
        return (T)attribute.getLastKnown();
    }

    @Override
    public <T> VirtualDevice set(String attributeName, T value) {
        base.set(attributeName, value);
        return this;
    }

    @Override
    public VirtualDevice update() {
        base.update();
        return this;
    }

    @Override
    public void finish() {
        base.finish();
    }

    private static final String UPDATE_DEVICE_RESOURCE =
        RestApi.V2.getReqRoot()+"/apps/%1$s/devices/%2$s/deviceModels/%3$s/attributes";

    private static final String UPDATE_DEVICE_APP_RESOURCE =
        RestApi.V2.getReqRoot()+"/apps/%1$s/deviceApps/%2$s/deviceModels/%3$s/attributes";

    /**
     * Set all the attributes in an update batch. Errors are handled in the
     * set call, including calling the on error handler.
     * @param updatedAttributes The attributes modified during an update
     */
    @Override
    public void updateFields(final List<Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>> updatedAttributes) {
        JSONObject objectBuilder = new JSONObject();
        for (Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object> entry : updatedAttributes) {
            VirtualDeviceAttributeBase<VirtualDevice, ?> attribute =
                entry.getKey();
            Object value = entry.getValue();
            String attributeName =
                attribute.getDeviceModelAttribute().getName();
            try {
                switch (attribute.getDeviceModelAttribute().getType()) {
                case NUMBER:
                case INTEGER:
                case STRING:
                case BOOLEAN:
                    objectBuilder.put(attributeName, value);
                    break;

                case DATETIME:
                    long dateTime;

                    if (value instanceof Date) {
                        dateTime = ((Date)value).getTime();
                    } else {
                        dateTime = ((Number)value).longValue();
                    }

                    objectBuilder.put(attributeName, dateTime);
                    break;
                case URI:
                    if (!syncStorageObject(attributeName, value)) {
                        processOnError(this, updatedAttributes, "Content sync failed.");
                        return;
                    }
                    objectBuilder.put(attributeName,((oracle.iot.client.ExternalObject)value).getURI());
                    break;
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        EnterpriseClient ec = getEnterpriseClient();
        String resource_format = isDeviceApp() ?
                UPDATE_DEVICE_APP_RESOURCE : UPDATE_DEVICE_RESOURCE;

        String appId = ec.getApplication().getId();
        Controller.update(this, "PATCH", String.format(resource_format,
            appId, getEndpointId(), getDeviceModel().getURN()),
            objectBuilder.toString(),
            new Controller.Callback() {
                public void onError(VirtualDevice virtualDevice,
                        String resource, String payload, String reason) {
                    processOnError(virtualDevice, updatedAttributes, reason);
                }
            });
    }

    @Override
    public void setOnChange(String attributeName,
            ChangeCallback callback) {
        VirtualDeviceAttribute attribute = getAttribute(attributeName);
        attribute.setOnChange(callback);
    }

    @Override
    public void setOnChange(ChangeCallback callback) {
        base.setOnChange(callback);
    }

    @Override
    public void setOnError(ErrorCallback callback) {
        base.setOnError(callback);
    }

    @Override
    public void setOnError(String attributeName,
            ErrorCallback callback) {
        VirtualDeviceAttribute attribute = getAttribute(attributeName);
        attribute.setOnError(callback);
    }

    @Override
    public void setOnAlert(String formatURN, AlertCallback callback) {

        if (formatURN == null || formatURN.isEmpty()) {
            throw new IllegalArgumentException("format URN may not be null or empty");
        }

        // global callback, no validation of format URN
        if (!"*".equals(formatURN)) {
            // validate the format URN
            final DeviceModelImpl deviceModelImpl = base.getDeviceModel();
            boolean isValid = (deviceModelImpl.getDeviceModelFormats().get(formatURN) != null);
                if (!isValid) {
                    throw new IllegalArgumentException(formatURN +
                                                       " not found in model");
                }
        }

        if (alertCallbackMap == null) {
            alertCallbackMap = new HashMap<String, AlertCallback>();
        }

        if (callback != null) {
            alertCallbackMap.put(formatURN, callback);
        } else {
            alertCallbackMap.remove(formatURN);
        }
    }

    @Override
    public void setOnAlert(AlertCallback callback) {
        setOnAlert("*", callback);
    }

    @Override
    public void setOnData(String formatURN, DataCallback callback) {

        if (formatURN == null || formatURN.isEmpty()) {
            throw new IllegalArgumentException("format URN may not be null or empty");
        }

        // global callback, no validation of format URN
        if (!"*".equals(formatURN)) {
            // validate the format URN
            final DeviceModelImpl deviceModelImpl = base.getDeviceModel();
            boolean isValid = (deviceModelImpl.getDeviceModelFormats().get(formatURN) != null);
                if (!isValid) {
                    throw new IllegalArgumentException(formatURN +
                                                       " not found in model");
                }
        }

        if (dataCallbackMap == null) {
            dataCallbackMap = new HashMap<String, DataCallback>();
        }

        if (callback != null) {
            dataCallbackMap.put(formatURN, callback);
        } else {
            dataCallbackMap.remove(formatURN);
        }
    }

    @Override
    public void setOnData(DataCallback callback) {
        setOnData("*", callback);
    }

    private <T> VirtualDeviceAttributeBase<VirtualDevice, T> createAttribute(String attributeName) {

        if (attributeName == null || attributeName.length() == 0) return null;

        final DeviceModelImpl deviceModelImpl = base.getDeviceModel();
        DeviceModelAttribute deviceModelAttribute = deviceModelImpl.getDeviceModelAttributes().get(attributeName);
        return (deviceModelAttribute != null ? 
                new VirtualDeviceAttributeImpl(this, deviceModelAttribute) :            
                null);
    }

    // called by remoteSet
    private void processOnError(
            VirtualDevice virtualDevice,
            VirtualDeviceAttributeBase<VirtualDevice, ?> attribute,
            Object newValue, String reason) {

        if (attribute.getOnError() == null && base.getOnErrorCallback() == null) {
            return;
        }

        try {
            final String name = ((VirtualDeviceAttributeImpl)attribute)
                    .getDeviceModelAttribute().getName();
            NamedValue<?> namedValue = new VirtualDeviceBase.NamedValueImpl<Object>(name, newValue);

            if (attribute.getOnError() != null) {
                attribute.getOnError().onError(
                        new VirtualDeviceBase.ErrorEvent<VirtualDevice>(
                                virtualDevice,
                                namedValue,
                                reason
                        )
                );
            }
            if (base.getOnErrorCallback() != null) {
                base.getOnErrorCallback().onError(
                        new VirtualDeviceBase.ErrorEvent<VirtualDevice>(
                                virtualDevice,
                                namedValue,
                                reason
                        )
                );
            }
        } catch (Exception e) {
            // The onError method may throw exceptions. Since the CL has no
            // knowledge of what these might be, Exception is caught here.
            getLogger().log(Level.SEVERE, e.getMessage(), e);
        }
    }

    // Called by call
    private void processOnError(VirtualDevice virtualDevice,
            String action, Object data, String reason) {

        if (base.getOnErrorCallback() == null) {
            return;
        }

        try {
            VirtualDeviceBase.NamedValueImpl<Object> namedValue =
                new VirtualDeviceBase.NamedValueImpl<Object>(action, data);
            if (base.getOnErrorCallback() != null) {
                base.getOnErrorCallback().onError(
                    new VirtualDeviceBase.ErrorEvent<VirtualDevice>(
                        virtualDevice, namedValue, reason)
                );
            }
        } catch (Exception e) {
            // The onError method may throw exceptions. Since the CL has no
            // knowledge of what these might be, Exception is caught here.
            getLogger().log(Level.SEVERE, e.getMessage(), e);
        }
    }

    // Called by updateFields
    private void processOnError(VirtualDevice virtualDevice,
            List<Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>> attributes, String reason) {

        try {
            VirtualDeviceBase.NamedValueImpl<Object> namedValueList = null;
            VirtualDeviceBase.NamedValueImpl<Object> namedValuePtr = null;

            for (Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object> entry : attributes) {

                VirtualDeviceAttributeBase<VirtualDevice, ?> attribute =
                    entry.getKey();
                if (base.getOnErrorCallback() == null &&
                        attribute.getOnError() == null) {
                    continue;
                }
                // We have device onError or attribute onError
                VirtualDeviceBase.NamedValueImpl namedValue =
                    new VirtualDeviceBase.NamedValueImpl<Object>(
                        ((VirtualDeviceAttributeImpl)attribute)
                            .getDeviceModelAttribute().getName(),
                        entry.getValue());

                if (attribute.getOnError() != null) {
                    attribute.getOnError().onError(
                        new VirtualDeviceBase.ErrorEvent<VirtualDevice>(
                            virtualDevice, namedValue, reason));
                }
                if (base.getOnErrorCallback() != null) {
                    if (namedValueList == null) {
                        namedValueList = namedValue;
                        namedValuePtr = namedValueList;
                    } else {
                        namedValuePtr.setNext(namedValue);
                        namedValuePtr = namedValue;
                    }
                }

            }
            if (base.getOnErrorCallback() != null) {
                base.getOnErrorCallback().onError(
                    new VirtualDeviceBase.ErrorEvent<VirtualDevice>(
                        virtualDevice, namedValueList, reason)
                );
            }
        } catch (Exception e) {
            // The onError method may throw exceptions. Since the CL has no
            // knowledge of what these might be, Exception is caught here.
            getLogger().log(Level.SEVERE, e.getMessage(), e);
        }
    }

    /*
     * %1$s - application id
     * %2$s - device id
     * %3$s - model urn
     * %4$s - attribute name
     */
    private static final String DEVICE_ATTRIBUTES_FORMAT =
            RestApi.V2.getReqRoot()+"/apps/%1$s/devices/%2$s/deviceModels/%3$s/attributes/%4$s";
    private static final String DEVICE_APP_ATTRIBUTES_FORMAT =
            RestApi.V2.getReqRoot()+"/apps/%1$s/deviceApps/%2$s/deviceModels/%3$s/attributes/%4$s";

    /*
     * %1$s - application id
     * %2$s - device id
     * %3$s - model urn
     * %4$s - attribute name
     */
    private static final String DEVICE_ACTION_FORMAT =
            RestApi.V2.getReqRoot()+"/apps/%1$s/devices/%2$s/deviceModels/%3$s/actions/%4$s";

    private static final String DEVICE_APP_ACTION_FORMAT =
            RestApi.V2.getReqRoot()+"/apps/%1$s/deviceApps/%2$s/deviceModels/%3$s/actions/%4$s";

    /**
     * %1$s - actionName
     * %2$s - value
     */
    private static final String PAYLOAD_FORMAT = "{ \"%1$s\" : \"%2$s\" }";

    /**
     * %1$s - actionName
     * %2$s - value
     */
    private static final String NUMBER_PAYLOAD_FORMAT = "{ \"%1$s\" : %2$s }";

    void remoteSet(final VirtualDeviceAttributeBase<VirtualDevice, ?>
            attribute, final Object value) {

        final DeviceModelAttribute deviceModelAttribute =
                attribute.getDeviceModelAttribute();

        if (deviceModelAttribute.getAccess() !=
                DeviceModelAttribute.Access.WRITE_ONLY &&
                deviceModelAttribute.getAccess() !=
                        DeviceModelAttribute.Access.READ_WRITE) {
            throw new IllegalArgumentException(deviceModelAttribute.getName() +
                    "is not writable");
        }

        final String appid = getEnterpriseClient().getApplication().getId();
        final String name = deviceModelAttribute.getName();

        final String attributes_format = isDeviceApp() ?
                DEVICE_APP_ATTRIBUTES_FORMAT : DEVICE_ATTRIBUTES_FORMAT;
        String resource =
                String.format(attributes_format, appid, getEndpointId(),
                        getDeviceModel().getURN(),
                        name);

        boolean syncFailed = false;
        
        if (deviceModelAttribute.getType() == DeviceModelAttribute.Type.URI) {
            syncFailed = !syncStorageObject(name, value);
        }

        String payload = getPayload(deviceModelAttribute.getType(), "value", value);
        
        if (syncFailed) {
            processOnError(this, name, payload, "Content sync failed.");
            return;
        }

        Controller.update(this, "PUT", resource, payload,
                new Controller.Callback() {
                    public void onError(VirtualDevice virtualDevice,
                                        String resource, String payload,
                                        String reason) {
                        processOnError(virtualDevice, attribute, value, reason);
                    }
                });

    }

    private static String getPayload(DeviceModelAttribute.Type type, String name, Object value) {

        String payload = null;

        if (type != null && value != null) {
            switch(type) {
                case INTEGER:
                    payload = String.format(NUMBER_PAYLOAD_FORMAT,
                            name, ((Number)value).intValue());
                    break;
                case NUMBER:
                    payload = String.format(NUMBER_PAYLOAD_FORMAT,
                            name, ((Number)value).floatValue());
                    break;
                case STRING:
                default:
                    payload = String.format(PAYLOAD_FORMAT,
                            name, String.valueOf(value));
                    break;
                case BOOLEAN:
                    payload = String.format(NUMBER_PAYLOAD_FORMAT,
                            name, value);
                    break;
                case DATETIME:
                    long dateTime;

                    if (value instanceof Date) {
                        dateTime = ((Date)value).getTime();
                    } else {
                        dateTime = ((Number)value).longValue();
                    }

                    payload = String.format(NUMBER_PAYLOAD_FORMAT,
                            name, dateTime);
                    break;

                case URI:
                    if (value instanceof oracle.iot.client.ExternalObject) {
                        payload = String.format(PAYLOAD_FORMAT,
                            name, ((oracle.iot.client.ExternalObject)value).getURI());
                    }
                    break;
            }
        } else {
            // JSON null value is literal null
            payload = "{\"" + name + "\": null}";
        }

        return payload;

    }

    private void checkActionDataType(DeviceModelAttribute.Type type, String name, Object value)
        throws IllegalArgumentException {

        if (type != null) {
            switch(type) {
                case INTEGER:
                    if (!(value instanceof Number)) { // Integers are Numbers
                        throw new IllegalArgumentException(name + " requires an Integer or a Number argument");
                    }
                case NUMBER:
                    if (!(value instanceof Number)) {
                        throw new IllegalArgumentException(name + " requires a Number argument");
                    }
                    break;
                case STRING:
                    if (!(value instanceof String)) {
                        throw new IllegalArgumentException(name + " requires a String argument");
                    }
                    break;
                case BOOLEAN:
                    if (!(value instanceof Boolean)) {
                        throw new IllegalArgumentException(name + " requires a Boolean argument");
                    }
                    break;
                case DATETIME:
                    if (!(value instanceof Date) && !(value instanceof Long)) {
                        throw new IllegalArgumentException(name + " requires a Date or a Long argument");
                    }
                    break;
                case URI:
                    if (!(value instanceof oracle.iot.client.ExternalObject)) {
                        throw new IllegalArgumentException(name + " requires an ExternalObject argument");
                    }
                    break;
                default:
                    getLogger().log(Level.INFO, "unexpected type: " + type);
                    break;
            }
        }

    }

    /**
     * Execute a write-only action with the given data.
     * @param <T> the data type
     * @param actionName The name of the action
     * @param data The data to send to the action
     *
     */
    public <T> void call(final String actionName, final T data) {

        final DeviceModelImpl deviceModel = (DeviceModelImpl)getDeviceModel();
        DeviceModelAction dma = deviceModel.getDeviceModelActions().get(actionName);
        if (dma == null) {
            for (DeviceModelAction act : deviceModel.getDeviceModelActions().values()) {
                if (actionName.equals(act.getAlias())) {
                    dma = act;
                    break;
                }
            }
        }
        if (dma == null) {
            throw new IllegalArgumentException(actionName + "not found in device model");
        }

        final DeviceModelAction action = dma;
        final DeviceModelAttribute.Type argType = action.getArgType();
        checkActionDataType(argType, actionName, data);

        // With Controller
        final String action_format = isDeviceApp() ?
                DEVICE_APP_ACTION_FORMAT : DEVICE_ACTION_FORMAT;
        final String resource =
                String.format(action_format,
                        getEnterpriseClient().getApplication().getId(),
                        getEndpointId(),
                        getDeviceModel().getURN(),
                        action.getName());

        // be forgiving if the user passes data to an action that doesn't
        // take an argument.
        final T value = argType != null ? data : null;
        boolean syncFailed = false;
        if (argType == DeviceModelAttribute.Type.URI) {
            syncFailed = !syncStorageObject(actionName, value);
        }
        
        final String payload = getPayload(action.getArgType(), "value", value);
        
        if (syncFailed) {
            processOnError(this, actionName, payload, "Content sync failed.");
            return;
        }
        
        Controller.update(this, "POST", resource, payload,
                new Controller.Callback() {
                    public void onError(VirtualDevice virtualDevice,
                                        String resource, String payload,
                                        String reason) {
                        // on error, give user back the data they passed
                        processOnError(virtualDevice, actionName, data,
                            reason);
                    }
                });

    }
    
    private boolean syncStorageObject(String name, Object value) {
        StorageObjectImpl storageObject = (StorageObjectImpl)value;
        storageObject.setSyncEventInfo(this, name);
        storageObject.sync();
        return storageObject.getSyncStatus() == StorageObject.SyncStatus.IN_SYNC;
    }

    /**
     * Execute an action
     * @param actionName The name of the action
     */
    public void call(final String actionName) {
        final DeviceModelImpl deviceModel = (DeviceModelImpl)getDeviceModel();
        Map<String, DeviceModelAction> actionMap = deviceModel.getDeviceModelActions();
        DeviceModelAction action = actionMap.get(actionName);
        if (action == null) {
            for (DeviceModelAction act : actionMap.values()) {
                if (actionName.equals(act.getAlias())) {
                    action = act;
                    break;
                }
            }
        }
        if (action == null) {
            throw new IllegalArgumentException(actionName + " not found in device model");
        }

        final DeviceModelAttribute.Type argType = action.getArgType();
        if (argType != null) {
            throw new IllegalArgumentException(actionName + " requires " + argType + " argument");
        }

        // With Controller
        final String action_format = isDeviceApp() ?
                DEVICE_APP_ACTION_FORMAT : DEVICE_ACTION_FORMAT;
        // With Controller
        final String resource =
                String.format(action_format,
                        getEnterpriseClient().getApplication().getId(),
                        getEndpointId(),
                        getDeviceModel().getURN(),
                        action.getName());

        Controller.update(this, "POST", resource, "{}",
                new Controller.Callback() {
                    public void onError(VirtualDevice virtualDevice,
                                        String resource, String payload,
                                        String reason) {
                        processOnError(virtualDevice, actionName, null, reason);
                    }
                });
    }

    @Override
    public String toString() {
        return base.toString();
    }


    private class AlertEventImpl extends AlertEvent {
        final private VirtualDevice device;
        final private String urn;
        final private NamedValue<?> value;
        final private Date eventTime;

        private AlertEventImpl(VirtualDevice device, String urn, NamedValue<?> value,
                               Date eventTime) {
            this.device = device;
            this.urn = urn;
            this.value = value;
            this.eventTime = eventTime;
        }

        @Override
        public VirtualDevice getVirtualDevice() {return device;}

        @Override
        public NamedValue<?> getNamedValue() {return value;}

        @Override
        public Date getEventTime() {return eventTime;}

        @Override
        public String getURN() { return urn; }
    }

    private class DataEventImpl extends DataEvent {
        final private VirtualDevice device;
        final private String urn;
        final private NamedValue<?> value;
        final private Date eventTime;

        private DataEventImpl(VirtualDevice device, String urn, NamedValue<?> value,
                               Date eventTime) {
            this.device = device;
            this.urn = urn;
            this.value = value;
            this.eventTime = eventTime;
        }

        @Override
        public VirtualDevice getVirtualDevice() {return device;}

        @Override
        public NamedValue<?> getNamedValue() {return value;}

        @Override
        public Date getEventTime() {return eventTime;}

        @Override
        public String getURN() { return urn; }
    }

    /**
     * Performs a REST call to check if this virtualDevice is a
     * device.
     * @return {@code true} if this device can be found on the server,
     * {@code false} otherwise.
     */
    public boolean isDeviceApp() {

        if (isDeviceApp == null) {
			synchronized (isDeviceAppLock) {
				if (isDeviceApp == null) {
					try {
						String request = RestApi.V2.getReqRoot() + "/apps/" +
							enterpriseClient.getApplication().getId() +
							"/deviceApps?fields=type&q=" +
							URLEncoder.encode("{\"id\":{\"$eq\":\""+
							getEndpointId() + "\"}}", "UTF-8");
						isDeviceApp = false;

						JSONObject object = httpGet(secureConnection, request);

						// if the endpoint id is a device and not a deviceApp,
						// the list will be empty
						JSONArray items = object.optJSONArray("items");
						if (items != null && items.length() > 0) {
							JSONObject firstItem = items.optJSONObject(0);
							if (firstItem != null) {
								String type = firstItem.optString("type", "");
								isDeviceApp = "DEVICE_APPLICATION".equals(type);
							}
						}
					} catch (IOException ignored) {
					} catch (GeneralSecurityException ignored) {
					}
				}
			}
        }

        if (isDeviceApp == null) {
            return false;
        }

        return isDeviceApp;
    }

    private JSONObject httpGet(HttpSecureConnection secureConnection,
                               String request)
            throws IOException, GeneralSecurityException {


        HttpResponse res = secureConnection.get(request);
        int status = res.getStatus();
        if (status != StatusCode.OK.getCode()) {
            throw new IOException(res.getVerboseStatus("GET", request));
        }

        byte[] data = res.getData();
        if (data == null) {
            throw new IOException("GET " + request +
                " failed: no data received");
        }

        String json = new String(data, "UTF-8");
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            throw new IOException("GET " + request + ": " + e.getMessage());
        }
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
