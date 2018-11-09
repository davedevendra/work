/*
 * Copyright (c) 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

/**
 * Class for modifying values in a PersistenceStore. The PersistenceStore itself is not updated
 * until commit() is called.
 */
class PersistenceStoreTransaction {
    // Instance "variables"/properties...see constructor.

    constructor(inMemoryPersistenceStore) {
        // Instance "variables"/properties.
        this.inMemoryPersistenceStore = inMemoryPersistenceStore;
        /**
         * {Map<string, object>}
         */
        this.transactions = new Map();
        // Instance "variables"/properties.
    }

    /**
     * Mark all values to be removed from the PersistenceStore object.  When commit}is called,
     * values are removed before put methods are processed.
     *
     * @return {PersistenceStoreTransaction} this Transaction object.
     */
    clear() {
        this.transactions.clear();
        return this;
    }

    /**
     * Commit this transaction. This method persists the values to the backing store and
     * replaces the values in the {@code PersistenceStore} object.
     *
     * @return {boolean} true if the values were persisted.
     */
    commit() {
        let self = this;

        this.transactions.forEach((v, k) => {
            self.inMemoryPersistenceStore.items.set(k, v);
        });

        return true;
    }

    /**
     * Set an opaque value for the key, which is written back to the PersistenceStore object when
     * commit() is called.
     *
     * @param {string} key a key to be used to retrieve the value.
     * @param {object} value the value.
     * @return {PersistenceStoreTransaction} this Transaction object.
     */
    putOpaque(key, value) {
        this.transactions.set(key, value);
        return this;
    }

    /**
     * Mark all values to be removed from the PersistenceStore object.  When commit is called,
     * values are removed before put methods are processed.
     *
     * @param {string} key a key whose value is to be removed.
     * @return {PersistenceStoreTransaction} this Transaction object.
     */
    remove(key) {
        this.transactions.delete(key);
        return this;
    }
}
