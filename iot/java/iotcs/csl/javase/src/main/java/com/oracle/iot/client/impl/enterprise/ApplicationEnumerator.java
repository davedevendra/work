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

import oracle.iot.client.enterprise.Application;
import oracle.iot.client.enterprise.Filter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An {@code ApplicationEnumerator} can be used to enumerate applications.
 */
public class ApplicationEnumerator extends PageableEnumerator<String> {

    /**
     * The secure connection to use
     */
    private final HttpSecureConnection secureConnection;

    /**
     * Filter to apply to constrain the result
     */
    private final Filter filter;

    /**
     * Instantiate a new iterator.
     *
     * @param offset           the offset of the first item to fetch
     * @param limit            the maximum number of items requested per REST call
     * @param filter           a filter to constrain the response. If {@code null}, all applications are enumerated.
     * @param secureConnection the server connection
     * @throws IOException              if an I/O error occurs when retrieving the total count of elements matching the filter
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid
     */
    public ApplicationEnumerator(int offset, int limit, Filter filter, HttpSecureConnection secureConnection) throws IOException, GeneralSecurityException {

        super(offset, limit);
        this.filter = filter;
        this.secureConnection = secureConnection;
        this.setSize(this.getCount());

    }

    @Override
    protected HttpSecureConnection getSecureConnection() {
        return secureConnection;
    }

    /**
     * Performs a new REST call to get the count of applications matching the filter
     * @return the count of devices matching the request, or {@code -1} if unknown.
     */
    private int getCount() {
        try {
            // Creates a new request to get the count of devices matching the
            // filter
            ApplicationEnumerationRequest req =
                new ApplicationEnumerationRequest(null, "count", this.filter,
                this.offset, this.limit);
            JSONObject jsonObject = this.get(req.headers(), req.request());
            return jsonObject.optInt("count", -1);
        } catch (IOException ignored) {
        } catch (GeneralSecurityException ignored) {
        }

        // if not possible to get the count, return "unknown"
        return -1;
    }

    @Override
    protected PagedResponse<String> load(int offset, int limit) throws IOException {

        // Creates a new request to get the next chunk, using same criteria
        ApplicationEnumerationRequest req = new ApplicationEnumerationRequest(null, null, null, offset, limit);
        try {
            JSONObject jsonObject = this.get(req.headers(), req.request());
            return ApplicationEnumerationResponse.fromJson(jsonObject);
        } catch (Exception e) {
            throw new IOException("GET " + req.request() + ": " + e.getMessage());
        }
    }

    public static class ApplicationEnumerationRequest {

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
         * Creates an {@code ApplicationEnumerationRequest} for
         * {@code "/iot/api/v2/apps/{applicationId}/{path}?offset={offset}&limit={limit}"}
         *
         * @param applicationID the application identifier to extend to the path. If {@code null}, not added to the path.
         * @param path          the path to extend the request. If {@code null}, not added to the request.
         * @param filter        a filter to constrain the response. If {@code null}, all applications are enumerated.
         * @param offset        the offset of the first element to fetch
         * @param limit         the maximum number of elements to fetch
         */
        public ApplicationEnumerationRequest(String applicationID, String path, Filter filter, int offset, int limit) {
            String req = RestApi.V2.getReqRoot()+"/apps";
            if (applicationID != null) {
                req += "/" + applicationID;
            }
            if (path != null) {
                req += "/" + path;
            }
            String sep = "?";
            if (limit > 0) {
                req += sep + "limit=" + limit;
                sep = "&";
            }
            if (offset > 0) {
                req += sep + "offset=" + offset;
                sep = "&";
            }
            if (filter != null) {
                try {
                    String query = "q=" + URLEncoder.encode(
                        filter.toJson().toString(), "UTF8");
                    req += sep + query;
                } catch (UnsupportedEncodingException ignored) {
                }
            }
            this.request = req;
        }

        public String request() {
            return this.request;
        }

        public Map<String, String> headers() {
            return headers;
        }

    }

    public static class ApplicationEnumerationResponse extends
        PagedResponse<String> {

        ApplicationEnumerationResponse(Collection<String> appsId, int offset,
                int total, boolean hasMore, Map<String, String> links) {
            super(appsId, offset, total, hasMore, links);
        }

        public static ApplicationEnumerationResponse fromJson(
            JSONObject jsonObject) throws JSONException {

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
                    for (int i = 0, size = items.length(); i < size; i++) {
                        Object item = items.opt(i);
                        Application app =
                            ApplicationImpl.fromJson((JSONObject) item);
                        list.add(app.getId());
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

                return new ApplicationEnumerationResponse(list, offset, total,
                    hasMore, map);
            } catch (ClassCastException e) {
                throw new JSONException("Incorrect response format");
            }
        }
    }
}
