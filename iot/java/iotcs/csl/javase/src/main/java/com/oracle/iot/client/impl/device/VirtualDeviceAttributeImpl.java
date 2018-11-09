/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import com.oracle.iot.client.DeviceModelAttribute;
import com.oracle.iot.client.impl.VirtualDeviceAttributeBase;
import oracle.iot.client.device.VirtualDevice;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * VirtualDeviceAttribute is an attribute in the device model.
 */
public final class VirtualDeviceAttributeImpl<T> extends
        VirtualDeviceAttributeBase<VirtualDevice, T> {

    public VirtualDeviceAttributeImpl(VirtualDeviceImpl device, DeviceModelAttribute model) {
        super(device, model);
    }

    public DeviceModelAttribute getDeviceModelAttribute() {
        return model;
    }

    /** {@inheritDoc} */
    @Override
    public void set(T value) {

        if (value instanceof Number && Double.isNaN(((Number)value).doubleValue())) {
            return;
        }

        // validate throws IllegalArgumentException if value is not valid
        validate(model, value);

        if (getLogger().isLoggable(Level.FINEST)) {
            getLogger().log(Level.FINEST,
                    "\nVirtualDevice: " + virtualDevice.toString() +
                            "\n\t attributeName " + getDeviceModelAttribute().getName() +
                            "\n\t attributeValue " + this.value +
                            "\n\t newValue " + value  +
                            "\n"
            );
        }

        this.lastKnownValue = this.value = value;

        // This may set up an infinite loop!
        ((VirtualDeviceImpl) virtualDevice).processOnChange(this, this.value);
    }

    /** {@inheritDoc} */
    @Override
    public boolean update(Object value) {

        if (value instanceof Number && Double.isNaN(((Number)value).doubleValue())) {
            return false;
        }

        // validate throws IllegalArgumentException if value is not valid
        validate(model, value);

        if (getLogger().isLoggable(Level.FINEST)) {
            getLogger().log(Level.FINEST,
                    "\nVirtualDevice: " + virtualDevice.toString() +
                            "\n\t attributeName " + getDeviceModelAttribute().getName() +
                            "\n\t attributeValue " + this.value +
                            "\n\t newValue " + value +
                            "\n"

            );
        }

        this.lastKnownValue = this.value = (T)value;

        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj.getClass() != this.getClass()) return false;
        VirtualDeviceAttributeImpl other = (VirtualDeviceAttributeImpl)obj;

        if (this.value != null ? !this.value.equals(other.value) : other.value != null) return false;
        return this.getDeviceModelAttribute().equals(((VirtualDeviceAttributeImpl) obj).getDeviceModelAttribute());
    }

    @Override
    public int hashCode() {
        int hash = 37;
        hash = 37 * hash + (this.value != null ? this.value.hashCode() : 0);
        hash = 37 *  this.getDeviceModelAttribute().hashCode();
        return hash;
    }


    @Override
    public boolean isSettable() {
        // An attribute is always settable on the device-client side
        return true;
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
