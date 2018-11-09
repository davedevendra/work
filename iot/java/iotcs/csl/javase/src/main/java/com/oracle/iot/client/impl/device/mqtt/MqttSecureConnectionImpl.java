/*
 * Copyright (c) 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device.mqtt;

import com.oracle.iot.client.HttpResponse;
import com.oracle.iot.client.message.Message;
import com.oracle.iot.client.message.StatusCode;
import com.oracle.iot.client.trust.TrustedAssetsManager;
import com.oracle.iot.client.trust.TrustException;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.PrivilegedAction;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * SecureMqttConnectionImpl
 */
public class MqttSecureConnectionImpl extends MqttSecureConnection implements MqttCallback {

    // The maximum time in seconds the client will wait for the connection to
    // the MQTT server to be established. The default timeout is 30 seconds.
    // A value of 0 means the client will wait until the network connection is
    // made successfully or fails.
    private final static String MQTT_CONNECTION_TIMEOUT_PROPERTY =
            "oracle.iot.client.device.mqtt_connection_timeout";

    private final static int MQTT_CONNECTION_TIMEOUT;

    // The maximum time in seconds before the client will disconnect if no
    // messages are sent or received. between messages sent or received.
    // The default value is 60 seconds. A value of zero means the client
    // will not disconnect due to inactivity.
    private final static String MQTT_KEEP_ALIVE_INTERVAL_PROPERTY =
            "oracle.iot.client.device.mqtt_keep_alive_interval";

    private final static int MQTT_KEEP_ALIVE_INTERVAL;

    // The QoS to use when publishing messages.
    private final static String SEND_MESSAGE_QOS_PROPERTY =
            "oracle.iot.client.device.send_message_qos";

    private final static int SEND_MESSAGE_QOS_DEFAULT = 1;
    private final static int SEND_MESSAGE_QOS;

    // The time to wait, in milliseconds, for an action to complete. A value
    // of zero means 'no timeout'
    private final static String TIME_TO_WAIT_PROPERTY =
            "oracle.iot.client.device.mqtt_time_to_wait";

    // Default time to wait.
    private final static int TIME_TO_WAIT_DEFAULT = 1000;

    private final static int TIME_TO_WAIT;

    // PAHO disconnect timeouts
    private final static int PAHO_QUIECENSE_TIMEOUT = 0;
    private final static int PAHO_DISCONNECT_TIMEOUT = 10000;

    static {
        
        int val = Integer.getInteger(MQTT_CONNECTION_TIMEOUT_PROPERTY,
                MqttConnectOptions.CONNECTION_TIMEOUT_DEFAULT);

        MQTT_CONNECTION_TIMEOUT = (0 <= val)
                ? val
                : MqttConnectOptions.CONNECTION_TIMEOUT_DEFAULT;

        val = Integer.getInteger(MQTT_KEEP_ALIVE_INTERVAL_PROPERTY,
                MqttConnectOptions.KEEP_ALIVE_INTERVAL_DEFAULT);

        MQTT_KEEP_ALIVE_INTERVAL = (0 <= val)
                ? val
                : MqttConnectOptions.KEEP_ALIVE_INTERVAL_DEFAULT;

        val = Integer.getInteger(SEND_MESSAGE_QOS_PROPERTY,
            SEND_MESSAGE_QOS_DEFAULT);
        SEND_MESSAGE_QOS = (0 <= val && val <= 2)
                ? val
                : SEND_MESSAGE_QOS_DEFAULT;

        val = Integer.getInteger(TIME_TO_WAIT_PROPERTY, TIME_TO_WAIT_DEFAULT);
        TIME_TO_WAIT = (0 <= val) ? val : 0;

    }

    /**
     * Flag indicating whether to enable revocation check for the PKIX trust
     * manager.
     */
    private final static boolean checkTLSRevocation =
            Boolean.getBoolean("com.sun.net.ssl.checkRevocation");

    private final static Charset UTF_8 = Charset.forName("UTF-8");

    private MqttClient mqttClient;

    // MqttSendReceiveImpl gets first shot at handling the callback
    private final AtomicReference<MqttSendReceiveImpl> mqttSendReceiveImpl
            = new AtomicReference<MqttSendReceiveImpl>();

