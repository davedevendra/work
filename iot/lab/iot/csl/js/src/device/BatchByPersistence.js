/*
 * Copyright (c) 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

/**
 * SQL database persistence for 'batchBy' policy data. The data consists of the message and storage object, if
 * the message is related to a storage object.
 */
class BatchByPersistence {
    // Instance "variables"/properties...see constructor.

    /**
     *
     */
    constructor() {
        // Instance "variables"/properties.
        // Instance "variables"/properties.
        this.db = new sqlite3.Database(BatchByPersistence.DB_NAME, error => {
            if (error) {
                return console.error(error.message);
            } else {
                this.createBatchByTableIfNotExists();
            }
        });
    }

    /**
     * Creates the message persistent storage table if it doesn't exist.
     */
    createBatchByTableIfNotExists() {
        let tableExistsSql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";

        this.db.get(tableExistsSql, [BatchByPersistence.TABLE_NAME], (error, row) => {
            if (error || !row) {
                console.log('Creating batch by storage table.');
                let maxUuidLength = 40;

                let createTableSql =
                    'CREATE TABLE ' + BatchByPersistence.TABLE_NAME + ' (' +
                    'TIMESTAMP BIGINT NOT NULL, ' +
                    'ENDPOINT_ID VARCHAR(' + maxUuidLength + ') NOT NULL, ' +
                    'MESSAGE_ID VARCHAR(' + maxUuidLength + ') NOT NULL, ' +
                    'MESSAGE BLOB, ' +
                    'PRIMARY KEY (MESSAGE_ID))';

                    let createTableIndex = 'CREATE INDEX endpoint_id ON ' +
                        BatchByPersistence.TABLE_NAME +
                        '(ENDPOINT_ID)';

                this.db.run(createTableSql, error => {
                    if (error) {
                        console.log('Error creating table: ' + error);
                    }
                });
            }
        });
    }

    /**
     * @param {Set<Message>}
     * @return {boolean}
     */
    delete(messages) {
        let stmt = null;

        // Construct multiple delete statements into one for better performance.
        messages.forEach(message => {
            stmt += BatchByPersistence.DELETE + "'" + message._.internalObject.clientId + "'";
        });

        if (stmt && (stmt.length > 0)) {
            this.db.exec(stmt);
        }
    }

    /**
     * @param {string} endpointId
     * @return {Set<Message>}
     */
    get(endpointId) {
        return new Promise((resolve, reject) => {
            let messages = new Set();

            this.db.all(BatchByPersistence.GET, endpointId, (error, rows) => {
                if (error) {
                    console.log('Table does not exist: ' + BatchByPersistence.TABLE_NAME);
                    reject();
                } else {
                    rows.forEach(row => {
                        let message = new lib.message.Message();
                        message._.internalObject.clientId = row.MESSAGE_ID;
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
        messages.forEach(message => {
            this.db.serialize(function () {
                let stmt = this.prepare(BatchByPersistence.SAVE);

                stmt.run(message._.internalObject.eventTime, endpointId,
                    message._.internalObject.clientId, JSON.stringify(message.getJSONObject()),
                    function(error)
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

BatchByPersistence.DB_NAME = 'batch_by.sqlite';
BatchByPersistence.TABLE_NAME = "BATCH_BY";
BatchByPersistence.DELETE = 'DELETE FROM ' + BatchByPersistence.TABLE_NAME + ' WHERE MESSAGE_ID = ';
BatchByPersistence.GET = "SELECT * FROM " + BatchByPersistence.TABLE_NAME +
    " WHERE ENDPOINT_ID = ? ORDER BY TIMESTAMP";
BatchByPersistence.SAVE = 'INSERT INTO ' + BatchByPersistence.TABLE_NAME + ' VALUES (?, ?, ?, ?)';

BatchByPersistence.TIMESTAMP_COLUMN_INDEX = 1;
BatchByPersistence.ENDPOINT_ID_COLUMN_INDEX = 2;
BatchByPersistence.MESSAGE_ID_COLUMN_INDEX = 3;
BatchByPersistence.MESSAGE_BLOB_COLUMN_INDEX = 4;
