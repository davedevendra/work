/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import com.oracle.iot.client.DeviceModelFormat;
import com.oracle.iot.client.impl.DeviceModelImpl;
import com.oracle.iot.client.message.AlertMessage;
import com.oracle.iot.client.message.Message.Reliability;
import oracle.iot.client.AbstractVirtualDevice.ErrorCallback;
import oracle.iot.client.DeviceModel;
import oracle.iot.client.StorageObject;
import oracle.iot.client.device.Alert;
import oracle.iot.client.device.VirtualDevice;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AlertImpl
 */
public class AlertImpl extends Alert {

    private final VirtualDeviceImpl virtualDevice;
    private final DeviceModelFormat deviceModelFormat;
    private final Map<String, Object> fieldValues;
    private ErrorCallback<VirtualDevice> errorCallback;

    public AlertImpl(VirtualDevice virtualDevice, String alertUrn) {

        if (!(virtualDevice instanceof VirtualDeviceImpl)) {
            throw new IllegalArgumentException("expected: " + VirtualDeviceImpl.class.getName());
        }

        this.deviceModelFormat = getDeviceModelFormat(virtualDevice, alertUrn);
        
        if (this.deviceModelFormat == null) {
            throw new IllegalArgumentException("'" + alertUrn + "' not found");
        }
        
        if (this.deviceModelFormat.getType() != DeviceModelFormat.Type.ALERT) {
            throw new IllegalArgumentException(
                    "'" + alertUrn + "' is not an alert format"
            );
        }
        
        this.virtualDevice = (VirtualDeviceImpl) virtualDevice;
        this.fieldValues = new HashMap<String, Object>();
    }

    @Override
    public <T> Alert set(String fieldName, T value) {

        DeviceModelFormat.Field field = getField(fieldName);
        if (field == null) {
            throw new IllegalArgumentException(fieldName + " not in model");
        }

        switch (field.getType()) {
            case NUMBER:
                if (!(value instanceof Number)) {
                    throw new IllegalArgumentException("value for '" + fieldName + "' is not a NUMBER");
                }
                break;
            case INTEGER:
                if (!(value instanceof Integer)) {
                    throw new IllegalArgumentException("value for '" + fieldName + "' is not an INTEGER");
                }
                break;
            case DATETIME:
                if (!(value instanceof Date) && !(value instanceof Long)) {
                    throw new IllegalArgumentException("value for '" + fieldName + "' is not a DATETIME");
                }
                break;
            case BOOLEAN:
                if (!(value instanceof Boolean)) {
                    throw new IllegalArgumentException("value for '" + fieldName + "' is not a BOOLEAN");
                }
                break;
            case STRING:
                if (!(value instanceof String)) {
                    throw new IllegalArgumentException("value for '" + fieldName + "' is not a STRING");
                }
                break;
            case URI:
                if (!(value instanceof oracle.iot.client.ExternalObject)) {
                    throw new IllegalArgumentException("value for '" + fieldName + "' is not an ExternalObject");
                }
                break;
        }

        fieldValues.put(fieldName, value);

        return this;
    }

    @Override
    public void raise() {
        AlertMessage.Builder builder = new AlertMessage.Builder();
        List<DeviceModelFormat.Field> fieldList = deviceModelFormat.getFields();
        StorageObjectImpl storageObject = null;
        for (DeviceModelFormat.Field field : fieldList) {
            final String key = field.getName();
            Object value = fieldValues.remove(key);
            if (value != null) {
                switch (field.getType()) {
                    case NUMBER:
                        builder.dataItem(key, ((Number) value).doubleValue());
                        break;
                    case INTEGER:
                        builder.dataItem(key, (Integer) value);
                        break;
                    case DATETIME:
                        if (value instanceof Date) {
                            builder.dataItem(key, (double) ((Date) value).getTime());
                        } else if (value instanceof Long) {
                            builder.dataItem(key, (Long) value);
                        }
                        break;
                    case BOOLEAN:
                        builder.dataItem(key, (Boolean) value);
                        break;
                    case STRING:
                        builder.dataItem(key, (String) value);
                        break;
                    case URI:
                        if (value instanceof StorageObjectImpl) {
                            storageObject = (StorageObjectImpl)value;
                            if ((storageObject.getSyncStatus() == StorageObject.SyncStatus.NOT_IN_SYNC) ||
                                    (storageObject.getSyncStatus() == StorageObject.SyncStatus.SYNC_PENDING)) {
                            }
                            storageObject.setSyncEventInfo(virtualDevice, key);
                        }
                        builder.dataItem(key, ((oracle.iot.client.ExternalObject)value).getURI());
                        break;
                    default:
                        getLogger().log(Level.INFO, "unknown type: " + field.getType());
                }

            } else if (!field.isOptional()) {
                fieldValues.clear();
                throw new IllegalStateException("non-optional field '" + key + "' not set");
            }
        }

        fieldValues.clear();

        builder.format(deviceModelFormat.getURN())
                .description(deviceModelFormat.getDescription())
                .source(virtualDevice.getEndpointId())
                .reliability(Reliability.GUARANTEED_DELIVERY);

        AlertMessage message = builder.build();

        try {
            // MessageDispatcherImpl ends up calling VirtualDeviceImpl.ErrorCallbackBridge
            // if the message fails to be sent. Here, we tell VirtualDeviceImpl about
            // the callback for this message so that the ErrorCallbackBridge
            // can invoke it.
            if (errorCallback != null) {
                VirtualDeviceImpl.addOnErrorCallback(message.getClientId(), errorCallback);
            }

            virtualDevice.queueMessage(message, storageObject);
        } catch (ArrayStoreException e) {
            throw e;
        } catch(Throwable t) {
            getLogger().log(Level.SEVERE, t.toString());
        }
    }

    @Override
    public void setOnError(ErrorCallback<VirtualDevice> callback) {
        final ErrorCallback<VirtualDevice> oldCallback = this.errorCallback;
        this.errorCallback = callback;
        VirtualDeviceImpl.replaceOnErrorCallback(oldCallback, this.errorCallback);
    }

    private DeviceModelFormat.Field getField(String fieldName) {
        
        if (deviceModelFormat == null) return null;
        
        List<DeviceModelFormat.Field> fieldList = deviceModelFormat.getFields();
        for(DeviceModelFormat.Field field : fieldList) {
            if (field.getName().equals(fieldName)) {
                return field;
            }
        }
        return null;
    }
    
    private static DeviceModelFormat getDeviceModelFormat(
            VirtualDevice virtualDevice,
            String alertUrn) {

        DeviceModel dm = virtualDevice.getDeviceModel();
        if (!(dm instanceof DeviceModelImpl)) return null;
        
        DeviceModelImpl deviceModel = (DeviceModelImpl)dm;
        return deviceModel.getDeviceModelFormats().get(alertUrn);
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
