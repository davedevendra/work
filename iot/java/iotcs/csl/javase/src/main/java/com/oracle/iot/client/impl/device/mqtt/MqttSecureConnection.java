/*
 * Copyright (c) 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device.mqtt;

import com.oracle.iot.client.RestApi;
import com.oracle.iot.client.SecureConnection;
import com.oracle.iot.client.HttpResponse;
import com.oracle.iot.client.trust.TrustedAssetsManager;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * SecureMqttConnection
 */
public abstract class MqttSecureConnection extends SecureConnection {


    // "At most once", where messages are delivered according to the best
    // efforts of the operating environment. Message loss can occur. This
    // level could be used, for example, with ambient sensor data where it
    // does not matter if an individual reading is lost as the next one will
    // be published soon after
    // - http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html
    protected final static int QOS_AT_MOST_ONCE = 0;

    // "At least once", where messages are assured to arrive but duplicates
    // can occur.
    // - http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html
    protected final static int QOS_AT_LEAST_ONCE = 1;

    // "Exactly once", where message are assured to arrive exactly once.
    // This level could be used, for example, with billing systems where
    // duplicate or lost messages could lead to incorrect charges being applied.
    // - http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html
    protected final static int QOS_EXACTLY_ONCE = 2;

    /**
     * Server scheme for mqtt over ssl.
     * @see com.oracle.iot.client.trust.TrustedAssetsManager#getServerScheme()
     */
    protected static final String MQTT_SSL = "mqtts";

    /**
     * Server scheme for mqtt over tcp.
     * @see com.oracle.iot.client.trust.TrustedAssetsManager#getServerScheme()
     */
    protected static final String MQTT_TCP = "mqtt";

    /**
     * Server scheme for mqtt over websockets.
     * @see com.oracle.iot.client.trust.TrustedAssetsManager#getServerScheme()
     */
    protected static final String MQTT_WS  = "mqtt-ws";

    /**
     * Server scheme for mqtt over websockets secure.
     * @see com.oracle.iot.client.trust.TrustedAssetsManager#getServerScheme()
     */
    protected static final String MQTT_WSS = "mqtt-wss";

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final static String ACTIVATION_POLICY_TOPIC =
            "%1$s/%2$s/activation/policy";

    private static final String ACTIVATION_POLICY_PAYLOAD =
            "{\"OSName\":\"%1$s\",\"OSVersion\":\"%2$s\"}";

    private final static String ACTIVATION_DIRECT_TOPIC =
            "%1$s/%2$s/activation/direct";

    private final static String ACTIVATION_INDIRECT_TOPIC =
            "%1$s/%2$s/activation/indirect/device";

    private static final String DEVICE_MODEL_TOPIC =
            "%1$s/%2$s/deviceModels";

    private static final String DEVICE_MODEL_PAYLOAD =
            "{\"urn\":\"%1$s\"}";

    private static final String MESSAGES_TOPIC =
            "%1$s/%2$s/messages";

    public static boolean isMqttPort(TrustedAssetsManager tam) {

        if (tam.getServerScheme() == null) {
            return false;
        }

        final String scheme = tam.getServerScheme().toLowerCase(Locale.ROOT);
        return (MQTT_SSL.equals(scheme) ||
                MQTT_WSS.equals(scheme) ||
                MQTT_TCP.equals(scheme) || // TODO: remove
                MQTT_WS.equals(scheme));   // TODO: remove
    }

    protected MqttSecureConnection(TrustedAssetsManager trustedAssetsManager, boolean useOnlySharedSecret) {
        super(trustedAssetsManager, useOnlySharedSecret);
    }

    public static MqttSecureConnection createSecureMqttConnection(TrustedAssetsManager tam, boolean isGateway)
            throws GeneralSecurityException {
        return new MqttSecureConnectionImpl(tam, isGateway);
    }

