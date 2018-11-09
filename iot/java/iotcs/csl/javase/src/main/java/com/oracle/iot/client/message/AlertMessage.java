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
 * AlertMessage extends Message class. It can be used for alerts to IoT Server.
 * This class is immutable.
 */
public final class AlertMessage extends Message {

    /** optional description for this alert */
    private String description;

    /** severity of this alert */
    private Severity severity;

    /**
     * The Severity of this Alert
     */
    public enum Severity {
        LOW(4), NORMAL(3), SIGNIFICANT(2), CRITICAL(1);

        /** int value of the severity */
        private final int value;

        /**
         * Constructs enum from appropriate value. The value should be in the range [1-4] (CRITICAL=1,
         * LOW=4)
         */
        Severity(int value) {
            this.value = value;
        }

        /**
         * Returns severity as int value.
         * @return value of the severity
         */
        public int getValue() {
            return value;
        }
    }

    /** format for the alert message */
    private final String format;

    /** Actual data for the alert message */
    private final List<DataItem<?>> items;

    /**
     * AlertMessage constructor takes AlertMessage.Builder and set values to each
     * field. If the value is {@code null},this method will set a default value.
     * format cannot be {@code null} or empty. description cannot be {@code null}
     * or empty.
     *
     * @param builder AlertMessage.Builder
     *
     * @throws IllegalArgumentException if the format is not specified or is empty
     * @throws IllegalArgumentException if the description is not specified or is empty
     */
    private AlertMessage(Builder builder) {
        super(builder);

        if ((builder.format == null || (builder.format.length() == 0))) {
            throw new IllegalArgumentException("format cannot be null or empty.");
        } else {
            this.format = builder.format;
        }

        // TODO : see if description should be removed from this object.
        this.description = builder.description;

        items = Collections.unmodifiableList(new ArrayList<DataItem<?>>(builder.items));

        if(builder.severity == null) {
            this.severity = Severity.SIGNIFICANT;
        }
        else {
            this.severity = builder.severity;
        }
    }


    /**
     * Gets the severity of this alert
     *
     * @return the severity of this alert
     */
    public Severity getSeverity() {
        return severity;
    }

    /**
     * Gets the description for this alert.
     *
     * @return the description for this alert.
     */
    public String getDescription() {
        return description;
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
     * @return Payload that is carried by {@link AlertMessage}, never {@code null}.
     */
    public final List<DataItem<?>> getDataItems() {
        return Collections.unmodifiableList(this.items);
    }

    /**
     * {@link Builder} extends {@link Message.MessageBuilder} class. {@link AlertMessage} class is immutable. A
     * {@link Builder} is required when creating {@link AlertMessage}. {@link AlertMessage} uses Builder
     * design pattern.
     */
    public final static class Builder extends MessageBuilder<Builder> {


        /** message for the alert */
        private String description;

        /** severity for the alert */
        private Severity severity;

        public Builder() {
            // Alert messages are highest priority
            priority = Priority.HIGHEST;

            // default severity is SIGNIFICANT
            severity = Severity.SIGNIFICANT;
        }

        /** format for the alert message */
        private String format;

        /** List of data items */
        private final List<DataItem<?>> items = new ArrayList<DataItem<?>>();

        @Override
        public Builder fromJson(JSONObject jsonObject) {
            try {
                JSONObject payload = jsonObject.getJSONObject("payload");
                Builder builder = super.fromJson(jsonObject);
                builder.description = payload.optString("description", null);
                builder.severity =
                    Severity.valueOf(payload.optString("severity", null));
                builder.format = payload.optString("format", null);
                Message.Utils.dataFromJson(jsonObject, builder.items);
                return builder;
            } catch (JSONException e) {
                throw new MessageParsingException(e);
            }
        }

        /**
         * Set alert severity
         *
         * @param severity the severity for the (@link AlertMessage}
         * @return Builder with update severity field
         */
        public final Builder severity(Severity severity) {
            this.severity = severity;
            return self();
        }

        /**
         * Set the description for the alert
         *
         * @param description description for the {@link AlertMessage}
         * @return Builder with updated format field.
         */
        public final Builder description(String description) {
            this.description = description;
            return self();
        }

        /**
         * Set message format.
         *
         * @param format format for the {@link AlertMessage}
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
         * Creates new instance of {@link AlertMessage} using values from {@link AlertMessage.Builder}.
         * @return Instance of {@link AlertMessage}
         */
        @Override
        public final AlertMessage build() {
            return new AlertMessage(this);

        }

        /**
         * Returns current instance of {@link AlertMessage.Builder}.
         * @return Instance of {@link AlertMessage.Builder}
         */
        @Override
        protected final Builder self() {
            return this;
        }
    }

    @Override
    public Type getType() {
        return Type.ALERT;
    }

    /**
     * Exports data from {@link AlertMessage} to {@link String} using JSON interpretation of the message.
     * @return JSON interpretation of the message as {@link String}.
     */
    @Override
    public final String toString() {
        return toJson().toString();
    }

    @Override
    public JSONObject toJson() {
        JSONObject jsonObject = Utils.dataToJson(this, items, this.format,
            this.description, this.getSeverity().toString());
        try {
            jsonObject.put("type", Type.ALERT.name());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return jsonObject;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        AlertMessage that = (AlertMessage) o;
        if (description == null ?  that.description != null :
            (!description.equals(that.description))) return false;
        if(!severity.equals(that.severity)) return false;
        return items.equals(that.items);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + items.hashCode();
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + severity.hashCode();
        return result;
    }

}
