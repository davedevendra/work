/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import com.oracle.iot.client.impl.StorageConnection;
import com.oracle.iot.client.device.util.StorageDispatcher;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * StorageObjectDelegate extends com.oracle.iot.client.impl.StorageObjectDelegate to provide extra state and
 * data for working with StorageConnectionImpl. This is the device library version of this class. It is
 * instantiated by com.oracle.iot.client.impl.device.StorageConnectionImpl.
 */
final class StorageObjectDelegate extends com.oracle.iot.client.impl.StorageObjectDelegate {

    StorageObjectDelegate(StorageConnection storageConnection,
                          String uri,
                          String name,
                          String contentType,
                          String contentEncoding,
                          String dateOfLastModification, long length) {
        super(storageConnection, uri, name, contentType, contentEncoding, dateOfLastModification, length);
        this.state = null;
    }

    void setState(StorageDispatcher.Progress.State state) {
        this.state = state;
    }

    StorageDispatcher.Progress.State getState() {
        return state;
    }

    /**
     * Number of bytes transferred between progress updates
     */
    private static final long PROGRESS_UPDATE_INTERVAL = 1024*1024;

    @Override
    public void setTransferredBytes(long transferredBytes) {
        final long diff = transferredBytes - this.transferredBytes;
        super.setTransferredBytes(transferredBytes);
        if (diff >= PROGRESS_UPDATE_INTERVAL) {
            // TODO: IOT-42148
        }
    }

    @Override
    public void setOutputStream(OutputStream outputStream) {
        if (outputStream != null &&
                state != null &&
                state != StorageDispatcher.Progress.State.COMPLETED &&
                state != StorageDispatcher.Progress.State.CANCELLED &&
                state != StorageDispatcher.Progress.State.FAILED) {
            throw new IllegalStateException("sync in progress");
        }
        super.setOutputStream(outputStream);
        setState(null);
    }

    @Override
    public void setInputStream(InputStream inputStream) {
        if (inputStream != null &&
                state != null &&
                state != StorageDispatcher.Progress.State.COMPLETED &&
                state != StorageDispatcher.Progress.State.CANCELLED &&
                state != StorageDispatcher.Progress.State.FAILED) {
            throw new IllegalStateException("sync in progress");
        }
        super.setInputStream(inputStream);
        setState(null);
    }

    @Override
    public boolean isCancelled() {
        return state == StorageDispatcher.Progress.State.CANCELLED;
    }

    private volatile StorageDispatcher.Progress.State state;
}