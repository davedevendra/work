/**
 * Copyright (c) 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and the Universal
 * Permissive License (UPL).  See the LICENSE file in the root directory for license
 * terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl;

import java.util.logging.Level;
import java.util.logging.Logger;

public class TimeManager {
    private static long diffWithServerMilis = 0;

    public static long currentTimeMillis() {
        return System.currentTimeMillis() + diffWithServerMilis;
    }

    public static void setCurrentTimeMillis(long serverTime) {
        long diffWithServerMilis = serverTime - System.currentTimeMillis();
        TimeManager.diffWithServerMilis = diffWithServerMilis;
        LOG("Time", "Difference between server time and client time is " + diffWithServerMilis + " milliseconds.");
    }

    private static void LOG(String meta, String message) {
        if (getLogger().isLoggable(Level.FINEST)) {
            getLogger().log(Level.FINEST, "::: [" + meta + "] ::: " + message);
        }
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
