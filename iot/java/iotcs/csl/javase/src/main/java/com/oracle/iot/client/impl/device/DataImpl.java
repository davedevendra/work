/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import com.oracle.iot.client.DeviceModelFormat;
import com.oracle.iot.client.impl.DeviceModelImpl;
import com.oracle.iot.client.message.DataMessage;
import com.oracle.iot.client.device.util.MessageDispatcher;
import oracle.iot.client.AbstractVirtualDevice.ErrorCallback;
import oracle.iot.client.DeviceModel;
import oracle.iot.client.StorageObject;
import oracle.iot.client.device.Data;
import oracle.iot.client.device.VirtualDevice;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * DataImpl
 */
public class DataImpl extends Data {

    private final VirtualDeviceImpl virtualDevice;
    private final DeviceModelFormat deviceModelFormat;
    private final Map<String, Object> fieldValues;
    private ErrorCallback<VirtualDevice> errorCallback;

    public DataImpl(VirtualDevice virtualDevice, String dataUrn) {

        if (!(virtualDevice instanceof VirtualDeviceImpl)) {
            throw new IllegalArgumentException("expected: " + VirtualDeviceImpl.class.getName());
        }

        this.deviceModelFormat = getDeviceModelFormat(virtualDevice, dataUrn);
        
        if (this.deviceModelFormat == null) {
            throw new IllegalArgumentException("'" + dataUrn + "' not found");
        }
        
        if (this.deviceModelFormat.getType() != DeviceModelFormat.Type.DATA) {
            throw new IllegalArgumentException(
                    "'" + dataUrn + "' is not an data format"
            );
        }
        
        this.virtualDevice = (VirtualDeviceImpl) virtualDevice;
        this.fieldValues = new HashMap<String, Object>();
    }

    @Override
    public <T> Data set(String fieldName, T value) {

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
    public void submit() {
        DataMessage.Builder builder = new DataMessage.Builder();
        List<DeviceModelFormat.Field> fieldList = deviceModelFormat.getFields();
        List<StorageObjectImpl> storageObjects = new ArrayList<StorageObjectImpl>();
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
                            StorageObjectImpl storageObjectImpl = (StorageObjectImpl)value;
                            if ((storageObjectImpl.getSyncStatus() == StorageObject.SyncStatus.NOT_IN_SYNC) || 
                                    (storageObjectImpl.getSyncStatus() == StorageObject.SyncStatus.SYNC_PENDING)) {
                                storageObjects.add(storageObjectImpl);
                            }
                            storageObjectImpl.setSyncEventInfo(virtualDevice, key);
                            storageObjectImpl.sync();
                        }
                        builder.dataItem(key, ((oracle.iot.client.ExternalObject)value).getURI());
                        break;
                    default:
                        getLogger().info("unknown type: " + field.getType());
                }

            } else if (!field.isOptional()) {
                fieldValues.clear();
                throw new IllegalStateException("non-optional field '" + key + "' not set");
            }
        }

        fieldValues.clear();

        builder.format(deviceModelFormat.getURN())
                .source(virtualDevice.getEndpointId());

        DataMessage message = builder.build();

        MessageDispatcherImpl messageDispatcher =
            (MessageDispatcherImpl)MessageDispatcher.getMessageDispatcher(
                virtualDevice.directlyConnectedDevice);
        try {
            for (StorageObjectImpl so:storageObjects) {
                messageDispatcher.addStorageObjectDependency(so, message.getClientId());
            }
            // MessageDispatcherImpl ends up calling VirtualDeviceImpl.ErrorCallbackBridge
            // if the message fails to be sent. Here, we tell VirtualDeviceImpl about
            // the callback for this message so that the ErrorCallbackBridge
            // can invoke it.
            if (errorCallback != null) {
                VirtualDeviceImpl.addOnErrorCallback(message.getClientId(), errorCallback);
            }
            messageDispatcher.queue(message);
        } catch(Throwable t) {
            getLogger().severe(t.toString());
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
            String dataUrn) {

        DeviceModel dm = virtualDevice.getDeviceModel();
        if (!(dm instanceof DeviceModelImpl)) return null;
        
        DeviceModelImpl deviceModel = (DeviceModelImpl)dm;
        return deviceModel.getDeviceModelFormats().get(dataUrn);
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
