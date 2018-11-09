/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.impl.enterprise;

import com.oracle.iot.client.SecureConnection;
import com.oracle.iot.client.StorageObject;
import com.oracle.iot.client.impl.StorageConnectionBase;

/**
 * The StorageConnection transfers content to the Storage Cloud Service.
 * Service.
 * There is one StorageConnection instance per client.
 */
public class StorageConnectionImpl extends StorageConnectionBase {

    public StorageConnectionImpl(SecureConnection secureConnection) {
        super(secureConnection);
    }

    @Override
    protected StorageObject createStorageObject(String storageContainerUrl,
                                                String name,
                                                String type,
                                                String encoding,
                                                String date,
                                                int length) {
        return new com.oracle.iot.client.impl.enterprise.StorageObjectDelegate(
                this,
                storageContainerUrl,
                name,
                type,
                encoding,
                date,
                length);
    }
}
