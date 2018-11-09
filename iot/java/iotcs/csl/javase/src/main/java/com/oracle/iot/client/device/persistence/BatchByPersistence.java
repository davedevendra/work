/*
 * Copyright (c) 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.device.persistence;

import com.oracle.iot.client.message.Message;

/**
 * Support for persisting messages for guaranteed delivery.
 */
public abstract class BatchByPersistence extends MessagePersistence {

    /**
     * Initialize the batch-by persistence instance.
     * This is an implementation detail and is called from the implementation of
     * the virtualization API.
     * @param messagePersistence an application context, or {@code null}
     * @return the batch-by persistence instance.
     */
    public static BatchByPersistence initBatchByPersistence(MessagePersistence messagePersistence) {
        BatchByPersistence instance = BatchByPersistence.batchByPersistence;
        if (instance == null) {
            synchronized (BatchByPersistence.class) {
                instance = BatchByPersistence.batchByPersistence;
                if (instance == null) {
                    try {
                        instance = BatchByPersistence.batchByPersistence =
                            messagePersistence != null
                                ? new BatchByPersistenceImpl(messagePersistence)
                                : null;
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
     * Get the batch-by persistence instance.
     * @return the batch-by persistence instance.
     */
    public static BatchByPersistence getInstance() {
        synchronized (BatchByPersistence.class) {
            return BatchByPersistence.batchByPersistence;
        }
    }

    /**
     * Save a message to the batch-by persistence table.
     * @param message the message to be saved
     * @param endpointId the endpoint id
     */
    public abstract void save(Message message, String endpointId);

    /**
     * Base class constructor for implementations
     */
    protected BatchByPersistence() {}

    private static volatile BatchByPersistence batchByPersistence;

}
