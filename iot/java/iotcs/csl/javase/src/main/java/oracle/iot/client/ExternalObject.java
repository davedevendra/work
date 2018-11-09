/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package oracle.iot.client;

/**
 * ExternalObject represents the value of a URI type in a device model.
 * The application is responsible for uploading/downloading the content referred 
 * to by the URI.
 */
public class ExternalObject {

    /**
     * Create an {@code ExternalObject}.
     * @param uri The URI
     */
    public ExternalObject(String uri) {
        this.uri = uri;
    }
    
    /**
     * Get the URI value.
     * @return the URI
     */
    public final String getURI() {
        return uri;
    }

    private final String uri;

}