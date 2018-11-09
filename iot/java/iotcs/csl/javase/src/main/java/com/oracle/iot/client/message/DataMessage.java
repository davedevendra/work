/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.message;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * DataMessage extends Message class. It can be used for sending sensor data to IoT Server. This class
 * is immutable
 */
public final class DataMessage extends Message {

    /** Format for the data message */
    private final String format;

    /** Actual data for the data message */
    private final List<DataItem<?>> items;

    /**
     * DataMessage constructor takes DataMessage.Builder and set values to each
     * field. If the value is {@code null}, set a default value. Format cannot be
     * {@code null} or empty.
     * 
     * @param builder DataMessage.Builder
     * @throws IllegalArgumentException if the format is not specified.
     * @throws IllegalArgumentException if no data items are set
     */
    private DataMessage(Builder builder) {
        super(builder);

        if ((builder.format == null || builder.format.length() == 0)) {
            throw new IllegalArgumentException("Format cannot be null or empty.");
        } else {
            this.format = builder.format;
        }

        if (builder.items == null || builder.items.isEmpty()) {
            throw new IllegalArgumentException("Data items cannot be null or empty.");
        } else {
            items = Collections.unmodifiableList(new ArrayList<DataItem<?>>(builder.items));
        }
    }

    /**
     * Get the name of the format for data.
     *
     * @return data format, never {@code null}.
     */
    public final String getFormat() {
        return this.format;
    }

    /**
     * Get message payload as {@link List} of {@link DataItem}s.
     * 
     * @return Payload that is carried by {@link DataMessage}, never {@code null}.
     */
    public final List<DataItem<?>> getDataItems() {
        return Collections.unmodifiableList(this.items);
    }

    /**
     * {@link Builder} extends {@link Message.MessageBuilder} class. {@link DataMessage} class is immutable. A
     * {@link Builder} is required when creating {@link DataMessage}. {@link DataMessage} uses Builder
     * design pattern.
     */
    public final static class Builder extends MessageBuilder<Builder> {

        /** Format for the data message */
        private String format;

        /** List of data items */
        private final List<DataItem<?>> items = new ArrayList<DataItem<?>>();

        public Builder() {

        }

        @Override
        public Builder fromJson(JSONObject jsonObject) {
            JSONObject payload = jsonObject.optJSONObject("payload");
            Builder builder = super.fromJson(jsonObject);
            if (payload != null) {
                builder.format = payload.optString("format", null);
                Message.Utils.dataFromJson(jsonObject, builder.items);
            }

            return builder;
        }

        /**
         * Set message format.
         *
         * @param format Format for the {@link DataMessage}
         * @return Builder with updated format field.
         */
        public final Builder format(String format) {
            this.format = format;
            return self();
        }

        /**
         * Add {@code double} {@link DataItem}. Key cannot be {@code null}, empty or long {@link String}.
         *
         * @param key {@link String} item key.
         * @param value {@code double} item value.
         * @param <T> builder
         * @return Builder with added new dataItem field.
         *
         * @throws IllegalArgumentException when value is {@link Double#NEGATIVE_INFINITY}, {@link Double#POSITIVE_INFINITY},
         *                                  {@link Double#NaN} or the key is empty or long string.
         * @throws NullPointerException when the key is {@code null}.
         */
        public final <T> Builder dataItem(String key, double value) {
            this.items.add(new DataItem<Double>(key, value));
            return self();
        }

        /**
         * Add {@code boolean} {@link DataItem}. Key cannot be {@code null}, empty or long {@link String}.
         *
         * @param key {@link String} item key
         * @param value {@code boolean} item value
         * @param <T> builder
         * @return Builder with added new dataItem field.
         *
         * @throws IllegalArgumentException when the key is empty or long string.
         * @throws NullPointerException when the key is {@code null}.
         */
        public final <T> Builder dataItem(String key, boolean value) {
            this.items.add(new DataItem<Boolean>(key, value));
            return self();
        }

        /**
         * Add {@link String} {@link DataItem}. Key cannot be {@code null}, empty or long {@link String}.
         * Value cannot be long {@link String}.
         * 
         * @param key {@link String} item key.
         * @param value {@link String} item value.
         * @param <T> builder
         * @return Builder with added new dataItem field.
         *
         * @throws IllegalArgumentException when the key is empty, key or value are long strings.
         * @throws NullPointerException when the key or value are {@code null}.
         */
        public final <T> Builder dataItem(String key, String value) {
            this.items.add(new DataItem<String>(key, value));
            return self();
        }

        /**
         * Add all {@link DataItem}s to existing ones.
         *
         * @param dataItems {@link Collection} of {@link DataItem}.
         * @param <T> builder
         * @return Builder with added new dataItem field.
         */
        public final <T> Builder dataItems(Collection<DataItem<?>> dataItems) {
            this.items.addAll(dataItems);
            return self();
        }

        /**
         * Creates new instance of {@link DataMessage} using values from {@link DataMessage.Builder}.
         * @return Instance of {@link DataMessage}
         */
        @Override
        public final DataMessage build() {
            return new DataMessage(this);

        }

        /**
         * Returns current instance of {@link DataMessage.Builder}.
         * @return Instance of {@link DataMessage.Builder}
         */
        @Override
        protected final Builder self() {
            return this;
        }

    }

    /**
     * Get type message type.
     *
     * @return type, never {@code null}.
     */
    @Override
    public Type getType() {
        return Type.DATA;
    }

    /**
     * Exports data from {@link DataMessage} to {@link String} using JSON interpretation of the message.
     * @return JSON interpretation of the message as {@link String}.
     */
    @Override
    public final String toString() {
        return toJson().toString();
    }

    /**
     * Export {@link DataMessage} to {@link JSONObject}.
     * @return {@link com.oracle.iot.client.message.DataMessage} as
     *       {@link JSONObject}.
     */
    @Override
    public JSONObject toJson() {
        return Utils.dataToJson(this, items, this.format, null, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        DataMessage that = (DataMessage) o;

        if (!format.equals(that.format)) return false;
        return items.containsAll(that.items) && that.items.containsAll(items);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + format.hashCode();
        result = 31 * result + items.hashCode();
        return result;
    }
}
