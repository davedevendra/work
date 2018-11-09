/*
 * Copyright (c) 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

/**
 * InMemoryPersistenceStore
 */
class InMemoryPersistenceStore {
    // Instance "variables"/properties...see constructor.

    /**
     *
     * @param {string} name
     */
    constructor(name) {
        // Instance "variables"/properties.
        this.name = name;
        /**
         * Map of items.  Key is the item name.
         * @type {Map<string, object> }
         */
        this.items = new Map();
        // Instance "variables"/properties.
    }

    /**
     * Return true if this PersistenceStore contains the given key.
     *
     * @param key the key to search for.
     * @returns {boolean} true if this {PersistenceStore contains the given key.
     */
    contains(key) {
        return PersistenceStoreManager.has(key);
    }

    /**
     * Return a map of all key/value pairs in this PersistenceStore.
     *
     * @return {Map<string, object>}
     */
    getAll() {
        return new Map(this.items);
    }

    getName() {
        return name;
    }

    /**
     * Return an object value for the given key.
     *
     * @param {string} key the key to search for.
     * @param {object} defaultValue the value to use if this PersistenceStore does not contain the
     *                 key.
     * @return {object} the value for the key.
     */
    getOpaque(key, defaultValue) {
        let obj = this.items.get(key);
        return obj ? obj : defaultValue;
    }

    openTransaction() {
        return new PersistenceStoreTransaction(this);
    }
}
