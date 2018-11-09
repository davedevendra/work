/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl;

import com.oracle.iot.client.impl.util.Pair;
import oracle.iot.client.AbstractVirtualDevice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of VirtualDevice.
 */
public final class VirtualDeviceBase<V extends AbstractVirtualDevice<V>> {

    private volatile boolean updateMode;

    /** The attributes modified during an update */
    private List<Pair<VirtualDeviceAttributeBase<V, Object>, Object>> updatedAttributes;

    /** Lock for update changes */
    private final Object UPDATE_LOCK = new Object();

    private final Adapter<V> adapter;
    private final String endpointId;
    private final DeviceModelImpl deviceModel;

    private AbstractVirtualDevice.ChangeCallback<V> changeCallback;
    private AbstractVirtualDevice.ErrorCallback<V> errorCallback;

    /**
     * @param adapter the device or enterprise specific implementation
     *                of the Adapter interface
     * @param endpointId the server's endpoint id for the device
     * @param deviceModel the device model the device implements
     *
     */
    public VirtualDeviceBase(Adapter<V> adapter, String endpointId, DeviceModelImpl deviceModel) {
        this.adapter = adapter;
        this.endpointId = endpointId;
        this.deviceModel = deviceModel;
    }


    /**
     * An interface for adapting to the device or enterprise implementation
     * of an AbstractVirtualDevice
     * @param <V> The type of AbstractVirtualDevice
     */
    public interface Adapter<V extends AbstractVirtualDevice<V>> {

        /**
         * Sets the the value for attribute.
         * For enterprise client make a resource request to set the
         * the attribute. For device client calls the attribute's set method.
         * Invokes the on error handlers on error.
         * @param deviceAttribute the device attribute name
         * @param value the device attribute value
         */
        void setValue(VirtualDeviceAttributeBase<V, Object> deviceAttribute, Object value);

        /**
         * Return a VirtualDeviceAttribute appropriate for either the enterprise
         * or device client.
         * @param name the name of the attribute
         * @return The VirtualDeviceAttribute for the given name
         */
        VirtualDeviceAttributeBase<V, Object> getAttribute(String name);

        /**
         * Set all the attributes in an update batch. Errors are handled in the
         * set call, including calling the on error handler.
         * @param updatedAttributes The attributes modified during an update
         */
        void updateFields(List<Pair<VirtualDeviceAttributeBase<V, Object>, Object>> updatedAttributes);
    }

    public DeviceModelImpl getDeviceModel() {
        return deviceModel;
    }

    public String getEndpointId() {
        return endpointId;
    }

    public void set(String attributeName, Object value) {
        // Consider moving validation from adapter.setValue and pass VirtualDeviceAttribute instance.
        // Validate here for updateSetField.

        // getAttribute throws IllegalArgumentException if the attribute
        // is not in the model.
        VirtualDeviceAttributeBase<V, Object> df = adapter.getAttribute(attributeName);

        if (!df.isSettable()) {
            throw new UnsupportedOperationException("attempt to modify read-only attribute '" + attributeName + "'");
        }

        if (value instanceof Number && Double.isNaN(((Number)value).doubleValue())) {
            return;
        }

        // Will throw IllegalArgumentException
        df.validate(df.getDeviceModelAttribute(), value);

        if (!isUpdateMode()) {
            adapter.setValue(df, value);
        } else {
            updateSetField(df, value);
        }
    }

    public void update() {
        synchronized (UPDATE_LOCK) {
            if (updatedAttributes == null) {
                updatedAttributes = new ArrayList<Pair<VirtualDeviceAttributeBase<V, Object>, Object>>();
            }
            updateMode = true;
        }
    }

     public void finish() {
        synchronized (UPDATE_LOCK) {
            if (updatedAttributes != null && !updatedAttributes.isEmpty()) {
                List<Pair<VirtualDeviceAttributeBase<V, Object>, Object>> workingCopy =
                        new ArrayList<Pair<VirtualDeviceAttributeBase<V, Object>, Object>>(updatedAttributes.size());
                workingCopy.addAll(updatedAttributes);
                adapter.updateFields(workingCopy);
                updatedAttributes.clear();
            }
            updateMode = false;
        }
    }

    public synchronized void setOnChange(
            AbstractVirtualDevice.ChangeCallback<V> callback) {
        changeCallback = callback;
    }

    public synchronized AbstractVirtualDevice.ChangeCallback<V>
            getOnChangeCallback() {
        return changeCallback;
    }

    public synchronized void setOnError(
            AbstractVirtualDevice.ErrorCallback<V> callback) {
        errorCallback = callback;
    }

    public synchronized AbstractVirtualDevice.ErrorCallback<V>
            getOnErrorCallback() {
        return errorCallback;
    }

    /**
     * Return true if the device is in update mode.
     * @return true if in update mode else false
     */
    public boolean isUpdateMode() {
        synchronized(UPDATE_LOCK) {
            return updateMode;
        }
    }

    /**
     * Add attribute {@code attribute} to the set of attributes for batch update.
     * @param attribute the attribute being set
     * @param value the attribute value
     */
    private void updateSetField(VirtualDeviceAttributeBase<V, Object> attribute, Object value) {
        synchronized(UPDATE_LOCK) {
            updatedAttributes.add(new Pair<VirtualDeviceAttributeBase<V, Object>,Object>(attribute, value));
        }
    }

    @Override
    public String toString() {
        return "{" + endpointId + " , " + deviceModel.getURN() + "}";

    }
    public static class NamedValueImpl<T> extends AbstractVirtualDevice.NamedValue<T> {

        final private String name;
        final private T value;
        private AbstractVirtualDevice.NamedValue<?> next;

        public NamedValueImpl(String name, T value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public T getValue() {
            return value;
        }

        @Override
        public AbstractVirtualDevice.NamedValue<?> next() {
            return next;
        }

        public void setNext(AbstractVirtualDevice.NamedValue<?> next) {
            this.next = next;
        }

    }
    public static class ChangeEvent<V extends AbstractVirtualDevice>
            extends AbstractVirtualDevice.ChangeEvent<V> {

        private final V virtualDevice;
        private final AbstractVirtualDevice.NamedValue<?> value;

        public ChangeEvent(V virtualDevice, AbstractVirtualDevice.NamedValue<?> value) {
            this.virtualDevice = virtualDevice;
            this.value = value;
        }

        @Override
        public V getVirtualDevice() {return virtualDevice;}

        @Override
        public AbstractVirtualDevice.NamedValue<?> getNamedValue() {return value;}
    }

    public static class ErrorEvent<V extends AbstractVirtualDevice>
            extends AbstractVirtualDevice.ErrorEvent<V> {
        private final V virtualDevice;
        private final AbstractVirtualDevice.NamedValue<?> value;
        private final String message;

        public ErrorEvent(V virtualDevice, AbstractVirtualDevice.NamedValue<?> value, String message) {
            this.virtualDevice = virtualDevice;
            this.value = value;
            this.message = message;
        }

        @Override
        public V getVirtualDevice() {return virtualDevice;}

        @Override
        public AbstractVirtualDevice.NamedValue<?> getNamedValue() {return value;}

        @Override
        public String getMessage() {return message;}
    }

}
