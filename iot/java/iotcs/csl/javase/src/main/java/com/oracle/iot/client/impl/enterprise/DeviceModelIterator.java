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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import java.net.URI;
import java.net.URISyntaxException;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * A {@code DeviceModelIterator} can be used to enumerate deviceModels matching a set
 * of criteria.
 * When iterating, it may generates REST calls to get the next chunk of
 * deviceModels.
 */
public final class DeviceModelIterator extends PageableEnumerator<String> {

    /**
     * The {@code TrustedAssetsManager} to store,
     * load and handle the device trust material.
     */
    private final HttpSecureConnection secureConnection;

    /**
     * The application identifier in which we should look for devices.
     */
    private final String appId;

    /**
     * Instantiate a new iterator.
     *
     * @param appId                the application identifier in which we should
     *                             look for devices, or {@code null}. If {@code null},
     *                             all device models are returned.
     * @param offset               the offset of the first element to fetch
     * @param limit                the maximum number of items requested per REST call
     * @param secureConnection    a connection to the server
     *
     * @throws IOException              if request for deviceModels failed
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid
     */
    public DeviceModelIterator(String appId, int offset, int limit, HttpSecureConnection secureConnection)
            throws IOException, GeneralSecurityException {

        super(offset, limit);

        this.appId = appId;
        this.secureConnection = secureConnection;
        this.setSize(this.getCount());
    }

    /**
     * Performs a new REST call to get the count of device models
     * @return the count of devices models, or {@code -1} if unknown.
     */
    private int getCount() {
        try {
            // Creates a new request to get the count of devices matching the
            // filter
            DeviceModelRequest req =
                new DeviceModelRequest(appId, "count", offset, limit);
            JSONObject jsonObject = this.get(req.headers(), req.request());
            return jsonObject.optInt("count", -1);
        } catch (IOException ignored) {
        } catch (GeneralSecurityException ignored) {
        }
        return -1;
    }

    /**
     * Performs a new REST call to refill internal cache.
     *
     * @param offset: the offset of first element to fetch
     * @param limit:  the maximum number of elements to fetch
     */
    protected PagedResponse<String> load(int offset, int limit) throws IOException {
        // Creates a new request to get the next chunk, using same criteria
        DeviceModelRequest req = new DeviceModelRequest(appId, null, offset, limit);

        try {
            // always refill: remote collection might have evolved (new entries added)
            JSONObject object = this.get(req.headers(), req.request());
            return DeviceModelResponse.fromJson(object);
        } catch (JSONException e) {
            throw new IOException("GET " + req.request() + ": " + e.getMessage());
        } catch (GeneralSecurityException e) {
            throw new IOException("GET " + req.request() + ": " + e.getMessage());
        }
    }

    @Override
    protected HttpSecureConnection getSecureConnection() {
        return this.secureConnection;
    }

    /**
     * REST API request to get the list of deviceModels
     */
    static class DeviceModelRequest {

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
         * Creates a {@code DeviceModelRequest} matching the specified
         * criteria.
         *
         * @param appId the application identifier in which we
         *              should look for devices.
         * @param limit the maximum number of result to return
         * @param offset the offset of the results in the entire list of
         *               deviceModels matching the specified criteria
         */
        DeviceModelRequest(String appId, String path, int offset, int limit) {
            String req;
            String query = "totalResults=true";

            if (appId == null) {
                req = RestApi.V2.getReqRoot();
            } else {
                req = RestApi.V2.getReqRoot()+"/apps/" + appId;
            }

            req += "/deviceModels";

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
            return "DeviceModelRequest: " + this.request();
        }
    }

    private static class DeviceModelResponse extends PagedResponse<String> {

        private DeviceModelResponse(Collection<String> elements, int offset, int total, boolean hasMore, Map<String, String> links) {
            super(elements, offset, total, hasMore, links);
        }

        private static DeviceModelResponse fromJson(JSONObject jsonObject)
                throws JSONException {

            try {
                JSONArray items = (JSONArray)jsonObject.opt("items");
                JSONArray links = (JSONArray)jsonObject.opt("links");
                boolean hasMore = jsonObject.optBoolean("hasMore", false);
                // default: unknown
                int offset = jsonObject.optInt("offset", -1);
                // default: unknown
                int total = jsonObject.optInt("totalResults", -1);

                List<String> list = new ArrayList<String>();
                if (items != null) {
                    // for each item, create an endpoint from json stream
                    for (int i = 0, size = items.length(); i < size; i++) {
                        JSONObject model = (JSONObject)items.opt(i);
                        String urn = model.optString("urn", null);
                        if (urn != null) {
                            list.add(urn);
                        }
                    }
                }

                HashMap<String, String> map = new HashMap<String, String>();
                if (links != null) {
                    for (int i = 0, size = links.length(); i < size; i++) {
                        JSONObject object = (JSONObject)links.opt(i);
                        String rel = object.optString("rel", null);
                        String href = object.optString("href", null);
                        if (rel != null && href != null) {
                            map.put(rel, href);
                        }
                    }
                }

                return new DeviceModelResponse(list, offset, total, hasMore,
                                               map);
            } catch (ClassCastException e) {
                throw new JSONException("Incorrect response format");
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
