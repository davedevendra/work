/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.enterprise;


import java.io.IOException;

import java.security.GeneralSecurityException;

import java.util.Map;

/**
 * A {@code Response} instance is used to retrieve the result of a request on a {@link Resource}.
 * Requests on these resources are asynchronous, so, before accessing the result code or data, it is necessary to
 * check that the request is terminated by calling {@link #isDone()}.
 * <p>
 * <b>Example of use:</b>
 * <pre><code>
 *      Response resp = resource.get(...);
 *      while (!resp.isDone()) {
 *          Thread.sleep(xxx);
 *          resp.poll();
 *      }
 *      byte[] data = resp.getBody();
 *      ...
 * </code></pre>
 */
public interface Response {

    /**
     * Returns the HTTP status code
     *
     * @return the status code.
     * @throws IllegalStateException if the response is not complete.
     */
    int getStatusCode();

    /**
     * Returns the message id of the http request that this is a response for.
     *
     * @return the request id.
     */
    String getRequestId();

    /**
     * Get request message Path.
     *
     * @return Path, never {@code null}
     */
    String getPath();

    /**
     * Get HTTP response message body. If message body contains string value, default encoding is UTF-8.
     *
     * @return body, never {@code null}
     * @throws IllegalStateException if the response is not complete.
     * @see #isDone()
     */
    byte[] getBody() throws IllegalStateException;

    /**
     * @return the Response headers.
     * @throws IllegalStateException if the response is not complete.
     * @see #isDone()
     */
    Map<String, String> getHeaders() throws IllegalStateException;

    /**
     * Call {@code isDone} to determine if the response is complete.
     * Resource requests to the cloud service can be asynchronous. Before
     * obtaining the body {@code isDone} must be called and return {@code true}
     * before calling {@code getBody}, {@code getBodyString} or
     * {@code getHeaders}.
     * If {@code isDone()} returns {@code false} the application must call {@link #poll()}
     * to try to update the response.
     *
     * @return {code true} if the response is complete, {@code false} otherwise.
     */
    boolean isDone();

    /**
     * This method is used to look for an available response.
     * It should typically be called when {@link #isDone()} has returned {@code false}.
     *
     * @throws IOException if network error occurs
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid
     */
    void poll() throws IOException, GeneralSecurityException;
}
