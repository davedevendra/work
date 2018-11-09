/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package oracle.iot.client;

/**
 * AbstractVirtualDevice is a representation of a device model implemented by a
 * device. A device model is a specification of the attributes, formats,
 * and resources available on the device.
 * <p>
 *     The AbstractVirtualDevice API is common to both the enterprise client and the
 *     device client. The semantics of the API are also the same. The processing
 *     model on the enterprise client is different, however, from the processing
 *     model on the device client.
 * <p>
 *     On the enterprise client, an enterprise application calls the
 *     {@code AbstractVirtualDevice} set methods in order to affect a change on
 *     the physical device. The enterprise client application receives
 *     confirmation of that the change was accepted via a
 *     {@link ChangeCallback ChangeCallback}.
 *     The {@code ChangeCallback} indicates to the enterprise client that
 *     the value of the attribute on the device has changed. This callback may
 *     come as a result of the enterprise client calling {@code set} on the
 *     attribute, or it may come unsolicited as a result of the device-client
 *     updating the value from the physical device.
 * <p>
 *     The enterprise client may also receive an
 *     {@link ErrorCallback ErrorCallback}
 *     if the attribute value change was not accepted. This could be because the
 *     request timed out, the connection failed, or the change was explicitly
 *     rejected. The {@code ErrorCallback} indicates to the enterprise client
 *     that it should roll-back the attribute value to the last known value.
 * <p>
 *     On the device client, the application will call {@code set} on a
 *     {@code AbstractVirtualDevice} attribute. This will cause a message to
 *     be sent to the server.  The {@code ErrorCallback} is invoked if there
 *     is an error sending the message to the server.
 *     Any enterprise application that is monitoring the device
 *     will be notified via the {@code ChangeCallback} that the value has
 *     changed. When the client-library receives a request from the server, the
 *     request is forwarded to a handler which invokes the
 *     {@link ChangeCallback ChangeCallback} on
 *     the requested attribute. A {@code GET} request simply calls {@code get()}
 *     on the attribute.
 * @param <V> the type of the AbstractVirtualDevice instance
 */
public abstract class AbstractVirtualDevice<V extends AbstractVirtualDevice<V>> {

    /**
     * A name-value pair in an event. Typically, the name is the name of
     * an attribute, and value is the attribute's value. But a name-value
     * pair could also be the name of a field in a format, or the name of
     * an action.
     * @param <T> the type of the value
     */
    public static abstract class NamedValue<T> {

        /**
         * Get the name.
         * @return the name
         */
        public abstract String getName();

        /**
         * Get the value.
         * @return the value
         */
        public abstract T getValue();

        /**
         * Get the next name-value pair in the event. This method
         * returns {@code null} if there are no more name-value pairs.
         * @return the next name-value pair, or {@code null}
         */
        public abstract NamedValue<?> next();
    }

    /**
     * An event passed to a callback.
     * @param <V> the type of AbstractVirtualDevice
     */
    public static abstract class Event<V> {
        /**
         * Get the virtual device that is the source of the event
         * @return the virtual device, never {@code null}
         */
        public abstract V getVirtualDevice();

        /**
         * Get the name-value pair from the event.
         * @return the value, never {@code null}
         * @see AbstractVirtualDevice.ChangeCallback
         * @see AbstractVirtualDevice.ErrorCallback
         */
        public abstract NamedValue<?> getNamedValue();
    }

    /**
     * An event passed to the {@code onChange} callback to
     * indicate one or more attribute values have changed.
     * @param <V> the type of AbstractVirtualDevice
     * @see AbstractVirtualDevice.ChangeCallback
     * @see AbstractVirtualDevice.Event
     */
    public static abstract class ChangeEvent<V> extends Event<V> {
    }

    /**
     * An event passed to the {@code onError} callback to indicate an
     * error has occurred when setting one or more attributes.
     * @param <V> the type of AbstractVirtualDevice
     * @see AbstractVirtualDevice.ErrorCallback
     * @see AbstractVirtualDevice.Event
     */
    public static abstract class ErrorEvent<V> extends Event<V> {

