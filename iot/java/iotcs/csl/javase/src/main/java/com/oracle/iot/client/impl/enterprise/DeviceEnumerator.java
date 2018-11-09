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

import oracle.iot.client.enterprise.Device;
import oracle.iot.client.enterprise.Filter;

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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@code DeviceIterator} can be used to enumerate devices matching a set of criteria.
 * When iterating, it may generates REST calls to get the next chunk of devices.
 */
public final class DeviceEnumerator extends PageableEnumerator<Device> {

    /**
     * Whether or not the results should include decommissioned devices
     */
    private final boolean includeDecommissioned;
    /**
     * Set of fields requested
     */
    private final Set<Device.Field> fields;
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
     * @param appID                 the application identifier if looking for
     *                              devices of this application, otherwise {@code null}.
     *                              If {@code null} all devices are returned.
     * @param offset                the offset of the first item to fetch
     * @param limit                 the maximum number of items requested per REST call
     * @param includeDecommissioned Whether or not the results should include decommissioned devices
     * @param fields                Set of fields requested, may be {@code null}. If
     *                              the set is {@code null} or empty, all fields are
     *                              returned.
     * @param filter                List of filters to apply to the result, may be {@code null}.
     *                              If the filter is {@code null}, all results are returned.
     * @param secureConnection      the server connection
     * @throws IOException              if an I/O error occurs when retrieving the total count of elements matching the filter
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid
     */
    public DeviceEnumerator(String appID,
                            int offset,
                            int limit,
                            boolean includeDecommissioned,
                            Set<Device.Field> fields,
                            Filter filter,
                            HttpSecureConnection secureConnection) throws IOException, GeneralSecurityException {

        super(offset, limit);
        this.appID = appID;
        this.fields = fields;
        this.filter = filter;
        this.includeDecommissioned = includeDecommissioned;
        this.secureConnection = secureConnection;
        this.setSize(this.getCount());
    }

    /**
     * Performs a new REST call to get the count of devices matching the filter
     * @return the count of devices matching the request, or
     * {@code -1} if unknown.
     */
    private int getCount() {
        // Creates a new request to get the count of devices matching the filter
        DeviceEnumerationRequest req = new DeviceEnumerationRequest(appID,
            "count", offset, limit, includeDecommissioned, fields, filter);
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
     * Performs a new REST call to refill internal cache.
     *
     * @param offset: the offset of first element to fetch
     * @param limit:  the maximum number of elements to fetch
     */
    protected PagedResponse<Device> load(int offset, int limit) throws IOException {
        // Creates a new request to get the next chunk, using same criteria
        DeviceEnumerationRequest req = new DeviceEnumerationRequest(appID, null, offset, limit, includeDecommissioned, fields, filter);
        try {
            JSONObject jsonObject = this.get(req.headers(), req.request());
            return DeviceEnumerationResponse.fromJson(jsonObject);
        } catch (JSONException e) {
            throw new IOException("GET " + req.request() + ": " + e.getMessage());
        } catch (GeneralSecurityException e) {
            throw new IOException("GET " + req.request() + ": " + e.getMessage());
        }
    }

    @Override
    protected HttpSecureConnection getSecureConnection() {
        return secureConnection;
    }

    /**
     * Creates a request for GET iot/api/v2/devices or GET iot/api/v2/apps/{a-id}/devices
     */
    public static class DeviceEnumerationRequest {

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
         * Creates a {@code DeviceEnumerationRequest} matching the specified criteria.
         *
         * @param limit                 the maximum number of result to return
         * @param offset                the offset of the results in the entire list of devices matching the specified criteria
         * @param includeDecommissioned whether or not the result should include decommissioned devices
         * @param fields                set of expected fields
         * @param filter                filter to be applied
         */
        public DeviceEnumerationRequest(int offset, int limit, boolean includeDecommissioned, Set<Device.Field> fields, Filter filter) {
            this(null, null, offset, limit, includeDecommissioned, fields, filter);
        }

        /**
         * Creates a {@code DeviceEnumerationRequest} matching the specified criteria.
         *
         * @param applicationID         the application identifier in which we should look for devices.
         * @param limit                 the maximum number of result to return
         * @param offset                the offset of the results in the entire list of devices matching the specified criteria
         * @param includeDecommissioned whether or not the result should include decommissioned devices
         * @param fields                set of expected fields
         * @param filter                filter to be applied
         */
        public DeviceEnumerationRequest(String applicationID, int offset, int limit, boolean includeDecommissioned, Set<Device.Field> fields, Filter filter) {
            this(applicationID, null, offset, limit, includeDecommissioned, fields, filter);
        }

        /**
         * Creates a {@code DeviceEnumerationRequest} matching the specified criteria.
         * {@code "/iot/api/v2/apps/{applicationId}/devices/{path}?offset={offset}&limit={limit},"}
         *
         * @param applicationID         the application identifier in which we should look for devices.
         * @param path                  the path to extend the request. If {@code null}, not added to the request.
         * @param limit                 the maximum number of result to return
         * @param offset                the offset of the results in the entire list of devices matching the specified criteria
         * @param includeDecommissioned whether or not the result should include decommissioned devices
         * @param fields                set of expected fields
         * @param filter                filter to be applied
         */
        public DeviceEnumerationRequest(String applicationID, String path, int offset, int limit, boolean includeDecommissioned, Set<Device.Field> fields, Filter filter) {

            String req;
            String sep = "?";

            if (applicationID == null) {
                req = RestApi.V2.getReqRoot();
            } else {
                req = RestApi.V2.getReqRoot()+"/apps/" + applicationID;
            }
            req += "/devices";
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
            req += sep + "includeDecommissioned=" + includeDecommissioned;
            req += "&totalResults=true";
            req += "&expand=location,metadata";

            // always ask for the type and id: required to instantiate a endpoint.
            Set<Device.Field> reqFields = (fields != null) ? new HashSet<Device.Field>(fields) : new HashSet<Device.Field>();
            reqFields.add(Device.Field.TYPE);
            reqFields.add(Device.Field.ID);

            if (fields != null) {
                req += "&fields=";
                req += joinFields(",", reqFields);
            }
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
                    getLogger().log(Level.SEVERE, e.toString());
                }
            }
            this.request = req;
        }

