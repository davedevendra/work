/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.impl.enterprise;

import com.oracle.iot.client.HttpResponse;
import com.oracle.iot.client.enterprise.Response;

import java.net.URI;

import com.oracle.iot.client.impl.http.HttpSecureConnection;
import com.oracle.iot.client.message.StatusCode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.net.URISyntaxException;

import java.security.GeneralSecurityException;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements {@code Response}.
 */
public class ResponseImpl implements Response {

    // request data: immutable

    /**
     * Request identifier
     */
    private final String requestId;

    /**
     * Request path
     */
    private final String path;

    /**
     * Request URI used to re-emit the request. Might be null is processed synchronously
     */
    private final URI uri;

    /**
     * Request headers used to re-emit the request. Might be null if response is processed synchronously
     */
    private final Map<String, String> requestHeaders;

    // response data: mutable

    /**
     * Response body
     */
    private byte[] body;

    /**
     * Response headers,
     */
    private Map<String, String> responseHeaders;
    /**
     * Secure connection
     */
    private HttpSecureConnection secureConnection;

    /**
     * Response status
     */
    private int statusCode;

    /**
     * {@code true} if request is done
     */
    private boolean done;

    /**
     * Create a {@link Response} instance from the specified data.
     * @param path the request path
     * @param reqHeaders the request http headers
     * @param res the http response received
     * @param secureConnection the secure connection to use
     * @return a {@link Response} instance
     * @throws IOException if response data is incorrect
     */
    public static ResponseImpl from(String path, Map<String, String> reqHeaders,
            HttpResponse res, HttpSecureConnection secureConnection)
            throws IOException {

        try {
            byte[] data = res.getData();
            if (data == null) {
                throw new IOException(path + ": No data received");
            }

            String json = new String(data, "UTF-8");
            JSONObject jsonBody = new JSONObject(json);
            String requestId = jsonBody.optString("id", "");

            switch (res.getStatus()) {
                case 200:
                    // Request is done
                    Map<String, String> respHeaders =
                        new HashMap<String, String>();
                    if (res.getHeaders() != null) {
                        Set<Map.Entry<String, List<String>>> headers =
                            res.getHeaders().entrySet();
                        for (Map.Entry<String, List<String>> header : headers) {
                            respHeaders.put(header.getKey(),
                                header.getValue().toString());
                        }
                    }

                    ResponseImpl resp = new ResponseImpl(requestId, path, null,
                        StatusCode.OK.getCode(), null, secureConnection,
                        reqHeaders, respHeaders, true);
                    resp.updateFrom(res.getData());
                    return resp;

                case 202:
                    // Request is not yet done
                    JSONArray links = jsonBody.optJSONArray("links");
                    if (links == null) {
                        throw new IOException(path + ": No links in response");
                    }

                    URI requestURI = null;
                    for (int i = 0, size = links.length(); i < size; ++i) {
                        String rel =
                            links.getJSONObject(i).getString("rel").trim();
                        if ("requests".equals(rel)) {
                            String link =
                                links.getJSONObject(i).getString("href");
                            try {
                                requestURI = new URI(link);
                            } catch (URISyntaxException e) {
                                throw new IOException(e);
                            }
                            break;
                        }
                    }

                    if (requestURI == null) {
                        throw new IOException(path +
                            ": Incorrect response: missing request link");
                    }

                    return new ResponseImpl(requestId, path, res.getData(),
                        StatusCode.ACCEPTED.getCode(), requestURI,
                        secureConnection, reqHeaders, null, false);

                default:
                    throw new IOException(res.getVerboseStatus(null, path));
            }
        } catch (JSONException e) {
            throw new IOException(path + ": " + e.getMessage());
        }
    }

    private void updateFrom(byte[] httpResponse) throws IOException {
        String json = new String(httpResponse, "UTF-8");

        try {
            JSONObject jsonObject = new JSONObject(json);
            String reqStatus = jsonObject.optString("status", "");
            if ("COMPLETED".equals(reqStatus)) {
                JSONObject respObject = jsonObject.getJSONObject("response");

                this.statusCode = respObject.getInt("statusCode");

                this.body = respObject.optString("body", "").getBytes("UTF-8");

                this.responseHeaders = new HashMap<String, String>();

                JSONObject jsonHeaders = respObject.optJSONObject("headers");
                if (jsonHeaders != null) {
                    Iterator<String> keys = jsonHeaders.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        Object value = jsonHeaders.opt(key);
                        this.responseHeaders.put(key, value.toString());
                    }
                }

                this.done = true;
                return;
            }

            if ("FAILED".equals(reqStatus)) {
                this.statusCode = StatusCode.BAD_REQUEST.getCode();
                this.done = true;
            }
        } catch (UnsupportedOperationException e) {
            throw new IOException(e);
        } catch (ClassCastException e) {
            throw new IOException(e);
        } catch (NullPointerException e) {
            throw new IOException(e);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }


    private ResponseImpl(String requestId, String path, byte[] body, int status,
                         URI requestURI,
                         HttpSecureConnection secureConnection,
                         Map<String, String> reqHeaders,
                         Map<String, String> respHeaders,
                         boolean done) {

        this.requestId = requestId;
        this.statusCode = status;
        this.path = path;
        this.uri = requestURI;
        this.requestHeaders = reqHeaders;
        this.secureConnection = secureConnection;
        this.body = body;
        this.responseHeaders = respHeaders;
        this.done = done;
    }

    @Override
    public int getStatusCode() throws IllegalStateException {
        if (!done) {
            throw new IllegalStateException("Request " + requestId +
                                            " not done.");
        }

        return statusCode;
    }

    @Override
    public String getRequestId() {
        return requestId;
    }

    @Override
    public String getPath() {
        if (path == null) {
            return uri.getPath();
        }

        return path;
    }

    @Override
    public byte[] getBody() throws IllegalStateException {
        if (!done) {
            throw new IllegalStateException("Request " + requestId +
                                            " not done.");
        }

        return body;
    }

    @Override
    public Map<String, String> getHeaders() throws IllegalStateException {
        if (!done) {
            throw new IllegalStateException("Request " + requestId +
                                            " not done.");
        }

        return responseHeaders;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public void poll() throws IOException, GeneralSecurityException {
        if (done) {
            return;
        }

        HttpResponse response = secureConnection.get(uri.getPath());

        int status = response.getStatus();

        // If the request fails return that status code.
        // For the initial request response, 
        // async requests should be 202, but if it completes
        // on the first time or is synchronous it will be 200.
        if (status != StatusCode.ACCEPTED.getCode() &&
            status != StatusCode.OK.getCode()) {
            getLogger().log(Level.INFO, response.getVerboseStatus("GET", uri.getPath()));
            statusCode = status;
            done = true;
            return;
        }

        this.updateFrom(response.getData());

    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
