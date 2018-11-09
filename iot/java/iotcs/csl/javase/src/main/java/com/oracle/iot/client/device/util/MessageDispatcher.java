/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.device.util;

import com.oracle.iot.client.device.DirectlyConnectedDevice;
import com.oracle.iot.client.impl.device.MessageDispatcherImpl;
import com.oracle.iot.client.device.persistence.MessagePersistence;
import com.oracle.iot.client.message.Message;

import java.io.Closeable;
import java.util.WeakHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The MessageDispatcher queues messages for automatic dispatching to the
 * for the Oracle IoT Cloud Service.
 * The MessageDispatcher prioritizes message dispatching to ensure
 * high priority messages are dispatched ahead of lower priority messages.
 * There is one MessageDispatcher instance per client.
 */
public abstract class MessageDispatcher implements Closeable {

    // TODO: this leaks. MessageDispatcherImpl holds DirectlyConnectedDevice. Maybe move all this to impl.
    private final static Map<DirectlyConnectedDevice,MessageDispatcher> dispatcherMap =
        new WeakHashMap<DirectlyConnectedDevice,MessageDispatcher>();

    /**
     * Get the instance of a MessageDispatcher for the DirectlyConnectedDevice.
     * This is an implementation detail and is called from the implementation of
     * the virtualization API.
     *
     * @param directlyConnectedDevice is a gateway or directly connected device
     * @return A MessageDispatcher for the DirectlyConnectedDevice.
     */
    public static MessageDispatcher getMessageDispatcher(DirectlyConnectedDevice directlyConnectedDevice) {
        synchronized (dispatcherMap) {
            MessageDispatcher messageDispatcher = dispatcherMap.get(directlyConnectedDevice);
            if (messageDispatcher == null) {
                messageDispatcher = new MessageDispatcherImpl(directlyConnectedDevice);
                dispatcherMap.put(directlyConnectedDevice, messageDispatcher);
            }
            return messageDispatcher;
        }
    }

    /**
     * Set the instance of a MessageDispatcher for the DirectlyConnectedDevice.
     * This is an implementation detail and is called from the implementation of
     * the virtualization API.
     *
     * @param directlyConnectedDevice is a gateway or directly connected device
     * @param messageDispatcher is the MessageDispatcher for the directly connected device
     */
    public static void setMessageDispatcher(DirectlyConnectedDevice directlyConnectedDevice, MessageDispatcher messageDispatcher) {
        synchronized (dispatcherMap) {
            dispatcherMap.put(directlyConnectedDevice, messageDispatcher);
        }
    }

    /**
     * Remove the instance of a MessageDispatcher for the DirectlyConnectedDevice. After calling
     * {@code removeMessageDispatcher}, a call to {@link #getMessageDispatcher(DirectlyConnectedDevice)}
     * will return {@code null}.
     * This is an implementation detail and is called from the implementation of
     * the virtualization API.
     @param directlyConnectedDevice is a gateway or directly connected device
     * @return the MessageDispatcher for the DirectlyConnectedDevice, or {@code null}
     */
    public static MessageDispatcher removeMessageDispatcher(DirectlyConnectedDevice directlyConnectedDevice) {
        synchronized (dispatcherMap) {
            return dispatcherMap.remove(directlyConnectedDevice);
        }
    }

    /**
     * Get the RequestDispatcher that is used by this MessageDispatcher for
     * handling RequestMessages.
     * @return a RequestDispatcher
     * @see RequestDispatcher
     */
    public abstract RequestDispatcher getRequestDispatcher();

    /**
     * Add the messages to the outgoing message queue if it is possible to
     * do so without violating capacity restrictions.
     * @param messages The messages to be queued
     * @throws ArrayStoreException if all the messages cannot be added to the queue
     * @throws IllegalArgumentException if {@code messages} is null or empty
     */
    public abstract void queue(Message... messages);

    /**
     * Add the message to the outgoing message queue if it is possible to
     * do so without violating capacity restrictions.
     * @param message The message to be queued
     * @throws ArrayStoreException if the message cannot be added to the queue
     * @throws IllegalArgumentException if the message is null
     */
    public void queue(Message message) {
        // delegate to queue(Message... messages)
        queue(new Message[]{message});
    };

    /**
     * Offer messages to be queued. Depending on the policies, if any, the messages will
     * be queued if it is possible to do so without violating capacity restrictions.
     * @param messages The messages to be queued
     * @throws ArrayStoreException if all the messages cannot be added to the queue
     * @throws IllegalArgumentException if {@code messages} is null or empty
     */
    public abstract void offer(Message... messages);

    /**
     * Offer a message to be queued. Depending on the policies, if any, the message will
     * be queued if it is possible to do so without violating capacity restrictions.
     * @param message The message to be queued
     * @throws ArrayStoreException if all the messages cannot be added to the queue
     * @throws IllegalArgumentException if {@code messages} is null or empty
     */
    public void offer(Message message) {
        // delegate to queue(Message... messages)
        offer(new Message[]{message});
    };

    /**
     * A callback interface for successful delivery of messages.
     */
    public interface DeliveryCallback {
        /**
         * Notify that messages have been successfully dispatched. This callback
         * indicates that the messages have been delivered to, and accepted by,
         * the server and are no longer queued.
         * @param messages the messages that were delivered
         */
        void delivered(List<Message> messages);
    }

    /**
     * A callback interface for errors in delivery of messages.
     */
    public interface ErrorCallback {
        /**
         * Notify that an error occurred when attempting to deliver messages
         * to the server. This callback indicates that the messages have
         * not been delivered to the server and are no longer queued.
         * @param messages the messages that were not delivered
         * @param exception the exception that was raised when attempting
         *                  to deliver the messages.
         */
        void failed(List<Message> messages, Exception exception);
    }

    /**
     * Set a callback to be notified if there is an error in dispatching.
     * @param callback callback to invoke, if {@code null},
     *                 the existing callback will be removed
     */
    public abstract void setOnError(ErrorCallback callback);

    /**
     * Set a callback to be notified if message is successfully delivered.
     * @param callback callback to invoke, if {@code null},
     *                 the existing callback will be removed
     */
    public abstract void setOnDelivery(DeliveryCallback callback);

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }

}
