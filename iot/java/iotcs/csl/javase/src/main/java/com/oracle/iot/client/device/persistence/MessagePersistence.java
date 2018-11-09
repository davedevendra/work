/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.device.persistence;

import com.oracle.iot.client.message.Message;

import java.util.Collection;
import java.util.List;

/**
 * Support for persisting messages for guaranteed delivery.
 */
public abstract class MessagePersistence {

    /**
     * Initialize the message persistence instance.
     * This is an implementation detail and is called from the implementation of
     * the virtualization API.
     * @param context an application context, or {@code null}
     * @return the message persistence instance.
     */
    public static MessagePersistence initMessagePersistence(Object context) {
        MessagePersistence instance = MessagePersistence.messagePersistence;
        if (instance == null) {
            // double-check locking on volatile
            synchronized (MessagePersistence.class) {
                instance = MessagePersistence.messagePersistence;
                if (instance == null) {
                    try {
                        instance = MessagePersistence.messagePersistence =
                                new MessagePersistenceImpl(context);
                    } catch (Exception e) {
                        // TODO: try-catch here is because unit tests on Android give Stub! on SQLite
                        instance = null;
                    }
                }
            }
        }
        return instance;
    }

    /**
     * Get the message persistence instance.
     * @return the message persistence instance.
     */
    public static MessagePersistence getInstance() {
        synchronized (MessagePersistence.class) {
            return MessagePersistence.messagePersistence;
        }
    }

    /**
     * Save the given collection of messages to persistent storage. Null or empty collections
     * are supported, just ignored. Null entries in the collection are ignored. This method
     * will block until the job is complete.
     *
     * @param m The messages to save
     * @param endpointId Id of the subsystem that the messages belongs to
     */
    public abstract void save(Collection<Message> m, String endpointId);

    /**
     * Delete the given collection of messages from persistent storage. Null or empty collections
     * are supported, just ignored. Null entries in the collection are ignored. This method will
     * block until the job is complete.
     *
     * @param m The messages to delete
     */
    public abstract void delete(Collection<Message> m);

    /**
     * Loads the set of persisted messages from disk that belongs to the subsystem identified by endpointId.
     *
     * @param endpointId Id of the subsystem that the caller want to load messages for
     * @return The messages that were persisted for given subsystem. Should not return null (it would be an error to do so)
     *         but null returns are treated as empty.
     */
    public abstract List<Message> load(String endpointId);

    /**
     * Base class constructor for implementations
     */
    protected MessagePersistence() {}

    private static MessagePersistence messagePersistence = null;

}
