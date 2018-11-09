/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.impl.enterprise;

import com.oracle.iot.client.enterprise.MessageEnumerator.MessageListener;

import com.oracle.iot.client.impl.http.HttpSecureConnection;
import com.oracle.iot.client.message.Message;
import oracle.iot.client.enterprise.Pageable;

import java.io.IOException;

import java.security.GeneralSecurityException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Polls for messages and forwards new messages to listeners using a
 * {@code MessageIterator} to enumerate messages matching a set
 * of criteria.
 * <p>
 *     The {@code MessagePoller} polls the server once every 3000 milliseconds.
 *     This can be configured with the
 *     {@code com.oracle.iot.client.enterprise.message_polling_interval} property.
 * </p><p>
 *     The {@code MessagePoller} returns 10 messages per poll.
 *     This can be configured with the
 *     {@code com.oracle.iot.client.enterprise.message_polling_limit} property.
 *     The value for this property is clamped between 10 and 200, inclusive.
 *</p>
 *
 */
public final class MessagePoller implements Runnable {
    private static final int DEFAULT_POLL_INTERVAL = 3000;
    private static final int MIN_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;

    /**
     * Delay polling for messages in milliseconds, default 3 seconds
     */
    private static final int pollInterval;

    /**
     * max number of messages refilled at a time
     */
    private static final int limit;

    static {
        Integer val = Integer.getInteger(
            "com.oracle.iot.client.enterprise.message_polling_interval");
        pollInterval =
            ((val != null) && (val > 0))? val : DEFAULT_POLL_INTERVAL;

        val = Integer.getInteger(
                "com.oracle.iot.client.enterprise.message_polling_limit");
        limit = (val != null)
                ? Math.min(MAX_PAGE_SIZE, Math.max(MIN_PAGE_SIZE, val))
                : MIN_PAGE_SIZE;
    }

    private static final HashMap<String, RestParameters> listeners =
        new HashMap<String, RestParameters>(10);

    private static boolean running = false;

    /**
     * Allows to set an listener that gets notified when a message is received
     * from the specified device.
     * <p>
     * When the listener is {@code null}, the method removes the listener for
     * this device.
     * <p>
     * Note: only one listener can be set per device.
     *
     * @param appID            the application identifier
     * @param secureConnection a connection to the server.
     * @param deviceId         the device to listen.
     *                         If {@code null}, all devices are listened.
     * @param type             the type of messages to listen
     *                         If {@code null}, all message types are listened.
     * @param listener         the listener to notify when a message is received.
     *                         If {@code null}, the listener is removed for the
     *                         device.
     * @throws IllegalStateException    if an listener has already been set for
     *                                  this device
     * @throws IOException              if request for messages failed
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid
     */
    public static void setListener(String appID, HttpSecureConnection secureConnection,
            String deviceId, Message.Type type, MessageListener listener)
            throws IllegalStateException, IOException,
            GeneralSecurityException {
        if (listener == null) {
            removeListener(deviceId);
            return;
        }

        synchronized (listeners) {
            if (listeners.containsKey(deviceId)) {
                throw new IllegalStateException(
                    "Already listening for device " + deviceId);
            }
        }

        // Initialize REST parameters
        RestParameters rp = new RestParameters(
                appID,
                secureConnection,
                deviceId,
                type,
                listener);

        // Initialize 'since' parameter with the time of latest message received by the server
        rp.since = getStartTime(rp);

        synchronized (listeners) {
            if (!running) {

                running = true;
                Thread t = new Thread(new MessagePoller());

                // Allow the VM to exit if this thread is still running
                t.setDaemon(true);
                t.start();
            }
        }

        synchronized (listeners) {
            listeners.put(deviceId, rp);
        }
    }

    /**
     * Stop listening for messages for a given device.
     *
     * @param deviceId ID of the device
     */
    private static void removeListener(String deviceId) {
        synchronized (listeners) {
            listeners.remove(deviceId);
        }
    }

    /**
     * Gets the time (millisecond) of the last message received by the server matching criteria specified
     * by the {@code RestParameters}.
     *
     * @param rp the REST parameters used to get the last message
     * @return the time of the last message received by the server
     *
     * @throws IOException if request for messages failed
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid
     */
    private static long getStartTime(RestParameters rp) throws IOException, GeneralSecurityException {
        MessageIterator messages = new MessageIterator(rp.appID, 0, 1, rp.deviceID, rp.type, true, 0, 0, rp.secureConnection, true);

        if (!messages.hasMore()) {
            return 0;
        }
        return messages.next().elements().iterator().next().getReceivedTime() + 1L;
    }

