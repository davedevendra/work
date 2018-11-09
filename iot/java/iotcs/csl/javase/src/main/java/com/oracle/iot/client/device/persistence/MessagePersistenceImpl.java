/*
 * Copyright (c) 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.device.persistence;

import com.oracle.iot.client.message.Message;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Support for persisting messages for guaranteed delivery.
 */
public class MessagePersistenceImpl extends MessagePersistence {

    final DataSource dataSource;

    /**
     * Create an implementation of MessagePersistence.
     * @param context an application context, typically a {@code android.content.Context}, or {@code null}
     */
    public MessagePersistenceImpl(Object context) {
        super();

        if (PersistenceMetaData.isPersistenceEnabled()) {

            final Properties properties = new Properties();
            properties.put(JavaDBDataSourceFactory.JDBC_DATABASE_NAME, PersistenceMetaData.getDBName());
            //properties.put(JavaDBDataSourceFactory.BOOT_PASSWORD_PROPERTY, ??);

            DataSource ds = null;
            try {
                ds = JavaDBDataSourceFactory.createDataSource(properties);
            } catch (SQLException e) {
                getLogger().log(Level.WARNING, "SQL exception during creation of the DataSource.", e);
            } finally {
                this.dataSource = ds;
            }

            if (!tableExists("MESSAGES")) {
                createTable("MESSAGES");
            }

        } else {
            this.dataSource = null;
        }
    }

