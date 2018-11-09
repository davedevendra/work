/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 */

package com.oracle.iot.client.impl;

import com.oracle.iot.client.device.util.MessageDispatcher;
import com.oracle.iot.client.device.util.RequestHandler;
import com.oracle.iot.client.message.DataMessage;
import com.oracle.iot.client.message.RequestMessage;
import com.oracle.iot.client.message.ResponseMessage;
import com.oracle.iot.client.message.StatusCode;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.StringReader;
import java.io.UnsupportedEncodingException;

import java.util.Arrays;
import java.util.Locale;

/**
 * This class contains inner classes for handling diagnostics test connectivity messages.It is assumed there will only
 * be one instance of this class constructed.
 */
public class TestConnectivity {
    // The number of Messages to send.
    private  long count;
    // The number of Messages already sent.
    private  long currentCount;
    // The interval between sending each Message.
    private  long interval;
    // The size of the payload of the Message.
    private  long size;

    /** The ID of the current or last test connectivity invocation so we can get the status and cancel. */
    private final MessageDispatcher messageDispatcher;
    private final String endpointId;
    /** The status of the current or last test connectivity invocation.  We only keep one */
    private TestConnectivityThread testConnectivityThread = null;
    private TestConnectivityHandler testConnectivityHandler;
    private final Object testConnectivityThreadLock = new Object();

    /**
     * Constructs a TestConnectivity
     *
     * @param endpointId the endpoint ID of the Gateway.
     * @param messageDispatcher  a reference to a MessageDispatcher.
     */
    public TestConnectivity(String endpointId, MessageDispatcher messageDispatcher) {
        this.endpointId = endpointId;
        this.messageDispatcher = messageDispatcher;
    }

    /**
     * Appends spaces (error separators) to the errors list if it's not empty.
     *
     * @param errors a list of errors in a StringBuilder.
     */
    private void appendSpacesToErrors(StringBuilder errors) {
        if (errors.toString().length() != 0) {
            errors.append("  ");
        }
    }

    /**
     * Returns an appropriate response if the connectivity test is currently running.
     *
     * @param requestMessage the request for this capability.
     * @return an appropriate response if the connectivity test is currently running.
     */
    private ResponseMessage getAlreadyRunningResponse(RequestMessage requestMessage) {
        return getResponseMessage(requestMessage, "Test connectivity is already running.", StatusCode.CONFLICT);
    }

    /**
     * Returns an appropriate response if the request is bad.
     *
     * @param requestMessage the request for this capability.
     * @return an appropriate response if the request is bad.
     */
    private ResponseMessage getBadRequestResponse(RequestMessage requestMessage, String message) {
        return getResponseMessage(requestMessage, message, StatusCode.BAD_REQUEST);
    }

    /**
     * Returns an {@link RequestMessage} with the supplied parameters.
     *
     * @param requestMessage the request for this capability.
     * @param body           the body for the response.
     * @param  statusCode    the status code of the response.
     * @return an appropriate response if the request is {@code null}.
     */
    private ResponseMessage getResponseMessage(RequestMessage requestMessage, String body, StatusCode statusCode) {
        return new ResponseMessage.Builder(requestMessage)
            .body(body)
            .statusCode(statusCode)
            .build();
    }

    /**
     * Returns an appropriate response if a method call is not supported by this handler.
     *
     * @param requestMessage the request for this capability.
     * @return an appropriate response if a method call is not supported by this handler.
     */
    private ResponseMessage getMethodNotAllowedResponse(RequestMessage requestMessage) {
        return getResponseMessage(requestMessage, "Unsupported request: " + requestMessage.toString(),
            StatusCode.METHOD_NOT_ALLOWED);
    }

    /**
     * Returns an appropriate response if the request is {@code null}.
     *
     * @param requestMessage the request for this capability.
     * @return an appropriate response if the request is {@code null}.
     */
    private ResponseMessage getNullRequestResponse(RequestMessage requestMessage) {
        return new ResponseMessage.Builder(requestMessage)
            .body("Unsupported request.")
            .statusCode(StatusCode.METHOD_NOT_ALLOWED)
            .build();
    }

    /**
     * Returns the Long parameter specified by 'paramName' in the request if
     * it's available.
     *
     * @param jsonObject a {@link JSONObject} containing the JSON request.
     * @param paramName   the name of the parameter to get.
     * @param errors  a {@link StringBuilder} of errors.  Any errors produced
     *                from retrieving the parameter will be appended to this.
     *
     * @return the parameter if it can be retrieved, otherwise {@code null}.
     */
    private Long getParam(JSONObject jsonObject, String paramName,
            StringBuilder errors) {
        Long value = null;

        try {
            Number jsonNumber = (Number)jsonObject.opt(paramName);

            if (jsonNumber != null) {
                try {
                    value = jsonNumber.longValue();

                    if (value < 1) {
                        errors.append(paramName).append(
                            " must be a numeric value greater then 0.");
                    }
                } catch (NumberFormatException nfe) {
                    errors.append(paramName).append(
                        " must be a numeric value greater then 0.");
                }
            } else {
                appendSpacesToErrors(errors);
                errors.append("The ").append(paramName).append(
                    " value must be supplied.");
            }
        } catch(ClassCastException cce) {
            appendSpacesToErrors(errors);
            errors.append("The ").append(paramName).append(
                " value must be a number.");
        }

        return value;
    }

