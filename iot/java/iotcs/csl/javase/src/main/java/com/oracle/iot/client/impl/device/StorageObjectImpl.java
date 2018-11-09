/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import com.oracle.iot.client.StorageObject;
import com.oracle.iot.client.device.util.StorageDispatcher;
import com.oracle.iot.client.impl.StorageObjectBase;
import oracle.iot.client.device.VirtualDevice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * StorageObjectImpl
 */
public final class StorageObjectImpl extends StorageObjectBase {

    // The name of an attribute or a field
    private String nameForSyncEvent;
    private VirtualDevice deviceForSyncEvent;
    private final StorageDispatcherImpl storageDispatcher;

    public StorageObjectImpl(com.oracle.iot.client.device.DirectlyConnectedDevice directlyConnectedDevice,
                             com.oracle.iot.client.StorageObject delegate) {
        super(delegate);
        storageDispatcher = (StorageDispatcherImpl)StorageDispatcher.getStorageDispatcher(directlyConnectedDevice);
    }

    @Override
    final public void sync() {
        if (getSyncStatus() == SyncStatus.NOT_IN_SYNC) {
            super.sync();
            if (!storageDispatcher.isClosed()) {
                com.oracle.iot.client.StorageObject delegate = getDelegate();
                delegateMap.put(delegate, this);
                storageDispatcher.setProgressCallback(progressCallback);
                storageDispatcher.queue(delegate);
            }
        } else {
            super.sync();
        }
    }

    @Override
    protected void handleStateChange() {
        if (deviceForSyncEvent != null) {
            ((VirtualDeviceImpl) deviceForSyncEvent).handleStorageObjectStateChange(this);
        }
    }

    @Override
    protected SyncEvent<VirtualDevice> createSyncEvent() {
        return new SyncEventImpl<VirtualDevice>(this, deviceForSyncEvent, nameForSyncEvent);
    }

    void setSyncEventInfo(VirtualDevice virtualDevice, String nameForSyncEvent) {
        this.deviceForSyncEvent = virtualDevice;
        this.nameForSyncEvent = nameForSyncEvent;
    }

    private static final StorageDispatcher.ProgressCallback progressCallback = new StorageDispatcher.ProgressCallback() {
        @Override
        public void progress(StorageDispatcher.Progress progress) {

            final StorageObjectDelegate delegate = (StorageObjectDelegate)progress.getStorageObject();
            final StorageObjectImpl storageObject = delegateMap.get(delegate);
            if (storageObject == null) return;

            final SyncStatus oldStatus = storageObject.getSyncStatus();
            final StorageDispatcher.Progress.State state = progress.getState();
            switch (state) {
                case COMPLETED:
                    storageObject.setSyncStatus(SyncStatus.IN_SYNC);
                    delegateMap.remove(delegate);
                    break;
                case CANCELLED:
                case FAILED:
                    storageObject.setSyncStatus(SyncStatus.SYNC_FAILED);
                    delegateMap.remove(delegate);
                    break;
                case IN_PROGRESS:
                case INITIATED:
                case QUEUED:
                default:
                    // do nothing
            }
            if (oldStatus != storageObject.getSyncStatus()) {
                if (storageObject.getInputPath() != null) {
                    storageObject.handleStateChange();
                }
                final SyncCallback syncCallback = storageObject.getSyncCallback();
                if (syncCallback != null) {
                    dispatcher.execute(new Runnable() {
                        @Override
                        public void run() {
                            final List<SyncEvent> syncEvents =
                                    new ArrayList<SyncEvent>(storageObject.getSyncEvents());
                            while (!syncEvents.isEmpty()) {
                                final SyncEvent syncEvent = syncEvents.remove(0);
                                syncCallback.onSync(syncEvent);
                            }
                        }
                    });
                }
            }
        }
    };

    private static Map<StorageObject, StorageObjectImpl> delegateMap =
            Collections.synchronizedMap(new HashMap<StorageObject, StorageObjectImpl>());

}
