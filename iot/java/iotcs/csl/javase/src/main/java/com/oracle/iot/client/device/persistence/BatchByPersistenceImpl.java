/*
 * Copyright (c) 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.device.persistence;

import com.oracle.iot.client.message.Message;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Support for persisting batched messages from "batchBy" policy functions
 */
public class BatchByPersistenceImpl extends BatchByPersistence {

    /**
     * Create an implementation of BatchByPersistence.
     * @param messagePersistence the application {@code android.content.Context}
     */
    public BatchByPersistenceImpl(MessagePersistence messagePersistence) {
        super();
        this.messageCollection = new ArrayList<Message>(1);
        this.delegate = (MessagePersistenceImpl)messagePersistence;

        if (!this.delegate.tableExists("BATCH_BY")) {
            this.delegate.createTable("BATCH_BY");
        }

    }

    @Override
    public void save(Message message, String endpointId) {
        synchronized (messageCollection) {
            try {
                messageCollection.add(message);
                this.delegate.save("BATCH_BY", messageCollection, endpointId);
            } finally {
                messageCollection.clear();
            }
        }
    }

    @Override
    public void save(Collection<Message> m, String endpointId) {
        this.delegate.save("BATCH_BY", m, endpointId);
    }

    @Override
    public void delete(Collection<Message> m) {
        this.delegate.delete("BATCH_BY", m);
    }

    @Override
    public List<Message> load(String endpointId) {
        return this.delegate.load("BATCH_BY", endpointId);
    }

    private final MessagePersistenceImpl delegate;
    private final Collection<Message> messageCollection;
}
