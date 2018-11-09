/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.message;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MessageProperties contains a table of key and list of values pairs for extra
 * information to send. This class is immutable
 */
public final class MessageProperties {
    /**
     * MessagePropertiesBuilder is the builder for MessageProperties.
     */
    public final static class Builder {

        /** A {@link Map} for message properties. It is a key/list of value pair  */
        private Map<String, List<String>> propertiesTable = new HashMap<String, List<String>>();

        public Builder() {

        }

        /**
         * Add a new key/vale pair. If the key exists, add the value to the list of values. If the key does
         * not exist, put the key and value to the table. Key or value cannot be {@code null}. Key cannot be empty.
         * Key or value cannot be long strings. Maximum length for key is {@link Message.Utils#MAX_KEY_LENGTH} bytes,
         * maximum length for value is {@link Message.Utils#MAX_STRING_VALUE_LENGTH} bytes. The length is measured
         * after the string is encoded using UTF-8 encoding.
         *
         * @param  key property key
         * @param  value property value
         * @return MessageProperties.Builder
         * @throws NullPointerException for {@code null} key or the values are {@code null}.
         * @throws IllegalArgumentException for empty or long key or for long value.
         */
        public final Builder addValue(String key, String value) {
            Message.Utils.checkNullValueThrowsNPE(key, "MessageProperties: Key");
            Message.Utils.checkEmptyStringThrowsIAE(key, "MessageProperties: Key");
            Message.Utils.checkNullValueThrowsNPE(value, "MessageProperties: Value");

            Message.Utils.checkKeyLengthAndThrowIAE(key, "MessageProperties: Key");
            Message.Utils.checkValueLengthAndThrowIAE(value, "MessageProperties: Key");

            List<String> values;
            if (propertiesTable.containsKey(key)) {
                values = propertiesTable.get(key);

            } else {
                values = new ArrayList<String>();
            }
            values.add(value);
            propertiesTable.put(key, values);

            return this;
        }
        
        /**
         * Add a new key/values pair. If the key exists, adds the values to the list of existing values. If the key does
         * not exist, puts the key and values to the table. Key or values cannot be {@code null}. Key cannot be empty
         * or long string. Values cannot contain long strings. Maximum length for key is
         * {@link Message.Utils#MAX_KEY_LENGTH} bytes, maximum length for any value is
         * {@link Message.Utils#MAX_STRING_VALUE_LENGTH} bytes. The length is measured after the string is encoded
         * using UTF-8 encoding.
         *
         * @param  key property key
         * @param  values property values
         * @return MessageProperties.Builder
         * @throws NullPointerException for {@code null} key or if the values are {@code null} or any item in values
         *                              is {@code null}.
         * @throws IllegalArgumentException for empty or long key and for any long item in values.
         */
        public final Builder addValues(String key, List<String> values) {
            Message.Utils.checkNullValueThrowsNPE(key, "MessageProperties: Key");
            Message.Utils.checkEmptyStringThrowsIAE(key, "MessageProperties: Key");
            Message.Utils.checkNullValueThrowsNPE(values, "MessageProperties: Values");
            Message.Utils.checkNullValuesThrowsNPE(values, "MessageProperties: Values");

            Message.Utils.checkKeyLengthAndThrowIAE(key, "MessageProperties: Key");
            Message.Utils.checkValuesLengthAndThrowIAE(values, "MessageProperties: Values");

            List<String> existingValues;

            if (propertiesTable.containsKey(key)) {
                existingValues = propertiesTable.get(key);

            } else {
                existingValues = new ArrayList<String>();
            }

            existingValues.addAll(values);
            propertiesTable.put(key, existingValues);
            return this;
        }

        /**
         * Copy another {@link MessageProperties} by adding all properties to the current {@link MessageProperties}
         * @param properties {@link MessageProperties} to copy
         * @return MessageProperties.Builder
         */
        public final Builder copy(MessageProperties properties){
            for(String key: properties.getKeys()) {
                List<String> values = new java.util.ArrayList<String>(properties.getProperties(key).size());
                for (String value: properties.getProperties(key)) {
                    values.add(value);
                }
                propertiesTable.put(key, values);
            }
            return this;
        }

        /**
         * Creates new instance of {@link MessageProperties} using values from {@link MessageProperties.Builder}.
         * @return Instance of {@link MessageProperties}
         */
        public final MessageProperties build() {
            return new MessageProperties(this);
        }
    }

    /** A {@link Map} for message properties. It is a key/list of value pair  */
    private final Map<String, List<String>> propertiesTable;

    /**
     * {@link MessageProperties} constructor takes {@link MessageProperties.Builder} and set propertiesTable.
     * @param builder Builder containing properties.
     */
    private MessageProperties(Builder builder) {
        this.propertiesTable = builder.propertiesTable;
    }

    /**
     * Check if the properties contain the key.
     * @param key property key
     * @return {@code true} if the properties contain the key
     */
    public final boolean containsKey(String key) {
        return propertiesTable.containsKey(key);
    }

    /**
     * Get a {@link Set} of the keys.
     * @return a set of the keys, never {@code null}.
     */
    public final Set<String> getKeys() {
        return Collections.unmodifiableSet(propertiesTable.keySet());
    }

    /**
     * Get a {@link List} of values for a particular key.
     * @param key property key
     * @return a {@link List} of {@link String} values
     */
    public final List<String> getProperties(String key) {
        return Collections.unmodifiableList(propertiesTable.get(key));
    }

    /**
     * Get the first value for a particular key.
     * @param key property key
     * @return value, may return {@code null} if the key does not exist or values assigned to this key is empty.
     */
    public final String getProperty(String key) {
        List<String> propertyValues = propertiesTable.get(key);
        
        if (propertyValues != null && propertyValues.size() > 0) {
            return propertyValues.get(0);
        }
        else {
            return null;
        }
    }

    /**
     * Get a specific value.
     * @param key property key
     * @param index index of the {@link List} of the {@link String} values
     * @return value, may return {@code null} if the key does not exist or index is out of range.
     */
    public final String getProperty(String key, int index) {
        List<String> propertyValues = propertiesTable.get(key);
        
        if (propertyValues != null && propertyValues.size() > index) {
            return propertyValues.get(index);
        }
        else {
            return null;
        }
    }

    /**
     * Get all properties.
     * @return a {@link Map} of key and list of values, never {@code null}
     */
    public final Map<String, List<String>> getAllProperties() {
        return Collections.unmodifiableMap(propertiesTable);
    }
    
    /**
     * Method to print the message properties in JSON format.
     * @return message properties in string format
     */
    @Override
    public final String toString() {
        return toJson().toString();
    }

    /**
     * Method to export the message properties to JSONObject.
     * @return message properties in JSON format
     */
    public final JSONObject toJson() {
        JSONObject properties = new JSONObject();

        for (String k : getKeys()) {
            JSONArray jsonValue = new JSONArray();
            List<String> values = getProperties(k);

            for (String v : values) {
                jsonValue.put(v);
            }

            try {
                properties.put(k, jsonValue);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        return properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MessageProperties that = (MessageProperties) o;

        return propertiesTable.equals(that.propertiesTable);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return propertiesTable.hashCode();
    }
}
