/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.enterprise;

import com.oracle.iot.client.StorageObject;
import com.oracle.iot.client.impl.StorageObjectBase;
import oracle.iot.client.enterprise.VirtualDevice;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * StorageObjectImpl
 */
public final class StorageObjectImpl extends StorageObjectBase {

    // The name of an attribute or a field
    private String nameForSyncEvent;
    private VirtualDevice deviceForSyncEvent;

    public StorageObjectImpl(StorageObject delegate) {
        super(delegate);
    }

    final public void sync() {
        if (getSyncStatus() == SyncStatus.NOT_IN_SYNC) {
            super.sync();
            dispatcher.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        // blocking!
                        getDelegate().sync();
                        addSyncEvent(createSyncEvent());
                        setSyncStatus(SyncStatus.IN_SYNC);
                    } catch (IOException e) {
                        addSyncEvent(createSyncEvent());
                        setSyncStatus(SyncStatus.SYNC_FAILED);
                    } catch (GeneralSecurityException e) {
                        addSyncEvent(createSyncEvent());
                        setSyncStatus(SyncStatus.SYNC_FAILED);
                    }
                }
            });
        } else {
            super.sync();
        }
    }

    void setSyncEventInfo(VirtualDevice virtualDevice, String nameForSyncEvent) {
        this.deviceForSyncEvent = virtualDevice;
        this.nameForSyncEvent = nameForSyncEvent;
    }

    @Override
    protected void handleStateChange() {}

    @Override
    protected SyncEvent<VirtualDevice> createSyncEvent() {
        return new SyncEventImpl<VirtualDevice>(this, deviceForSyncEvent, nameForSyncEvent);
    }

}