    // this method is package private because of testing
    boolean tableExists(String tableName) {
        Connection connection = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            connection.setTransactionIsolation(PersistenceMetaData.getIsolationLevel(connection));
            connection.setAutoCommit(false);
            DatabaseMetaData metaData = connection.getMetaData();
            resultSet = metaData.getTables(null, null, tableName, null);
            boolean b = resultSet.next();
            return b;
        } catch (SQLException e) {
            getLogger().log(Level.WARNING, "SQL exception during validation of existence of the table for messages.", e);
        } finally {
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException ignored) {}
            }
        }
        return false;
    }

    // this method is package private because of testing
    void createTable(String tableName) {
        Connection connection = null;
        Statement createStmt = null;
        try {
            connection = dataSource.getConnection();
            connection.setTransactionIsolation(PersistenceMetaData.getIsolationLevel(connection));
            createStmt = connection.createStatement();

            int maxEndpointIdLength = 100;
            int maxUuidLength = 40;

            String CREATE_TABLE =
                    "CREATE TABLE " + tableName.toUpperCase(Locale.ROOT) +
                            "(TIMESTAMP BIGINT NOT NULL," +
                            "UUID VARCHAR(" + maxUuidLength + ") NOT NULL," +
                            "ENDPOINT_ID VARCHAR(" + maxEndpointIdLength + ") NOT NULL," +
                            "MESSAGE BLOB," +
                            " PRIMARY KEY (UUID))";

            String ENDPOINT_ID_INDEX =
                    "CREATE INDEX endpoint_id ON " + tableName + "(ENDPOINT_ID)";

            createStmt.executeUpdate(CREATE_TABLE);
            createStmt.executeUpdate(ENDPOINT_ID_INDEX);
        } catch (SQLException e) {
            // X0Y32 is the error code when the database already exists.
            if ("X0Y32".equals(e.getSQLState())) {
                getLogger().log(Level.FINE, "The table for messages already exists.", e);
            }
            else {
                getLogger().log(Level.WARNING, "SQL exception during creation of the table for messages.", e);
            }
        } finally {
            if (createStmt != null) {
                try {
                    createStmt.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    @Override
    public void save(Collection<Message> messages, String endpointId) {
        save("MESSAGES", messages, endpointId);
    }

    /* package */ synchronized void save(String tableName, Collection<Message> messages, String endpointId) {
        if (!PersistenceMetaData.isPersistenceEnabled()) {
            return;
        }
        Connection connection = null;
        PreparedStatement ps = null;
        try {
            connection = dataSource.getConnection();
            connection.setTransactionIsolation(PersistenceMetaData.getIsolationLevel(connection));
            connection.setAutoCommit(false);
            ps = connection.prepareStatement("INSERT INTO " + tableName + " VALUES (?, ?, ?, ?)");
            for (Message message : messages) {

                final byte[] blob;
                try {
                    blob = message.toJson().toString().getBytes("UTF-8");
                } catch (UnsupportedEncodingException cannot_happen) {
                    // Cannot happen. UTF-8 is a required encoding
                    // Throw runtime exception to make compiler happy
                    throw new RuntimeException(cannot_happen);
                }

                ps.setLong(1, message.getEventTime());
                ps.setString(2, message.getClientId());
                ps.setString(3, endpointId);
                ps.setBlob(4, new ByteArrayInputStream(blob));
                ps.addBatch();
            }
            ps.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            getLogger().log(Level.WARNING, "SQL exception during saving messages to database.", e);
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException e1) {
                    getLogger().log(Level.WARNING, "Cannot rollback transaction.", e1);
                }
            }
        } finally {
            if (ps != null) {
                try {
                    ps.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    // Package
    synchronized void save(String tableName, Message message, String endpointId) {
        if (!PersistenceMetaData.isPersistenceEnabled()) {
            return;
        }
        Connection connection = null;
        PreparedStatement ps = null;
        try {
            connection = dataSource.getConnection();
            connection.setTransactionIsolation(PersistenceMetaData.getIsolationLevel(connection));
            connection.setAutoCommit(false);
            ps = connection.prepareStatement("INSERT INTO " + tableName + " VALUES (?, ?, ?, ?)");

            final byte[] blob;
            try {
                blob = message.toJson().toString().getBytes("UTF-8");
            } catch (UnsupportedEncodingException cannot_happen) {
                // Cannot happen. UTF-8 is a required encoding
                // Throw runtime exception to make compiler happy
                throw new RuntimeException(cannot_happen);
            }

            ps.setLong(1, message.getEventTime());
            ps.setString(2, message.getClientId());
            ps.setString(3, endpointId);
            ps.setBlob(4, new ByteArrayInputStream(blob));
            ps.execute();
            connection.commit();
        } catch (SQLException e) {
            getLogger().log(Level.WARNING, "SQL exception during saving messages to database.", e);
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException e1) {
                    getLogger().log(Level.WARNING, "Cannot rollback transaction.", e1);
                }
            }
        } finally {
            if (ps != null) {
                try {
                    ps.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    @Override
    public void delete(Collection<Message> messages) {
        delete("MESSAGES", messages);
    }

    /* package */ synchronized void delete(String tableName, Collection<Message> messages) {
        if (!PersistenceMetaData.isPersistenceEnabled()) {
            return;
        }
        Connection connection = null;
        PreparedStatement ps = null;
        try {
            connection = dataSource.getConnection();
            connection.setTransactionIsolation(PersistenceMetaData.getIsolationLevel(connection));
            connection.setAutoCommit(false);
            ps = connection.prepareStatement("DELETE FROM " + tableName + " WHERE uuid = ?");
            for (Message message : messages) {
                ps.setString(1, message.getClientId());
                ps.addBatch();
            }
            ps.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException e1) {
                getLogger().log(Level.WARNING, "Cannot rollback transaction.", e1);
            }
            getLogger().log(Level.WARNING, "SQL exception during deleting messages from database.", e);
        } finally {
            if (ps != null) {
                try {
                    ps.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    @Override
    public List<Message> load(String endpointId) {
        return load("MESSAGES", endpointId);
    }

    /* package */ synchronized List<Message> load(String tableName, String endpointId) {

        if (!PersistenceMetaData.isPersistenceEnabled()) {
            return null;
        }
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            connection.setTransactionIsolation(PersistenceMetaData.getIsolationLevel(connection));
            final List<Message> messages = new ArrayList<Message>();
            ps = connection.prepareStatement(
                    "SELECT * FROM " + tableName + " WHERE ENDPOINT_ID = ? ORDER BY timestamp"
            );
            ps.setString(1, endpointId);
            resultSet = ps.executeQuery();

            while (resultSet.next()) {
                final Blob blob = resultSet.getBlob(4);
                final byte[] bytes = blob.getBytes(1, (int) blob.length());
                final List<Message> list = Message.fromJson(bytes);
                final Message message = list.isEmpty() ? null : list.get(0);
                messages.add(message);
            }
            return messages;
        } catch (SQLException e) {
            getLogger().log(Level.WARNING, "SQL exception during loading messages from database.", e);
        } finally {
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException ignored) {}
            }
            if (ps != null) {
                try {
                    ps.close();
                } catch (SQLException ignored) {}
            }
        }
        return Collections.emptyList();
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }

}
