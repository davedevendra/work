/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.  All rights reserved.
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
 * Represents a set of custom data fields (key/value pairs) to be sent to the
 * server. The custom data is sent by calling the
 * {@link #submit()} method. The time of the data fields are set when
 * {@code submit()} is called, allowing {@code submit()} to be called more
 * than once.
 * <p>
 * The {@code set} method returns the {@code Data} instance to allow
 * fields of data to be set in fluent style.
 * @see VirtualDevice#createData(String)
 */
public abstract class Data {

    /**
     * Set the value of a field in the {@code Data}. The fields are
     * determined by the format given when the Data is created.
     * The value is validated according to the constraints in the format.
     * If the value is not valid, an IllegalArgumentException is raised.
     * <p>All fields defined in the format that are {@code "optional" : true}
     * must be set before calling {@code submit()}.</p>
     * @param <T> the type of the value.
     * @param field the name of a field from the custom data format
     * @param value the value to set
     * @return this Data instance
     * @throws IllegalArgumentException if the value is not valid,
     * or {@code field} is {@code null}
     * @see VirtualDevice#createData(String)
     */
    public abstract <T> Data set(String field, T value);

    /**
     * Submit the data. The event time is set when this method is called. The 
     * {@link Data#setOnError(oracle.iot.client.AbstractVirtualDevice.ErrorCallback) onError handler}
     * will be called if there is error sending the alert.
     * <p>All fields defined in the format that are {@code "optional" : true}
     * must be set before calling {@code submit()}. If {@code submit()} is
     * called
     * before setting all {@code "optional" : true} fields, an (unchecked)
     * IllegalStateException is thrown.</p>
     * @throws IllegalStateException if {@code submit()} is called
     * before setting all {@code "optional" : true} fields
     */
    public abstract void submit();

    /**
     * Set a callback that is invoked if an error occurs when submitting the
     * {@code Data}. The callback may be {@code null}, which will un-set
     * the callback.
     * @param callback a callback to invoke, or {@code null}
     */
    public abstract void setOnError(ErrorCallback<VirtualDevice> callback);

    /*  */
    protected Data() {}
}
