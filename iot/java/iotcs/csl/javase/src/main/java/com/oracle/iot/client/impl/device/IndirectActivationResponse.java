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

import java.util.Date;

/**
 * IndirectActivationResponse
 */
public class IndirectActivationResponse {

    private static final String FIELD_ENDPOINT_ID       = "endpointId";
    private static final String FIELD_ACTIVATION_TIME   = "activationTime";
    private static final String FIELD_ENDPOINT_STATE    = "endpointState";

    private String endpointId;
    private Date activationTime;
    private String endpointState;

    public IndirectActivationResponse() {}

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
        return "IndirectActivationResponse{" +
                "endpointId='" + endpointId + '\'' +
                ", activationTime=" + activationTime +
                ", endpointState='" + endpointState + '\'' +
                '}';
    }

    public static IndirectActivationResponse fromJson(final JSONObject 
            jsonObject) {
        IndirectActivationResponse response = new IndirectActivationResponse();

        try {
            response.endpointId = jsonObject.getString(FIELD_ENDPOINT_ID);

            // TODO: implement a Date converter
            //response.activationTime = 
            // getJsonDate(jsonObject.get(FIELD_ACTIVATION_TIME));
            response.endpointState = jsonObject.getString(FIELD_ENDPOINT_STATE);
        } catch (JSONException e) {
            throw new RuntimeException(e);
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
