/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import org.json.JSONException;
import org.json.JSONObject;

/**
 */
public final class ActivationPolicyResponse {

    private final String keyType;
    private final int keySize;
    private final String hashAlgorithm;

    public ActivationPolicyResponse(String keyType, int keySize, String hashAlgorithm) {
        this.keyType = keyType;
        this.keySize = keySize;
        this.hashAlgorithm = hashAlgorithm;
    }

    public String getKeyType() {
        return keyType;
    }

    public int getKeySize() {
        return keySize;
    }

    public String getHashAlgorithm() {
        return hashAlgorithm;
    }

    @Override
    public String toString() {
        return "ActivationPolicyResponse{" +
                "keyType='" + keyType + '\'' +
                ", keySize=" + keySize +
                ", hashAlgorithm='" + hashAlgorithm + '\'' +
                '}';
    }

    private static final String FIELD_KEY_TYPE = "keyType";
    private static final String FIELD_HASH_ALGORITHM = "hashAlgorithm";
    private static final String FIELD_KEY_SIZE = "keySize";

    public static ActivationPolicyResponse fromJson(JSONObject jsonObject) {
        if (jsonObject == null) {
            return null;
        }

        try {
            String keyType = jsonObject.getString(FIELD_KEY_TYPE);
            int keySize = jsonObject.getInt(FIELD_KEY_SIZE);
            String hashAlgorithm = jsonObject.getString(FIELD_HASH_ALGORITHM);

            return new ActivationPolicyResponse(keyType, keySize,
                                                hashAlgorithm);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public JSONObject toJson() {
        JSONObject objectBuilder = new JSONObject();

        try {
            objectBuilder.put(FIELD_KEY_TYPE, keyType);
            objectBuilder.put(FIELD_KEY_SIZE, keySize);
            objectBuilder.put(FIELD_HASH_ALGORITHM, hashAlgorithm);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return objectBuilder;
    }
}
