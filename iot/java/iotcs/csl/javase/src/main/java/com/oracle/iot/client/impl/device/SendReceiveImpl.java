/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import com.oracle.iot.client.device.DirectlyConnectedDevice;
import com.oracle.iot.client.message.Message;
import com.oracle.iot.client.message.RequestMessage;

import com.oracle.iot.client.message.ResponseMessage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SendReceiveImpl is an implementation of the send(Message...) method of
 * {@link DirectlyConnectedDevice}. The send is
 * synchronous. Receive should be considered as a synchronous call. The
 * implementation has a buffer for receiving request messages. If there are
 * no messages in the buffer, the receive implementation will send a message
 * to the server to receive any pending requests the server may have.
 * <p>
 * The size of the buffer can be configured by setting the property {@code TBD}.
 */
public abstract class SendReceiveImpl {

    /*
     * Messages from server to be handled. The buffer is a circular buffer of
     * bytes. The first two bytes at the head of the buffer always contains
     * the number of bytes in the message that follows. The tail points to the
     * next available chunk of buffer.  When bytes are written to the buffer,
     * the bytes are written from (tail+2) to the end of the buffer, and then
     * from buffer[0] if necessary. In this way, the buffer wraps from tail
     * to head. Head keeps moving as messages are read. Tail keeps moving as
     * requests are received. Bytes in the buffer never have to be moved.
     *
     * The number of bytes available are sent to the server in a query parameter
     * to the messages REST API:
     * POST /iot/api/v2/messages?acceptBytes=<number-of-bytes>
     */

    private static final short DEFAULT_REQUEST_BUFFER_SIZE = 4192;
    private static final short DEFAULT_SEND_RECEIVE_TIMEOUT = 100; // in milliseconds

    protected static final String REQUEST_BUFFER_SIZE_PROPERTY = "oracle.iot.client.device.request_buffer_size";
    private static final String DISABLE_LONG_POLLING_PROPERTY =
        "com.oracle.iot.client.disable_long_polling";
    private static final String SEND_RECEIVE_TIMEOUT_PROPERTY = "oracle.iot.client.device.send_receive_timeout";

    private static final String MIN_ACCEPT_BYTES_HEADER = "X-min-acceptBytes";
    private static final int USE_DEFAULT_TIMEOUT_VALUE = -1;

    private static short getShortPropertyValue(String key, short defaultValue) {
        short imax = Utils.getShortProperty(key, defaultValue);
        short max = (imax > defaultValue ? imax : defaultValue);
        return (max > 0 ? max : defaultValue);
    }

    private static short getSendReceiveTimeout(String key, short defaultValue) {
        short imax = Utils.getShortProperty(key, defaultValue);
        if (imax == 0)
            // allow to set 0 send_receive_timeout which would mean don't 'post(null)'
            return 0;
        short max = (imax > defaultValue ? imax : defaultValue);
        // set to property value if it's greater then default
        return (max > 0 ? max : defaultValue);
    }

    private final byte[] requestBuffer;
    private int head;
    private int tail;

    protected final boolean useLongPolling;

    private final short  sendReceiveTimeLimit;

    private long sendCallTime ;

    private static class CircularBufferInputStream extends InputStream {

        private final byte[] buffer;
        private int pos;
        private final int eos;

        private CircularBufferInputStream(byte[] buffer, int head, int nBytes) {
            this.buffer = buffer;
            this.pos = head;
            this.eos = this.pos + nBytes;
            if (nBytes > buffer.length) {
                throw new IllegalArgumentException(
                        nBytes
                        + " bytes requested but buffer only has "
                        + buffer.length + " bytes"
                );
            }
        }

        @Override
        public synchronized int read() throws IOException {
            return (pos < eos) ? buffer[(pos++ % buffer.length)] : -1;
        }

        @Override
        public void close() throws IOException {

        }
    }

    private final short requestBufferSize;

    /**
     */
    protected SendReceiveImpl() {

        requestBufferSize =
                getShortPropertyValue(REQUEST_BUFFER_SIZE_PROPERTY,
                                      DEFAULT_REQUEST_BUFFER_SIZE);

        this.sendReceiveTimeLimit =
            getSendReceiveTimeout(SEND_RECEIVE_TIMEOUT_PROPERTY,
                                  DEFAULT_SEND_RECEIVE_TIMEOUT);

        // TODO: configurable.
        //requestBuffer = new byte[getRequestBufferSize()];
        requestBuffer = new byte[requestBufferSize];
        head = tail = 0;
        this.sendCallTime = -1;
        final String disableLongPollingPropertyValue = System.getProperty(DISABLE_LONG_POLLING_PROPERTY);
        final boolean disableLongPolling = "".equals(disableLongPollingPropertyValue) || Boolean.parseBoolean(disableLongPollingPropertyValue);
        this.useLongPolling = (this instanceof com.oracle.iot.client.impl.device.http.HttpSendReceiveImpl && !disableLongPolling);
    }

