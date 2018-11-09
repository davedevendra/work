/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

class ConnectionPoolManagerImpl implements ConnectionPoolManager {

    // Constants for default pool size, package private for testing
    static final int DEFAULT_MIN_POOL_SIZE = 5;
    static final int DEFAULT_MAX_POOL_SIZE = 20;

    private static int MIN_POOL_SIZE
            = Integer.getInteger("com.oracle.iot.message_persistence_min_pool_size", DEFAULT_MIN_POOL_SIZE);

    private static int MAX_POOL_SIZE
            = Integer.getInteger("com.oracle.iot.message_persistence_max_pool_size", DEFAULT_MAX_POOL_SIZE);

    // Logger used for logging events
    private final Logger logger;

    // Queue for storing opened connection, package private for testing
    Queue<Connection> connectionPool = new LinkedList<Connection>();

    // Thread that is responsible for creation/closing of the connections, package private for testing
    Thread connectionHandlerThread;

    private final DataSource dataSource;

    // Sets all needed variables & constants
    ConnectionPoolManagerImpl(DataSource dataSource, final Logger logger) {
        this.dataSource = dataSource;
        this.logger = logger;
        initialize();
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                ConnectionPoolManagerImpl.this.stop();
            }
        }));
    }

    /**
     * This method creates DataSource, creates new connections and starts
     * thread that will be responsible for creation/closing connections.
     */
    //package private for testing
    void initialize() {
        updateConnectionPool();
        startConnectionHandlerThread();
    }

    /**
     * This method contains body for thread that is responsible for creation/closing connections
     */
    //package private for testing
    void startConnectionHandlerThread() {
        connectionHandlerThread = new Thread() {
            @Override
            public void run() {
                while (!this.isInterrupted()) {
                    updateConnectionPool();
                    synchronized (connectionPool) {
                        try {
                            connectionPool.wait();
                        } catch (InterruptedException e) {
                            this.interrupt();
                            break;
                        }
                    }
                }

            }
        };
        connectionHandlerThread.start();
    }

    /**
     * This methods increases number of connections if the number of connection is below configurable minimum or
     * removed and closes connections if there are more connections than configurable maximum. It does nothing if the number
     * of connection is in interval between min and max.
     */
    // package private for testing
    void updateConnectionPool() {
        int poolSizeDifference = checkSizeOfConnectionPool();
        if (poolSizeDifference > 0) {
            for (int i = 0; i < poolSizeDifference; i++) {
                Connection connection = null;
                while (connection == null) {
                    connection = createNewConnection();
                    if (connection != null) {
                        synchronized (connectionPool) {
                            connectionPool.offer(connection);
                        }
                    }
                }
            }
        } else if (poolSizeDifference < 0) {
            Connection connection;
            for (int i = poolSizeDifference; i < 0; i++) {
                synchronized (connectionPool) {
                    connection = connectionPool.poll();
                }
                try {
                    connection.close();
                } catch (SQLException e) {
                    logger.log(Level.WARNING, "Unable to close connection to database", e);
                }
            }
        }
    }

    /**
     * Method checks the number of available connections. If the number is below min, it returns the positive number
     * of connection that should be created. If the number is higher than max, if return the negative number of connection
     * that should be removed from the pool and closed. Zero is returned otherwise.
     *
     * @return Number of connection to create (positive) or to destroy (negative) or 0.
     */
    private int checkSizeOfConnectionPool() {
        int minSize = 5; //config.getConnectionMinPoolSize();
        int maxSize = 10; //config.getConnectionMaxPoolSize();
        if (minSize <= 0 || maxSize <= 0 || minSize >= maxSize) {
            minSize = DEFAULT_MIN_POOL_SIZE;
            maxSize = DEFAULT_MAX_POOL_SIZE;
        }
        synchronized (connectionPool) {
            int currentSize = connectionPool.size();
            if (currentSize > maxSize) return maxSize - currentSize;
            if (currentSize < minSize) return minSize - currentSize;
        }
        return 0;
    }

    /**
     * Method creates new connection to the database
     * @return newly created connection to the database
     */
    // package private for testing
    Connection createNewConnection() {
        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Cannot create new connection to database", e);
            return null;
        }
        return connection;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Connection getConnectionFromPool() {
        Connection connection = null;
        synchronized (connectionPool) {
            while (connectionPool.size() > 0) {
                connection = connectionPool.poll();
                try {
                    if (connection.isClosed()) {
                        connection = null;
                        continue;
                    }
                } catch (SQLException e) {
                    logger.log(Level.FINE, "Pre-created connection to database was already closed.", e);
                    connection = null;
                    continue;
                }
                if (connection != null) {
                    break;
                }
            }
            connectionPool.notify();
        }
        if (connection != null) {
            return connection;
        } else {
            logger.log(Level.FINE, "There are no more available connections");
            // if there are no available connections, lets create new one.
            return createNewConnection();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void returnConnectionToPool(Connection connection) {
        try {
            if (!connection.isClosed()) {
                synchronized (connectionPool) {
                    connection.setAutoCommit(true);
                    connectionPool.offer(connection);
                    connectionPool.notify();
                }
            } else {
                logger.log(Level.WARNING, "Trying to reuse closed connection");
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Trying to reuse closed connection", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        connectionHandlerThread.interrupt();
        synchronized (connectionPool) {
            while (!connectionPool.isEmpty()) {
                try {
                    connectionPool.poll().close();
                } catch (SQLException e) {
                    logger.log(Level.FINE, "Cannot close connection to the database!", e);
                }
            }
        }
    }
}