    @Override
    public final HttpResponse get(String restApi)
            throws IOException, GeneralSecurityException {
        
        if (restApi.startsWith(RestApi.V2.getReqRoot()+"/activation/policy")) {

            final String topic =
                    String.format(ACTIVATION_POLICY_TOPIC,
                            "iotcs",
                            trustedAssetsManager.getClientId());

            final String expect =
                    String.format(ACTIVATION_POLICY_TOPIC,
                            "devices",
                            trustedAssetsManager.getClientId());

            final byte[] payload =
                    String.format(ACTIVATION_POLICY_PAYLOAD,
                            System.getProperty("os.name"),
                            System.getProperty("os.version"))
                    .getBytes(UTF_8);

            return publish(topic, payload, expect);

        } else if (restApi.startsWith(RestApi.V2.getReqRoot() + "/deviceModels")) {

            final String topic =
                    String.format(DEVICE_MODEL_TOPIC,
                            "iotcs",
                            (trustedAssetsManager.isActivated()
                                    ? trustedAssetsManager.getEndpointId()
                                    : trustedAssetsManager.getClientId()));

            final String expect =
                    String.format(DEVICE_MODEL_TOPIC,
                            "devices",
                            (trustedAssetsManager.isActivated()
                                    ? trustedAssetsManager.getEndpointId()
                                    : trustedAssetsManager.getClientId()));

            final int beginIndex = (RestApi.V2.getReqRoot() + "/deviceModels/").length();
            final int endIndex = restApi.indexOf('?');

            final String modelName = endIndex != -1
                    ? restApi.substring(beginIndex, endIndex)
                    : restApi.substring(beginIndex);

            final byte[] payload =
                    String.format(DEVICE_MODEL_PAYLOAD, modelName).getBytes(UTF_8);

            return publish(topic, payload, expect);

        } else {
            throw new UnsupportedOperationException("GET " + restApi);
        }
    }

    @Override
    // timeout is only needed the HTTP long polling.
    public final HttpResponse post(String restApi, byte[] payload, int timeout)
            throws IOException, GeneralSecurityException {
        return post(restApi, payload);
    }

    @Override
    public final HttpResponse post(String restApi, byte[] payload)
            throws IOException, GeneralSecurityException {

        if (restApi.startsWith(RestApi.V2.getReqRoot() + "/messages")) {

            final String topic =
                    String.format(MESSAGES_TOPIC,
                            "iotcs",
                            trustedAssetsManager.getEndpointId());

            final String expect =
                    String.format(MESSAGES_TOPIC,
                            "devices",
                            trustedAssetsManager.getClientId());

            return publish(topic, payload, expect);

        } else if (restApi.startsWith(RestApi.V2.getReqRoot() + "/activation/direct")) {

            final String topic =
                    String.format(ACTIVATION_DIRECT_TOPIC,
                            "iotcs",
                            trustedAssetsManager.getClientId());

            final String expect =
                    String.format(ACTIVATION_DIRECT_TOPIC,
                            "devices",
                            trustedAssetsManager.getClientId());

            return publish(topic, payload, expect);

        } else if (restApi.startsWith(RestApi.V2.getReqRoot() + "/activation/indirect/device")) {

            final String topic =
                    String.format(ACTIVATION_INDIRECT_TOPIC,
                            "iotcs",
                            trustedAssetsManager.getEndpointId());

            final String expect =
                    String.format(ACTIVATION_INDIRECT_TOPIC,
                            "devices",
                            trustedAssetsManager.getEndpointId());

            return publish(topic, payload, expect);

        } else {
            throw new UnsupportedOperationException("POST " + restApi);
        }

    }

    @Override
    public final HttpResponse put(String restApi, byte[] payload)
            throws IOException, GeneralSecurityException {
        // device client does not use PUT
        throw new UnsupportedOperationException("PUT " + restApi);
    }

    @Override
    public final HttpResponse delete(String restApi)
            throws IOException, GeneralSecurityException {
        // device client does not use DELETE
        throw new UnsupportedOperationException("DELETE " + restApi);
    }

    @Override
    public final HttpResponse patch(String restApi, byte[] payload)
            throws IOException, GeneralSecurityException {
        // device client does not use PATCH
        throw new UnsupportedOperationException("PATCH " + restApi);
    }

    protected abstract HttpResponse publish(String topic,
                                            byte[] payload,
                                            String expect)
            throws IOException, GeneralSecurityException;



// TODO (??): private final static Logger LOGGER = Logger.getLogger(MqttSecureConnection.class.getSimpleName());

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    /*private*/ static Logger getLogger() { return LOGGER; }   
}
