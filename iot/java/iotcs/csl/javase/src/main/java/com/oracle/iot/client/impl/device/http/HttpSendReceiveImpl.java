/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device.http;

import com.oracle.iot.client.TransportException;
import com.oracle.iot.client.RestApi;
import com.oracle.iot.client.impl.http.HttpSecureConnection;
import com.oracle.iot.client.device.DirectlyConnectedDevice;
import com.oracle.iot.client.HttpResponse;
import com.oracle.iot.client.impl.device.SendReceiveImpl;
import com.oracle.iot.client.message.Message;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HttpSendReceiveImpl is an implementation of the send(Message...) method of
 * {@link DirectlyConnectedDevice}. The send is
 * synchronous. Receive should be considered as a synchronous call. The
 * implementation has a buffer for receiving request messages. If there are
 * no messages in the buffer, the receive implementation will send a message
 * to the server to receive any pending requests the server may have.
 * <p>
 * The size of the buffer can be configured by setting the property {@code TBD}.
 */
public final class HttpSendReceiveImpl extends SendReceiveImpl {

    private static final String LONG_POLL_OFFSET_PROP =
        "com.oracle.iot.client.device.long_polling_timeout_offset";
    private static final String MIN_ACCEPT_BYTES_HEADER = "X-min-acceptBytes";
    private static final int USE_DEFAULT_TIMEOUT_VALUE = -1;
    private static final int LONG_POLL_OFFSET =
        Integer.getInteger(LONG_POLL_OFFSET_PROP, 100);
    private final HttpSecureConnection secureConnection;

    public HttpSendReceiveImpl(HttpSecureConnection secureConnection) {
        super();
        this.secureConnection = secureConnection;
    }

    /*************************************************************************
     *
     * Methods and classes for handling outgoing message dispatch
     * Implementation and API in this section should be private
     *
     *************************************************************************/

    /*
     * Called from post(Collection<MessageQueueEntry>). This simply makes
     * the other code easier to read.
     */
    @Override
    protected void post(byte[] payload) throws IOException,
            GeneralSecurityException {
        post(payload, USE_DEFAULT_TIMEOUT_VALUE);
    }

    @Override
    protected void post(byte[] payload, int timeout) throws IOException,
            GeneralSecurityException {

        StringBuffer restApi = new StringBuffer(RestApi.V2.getReqRoot())
            .append("/messages");

        int usedBytes = 0;
        int availableBytes = 0;
        if (!useLongPolling || payload == null) {
            // head and tail are kept so that they are less than requestBuffer.length.
            // This avoids the (unlikely) situation that they would overflow.
            // But this also means that tail might be less than head, since the buffer
            // is circular. This has to be taken into account when calculating the
            // available bytes. Remember that tail is the pointer to the next
            // available chunk and does not count toward used bytes (no need to
            // adjust used byte count by 1). If tail == head, then used bytes is
            // zero - perfect!
            usedBytes = getUsedBytes();

            // subtract 2 for the length bytes.
            // TODO: subtracting 2 may fail if there is more than one message
            // in the received buffer. Say we had 100 bytes available and we two
            // messages of 49 bytes. We'd write 2 bytes for the 49, then the
            // 49 bytes. Now we only have 49 bytes of buffer left, which is not
            // enough to hold 2 bytes of length plus 49 bytes of message.
            availableBytes = getRequestBufferSize() - usedBytes - 2;
        }

        restApi.append("?acceptBytes=").append(availableBytes);

        boolean longPolling = useLongPolling && availableBytes > 0;
        if (longPolling) {
            restApi.append("&iot.sync");

            if (timeout > 0) {
                // iot.timeout is in seconds
                int iotTimeout = timeout / 1000;
                if (iotTimeout == 0) {
                    iotTimeout = 1;
                }

                restApi.append("&iot.timeout=");
                restApi.append(iotTimeout);

                /*
                 * Add an offset to the transport level timeout
                 * as a failover mechanism, just in case.
                 */
                timeout = (iotTimeout * 1000) + LONG_POLL_OFFSET;
            }
        }

        final String uri = restApi.toString();

        if (getLogger().isLoggable(Level.FINER)) {

            String msg = new StringBuilder("POST ")
                    .append(uri)
                    .append(" : ")
                    .append(Message.prettyPrintJson(payload))
                    .toString();

            getLogger().log(Level.FINER, msg);
        }

        HttpResponse response;
        try {
            response =
                secureConnection.post(uri, payload, timeout);
        } catch (IOException ie) {
            // Do not throw the SocketTimeoutException if the post was for long polling.
            // The SocketTimeoutException is expected for long polling and just
            // means that the server closed its end.
            if (longPolling && ie instanceof java.net.SocketTimeoutException) {
                return;
            }

            throw ie;
        }

        if (getLogger().isLoggable(Level.FINEST)) {
            getLogger().log(Level.FINEST, response.getVerboseStatus("POST", uri));
        }

        final int status = response.getStatus();

        if (status == 202 || status == 200) {
            if (availableBytes == 0) {
                return;
            }

            Map<String, List<String>> map = response.getHeaders();
            List<String> header = map.get(MIN_ACCEPT_BYTES_HEADER);
            if (header != null && !header.isEmpty()) {
                int minAcceptBytes = Integer.parseInt(header.get(0));

                //
                // The next request the server has for this client is larger
                // than the bytes available in the request buffer.
                //
                // A properly configured and responsive system should never
                // get in this state.
                //

                final int requestBufferSize = getRequestBufferSize();
                if (minAcceptBytes > requestBufferSize - 2) {
                    //
                    // The request the server has is larger than buffer.
                    //
                    getLogger().log(Level.SEVERE,
                        "The server has a request of " + minAcceptBytes +
                        " bytes for this client, which is too large for the " +
                        requestBufferSize + " byte request buffer. Please " +
                        "restart the client with larger value for " +
                        REQUEST_BUFFER_SIZE_PROPERTY);
                } else {
                    //
                    // The message(s) from the last time have not been
                    // processed.
                    //
                    getLogger().log(Level.WARNING,
                        "The server has a request of " + minAcceptBytes +
                        " bytes for this client, which cannot be sent " +
                        " because the " + requestBufferSize +
                        " byte request buffer is filled with " + usedBytes +
                        " of unprocessed requests");
                }
                return;
            }

            bufferRequest(response.getData());

            return;

        } else {
            throw new TransportException(response.getStatus(),
                response.getVerboseStatus("POST", restApi.toString()));
        }

    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
