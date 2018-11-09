/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package oracle.iot.client.device;

import oracle.iot.client.AbstractVirtualDevice;

/**
 * VirtualDevice for a device-client adds methods to handle write-only and
 * executable actions.
 */
public abstract class VirtualDevice extends AbstractVirtualDevice<VirtualDevice> {

    /**
     * Callback interface for actions in the device model
     * @param <T> the type of data for a write-only action,
     *           or Void for an execute action.
     */
    public interface Callable<T> {
        /**
         * The method called for handling a device model action.
         * For an execute action, the generic type should be {@code Void}.
         * The client library will pass {@code null} as the {@code data}
         * parameter value if the action is executable. For a write-only action,
         * the generic type should match the expected data type of the action.
         *  @param virtualDevice the VirtualDevice on which the action is
         *                      being invoked
         * @param data the data
         */
        void call(VirtualDevice virtualDevice, T data);
    }

    /**
     * Set callback for handling an action
     * @param actionName The action name
     * @param callable The interface to invoke
     */
    public abstract void setCallable(String actionName, Callable<?> callable);

    /**
     * Create an Alert for this VirtualDevice. The alert will be created with
     * the given format URN.
     * @param format the alert format URN.
     * @return an Alert
     */
    public abstract Alert createAlert(String format);

    /**
     * Create a custom data object for this VirtualDevice.
     * The custom data object will be created with the given format URN.
     * @param format the custom data message format URN.
     * @return a custom data object
     */
    public abstract Data createData(String format);

    /**
     * Offer to set the value of an attribute. The attribute
     * value is set depending upon any policy that may have been
     * configured for the attribute. If there is no policy for the
     * given attribute, offer behaves as if the
     * {@link AbstractVirtualDevice#set(String, Object) set} method were called.
     * The value is validated according to the constraints in the device model.
     * If the value is not valid, an IllegalArgumentException is raised.
     * @param <T> the type of the attribute value
     * @param attributeName the name of an attribute from the device type model
     * @param value the value to set
     * @return this VirtualDevice instance
     * @throws IllegalArgumentException if the value is not valid,
     * the attribute is not in the model,
     * or {@code attributeName} is {@code null}
     * @throws UnsupportedOperationException if the attribute is read-only
     */
    public abstract <T> VirtualDevice offer(String attributeName, T value);

    /**
     * Compiler-required constructor for derived classes.
     */
    protected VirtualDevice() { super(); }

}
