/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.enterprise;

import com.oracle.iot.client.RestApi;
import com.oracle.iot.client.impl.enterprise.DeviceAppImpl;
import com.oracle.iot.client.impl.enterprise.PageableEnumerator;
import com.oracle.iot.client.impl.enterprise.SecureHttpConnectionMap;
import com.oracle.iot.client.impl.http.HttpSecureConnection;

import oracle.iot.client.enterprise.EnterpriseClient;
import oracle.iot.client.enterprise.Filter;
import oracle.iot.client.enterprise.Pageable;
import oracle.iot.client.enterprise.UserAuthenticationException;

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
import java.util.logging.Logger;

/**
 * A {@code DeviceAppEnumerator} can be used to enumerate device applications.
 */
public final class DeviceAppEnumerator {
    /**
     * The secure connection to use
     */
    private final HttpSecureConnection secureConnection;

    /**
     * The application identifier: namespace to use to look for messages
     */
    private final String appID;

    /**
     * Creates a message enumerator to retrieve messages.
     * @param client the client to use to retrieve messages
     *
     * @throws NullPointerException if the client is {@code null}
     */
    public DeviceAppEnumerator(EnterpriseClient client) {
        if (client == null) {
            throw new NullPointerException();
        }

        String id = null;
        if (client.getApplication() != null) {
            id = client.getApplication().getId();
        }
        this.appID = id;

        this.secureConnection =
                SecureHttpConnectionMap.getSecureHttpConnection(client);
    }

    /**
     * Return a {@link Pageable} collection of {@link DeviceApp} matching the specified filter.
     * <p>
     * The {@code filter} forms a query. If not {@code null}, only device applications that satisfy the {@code filter}
     * are returned.
     *
     * @param filter A filter to constrain the collection of device apps. If {@code null}, all the device applications
     *               are returned.
     *
     * @return an {@link Pageable} collection of {@link DeviceApp} that satisfy the {@code filter} and {@code status}.
     * If no matching device application is found, an {@link Pageable} with no elements is returned.
     *
     * @throws IOException              if an I/O error occurred when trying to retrieve data from server
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid. If User Authentication is used,
     *                                  then {@link UserAuthenticationException} will be thrown
     *                                  if the session cookie has expired.
     */
    public Pageable<DeviceApp> getDeviceApps(Filter filter) throws IOException, GeneralSecurityException {
        return new DeviceAppPageable(this.appID, 0, 10, filter, this.secureConnection);
    }

    private static final class DeviceAppPageable extends PageableEnumerator<DeviceApp> {
        /**
         * Filter to apply to constrain the result
         */
        private final Filter filter;

        /**
         * The application identifier: namespace to use to look for devices
         */
        private final String appID;

        /**
         * The secure connection to use
         */
        private final HttpSecureConnection secureConnection;

        /**
         * Instantiate a new iterator.
         *
         * @param appID            the application identifier if looking for devices of this application, otherwise {@code null}.
         * @param offset           the offset of the first item to fetch
         * @param limit            the maximum number of items requested per REST call
         * @param filter           List of filters to apply to the result
         * @param secureConnection the server connection
         * @throws IOException              if an I/O error occurs when retrieving the total count of elements matching the filter
         * @throws GeneralSecurityException when key or signature algorithm class
         *                                  cannot be loaded, or the key is not in
         *                                  the trusted assets store, or the
         *                                  private key is invalid. If User Authentication is used,
         *                                  then {@link UserAuthenticationException} will be thrown
         *                                  if the session cookie has expired.
         */
        public DeviceAppPageable(String appID,
                                 int offset,
                                 int limit,
                                 Filter filter,
                                 HttpSecureConnection secureConnection) throws IOException, GeneralSecurityException {

            super(offset, limit);
            this.appID = appID;
            this.filter = filter;
            this.secureConnection = secureConnection;
            this.setSize(this.getCount());
        }

        /**
         * Performs a new REST call to get the count of deviceApps matching
         * the filter
         *
         * @return the count of deviceApps matching the request, or
         * {@code -1} if unknown.
         */
        private int getCount() {
            // Creates a new request to get the count of devices matching
            // the filter
            DeviceAppEnumerationRequest req =
                new DeviceAppEnumerationRequest(appID, "count", offset, limit,
                filter);
            try {
                JSONObject jsonObject = this.get(req.headers(), req.request());
                return jsonObject.optInt("count", -1);
            } catch (IOException ignored) {
            } catch (GeneralSecurityException ignored) {
            }
            // if not possible to get the count, return "unknown"
            return -1;
        }


        /**
         * {@inheritDoc}
         */
        @Override
        protected HttpSecureConnection getSecureConnection() {
            return secureConnection;
        }

