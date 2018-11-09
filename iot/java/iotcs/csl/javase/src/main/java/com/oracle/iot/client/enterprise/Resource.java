/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.enterprise;

import java.util.List;
import java.util.Map;

/**
 * The Resource class represents a device resource.
 */
public interface Resource {
    /**
     * Get the device identifier this resource belongs to
     * @return the device identifier that owns this resource
     */
    String getDeviceId();

    /**
     * Get the resource identifier
     * @return the resource id.
     */
    String getId();

    /**
     * Get the free form description of this resource.
     * @return the resource description
     */
    String getDescription();

    /**
     * Get the relative url to access this resource.
     * @return the resource url
     */
    String getURL();

    /**
     * Get the methods allowed on this resource.
     * @return the methods
     */
    List<String> getMethods();

    /**
     * Invoke the resource GET method and return a response when ready.
     *
     * @param queryparam {@link Map} of {@link String}, {@link String} query
     * @param headers    {@link Map} of {@link String}, {@link String} the request headers.
     * @return response when ready.
     * @throws Exception if GET is not implemented
     */
    Response get(Map<String, String> queryparam, Map<String, String> headers) throws Exception;

    /**
     * Update a resource of a device with the PUT method
     * and returns the response when ready.
     *
     * @param queryparam {@link Map} of {@link String}, {@link String} query
     * @param headers    {@link Map} of {@link String}, {@link String} the request headers.
     * @param body       the request body content.
     * @return - the response when ready.
     * @throws Exception if PUT is not implemented
     */
    Response put(Map<String, String> queryparam, Map<String, String> headers, byte[] body) throws Exception;

    /**
     * Update a resource of a device with the POST method
     * and return a response when ready.
     *
     * @param queryparam {@link Map} of {@link String}, {@link String} query
     * @param headers    {@link Map} of {@link String}, {@link String} the request headers.
     * @param body       the request body content.
     * @return - the response when ready.
     * @throws Exception if POST is not implemented
     */
    Response post(Map<String, String> queryparam, Map<String, String> headers, byte[] body) throws Exception;

    /**
     * Delete a resource of a device with the DELETE method
     * and return a response when ready.
     *
     * @param headers {@link Map} of {@link String}, {@link String} the request headers.
     * @return - the response when ready.
     * @throws Exception if DELETE is not implemented
     */
    Response delete(Map<String, String> headers) throws Exception;
}
