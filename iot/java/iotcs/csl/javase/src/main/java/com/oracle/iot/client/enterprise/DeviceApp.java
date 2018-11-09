/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.enterprise;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@code DeviceApp} represents an application, running on a device, and implementing its own device model.
 *
 */
public abstract class DeviceApp {

    /**
     * Get the endpoint identifier.
     *
     * @return the endpoint identifier.
     */
    public abstract String getId();

    /**
     * Return the endpoint metadata. Might be empty.
     * @return a map of device metadata
     */
    public abstract Map<String,String> getMetadata();

    /**
     * Return the value of the metadata field {@code key}.
     * @param key the metadata key to return
     * @return value for metadata {@code key} or {@code null} if not found.
     */
    public abstract String getMetadata(String key);

    /**
     * Get the list of device models supported by this endpoint.
     *
     * @return a list of device model URN.
     */
    public abstract List<String> getDeviceModels();

    /**
     * Get the value of the specified {@code field} or an empty String if the value is unknown.
     *
     * @param field the field to look for
     * @return the field value or an empty String.
     */
    public abstract String getValue(Field field);


    /**
     * The Field enum defines the characteristics of a device.
     */
    public enum Field {

        /**
         * A unique value identifying the device application
         */
        ID("id"),
        /**
         * The endpoint type
         */
        TYPE("type"),
        /**
         * Optional free form text describing the device application.
         */
        DESCRIPTION("description"),
        /**
         * The device application name.
         */
        APP_NAME("appName"),
        /**
         * The device application name.
         */
        APP_VERSION("appVersion"),
        /**
         * The device application type.
         */
        APP_TYPE("appType"),
        /**
         * For endpoints which are not themselves directly connected to the IoT
         * network, this points to the endpoint which is handling
         * communications on their behalf.
         */
        DIRECTLY_CONNECTED_OWNER("directlyConnectedOwner"),
        /**
         * Additional user-defined key / value pairs describing the endpoint.
         */
        METADATA("metadata"),
        /**
         * String in ISO-8601 time format indicating the date and time the
         * endpoint was created.
         */
        CREATED("created"),
        /**
         * The device state.
         */
        STATE("state"),
        /**
         * A list of device models
         */
        DEVICE_MODELS("deviceModels");

        /**
         * Create a Field instance
         * @param alias the alias name used by server
         */
        Field(String alias) {
            this.alias = alias;
        }

        /**
         * The alias used by server REST API
         */
        final String alias;

        /**
         * Get the {@code Field} instance associated with the specified alias.
         * @param alias the alias to look for
         * @return the Field instance matching the specified alias
         */
        public static Field fromAlias(String alias) {
            for(Field f: Field.values()) {
                if (f.alias.equals(alias)) {
                    return f;
                }
            }
            return null;
        }

        /**
         * Get a new {@code Set} initialized with all possible {@code Field} values.
         * @return a new {@code Set} of all possible {@code Field} values.
         */
        static public Set<Field> all() {
            return EnumSet.allOf(Field.class);
        }

        /**
         * Get the alias of this {@code Field}.
         * @return the alias of this {@code Field}.
         */
        public String alias() {
            return this.alias;
        }
    }


}
