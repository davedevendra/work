/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl;

import org.json.JSONException;
import org.json.JSONObject;

/**
* AccessToken
*/
public class AccessToken {
    private final long expires;
    private final String tokenType;
    private final String token;
    private final long expirationTime;

    public AccessToken(long expires, String tokenType, String token) {
        this.expires = expires;
        this.tokenType = tokenType;
        this.token = token;
        this.expirationTime = TimeManager.currentTimeMillis() + expires;
    }

    public final boolean hasExpired() {
        return (TimeManager.currentTimeMillis() >= this.expirationTime);
    }

    public long getExpires() {
        return expires;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String getToken() {
        return token;
    }

    @Override
    public String toString() {
        return "Accessor.Token{" +
                "expires=" + expires +
                ", tokenType='" + tokenType + '\'' +
                ", token='" + token + '\'' +
                '}';
    }

    public static AccessToken fromJson(JSONObject jsonObject) {
        try {
            return new AccessToken(
                jsonObject.getInt("expires_in"),
                jsonObject.getString("token_type"),
                jsonObject.getString("access_token"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
