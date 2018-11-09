/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.enterprise;

import com.oracle.iot.client.impl.StorageConnection;

/**
 * StorageObjectDelegate extends com.oracle.iot.client.impl.StorageObjectDelegate. This
 * is the enterprise library version of this class. It is instantiated by
 * com.oracle.iot.client.impl.enterprise.StorageConnectionImpl.
 */
final class StorageObjectDelegate extends com.oracle.iot.client.impl.StorageObjectDelegate {

    StorageObjectDelegate(StorageConnection storageConnection,
                          String uri,
                          String name,
                          String contentType,
                          String contentEncoding,
                          String dateOfLastModification, long length) {
        super(storageConnection, uri, name, contentType, contentEncoding, dateOfLastModification, length);
    }
}