/*
 * Copyright (c) 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.http;

import com.oracle.iot.client.SecureConnection;
import com.oracle.iot.client.trust.TrustedAssetsManager;

import java.security.GeneralSecurityException;

/**
 * SecureHttpConnection is an HTTP connection that uses a TrustedAssetsManager
 * for creating HTTP requests. 
 */
public abstract class HttpSecureConnection extends SecureConnection {

    protected HttpSecureConnection(TrustedAssetsManager trustedAssetsManager, boolean useOnlySharedSecret) {
        super(trustedAssetsManager, useOnlySharedSecret);
    }

    public static HttpSecureConnection createHttpSecureConnection(TrustedAssetsManager tam, boolean useOnlySharedSecret)
            throws GeneralSecurityException {
        return new HttpSecureConnectionImpl(tam, useOnlySharedSecret);
    }

    public static HttpSecureConnection createUserAuthSecureConnection(TrustedAssetsManager tam)
            throws GeneralSecurityException {
        // TODO: choose the right authentication scheme
        return new HttpSecureConnectionUserAuthImpl(tam);
    }
}
