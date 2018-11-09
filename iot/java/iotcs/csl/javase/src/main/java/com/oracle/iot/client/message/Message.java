/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.message;

import com.oracle.iot.client.impl.TimeManager;
import com.oracle.iot.client.impl.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An abstract class for different types of messages in messaging server.
 * This class is immutable.
 */
public abstract class Message {

    /**
     * The priority of the message.
     */
    public enum Priority {
        LOWEST(0), LOW(1), MEDIUM(2), HIGH(3), HIGHEST(4);

        /** int value of the priority*/
        private final int value;

        /** Constructs enum from appropriate value. The value should be from interval [0-4] (0=LOWEST, 1=LOW,
         * 2=MEDIUM, 3=HIGH, 4=HIGHEST).
         */
        Priority(int value) {
            this.value = value;
        }

        /**
         * Returns priority as int value.
         * @return priority ranging from 0 (LOWEST) to 4 (HIGHEST)
         */
        public int getValue() {
            return value;
        }
    }

    /**
     * Reliability of the message
     */
    public enum Reliability {
        /** Message will not be persisted, number of retries if sending was unsuccessful
         is the base number of retries. */
        NO_GUARANTEE(0),

        /** Message will be persisted, number of retries if sending was unsuccessful
         is two times the base number of retries. */
        BEST_EFFORT(1),

        /** Message will be persisted, number of retries is unlimited. */
        GUARANTEED_DELIVERY(2);

        /** int value of the reliability*/
        private final int value;

        /** Constructs enum from appropriate value The value should be from interval [0-2] (0=NO_GUARANTEE,
         * 1=BEST_EFFORT, 2=GUARANTEED_DELIVERY.
         */
        Reliability(int value) {
            this.value = value;
        }

        /**
         * Returns reliability as int value.
         * @return reliability ranging from 0 (NO_GUARANTEE) to 2 (GUARANTEED_DELIVERY)
         */
        public int getValue() {
            return value;
        }
    }

    /**
     * The message type. Each type corresponds to one type of message.
     */
    public enum Type {
        DATA("DATA"),
        REQUEST("REQUEST"),
        RESPONSE("RESPONSE"),
        RESOURCE("RESOURCES_REPORT"),
        ALERT("ALERT");

        private final String alias;

        Type(String alias) {
            this.alias = alias;
        }

        public String alias() {
            return this.alias;
        }
    }

    /**
     * The message direction with respect to the device.
     */
    public enum Direction {
        FROM_DEVICE,
        TO_DEVICE
    }

    /**
     * Created time of the message diagnostic value. The value of the created time as a {@link java.lang.Long} that
     * contains the epoch value in milliseconds.
     */
    public static final String DIAG_CREATED_TIME = "createdTime";
    /**
     * Client address diagnostic value. The address client as a {@link String}
     */
    public static final String DIAG_CLIENT_ADDRESS = "clientAddress";

    /**
     * This is an abstract class for different message builders. Message classes
     * are immutable. A builder is required when creating messages.
     */
    public static abstract class MessageBuilder<T extends MessageBuilder<T>> {

        /**
         * A unique ID for each message that is assigned by the server (on a receipt of a message from a client or
         * when the server generates a message)
         */
        String id;
        /**
         * A unique ID for each message assigned by the client for tracking purposes.
         */
        String clientId;
        /**
         * Source of the message (should correspond to endpoint id)
         */
        String source;
        /**
         * Destination of the message.
         */
        String destination;
        /**
         * Message priority.
         */
        Priority priority;
        /**
         * Reliability for message delivering.
         */
        Reliability reliability;
        /**
         * Message event time.
         */
        Long eventTime;
        /**
         * Extra information for the message.
         */
        MessageProperties properties;
        /**
         * The message sender (should correspond to endpoint id).
         */
        String sender;
        /**
         * The message diagnostics
         */
        Map<String, Object> diagnostics;
        /**
         * The message direction (with respect to the server)
         */
        Direction direction;
        /**
         * The time when the message was received by the server (epoch value in nanoseconds)
         */
        Long receivedTime;
        /**
         * The time when the message was sent by the server (epoch value in nanoseconds)
         */
        Long sentTime;

        /**
         * Return current instance of the {@link Message.MessageBuilder}.
         * @return Current instance of {@link Message.MessageBuilder}.
         */
        protected abstract T self();

        public MessageBuilder() {

        }

        /**
         * Sets message ID. Message id is instance of {@link UUID}. If no id is provided during creation of the
         * {@link Message}, random one is created.
         *
         * @param id {@link UUID} of the message assigned to this message.
         * @return generic type of message builder
         */
        public final T id(String id) {
            this.id = id;
            return self();
        }

        /**
         * Sets message client ID. The client message id should be unique within the client. If no id is provided
         * during creation of the {@link Message}, random one is created.
         *
         * @param id {@link UUID} of the message assigned to this message.
         * @return generic type of message builder
         */
        public final T clientId(String id) {
            this.clientId = id;
            return self();
        }

        /**
         * Set message source. The value should correspond with endpoint ID where the message was originated.
         *
         * @param source source (source endpoint ID) of the message
         * @return generic type of message builder
         */
        public final T source(String source) {
            this.source = source;
            return self();
        }

        /**
         * Set message destination. The value should correspond with endpoint ID where the message should be delivered.
         *
         * @param destination destination (destination endpoint ID) of the message
         * @return generic type of message builder
         */
        public final T destination(String destination) {
            this.destination = destination;
            return self();
        }

        /**
         * Set message priority.
         *
         * @param priority priority of the message
         * @return generic type of message builder
         */
        public final T priority(Priority priority) {
            this.priority = priority;
            return self();
        }

        /**
         * Sets reliability for message delivering.
         *
         * @param reliability reliability for message delivering.
         * @return generic type of the message builder
         */
        public final T reliability(Reliability reliability){
            this.reliability = reliability;
            return self();
        }

