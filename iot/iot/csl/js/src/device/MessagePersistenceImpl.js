/*
 * Copyright (c) 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

/**
 * Provides for storing and retrieving messages to a persistent store.
 */
class MessagePersistenceImpl {
    static getInstance() {
        if (!MessagePersistenceImpl.instance) {
            MessagePersistenceImpl.instance = new MessagePersistenceImpl();
        }

        return MessagePersistenceImpl.instance;
    }

    constructor() {
        if (MessagePersistenceImpl.PERSISTENCE_ENABLED) {
            this.db = new sqlite3.Database(MessagePersistenceImpl.DB_NAME, error => {
                if (error) {
                    return console.error(error.message);
                } else {
                    this.createMspsTableIfNotExists();
                }
            });
        }
    }

    /**
     * Creates the message persistent storage table if it doesn't exist.
     */
    createMspsTableIfNotExists() {
        let tableExistsSql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";

        this.db.get(tableExistsSql, [MessagePersistenceImpl.TABLE_NAME], (error, row) => {
            if (error || !row) {
                //console.log('Creating message persistent storage table.');
                let maxEndpointIdLength = 100;
                let maxUuidLength = 40;

                let createTableSql =
                    "CREATE TABLE " + MessagePersistenceImpl.TABLE_NAME +
                    "(TIMESTAMP BIGINT NOT NULL," +
                    "UUID VARCHAR(" + maxUuidLength + ") NOT NULL," +
                    "ENDPOINT_ID VARCHAR(" + maxEndpointIdLength + ") NOT NULL," +
                    "MESSAGE BLOB," +
                    " PRIMARY KEY (UUID))";

                this.db.run(createTableSql, error => {
                    if (error) {
                        console.log('Error creating table: ' + error);
                    }
                });
            }
        });
    }

    /**
     *
     * @param {Set<Message>} messages
     */
    delete(messages) {
        if (!MessagePersistenceImpl.PERSISTENCE_ENABLED) {
            return;
        }

        let stmt = '';

        // Construct multiple delete statements into one for better performance.
        messages.forEach(message => {
            stmt += MessagePersistenceImpl.DELETE + "'" + message._.internalObject.clientId + "';";
        });

        if (stmt && (stmt.length > 0)) {
            this.db.exec(stmt);
        }
    }

    /**
     * @param {string} endpointId
     * @return {Promise} - a Set<Message> a set of loaded messages.  May be an empty set if there
     *         are no messages to load.
     */
    load(endpointId) {
        return new Promise((resolve, reject) => {
            let messages = new Set();

            if (!MessagePersistenceImpl.PERSISTENCE_ENABLED) {
                resolve(messages);
                return;
            }

            this.db.all(MessagePersistenceImpl.LOAD, endpointId, (error, rows) => {
                if (error) {
                    let errorMsg = 'Table does not exist: ' + MessagePersistenceImpl.TABLE_NAME;
                    //console.log(errorMsg);
                    reject(errorMsg);
                } else {
                    rows.forEach(row => {
                        let message = new lib.message.Message();
                        message._.internalObject.clientId = row.UUID;
                        message._.internalObject.eventTime = row.TIMESTAMP;
                        message._.internalObject.source = row.ENDPOINT_ID;
                        let messageJson = JSON.parse(row.MESSAGE);

                        if (messageJson) {
                            message._.internalObject.BASIC_NUMBER_OF_RETRIES =
                                messageJson.BASIC_NUMBER_OF_RETRIES;
                            message._.internalObject.destination = messageJson.destination;
                            message._.internalObject.payload = messageJson.payload;
                            message._.internalObject.priority = messageJson.priority;
                            message._.internalObject.reliability = messageJson.reliability;
                            message._.internalObject.remainingRetries = messageJson.remainingRetries;
                            message._.internalObject.sender = messageJson.sender;
                            message._.internalObject.type = messageJson.type;
                            messages.add(message);
                        }
                    });

                    resolve(messages);
                }
            });
        });
    }

    /**
     *
     * @param {Set<Message>} messages
     * @param {string} endpointId
     */
    save(messages, endpointId) {
        if (!MessagePersistenceImpl.PERSISTENCE_ENABLED) {
            return;
        }

        messages.forEach(message => {
            this.db.serialize(function () {
                let stmt = this.prepare(MessagePersistenceImpl.SAVE);

                stmt.run(message._.internalObject.eventTime, message._.internalObject.clientId,
                    endpointId, JSON.stringify(message.getJSONObject()), function(error)
                    {
                        if (error) {
                            if (error.message &&
                                !error.message.includes('SQLITE_CONSTRAINT: UNIQUE constraint failed'))
                            {
                                console.log('Error persisting message: ' + error);
                            }
                        }

                        stmt.finalize();
                    });
            });
        });
    }
}

// Default name of the database
MessagePersistenceImpl.DB_NAME = (process.env['com.oracle.iot.messagingservice.persistent.store.dbname'], 'msps.sqlite');
MessagePersistenceImpl.TABLE_NAME = 'MESSAGE_PERSISTENT_STORE';
MessagePersistenceImpl.SAVE = 'INSERT INTO ' + MessagePersistenceImpl.TABLE_NAME + ' VALUES (?, ?, ?, ?)';
// This statement is not parmaterized.
MessagePersistenceImpl.DELETE = 'DELETE FROM ' + MessagePersistenceImpl.TABLE_NAME + ' WHERE uuid = ';
MessagePersistenceImpl.LOAD = 'SELECT * FROM ' + MessagePersistenceImpl.TABLE_NAME + ' WHERE ENDPOINT_ID = ? ORDER BY timestamp';
MessagePersistenceImpl.ENDPOINT_ID_INDEX = 'CREATE INDEX endpoint_id ON ' + MessagePersistenceImpl.TABLE_NAME + '(ENDPOINT_ID)';
MessagePersistenceImpl.PERSISTENCE_ENABLED = (process.env['com.oracle.iot.message_persistence_enabled'], 'true');
MessagePersistenceImpl.POOL_CONNECTIONS = (process.env['com.oracle.iot.message_persistence_pool_connections'], 'true');
MessagePersistenceImpl.ISOLATION_LEVEL = (process.env['com.oracle.iot.message_persistence_isoloation'], 1);
