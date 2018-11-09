/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.enterprise;

import oracle.iot.client.enterprise.EnterpriseClient;

import com.oracle.iot.client.impl.http.HttpSecureConnection;

import java.util.WeakHashMap;
import java.util.Map;

/**
 * This class enables low level classes to access the SecureConnection created
 * by a EnterpriseClient.
 */
public class SecureHttpConnectionMap {

    private static final
        Map<EnterpriseClient, HttpSecureConnection> secureConnectionMap =
            new WeakHashMap<EnterpriseClient, HttpSecureConnection>();

    /**
     * Get the SecureConnection instance created by a client.
     *
     * @param client creator of the connection
     *
     * @return SecureConnection or null
     */
    public static HttpSecureConnection getSecureHttpConnection(
            EnterpriseClient client) {
        return secureConnectionMap.get(client);
    }

    /**
     * Put a SecureConnection created by an EnterpriseClient in the map. If a
     * SecureConnection is null then remove client's entry from the map.
     *
     * @param client creator of the connection
     * @param secureConnection connection or null to remove the connection
     */
    public static void putSecureConnection(EnterpriseClient client,
            HttpSecureConnection secureConnection) {
        if (secureConnection == null) {
            secureConnectionMap.remove(client);
            return;
        }

        secureConnectionMap.put(client, secureConnection);
    }
}
