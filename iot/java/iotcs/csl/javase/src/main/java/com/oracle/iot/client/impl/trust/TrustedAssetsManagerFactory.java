/*
 * Copyright (c) 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.impl.trust;

import java.security.GeneralSecurityException;

import com.oracle.iot.client.trust.TrustedAssetsManager;

public class TrustedAssetsManagerFactory {
    static final String DEFAULT_TA_STORE = "trustedAssetsStore.jks";

    public static TrustedAssetsManager create(Object context)
            throws GeneralSecurityException {
        String path = System.getProperty(
            TrustedAssetsManagerBase.TA_STORE_PROPERTY, DEFAULT_TA_STORE);

        String password = System.getProperty(
            TrustedAssetsManagerBase.TA_STORE_PASSWORD_PROPERTY);

        return create(path, password, null);
    }
	    
    public static TrustedAssetsManager create(String path, String password,
            Object context) throws GeneralSecurityException {
        if (path.endsWith("jceks") || path.endsWith(".jks")) {
            return new DefaultTrustedAssetsManager(path, password, null);
        }

        return new UnifiedTrustedAssetsManager(path, password, null);
    }
}
