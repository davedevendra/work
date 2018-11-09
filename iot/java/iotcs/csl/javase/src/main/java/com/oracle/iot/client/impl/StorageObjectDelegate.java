/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * StorageObjectDelegate extends com.oracle.iot.client.StorageObject to provide extra state and
 * data for working with StorageConnectionImpl.
 */
public class StorageObjectDelegate extends com.oracle.iot.client.StorageObject {

    private final StorageConnection storageConnection;
    protected long transferredBytes;

    protected StorageObjectDelegate(StorageConnection storageConnection,
                                    String uri,
                                    String name,
                                    String contentType,
                                    String contentEncoding,
                                    String dateOfLastModification,
                                    long length) {
        super(uri, name, contentType, contentEncoding, dateOfLastModification, length);
        this.storageConnection = storageConnection;
        this.transferredBytes = 0;
    }

    @Override
    final protected void setMetadata(String date, long length) {
        super.setMetadata(date, length);
    }

    @Override
    final public void sync() throws IOException, GeneralSecurityException {
        storageConnection.sync(this);
    }

    public boolean isCancelled() {
        // TODO: this is overridden on the DC side, but the EC side cannot cancel. IOT-42145
        return false;
    }

    public void setTransferredBytes(long transferredBytes) {
        this.transferredBytes = transferredBytes;
    }

}