        /**
         * Set message creation time. 
         * Will be automatically set by the library to current time. 
         * Apps should only set it explicitly, if it needs to be something 
         * other than current time (for example: some historical measurement 
         * from a sensor that just got connected) 
         *
         * @param eventTime event time of the message
         * @return generic type of message builder
         */
        public final T eventTime(Long eventTime) {
            if (eventTime != null) {
                this.eventTime = eventTime;
            }
            return self();
        }

        /**
         * Set message creation time.
         *
         * @param eventTime event time of the message
         * @return generic type of message builder
         */
        public final T eventTime(long eventTime) {
            this.eventTime = Long.valueOf(eventTime);
            return self();
        }

        /**
         * Set message properties.
         *
         * @param properties extra information for the message
         * @return generic type of message builder
         */
        public final T properties(MessageProperties properties) {
            this.properties = properties;
            return self();
        }

        /**
         * Set message sender. The value should correspond with endpoint ID where the message was sent from.
         *
         * @param sender sender (sender endpoint ID) of the message
         * @return generic type of message builder
         */
        public final T sender(String sender) {
            this.sender = sender;
            return self();
        }

        /**
         * Set message direction.
         *
         * @param direction message direction
         * @return generic type of message builder
         */
        public final T direction(Direction direction) {
            this.direction = direction;
            return self();
        }

        /**
         * Set the time when the message was received by the server.
         *
         * @param time epoch value in nano seconds
         * @return generic type of message builder
         */
        public final T receivedTime(Long time) {
            this.receivedTime = time;
            return self();
        }

        /**
         * Set the time when the message was sent by the server.
         *
         * @param time epoch value in nano seconds
         * @return generic type of message builder
         */
        public final T sentTime(Long time) {
            this.sentTime = time;
            return self();
        }

        /**
         *  Set a diagnostic {@link Object} value
         *
         * @param name diagnostic name
         * @param value diagnostic value
         * @return generic type of message builder
         */
        public final T diagnostic(String name, Object value) {
            if ( diagnostics == null ){
                diagnostics = new HashMap<String,Object>();
            }
            this.diagnostics.put(name, value);
            return self();
        }

        /**
         * Method to deserialization of the Message from a JsonObject.
         *
         * @param jsonObject the jsonObject to fromString
         * @return generic type of message builder
         * @throws MessageParsingException when json object is wrong or
         *         does not contain mandatory fields.
         */
        public T fromJson(JSONObject jsonObject) {
            try {
                this.id(jsonObject.optString("id", null));
                this.clientId = jsonObject.optString("clientId", null);
                String source = jsonObject.optString("source", null);
                String destination = jsonObject.optString("destination", null);
                String sender = jsonObject.optString("sender", null);

                // TODO: remove second condition after RequestMessage
                // from Server has source
                if ((source == null || source.length() == 0) &&
                        (destination == null || destination.length() == 0)) {
                    throw new MessageParsingException(
                        "message.source.destination.null");
                }

                this.source(source);
                this.destination(destination);
                this.sender(sender);

                String priority = jsonObject.optString("priority", null);
                Utils.checkNullValueAndThrowMPE(priority,
                    "message.priority.null");
                try {
                    this.priority(Message.Priority.valueOf(priority));
                } catch (IllegalArgumentException e) {
                    throw new MessageParsingException(
                        "message.priority.illegal", e);
                }

                String reliability = jsonObject.optString("reliability", null);
                Utils.checkNullValueAndThrowMPE(reliability,
                    "message.reliability.null");
                try {
                    this.reliability(Message.Reliability.valueOf(reliability));
                } catch (IllegalArgumentException e) {
                    throw new MessageParsingException(
                        "message.reliability.illegal", e);
                }

                long eventTime = jsonObject.optLong("eventTime", -1);
                if (eventTime != -1) {
                    this.eventTime(eventTime);
                } else {
                    throw new MessageParsingException(
                        "message.eventTime.wrong");
                }


                MessageProperties.Builder properties =
                    new MessageProperties.Builder();
                JSONObject propertiesObject =
                    jsonObject.optJSONObject("properties");
                if (propertiesObject != null) {
                    Iterator<String> keys = propertiesObject.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        Utils.checkKeyLengthAndThrowMPE(key,
                            "message.property.key.long");
                        JSONArray keyValues =
                            propertiesObject.optJSONArray(key);
                        if (keyValues == null) {
                            List<String> values = Collections.emptyList();
                            properties.addValues(key, values);
                        } else {
                            for (int j = 0; j < keyValues.length(); j++) {
                                String value = keyValues.optString(j, null);
                                Utils.checkValueLengthAndThrowMPE(value,
                                   "message.property.value.long");
                                properties.addValue(key, value);
                            }
                        }
                    }
                }

                this.properties(properties.build());

                JSONObject diagnosticsObject =
                    jsonObject.optJSONObject("diagnostics");
                if (diagnosticsObject != null) {
                    Iterator<String> keys = diagnosticsObject.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        Object value = diagnosticsObject.get(key);

                        if (value instanceof Number ||
                                value instanceof Boolean) {
                            this.diagnostic(key, value);
                        } else { // treat everything else as String
                            this.diagnostic(key, value.toString());
                        }
                    }
                }

                String direction = jsonObject.optString("direction", null);
                if (direction != null) {
                    try {
                        this.direction(Message.Direction.valueOf(direction));
                    } catch (IllegalArgumentException e) {
                        throw new MessageParsingException(
                            "message.direction.wrong", e);
                    }
                }

                Object value = jsonObject.opt("receivedTime");
                if (value != null && value instanceof Number) {
                    this.receivedTime(
                        Long.valueOf(((Number)value).longValue()));
                }

                value = jsonObject.opt("sentTime");
                if (value != null && value instanceof Number) {
                    this.sentTime(Long.valueOf(((Number)value).longValue()));
                }
            } catch (JSONException e) {
                throw new MessageParsingException(e);
            }

