/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import java.util.Map;

/**
 * PersistenceStore
 */
public interface PersistenceStore {

    /**
     * Interface for modifying values in a {@code PersistenceStore}. The {@code PersistenceStore}
     * itself is not updated until {@code commit()} is called.
     */
    interface Transaction {

        /**
         * Mark all values to be removed from the {@code PersistenceStore} object.
         * When {@code commit} is called, values are removed before put methods are processed.
         *
         * @return this {@code Transaction} object
         */
        Transaction clear();

        /**
         * Commit this transaction. This method persists the values to the backing store and
         * replaces the values in the {@code PersistenceStore} object.
         * @return {@code true} if the values were persisted
         */
        boolean commit();

        /**
         * Set a {@code boolean} value for the {@code key}, which is written back to
         * the {@code PersistenceStore} object when {@code commit()} is called.
         * @param key a key to be used to retrieve the value
         * @param value the value
         * @return this {@code Transaction} object
         */
        Transaction putBoolean(String key, boolean value);

        /**
         * Set a {@code double} value for the {@code key}, which is written back to
         * the {@code PersistenceStore} object when {@code commit()} is called.
         * @param key a key to be used to retrieve the value
         * @param value the value
         * @return this {@code Transaction} object
         */
        Transaction putDouble(String key, double value);

        /**
         * Set a {@code float} value for the {@code key}, which is written back to
         * the {@code PersistenceStore} object when {@code commit()} is called.
         * @param key a key to be used to retrieve the value
         * @param value the value
         * @return this {@code Transaction} object
         */
        Transaction putFloat(String key, float value);

        /**
         * Set an {@code int} value for the {@code key}, which is written back to
         * the {@code PersistenceStore} object when {@code commit()} is called.
         * @param key a key to be used to retrieve the value
         * @param value the value
         * @return this {@code Transaction} object
         */
        Transaction putInt(String key, int value);

        /**
         * Set a {@code long} value for the {@code key}, which is written back to
         * the {@code PersistenceStore} object when {@code commit()} is called.
         * @param key a key to be used to retrieve the value
         * @param value the value
         * @return this {@code Transaction} object
         */
        Transaction putLong(String key, long value);

        /**
         * Set a {@code String} value for the {@code key}, which is written back to
         * the {@code PersistenceStore} object when {@code commit()} is called.
         * @param key a key to be used to retrieve the value
         * @param value the value
         * @return this {@code Transaction} object
         */
        Transaction putString(String key, String value);

        /**
         * Set an opaque value for the {@code key}, which is written back to
         * the {@code PersistenceStore} object when {@code commit()} is called.
         * @param key a key to be used to retrieve the value
         * @param value the value
         * @return this {@code Transaction} object
         */
        Transaction putOpaque(String key, Object value); // TODO: value should be Serializable or some such.

        /**
         * Mark all values to be removed from the {@code PersistenceStore} object.
         * When {@code commit} is called, values are removed before put methods are processed.
         *
         * @param key a key whose value is to be removed
         * @return this {@code Transaction} object
         */
        Transaction remove(String key);
    }

    /**
     * Return the name used by the {@code PersistenceManager} to reference this {@code PersistenceStore}.
     * @return The {@code PersistenceStore} name.
     */
    String getName();

    /**
     * Open a {@code Transaction} object for modifying the values of this {@code PersistenceStore}.
     * @return a {@link PersistenceStore.Transaction} object
     */
    Transaction openTransaction();

    /**
     * Return true if this {@code PersistenceStore} contains the given key.
     * @param key the key to search for
     * @return {@code true} if this {@code PersistenceStore} contains the given key
     */
    boolean contains(String key);

    /**
     * Return a map of all key&ndash;value pairs in this {@code PersistenceStore}
     * @return
     */
    Map<String,?> getAll();

    /**
     * Return a {@code boolean} value for the given key.
     * @param key the key to search for
     * @param defaultValue the value to use if this {@code PersistenceStore} does not contain the key
     * @return the value for the key
     */
    boolean getBoolean(String key, boolean defaultValue);

    /**
     * Return a {@code double} value for the given key.
     * @param key the key to search for
     * @param defaultValue the value to use if this {@code PersistenceStore} does not contain the key
     * @return the value for the key
     */
    double getDouble(String key, double defaultValue);

    /**
     * Return a {@code float} value for the given key.
     * @param key the key to search for
     * @param defaultValue the value to use if this {@code PersistenceStore} does not contain the key
     * @return the value for the key
     */
    float getFloat(String key, float defaultValue);

    /**
     * Return a {@code int} value for the given key.
     * @param key the key to search for
     * @param defaultValue the value to use if this {@code PersistenceStore} does not contain the key
     * @return the value for the key
     */
    int getInt(String key, int defaultValue);

    /**
     * Return a {@code long} value for the given key.
     * @param key the key to search for
     * @param defaultValue the value to use if this {@code PersistenceStore} does not contain the key
     * @return the value for the key
     */
    long getLong(String key, long defaultValue);

    /**
     * Return a {@code String} value for the given key.
     * @param key the key to search for
     * @param defaultValue the value to use if this {@code PersistenceStore} does not contain the key
     * @return the value for the key
     */
    String getString(String key, String defaultValue);

    /**
     * Return a {@code Object} value for the given key.
     * @param key the key to search for
     * @param defaultValue the value to use if this {@code PersistenceStore} does not contain the key
     * @return the value for the key
     */
    Object getOpaque(String key, Object defaultValue);
}
