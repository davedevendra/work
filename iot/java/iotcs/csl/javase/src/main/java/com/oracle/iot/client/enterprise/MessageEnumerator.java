/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.enterprise;

import com.oracle.iot.client.impl.enterprise.MessageIterator;
import com.oracle.iot.client.impl.enterprise.MessagePoller;

import com.oracle.iot.client.impl.enterprise.SecureHttpConnectionMap;
import com.oracle.iot.client.impl.http.HttpSecureConnection;
import com.oracle.iot.client.message.Message;

import oracle.iot.client.enterprise.EnterpriseClient;
import oracle.iot.client.enterprise.Pageable;
import oracle.iot.client.enterprise.UserAuthenticationException;

import java.io.IOException;

import java.security.GeneralSecurityException;

import java.util.Collection;


/**
 * Provides a means to lookup messages stored by cloud service.
 */
public class MessageEnumerator {

    private static final int MIN_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;

    /**
     * max number of messages refilled at a time
     */
    private static final int limit;

    static {
        Integer val = Integer.getInteger(
                "oracle.iot.client.enterprise.message_enumerator_limit");
        limit = (val != null)
                ? Math.min(MAX_PAGE_SIZE, Math.max(MIN_PAGE_SIZE, val))
                : MIN_PAGE_SIZE;
    }

    /**
     * The secure connection to use
     */
    private final HttpSecureConnection secureConnection;

    /**
     * The application identifier: namespace to use to look for messages
     */
    private final String appID;

    /**
     * Creates a message enumerator to retrieve messages.
     * @param client the client to use to retrieve messages
     *
     * @throws NullPointerException if the client is {@code null}
     */
    public MessageEnumerator(EnterpriseClient client) {
        if (client == null) {
            throw new NullPointerException();
        }

        String id = null;
        if (client.getApplication() != null) {
            id = client.getApplication().getId();
        }
        this.appID = id;

        this.secureConnection =
            SecureHttpConnectionMap.getSecureHttpConnection(client);
    }

    /**
     * Returns a list of messages stored by cloud service matching the given query criteria.
     * <p>
     * The query criteria can be based on either a specific device or a specific type of message.
     * The messages are always returned in descending order of message
     * received-time (for messages sent by the device) or message sent-time
     * (for messages sent to the device) as recorded by the cloud service.
     * </p>
     * <p>
     *     The efficiency of this method depends on the number
     *     of messages being accessed. If the {@code deviceId} parameter is null,
     *     messages from any device matching the streams configured for the
     *     enterprise integration will be listed. Streams determine which kinds
     *     of data messages and alerts will be processed and used by an
     *     enterprise integration. The greater the number of devices in the
     *     stream, the greater the potential impact on the efficiency of this
     *     method. Consider using {@link MessageIterator}, which gives greater
     *     control over the message enumeration parameters, when accessing
     *     messages for all devices in a stream.
     * </p>
     * <p>
     *     The efficiency of this method also depends on
     *     the type of messages being accessed. If the {@code type} parameter
     *     is null, all message types are enumerated.
     *     Consider using {@link MessageIterator}, which gives greater
     *     control over the message enumeration parameters, when accessing
     *     messages for all message types in a stream.
     * </p>
     * <p>
     *     By default, the {@code Pageable} will contain at most 10 messages.
     *     This can be configured by setting the property
     *     {@code oracle.iot.client.enterprise.message_enumerator_limit} to an
     *     integer value between 10 and 200, inclusive.
     * </p>
     *
     * @param deviceID   the identifier of the device to get messages.
     *                   If {@code null}, messages coming from any device are listed.
     * @param type       the type of messages to enumerate
     *                   If {@code null}, all message types are enumerated.
     * @param expand     indicates that messages should include the message
     *                   payload along with core message properties.
     * @param since      Epoch time in milliseconds used to retrieve the messages
     *                   with message recorded time (either received-time or sent-time) by the
     *                   cloud service that is greater than or equal to this time. If the
     *                   value is 0 the parameter is ignored.
     * @param until      Epoch time in milliseconds used to retrieve the messages
     *                   with message recorded time (either received-time or sent-time) by the
     *                   cloud service that is less than this time. If the value is 0
     *                   the parameter is ignored.
     * @return an {@link Iterable} of {@link Message}
     * @throws IOException              if request for messages failed
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid. If User Authentication is used,
     *                                  then {@link UserAuthenticationException} will be thrown
     *                                  if the session cookie has expired.
     */
    public Pageable<Message> getMessages(String deviceID, Message.Type type,
            final boolean expand, final long since,
            final long until) throws IOException, GeneralSecurityException {
        return new MessageIterator(this.appID, 0, limit, deviceID, type, expand, since, until, secureConnection, false);
    }

    /**
     * An interface used to notify a listener when messages are received.
     * @see #setListener(String, Message.Type, MessageListener)
     */
    public interface MessageListener {
        /**
         * Method called when messages are received.
         * @param msgReceived the messages received
         */
        void notify(Collection<Message> msgReceived);
    }

    /**
     * Set a listener that gets notified when a message is received from the specified device.
     * When the listener is {@code null}, the method removes the listener for this device.
     * Only one listener can be set per device.
     * <p>
     *     This method calls the {@code MessagePoller.setListener} API.
     *     The {@code MessagePoller} sends a query to the server once every 3000 milliseconds.
     *     This can be configured with the
     *     {@code com.oracle.iot.client.enterprise.message_polling_interval} property.
     *     By default, the {@code MessagePoller} returns 10 messages per query.
     *     This can be configured with the
     *     {@code com.oracle.iot.client.enterprise.message_polling_limit} property.
     *     The value for this property may be between 10 and 200, inclusive.
     *</p><p>
     *     The efficiency of the {@code MessagePoller} depends on the number
     *     of messages being accessed. If the {@code deviceId} parameter is null,
     *     messages from any device matching the streams configured for the
     *     enterprise integration will be listed. Streams determine which kinds
     *     of data messages and alerts will be processed and used by an
     *     enterprise integration. The greater the number of devices in the
     *     stream, the greater the potential impact on the efficiency of this
     *     method.
     * </p>
     * @param deviceId the device to listen.
     *                 If {@code null}, messages coming from any device are listened.
     * @param type     the type of messages to listen.
     *                 If {@code null}, all message types are listened.
     * @param listener the listener to notify when a message is received.
     *                 If {@code null}, removes the listener for the specified device.
     *
     * @throws IllegalStateException if an listener has already been set for this device
     * @throws IOException if request for the time of the last message failed
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid.  If User Authentication is used,
     *                                  then {@link UserAuthenticationException} will be thrown
     *                                  if the session cookie has expired.
     */
    public void setListener(String deviceId, Message.Type type, MessageListener listener)
            throws IllegalStateException, IOException,
            GeneralSecurityException {

        MessagePoller.setListener(this.appID, this.secureConnection, deviceId, type, listener);
    }
}
