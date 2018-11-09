/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.oracle.iot.client.impl.util.Base64;

/**
 * IndirectActivationRequest
 */
public class IndirectActivationRequest {

    /**
     * The hardware id of the device
     */
    private static final String HARDWARE_ID = "hardwareId";

    private static final String FIELD_DEVICE_MODELS = "deviceModels";

    private String hardwareId;
    private Map<String, String> metadata;
    private Set<String> deviceModels;
    private byte[] signature;
    private PublicKey publicKey;

    protected IndirectActivationRequest() {
    }

    public IndirectActivationRequest(String hardwareId, Map<String, String> metadata, Set<String> deviceModels, byte[] signature) {
        this.hardwareId = hardwareId;
        this.metadata = metadata;
        this.deviceModels = deviceModels;
        this.signature = signature;
    }

    public String getHardwareId() {
        return this.hardwareId;
    }

    public void setHardwareId(String hardwareId) {
        this.hardwareId = hardwareId;
    }

    public Set<String> getDeviceModels() {
        return this.deviceModels;
    }

    public void setDeviceModels(Set<String> deviceModels) {
        this.deviceModels = deviceModels;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public byte[] getSignature() { return  signature; }

    public PublicKey getPublicKey() { return publicKey; }

    public static IndirectActivationRequest fromJson(String jsonString) {
        IndirectActivationRequest request = new IndirectActivationRequest();
        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            request.fromJson(jsonObject);
        } catch (JSONException ex) {
            // TODO
        }
        return request;
    }

    public void fromJson(JSONObject jsonObject) {
        Iterator<String> iterator = jsonObject.keys();
        metadata = new HashMap<String,String>();
        while (iterator.hasNext()) {
            String key = iterator.next();
            Object value = jsonObject.opt(key);
            if (key.equals(HARDWARE_ID)) {
                this.hardwareId = value.toString();
            }

            metadata.put(key, value.toString());
        }

        JSONArray deviceModelsArray =
            jsonObject.optJSONArray(FIELD_DEVICE_MODELS);
        if (deviceModelsArray != null) {
            Set<String> deviceModels = new HashSet<String>();
            int size = deviceModelsArray.length();
            for (int i = 0; i < size; i++) {
                deviceModels.add(deviceModelsArray.opt(i).toString());
            }

            this.setDeviceModels(deviceModels);
        }

        final String sig = jsonObject.optString("signature", null);
        if (sig != null) {
            this.signature = Base64.getDecoder().decode(sig);
        } else {
            this.signature =  null;
        }
    }
    
    public String toJson() {
        JSONObject jbuilder = new JSONObject();
        JSONArray deviceModels = new JSONArray();

        try {
            if (hardwareId == null) {
                throw new NullPointerException("Hardware ID is null");
            }

            if (signature != null) {
                jbuilder.put("signature", Base64.getEncoder().encodeToString(signature));
            }

            jbuilder.put(HARDWARE_ID, hardwareId);
            if (metadata != null) {
                Set<Map.Entry<String, String>> entries = metadata.entrySet();
                Iterator<Map.Entry<String, String>> iterator =
                    entries.iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, String> entry = iterator.next();
                    jbuilder.put(entry.getKey(), entry.getValue());
                }
            }

            if (this.deviceModels != null) {
                for (String deviceModel: this.deviceModels) {
                    deviceModels.put(deviceModel);
                }

                jbuilder.put(FIELD_DEVICE_MODELS, deviceModels);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return jbuilder.toString();
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer("IndirectActivationRequest");
        sb.append("{");
        sb.append("hardwareId=").append(this.hardwareId);
        sb.append("deviceModels=").append(Arrays.toString(deviceModels.toArray()));
        if (metadata != null) {
            Set<Map.Entry<String, String>> entries = metadata.entrySet();
            Iterator<Map.Entry<String, String>> iterator = entries.iterator();
            while (iterator.hasNext()) {
                Map.Entry<String,String> entry = iterator.next();
                sb.append(entry.getKey()).append(entry.getValue());
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
