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
import com.oracle.iot.client.impl.http.HttpSecureConnection;
import com.oracle.iot.client.message.Message;
import com.oracle.iot.client.message.StatusCode;

import oracle.iot.client.enterprise.VirtualDevice;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
class Controller {

    interface Callback {
        void onError(VirtualDevice virtualDevice, String resource, String payload, String reason);
    }

    static void update(VirtualDeviceImpl virtualDevice, String method,
            String resource, String payload, Callback callback) {

        HttpSecureConnection connmgr;
        HttpResponse response;
        try {
            connmgr = virtualDevice.getSecureConnection();
            String endptid = connmgr.getEndpointId();

            if (getLogger().isLoggable(Level.FINEST)){

                StringBuilder builder = new StringBuilder();
                builder.append(method)
                        .append(' ')
                        .append(resource);
                if (payload != null) {
                    builder.append(' ').append(Message.prettyPrintJson(payload));
                }

                getLogger().log(Level.FINEST, builder.toString());
            }

            byte[] payloadBytes = payload != null ? payload.getBytes("UTF-8") :
                null;

            // Assuming PUT is supported, READ_WRITE or WRITE_ONLY should
            // have been set for PUT or POST, and isWritable had been called
            // before we get here.
            if (method.equals("GET")) {
                response = connmgr.get(resource);
            } else if (method.equals("POST")) {
                response = connmgr.post(resource, payloadBytes);
            } else if (method.equals("PUT")) {
                response = connmgr.put(resource, payloadBytes);
            } else { // Must be PATCH
                response = connmgr.patch(resource, payloadBytes);
            }
        } catch (Exception e) {
            getLogger().log(Level.FINEST, "ConnectionManager " +
                method + " failed. " + e.getMessage());
            if (callback != null) {
                callback.onError(virtualDevice, resource, payload, e.getMessage());
            }

            return;
        }

        // Since the callback is the consumer of the response,
        // only look for the response if callback is not null
        if (callback != null) {

            try {

                StatusCode status = handleResponse(connmgr, response);

                // Accepted and Ok responses are dropped on the floor.
                // We expect a DataMessage with new values to come through
                // the Monitor. Here, we just want to report on errors.
                if (status != StatusCode.ACCEPTED && status != StatusCode.OK) {
                    callback.onError(virtualDevice, resource, payload, status.getDescription());
                }

            } catch (IOException e) {
                callback.onError(virtualDevice, resource, payload, e.getMessage());
            } catch (GeneralSecurityException e) {
                callback.onError(virtualDevice, resource, payload, e.getMessage());
            }
        }
    }

    private static final String CONTROLLER_POLLING_INTERVAL =
        "oracle.iot.client.enterprise.controller_polling_interval";

    // Delay between polls for requests/{id}/response to avoid busy loop
    private static final long pollingInterval = Long.getLong(
            CONTROLLER_POLLING_INTERVAL, 1000);

    // How many times to iterate calling the server for a COMPLETE status
    private static final String CONTROLLER_RETRY_COUNT =
        "oracle.iot.client.enterprise.controller_retry_count";

    // Default with 20 iterations
    private static int controllerRetryCount =
            Integer.getInteger(CONTROLLER_RETRY_COUNT, 20);

