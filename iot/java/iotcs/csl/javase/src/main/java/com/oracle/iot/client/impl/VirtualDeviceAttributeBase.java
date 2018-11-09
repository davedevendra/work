/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl;

import oracle.iot.client.AbstractVirtualDevice;
import com.oracle.iot.client.VirtualDeviceAttribute;
import com.oracle.iot.client.DeviceModelAttribute;

import java.nio.charset.Charset;

import java.util.Date;

/**
 * VirtualDeviceAttribute is an attribute in the virtualDevice model.
 */
public abstract class VirtualDeviceAttributeBase<V extends AbstractVirtualDevice<V>, T> extends VirtualDeviceAttribute<V, T> {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    protected final V virtualDevice;
    protected final DeviceModelAttribute<T> model;
    protected T value;
    protected T lastKnownValue;
    private AbstractVirtualDevice.ChangeCallback<V> changeCallback;
    private AbstractVirtualDevice.ErrorCallback<V> errorCallback;

    protected VirtualDeviceAttributeBase(V virtualDevice, DeviceModelAttribute<T> model) {
        this.virtualDevice = virtualDevice;
        this.model = model;
    }

    public DeviceModelAttribute<T> getDeviceModelAttribute() {
        return model;
    }

    /**
     * Same as set, but doesn't generate an event. Returns true if the value
     * was updated.
     * @param value the new value with which the attribute will be updated
     * @return true if the value was updated
     */
    public abstract boolean update(Object value);

    /**
     * Check if the attribute can be set. On the device-client, an attribute
     * should always be settable. On the enterprise-client, an attribute is
     * settable if the model says the attribute is writable.
     * @return true if the attribute can be set
     */
    public abstract boolean isSettable();

    /** {@inheritDoc} */
    @Override
    public T get() {
        return value != null ? value : getDeviceModelAttribute().getDefaultValue();
    }

    /** {@inheritDoc} */
    @Override
    public T getLastKnown() {
        return lastKnownValue;
    }

    /** {@inheritDoc} */
    @Override
    public void setOnChange(AbstractVirtualDevice.ChangeCallback<V> callback) {
        this.changeCallback = callback;
    }

    /**
     * Get the ChangeCallback for this attribute
     * @return ChangeCallback, which may be null
     */
    public AbstractVirtualDevice.ChangeCallback<V> getOnChange() {
       return changeCallback;
    }

    /** {@inheritDoc} */
    @Override
    public void setOnError(AbstractVirtualDevice.ErrorCallback<V> callback) {
        this.errorCallback = callback;
    }

    /**
     * Get the ErrorCallback for this attribute
     * @return ErrorCallback, which may be null
     */
    public AbstractVirtualDevice.ErrorCallback<V> getOnError() {
        return errorCallback;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("name : ").append(model.getName()).append("\n")
            .append("description : ").append(String.valueOf(model.getDescription())).append("\n")
            .append("alias : ").append(String.valueOf(model.getAlias())).append("\n")
            .append("model : ").append(String.valueOf(model.getModel())).append("\n")
            .append("access : ").append(String.valueOf(model.getAccess())).append("\n")
            .append("type : ").append(String.valueOf(model.getType())).append("\n");

        DeviceModelAttribute.Type modelType = model.getType();
        if (modelType == DeviceModelAttribute.Type.NUMBER || modelType == DeviceModelAttribute.Type.INTEGER) {
            sb.append("lowerBound : ")
                .append(String.valueOf(model.getLowerBound()))
                .append("\n")
                .append("upperBound : ")
                .append(String.valueOf(model.getUpperBound()))
                .append("\n");
        }

        sb.append("value : ")
                .append(String.valueOf(value))
                .append("\n")
                .append("lastKnownValue : ")
                .append(String.valueOf(lastKnownValue))
                .append("\n")
                .append("defaultValue : ")
                .append(String.valueOf(getDeviceModelAttribute().getDefaultValue()))
                .append("\n");

        return sb.toString();
    }

    /* @return true if the value is valid for the attribute */
    public void validate(DeviceModelAttribute attribute, Object value) {

        if (value == null) return;

        final DeviceModelAttribute.Type type = attribute.getType();

        // block assumes value is not null
        switch (type) {
            case INTEGER:
                if (!(value instanceof Integer)) {
                    throw new IllegalArgumentException("value is not INTEGER");
                }
                break;
            case NUMBER:
                if (!(value instanceof Number)) {
                    throw new IllegalArgumentException("value is not NUMBER");
                }
                break;
            case STRING:
                if (!(value instanceof String)) {
                    throw new IllegalArgumentException("value is not STRING");
                }
                break;
            case BOOLEAN:
                if (!(value instanceof Boolean)) {
                    throw new IllegalArgumentException("value is not BOOLEAN");
                }
                break;
            case DATETIME:
                if (!(value instanceof Date) && !(value instanceof Long)) {
                    throw new IllegalArgumentException("value is not DATETIME");
                }
                break;
            case URI:
                if (!(value instanceof oracle.iot.client.ExternalObject)) {
                    throw new IllegalArgumentException("value is not an ExternalObject");
                }
                break;
        }

        if (((type == DeviceModelAttribute.Type.INTEGER) || (type == DeviceModelAttribute.Type.NUMBER))) {
            // Assumption here is that lowerBound <= upperBound
            final double val = ((Number) value).doubleValue();
            if (attribute.getUpperBound() != null) {
                final double upper = attribute.getUpperBound().doubleValue();
                if (Double.compare(val, upper) > 0) {
                    throw new IllegalArgumentException(val + " > " + upper);
                }
            }
            if (attribute.getLowerBound() != null) {
                final double lower = attribute.getLowerBound().doubleValue();
                if(Double.compare(val, lower) < 0) {
                    throw new IllegalArgumentException(val + " < " + lower);
                }
            }
        }
    }
}