    /**
     * Performs a new REST call to get messages for a device.
     *
     * @param rp REST parameters
     * @return a page of messages
     *
     * @throws IOException if request for messages failed
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid
     */
    private static Pageable<Message> poll(RestParameters rp)
            throws IOException, GeneralSecurityException {

        // If no pending page, create a new message iterator using stored parameters
        if ((rp.messages == null) || !rp.messages.hasMore()){
            rp.messages = new MessageIterator(
                    rp.appID,
                    0,
                    limit,
                    rp.deviceID,
                    rp.type,
                    true,
                    rp.since,
                    0,
                    rp.secureConnection,
                    false);

        }
        return rp.messages;
    }

    /**
     * Instantiate a new {@code MessagePoller}.
     */
    private MessagePoller() {
    }

    public void run() {
        poll();
    }

    private static void poll() {
        boolean sleep;

        while (running) {
            ArrayList<RestParameters> list = null;
            synchronized (listeners) {
                sleep = true;
                list = new ArrayList<RestParameters>(listeners.values());
            }
            /*
             * loop on all REST request parameters to poll for all
             * messages expected by listeners
             */
            for (RestParameters rp: list) {
                try {
                        // get a page on messages matching with parameters
                        Pageable<Message> messages = poll(rp);

                        if (messages.hasMore()) {
                            // get next messages available (REST call)
                            messages = messages.next();
                            List<Message> elements =
                                (List<Message>)messages.elements();
                            int size = elements.size();
                            if (size > 0 && rp.lastMsgId != null) {
                                /*
                                 * There should be at least one duplicate
                                 * message because we used the since from
                                 * the last message received and it is
                                 * inclusive.
                                 *
                                 * Find the message we last gave the listener,
                                 * and remove that message and the ones
                                 * before it. If for some reason we don't
                                 * find the message, do not remove any messages.
                                 */
                                int i = 0;
                                for (; i < size; i++) {
                                    Message m = elements.get(i);
                                    if (m.getId().equals(rp.lastMsgId)) {
                                        break;
                                    }

                                    if (m.getReceivedTime() > rp.since) {
                                        i = size;
                                        break;
                                    }
                                }

                                if (i < size) {
                                    for (; i >= 0; i--) {
                                        elements.remove(0);
                                    }
                                }

                                size = elements.size();
                            }

                            if (size == 0) {
                                /*
                                 * There are no more messages for the time
                                 * of last message given to the
                                 * listener, so next time we can just use
                                 * since + 1 and not process the message list.
                                 */
                                rp.lastMsgId = null;
                                rp.since += 1;
                                continue;
                            }

                            // notify listeners with messages received
                            rp.listener.notify(elements);

                            /*
                             * get time of last message received and use it as
                             * basis for next REST call
                             */
                            Message last = elements.get(elements.size() - 1);
                            rp.lastMsgId = last.getId();
                            rp.since = last.getReceivedTime();

                            /*
                             * if at least one page has more elements,
                             * no need to sleep
                             */
                            if (messages.hasMore()) {
                                sleep = false;
                            }
                        }
                    } catch (Exception ignored) {
                        getLogger().log(Level.SEVERE, ignored.toString(), ignored);
                    }
            }

            try {
                if (sleep) {
                    synchronized (listeners) {
                        listeners.wait(pollInterval);
                    }
                }
            } catch (InterruptedException e) {
                synchronized (listeners) {
                    running = false;
                }
            }
        }
    }

    private static class RestParameters {

        // REST request immutable parameters
        final String appID;
        final HttpSecureConnection secureConnection;
        final String deviceID;
        final Message.Type type;
        final MessageListener listener;

        // Used to iterate over messages: starting time for next messages to to receive
        long since;
        String lastMsgId;

        // Last set of messages received (null if no messages received yet)
        Pageable<Message> messages;

        RestParameters(String appID,
                       HttpSecureConnection secureConnection,
                       String deviceID,
                       Message.Type type,
                       MessageListener listener) {
            this.appID = appID;
            this.secureConnection = secureConnection;
            this.deviceID = deviceID;
            this.type = type;
            this.listener = listener;

            // start with no messages
        }
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
