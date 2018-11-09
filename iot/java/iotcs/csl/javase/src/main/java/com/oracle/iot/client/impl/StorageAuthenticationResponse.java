/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * StorageAuthenticationResponse
 */
class StorageAuthenticationResponse {

    private static final String FIELD_STORAGE_CONTAINER_URL  = "storageContainerUrl";
    private static final String FIELD_AUTH_TOKEN    = "authToken";

    private String storageContainerUrl;
    private String authToken;

    public StorageAuthenticationResponse() {}

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getStorageContainerUrl() {
        return storageContainerUrl;
    }

    public void setServicePath(String servicePath) {
        this.storageContainerUrl = servicePath;
    }

    @Override
    public String toString() {
        return "StorageAuthenticationResponse{" +
                "storageContainerUrl='" + storageContainerUrl + '\'' +
                ", authToken=" + authToken +
                '}';
    }

    public static StorageAuthenticationResponse fromJson(final JSONObject 
            jsonObject) {
        StorageAuthenticationResponse response = new StorageAuthenticationResponse();

        try {
            response.storageContainerUrl = jsonObject.getString(FIELD_STORAGE_CONTAINER_URL);
            response.authToken = jsonObject.getString(FIELD_AUTH_TOKEN);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return response;
    }
    
    public String toJson() {
        JSONObject objectBuilder = new JSONObject();

        try {
            objectBuilder.put(FIELD_STORAGE_CONTAINER_URL, storageContainerUrl);
            objectBuilder.put(FIELD_AUTH_TOKEN, authToken);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return objectBuilder.toString();
    }
}