    // This should be in some util class for general use when handling
    // asynchronous requests.
    /**
     * Handle the asynchronous request responses.
     * Continually poll the server for the COMPLETE response that 
     * indicates that the request and made to the target client and
     * the client has responded.
     * @param response the initial server response.
     * @return the Http status code from the completed the client request
     * or the initial server response if it doesn't succeed.
     */
    private static StatusCode handleResponse(
            HttpSecureConnection secureConnection,
            HttpResponse response)
            throws IOException, GeneralSecurityException {
        
        // Async requests return ACCEPTED when the request reaches
        // the server. The response "state" is RECEIVED.
        // When the request reaches the device the response status
        // code is OK and the "state" is COMPLETED.
        // Intermediate responses contain the "callback" request url
        // in "links" : [ ...,{ "rel" : "requests" : <url> }, ...]
        // elements.
        StatusCode httpstatus = StatusCode.valueOf(response.getStatus());

        if (controllerRetryCount <= 0) {
            controllerRetryCount = 20;
        }

        for (int retryCount = 0; retryCount < controllerRetryCount;
                retryCount++) {
            // If the request fails return that status code.
            // For the initial request response,
            // async requests should be 202, but if it completes
            // on the first time or is synchronous it will be 200.
            if (httpstatus != StatusCode.ACCEPTED &&
                    httpstatus != StatusCode.OK) {
                return httpstatus;
            }

            JSONObject jsonData = null;
            String reqStatus;

            try {
                byte[] data = response.getData();
                String json = new String(data, "UTF-8");
                jsonData = new JSONObject(json);
                reqStatus = jsonData.get("status").toString();
            } catch (JSONException e) {
                // server sent us some bad data
                getLogger().log(Level.SEVERE,
                    e.getMessage(), e);
                return StatusCode.INTERNAL_SERVER_ERROR;
            }

            if ("COMPLETED".equals(reqStatus) ||
                    "FAILED".equals(reqStatus)) {
                // If the status cannot be obtained,
                // getResponseStatusCode, logs the error
                // and returns an appropriate status code.
                return getResponseStatusCode(jsonData);
            }

            String respReqHref = getLinksResponse(jsonData);

            if (respReqHref == null) {
                // received json data does not conform to spec?
                getLogger().log(Level.SEVERE, "Could not get request href");
                return StatusCode.INTERNAL_SERVER_ERROR;
            }

            URI uri;
            try {
                uri = new URI(respReqHref);
            } catch(URISyntaxException e) {
                getLogger().log(Level.SEVERE,
                        "Cannot decode links:requests:href "+ respReqHref);
                return StatusCode.INTERNAL_SERVER_ERROR;
            }

            // Give the last request some time
            try {
                Thread.sleep(pollingInterval);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            response = secureConnection.get(uri.getPath());
            httpstatus = StatusCode.valueOf(response.getStatus());
        }

        // If the loop exits because retryCount == 0, then the
        // device was not reachable in the allotted number of retries,
        // otherwise, the code would have returned from within the loop

        // TODO: Should throw exception, and have it bubble up
        // to the application.
        if (getLogger().isLoggable(Level.FINEST)) {
            getLogger().log(Level.FINEST, CONTROLLER_RETRY_COUNT +
                " " + controllerRetryCount + " reached, returning " +
                StatusCode.REQUEST_TIMEOUT.toString());
        }
        return StatusCode.REQUEST_TIMEOUT;
    }

    /**
     * Given the asynchronous response state, return the appropriate
     * "links" href value.
     * @param jsonResponse the cloud service response
     * @return the uri to obtain the response for the given asynchronous 
     * response state.
     */
    private static String getLinksResponse(JSONObject jsonResponse) {
        String id = jsonResponse.optString("id", null);
        return id != null ?
            RestApi.V2.getReqRoot()+"/requests/".concat(id) : null;
    }

    /**
     * Return the async request response status from the embedded
     * json object, "response".
     * @param jsonResponse the requests api response.
     * @return the async request's status or StatusCode.INTERNAL_SERVER_ERROR
     *         if the actual response cannot be obtained.
     */
    private static StatusCode getResponseStatusCode(JSONObject jsonResponse) {
        StatusCode statusCode = StatusCode.INTERNAL_SERVER_ERROR;
        try {
            JSONObject asyncResponse = jsonResponse.optJSONObject("response");
            statusCode = StatusCode.valueOf(asyncResponse.getInt("statusCode"));
        } catch (IllegalArgumentException e) {
            // server sent us a bad value
            getLogger().log(Level.SEVERE, e.getMessage(), e);
        } catch (NullPointerException e) {
            // Unexpected json format
            getLogger().log(Level.SEVERE, e.getMessage(), e);
        } catch (JSONException e) {
            // Unexpected json format
            getLogger().log(Level.SEVERE, e.getMessage(), e);
        }
        return statusCode;
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
