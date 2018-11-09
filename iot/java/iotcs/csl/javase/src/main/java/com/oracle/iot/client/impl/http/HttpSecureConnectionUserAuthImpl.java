/*
 * Copyright (c) 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.http;

import com.oracle.iot.client.HttpResponse;
import com.oracle.iot.client.trust.TrustedAssetsManager;

import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SecureConnection
 */
public final class HttpSecureConnectionUserAuthImpl extends HttpSecureConnectionImpl {

    public HttpSecureConnectionUserAuthImpl(TrustedAssetsManager tam) throws GeneralSecurityException {
        super(tam, false);
    }

    protected HttpResponse invoke(String method,
                                String restApi,
                                byte[] payload)
            throws IOException, GeneralSecurityException {
        return invoke(method, restApi, payload, -1);
    }

    protected HttpResponse invoke(String method,
                                String restApi,
                                byte[] payload,
                                int timeout)
            throws IOException, GeneralSecurityException {

        final String serverHost = getTrustedAssetsManager().getServerHost();
        final int serverPort = getTrustedAssetsManager().getServerPort();

        final URL url = new URL("https", serverHost, serverPort, restApi);

        final HashMap<String, String> headers = new HashMap<String, String>(3);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");

        final HttpClient httpClient = new HttpClientImpl(getSSLSocketFactory(), url);
        HttpResponse response = invoke(httpClient, method, payload, headers,
            timeout);

        if (getLogger().isLoggable(Level.FINEST)) {
            getLogger().log(Level.FINEST, response.getVerboseStatus(method, url.toExternalForm()));
        }

        return response;
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
