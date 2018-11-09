/*
 * Copyright (c) 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device.mqtt;

import com.oracle.iot.client.HttpResponse;
import com.oracle.iot.client.impl.device.SendReceiveImpl;
import com.oracle.iot.client.device.DirectlyConnectedDevice;
import com.oracle.iot.client.message.Message;
import com.oracle.iot.client.message.StatusCode;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MqttSendReceiveImpl is an implementation of the send(Message...) method of
 * {@link DirectlyConnectedDevice}. The send is
 * synchronous. Receive should be considered as a synchronous call. The
 * implementation has a buffer for receiving request messages. If there are
 * no messages in the buffer, the receive implementation will send a message
 * to the server to receive any pending requests the server may have.
 * <p>
 * The size of the buffer can be configured by setting the property {@code TBD}.
 */
public final class MqttSendReceiveImpl extends SendReceiveImpl {

    static final HttpResponse ACCEPTED =
            new HttpResponse(StatusCode.ACCEPTED.getCode(), new byte[0], null);

    private final MqttSecureConnectionImpl secureConnection;
    private final String publishMessagesTopic;
    private final String publishMessagesAcceptBytesTopic;
    private final String subscribeMessagesTopic;
    private final String subscribeMessagesErrorTopic;
    private final String subscribeMessagesAcceptBytesTopic;

    public MqttSendReceiveImpl(MqttSecureConnection secureConnection) {
        super();
        this.secureConnection = (MqttSecureConnectionImpl) secureConnection;
        this.publishMessagesTopic = "iotcs/" + this.secureConnection.getEndpointId() + "/messages";
        this.publishMessagesAcceptBytesTopic = "iotcs/" + this.secureConnection.getEndpointId() + "/messages/acceptBytes";
        this.subscribeMessagesTopic = "devices/" + this.secureConnection.getEndpointId() + "/messages";
        this.subscribeMessagesErrorTopic = "devices/" + this.secureConnection.getEndpointId() + "/messages/error";
        this.subscribeMessagesAcceptBytesTopic = "devices/" + this.secureConnection.getEndpointId() + "/messages/acceptBytes";
    }

    public void initialize() {
        // cannot be done from ctor since handleMessage could be called
        // before the object is fully instantiated
        this.secureConnection.setMqttSendReceiveImpl(this);
    }

    // Called from MqttSecureConnection.
    String[] getSubscribeTo() {
        // MqttSecureConnection assumes the return value is not null!
        return new String[] {
                this.subscribeMessagesTopic,
                this.subscribeMessagesErrorTopic,
                this.subscribeMessagesAcceptBytesTopic
        };
    }

    // Called from MqttSecureConnection.
    int[] getSubscribeQos() {
        // MqttSecureConnection assumes the return value is not null!
        return new int[] {
                MqttSecureConnection.QOS_AT_LEAST_ONCE,
                MqttSecureConnection.QOS_AT_LEAST_ONCE,
                MqttSecureConnection.QOS_AT_LEAST_ONCE
        };
    }

    private int lastAvaliableBytes = -1;

    @Override
    // timeout is only needed the HTTP long polling.
    protected synchronized void post(byte[] payload, int timeout) throws IOException, GeneralSecurityException {
        post(payload);
    }

    @Override
    protected synchronized void post(byte[] payload) throws IOException, GeneralSecurityException {

        final int requestBufferSize = getRequestBufferSize();
        final int usedBytes = getUsedBytes();
        final int avaliableBytes = requestBufferSize - usedBytes - 2;
        if (avaliableBytes != lastAvaliableBytes) {
            lastAvaliableBytes = avaliableBytes;
            final byte[] acceptBytes = toBytes(avaliableBytes);
            post(this.publishMessagesAcceptBytesTopic, acceptBytes, null);
        }
        post(this.publishMessagesTopic, payload, this.subscribeMessagesTopic);

    }

    private boolean post(String topic, byte[] payload, String expect)
            throws IOException, GeneralSecurityException {

        if (getLogger().isLoggable(Level.FINER)) {

            String msg = new StringBuilder("POST ")
                    .append(topic)
                    .append(" : ")
                    .append(Message.prettyPrintJson(payload))
                    .toString();

            getLogger().log(Level.FINER, msg);
        }

        HttpResponse response = secureConnection.publish(topic, payload, expect);
        int code = response.getStatus();

        if (getLogger().isLoggable(Level.FINEST)) {
            getLogger().log(Level.FINEST, response.getVerboseStatus("POST", topic));
        }

        // 401 means the credentials have expired. 403 means we're probably
        // using client-secret where we need client-credentials. If
        // HttpResponse contains a 401 or 403, MqttSecureConnectionImpl will
        // forcibly disconnect the client. Here, we retry the publish which
        // will reconnect the client with fresh credentials.
        if (code == 401 || code == 403) {
            response = secureConnection.publish(topic, payload, expect);
            code = response.getStatus();
        }
        if (code != 202) {
            MqttSecureConnection.getLogger().log(Level.INFO,response.getVerboseStatus("publish", topic));
            return false;
        }
        return true;
    }