        /**
         * Get the error message.
         * @return An error message
         */
        public abstract String getMessage();
    }

    /**
     * A callback interface for receiving an event when the value
     * of an attribute has changed.
     * @param <V> the type of AbstractVirtualDevice
     * @see #setOnChange(ChangeCallback)
     */
    public interface ChangeCallback<V> {
        /**
         * Callback for receiving an event when the value of an attribute
         * has changed. This callback will never be invoked by the
         * client-library with a {@code null} argument.
         * If an exception is thrown in a device client context, the new
         * value will not be set.
         * <p>
         * The key-value pairs of the {@code Map<String,Object>} parameter
         * are the name of the attribute and the attribute's new value.
         * @param event The data from the change event
         */
        void onChange(ChangeEvent<V> event);
    }

    /**
     * A callback interface for receiving notification of an error when setting
     * the value of an attribute.
     * @param <V> the type of AbstractVirtualDevice
     * @see #setOnError(ErrorCallback)
     */
    public interface ErrorCallback<V> {
        /**
         * Callback for receiving an event when there is an error setting
         * the value of an attribute. This callback will never be invoked by the
         * client-library with a {@code null} argument.
         * @param event The error event
         */
        void onError(ErrorEvent<V> event);
    }

    /**
     * Get the device model of this device object.
     * @return the device model
     */
    public abstract DeviceModel getDeviceModel();

    /**
     * Get the endpoint id of the device. This is the identifier that
     * the server creates when a device that has been registered with the
     * server is activated.
     * @return the device's endpoint id
     */
    public abstract String getEndpointId();

    /**
     * Get the value of an attribute.
     * This method returns the current value
     * held by the virtual device model. For an enterprise client, no
     * REST API call is made to the server. For a device client, no call is
     * made to the physical device.
     * @param <T> the type of the attribute value
     * @param attributeName the name of an attribute from the device type model
     * @return the attribute value
     * @throws ClassCastException if the attribute is not the correct return type
     * @throws IllegalArgumentException if the attribute is not in the model,
     * or {@code attributeName} is {@code null}
     */
    public abstract <T> T get(String attributeName);

    /**
     * Get the last known value of an attribute.
     * This method returns the last known value of an attribute.
     * <p>
     * In the case where the enterprise client sets the value of an attribute,
     * the last known value is the value before an
     * {@link #setOnChange(ChangeCallback) onChange}
     * event is received for the attribute. In the event the attribute value
     * could not be set, the current value is rolled back to the last known value.
     * <p>
     * For a device client, the last known value is always the same as the
     * current value. The last known value is held by the AbstractVirtualDevice instance.
     * No REST API call is made to the server.
     * @param <T> the type of the attribute value
     * @param attributeName the name of an attribute from the device type model
     * @return the attribute value
     * @throws ClassCastException if the attribute is not the correct return type
     * @throws IllegalArgumentException if the attribute is not in the model,
     * or {@code attributeName} is {@code null}
     */
    public abstract <T> T getLastKnown(String attributeName);

    /**
     * Set the value of an attribute.
     * This method is used by the application
     * to synchronize the {@code AbstractVirtualDevice} state with the physical device.
     * This results in a REST API call to the server. For an enterprise
     * client, an endpoint resource REST API call is made to the server. For
     * a device client, an endpoint DATA message REST API call is made to the
     * server.
     * The value is validated according to the constraints in the device model.
     * If the value is not valid, an IllegalArgumentException is raised.
     * @param <T> the type of the attribute value
     * @param attributeName the name of an attribute from the device type model
     * @param value the value to set
     * @return this AbstractVirtualDevice instance
     * @throws IllegalArgumentException if the value is not valid,
     * the attribute is not in the model,
     * or {@code attributeName} is {@code null}
     * @throws UnsupportedOperationException if the attribute is read-only
     */
    public abstract <T> V set(String attributeName, T value);

