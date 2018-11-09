/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.enterprise;

import com.oracle.iot.client.DeviceModelAttribute;
import com.oracle.iot.client.impl.VirtualDeviceAttributeBase;
import oracle.iot.client.enterprise.VirtualDevice;

/**
 * VirtualDeviceAttribute is an attribute in the device model.
 */
final class VirtualDeviceAttributeImpl<T> extends VirtualDeviceAttributeBase<VirtualDevice, T> {

    VirtualDeviceAttributeImpl(VirtualDeviceImpl device, DeviceModelAttribute model) {
        super(device, model);
    }

    VirtualDeviceAttributeImpl(VirtualDeviceImpl device, DeviceModelAttribute model, T value) {
        super(device, model);
        validate(model, value);
        this.value = value;
        this.lastKnownValue = value;
    }

    // IOT-11791 - This method should trigger a network request
    // for Enterprise clients. However doing so in the current 
    // implementation will create an infinite loop of 
    // cloud service request -> set device -> send data message ->
    // poll for device messages -> set attribute -> cloud service request ->...
    //
    // In this implementation only VirtualDevice.set triggers a network
    // request and this method is restricted to setting the local value
    // for Enterprise clients.
    /**
     * {@inheritDoc}
     */
    @Override
    public void set(T value) {
        validate(this.getDeviceModelAttribute(), value);
        ((VirtualDeviceImpl)virtualDevice).remoteSet(this, value);
    }

    @Override
    public boolean update(Object value) {
        validate(this.getDeviceModelAttribute(), value);

        // Only change if there is a change in value and only then
        // call the on change handler. This because the way we poll
        // the server for attribute changes generates duplicate responses
        if ((this.value != null && !this.value.equals(value)) ||
                (value != null && !value.equals(this.value))) {

            this.lastKnownValue = this.value;
            this.value = (T)value;

            return true;
        }
        return false;
    }

    @Override
    public boolean isSettable() {
        // An attribute should never be EXECUTABLE, so no check is made here
        return getDeviceModelAttribute().getAccess() != DeviceModelAttribute.Access.READ_ONLY;
    }

}
