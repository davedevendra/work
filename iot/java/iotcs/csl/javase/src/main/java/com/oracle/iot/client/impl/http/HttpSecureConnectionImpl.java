/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.http;

import com.oracle.iot.client.TransportException;
import com.oracle.iot.client.HttpResponse;
import com.oracle.iot.client.RestApi;
import com.oracle.iot.client.impl.AccessToken;
import com.oracle.iot.client.impl.TimeManager;
import com.oracle.iot.client.trust.TrustedAssetsManager;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SecureConnection
 */
public class HttpSecureConnectionImpl extends HttpSecureConnection {
    private static final int USE_DEFAULT_TIMEOUT_VALUE = -1;

	/**
	* Flag indicating whether to enable revocation check for the PKIX trust
	* manager.
	*/
	private final static boolean checkTLSRevocation = 
                Boolean.getBoolean("com.sun.net.ssl.checkRevocation");

    private final static Map<String, SSLSocketFactory> socketFactoryMap =
            Collections.synchronizedMap(new HashMap<String, SSLSocketFactory>());

    private final SSLSocketFactory sslSocketFactory;

    protected final SSLSocketFactory getSSLSocketFactory() {
        return sslSocketFactory;
    }

    private final String hostUrl;

    public HttpSecureConnectionImpl(TrustedAssetsManager tam, boolean sharedSecretCredential) throws GeneralSecurityException {
        super(tam, sharedSecretCredential);
        // TODO: this is a hack to get mock server unit tests to work.
        // TODO: IOT-29666 needs to be done to fix this properly.
        hostUrl = tam.getServerScheme() + "://" + tam.getServerHost(); // TODO: add port, but it shouldn't matter
        SSLSocketFactory socketFactory = socketFactoryMap.get(hostUrl);
        if (socketFactory == null) {
            socketFactory = getDefaultSSLSocketFactory(tam);
            socketFactoryMap.put(hostUrl, socketFactory);
        }
        sslSocketFactory = socketFactory;
    }

    @Override
    public HttpResponse get(String restApi)
            throws IOException, GeneralSecurityException {

        return invoke("GET", restApi, null);
    }

    @Override
    public HttpResponse post(String restApi, byte[] payload)
            throws IOException, GeneralSecurityException {

        return invoke("POST", restApi, payload);
    }

    @Override
    public HttpResponse post(String restApi, byte[] payload, int timeout)
            throws IOException, GeneralSecurityException {

        return invoke("POST", restApi, payload, timeout);
    }

    @Override
    public HttpResponse put(String restApi, byte[] payload)
            throws IOException, GeneralSecurityException {

        return invoke("PUT", restApi, payload);
    }

    @Override
    public HttpResponse delete(String restApi)
            throws IOException, GeneralSecurityException {

        return invoke("DELETE", restApi, null);
    }

    @Override
    public HttpResponse patch(String restApi, byte[] payload)
            throws IOException, GeneralSecurityException {

        return invoke("PATCH", restApi, payload);
    }

    private static final String ACTIVATION_API = RestApi.V2.getReqRoot()+"/activation";
    private static boolean isActivationApi(String restApi) {
        return ACTIVATION_API.regionMatches(0, restApi, 0, ACTIVATION_API.length());
    }

    private HttpResponse invoke(String method, String restApi, byte[] payload)
            throws IOException, GeneralSecurityException {
        return invoke(method, restApi, payload, USE_DEFAULT_TIMEOUT_VALUE);
    }

