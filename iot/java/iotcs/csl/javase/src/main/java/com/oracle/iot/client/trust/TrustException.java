/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.trust;

import java.security.GeneralSecurityException;

/**
 * Thrown to indicate that a general exception occurred on a Trust Management operation.
 */
@SuppressWarnings("serial")
public class TrustException extends GeneralSecurityException {

    private static final long serialVersionUID = 3449247095701815213L;

    /**
     * Constructs a new {@code TrustException} instance with the specified
     * detailed reason message. The error message string {@code message} can
     * later be retrieved by the {@link Throwable#getMessage() getMessage}
     * method.
     *
     * @param message the detail message (which is saved for later retrieval by
     * the getMessage() method)
     */
    public TrustException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code TrustException} instance with the specified
     * detailed reason message. The error message string {@code message} can
     * later be retrieved by the {@link Throwable#getMessage() getMessage}
     * method.
     *
     * @param message the detail message (which is saved for later retrieval by
     * the getMessage() method)
     * @param cause the cause (which is saved for later retrieval by the
     * getCause() method). (A null value is permitted, and indicates that the
     * cause is nonexistent or unknown)
     */
    public TrustException(String message, Throwable cause) {
        super(message, cause);
    }

}