            return self();
        }

        /**
         * Abstract build method
         *
         * @return message
         */
        public abstract Message build();
    }

    /**
     * A unique ID for each message generated by the server (on receipt of a message from the client or when
     * a message is generated by the server)
     */
    final String id;
    /**
     * A unique ID for each message. It is generated when the message is first
     * created or can be passed in by the user.
     */
    private final String clientId;
    /**
     * Source of the message.
     */
    final String source;
    /**
     * Destination of the message.
     */
    final String destination;
    /**
     * Message priority.
     */
    private final Priority priority;
    /**
     * Reliability for message delivering.
     */
    private final Reliability reliability;
    /**
     * Message event time.
     */
    private final Long eventTime;
    /**
     * Extra information for the message.
     */
    private final MessageProperties properties;
    /**
     * The message sender (should correspond to endpoint id).
     */
    private String sender;
    /**
     * The message diagnostics
     */
    private Map<String,Object> diagnostics;
    /**
     * The message direction (with respect to the server)
     */
    private Direction direction;
    /**
     * The time when the message was received by the server (epoch value in milliseconds)
     */
    private Long receivedTime;
    /**
     * The time when the message was sent by the server (epoch value in milliseconds)
     */
    private Long sentTime;

    /**
     * Maintains order of messages when messages are created less than a millisecond apart.
     */
    private long ordinal;

    static AtomicLong ordinalValue = new AtomicLong(0l);
    static long nextOrdinal() {
        Long l = ordinalValue.addAndGet(1);
        if (l == Long.MAX_VALUE) {
            ordinalValue.set(0);
        }
        return l.longValue();
    }

    private static final int BASE_NUMBER_OF_RETRIES;
    static {
        int max_retries = Integer.getInteger("oracle.iot.client.device.dispatcher_base_number_of_retries", 3);
        BASE_NUMBER_OF_RETRIES = (max_retries > 3 ? max_retries : 3);
    }


    /**
     * The number of send retries remaining for this message.
     */
    private int remainingRetries;

    /**
     * Message constructor takes message builder and set values to each field.
     * If the value is {@code null}, set a default value. The default value for priority
     * is {@link Priority#LOW}. The default value for reliability is {@link Reliability#BEST_EFFORT}
     *
     * @param builder MessageBuilder
     * @throws IllegalArgumentException when the message has no source and no destination set.
     */
    Message(MessageBuilder<?> builder) {
        if (builder.clientId != null) {
            this.clientId = builder.clientId;
        } else {
            // Don't require clientId to be set, auto-generate one if it isn't.
            this.clientId = UUID.randomUUID().toString();
        }

        this.id = builder.id;

        if ((builder.source == null || builder.source.length() == 0)) {
            throw new IllegalArgumentException("Source cannot be null or empty.");
        }
            this.source = builder.source;

        if (builder.destination == null) {
            this.destination = "";
        } else {
            this.destination = builder.destination;
        }

        if (builder.sender == null) {
            this.sender = "";
        } else {
            this.sender = builder.sender;
        }

        if (builder.eventTime == null) {
            this.eventTime = Long.valueOf(TimeManager.currentTimeMillis());
        } else {
            this.eventTime = builder.eventTime;
        }

        if (builder.priority == null) {
            this.priority = Priority.LOW;
        } else {
            this.priority = builder.priority;
        }
        if (builder.reliability == null){
            this.reliability = Message.Reliability.BEST_EFFORT;
        } else {
            this.reliability = builder.reliability;
        }

        switch (this.reliability) {
            case NO_GUARANTEE:
            default:
                remainingRetries = BASE_NUMBER_OF_RETRIES;
                break;
            case BEST_EFFORT:
                remainingRetries = BASE_NUMBER_OF_RETRIES * 2;
                break;
            case GUARANTEED_DELIVERY:
                remainingRetries = Integer.MAX_VALUE;
                break;
        }

        if (builder.properties == null) {
            MessageProperties.Builder propertyBuilder = new MessageProperties.Builder();
            this.properties = propertyBuilder.build();

        } else {
            this.properties = builder.properties;
        }

        this.diagnostics = builder.diagnostics;
        this.diagnostics = (builder.diagnostics != null ) ? Collections.unmodifiableMap(builder.diagnostics) : null;
        this.direction = builder.direction;
        this.receivedTime = builder.receivedTime;
        this.sentTime = builder.sentTime;
        this.ordinal = nextOrdinal();

    }

    /**
     * Get unique ID for a message.
     *
     * @return Message id ({@link String}), never {@code null}
     */
    public final String getId() {
        return id;
    }

    /**
     * Abstract method to return message type.
     *
     * @return type, never {@code null}.
     */
    public abstract Type getType();

    /**
     * Get client's unique ID for a message.
     *
     * @return Message id ({@link String}), never {@code null}
     */
    public final String getClientId() {
        return clientId;
    }

    /**
     * Get message source (Endpoint Id from which the message is originated).
     *
     * @return source, may be {@code null} if the message was sent from server.
     */
    public final String getSource() {
        return source;
    }

    /**
     * Get message destination (Endpoint Id to which the message is originated).
     *
     * @return destination, may be {@code null} if the message was sent to server.
     */
    public final String getDestination() {
        return destination;
    }

    /**
     * Get message priority.
     *
     * @return priority, never {@code null}.
     */
    public final Priority getPriority() {
        return priority;
    }

    /**
     * Get message reliability
     *
     * @return reliability, never {@code null}.
     */
    public final Reliability getReliability() { return reliability;}

    /**
     * Get event time of the message
     *
     * @return eventTime, never {@code null}.
     */
    public final Long getEventTime() {
        return eventTime;
    }

    /**
     * Get message properties.
     *
     * @return properties, never {@code null}
     */
    public final MessageProperties getProperties() {
        return this.properties;
    }

    /**
     * Get message sender (Endpoint Id from which the message is sent from).
     *
     * @return sender
     */
    public final String getSender() {
        return this.sender;
    }

    /**
     * Get the order in which this message was created. This information is
     * used for sorting outgoing messages and is not transfered across the wire.
     * @return the order in which the message was created
     */
    public final long getOrdinal() {
        return ordinal;
    }

    /**
     * Not intended for general use.
     * Used internally by the message dispatcher implementation.
     * @return the number of remaining retries
     */
    public final int getRemainingRetries() {
        return reliability == Reliability.GUARANTEED_DELIVERY ? Integer.MAX_VALUE : remainingRetries;
    }

    /**
     * Not intended for general use.
     * Used internally by the message dispatcher implementation.
     * @param remainingRetries the new number of remaining retries
     */
    public void setRemainingRetries(int remainingRetries) {
        this.remainingRetries = remainingRetries;
    }

    /**
     * Get message diagnostics
     *
     * @return message diagnostics, can be {@code null}
     */
    public final Map<String,Object> getDiagnostics() {
        return this.diagnostics;
    }

    public final Object getDiagnosticValue(String diagName) {
        if ( diagName != null && this.diagnostics != null ){
            return this.diagnostics.get(diagName);
        }
        return null;
    }

    /**
     * Get message direction.
     *
     * @return message direction, can be {@code null}
     */
    public final Direction getDirection() {
        return this.direction;
    }

    /**
     * Get message received time.
     *
     * @return message received time, can be {@code null}
     */
    public final Long getReceivedTime() {
        return this.receivedTime;
    }

    /**
     * Get message sent time.
     *
     * @return message sent time, can be {@code null}
     */
    public final Long getSentTime() {
        return this.sentTime;
    }

    /**
     * Exports common data from {@link Message} to {@link String} using JSON interpretation of the message.
     *
     * @return JSON interpretation of the message as {@link String}.
     */
    public String toString() {
        return toJson().toString();
    }

    /**
     * Export the basic properties of the message to {@link JSONObject}.
     *
     * @return message fields in JSONObject format, never {@code null}
     */
    public JSONObject toJson() {
        return Utils.commonFieldsToJson(this);
    }

    /**
     * Convert a {@link List} of messages into a {@link JSONArray}.
     *
     * @param messages The {@link List} of {@link Message}s
     * @return A {@link JSONArray} representing the list of {@link Message}s,
     * never {@code null}.
     */
    public static JSONArray toJson(List<? extends Message> messages) {
        JSONArray jsonArray = new JSONArray();

        for (Message message : messages) {
            if (message != null) {
                jsonArray.put(message.toJson());
            }
        }
        return jsonArray;
    }

    /**
     * Convert a {@link List} of {@link Message}s into a {@link JSONArray}.
     *
     * @param messages The {@link List} of {@link Message}s.
     * @param expand {@code boolean} flag indicating if properties to be
     *               included or not.
     * @return A {@link JSONArray} representing the list of {@link Message}s
     */
    public static JSONArray toJson(List<Message> messages, boolean expand) {
        JSONArray jsonArray = new JSONArray();

        for (Message message : messages) {
            if (message != null) {
                if (expand) {
                    jsonArray.put(message.toJson());
                } else {
                    JSONObject jsonObject =
                        Utils.commonFieldsToJson(message, false);
                    jsonArray.put(jsonObject);
                }
            }
        }
        return jsonArray;
    }

    /**
     * Convert a byte array to a {@link List} of {@link Message}s.
     *
     * @param messagesByteArray {@link byte[]} containing json interpretation of {@link Message}s
     * @return The {@link List} of {@link Message}s from the {@link byte[]}
     * @throws MessageParsingException when messageArray is {@code null}.
     */
    public static List<Message> fromJson(byte[] messagesByteArray) {
        if (messagesByteArray == null) {
            throw new MessageParsingException("message.byteArray.null");
        }

        final String jsonString;
        try {
            jsonString = new String(messagesByteArray, "UTF-8");
        } catch (UnsupportedEncodingException cannot_happen) {
            // Cannot happen. UTF-8 is a required encoding
            // Throw runtime exception to make compiler happy
            throw new RuntimeException(cannot_happen);
        }

        return fromJson(jsonString);
    }

    /**
     * Convert a json string to a {@link List} of {@link Message}s.
     *
     * @param jsonString {@link String} containing json interpretation of
     *                   {@link Message}s
     * @return The {@link List} of {@link Message}s from the jsonString.
     * @throws MessageParsingException when jsonString is {@code null}.
     */
    public static List<Message> fromJson(String jsonString) {
        if (jsonString == null) {
            throw new MessageParsingException("message.jsonString.null");
        }

        try {
            return fromJson(new JSONTokener(jsonString).nextValue());
        } catch (JSONException e) {
            throw new MessageParsingException(e);
        }
    }

    /**
     * Convert a {@link JSONArray} or {@link JSONObject} to a {@link List}
     * of {@link Message}s.
     *
     * @param structure {@link Object} containing {@code Message}s
     * @return The {@link List} of {@link Message}s
     * @throws MessageParsingException when structure is {@code null} or
     * when other exception occur during parsing
     */
    public static List<Message> fromJson(Object structure) {
        if (structure == null) {
            throw new MessageParsingException("message.structure.null");
        }

        List<Message> messageCollection = new ArrayList<Message>();
        if (structure instanceof JSONArray) {
            JSONArray jsonArray = (JSONArray) structure;
            for (int i = 0, size = jsonArray.length(); i < size; i++) {
                JSONObject jsonObject = jsonArray.optJSONObject(i);
                try {
                    messageCollection.add(getMessage(jsonObject));
                } catch (MessageParsingException mpe) {
                    //
                    // When building a list don't quit because of an
                    // unsupported message type
                    //
                    if (mpe.getErrorCode() !=
                            MessageParsingException.INVALID_MSG_TYPE) {
                        throw mpe;
                    }
                } catch (Exception e) {
                    throw new 
                        MessageParsingException("message.parsing.unknown", e);
                }
            }
        } else if (structure instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) structure;
            messageCollection.add(getMessage(jsonObject));
        }

        return messageCollection;
    }

    /**
     * Convert a byte array to a {@link List} of
     * {@link com.oracle.iot.client.message.Message.MessageBuilder}s.
     *
     * @param messagesByteArray {@code byte[]} containing json interpretation
     * of {@link Message}s
     * @return The {@link List} of
     * {@link com.oracle.iot.client.message.Message.MessageBuilder}s from
     * the {@code byte[]}
     * @throws MessageParsingException when messageArray is {@code null}.
     */
    public static List<Message.MessageBuilder> createBuilderFromJson(
            byte[] messagesByteArray) {
        if (messagesByteArray == null) {
            throw new MessageParsingException("message.byteArray.null");
        }

        Object structure;
        try {
            structure = new JSONTokener(
                new String(messagesByteArray, "UTF-8")).nextValue();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        } catch (JSONException e) {
            throw new MessageParsingException(e);
        }

        return createBuilderFromJson(structure);
    }

    private static List<Message.MessageBuilder>
            createBuilderFromJson(Object structure) {
        if (structure == null) {
            throw new MessageParsingException("message.structure.null");
        }

        List<Message.MessageBuilder> messageBuilderList =
            new ArrayList<Message.MessageBuilder>();
        if (structure instanceof JSONArray) {
            JSONArray jsonArray = (JSONArray)structure;
            for (int i = 0, size = jsonArray.length(); i < size; i++) {
                JSONObject jsonObject = jsonArray.optJSONObject(i);
                try {
                    messageBuilderList.add(getMessageBuilder(jsonObject));
                } catch (Exception e) {
                    if (e instanceof MessageParsingException) {
                        throw (MessageParsingException)e;
                    } else {
                        throw new MessageParsingException(
                            "message.parsing.unknown", e);
                    }
                }
            }
        } else if (structure instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) structure;
            messageBuilderList.add(getMessageBuilder(jsonObject));
        }

        return messageBuilderList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Message message = (Message) o;

        if (id == null ? (message.id != null) : (!id.equals(message.id))) return false;
        return clientId.equals(message.clientId);

