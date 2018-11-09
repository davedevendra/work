/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.message;

/**
 * DataItem is a key/value/type triplet for data in DataMessage along with the type of the data.
 * Key cannot be {@code null} or empty, it is mandatory field.
 * {@link String} value cannot be {@code null}, other values are primitive data types. Value is mandatory field.
 * Type can be STRING, BOOLEAN or DOUBLE, see Type; it is assigned automatically by constructor.
 */
public final class DataItem<T> {

    /** Data item key */
    private final String key;

    /** Data item value */
    private final T value;
    
    /** Type of the value */
    private final Type type;
    
    /**
     * The data type of the data item.
     */
    public enum Type {
        STRING,
        DOUBLE,
        BOOLEAN;

        /**
         * Returns name of the enum value as {@link String}
         * @return enum value as {@link String}
         */
        public String getValue() {

            return this.name();
        }

        /**
         * Returns name of the enum value as {@link String}
         * @return enum value as {@link String}
         */
        public String toString() {

            return this.name();
        }
    }

    /**
     * Constructor that takes {@link String} key and {@code double} value. This is to reinforce the value type.
     * 
     * @param key data item key
     * @param value data item double value
     *
     * @throws IllegalArgumentException when value is {@link Double#NEGATIVE_INFINITY}, {@link Double#POSITIVE_INFINITY}
     *                                  or {@link Double#NaN} or the key is empty or long string. Maximum length for key
     *                                  is {@link Message.Utils#MAX_KEY_LENGTH} bytes. The length is measured after
     *                                  the key is encoded using UTF-8 encoding.
     * @throws NullPointerException when the key is {@code null}.
     */
    public DataItem(String key, double value) {
        this(key, value, Type.DOUBLE);
        if (Double.isNaN(value) || Double.isInfinite(value))
            throw new IllegalArgumentException("Data Item: Double value is infinite or NaN");
    }

    /**
     * Constructor that takes {@link String} key and {@code boolean} value. This is to reinforce the value type.
     * 
     * @param key data item key
     * @param value data item boolean value
     * @throws IllegalArgumentException when the key is empty or long string. Maximum length for key is
     *          {@link Message.Utils#MAX_KEY_LENGTH} bytes. The length is measured after the key is encoded
     *          using UTF-8 encoding.
     * @throws NullPointerException when the key is {@code null}.
     */
    public DataItem(String key, boolean value) {
        this(key, value, Type.BOOLEAN);
    }

    /**
     * Constructor that takes {@link String} key and {@link String} value. This is to reinforce the value type
     * 
     * @param key data item key
     * @param value data item {@link String} value
     * @throws IllegalArgumentException when the key is empty, key or value are long strings. Maximum length for key is
     *          {@link Message.Utils#MAX_KEY_LENGTH} bytes, maximum length for value is
     *          {@link Message.Utils#MAX_STRING_VALUE_LENGTH} bytes. The length is measured after the string is encoded
     *          using UTF-8 encoding.
     * @throws NullPointerException when the key or value are {@code null}.
     */
    public DataItem(String key, String value) {
        this(key, value, Type.STRING);
        Message.Utils.checkNullValueThrowsNPE(value, "Data Item: String value");
        Message.Utils.checkValueLengthAndThrowIAE(value, "Data Item: String value");
    }

    /**
     * Private constructor for data item.
     * 
     * @param key data item key
     * @param value data item value
     * @param type type of the data
     * @throws IllegalArgumentException when the key is empty or long string.
     * @throws NullPointerException when the key is {@code null}.
     */
    @SuppressWarnings({"unchecked"})
    private DataItem(String key, Object value, Type type) {
        Message.Utils.checkNullValueThrowsNPE(key, "Data Item: Key");
        Message.Utils.checkEmptyStringThrowsIAE(key, "Data Item: Key");
        Message.Utils.checkKeyLengthAndThrowIAE(key, "Data Item: Key");

        this.key = key;
        this.type = type;
        // throw exception if the value is not supported, should never happen
        this.value = (T) value;
    }

    /**
     * Get data item key.
     * 
     * @return key of this {@code DataItem}, never {@code null}.
     */
    public final String getKey() {
        return this.key;
    }

    /**
     * Get data item value.
     * 
     * @return value of this {@code DataItem}, never {@code null}.
     */
    public final T getValue() {
        return this.value;
    }

    /**
     * Get data item type.
     * 
     * @return type of this {@code DataItem}, never {@code null}.
     */
    public final Type getType() {
        return this.type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DataItem that = (DataItem) o;

        if (!this.key.equals(that.key)) return false;
        if (!this.type.equals(that.getType())) return false;
        return this.value.equals(that.value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + value.hashCode();
        return result;
    }

    @Override
    public String toString() {
        final String val = type == Type.STRING ? "\"" + value + "\"" : value.toString();
        return "{\"" + key + "\"," + val + "}";
    }
}