    private HttpResponse invoke(String method, String restApi, byte[] payload,
            int timeout) throws IOException, GeneralSecurityException {

        if (isClosed()) {
            throw new IOException("Connection is closed");
        }

        HttpResponse response = null;
        final URL url;
        try {
            final String serverHost = getTrustedAssetsManager().getServerHost();
            final int serverPort = getTrustedAssetsManager().getServerPort();
            final HashMap<String, String> headers = new HashMap<String, String>(4);
            final boolean useWebAPI = RestApi.V2.isWebApi();
            url = new URL("https", serverHost, serverPort, restApi);

            if (!useWebAPI){
                // Note: accessToken is volatile and immutable.
                // currentAccessToken is only un-synchronized read of
                // accessToken in this block.
                AccessToken currentAccessToken = this.accessToken;
                if(currentAccessToken == null || currentAccessToken.hasExpired()) {
                    synchronized (LOCK) {
                        // synchronized read of this.accessToken
                        currentAccessToken = this.accessToken;
                        if (currentAccessToken == null || currentAccessToken.hasExpired()) {
                            currentAccessToken = renewAccessToken(this);
                            this.accessToken = currentAccessToken;
                        }
                    }
                }
                headers.put("Authorization", currentAccessToken.getTokenType() + " "
                        + currentAccessToken.getToken());
            }

            headers.put("Content-Type", "application/json");
            headers.put("Accept", "application/json");

            if (isActivationApi(restApi)) {
                if (getTrustedAssetsManager().isActivated()) {
                    headers.put("X-EndpointId",
                                getTrustedAssetsManager().getEndpointId());
                } else {
                    headers.put("X-ActivationId",
                                getTrustedAssetsManager().getClientId());
                }
            } else if (!RestApi.V2.isWebApi()) {
                if (getTrustedAssetsManager().isActivated()) {
                    final String eid =
                        getTrustedAssetsManager().getEndpointId();
                    headers.put("X-EndpointId", eid);
                } else {
                    headers.put("X-ActivationId",
                        getTrustedAssetsManager().getClientId());
                }
            }

            final HttpClient httpClient =
                new HttpClientImpl(sslSocketFactory, url);
            response = invoke(httpClient, method, payload, headers, timeout);

            if (getLogger().isLoggable(Level.FINEST)) {
                getLogger().log(Level.FINEST,
                    response.getVerboseStatus(method, url.toExternalForm()));
            }

            // 401 means the credentials have expired
            // 403 means we're probably using client-secret where we need
            // client-credentials
            if (response.getStatus() == 401 || response.getStatus() == 403) {
                AccessToken newAccessToken = null;
                synchronized (LOCK) {
                    newAccessToken = renewAccessToken(this);
                    this.accessToken = newAccessToken;
                }
                headers.put("Authorization", newAccessToken.getTokenType() +
                    " " + newAccessToken.getToken());
                response =
                    invoke(httpClient, method, payload, headers, timeout);
            }

        } catch(java.net.UnknownHostException uhe) {
            getLogger().log(Level.SEVERE,
                    "Unknown host: " + getTrustedAssetsManager().getServerHost());
            throw uhe;
        } catch(java.net.ConnectException ce) {
            getLogger().log(Level.SEVERE,
                    "Cannot connect: " + getTrustedAssetsManager().getServerHost() +
                            ":" + getTrustedAssetsManager().getServerPort());
            throw ce;
        } catch (java.net.SocketTimeoutException ce) {
            if (timeout < 0) {
                // NOT long polling, log error
                getLogger().log(Level.SEVERE, "Connection timed out: " +
                        getTrustedAssetsManager().getServerScheme() +
                        "://" + getTrustedAssetsManager().getServerHost() +
                        ":" + getTrustedAssetsManager().getServerPort());
            }

            throw ce;
        } catch(IOException ie) {
            // Some class of IOException not handled above
            getLogger().log(Level.SEVERE, ie.getMessage(), ie);
            throw ie;
        }

        if (response.getStatus() == 400) {
            String errmsg = response.getVerboseStatus(method, url.toExternalForm());
            getLogger().log(Level.SEVERE, errmsg);
        }

        return response;
    }

    protected HttpResponse invoke(HttpClient httpClient, String method,
            byte[] payload, Map<String, String> headers, int timeout)
            throws IOException , GeneralSecurityException {

        if (method.equals("GET")) {
            return httpClient.get(headers);
        } else if (method.equals("POST")) {
            return httpClient.post(payload, headers, timeout);
        } else if (method.equals("DELETE")) {
            return httpClient.delete(headers);
        } else if (method.equals("PATCH")) {
            headers.put("X-HTTP-Method-Override", "PATCH");
            return httpClient.post(payload, headers, timeout);
        } else { // Must be "PUT"
            return httpClient.put(payload, headers);
        }
    }

    private static SSLSocketFactory getDefaultSSLSocketFactory(TrustedAssetsManager trustedAssetsManager)
            throws GeneralSecurityException {

        final SSLContext sslContext = SSLContext.getInstance("TLS");

        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        final Vector<byte[]> certs = trustedAssetsManager.getTrustAnchorCertificates();

        if (certs == null || certs.isEmpty()) {
            // Use default platform TrustManager if no Trust Anchors are provided
            sslContext.init(null,null,null);
            return sslContext.getSocketFactory();
        }

        final Set<TrustAnchor> trustAnchors = new HashSet<TrustAnchor>();
        for (int i = 0; i < certs.size(); i++) {
            TrustAnchor trustAnchor = new TrustAnchor((X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certs.elementAt(i))), null);
            trustAnchors.add(trustAnchor);
        }