    HttpResponse handleMessage(String topic, MqttMessage mqttMessage) {

        if (!topic.startsWith(this.subscribeMessagesTopic)) return null;

        MqttSecureConnection.getLogger().log(Level.FINEST, topic);

        final byte[] payload  = mqttMessage.getPayload();

        // MqttMessage does not allow null payload, but it may be empty
        if (payload.length == 0) return ACCEPTED;

        if (this.subscribeMessagesTopic.equals(topic)) {

            if (2 < payload.length) {

                Thread thread = new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                MqttSendReceiveImpl.this.bufferRequest(payload);
                            }
                        }
                );
                thread.start();

            }
            return ACCEPTED;

        } else if (this.subscribeMessagesErrorTopic.equals(topic)){
            return MqttSecureConnectionImpl.getErrorResponse(payload);

        } else if (this.subscribeMessagesAcceptBytesTopic.equals(topic)){

            handleAcceptBytes(mqttMessage.getPayload());
            return ACCEPTED;

        }


        final String msg = "unexpected topic: " + topic;
        final byte[] msgBytes;
        try {
            msgBytes = msg.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is a required encoding, so this can't happen.
            // Throw runtime exception to make compiler happy.
            throw new RuntimeException(e);
        }

        getLogger().log(Level.SEVERE, msg);
        return new HttpResponse(StatusCode.OTHER.getCode(), msgBytes, null);
    }

    private final static int INTEGER_BYTES = Integer.SIZE / Byte.SIZE;

    private void handleAcceptBytes(final byte[] payload) {

        if (payload.length > INTEGER_BYTES) {

            getLogger().log(Level.SEVERE,
                    this.subscribeMessagesAcceptBytesTopic +
                            " ignored. Expected <= " + INTEGER_BYTES +
                            " bytes payload, received:" +
                            Arrays.toString(payload));
            return;
        }

        Thread thread = new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        // See comments in HttpSendReceiveImpl
                        final int requestBufferSize = MqttSendReceiveImpl.this.getRequestBufferSize();
                        final int usedBytes = MqttSendReceiveImpl.this.getUsedBytes();
                        final int availableBytes = requestBufferSize - usedBytes - 2;

                        final int minAcceptBytes = toInt(payload);

                        if (minAcceptBytes > requestBufferSize) {
                            //
                            // The request the server has is larger than buffer.
                            //
                            getLogger().log(Level.SEVERE,
                                    "The server has a request of " + minAcceptBytes +
                                            " bytes for this client, which is too large for the " +
                                            requestBufferSize + " byte request buffer. Please " +
                                            "restart the client with larger value for " +
                                            REQUEST_BUFFER_SIZE_PROPERTY);

                        } else if (minAcceptBytes > availableBytes) {

                            //
                            // The message(s) from the last time have not been processed.
                            //
                            getLogger().log(Level.WARNING,
                                    "The server has a request of " + minAcceptBytes +
                                            " bytes for this client, which cannot be sent because " +
                                            "the " + requestBufferSize + " byte request buffer is " +
                                            "filled with " + usedBytes + " of unprocessed requests");
                        }

                        // If minAcceptBytes is less than available bytes, then the
                        // MQTT bridge thinks the client has less buffer available than
                        // the client really does. This is ok. The next time the client
                        // sends a message, everything will sync up again.
                    }
                });
        thread.setDaemon(false);
        thread.start();
    }

    // package for testing
    static int toInt(byte[] buf) {

        // NOTE: assuming buf.length <= INTEGER_BYTES!
        int ival = 0;
        for (int n=0; n<buf.length; n++) {
            ival <<= 8;
            ival |= (buf[n] & 0xFF);
        }
        return ival;
    }

    // package for testing
    static byte[] toBytes(int ival) {

        final byte[] buf = new byte[INTEGER_BYTES];
        for (int n=INTEGER_BYTES-1; 0 <= n; n--) {
            int byteval = (ival & 0xff);
            buf[n] = (byte)byteval;
            ival >>>= 8;
        }
        return buf;
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
