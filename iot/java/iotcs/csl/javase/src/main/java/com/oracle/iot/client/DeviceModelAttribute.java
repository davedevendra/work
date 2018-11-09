/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client;

import oracle.iot.client.DeviceModel;

/**
 * DeviceModelAttribute is the model of an attribute in a {@link DeviceModel}.
 * @param <T> the type of value
 */
public abstract class DeviceModelAttribute<T> {

    /**
     * Get the attribute name.
     * @return the attribute name from the device model
     */
    public abstract String getName();

    /**
     * Get the URN of the device type model this attribute comes from.
     * @return the URN of the device type model
     */
    public abstract String getModel();

    /**
     * A human friendly description of the attribute. If the model does not
     * contain a description, this method will return an empty String.
     * @return the attribute description, or an empty string
     */
    public abstract String getDescription();

    /**
     * An enumeration of the data types an attribute may contain.
     */
    public enum Type {
        /** The type of the attribute is a Number. */
        NUMBER() {
            @Override
            public Float fromString(String str) {
                // may throw NumberFormatException
                return Float.valueOf(str);
            }
        },
        /** The type of the attribute is a String. */
        STRING() {
            @Override
            public String fromString(String str) {
                return str;
            }
        },
        /** The type of the attribute is a URI. */
        URI() {
            @Override
            public ExternalObject fromString(String str) {
                // TODO: handle different URIs.
                return new ExternalObject(str);
            }
        },
        /** The type of the attribute is a Boolean */
        BOOLEAN() {
            @Override
            public Boolean fromString(String str) {
                return Boolean.valueOf(str);
            }
        },
        /**  The type of the attribute is an Integer. */
        INTEGER() {
            @Override
            public Integer fromString(String str) {
                // may throw NumberFormatException
                return Integer.valueOf(str);
            }
        },
        /**
         * The type of the attribute is a Long, which represents
         * the millisecond time since the epoch.
         */
        DATETIME() {
            @Override
            public Long fromString(String str) {
                // may throw NumberFormatException
                return Long.parseLong(str);
            }
        };

        abstract public Object fromString(String str);
    }

    /**
     * The data type of the attribute. If the access type of the attribute is
     * executable, this method will return null.
     * @return the attribute's data type, or null
     */
    public abstract Type getType();


    /**
     * For {@link Type#NUMBER} only, give the lower bound of the
     * acceptable range of values. Null is always returned for attributes
     * other than {@code NUMBER} type.
     *
     * @return a Number, or null if no lower bound has been set
     */
    public abstract Number getLowerBound();

    /**
     * For {@link Type#NUMBER} only, give the upper bound of the
     * acceptable range of values. Null is always returned for attributes
     * other than {@code NUMBER} type.
     *
     * @return a Number, or null if no upper bound has been set
     */
    public abstract Number getUpperBound();

    /**
     * Access rules for an attribute.
     */
    public enum Access {
        /**
         * The attribute value can be read but may not be set.
         */
        READ_ONLY,
        /**
         * The attribute value can be set but may not be read.
         */
        WRITE_ONLY,
        /**
         * The attribute value can be set and can be read
         */
        READ_WRITE,
        /**
         * The attribute is a action
         */
        EXECUTABLE
    }

    /**
     * Return the access rules for the attribute. The default is READ-ONLY
     * @return the access rule for the attribute
     */
    public abstract Access getAccess();

    /**
     * @return an alternative name for the attribute
     * @deprecated Use {@link #getName()}
     */
    @Deprecated
    public abstract String getAlias();

    /**
     * Get the default value of the attribute as defined by the device model. If there is no
     * {@code defaultValue} for the attribute in the device model, then this method will return
     * {@code null}. The value {@code null} is <em>not</em> a default value.
     * @return the default value of the attribute, or {@code null} if no default is defined
     */
    public T getDefaultValue() { return null; }

    protected DeviceModelAttribute() {}

}
