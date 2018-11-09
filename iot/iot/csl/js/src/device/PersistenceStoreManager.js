/*
 * Copyright (c) 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

/**
 * PersistenceStoreManager
 */
class PersistenceStoreManager {
    /**
     *
     * @param {string} name
     * @return {InMemoryPersistenceStore}
     */
    static get(name) {
        if (!PersistenceStoreManager.persistentStores) {
            /**
             * Map from name to a PersistenceStore instance.
             *
             * @type {Map<string, PersistenceStore>}
             */
            PersistenceStoreManager.persistentStores = new Map();
        }

        let persistentStore = PersistenceStoreManager.persistentStores.get(name);

        if (!persistentStore) {
            persistentStore = new InMemoryPersistenceStore(name);
            PersistenceStoreManager.persistentStores.set(name, persistentStore);
        }

        return persistentStore;
    }

    static has(name) {
        if (!PersistenceStoreManager.persistentStores) {
            /**
             * Map from name to a PersistenceStore instance.
             *
             * @type {Map<string, PersistenceStore>}
             */
            PersistenceStoreManager.persistentStores = new Map();
            return false;
        }

        return PersistenceStoreManager.persistentStores.has(name);
    }
}
