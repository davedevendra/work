/*
 * Copyright (c) 2015, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client;

/**
 * RestApi provides the root path to the REST API version. Setting the
 * property {@code "oracle.iot.client.use_webapi"} to true will cause the
 * code to use {@code /iot/webapi} instead of {@code /iot/api}.
 */
public enum RestApi {

    V1("v1"),
    V2("v2");

    private final String reqRoot;
    private final String privateRoot;
    private final boolean isWebApi;

    RestApi(String version) {
        isWebApi = Boolean.getBoolean("com.oracle.iot.client.use_webapi");
        reqRoot = (isWebApi ? "/iot/webapi/" : "/iot/api/") + version;
        privateRoot = (isWebApi ? "/iot/privatewebapi/" : "/iot/privateapi/") + version;
    }

    public String getReqRoot() {
        return reqRoot;
    }

    public String getPrivateRoot() {
        return privateRoot;
    }

    public boolean isWebApi() { return isWebApi; }
}
