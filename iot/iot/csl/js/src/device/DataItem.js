/**
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

class DataItem {
    /**
     * Constructor that takes a string key and value.
     *
     * @param {string} key data item key.
     * @param {object} value data item value.
     *
     * TODO: Handle these two situations (below).
     * @throws IllegalArgumentException when value is {@link Double#NEGATIVE_INFINITY}, {@link Double#POSITIVE_INFINITY}
     *                                  or {@link Double#NaN} or the key is empty or long string. Maximum length for key
     *                                  is {@link Message.Utils#MAX_KEY_LENGTH} bytes. The length is measured after
     *                                  the key is encoded using UTF-8 encoding.
     * @throws NullPointerException when the key is {@code null}.
     */
    constructor(key, value) {
        // Note: We need to use 'typeof undefined' for value as a !value check is true when value is
        // 0, which is an OK value.
        if (!key || (typeof value === 'undefined')) {
            throw new Error('Key and value must be defined.');
        }

        // Instance "variables"/properties.
        /**
         * Data item key
         * @type {string}
         */
        this.key = key;
        /**
         * Data item value.
         * @type {object}
         */
        this.value = value;
        /**
         * Type of the value.
         * @type {object} (Type)
         */
        this.type = '';
        // Instance "variables"/properties.
    }

    getKey() {
        return this.key;
    }

    getType() {
        return this.type;
    }

    getValue() {
        return this.value;
    }
}
