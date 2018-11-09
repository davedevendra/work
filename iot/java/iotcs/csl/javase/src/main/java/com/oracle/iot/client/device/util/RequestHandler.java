/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.device.util;

import com.oracle.iot.client.message.RequestMessage;
import com.oracle.iot.client.message.ResponseMessage;

/**
 * RequestHandler
 */
public interface RequestHandler {

    ResponseMessage handleRequest(RequestMessage requestMessage) throws Exception;

}
