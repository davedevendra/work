/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client;

import java.util.List;

import oracle.iot.client.DeviceModel;

/**
 * DeviceModelFormat is the model of an message in a
 * {@link DeviceModel}.
 */
public abstract class DeviceModelFormat {

    /**
     * The message type. Each type corresponds to one type of message.
     */
    public enum Type {
        DATA,
        ALERT
    }

    /**
     * Describes a field of a message.
     */
    public interface Field {
        /**
         * Get the message format name.
         * @return the message format name
         */
        String getName();

        /**
         * The JSON type message.
         * @return the message data
         */
        DeviceModelAttribute.Type getType();

        /**
         * If the field is optionalThe type message.
         * @return the message data
         */
        boolean isOptional();
    }

    /**
     * Get the URN of the message format
     * @return the message format URN
     */
    public abstract String getURN();

    /**
     * Get the message format name.
     * @return the message format name
     */
    public abstract String getName();

    /**
     * A human friendly description of the message format. If the format does
     * not contain a description, this method will return an empty String.
     * @return the message format description, or an empty string
     */
    public abstract String getDescription();

    /**
     * The type of the message.
     * @return the type of the message
     */
    public abstract Type getType();

    /**
     * Get the format of fields.
     * @return list of field formats
     */
    public abstract List<Field> getFields();

    protected DeviceModelFormat() {}
}
