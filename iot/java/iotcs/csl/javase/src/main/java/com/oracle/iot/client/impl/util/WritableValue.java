/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.util;

/**
 * WritableValue is a wrapper around a value that can be read or written.
 * @param <T> the type of value
 */
public final class WritableValue<T> {

    private T value;

    /**
     * Get the value.
     * @return the value
     */
    public T getValue() { return this.value; }

    /**
     * Set the value
     * @param value the value
     */
    public void setValue(T value) { this.value = value; }
}
