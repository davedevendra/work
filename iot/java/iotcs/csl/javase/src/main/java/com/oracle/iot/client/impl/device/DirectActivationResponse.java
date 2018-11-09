/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import com.oracle.iot.client.impl.util.Base64;

import org.json.JSONObject;
import org.json.JSONException;

import java.util.Arrays;
import java.util.Date;

/**
 * DirectActivationResponse
 */
public class DirectActivationResponse {

    private static final String FIELD_ENDPOINT_ID       = "endpointId";
    private static final String FIELD_ACTIVATION_TIME   = "activationTime";
    private static final String FIELD_ENDPOINT_STATE    = "endpointState";
    private static final String FIELD_CERTIFICATE       = "certificate";

    private String endpointId;
    private Date activationTime;
    private String endpointState;

    private byte[] certificate;

    public byte[] getCertificate() {
        return certificate;
    }

    public void setCertificate(byte[] certificate) {
        this.certificate = certificate;
    }

    public DirectActivationResponse() {}

    public Date getActivationTime() {
        return activationTime;
    }

    public void setActivationTime(Date activationTime) {
        this.activationTime = activationTime;
    }

    public String getEndpointState() {
        return endpointState;
    }

    public void setEndpointState(String endpointState) {
        this.endpointState = endpointState;
    }

    public String getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(String endpointId) {
        this.endpointId = endpointId;
    }

    @Override
    public String toString() {
        return "ActivationResponse{" +
                "endpointId='" + endpointId + '\'' +
                ", activationTime=" + activationTime +
                ", endpointState='" + endpointState + '\'' +
                ", certificate=" + Arrays.toString(certificate) +
                '}';
    }

    public static DirectActivationResponse fromJson(JSONObject jsonObject) {
        DirectActivationResponse response = new DirectActivationResponse();

        try {
            response.endpointId = jsonObject.getString(FIELD_ENDPOINT_ID);

            // TODO: implement a Date converter
            //activationTime =
            // getJsonDate(jsonObject.get(FIELD_ACTIVATION_TIME));
            response.endpointState = jsonObject.getString(FIELD_ENDPOINT_STATE);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        // TODO: we're manually replacing EOLs here, need to figure out a
        // better solution
        String fieldCertificate = jsonObject.optString(FIELD_CERTIFICATE, null);
        if (fieldCertificate != null) {
            String certificateString =
                fieldCertificate.toString().replace("\r", "").replace("\n", "");
            response.certificate =
                Base64.getDecoder().decode(certificateString);
        } else {
            response.certificate = new byte[0];
        }

        return response;
    }
    
    public String toJson() {
        JSONObject objectBuilder = new JSONObject();

        try {
            objectBuilder.put(FIELD_ENDPOINT_ID, endpointId);
            objectBuilder.put(FIELD_ENDPOINT_STATE, endpointState);
            objectBuilder.put(FIELD_ACTIVATION_TIME, activationTime.toString());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return objectBuilder.toString();
    }
}
