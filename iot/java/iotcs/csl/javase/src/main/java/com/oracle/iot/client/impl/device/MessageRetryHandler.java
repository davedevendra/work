/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import com.oracle.iot.client.message.Message;

public class MessageRetryHandler {

    private final Message message;
    private final int allowedRetryCount;
    private int retryCount;

    MessageRetryHandler(Message message, int baseRetryCount) {
        // Quick check to make sure we're not doing something bad
        if (message == null) 
            throw new NullPointerException();

        this.message = message;
        this.retryCount = 0;

        switch(message.getReliability()){
        case NO_GUARANTEE:
            this.allowedRetryCount = baseRetryCount;
            break;
        case BEST_EFFORT:
            this.allowedRetryCount = baseRetryCount * 2;
            break;
        case GUARANTEED_DELIVERY:
            this.allowedRetryCount = Integer.MAX_VALUE;
            break;
        default:
            // this should never happen, reliability is demanded attribute
            this.allowedRetryCount = 0;
            break;
        }
    }

    Message getMessage() {
        return message;
    }

    // Should be called if the attempt of sending message was unsuccessful
    void addRetry() {
        this.retryCount++;
    }

    // Return available retry count for this message.
    int getAvailableRetryCount() { 
        return this.allowedRetryCount - retryCount; 
    }

    int getRetryCount() {
        return retryCount;
    }
 }
