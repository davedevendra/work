/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package oracle.iot.client.device;

import oracle.iot.client.AbstractVirtualDevice.ErrorCallback;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * An Alert to be sent to the server. The alert is sent by calling the
 * {@link #raise()} method. The time of the alert is set when {@code raise()}
 * is called, allowing {@code raise()} to be called more than once.
 * <p>
 * The {@code set} method returns the {@code Alert} instance to allow
 * the fields of the alert to be set in fluent style.
 * @see VirtualDevice#createAlert(String)
 */
public abstract class Alert {

    /**
     * Set the value of a field in the {@code Alert}. The fields are
     * determined by the format given when the Alert is created.
     * The value is validated according to the constraints in the format.
     * If the value is not valid, an IllegalArgumentException is raised.
     * <p>All fields defined in the format that are {@code "optional" : true}
     * must be set before calling {@code raise()}.</p>
     * @param <T> the type of the value.
     * @param field the name of a field from the alert format
     * @param value the value to set
     * @return this Alert instance
     * @throws IllegalArgumentException if the value is not valid,
     * or {@code field} is {@code null}
     * @see VirtualDevice#createAlert(String)
     */
    public abstract <T> Alert set(String field, T value);

    /**
     * Send the alert. The event time is set when this method is called.
     * The {@link Alert#setOnError(oracle.iot.client.AbstractVirtualDevice.ErrorCallback) onError handler}
     * will be called if there is error sending the alert.
     * <p>All fields defined in the format that are {@code "optional" : true}
     * must be set before calling {@code raise()}. If {@code raise()} is called
     * before setting all {@code "optional" : true} fields, an (unchecked)
     * IllegalStateException is thrown.</p>
     * @throws IllegalStateException if {@code raise()} is called
     * @throws ArrayStoreException if the alert message cannot be queued
     * before setting all {@code "optional" : true} fields
     */
    public abstract void raise();

    /**
     * Set a callback that is invoked if an error occurs when raising the
     * {@code Alert}. The callback may be {@code null}, which will un-set
     * the callback.
     * @param callback a callback to invoke, or {@code null}
     */
    public abstract void setOnError(ErrorCallback<VirtualDevice> callback);

    /*  */
    protected Alert() {}

}
