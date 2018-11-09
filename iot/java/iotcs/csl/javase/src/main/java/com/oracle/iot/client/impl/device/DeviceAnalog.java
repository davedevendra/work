/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import com.oracle.iot.client.message.Message;
import oracle.iot.client.DeviceModel;

/**
 * DeviceAnalog combines a device (endpoint id and attributes) with a model.
 */
public interface DeviceAnalog {

    /**
     * Get the endpoint id of the device
     * @return the device endpoint id
     */
    String getEndpointId();

    /**
     * Get the device model of the device.
     * @return the device model
     */
    DeviceModel getDeviceModel();

    /**
     * Set the named attribute to the given value.
     * @param attribute the attribute to set
     * @param value the value of the attribute
     * @throws IllegalArgumentException if the attribute is not in the device model,
     * the value is {@code null}, or the value does not match the attribute type.
     */
    void setAttributeValue(String attribute, Object value);

    /**
     * Get the value of the named attribute.
     * @param attribute the attribute to get
     * @return the value of the attribute, possibly {@code null}
     */
    Object getAttributeValue(String attribute);

    /**
     * Invoke an action.
     * @param actionName the name of the action to call
     * @param data the data, possibly {@code null} or empty, to pass to the action
     */
    void call(String actionName, Object... data);

    /**
     * Queue a message for dispatch to the server. The implementation of
     * this method may send the message directly without queuing.
     * @param message the message to be queued
     */
    void queueMessage(Message message); // TODO: this API doesn't really fit here, but needed for alertCondition

}
