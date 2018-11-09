/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.impl.enterprise;

import com.oracle.iot.client.RestApi;
import com.oracle.iot.client.impl.http.HttpSecureConnection;
import com.oracle.iot.client.message.Message;
import com.oracle.iot.client.message.MessageParsingException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@code MessageIterator} can be used to enumerate messages matching a set
 * of criteria.
 * When iterating, it may generate REST calls to get the next page of
 * messages.
 */
public final class MessageIterator extends PageableEnumerator<Message> {
    /**
     * The application identifier: namespace to use to look for messages
     */
    private final String appID;
    /**
     * The {@code SecureHttpConnection} to handle requests.
     */
    private final HttpSecureConnection secureConnection;

    /**
     * The identifier of the device which is the source of messages
     */
    private final String deviceID;

    /**
     * The value of the parameter to put in the query.
     */
    private final Message.Type type;

    /**
     * The value of the expand parameter to put in the query, if true,
     * the default value, the value is not sent.
     */
    private final boolean expand;

    /**
     * Epoch time in milliseconds used to retrieve the messages
     * with message recorded time (either received-time or sent-time) by the 
     * cloud service that is greater than or equal to this time.
     * If the value is 0 the parameter is not sent.
     */
    private final long since;

    /**
     * Epoch time in milliseconds used to retrieve the messages
     * with message recorded time (either received-time or sent-time) by the
     * cloud service that is less than this time. If the value is 0 
     * the value is not sent.
     */
    private final long until;

    /**
     * Ordering of messages by received time: newest first (descending) or oldest first (ascending)
     */
    private final boolean newestFirst;

    private PagedResponse<Message> firstPage;

    /**
     * Instantiate a new iterator.
     *
     * @param appID            the application identifier
     * @param offset           the offset of the first item to retrieve
     * @param refillCount      the maximum number of items requested per
     *                         REST call
     * @param deviceID         the identifier of the device to get messages.
     *                         If {@code null}, messages coming from any device are listed.
     * @param type             the type of messages to enumerate
     *                         If {@code null}, all message types are enumerated.
     * @param expand           indicates that messages should include the message
     *                         payload along with core message properties
     * @param since            Epoch time in milliseconds used to retrieve the messages
     *                         with message recorded time (either received-time or sent-time) by the
     *                         cloud service that is greater than or equal to this time. If the
     *                         value is 0 the parameter is ignored.
     * @param until            Epoch time in milliseconds used to retrieve the messages
     *                         with message recorded time (either received-time or sent-time) by the
     *                         cloud service that is less than this time. If the value is 0
     *                         the parameter is ignored.
     * @param secureConnection a connection to the server
     * @param newestFirst      indicates message received time ordering: newest first or oldest first.
     *
     * @throws IOException if request for messages failed
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid
     */
    public MessageIterator(String appID, int offset, int refillCount,
            String deviceID, Message.Type type,
            boolean expand, long since, long until,
            HttpSecureConnection secureConnection,
            boolean newestFirst)
            throws IOException, GeneralSecurityException {
        super(offset, refillCount);
        this.appID = appID;
        this.secureConnection = secureConnection;
        this.deviceID = deviceID;
        this.type = type;
        this.expand = expand;
        this.since = since;
        this.until = until;
        this.newestFirst = newestFirst;

        firstPage = load(offset, refillCount);
        this.size = this.getCount();
        this.hasMore = !firstPage.elements().isEmpty();
    }

    private int getCount() {
        try {
            // Creates a new request to get the count of devices matching
            // the filter
            MessageRequest req =
                new MessageRequest(this.appID, "count", this.offset, limit);
            JSONObject jsonObject = this.get(req.headers(), req.request());
            return jsonObject.optInt("count", -1);
        } catch (IOException ignored) {
        } catch (GeneralSecurityException ignored) {
        }
        return -1;
    }

    protected HttpSecureConnection getSecureConnection() {
        return secureConnection;
    }

    protected PagedResponse<Message> load(int offset, int limit)
            throws IOException {
        if (firstPage != null) {
            PagedResponse<Message> page = firstPage;
            firstPage = null;
            if (offset == 0) {
                return page;
            }
        }

        HashMap<String, String> headers = new HashMap<String, String>(2);
        headers.put("Accept", "application/json");

        final String request;
        try {
            request = createMessageRequest(this.appID, limit, offset, deviceID,
                type, expand, since, until, newestFirst);
        } catch (URISyntaxException e) {
            throw new IOException(e.getMessage());
        }

        try {
            JSONObject response = get(headers, request);
            return MessageResponse.fromJson(response, offset);
        } catch (JSONException e) {
            throw new IOException("GET " + request + ": " + e.getMessage());
        } catch (GeneralSecurityException e) {
            throw new IOException("GET " + request + ": " + e.getMessage());
        }
    }