        /**
         * Performs a new REST call to refill internal cache.
         *
         * @param offset: the offset of first element to fetch
         * @param limit:  the maximum number of elements to fetch
         * @return the new page loaded
         * @throws IOException if an I/O error occurs during the REST call
         */
        @Override
        protected PagedResponse<DeviceApp> load(int offset, int limit) throws IOException {
            // Creates a new request to get the next chunk, using same criteria
            DeviceAppEnumerationRequest req = new DeviceAppEnumerationRequest(appID, null, offset, limit, filter);
            try {
                JSONObject jsonObject = this.get(req.headers(), req.request());
                return DeviceAppEnumerationResponse.fromJson(jsonObject);
            } catch (JSONException e) {
                throw new IOException("GET " + req.request() + ": " + e.getMessage());
            } catch (GeneralSecurityException e) {
                throw new IOException("GET " + req.request() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Creates a request for GET iot/api/v2/deviceApps or GET iot/api/v2/apps/{a-id}/deviceApps
     */
    private static class DeviceAppEnumerationRequest {
        /**
         * List of headers required for the REST API
         */
        private static final Map<String, String> HEADERS;

        static {
            HEADERS = new HashMap<String, String>();
            HEADERS.put("Content-Type", "application/json");
            HEADERS.put("Accept", "application/json");
        }

        /**
         * The REST request including parameters
         */
        private final String request;

        /**
         * Creates a {@code DeviceEnumerationRequest} matching the specified criteria.
         * {@code "/iot/api/v2/apps/{applicationId}/devices/{path}?offset={offset}&limit={limit},"}
         *
         * @param applicationID the application identifier in which we should look for devices.
         * @param path          the path to extend the request. If {@code null}, not added to the request.
         * @param limit         the maximum number of result to return
         * @param offset        the offset of the results in the entire list of devices matching the specified criteria
         * @param filter        filter to be applied
         */
        private DeviceAppEnumerationRequest(String applicationID, String path, int offset, int limit, Filter filter) {

            String req;
            String sep = "?";

            if (applicationID == null) {
                req = RestApi.V2.getReqRoot();
            } else {
                req = RestApi.V2.getReqRoot()+ "/apps/" + applicationID;
            }
            req += "/deviceApps";
            if (path != null) {
                req += "/" + path;
            }
            if (limit > 0) {
                req += sep + "limit=" + limit;
                sep = "&";
            }
            if (offset > 0) {
                req += sep + "offset=" + offset;
                sep = "&";
            }
            req += sep + "totalResults=true";

            // build query
            if (filter != null) {
                req += "&q=";
                req += filter.toString();
            }
            // Encode properly
            int queryPos = req.indexOf('?');
            if (queryPos > 0) {
                String p = req.substring(0, queryPos);
                String query = req.substring(queryPos + 1, req.length());
                try {
                    // used to encode properly the query
                    URI uri = new URI(null, null, p, query, null);
                    req = uri.toString();
                } catch (URISyntaxException e) {
                    // if encoding fail, log the error and keep the request in raw format
                    getLogger().severe(e.toString());
                }
            }
            this.request = req;
        }

        /**
         * Get the REST call (including parameters) representing this request
         *
         * @return the REST call (including parameters)
         */
        public String request() {
            return this.request;
        }

        /**
         * Get the list of HTTP headers required for this request
         *
         * @return HTTP headers required to perform this request
         */
        public Map<String, String> headers() {
            return HEADERS;
        }
    }

    /**
     * Response to GET iot/api/v2/deviceApps or GET iot/api/v2/apps/{a-id}/deviceApps
     */
    private static class DeviceAppEnumerationResponse extends PageableEnumerator.PagedResponse<DeviceApp> {

        private DeviceAppEnumerationResponse(Collection<DeviceApp> deviceApps, int offset, int total, boolean hasMore, Map<String, String> links) {
            super(deviceApps, offset, total, hasMore, links);
        }

        /**
         * Creates a {@code DeviceAppEnumerationResponse} from a JSON object
         * received in response to a {@link DeviceAppEnumerationRequest}
         *
         * @param jsonObject the JSON object to parse
         * @return a {@code DeviceEnumerationResponse} instance
         * @throws JSONException if the jsonObject has not the expected format
         * @see DeviceAppEnumerationRequest
         */
        private static DeviceAppEnumerationResponse fromJson(
                JSONObject jsonObject) throws JSONException {

            try {
                JSONArray items = jsonObject.optJSONArray("items");
                JSONArray links = jsonObject.optJSONArray("links");
                boolean hasMore = jsonObject.optBoolean("hasMore", false);
                // default: unknown
                int offset = jsonObject.optInt("offset", -1);
                // default: unknown
                int total = jsonObject.optInt("totalResults", -1);

                List<DeviceApp> list = new ArrayList<DeviceApp>();
                if (items != null) {
                    // for each item, create an endpoint from json stream
                    for (int i = 0, size = items.length(); i < size; i++) {
                        Object item = items.opt(i);
                        list.add(DeviceAppImpl.from((JSONObject) item));
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

                return new DeviceAppEnumerationResponse(list, offset, total,
                                                        hasMore, map);
            } catch (ClassCastException e) {
                throw new JSONException("Incorrect response format");
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
