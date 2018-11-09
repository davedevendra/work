/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl;

import oracle.iot.client.AbstractVirtualDevice;
import oracle.iot.client.StorageObject;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * StorageObjectBase is the common implementation between the
 * implementations of oracle.iot.client.impl.device.StorageObject,
 * namely com.oracle.iot.client.impl.device.StorageObjectImpl and
 * com.oracle.iot.client.impl.enterprise.StorageObjectImpl
 */
public abstract class StorageObjectBase extends StorageObject {

    private final com.oracle.iot.client.StorageObject delegate;
    private final List<SyncEvent> syncEvents;

    public StorageObjectBase(com.oracle.iot.client.StorageObject delegate) {
        super(delegate);
        this.delegate = delegate;
        this.syncEvents = Collections.synchronizedList(new LinkedList<SyncEvent>());
    }

    @Override
    public void sync() {
        if (getSyncStatus() == SyncStatus.NOT_IN_SYNC) {
            if (getOutputPath() == null && getInputPath() == null) {
                throw new IllegalStateException("input path or output path must be set");
            }
            setSyncStatus(SyncStatus.SYNC_PENDING);
            addSyncEvent(createSyncEvent());
        } else {
            addSyncEvent(createSyncEvent());
        }
    }


    @Override
    final public <V extends AbstractVirtualDevice> void setOnSync(SyncCallback<V> callback) {
        super.setOnSync(callback);
        syncEvents.clear();
    }

    final protected com.oracle.iot.client.StorageObject getDelegate() {
        return delegate;
    }

    final protected List<SyncEvent> getSyncEvents() {
        return syncEvents;
    }

    final protected void addSyncEvent(final SyncEvent syncEvent) {
        if (getSyncCallback() == null) {
            return;
        }
        switch (getSyncStatus()) {
            case SYNC_PENDING:
            case NOT_IN_SYNC:
                syncEvents.add(syncEvent);
                break;
            case SYNC_FAILED:
            case IN_SYNC:
                //Immediate Callback
                dispatcher.execute(new Runnable() {
                    @Override
                    public void run() {
                        final SyncCallback syncCallback = getSyncCallback();
                        if (syncCallback != null) {
                            syncCallback.onSync(syncEvent);
                        }
                    }
                });
                break;
        }
    }


    protected abstract void handleStateChange();

    protected abstract SyncEvent createSyncEvent();

    protected static final class SyncEventImpl<V extends AbstractVirtualDevice> implements StorageObject.SyncEvent<V> {

        /**
         * Get the virtual device that is the source of the event.
         * @return the virtual device, never {@code null}
         */
        @Override
        public V getVirtualDevice() {
            return virtualDevice;
        }

        /**
         * Get the name of the attribute, action, or format that this event
         * is associated with.
         * @return the name, never {@code null}
         */
        @Override
        public String getName() {
            return name;
        }

        /**
         * Get the {@code StorageObject} that is the source of this event.
         * @return the storageObject, never {@code null}
         */
        @Override
        public StorageObject getSource() {
            return source;
        }

        public SyncEventImpl(StorageObject source, V virtualDevice, String name) {
            this.source = source;
            this.virtualDevice = virtualDevice;
            this.name = name;
        }

        private final StorageObject source;
        private final V virtualDevice;
        private final String name;
    }

    protected static final Executor dispatcher =
            Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    final SecurityManager s = System.getSecurityManager();
                    final ThreadGroup group = (s != null) ? s.getThreadGroup()
                            : Thread.currentThread().getThreadGroup();

                    final Thread t = new Thread(group, r, "sync-callback-thread", 0);

                    // this is opposite of what the Executors.DefaultThreadFactory does
                    if (!t.isDaemon())
                        t.setDaemon(true);
                    if (t.getPriority() != Thread.NORM_PRIORITY)
                        t.setPriority(Thread.NORM_PRIORITY);
                    return t;
                }
            });

}