        private String joinFields(String delimiter, Iterable<Device.Field> list) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Device.Field f : list) {
                if (first) {
                    first = false;
                } else {
                    sb.append(delimiter);
                }
                sb.append(f.alias());
            }
            return sb.toString();
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
            return headers;
        }

        @Override
        public String toString() {
            return "DeviceEnumerationRequest: " + this.request();
        }

    }

    /**
     * Response to GET iot/api/v2/devices or GET iot/api/v2/apps/{a-id}/devices
     */
    public static class DeviceEnumerationResponse extends PagedResponse<Device> {

        private DeviceEnumerationResponse(Collection<Device> devices, int offset, int total, boolean hasMore, Map<String, String> links) {
            super(devices, offset, total, hasMore, links);
        }

        /**
         * Creates a {@code DeviceEnumerationResponse} from a JSON object received in response to a {@link DeviceEnumerationRequest}
         *
         * @param jsonObject the JSON object to parse
         * @return a {@code DeviceEnumerationResponse} instance
         * @throws JSONException if the jsonObject has not the expected format
         * @see DeviceEnumerationRequest
         */
        public static DeviceEnumerationResponse fromJson(JSONObject jsonObject)
                throws JSONException {

            try {
                JSONArray items = (JSONArray)jsonObject.opt("items");
                JSONArray links = (JSONArray)jsonObject.opt("links");
                boolean hasMore = jsonObject.optBoolean("hasMore", false);
                // default: unknown
                int offset = jsonObject.optInt("offset", -1);
                // default: unknown
                int total = jsonObject.optInt("totalResults", -1);

                List<Device> list = new ArrayList<Device>();
                if (items != null) {
                    // for each item, create an endpoint from json stream
                    for (int i = 0, size = items.length(); i < size; i++) {
                        Object item = items.opt(i);
                        list.add(DeviceImpl.from((JSONObject) item));
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

                return new DeviceEnumerationResponse(list, offset, total,
                                                     hasMore, map);
            } catch (ClassCastException e) {
                throw new JSONException("Incorrect response format");
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