    /*
     * This is where a call to DirectlyConnectedDevice#send(Message...)
     * ends up.
     */
    public final void send(Message... messages)
            throws IOException, GeneralSecurityException {

        byte[] payload = null;

        if (messages != null && messages.length > 0) {
            JSONArray jsonArray = new JSONArray();
            for (Message message : messages) {

                // Special handling for LL actionCondition
                //     If this is a request message that loops back to the sender,
                //         don't deliver it, but queue it up for the next call to receive.
                //     If this is a response message that loops back to the sender,
                //         then don't send it to the server.
                if (message instanceof RequestMessage) {
                    if (message.getDestination() != null && message.getDestination().equals(message.getSource())) {
                        final JSONObject jsonObject = message.toJson();
                        // buffer request expects an array
                        final String jsonString = "[" + jsonObject.toString() + "]";
                        final byte[] buf = jsonString.getBytes(Charset.forName("UTF-8"));
                        if (buf.length < (getRequestBufferSize() - getUsedBytes())) {
                            bufferRequest(buf);
                            continue;
                        } else {
                            throw new IOException("could not loopback request message");
                        }
                    }
                } else if (message instanceof ResponseMessage) {
                    if (message.getDestination() != null && message.getDestination().equals(message.getSource())) {
                        continue;
                    }
                }
                jsonArray.put(message.toJson());
            }

            payload = jsonArray.toString().getBytes(Charset.forName("UTF-8"));
        }

        if (payload != null && payload.length > 2) {
            post(payload);
        }

        if (!useLongPolling) {
            this.sendCallTime = System.currentTimeMillis();
        }
    }

