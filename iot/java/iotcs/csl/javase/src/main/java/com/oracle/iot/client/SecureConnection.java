/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client;

import com.oracle.iot.client.trust.TrustedAssetsManager;

import java.io.Closeable;
import java.io.IOException;
import java.lang.IllegalStateException;
import java.security.GeneralSecurityException;

/**
 * SecureConnection
 */
public abstract class SecureConnection implements Closeable {

    protected final TrustedAssetsManager trustedAssetsManager;
    protected final boolean useOnlySharedSecret;
    private boolean closed = false;

    protected SecureConnection(TrustedAssetsManager trustedAssetsManager, boolean useOnlySharedSecret) {
        this.trustedAssetsManager = trustedAssetsManager;
        this.useOnlySharedSecret = useOnlySharedSecret;
    }

    public final String getEndpointId() {
        try {
            return trustedAssetsManager.getEndpointId();
        } catch (IllegalStateException e) {
            if (trustedAssetsManager.getClientId() == null) {
                return null;
            }
            throw e;
        }
    }

    public abstract HttpResponse get(String restApi)
            throws IOException, GeneralSecurityException;

    public abstract HttpResponse post(String restApi, byte[] payload)
            throws IOException, GeneralSecurityException;

    /*
     * Timeout (in milliseconds) is need only for the HttpClient which passes
     * the timeout to HttpsUrlConnection.setReadTimeout, in which 
     * timeout of zero is interpreted as an infinite timeout.
     */
    public abstract HttpResponse post(String restApi, byte[] payload,
            int timeout) throws IOException, GeneralSecurityException;

    public abstract HttpResponse put(String restApi, byte[] payload)
            throws IOException, GeneralSecurityException;

    public abstract HttpResponse delete(String restApi)
            throws IOException, GeneralSecurityException;

    public abstract HttpResponse patch(String restApi, byte[] payload)
            throws IOException, GeneralSecurityException;

    @Override
    public void close() throws IOException {
        closed = true;
        disconnect();
        trustedAssetsManager.close();
    }

    public boolean isClosed() {
        return closed;
    }

    public void disconnect() {}
    
    /**
     * Compare {@link SecureConnection} 
     * @param obj {@link SecureConnection} to compare with {@code this}
     * @return {@code true} if equal.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        
        if (obj == null || this.getClass() != obj.getClass()) return false;
        
        SecureConnection other = (SecureConnection)obj;

        if (!trustedAssetsManager.getServerScheme().equals(
                other.trustedAssetsManager.getServerScheme())) {
            return false;
        }

        if (!trustedAssetsManager.getServerHost().equals(
                other.trustedAssetsManager.getServerHost())) {
            return false;
        }

        return (trustedAssetsManager.getServerPort() ==
                other.trustedAssetsManager.getServerPort());

    }
    
    @Override
    public int hashCode() {
        int hash = 37;
        hash = 37 * hash + this.trustedAssetsManager.getServerScheme().hashCode();
        hash = 37 * hash + this.trustedAssetsManager.getServerHost().hashCode();
        hash = 37 * hash + this.trustedAssetsManager.getServerPort();
        return hash;
    }

    protected final TrustedAssetsManager getTrustedAssetsManager() {
        return trustedAssetsManager;
    }

    protected final boolean usesOnlySharedSecret() {
        return useOnlySharedSecret;
    }
}
