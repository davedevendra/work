/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import java.util.HashMap;
import java.util.Map;

/**
 * PersistenceManager
 */
public class PersistenceStoreManager {

    public static PersistenceStore getPersistenceStore(String name) {

        PersistenceStore persistenceStore = map.get(name);
        if (persistenceStore == null) {
            persistenceStore = new InMemoryPersistenceStore(name);
            map.put(name, persistenceStore);
        }
        return persistenceStore;
    }

    private static Map<String, PersistenceStore> map = new HashMap<String,PersistenceStore>();

    private static class InMemoryPersistenceStore implements PersistenceStore {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Transaction openTransaction() {
            return new Transaction();
        }

        @Override
        public boolean contains(String key) {
            return map.containsKey(key);
        }

        @Override
        public Map<String, ?> getAll() {
            return new HashMap<String,Object>(map);
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            final Object obj = map.get(key);
            return obj != null ? Boolean.class.cast(obj) : defaultValue;
        }

        @Override
        public double getDouble(String key, double defaultValue) {
            final Object obj = map.get(key);
            return obj != null ? Double.class.cast(obj) : defaultValue;
        }

        @Override
        public float getFloat(String key, float defaultValue) {
            final Object obj = map.get(key);
            return obj != null ? Float.class.cast(obj) : defaultValue;
        }

        @Override
        public int getInt(String key, int defaultValue) {
            final Object obj = map.get(key);
            return obj != null ? Integer.class.cast(obj) : defaultValue;
        }

        @Override
        public long getLong(String key, long defaultValue) {
            final Object obj = map.get(key);
            return obj != null ? Long.class.cast(obj) : defaultValue;
        }

        @Override
        public String getString(String key, String defaultValue) {
            final Object obj = map.get(key);
            return obj != null ? String.class.cast(obj) : defaultValue;
        }

        @Override
        public Object getOpaque(String key, Object defaultValue) {
            final Object obj = map.get(key);
            return obj != null ? obj : defaultValue;
        }

        private InMemoryPersistenceStore(String name) {
            this.name = name;
            this.map = new HashMap<String, Object>();
        }

        private final String name;
        private final Map<String,Object> map;

        private class Transaction implements PersistenceStore.Transaction {

            @Override
            public PersistenceStore.Transaction clear() {
                map.clear();
                return this;
            }

            @Override
            public boolean commit() {
                InMemoryPersistenceStore.this.map.putAll(Transaction.this.map);
                return true;
            }

            @Override
            public PersistenceStore.Transaction putBoolean(String key, boolean value) {
                this.map.put(key, value);
                return this;
            }

            @Override
            public PersistenceStore.Transaction putDouble(String key, double value) {
                this.map.put(key, value);
                return this;
            }

            @Override
            public PersistenceStore.Transaction putFloat(String key, float value) {
                this.map.put(key, value);
                return this;
            }

            @Override
            public PersistenceStore.Transaction putInt(String key, int value) {
                this.map.put(key, value);
                return this;
            }

            @Override
            public PersistenceStore.Transaction putLong(String key, long value) {
                this.map.put(key, value);
                return this;
            }

            @Override
            public PersistenceStore.Transaction putString(String key, String value) {
                this.map.put(key, value);
                return this;
            }

            @Override
            public PersistenceStore.Transaction putOpaque(String key, Object value) {
                this.map.put(key, value);
                return this;
            }

            @Override
            public PersistenceStore.Transaction remove(String key) {
                this.map.remove(key);
                return this;
            }

            Transaction() {
                this.map = new HashMap<String,Object>();
            }

            private final Map<String,Object> map;

        }
    }
}