    private static String createMessageRequest(String aid, int limit, int offset,
            String deviceID, Message.Type type, boolean expand, long since,
            long until, boolean newestFirst) throws URISyntaxException {

        String query = "expand=" + expand;

        if (deviceID != null) {
            query += "&" + "device=" + deviceID;
        }

        if (type != null) {
            query += "&" + "type=" + type;
        }

        if (limit > 0) {
            query += "&" + "limit=" + limit;
        }

        if (offset > 0) {
            query += "&" + "offset=" + offset;
        }

        if (since > 0) {
            query += "&" + "since=" + since;
        }

        if (until > 0) {
            query += "&" + "until=" + until;
        }

        query += "&orderBy=deliveredTime:" + (newestFirst? "desc" : "asc");

        String request;
        if (aid == null) {
            request = RestApi.V2.getReqRoot()+"/messages";
        } else {
            request = RestApi.V2.getReqRoot()+"/apps/" + aid + "/messages";
        }
        // used to encode properly the query
        return new URI(null, null, request, query, null).toString();
    }

    static class MessageResponse extends PagedResponse<Message> {
        private MessageResponse(Collection<Message> elements, int offset, int total, boolean hasMore) {
            super(elements, offset, total, hasMore, new HashMap<String, String>());
        }

        public static MessageResponse fromJson(Object response,
                int defaultOffset) throws JSONException {
            try {
                JSONObject jsonObject = (JSONObject)response;
                JSONArray items = jsonObject.getJSONArray("items");
                boolean hasMore = jsonObject.optBoolean("hasMore", false);
                int offset = jsonObject.optInt("offset", defaultOffset);
                int total = jsonObject.optInt("totalResults", -1);  // default: unknown

                // MessagePoller will cast to this to a list and call get(i).
                List<Message> list = Message.fromJson(items);

                return new MessageResponse(list, offset, total, hasMore);

            } catch (MessageParsingException mpe) {
                return new MessageResponse(Collections.<Message>emptyList(),
                    0, 0, false);
            } catch (ClassCastException e) {
                throw new JSONException("Incorrect response format");
            }
        }
    }

    static class MessageRequest {
        /**
         * List of headers required for the REST API
         */
        private static final Map<String, String> headers;
        static {
            headers = new HashMap<String, String>();
            headers.put("Content-Type", "application/json");
            headers.put("Accept", "application/json");
        }

        /**
         * The REST request including parameters
         */
        private final String request;

        /**
         * Creates a {@code MessageRequest} matching the specified
         * criteria.
         *
         * @param appId the application identifier in which we
         *              should look for devices.
         * @param limit the maximum number of result to return
         * @param offset the offset of the results in the entire list of
         *               deviceModels matching the specified criteria
         */
        MessageRequest(String appId, String path, int offset, int limit) {
            String req;
            String query = "totalResults=true";

            if (appId == null) {
                req = RestApi.V2.getReqRoot();
            } else {
                req = RestApi.V2.getReqRoot()+"/apps/" + appId;
            }

            req += "/messages";

            if (path != null) {
                req += "/" + path;
            }

            if (limit > 0) {
                query += "&limit=" + limit;
            }

            if (offset > 0) {
                query += "&offset=" + offset;
            }

            try {
                // used to encode properly the query
                URI uri = new URI(null, null, req, query, null);
                request = uri.toString();
            } catch (URISyntaxException e) {
                /*
                 * if encoding fails, log the error and keep the request in
                 * raw format
                 */
                getLogger().log(Level.SEVERE, e.toString());
                throw new RuntimeException(e);
            }
        }

        /**
         * Get the REST call (including parameters) representing this request
         * @return the REST call (including parameters)
         */
        String request() {
            return this.request;
        }

        /**
         * Get the list of HTTP headers required for this request
         * @return HTTP headers required to perform this request
         */
        Map<String, String> headers() {
            return headers;
        }

        @Override
        public String toString() {
            return "MessageRequest: " + this.request();
        }
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
