/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.enterprise;

import com.oracle.iot.client.RestApi;

import com.oracle.iot.client.impl.http.HttpSecureConnection;

import com.oracle.iot.client.impl.enterprise.ResourceImpl;
import com.oracle.iot.client.impl.enterprise.PageableEnumerator;
import com.oracle.iot.client.impl.enterprise.SecureHttpConnectionMap;

import oracle.iot.client.enterprise.EnterpriseClient;
import oracle.iot.client.enterprise.Pageable;
import oracle.iot.client.enterprise.UserAuthenticationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import java.security.GeneralSecurityException;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides means to lookup for resources of a device.
 */
public class ResourceEnumerator {

    /**
     * The device to list resources
     */
    private final String deviceId;
    /**
     * The secure connection to use
     */
    private final HttpSecureConnection secureConnection;
    /**
     * The application identifier: namespace to use to look for resources
     */
    private final String appId;

    /**
     * Creates a resource enumerator to retrieve resources of a device.
     *
     * @param client the client to use to retrieve messages
     * @param deviceId the device identifier
     *
     * @throws NullPointerException if either the client or device ID
     *                              is {@code null}
     */
    public ResourceEnumerator(EnterpriseClient client, String deviceId) {
        if (client == null) {
            throw new NullPointerException("client parameter");
        }

        if (deviceId == null) {
            throw new NullPointerException("deviceId parameter");
        }

        this.deviceId = deviceId;
        this.secureConnection =
            SecureHttpConnectionMap.getSecureHttpConnection(client);

        this.appId = client.getApplication().getId();
    }

    /**
     * Get the resources for the specified {@code deviceId}.
     *
     * @return a list of resources for the device or null if no resources
     *
     * @throws IOException if an I/O error occurred while trying to
     *                     retrieve resources from server
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid.  If User Authentication is used,
     *                                  then {@link UserAuthenticationException} will be thrown
     *                                  if the session cookie has expired.
     */
    public Pageable<Resource> getResources()
            throws IOException, GeneralSecurityException {
        return new ResourcePageable(appId, deviceId, secureConnection);
    }

    static class ResourcePageable extends PageableEnumerator<Resource> {
        private final String appId;
        private final String deviceId;
        private final HttpSecureConnection secureConnection;

        private PagedResponse<Resource> firstPage;

        private ResourcePageable(String appId, String deviceId,
                HttpSecureConnection secureConnection)
                throws IOException, GeneralSecurityException {
            super(0, 200);
            this.appId = appId;
            this.deviceId = deviceId;
            this.secureConnection = secureConnection;

            // Since we don't know the total results, pre-fetch the first page
            this.firstPage = load(this.offset, this.limit);
            this.hasMore = !firstPage.elements().isEmpty();
        }

        protected HttpSecureConnection getSecureConnection() {
            return secureConnection;
        }

        protected PagedResponse<Resource> load(int offset, int limit)
                throws IOException {
            if (this.firstPage != null) {
                PagedResponse<Resource> page = this.firstPage;
                this.firstPage = null;
                if (offset == 0) {
                    return page;
                }
            }

            HashMap<String, String> headers = new HashMap<String, String>(2);
            headers.put("Accept", "application/json");

            String request = RestApi.V2.getReqRoot() + "/apps/" + appId +
                "/devices/" + deviceId +
                "/resources?offset=" + offset + "&limit=" + limit;

            this.hasMore = false;

            try {
                JSONObject response = get(headers, request);
                return ResourceResponse.fromJson(response, appId, secureConnection);
            } catch (GeneralSecurityException e) {
                throw new IOException("GET " + request + ": " + e.getMessage());
            } catch (JSONException e) {
                throw new IOException("GET " + request + ": " + e.getMessage());
            }
        }
    }

    /*private*/ static class ResourceResponse extends
            PageableEnumerator.PagedResponse<Resource> {

        ResourceResponse(List<Resource> resources, int offset, int total, boolean hasMore,
            final Map<String, String> links) {
            super(resources, offset, total, hasMore, links);
        }

        static ResourceResponse fromJson(JSONObject jsonObject, String appId,
                HttpSecureConnection secureConnection) throws JSONException {
            JSONArray items = jsonObject.optJSONArray("items");
            JSONArray links = jsonObject.optJSONArray("links");
            boolean hasMore = jsonObject.optBoolean("hasMore", false);
            int offset = jsonObject.optInt("offset", -1); // default: unknown
            int total = jsonObject.optInt("totalResults", -1);  // default: unknown

            List<Resource> resources = new ArrayList<Resource>();
            for (int i = 0, size = items.length(); i < size; i++) {
                Object r = items.opt(i);
                resources.add(createResource((JSONObject)r, appId,
                    secureConnection));
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

            return new ResourceResponse(resources, offset, total, hasMore, map);
        }

        private static Resource createResource(JSONObject resource,
                String appId, HttpSecureConnection secureConnection)
                throws JSONException {
            String endpointId = resource.getString("endpointId");
            String id = resource.getString("id");
            String description = resource.optString("description", "");
            String url = resource.getString("url");

            List<String> mList = new ArrayList<String>();
            JSONArray methods = resource.getJSONArray("methods");
            for (int i = 0, size = methods.length(); i < size ;i++) {
                String method = methods.getString(i);
                mList.add(method);
            }

            return new ResourceImpl(secureConnection, appId, endpointId, id,
                                    description, url, mList);
        }
    }
}
