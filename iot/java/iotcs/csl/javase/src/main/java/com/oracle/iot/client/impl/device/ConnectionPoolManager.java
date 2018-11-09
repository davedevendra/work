/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import java.sql.Connection;

/**
 * This interface is for simple connection pool manager. It stores and provides connections to the database.
 */
public interface ConnectionPoolManager {

    /**
     * Method returns connection to the database from the pool. Before the connection is returned, it is tested
     * that it is not closed. If there is no available connection, new one is opened. If there was called stop method,
     * it always creates and returns new connection to the database.
     *
     * @return Opened {@link Connection} to the database.
     */
    public Connection getConnectionFromPool();

    /**
     * This method returns the connection to the pool. It checks whether the connection was closed or not.
     * If the connection was closed, it is not added to the pool.
     *
     * @param connection Used connection that is not needed any more.
     */
    public void returnConnectionToPool(Connection connection);

    /**
     * Method for stopping the poll manager. It closes all connections and stops thread
     * that is responsible for creation of the connection.
     */
    public void stop();

}
