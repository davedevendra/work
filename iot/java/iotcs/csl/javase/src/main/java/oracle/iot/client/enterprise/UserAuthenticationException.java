/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package oracle.iot.client.enterprise;

import java.security.GeneralSecurityException;

/**
 * Thrown to indicate that a User Authentication exception occurred on a communication operation.
 */
public class UserAuthenticationException extends GeneralSecurityException {

    private static final long serialVersionUID = 4760019292913972546L;

    // Value of the location header field at the time a REST call has been redirected for
    // Authentication
    private String location;

    /**
     * Constructs a new {@code UserAuthenticationException} instance with the specified
     * detailed reason message and value of the Location header field contained in the HTTP redirect
     * response that triggers this exception. The error message string {@code message} can later be
     * retrieved by the {@link Throwable#getMessage() getMessage} method. The URL of the location
     * (@code location) can be retrieved by the
     * {@link UserAuthenticationException#getLocation()} method.
     *
     * @param message the detail message (which is saved for later retrieval by
     * the getMessage() method)
     * @param location a URL ,the value of the location header field contained in the HTTP
     * redirect response (which is saves for retrieval by the getLocation() method)
     */
    public UserAuthenticationException(String message, String location) {
        super(message);
        this.location = location;
    }

    /**
     * Constructs a new {@code UserAuthenticationException} instance with the specified
     * detailed reason message and value of the Location header field contained in the HTTP redirect
     * response that triggers this exception. The error message string {@code message} can later be
     * retrieved by the {@link Throwable#getMessage() getMessage} method. The URL of the location
     * (@code location) can be retrieved by the
     * {@link UserAuthenticationException#getLocation()} method.
     *
     * @param message the detail message (which is saved for later retrieval by
     * the getMessage() method)
     * @param location a URL, value of the location header field contained in the HTTP
     * redirect response (which is saves for retrieval by the getLocation() method)
     * @param cause the cause (which is saved for later retrieval by the
     * getCause() method). (A null value is permitted, and indicates that the
     * cause is nonexistent or unknown)
     */
    public UserAuthenticationException(String message, String location, Throwable cause) {
        super(message, cause);
        this.location = location;
    }

    /**
     * Returns a URL, value of the location header field contained in the HTTP redirect response
     * that triggers this exception.
     *
     * @return URL, value value of the location header field contained in the HTTP redirect response
     * that triggers this exception.
     */
    public String getLocation() {
        return this.location;
    }
}
