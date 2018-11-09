/*
 * Copyright (c) 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

/**
 * A convenience class to represent name-value pairs.
 */
class Pair{
    // Instance "variables"/properties...see constructor.

    /**
     * Creates a new pair
     *
     * @param {object} key The key for this pair.
     * @param {object} value The value to use for this pair.
     */
    constructor(key, value) {
        // Instance "variables"/properties.
        /**
         * Name of this Pair.
         */
        this.key = key;
        /**
         * Value of this this Pair.
         */
        this.value = value;
        // Instance "variables"/properties.
    }

    /**
     * Gets the key for this pair.
     *
     * @return {object} key for this pair
     */
    getKey() {
        return this.key;
    }

    /**
     * Gets the value for this pair.
     *
     * @return {object} value for this pair
     */
    getValue() {
        return this.value;
    }
}
