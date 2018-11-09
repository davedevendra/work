/*
 * Copyright (c) 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.device.persistence;

import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;

import org.apache.derby.jdbc.BasicEmbeddedDataSource40;
import org.apache.derby.jdbc.BasicEmbeddedConnectionPoolDataSource40;

/* package */ class JavaDBDataSourceFactory {

    final static String JAVADB_DRIVER_NAME = "derby";
    final static String JAVADB_DRIVER_CLASS = "org.apache.derby.jdbc";
    final static String JAVADB_DRIVER_VERSION = "10.11.1.1";
    final static String BOOT_PASSWORD_PROPERTY = "jdbcservice.bootPassword";

    final static String JDBC_DATABASE_NAME = "databaseName";
    final static String JDBC_DATASOURCE_NAME = "datasourceName";
    final static String JDBC_DESCRIPTION = "description";
    final static String JDBC_DRIVER_NAME = "driverName";
    final static String JDBC_DRIVER_CLASS  = "driverClass";
    final static String JDBC_DRIVER_VERSION = "driverVersion";
    final static String JDBC_NETWORK_PROTOCOL = "networkProtocol";
    final static String JDBC_SERVER_NAME = "serverName";
    final static String JDBC_PORT_NUMBER = "portNumber";
    final static String JDBC_USER = "user";
    final static String JDBC_PASSWORD = "password";
    final static String JDBC_ROLE_NAME = "roleName";
    final static String JDBC_URL = "url";
    final static String JDBC_INITIAL_POOL_SIZE = "initialPoolSize";
    final static String JDBC_MIN_POOL_SIZE = "minPoolSize";
    final static String JDBC_MAX_POOL_SIZE = "maxPoolSize";
    final static String JDBC_MAX_IDLE_TIME = "maxIdleTime";
    final static String JDBC_MAX_STATEMENTS = "maxStatements";
    final static String JDBC_PROPERTY_CYCLE = "propertyCycle";
    

    // No instantiation
    private JavaDBDataSourceFactory() {
    }

    private static void validateProperties(Properties properties, boolean plainDataSource) throws SQLException {
        if (properties.getProperty(JDBC_DATABASE_NAME) == null) {
            throw new SQLException("Database name is not specified");
        }
        for (Enumeration e = properties.propertyNames(); e.hasMoreElements();) {
            final String key = (String) e.nextElement();
            // no String in switch expression for se1.6 :(
            if (JDBC_NETWORK_PROTOCOL.equals(key)
                    || JDBC_SERVER_NAME.equals(key)
                    || JDBC_PORT_NUMBER.equals(key)) {
                throw new SQLException("Network database is not supported (" + key + ")");
            } else if (JDBC_USER.equals(key)
                    || JDBC_PASSWORD.equals(key)
                    || JDBC_ROLE_NAME.equals(key)) {
                // TODO.equals(key) enable user authentication
                throw new SQLException("User authentication is not supported (" + key + ")");
            } else if (JDBC_URL.equals(key)) {
                throw new SQLException("Database URL is not supported");
            } else if (JDBC_DATABASE_NAME.equals(key)
                    || JDBC_DATASOURCE_NAME.equals(key)
                    || JDBC_DESCRIPTION.equals(key)
                    || BOOT_PASSWORD_PROPERTY.equals(key)) {
                return;
            } else if (JDBC_INITIAL_POOL_SIZE.equals(key)
                    || JDBC_MIN_POOL_SIZE.equals(key)
                    || JDBC_MAX_POOL_SIZE.equals(key)
                    || JDBC_MAX_IDLE_TIME.equals(key)
                    || JDBC_MAX_STATEMENTS.equals(key)
                    || JDBC_PROPERTY_CYCLE.equals(key)) {
                if (plainDataSource) {
                    throw new SQLException(key + " is not supported");
                }
            } else if (JDBC_DRIVER_CLASS.equals(key)) {
                if (!properties.getProperty(key).equalsIgnoreCase(JAVADB_DRIVER_CLASS)) {
                    throw new SQLException("Unsupported driver class");
                }
            } else if (JDBC_DRIVER_NAME.equals(key)) {
                if (!properties.getProperty(key).equalsIgnoreCase(JAVADB_DRIVER_NAME)) {
                    throw new SQLException("Unsupported driver name");
                }
            } else if (JDBC_DRIVER_VERSION.equals(key)) {
                if (!properties.getProperty(key).equalsIgnoreCase(JAVADB_DRIVER_VERSION)) {
                    throw new SQLException("Unsupported driver version");
                }
            } else {
                throw new SQLException(key + " is not supported");
            }
        }
    }

    static DataSource createDataSource(Properties properties) throws SQLException {
        // TODO: TCK requires allowing null properties,
        // but Database Name is needed according to derby specification
        validateProperties(properties, true);
        BasicEmbeddedDataSource40 dataSource = new BasicEmbeddedDataSource40();
        dataSource.setDatabaseName(properties.getProperty(JDBC_DATABASE_NAME));
        dataSource.setDataSourceName(properties.getProperty(JDBC_DATASOURCE_NAME));
        dataSource.setDescription(properties.getProperty(JDBC_DESCRIPTION));
        dataSource.setCreateDatabase("create");
        String bootPassword = properties.getProperty(BOOT_PASSWORD_PROPERTY);
        if (bootPassword != null) {
            dataSource.setConnectionAttributes("dataEncryption=true;bootPassword=" + bootPassword);
        }
        return dataSource;
    }

    static ConnectionPoolDataSource createConnectionPoolDataSource(Properties properties) throws SQLException {
        validateProperties(properties, false);
        BasicEmbeddedConnectionPoolDataSource40 dataSource = new BasicEmbeddedConnectionPoolDataSource40();
        dataSource.setDatabaseName(properties.getProperty(JDBC_DATABASE_NAME));
        dataSource.setCreateDatabase("create");
        // TODO: how can we configure connection pool properties?
        String bootPassword = properties.getProperty(BOOT_PASSWORD_PROPERTY);
        if (bootPassword != null) {
            dataSource.setConnectionAttributes("dataEncryption=true;bootPassword=" + bootPassword);
        }
        return dataSource;
    }

}
