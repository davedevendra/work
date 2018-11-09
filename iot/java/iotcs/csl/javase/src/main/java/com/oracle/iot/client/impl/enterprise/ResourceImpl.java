/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.impl.enterprise;

import com.oracle.iot.client.RestApi;
import com.oracle.iot.client.HttpResponse;
import com.oracle.iot.client.enterprise.Resource;
import com.oracle.iot.client.enterprise.Response;
import com.oracle.iot.client.impl.http.HttpSecureConnection;

import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * The Resource class represents a device resource.
 */
public class ResourceImpl implements Resource {
    private final HttpSecureConnection secureConnection;
    private final String appId;
    private final String deviceId;
    private final String id;
    private final String description;
    private final String url;
    private final List<String> methods;

    public ResourceImpl(HttpSecureConnection secureConnection, String appId, String deviceId, String id, String description,
            String url, List<String> methods) {
        this.secureConnection = secureConnection;
        this.appId = appId;
        this.deviceId = deviceId;
        this.id = id;
        this.description = description;
        this.url = url;
        this.methods = methods;
    }

    @Override
    public String getDeviceId() {
        return deviceId;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getURL() {
        return url;
    }

    @Override
    public List<String> getMethods() {
        return methods;
    }

    @Override
    public Response get(Map<String, String> queryparam,
            Map<String, String> headers) throws Exception {
        if (!methods.contains("GET")) {
            throw new UnsupportedOperationException("GET not supported");
        }

        String path = combineApiAndQuery(queryparam);

        HttpResponse res = secureConnection.get(path);

        return  ResponseImpl.from(path, headers, res, secureConnection);
    }

    @Override
    public Response put(Map<String, String> queryparam,
            Map<String, String> headers, byte[] body) throws Exception {
        if (!methods.contains("PUT")) {
            throw new UnsupportedOperationException("PUT not supported");
        }

        String path = combineApiAndQuery(queryparam);

        HttpResponse res = secureConnection.put(path, body);

        return ResponseImpl.from(path, headers, res, secureConnection);
    }

    @Override
    public Response post(Map<String, String> queryparam,
            Map<String, String> headers, byte[] body) throws Exception {
        if (!methods.contains("POST")) {
            throw new UnsupportedOperationException("POST not supported");
        }

        String path = combineApiAndQuery(queryparam);

        HttpResponse res = secureConnection.post(path, body);

        return ResponseImpl.from(path, headers, res, secureConnection);
    }

    @Override
    public Response delete(Map<String, String> headers) throws Exception {
        if (!methods.contains("DELETE")) {
            throw new UnsupportedOperationException("DELETE not supported");
        }

        String path = combineApiAndQuery(null);

        HttpResponse res = secureConnection.delete(path);

        return ResponseImpl.from(path, headers, res, secureConnection);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("device ID = ");
        b.append(deviceId);
        b.append(", ID = ");
        b.append(id);
        b.append(", description = ");
        b.append(description);
        b.append(", URL = ");
        b.append(url);
        b.append(", methods = [");
        boolean first = true;
        for (String m: methods) {
            if (!first) {
                b.append(",");
            }
            
            b.append(m);
            first = false;
        }
        b.append("]");
        return b.toString();
    }

    private String combineApiAndQuery(Map<String, String> queryParams) {
        String query = "";

        if (queryParams != null) {
            Set<Map.Entry<String,String>> params = queryParams.entrySet();

            boolean first = true;
            for (Map.Entry<String,String> p: params) {
                if (first) {
                    query = "?";
                    first = false;
                } else {
                    query += "&";
                }

                query += p.getKey() + "=" + p.getValue();
            }
        }

        return RestApi.V2.getReqRoot()+"/apps/" + appId + "/devices/" + deviceId + "/resources/" + url + query;
    }

}
