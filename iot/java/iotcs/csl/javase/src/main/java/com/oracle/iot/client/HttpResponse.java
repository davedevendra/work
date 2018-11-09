/*
 * Copyright (c) 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

/**
 * HttpResponse
 */
public class HttpResponse {
    private final int status;
    private final byte[] data;
    private final Map<String, List<String>> headers;

    public HttpResponse(int responseCode, byte[] data, Map<String, List<String>> headers) {
        this.status = responseCode;
        this.data = data;
        this.headers = headers;
    }

    public int getStatus() {
        return status;
    }

    public byte[] getData() {
        return data;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public String getVerboseStatus(String method, String path) {

        final StringBuilder builder = new StringBuilder();
        if (method != null) {
            builder.append(method)
                    .append(' ');
        }
        if (path != null) {
            builder.append(path)
                    .append(": ");
        }
        builder.append("HTTP ")
                .append(status);

        if (status < 200 || status >= 400) {
            if (data != null && data.length > 0) {
                try {
                    builder.append(' ')
                            .append(new String(data, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    // UTF-8 is a required encoding
                }
            }
        }

        return builder.toString();
    }

    @Override
    public String toString() {
        return getVerboseStatus(null, null);
    }

}
