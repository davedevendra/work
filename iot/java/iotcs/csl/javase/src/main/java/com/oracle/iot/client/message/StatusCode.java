/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.message;

/**
 * Enum for handling HTTP status codes and tests.
 */
public enum StatusCode {
    OK(200, "OK"),
    CREATED(201, "Created"),
    ACCEPTED(202, "Accepted"),
    NON_AUTHORITATIVE_INFORMATION(203, "Non Authoritative Information"),
    NO_CONTENT(204, "No Content"),
    FINISHED(205, "Finished"),
    DATA_FINISHED(206, "Data Finished"),
    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    PAYMENT_REQUIRED(402, "Payment Required"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
    NOT_ACCEPTABLE(406, "Not Acceptable"),
    REQUEST_TIMEOUT(408, "Request Timeout"),
    CONFLICT(409, "Conflict"),
    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
    NOT_IMPLEMENTED(501, "Not Implemented"),
    BAD_GATEWAY(502, "Bad Gateway"),
    SERVICE_UNAVAILABLE(503, "Service Unavailable"),
    OTHER(-1, "Other");

    /** Status code */
    private final int code;
    /** Status text */
    private final String description;

    /**
     * Construct new instance of StatusCode
     * @param code Status code
     * @param description Status text
     */
    StatusCode(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Returns HTTP status code.
     * @return HTTP status code
     */
    public int getCode() {
        return code;
    }

    /**
     * Returns text description of HTTP status.
     * @return text with description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Return information about StatusCode in format: Http (code) (description).
     * @return Text of StatusCode
     */
    public String toString() {
        return "HTTP " + code + ": " + description;
    }

    /**
     * Returns instance of StatusCode for given code. If the code is not found, return {@link #OTHER}.
     * @param code status code to lookup
     * @return instance of StatusCode matching the code value.
     */
    public static StatusCode valueOf(int code) {
        for (StatusCode sc : StatusCode.values()) {
            if (sc.code == code) {
                return sc;
            }
        }
        return OTHER;
    }
}
