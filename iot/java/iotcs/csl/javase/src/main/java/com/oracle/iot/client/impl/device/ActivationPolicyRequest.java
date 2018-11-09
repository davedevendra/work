/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * ActivationPolicyRequest
 */
class ActivationPolicyRequest {

    private static final String OS_NAME = "OSName";
    private static final String OS_VERSION = "OSVersion";
    private static final String JSON_FORMAT =
            "{\"%1$s\":\"%2$s\",\"%3$s\":\"%4$s\"}";

    private final String osVersion;
    private final String osName;

    ActivationPolicyRequest(String osName, String osVersion) {
        this.osName = osName;
        this.osVersion = osVersion;
    }

    public String toQuery() {
        try {
            return "?" + OS_NAME + "=" + URLEncoder.encode(osName, "UTF-8") +
                "&" + OS_VERSION + "=" + URLEncoder.encode(osVersion, "UTF-8");
        }catch(UnsupportedEncodingException ex){
            throw new RuntimeException("utf-8 is not supported", ex);
        }
    }

    public String toJSON() {
        return String.format(JSON_FORMAT, OS_NAME, osName, OS_VERSION, osVersion);
    }

    @Override
    public String toString() {
        return "ActivationPolicyRequest{" +
                OS_NAME + "=" + osName +
                OS_VERSION + "=" + osVersion +
                '}';
    }
}