        sslContext.init(null, new TrustManager[] { new X509TrustManager() {

            @Override
            public void checkClientTrusted(X509Certificate[] certificates, String authType) throws CertificateException {
                throw new CertificateException();
            }

            @Override
            public void checkServerTrusted(X509Certificate[] certificates, String authType) throws CertificateException {
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                CertPath chain = factory.generateCertPath(Arrays.asList(certificates));
                PKIXParameters params;
                try {
                    params = new PKIXParameters(trustAnchors);
                    params.setRevocationEnabled(checkTLSRevocation);
                    CertPathValidator validator = CertPathValidator.getInstance("PKIX");
                    validator.validate(chain, params);
                } catch (Exception e) { // InvalidAlgorithmParameterException
                    // | NoSuchAlgorithmException |
                    // CertPathValidatorException
                    throw new CertificateException(e);
                }
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

        } }, null);

        return sslContext.getSocketFactory();
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Routines for creating AccessToken via /iot/api/v2/oauth2/token
    //
    ///////////////////////////////////////////////////////////////////////////

    private volatile AccessToken accessToken;
    private final Object LOCK = new int[0];

    private static HttpResponse postRenewAccessToken(
            HttpSecureConnectionImpl secureHttpConnection) 
        throws IOException, GeneralSecurityException {
        TrustedAssetsManager trustedAssetsManager =
            secureHttpConnection.getTrustedAssetsManager();
        String serverHost = trustedAssetsManager.getServerHost();
        int serverPort = trustedAssetsManager.getServerPort();

        URL url = new URL("https", serverHost, serverPort,
            RestApi.V2.getReqRoot() + "/oauth2/token");

        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("Accept", "application/json");

        if (getLogger().isLoggable(Level.FINEST)) {
            getLogger().log(Level.FINEST, "POST " +
                RestApi.V2.getReqRoot() + "/oauth2/token");
        }

        byte[] credentialsPostData =
            HttpCredentials.getClientAssertionCredentials(
                trustedAssetsManager,
                secureHttpConnection.usesOnlySharedSecret()
            );

        HttpClient httpClient =
            new HttpClientImpl(secureHttpConnection.sslSocketFactory, url);
        return httpClient.post(credentialsPostData, headers);
    }

    private static long parseResponseTime(byte[] responseData) throws IllegalArgumentException {
        String json = null;
        try {
            json = new String(responseData, "UTF-8");
            if (json.indexOf("currentTime") < 0) {
                // possible with unit tests
                return System.currentTimeMillis();
            }                
            String currentTime = new JSONObject(json).get("currentTime").toString();
            return Long.parseLong(currentTime);
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE,
                     t.toString());
            throw new IllegalArgumentException("Failed to parse server time from the response " + json);
        } finally {
        }
    }

    private static AccessToken renewAccessToken(
            HttpSecureConnectionImpl secureHttpConnection)
            throws IOException, GeneralSecurityException {
        HttpResponse response = postRenewAccessToken(secureHttpConnection);
        int status = response.getStatus();
        if (status == 400) {
            // attempt to synchronize time and post once again
            try {
                TimeManager.setCurrentTimeMillis(parseResponseTime(response.getData()));
                response = postRenewAccessToken(secureHttpConnection);
                status = response.getStatus();
            } catch (IllegalArgumentException ex) {
                getLogger().log(Level.SEVERE,
                     ex.toString());
            }
        }

        // MQTT returns GeneralSecurityException if the username/password is
        // not right. This aligns HTTP with MQTT. 
        if (status == 400) {
            throw new GeneralSecurityException(response.getVerboseStatus("POST",
                    RestApi.V2.getReqRoot()+"/oauth2/token"));
        }

        if (status != 200) {
            throw new TransportException(status, response.getVerboseStatus("POST",
                RestApi.V2.getReqRoot()+"/oauth2/token"));
        }

        byte[] data = response.getData();
        if (data == null || data.length == 0) {
            throw new IOException("POST " + RestApi.V2.getReqRoot() +
                "/oauth2/token: empty payload");
        }

        String json = new String(data, "UTF-8");
        try {
            JSONObject jsonObject = new JSONObject(json);
            return AccessToken.fromJson(jsonObject);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void disconnect() {
        synchronized (LOCK) {
            this.accessToken = null;
        }
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