    /**
     * Set a mark for beginning an update transaction. By default, each call to
     * &quot;set&quot; automatically sends the value
     * to the server. The {@code update} call allows more than one
     * value to be set on this {@code AbstractVirtualDevice} object without
     * sending values to the server. The values are sent to the server when the
     * {@code finish()} method is called, which also marks the end of the
     * update transaction.
     * For example<br>
     * {@code
     * virtualDevice.update().set("min", 10).set("max", 20).finish();
     * }
     * @return this AbstractVirtualDevice instance
     * @see #finish()
     */
    public abstract V update();

    /**
     * Causes the values set in an update transaction to be sent to the
     * server and clears the update-transaction mark.
     * @see #update()
     */
    public abstract void finish();

    /**
     * Set a callback that is invoked when the value of the given attribute in the
     * device model changes. The parameterized type of the ChangeCallback should
     * match the data type of the attribute. The callback may be {@code null}.
     * <p>
     * Note that it is possible to set a callback for a specific attribute and
     * to set a callback for all attributes via {@link #setOnChange(AbstractVirtualDevice.ChangeCallback)}.
     * If there is a callback for the specific attribute and for all attributes,
     * both callbacks will be invoked.
     * @param attributeName the name of an attribute from the device type model
     * @param callback a callback to invoke when an attribute value changes,
     * if {@code null}, the existing callback will be removed
     * @throws IllegalArgumentException if the attribute is not in the model,
     * or {@code attributeName} is {@code null}
     * @see #setOnChange(AbstractVirtualDevice.ChangeCallback)
     */
    public abstract void setOnChange(String attributeName, ChangeCallback<V> callback);

    /**
     * Set a callback that is invoked when the value of any attribute in the
     * device model changes. The callback may be {@code null}.
     * <p>
     * Note that it is possible to set a callback for all attributes and to set
     * a callback for a specific attribute via {@link #setOnChange(String,AbstractVirtualDevice.ChangeCallback)}.
     * If there is a callback for the specific attribute and for all attributes,
     * both callbacks will be invoked.
     * @param callback a callback to invoke when an attribute value changes,
     * if {@code null}, the existing callback will be removed
     * @see #setOnChange(String, AbstractVirtualDevice.ChangeCallback)
     */
    public abstract void setOnChange(ChangeCallback<?> callback);

    /**
     * Set a callback that is invoked if an error occurs when setting the
     * value of the given attribute in the device model. The parameterized
     * type of the {@code ErrorCallback} should match the data type of the attribute.
     * The callback may be {@code null}.
     * <p>
     * Note that it is possible to set a callback for a specific attribute and
     * to set a callback for all attributes via {@link #setOnError(AbstractVirtualDevice.ErrorCallback)}.
     * If there is a callback for the specific attribute and for all attributes,
     * both callbacks will be invoked.
     * @param attributeName the name of an attribute from the device model
     * @param callback a callback to invoke when there is an error setting the
     * attribute value, if {@code null}, the existing callback will be removed
     * @throws IllegalArgumentException if the attribute is not in the model,
     * or {@code attributeName} is {@code null}
     *
     * @see #setOnError(AbstractVirtualDevice.ErrorCallback)
     */
    public abstract void setOnError(String attributeName, ErrorCallback<V> callback);

    /**
     * Set a callback that is invoked if an error occurs when setting the
     * value of any attribute in the device model. The callback may be {@code null}.
     * <p>
     * Note that it is possible to set a callback for all attributes and to set
     * a callback for a specific attribute via {@link #setOnError(String, AbstractVirtualDevice.ErrorCallback)}.
     * If there is a callback for the specific attribute and for all attributes,
     * both callbacks will be invoked.
     * @param callback a callback to invoke when there is an error setting a
     * value, if {@code null}, the existing callback will be removed
     * @see #setOnError(String, AbstractVirtualDevice.ErrorCallback)
     */
    public abstract void setOnError(ErrorCallback<?> callback);

    protected AbstractVirtualDevice() {}

}
