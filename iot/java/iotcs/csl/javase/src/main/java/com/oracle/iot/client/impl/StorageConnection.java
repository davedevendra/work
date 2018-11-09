/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl;

import com.oracle.iot.client.StorageObject;

import java.io.Closeable;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * The StorageConnection transfers content to the Storage Cloud Service.
 * StorageConnection is used by the client internally.
 */
public interface StorageConnection extends Closeable {

    /**
     * Synchronize content with the Storage Cloud Service.
     *
     * @param storageObject The StorageObject that identifies the content.
     * @throws IOException if there is an {@code IOException} raised by the runtime,
     * or a failure reported by the storage cloud
     * @throws GeneralSecurityException if there is an exception establishing a secure connection to the storage cloud
     * @throws IllegalArgumentException if the {@code StorageObject} does not return an {@code InputStream} or {@code OutputStream}
     */
    void sync(StorageObject storageObject) throws IOException, GeneralSecurityException;

}
