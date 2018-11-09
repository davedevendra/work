/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client;

import oracle.iot.client.AbstractVirtualDevice;

/**
 * VirtualDeviceAttribute is an attribute in a VirtualDevice model. The
 * {@link AbstractVirtualDevice#get(String)} and {@link AbstractVirtualDevice#set(String, Object)} methods
 * are wrappers around the {@code VirtualDeviceAttribute} instances of a {@code VirtualDevice}.
 */
public abstract class VirtualDeviceAttribute<V extends AbstractVirtualDevice<V>, T> {

    /**
     * Get the value of an attribute.
     * This method returns the current value
     * held by the virtual device model. For an enterprise client, no
     * REST API call is made to the server. For a device client, no call is
     * made to the physical device.
     * @return the attribute value
     * @throws ClassCastException if the attribute value is not the correct return type.
     */
    public abstract T get();

    /**
     * Get the last&ndash;known value of an attribute.
     * This method returns the last&ndash;known value of the attribute, which
     * is not to be interpreted as the &quot;previous&quot; value.
     * For a device client, the last&ndash;known value is the device value.
     * For an enterprise client, the last&ndash;known value is the last value
     * retrieved from the server for a specific attribute, irrespective of the local
     * 'set' value.
     * @return the last&ndash;known attribute value
     * @throws ClassCastException if the attribute value is not the correct return type.
     */
    public abstract T getLastKnown();

    /**
     * Set the value of an attribute.
     * This method is used by the application
     * to synchronize the {@code VirtualDevice} state with the physical device.
     * This results in a REST API call to the server. For an enterprise
     * client, an endpoint resource REST API call is made to the server. For
     * a device client, an endpoint DATA message REST API call is made to the
     * server.
     * The value is validated according to the constraints in the device model.
     * If the value is not valid, an IllegalArgumentException is raised.
     * @param value the value to set
     * @throws IllegalArgumentException if the value is not valid
     */
    public abstract void set(T value);

    /**
     * Set a callback that is invoked when the value of an attribute changes on the
     * remote virtual device. For a device client, the
     * @param callback a callback to invoke when an attribute value changes,
     * if {@code null}, the existing callback will be removed
     * @see AbstractVirtualDevice#setOnChange(AbstractVirtualDevice.ChangeCallback)
     */
    public abstract void setOnChange(AbstractVirtualDevice.ChangeCallback<V> callback);


    /**
     * Set a callback that is invoked when there is an error in setting the
     * value of an attribute. An error may arise because of a network exception.
     * In addition, on an enterprise client, an error indicates that a
     * request timed out or was rejected by the device client.
     *
     * @param callback a callback to invoke when an attribute value changes,
     * if {@code null}, the existing callback will be removed
     * @see AbstractVirtualDevice#setOnError(AbstractVirtualDevice.ErrorCallback)
     */
    public abstract void setOnError(AbstractVirtualDevice.ErrorCallback<V> callback);


    protected VirtualDeviceAttribute() {}

}
