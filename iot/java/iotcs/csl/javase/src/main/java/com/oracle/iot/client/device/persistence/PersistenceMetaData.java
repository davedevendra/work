/*
 * Copyright (c) 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.device.persistence;

import java.io.File;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Meta-data related to persistence.
 */
public class PersistenceMetaData {

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }

    private static final String DB_NAME =
            AccessController.doPrivileged(new PrivilegedAction<String>() {
                @Override
                public String run() {
                    return System.getProperty("com.oracle.iot.client.device.persistence.db_name", "persistence_store");
                }
            });

    private static final File LOCAL_STORE_DIRECTORY =
            AccessController.doPrivileged(new PrivilegedAction<File>() {
                public File run() {
                    final String userDir = System.getProperty("user.dir");
                    final String local_store = System.getProperty("com.oracle.iot.client.device.persistence.directory", userDir);
                    return new File(local_store);
                }
            });


    private static final Integer ISOLATION_LEVEL =
            AccessController.doPrivileged(new PrivilegedAction<Integer>() {
                public Integer run() {
                    return Integer.getInteger("com.oracle.iot.client.device.persistence.isolation_level");
                }
            });

    private static final boolean PERSISTENCE_ENABLED =
            AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
                public Boolean run() {
                    final String prop = System.getProperty("com.oracle.iot.client.device.persistence.enabled", "true");
                    return Boolean.parseBoolean(prop);
                }
    });

    /**
     * By default, the database name is {@code "persistence_store"}.
     * This value can be set with the property {@code com.oracle.iot.client.device.persistence.db_name}
     * @return the database name
     */
    public static String getDBName() {
        return DB_NAME;
    }

    /**
     * Get the directory that is used for local persistence.
     * By default, the local storage directory is the directory from which java was run.
     * This value can be set with the property {@code com.oracle.iot.client.device.persistence.local_store}.
     * @return the directory that is used for local persistence
     */
    public static File getLocalStorageDirectory() {
        return LOCAL_STORE_DIRECTORY;
    }

    /**
     * Get the transaction isolation level for SQL database persistence.
     * By default, the value is {@code java.sql.Connection.TRANSACTION_READ_UNCOMMITTED}.
     * This value can be set with the property {@code com.oracle.iot.client.device.persistence.isolation_level}.
     * <p>
     * The {@code Connection} argument is used to ensure the database supports
     * the configured transaction isolation level.
     * If the database does not support transactions, this method returns {@code Connection.TRANSACTION_NONE}.
     * @param connection the database {@code java.sql.Connection}
     * @return the transaction isolation level
     */
    public static int getIsolationLevel(Connection connection) {

        try {
            final int isolationLevel;
            final DatabaseMetaData databaseMetaData = connection.getMetaData();
            if (ISOLATION_LEVEL == null) {
                // If ISOLATION_LEVEL is null, then the property was not set.
                // getDefaultTransactionIsolation() may return TRANSACTION_NONE
                isolationLevel = databaseMetaData.getDefaultTransactionIsolation();

            } else if (databaseMetaData.supportsTransactions()) {

                if (databaseMetaData.supportsTransactionIsolationLevel(ISOLATION_LEVEL)) {
                    isolationLevel = ISOLATION_LEVEL;
                } else {
                    isolationLevel = databaseMetaData.getDefaultTransactionIsolation();
                }

            } else {
                isolationLevel = Connection.TRANSACTION_NONE;
            }
            return isolationLevel;
        } catch (SQLException e) {
            // Could not get DatabaseMetaData
            getLogger().log(Level.WARNING, "Could not get DatabaseMetaData", e);
            return Connection.TRANSACTION_NONE;
        }
    }


    /**
     * Get whether or not persistence is enabled.
     * By default, the value is {@code true}.
     * This value can be set with the property {@code com.oracle.iot.client.device.persistence.enabled}
     * @return {@code true} if persistence is enabled.
     */
    public static boolean isPersistenceEnabled() {
        return PERSISTENCE_ENABLED;
    }

    /** static methods only, do not allow instantiation */
    private PersistenceMetaData() {}
}