    // The topic we are waiting on
    private final AtomicReference<String> expectedTopic =
            new AtomicReference<String>();

    // The response to a publish
    private HttpResponse publishResponse;

    // For synchronization while waiting for a response
    private final Object LOCK = new int[0];

    // For debugging purposes only. TODO: remove this variable
    private final AtomicBoolean connectionWasLost = new AtomicBoolean(false);

    public MqttSecureConnectionImpl(TrustedAssetsManager tam, boolean isGateway)
            throws GeneralSecurityException {
        super(tam, false);
        try {
            checkConnection();
        } catch (MqttException e) {
            throw new GeneralSecurityException(e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() {

        synchronized (this) {
            if (mqttClient != null) {
                try {
                    if (mqttClient.isConnected()) {
                        final MqttSendReceiveImpl mqttSendReceive = mqttSendReceiveImpl.get();
                        if (mqttSendReceive != null) {
                            try {
                                final String[] topicFilters = mqttSendReceive.getSubscribeTo();
                                mqttClient.unsubscribe(topicFilters);
                            } catch (MqttException ignored) {
                                MqttSecureConnection.getLogger().log(Level.FINEST,ignored.getMessage());
                            }
                        }
                        mqttClient.disconnect();
                    }
                        
                } catch (MqttException e) {
                    MqttSecureConnection.getLogger().log(Level.FINEST,e.getMessage());
                } finally {
                    try {
                        mqttClient.close();
                    } catch (MqttException e) {
                        MqttSecureConnection.getLogger().log(Level.FINEST,e.getMessage());
                    } finally {
                        mqttClient = null;
                    }
                }
            }
        }
    }

    void setMqttSendReceiveImpl(MqttSendReceiveImpl mqttSendReceiveImpl) {
        this.mqttSendReceiveImpl.set(mqttSendReceiveImpl);
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                final String[] topicFilters = mqttSendReceiveImpl.getSubscribeTo();
                final int[] qos = mqttSendReceiveImpl.getSubscribeQos();
                mqttClient.subscribe(topicFilters, qos);
            } catch (MqttException ignored) {
                MqttSecureConnection.getLogger().log(Level.FINEST,ignored.getMessage());
            }
        }

    }

    @Override
    protected synchronized HttpResponse publish(String topic,
                                                byte[] payload,
                                                String expect)
            throws IOException, GeneralSecurityException {

        final boolean waitForResponse = expect != null;
        final int qos = waitForResponse ? QOS_AT_LEAST_ONCE : SEND_MESSAGE_QOS;

        // MqttClient#publish(String,Byte[],int,boolean) throws NPE if
        // payload is null, so make the message here
        final MqttMessage message = new MqttMessage();
        if (payload != null) message.setPayload(payload);
        message.setQos(qos);
        message.setRetained(false);

        try {
            checkConnection();

            if (MqttSecureConnection.getLogger().isLoggable(Level.FINEST)) {
                MqttSecureConnection.getLogger().log(Level.FINEST, "publish: " + topic + ", expect: " + expect);
            }

            if (MqttSecureConnection.getLogger().isLoggable(Level.FINEST)) {
                MqttSecureConnection.getLogger().log(Level.FINEST, "data: " + Message.prettyPrintJson(payload));
            }

            if (waitForResponse) {

                waitForResponse(topic, message, expect);

            } else {

                mqttClient.publish(topic, message);
                publishResponse = MqttSendReceiveImpl.ACCEPTED;
            }

            if (publishResponse != null) {

                if (MqttSecureConnection.getLogger().isLoggable(Level.FINEST)) {
                    MqttSecureConnection.getLogger().log(Level.FINEST, "publishResponse: " + publishResponse.getVerboseStatus("publish", topic));
                }

                int status = publishResponse.getStatus();
                // 401 means the credentials have expired. 403 means we're
                // probably using client-secret where we need
                // client-credentials. In either case, force a disconnect so
                // the client will reconnect with new credentials.
                // Note that the MqttSendReceiveImpl#post method is counting on
                // the MqttClient disconnect in the case of a 401 or 403.
                if (status == 401 || status == 403) {
                    disconnectForcibly();
                }
                return publishResponse;

            } else {
                MqttSecureConnection.getLogger().log(Level.SEVERE,"publishResponse == null! " + topic + ", expect " + expect);
                return new HttpResponse(StatusCode.OTHER.getCode(), "publishResponse == null!".getBytes(UTF_8), null);
            }

        } catch (MqttException e) {
            disconnectForcibly();
            MqttSecureConnection.getLogger().log(Level.SEVERE, e.getMessage());
            throw new IOException(e.getMessage(), e);

        }
    }

    // HACK: Try to connect a second time to the Mqtt broker.
    // This code is written to recover from a situation where the
    // device gets activated but the client fails to get the 
    // activation response from the server. This means that the
    // assets file will not have the activation endpoint id.
    // As a result the library will think that the device is not
    // activated, but it may be. If the first connection attempt fails
    // due to an authentication failure, it may be because the server
    // responded that the device was alread activated. This can happen
    // if the client tries to connect using shared secret credentials
    // which the server rejects if the device is activated. 
    // If the assets file has the public key, it will be assumed that
    // the device is activated. In this case use client assertion
    // credentials to connect to the broker. This strategy only succeeds
    // if the deviceId and endpointId are the same. This may not be the
    // case in the future.
    private void retryConnect(MqttConnectOptions mqttConnectOptions,
            MqttSecurityException mqttException)
            throws MqttException, GeneralSecurityException, TrustException {

        // It would be good to have a more specific reason that is
        // closer to partial activation failure.
        if (mqttException.getReasonCode() !=
                MqttException.REASON_CODE_FAILED_AUTHENTICATION) {
            throw mqttException;
        }
        // Try to get the public key. If it throws exception
        // then they have not been generated, so it is likely
        // that this connection failure is not from the
        // partial activation scenario.
        try {
            trustedAssetsManager.getPublicKey();
        } catch (IllegalStateException ise) {
            throw mqttException;
        }
        // Get assertion credentials but don't use the shared secret.
        char[] password = MqttCredentials.getClientAssertionCredentials(
            trustedAssetsManager, mqttConnectOptions.getUserName(), false);
        mqttConnectOptions.setPassword(password);
        mqttClient.connect(mqttConnectOptions);
        // If we connected assume the initial failure was due to partial
        // activation failure and set the endpoint id with the activationId.
        // As of now both the activationId and enpointId are the same but this
        // may not be the case in the future
        trustedAssetsManager.setEndPointCredentials(
            mqttConnectOptions.getUserName(),
            trustedAssetsManager.getEndpointCertificate());
    }

    // Must only be called from constructor or from publish!
    private void checkConnection()
            throws MqttException, GeneralSecurityException {

        if (mqttClient == null) {

            final MqttClientPersistence mqttClientPersistence
                    = new MemoryPersistence();

            final String host = trustedAssetsManager.getServerHost();
            final int port = trustedAssetsManager.getServerPort();
            // Assuming here that we wouldn't be in this code if serverScheme
            // was null. serverScheme has to be one that indicates MQTT for
            // us to be in this code.
            final String scheme = trustedAssetsManager.getServerScheme().toLowerCase(Locale.ROOT);
            final String protocol;
            if (MQTT_WSS.equals(scheme)) {
                protocol = "wss";
            } else if (MQTT_TCP.equals(scheme)) { // TODO: remove
                protocol = "tcp";
            } else if (MQTT_WS.equals(scheme)) {   // TODO: remove
                protocol = "ws";
            } else {
                protocol = "ssl";
            }

            final String mqttBrokerUrl =
                    String.format(Locale.ROOT, "%1$s://%2$s:%3$d", protocol, host, port);

            final String deviceId = trustedAssetsManager.isActivated()
                    ? trustedAssetsManager.getEndpointId()
                    : trustedAssetsManager.getClientId();

            mqttClient = new MqttClient(
                    mqttBrokerUrl,
                    deviceId,
                    mqttClientPersistence
            );
            mqttClient.setCallback(this);

        }

        if (!mqttClient.isConnected()) {

            final String deviceId = trustedAssetsManager.isActivated()
                    ? trustedAssetsManager.getEndpointId()
                    : trustedAssetsManager.getClientId();

            final MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
            mqttConnectOptions.setUserName(deviceId);
            char[] password =
                    MqttCredentials.getClientAssertionCredentials(trustedAssetsManager);
            mqttConnectOptions.setPassword(password);
            mqttConnectOptions.setCleanSession(true);
            mqttConnectOptions.setConnectionTimeout(MQTT_CONNECTION_TIMEOUT);
            mqttConnectOptions.setKeepAliveInterval(MQTT_KEEP_ALIVE_INTERVAL);

            final String scheme = trustedAssetsManager.getServerScheme().toLowerCase(Locale.ROOT);
            if (MQTT_SSL.equals(scheme) || MQTT_WSS.equals(scheme)) {
                SSLSocketFactory ssf = getSocketFactory(trustedAssetsManager);
                mqttConnectOptions.setSocketFactory(ssf);
            }

            mqttClient.setCallback(this);

            if (connectionWasLost.get() &&
                    MqttSecureConnection.getLogger().isLoggable(Level.INFO)) {

                final String host = trustedAssetsManager.getServerHost();
                final String protocol = trustedAssetsManager.getServerScheme();
                final int port = trustedAssetsManager.getServerPort();

                final String id = trustedAssetsManager.isActivated()
                        ? trustedAssetsManager.getEndpointId()
                        : trustedAssetsManager.getClientId();
                final String msg =
                        String.format(Locale.ROOT, "connect %1$s to %2$s://%3$s:%4$d", id, protocol, host, port);

                MqttSecureConnection.getLogger().log(Level.INFO, msg);
            }

            // HACK: May want to try and return a more specific 
            // error from the broker if failure is due to already activated.
            // Possible reason codes
            // REASON_CODE_FAILED_AUTHENTICATION
            // REASON_CODE_INVALID_CLIENT_ID
            // It looks like MqttSecurityException is thrown.
            // This can be caught with just MqttException.
            try {
                mqttClient.connect(mqttConnectOptions);
            } catch (MqttSecurityException se) {
                if (se.getCause() instanceof GeneralSecurityException) {
                    throw (GeneralSecurityException) se.getCause();
                }
                retryConnect(mqttConnectOptions, se);
            }

            connectionWasLost.set(false);

            final MqttSendReceiveImpl mqttSendReceive = mqttSendReceiveImpl.get();
            if (mqttSendReceive != null) {
                final String[] topicFilters = mqttSendReceive.getSubscribeTo();
                final int[] qos = mqttSendReceive.getSubscribeQos();
                try {
                    mqttClient.subscribe(topicFilters, qos);
                } catch (MqttException ignored) {
                    MqttSecureConnection.getLogger().log(Level.FINEST, ignored.getMessage());
                }
            }
        }
    }

    private void waitForResponse(String topic, MqttMessage message, String expect)
            throws IOException, MqttException {

        final String[] topicFilters = new String[] {
                    expect,
                    expect.concat("/error")
            };

        publishResponse = null;
        assert expectedTopic.get() == null;
        expectedTopic.set(expect);

        try {

            if (!topic.endsWith("messages")) {
                mqttClient.subscribe(topicFilters, new int[]{QOS_AT_LEAST_ONCE, QOS_AT_LEAST_ONCE});
            }

            mqttClient.publish(topic, message);

            synchronized (LOCK) {
                if (publishResponse == null) {
                    LOCK.wait(TIME_TO_WAIT);
                }
            }

        } catch (InterruptedException e) {
            // TODO: handle spurious interrupt. spurious interrupt is possible, but not likely
            // restore the thread's interrupt state
            Thread.currentThread().interrupt();
        } finally {
            expectedTopic.set(null);
            if (mqttClient.isConnected() && !topic.endsWith("messages")) {
                try {
                    mqttClient.unsubscribe(topicFilters);
                } catch (MqttException ignored) {
                    MqttSecureConnection.getLogger().log(Level.FINEST,ignored.getMessage());
                }
            }
        }

        if (publishResponse == null) {

            // If publishResponse is null, then we timed out.
            // Force a disconnect so that the next time publish is called,
            // the MqttClient will reconnect. Since we are reconnecting with
            // clean state, this should clear out any pending messages that
            // haven't been ack'd yet.
            if (mqttClient.isConnected()) {
                disconnectForcibly();
            }
            connectionWasLost.set(true);
            throw new IOException("Timed out waiting for a response from the server");
        }

    }

    @Override
    public void connectionLost(Throwable throwable) {
        MqttSecureConnection.getLogger().log(Level.INFO, throwable.getMessage());
        connectionWasLost.set(true);
    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {

        final String expected = expectedTopic.get();
        final boolean isExpected = expected != null && topic.startsWith(expected);

        if (MqttSecureConnection.getLogger().isLoggable(Level.FINEST)) {
            MqttSecureConnection.getLogger().log(Level.FINEST, "messageArrived for topic: " + topic + ", expected: " + expected);
        }

        if (!isExpected) {
            MqttSecureConnection.getLogger().log(Level.SEVERE,
                    "Message for '" + topic + "' not expected. " +
                            "Expected '" + expectedTopic.get() + "'");
            throw new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION);
        }

        HttpResponse httpResponse = null;
        // We can get the receive the messages topic from SendReceiveImpl
        // initiating a poll (see SendReceiveImpl#receive(long). In
        final MqttSendReceiveImpl sendReceive = mqttSendReceiveImpl.get();
        if (sendReceive != null) {
            httpResponse = sendReceive.handleMessage(topic, mqttMessage);
            // if httpResponse is null, then it wasn't for the messages topic
        }

        if (httpResponse == null) {
            final byte[] payload = mqttMessage.getPayload();
            final boolean isError = topic.endsWith("/error");
            httpResponse = !isError
                    ? new HttpResponse(StatusCode.OK.getCode(), payload, null)
                    : getErrorResponse(payload);
        }

        synchronized (LOCK) {
            publishResponse = httpResponse;
            LOCK.notifyAll();
            return;
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        if (iMqttDeliveryToken.getException() != null) {
            MqttSecureConnection.getLogger().log(Level.INFO, iMqttDeliveryToken.getException().getMessage());
        }
    }

    // Payload is expected to be JSON of the form:
    // {
    //     "type":"https://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.1",
    //     "title":"Bad Request",
    //     "status": 400,"
    //     "detail":"Unable to retrieve resource: state",
    //     "o:errorCode":"client:error:bad:request"
    // }
    static HttpResponse getErrorResponse(byte[] payload) {
        try {
            final String json = new String(payload, UTF_8);
            final JSONObject jsonObject = new JSONObject(json);

            int code = jsonObject.getInt("status");
            return new HttpResponse(code, jsonObject.toString().getBytes(UTF_8), null);
        } catch (JSONException e) {
            MqttSecureConnection.getLogger().log(Level.SEVERE, e.getMessage() + ": " + new String(payload, UTF_8));
            return new HttpResponse(StatusCode.OTHER.getCode(), e.getMessage().getBytes(UTF_8), null);
        }
    }

    // TODO: This was copied from SecureHttpConnection
    private static SSLSocketFactory getSocketFactory(TrustedAssetsManager trustedAssetsManager)
            throws GeneralSecurityException {

        final SSLContext sslContext = SSLContext.getInstance("TLS");

        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        final Vector<byte[]> certs = trustedAssetsManager.getTrustAnchorCertificates();

        if (certs == null) {
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

    private void disconnectForcibly() {
        try {
            // attempt to disconnect, close and remove reference to this client
            try {
                mqttClient.disconnectForcibly(PAHO_QUIECENSE_TIMEOUT,
                    PAHO_DISCONNECT_TIMEOUT);
            } catch (Exception exception) {
                MqttSecureConnection.getLogger().log(Level.FINEST,exception.getMessage());
            } finally {
                mqttClient.close();
            }
        } catch (MqttException ignored) {
            MqttSecureConnection.getLogger().log(Level.FINEST,ignored.getMessage());
        } finally {
            mqttClient = null;
            connectionWasLost.set(true);
        }
    }
}