    public RequestHandler getTestConnectivityHandler() {
        if (testConnectivityHandler == null) {
            testConnectivityHandler = new TestConnectivityHandler();
        }

        return testConnectivityHandler;
    }

    /**
     * Reset the test connectivity run parameters.
     */
    private void resetParameters() {
        count = 0L;
        currentCount = 0L;
        interval = 0L;
        size = 0L;
    }

    /**
     * Inner class which handles HTTP requests for test connectivity.
     */
    private class TestConnectivityHandler implements RequestHandler {
        @Override
        public ResponseMessage handleRequest(RequestMessage request)
                throws Exception {
            if ((request != null) && (request.getMethod() != null)) {
                String method = request.getMethod().toUpperCase(Locale.ROOT);

                if (method.equals("GET")) {
                    return getTestConnectivity(request);
                } else if (method.equals("PUT")) {
                    return putTestConnectivity(request);
                }

                return getMethodNotAllowedResponse(request);
            }

            return getNullRequestResponse(request);
        }

        private ResponseMessage getTestConnectivity(RequestMessage request)
                throws Exception {
            JSONObject job = new JSONObject();

            synchronized (testConnectivityThreadLock) {
                if ((testConnectivityThread != null) &&
                        (testConnectivityThread.isAlive())) {
                    job.put("active", true);
                } else {
                    job.put("active", false);
                }
            }

            job.put("count", count);
            job.put("currentCount", currentCount);
            job.put("interval", interval);
            job.put("size", size);

            return getResponseMessage(request, job.toString(),
                StatusCode.OK);
        }

        private ResponseMessage putTestConnectivity(RequestMessage request)
                throws Exception {
            Boolean active = null;
            StringBuilder errors = new StringBuilder();
            String jsonRequestBody =
                new String(request.getBody(), "UTF-8");

            try {
                JSONObject jsonObject = new JSONObject(jsonRequestBody);

                active = jsonObject.getBoolean("active");

                if (active == null) {
                    // Invalid request, active is a required
                    // field.
                    return getBadRequestResponse(request,
                        "Required parameter 'active' is missing.");
                }

                if (!active) {
                    // Stop the test connectivity run.
                    synchronized (testConnectivityThreadLock) {
                        if ((testConnectivityThread != null) &&
                                (testConnectivityThread.isAlive())) {
                            testConnectivityThread.interrupt();
                        }
                    }

                    // Always return success
                    return getResponseMessage(request, "", StatusCode.OK);
                }

                // active is TRUE, attempt to start a test
                // connectivity run.
                synchronized (testConnectivityThreadLock) {
                    if ((testConnectivityThread != null) &&
                            (testConnectivityThread.isAlive())) {
                        // A test connectivity run is already
                        // running.
                        return getAlreadyRunningResponse(request);
                    }
                }

                // Start the test connectivity run.
                count = getParam(jsonObject, "count", errors);
                interval = getParam(jsonObject, "interval", errors);
                size = getParam(jsonObject, "size", errors);

                if (count < 1) {
                    errors.append("count must be greater then 0.");
                }

                if (interval < 0) {
                    errors.append("interval must be greater then or equal " +
                        "to 0.");
                }

                if (size > 4096) {
                    errors.append("size is greater than 4096.");
                }

                if (!errors.toString().isEmpty()) {
                    resetParameters();
                    return getBadRequestResponse(request, errors.toString());
                }

                testConnectivityThread = new TestConnectivityThread();
                testConnectivityThread.start();
            } catch (Exception e) {
                if (e instanceof ClassCastException || 
                        e instanceof JSONException ||
                        e instanceof NullPointerException) {
                    return getBadRequestResponse(request,
                        "Error parsing JSON request, check parameters.");
                }

                throw e;
            }

            return getResponseMessage(request, "", StatusCode.OK);
        }
    }

    /**
     * A Thread which sends a DataMessage to the server using the parameters
     * of the test connectivity request.
     */
    private class TestConnectivityThread extends Thread {
        private final byte[] payload;
        private final DataMessage.Builder testConnectivityMessageBuilder;

        /**
         * Constructs a TestConnectivityThread
         */
        TestConnectivityThread() {
            int sizeInt = size > Integer.MAX_VALUE ? Integer.MAX_VALUE :
                Integer.parseInt(Long.toString(size));
            this.payload = new byte[sizeInt];
            Arrays.fill(payload, (byte) 0);

            try {
                testConnectivityMessageBuilder = new DataMessage.Builder()
                    .dataItem("count", count)
                    .dataItem("payload", new String(payload, "UTF-8"))
                    .format("urn:oracle:iot:dcd:capability:diagnostics:test_message")
                    .source(endpointId);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void run() {
            try {
                for (long c = 0; c < count; c++) {
                    try {
                        messageDispatcher.queue(testConnectivityMessageBuilder
                            .dataItem("current", c).build());
                        currentCount = c + 1;
                    } catch (ArrayStoreException ase) {
                        // queue is full
                    }

                    Thread.sleep(interval);
                }
            } catch (InterruptedException ie) {
                resetParameters();
                Thread.currentThread().interrupt();
            }
        }
    }
}
