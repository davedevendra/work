/*
 * Copyright (c) 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

/**
 * Utils
 */
public class Utils {
    public static int getIntProperty(String name, int defaultValue) {
        int intValue = defaultValue;
        try {
            Integer value = Integer.getInteger(name, defaultValue);
            if (value != null) 
                intValue = value.intValue();
        } catch (SecurityException e) {
            // use default value
        } finally {
            return intValue;
        }
    }

    public static long getLongProperty(String name, long defaultValue) {
        long longValue = defaultValue;
        try {
            Long value = Long.getLong(name, defaultValue);
            if (value != null) 
                longValue = value.longValue();
        } catch (SecurityException e) {
            // use default value
        } finally {
            return longValue;
        }
    }

    public static short getShortProperty(String name, short defaultValue) {
        short shortValue = defaultValue;
        try {
            Integer value = Integer.getInteger(name, (int)defaultValue);
            if (value != null) {
                if (value.intValue() <= Short.MAX_VALUE)
                    shortValue = (short)value.intValue();
                else                     
                    ; // use default value
            }
        } catch (SecurityException e) {
            // use default value
        } finally {
            return shortValue;
        }
    }



}
