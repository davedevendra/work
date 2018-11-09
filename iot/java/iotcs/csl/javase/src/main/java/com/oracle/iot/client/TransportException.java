/*
 * Copyright (c) 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client;

import java.io.IOException;

/**
 * An unexpected HTTP response from the server is thrown as an TransportException.
 * This class extends IOException so that exceptions of this type can be
 * thrown by client library API.
 */
public class TransportException extends IOException {

    /**
     * Create an TransportException with an HTTP status code.
     * @param statusCode an <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html">HTTP status code</a>}
     */
    public TransportException(int statusCode) {
        super();
        this.statusCode = statusCode;
    }

    /**
     * Create an TransportException with a status code and a message.
     * @param statusCode an <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html">HTTP status code</a>}
     * @param message additional information that may be useful for diagnostics.
     */
    public TransportException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * Get HTTP status code.
     * @return an <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html">HTTP status code</a>}
     */
    public int getStatusCode() {
        return statusCode;
    }

    @Override
    public String toString() {
        final String message = "HTTP " + statusCode;
        if (getMessage() != null) {
            return message + ": " + getMessage();
        }
        return message;
    }

    private final int statusCode;
}