//        if (!clientId.equals(message.clientId)) return false;
//        if (!eventTime.equals(message.eventTime)) return false;
//        if (!destination.equals(message.destination)) return false;
//        if (priority != message.priority) return false;
//        if (!properties.equals(message.properties)) return false;
//        if (reliability != message.reliability) return false;
//        if (!source.equals(message.source)) return false;
//        if (!sender.equals(message.sender)) return false;
//        if (diagnostics == null ? (message.diagnostics != null) : (!diagnostics.equals(message.diagnostics))) return false;
//        if (direction == null ? (message.direction != null) : (!direction.equals(message.direction))) return false;
//        if (receivedTime == null ? (message.receivedTime != null) : (!receivedTime.equals(message.receivedTime))) return false;
//        return sentTime == null ? message.sentTime == null : sentTime.equals(message.sentTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int result = (id != null) ? id.hashCode():0;
            result = 31 * result + clientId.hashCode();
//        result = 31 * result + source.hashCode();
//        result = 31 * result + destination.hashCode();
//        result = 31 * result + priority.hashCode();
//        result = 31 * result + reliability.hashCode();
//        result = 31 * result + eventTime.hashCode();
//        result = 31 * result + properties.hashCode();
//        result = 31 * result + sender.hashCode();
//        if ( diagnostics != null )
//            result = 31 * result + diagnostics.hashCode();
//        if ( direction != null )
//            result = 31 * result + direction.hashCode();
//        if ( receivedTime != null )
//            result = 31 * result + receivedTime.hashCode();
//        if ( sentTime != null )
//            result = 31 * result + sentTime.hashCode();
        return result;
    }

    /**
     * Method to deserialize one {@link Message} object from JSON interpretation.
     * @param jsonObject JSON interpretation of a {@link Message}.
     * @return {@link Message} object extracted from jsonObject
     * @throws MessageParsingException when json object or does not contain field "type" or type is not supported.
     */
    private static Message getMessage(JSONObject jsonObject) {
        return getMessageBuilder(jsonObject).build();
    }

    private static Message.MessageBuilder getMessageBuilder(
            JSONObject jsonObject) {
        if (jsonObject == null) {
            throw new MessageParsingException("message.structure.null");
        }

        String t = jsonObject.optString("type", null);
        if (t == null) {
            throw new MessageParsingException("message.type.null");
        }

        final Type type;
        try {
            type = Type.valueOf(t);
        } catch (IllegalArgumentException e) {
            throw new MessageParsingException("message.type.illegal", e);
        }

        switch (type) {
        case REQUEST:
            return new RequestMessage.Builder().fromJson(jsonObject);
        case RESPONSE:
            return new ResponseMessage.Builder().fromJson(jsonObject);
        case RESOURCE:
            return new ResourceMessage.Builder().fromJson(jsonObject);
        case DATA:
            return new DataMessage.Builder().fromJson(jsonObject);
        case ALERT:
            return new AlertMessage.Builder().fromJson(jsonObject);
        }

        throw new MessageParsingException("message.type.wrong");
    }

    private final static int indentFactor;
    static {
        final String propertyValue = System.getProperty("oracle.iot.client.pretty_print_messages", "true");
        final boolean prettyPrint = Boolean.parseBoolean(propertyValue);
        indentFactor = prettyPrint ? 4 : 0;
    }

    public static String prettyPrintJson(JSONObject json) {

        if (json == null) {
            return "{}";
        }

        try {
            return json.toString(indentFactor);
        } catch (JSONException e) {
            getLogger().log(Level.SEVERE, e.getMessage());
            return e.getMessage();
        }
    }

    public static String prettyPrintJson(JSONArray json) {

        if (json == null) {
            return "{}";
        }

        try {
            return json.toString(indentFactor);
        } catch (JSONException e) {
            getLogger().log(Level.SEVERE, e.getMessage());
            return e.getMessage();
        }
    }

    public static String prettyPrintJson(String jsonString) {

        if (jsonString == null || (jsonString.length() == 0)) {
            return "{}";
        }

        try {
            final JSONTokener tokener = new JSONTokener(jsonString);
            final Object obj = tokener.nextValue();
            if (obj instanceof JSONObject) {
                return prettyPrintJson((JSONObject)obj);
            } else if (obj instanceof JSONArray){
                return prettyPrintJson((JSONArray)obj);
            } else {
                // not a JSON object or array, just return the string
                return jsonString;
            }
        } catch (JSONException e) {
            return jsonString;
        }

    }

    public static String prettyPrintJson(byte[] json) {

        if (json == null || (json.length == 0)) {
            return "{}";
        }

        final String jsonString;
        try {
            jsonString = new String(json, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // This will never happen since UTF-8 is a required encoding.
            // Throwing RuntimeException here makes the compiler happy.
            throw new RuntimeException(e);
        }
        return prettyPrintJson(jsonString);
    }
    /**
     * A utility class contains methods that can be used in messages.
     * This class is package private.
     */
    static final class Utils {

        //no instances here
        private Utils() {}

        /**
         * Constant defines maximum length of string key in Json
         */
        static final int MAX_KEY_LENGTH = 2048;

        /**
         * Constant defines maximum length of string value in Json
         */
        static final int MAX_STRING_VALUE_LENGTH = 64 * 1024;

        /**
         * Method checks length of the string parameter. It converts it to {@code byte[]} using UTF-8 encoding at first.
         * Then if it is longer than {@link Utils#MAX_KEY_LENGTH} bytes, method throws
         * {@link MessageParsingException}.
         * @param key String param for checking.
         * @param message Text of error to be thrown.
         * @throws MessageParsingException If the key is longer than {@link Utils#MAX_KEY_LENGTH} bytes.
         */
        public static void checkKeyLengthAndThrowMPE(final String key, final String message) {
            try {
                if (key.getBytes("UTF-8").length >= MAX_KEY_LENGTH) {
                    throw new MessageParsingException(message);
                }
            } catch (UnsupportedEncodingException e) {
                // This should never happen since "UTF-8" is valid encoding name
                e.printStackTrace();
            }
        }

        /**
         * Method checks length of the string parameter. It converts it to {@code byte[]} using UTF-8 encoding at first.
         * Then if it is longer than {@link Utils#MAX_STRING_VALUE_LENGTH} bytes, method throws
         * {@link MessageParsingException}.
         * @param value String param for checking.
         * @param message Text of error to be thrown.
         * @throws MessageParsingException If the value is longer than {@link Utils#MAX_STRING_VALUE_LENGTH} bytes.
         */
        public static void checkValueLengthAndThrowMPE(final String value, final String message) {
            try {
                if (value.getBytes("UTF-8").length >= MAX_STRING_VALUE_LENGTH) {
                    throw new MessageParsingException(message);
                }
            } catch (UnsupportedEncodingException e) {
                // This should never happen since "UTF-8" is valid encoding name
                e.printStackTrace();
            }
        }

        /**
         * Method checks whether the value is {@code null} or not. If it is {@code null}, method throws
         * {@link MessageParsingException}.
         * @param value Value for checking for {@code null}
         * @param message Text of error to be thrown.
         * @throws MessageParsingException If the value is {@code null}.
         */
        public static void checkNullValueAndThrowMPE(final Object value, final String message) {
            if (value == null)
                throw new MessageParsingException(message);
        }

        /**
         * Method checks whether the {@link String} value is {@code null} or empty. If it is, method throws
         * {@link MessageParsingException}.
         * @param value Value for checking for {@code null} or empty
         * @param message Text of error to be thrown.
         * @throws MessageParsingException If the value is {@code null} or empty.
         */
        public static void checkNullOrEmptyStringThrowMPE(final String value, final String message) {
            if (value == null || value.length() == 0) {
                throw new MessageParsingException(message);
            }
        }

        /**
         * Method checks length of the string parameter. It converts it to {@code byte[]} using UTF-8 encoding at first.
         * Then if it is longer than {@link Utils#MAX_KEY_LENGTH} bytes, method throws
         * {@link IllegalArgumentException}.
         * @param key String param for checking.
         * @param message Text of error to be thrown.
         * @throws IllegalArgumentException If the key is longer than {@link Utils#MAX_KEY_LENGTH} bytes.
         */
        public static void checkKeyLengthAndThrowIAE(final String key, final String message) {
            try {
                if (key.getBytes("UTF-8").length >= MAX_KEY_LENGTH) {
                    throw new IllegalArgumentException(message + " is longer than " + MAX_KEY_LENGTH + " bytes.");
                }
            } catch (UnsupportedEncodingException e) {
                // This should never happen since "UTF-8" is valid encoding name
                e.printStackTrace();
            }
        }

        /**
         * Method checks length of the string parameter. It converts it to {@code byte[]} using UTF-8 encoding at first. Then
         * if it is longer than {@link Utils#MAX_STRING_VALUE_LENGTH} bytes, method throws
         * {@link IllegalArgumentException}.
         * @param value String param for checking.
         * @param message Text of error to be thrown.
         * @throws IllegalArgumentException If the value is longer than {@link Utils#MAX_STRING_VALUE_LENGTH} bytes.
         */
        public static void checkValueLengthAndThrowIAE(final String value, final String message) {
            if (value == null) return;
            try {
                if (value.getBytes("UTF-8").length >= MAX_STRING_VALUE_LENGTH) {
                    throw new IllegalArgumentException(message + " is longer than " + MAX_STRING_VALUE_LENGTH + " bytes.");
                }
            } catch (UnsupportedEncodingException e) {
                // This should never happen since "UTF-8" is valid encoding name
                e.printStackTrace();
            }
        }

        /**
         * Method checks length of items in the {@link Collection}. It converts them to {@code byte[]} using UTF-8 encoding
         * at first. Then if any is longer than {@link Utils#MAX_STRING_VALUE_LENGTH} bytes,
         * {@link IllegalArgumentException} is thrown.
         * @param values {@link Collection} of {@code String} to be checked.
         * @param message Text of error to be thrown.
         * @throws IllegalArgumentException If any of the the value is longer than {@link Utils#MAX_STRING_VALUE_LENGTH}
         *                                  bytes.
         */
        public static void checkValuesLengthAndThrowIAE(final Collection<String> values, final String message) {
            if (values == null) return;
            for (String value: values) {
                try {
                    if (value.getBytes("UTF-8").length >= MAX_STRING_VALUE_LENGTH) {
                        throw new IllegalArgumentException(message + " contains value longer than " + MAX_STRING_VALUE_LENGTH + " bytes.");
                    }
                } catch (UnsupportedEncodingException e) {
                    // This should never happen since "UTF-8" is valid encoding name
                    e.printStackTrace();
                }
            }
        }

        /**
         * Method checks whether the collection contains non-null values. If the collection is {@code null}, no
         * {@link Exception} is thrown.
         *
         * @param values Checked collection.
         * @param message Text for exception message
         * @throws NullPointerException Exception is thrown when {@link Collection} contains null value
         */
        public static void checkNullValuesThrowsNPE(final Collection<String> values, final String message) {
            if (values == null) {
                return;
            }
            for (String value : values){
                if (value == null) {
                    throw new NullPointerException(message + " contains null value");
                }
            }
        }

        /**
         * Method checks whether the first parameter is {@code null} and returns {@link NullPointerException} accordingly.
         * @param checkedValue Checked value.
         * @param message Text for exception message
         * @throws NullPointerException Exception thrown when checkedValue is null.
         */
        public static void checkNullValueThrowsNPE(final Object checkedValue, final String message) {
            if (checkedValue == null) {
                throw new NullPointerException(message + " is null");
            }
        }

        /**
         * Method checks whether the first parameter is empty and returns {@link IllegalArgumentException} accordingly.
         * @param checkedValue Checked value.
         * @param message Text for exception message
         * @throws IllegalArgumentException Exception thrown when checkedValue is empty.
         */
        public static void checkEmptyStringThrowsIAE(final String checkedValue, final String message) {
            if (checkedValue.length() == 0){
                throw new IllegalArgumentException(message + " is empty");
            }
        }

        /**
         * Method computes hash code for byte array
         * @param a Array of bytes
         * @return hash code
         */
        public static int hashCodeByteArray(final byte a[]) {
            if (a == null)
                return 0;

            int result = 1;
            for (byte element : a)
                result = 31 * result + element;

            return result;
        }

        /**
         * Checks whether characters inside string are US-ASCII printable chars
         * @param text Text for checking
         * @return true, if the text is contain only US-ASCII printable characters or for null strings
         */
        public static boolean isAsciiPrintable(final String text) {
            if (text == null)
                return true;
            for (char ch : text.toCharArray()) {
                if (ch < 32 || ch > 126)
                    return false;
            }
            return true;
        }

        /**
         * Checks whether strings inside collection contain US-ASCII printable chars only
         *
         * @param texts Collection of string to be checked
         * @return true, if the texts contain only US-ASCII printable characters or for null collection
         */
        public static boolean isAsciiPrintable(final Collection<String> texts) {
            if (texts == null) return true;
            for (String text : texts) {
                if (!isAsciiPrintable(text)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Check whether the http header contains US-ASCII printable characters only
         * @param name name of the header
         * @param values values of the header
         * @return true, if all string contain US-ASCII printable characters
         */
        public static boolean isHttpHeaderAsciiPrintable(final String name, final List<String> values) {
            return isAsciiPrintable(name) && isAsciiPrintable(values);
        }

        /**
         * Returns a {@link JSONObject} containing common fields of the message.
         * @param message the message to fromString
         * @return {@link JSONObject}, never {@code null}
         */
        public static JSONObject commonFieldsToJson(Message message) {
            return commonFieldsToJson(message, true);
        }

        /**
         * Returns a {@link JSONObject} containing common fields of the message.
         * @param message the message to fromString
         * @param expand {@code boolean} flag indicating if properties to be
         *               included or not.
         * @return {@link JSONObject}, never {@code null}
         */
        public static JSONObject commonFieldsToJson(Message message,
                                                    boolean expand) {
            JSONObject jsonObject = new JSONObject();

            try {
                if (message.getId() != null) {
                    jsonObject.put("id", message.getId());
                }

                if ( message.getClientId() != null) {
                    jsonObject.put("clientId", message.getClientId());
                }

                jsonObject.put("source", message.getSource());
                jsonObject.put("destination", message.getDestination());
                jsonObject.put("priority", message.getPriority().toString());
                jsonObject.put("reliability",
                    message.getReliability().toString());
                jsonObject.put("eventTime",
                    (message.getEventTime().longValue()));
                jsonObject.put("sender", message.getSender());
                jsonObject.put("type", message.getType().name());

                if (expand) {
                    final MessageProperties messageProperties = message.getProperties();
                    if (messageProperties != null && !messageProperties.getAllProperties().isEmpty()) {
                        jsonObject.put("properties", messageProperties.toJson());
                    }
                }

                if ( message.getDirection() != null ){
                    jsonObject.put("direction", message.getDirection().name());
                }

                if ( message.getReceivedTime() != null ){
                    jsonObject.put("receivedTime", message.getReceivedTime());
                }

                if ( message.getSentTime() != null ){
                    jsonObject.put("sentTime", message.getSentTime());
                }

                if ( message.getDiagnostics() != null ){
                    jsonObject.put("diagnostics",
                        toJson(message.getDiagnostics()));
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }

            return jsonObject;
        }

        /**
         * Convert a {@link Map} of values into a {@link JSONObject}.
         *
         * @param map The {@link Map} of values
         * @return A {@link JSONObject} representing the list of values,
         *         never {@code null}.
         */
        public static JSONObject toJson(Map<String,Object> map) {
            JSONObject jsonObject = new JSONObject();
            Set<Map.Entry<String,Object>> entries = map.entrySet();
            for(Map.Entry<String,Object> entry : entries) {
                final String key = entry.getKey();
                final Object value = entry.getValue();
                if (value instanceof Boolean || value instanceof Number ||
                        value instanceof String ) {
                    try {
                         jsonObject.put(key, value);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            return jsonObject;
        }

        /**
         * This method fromString {@link java.util.Date} to {@link String} in format "yyyy-MM-dd'T'HH:mm:ss.SSSXXX".
         * The string representation is then used in JSON.
         *
         * @param date {@link Date} to be converted.
         * @return {@link String} representation of the the date.
         */
        public static String dateToString(final Date date) {
            final SimpleDateFormat dateFormat = DATE_FORMATTER.get();
            return dateFormat.format(date);
        }

        /**
         * This method fromString {@link String} representation of the date in format "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
         * to {@link Date}.
         *
         * @param date String representation of the date.
         * @return {@link Date}.
         */
        public static Date stringToDate(final String date) {
            final SimpleDateFormat dateFormat = DATE_FORMATTER.get();
            return dateFormat.parse(date, new java.text.ParsePosition(0));
        }


        private static final ThreadLocal<SimpleDateFormat> DATE_FORMATTER = new ThreadLocal<SimpleDateFormat>() {
            @Override
            protected SimpleDateFormat initialValue() {
                return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT);
            }
        };

        /**
         * Extract items from payload data and add them into
         * {@link AlertMessage} or {@link DataMessage} Builder.
         * @param jsonObject - json object representing data or alert message
         * @param items - items for to be filled from {@param jsonObject}.
         */
        static void dataFromJson(JSONObject jsonObject,
                List<DataItem<?>> items) {
            try {
                JSONObject payload = jsonObject.optJSONObject("payload");
                if (payload == null) {
                    return;
                }

                JSONObject data = payload.getJSONObject("data");
                Iterator<String> keys = data.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object value = data.opt(key);

                    Message.Utils.addItem(key, value, items);
                }
            } catch (JSONException e) {
                throw new MessageParsingException(e);
            }

            return;
        }

        private static List<DataItem<?>> addItem
                (String key, Object newValue, List<DataItem<?>> dataItems) {
            if (newValue instanceof Number) {
                dataItems.add(
                    new DataItem(key,((Number)newValue).doubleValue()));
            } else if (newValue instanceof String) {
                dataItems.add(new DataItem(key, (String)newValue));
            } else if (newValue instanceof Boolean) {
                dataItems.add(
                    new DataItem(key,((Boolean)newValue).booleanValue()));
            }

            return dataItems;
        }

        static JSONObject dataToJson(Message message,
                List<DataItem<?>> items, String format, String description,
                String severity) {
            JSONObject jsonObject = Utils.commonFieldsToJson(message);
            JSONObject payload = new JSONObject();
            JSONObject dataItems = new JSONObject();

            try {
                for (DataItem<?> item : items) {
                    switch(item.getType()) {
                    case STRING:
                    case BOOLEAN:
                    case DOUBLE:
                        dataItems.put(item.getKey(), item.getValue());
                        break;
                    }
                }

                payload.put("data", dataItems);

                if (format != null) {
                    payload.put("format", format);
                }

                if (description != null) {
                    payload.put("description", description);
                }

                if (severity != null) {
                    payload.put("severity", severity);
                }

                jsonObject.put("payload", payload);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }

            return jsonObject;
        }

        static JSONObject bodyToJson(Message message,
                Map<String, String> params, Type type, StatusCode statusCode, 
                String method, String url, String requestId,
                Map<String, List<String>> headers, byte[] body) {
            JSONObject jsonObject = Utils.commonFieldsToJson(message);
            JSONObject payload = new JSONObject();
            JSONObject headerObject = new JSONObject();

            try {
                if (type != null) {
                    jsonObject.put("type", type.name());
                }

                if (statusCode != null) {
                    payload.put("statusCode", statusCode.getCode());
                }

                if (method != null) {
                    payload.put("method", method);
                }

                if (url != null) {
                    payload.put("url", url);
                }

                if (requestId != null) {
                    payload.put("requestId", requestId);
                }

                Set<Map.Entry<String,List<String>>> entries =
                    headers.entrySet();
                for (Map.Entry<String,List<String>> entry : entries) {
                    JSONArray headerValues = new JSONArray();
                    String key = entry.getKey();
                    List<String> values = entry.getValue();
                    for (String v : values) {
                        headerValues.put(v);
                    }
                    headerObject.put(key, headerValues);
                }

                payload.put("headers", headerObject);

                if (params != null) {
                    JSONObject paramsObject = new JSONObject();
                    for (Map.Entry<String, String> entry : params.entrySet()) {
                        paramsObject.put(entry.getKey(), entry.getValue());
                    }
                    payload.put("params", paramsObject);
                }

                try {
                    payload.put("body",
                        new String(Base64.getEncoder().encode(body), "UTF-8"));
                } catch (java.io.UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }

                jsonObject.put("payload", payload);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }

            return jsonObject;
        }
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
    