    /*
     * this is where a call to DirectlyConnectedDevice#receive(long) ends up.
     *
     * Timeout (in milliseconds) is used when using HTTP long polling,
     * so it is only need for the HttpSendReceiveImpl,
     * which passes it to HttpSecureConnection, which passes it to HttpClient,
     * which passes it to HttpsUrlConnection.setReadTimeout, in which 
     * timeout of zero is interpreted as an infinite timeout.
     *
     * However, if a negative value is given, it will be converted to be the
     * default response timeout.
     */
    public final synchronized RequestMessage receive(long timeout)
            throws IOException, GeneralSecurityException {

        if (head == tail) {
            if (!useLongPolling) {
                long receiveCallTime = System.currentTimeMillis();
                if (this.sendReceiveTimeLimit == 0 ||
                        (receiveCallTime - this.sendCallTime) <
                        this.sendReceiveTimeLimit) {
                    // time delta between last send and this receive is too
                    // small do not make a call to the network to get request
                    // messages
                    return null;
                } else {
                    post(null, USE_DEFAULT_TIMEOUT_VALUE);
                    this.sendCallTime = System.currentTimeMillis();
                }
            } else {
                post(null, (int)timeout);
            }
        }

        if (head != tail) {

            int nBytes = 0;
            nBytes += ((requestBuffer[(head++) % requestBuffer.length] ) & 0xFF) << 8;
            nBytes += ((requestBuffer[(head++) % requestBuffer.length] ) & 0xFF);

            int offset = head;

            // keep head < requestBuffer.length to avoid overflow of head.
            head = (head + nBytes) % requestBuffer.length;

            JSONObject jsonObject = null;
            final InputStreamReader reader;
            try {
                reader = new InputStreamReader(
                                new CircularBufferInputStream(requestBuffer, offset, nBytes),
                                "UTF-8");
            } catch (UnsupportedEncodingException ignored) {
                // UTF-8 is a required encoding, so this can't happen
                return null;
            }

            try {
                final StringBuilder stringBuilder = new StringBuilder(nBytes);
                int c;
                while ((c = reader.read()) != -1) {
                    stringBuilder.append((char)c);
                }
                final String json = stringBuilder.toString();
                jsonObject = new JSONObject(json);
            } catch (JSONException e) {
                throw new IOException(e);
            } finally {
                try {
                    reader.close();
                } catch (IOException ingored) {
                }
            }

            if (getLogger().isLoggable(Level.FINER)) {
                getLogger().log(Level.FINER, "dequeued: " + Message.prettyPrintJson(jsonObject));
            }

            RequestMessage.Builder builder =
                    new RequestMessage.Builder().fromJson(jsonObject);
            return builder.build();
        }

        return null;
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
    abstract protected void post(byte[] payload) throws IOException, GeneralSecurityException;

    /*
     * Messages are received when posting, an application when using HTTP long
     * polling can set the read timeout (in milliseconds) when receiving,
     * the so it is only need for the HttpSendReceiveImpl,
     * which passes it to HttpSecureConnection, which passes it to HttpClient,
     * which passes it to HttpsUrlConnection.setReadTimeout, in which 
     * timeout of zero is interpreted as an infinite timeout.
     *
     * However, if a negative value is given, it will be converted to be the
     * default response timeout.
     */
    abstract protected void post(byte[] payload, int timeout)
        throws IOException, GeneralSecurityException;

    final synchronized protected int getUsedBytes() {
        return tail >= head
                ? tail - head
                : (tail + requestBuffer.length) - head;
    }

    final protected int getRequestBufferSize() {
        return requestBuffer.length;
    }

    final synchronized protected void bufferRequest(final byte[] data) {

        // if data.length == 2, then it is an empty json array and there are
        // no values in the message.
        if (data != null && data.length > 2) {
            int pos = 1;
            while (data[pos] == '{') {
                final int start = pos;
                int lengthIndex0 = tail;
                // first two bytes is for length, start buffering json bytes at tail + 2
                tail += 2;
                // json objects start with '{', end with '}'
                // count braces to pair '{' with matching '}'
                int braceCount = 0;
                while (pos < data.length) {
                    final byte b = data[pos++];
                    requestBuffer[tail++ % requestBuffer.length] = b;
                    if (b == '{') braceCount++;
                    else if (b == '}' && --braceCount == 0) break;
                }

                final int nbytes = pos - start;
                requestBuffer[lengthIndex0++ % requestBuffer.length] =
                        (byte)((0xff00 & nbytes) >> 8);
                requestBuffer[lengthIndex0 % requestBuffer.length] =
                        (byte)((0x00ff & nbytes));

                // get past the end of the json object
                // json objects end with '}' and json objects are separated by ','
                if(pos < data.length && data[pos] == '}') pos++;
                if(pos < data.length && data[pos] == ',') pos++;

                if (getLogger().isLoggable(Level.FINEST)) {
                    final JSONObject jsonObject;
                    final InputStreamReader reader;
                    try {
                        reader = new InputStreamReader(
                                        new CircularBufferInputStream(requestBuffer, start, nbytes),
                                        "UTF-8");
                    } catch (UnsupportedEncodingException ignored) {
                        // UTF-8 is a required encoding, so this can't happen
                        return;
                    }

                    try {
                        final StringBuilder stringBuilder = new StringBuilder(nbytes);
                        int c;
                        while ((c = reader.read()) != -1) {
                            stringBuilder.append((char)c);
                        }
                        final String json = stringBuilder.toString();
                        jsonObject = new JSONObject(json);

                        getLogger().log(Level.FINEST, "buffered: " + Message.prettyPrintJson(jsonObject));
                    } catch (JSONException ignored) {
                        // The JSONException will be thrown as the cause of
                        // a IOException when the message is read (by calling
                        // receive).
                    } catch (IOException ignored) {
                        // CircularBufferInputStream doesn't throw IOException;
                        // therefore, the InputStreamReader won't throw it either.
                        // But it is a checked exception so we have to catch it.
                    } finally {
                        try {
                            reader.close();
                        } catch (IOException ignored) {
                        }
                    }
                }

            }
        }
        
        // adjust tail to keep it < requestBuffer.length to avoid overflow.
        tail = tail % requestBuffer.length;

        // Logging done here since we need the adjusted tail to get the
        // correct available bytes remaining in the buffer.
        if (data != null && data.length > 2) {

            if (getLogger().isLoggable(Level.FINEST)) {
                // subtract 2 for the length bytes.
                final int availableBytes = getRequestBufferSize() - getUsedBytes() - 2;
                getLogger().log(Level.FINEST,
                        "buffered " + data.length + " bytes of request data from server. " +
                                availableBytes + " available bytes of " +
                                getRequestBufferSize() + " remaining in buffer."
                );
            }
        }

    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
