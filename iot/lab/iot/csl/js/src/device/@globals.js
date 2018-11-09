/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * @namespace
 * @alias iotcs.device
 * @memberof iotcs
 */
lib.device = {};

//////////////////////////////////////////////////////////////////////////////

/** @ignore */
lib.oracle = lib.oracle || {};

/** @ignore */
lib.oracle.iot = lib.oracle.iot || {};

/** @ignore */
lib.oracle.iot.client = lib.oracle.iot.client || {};

/** @ignore */
lib.oracle.iot.client.httpConnectionTimeout = lib.oracle.iot.client.httpConnectionTimeout || 15000;

/** @ignore */
lib.oracle.iot.client.monitor = lib.oracle.iot.client.monitor || {};

/** @ignore */
lib.oracle.iot.client.monitor.pollingInterval = lib.oracle.iot.client.monitor.pollingInterval || 1000;

//////////////////////////////////////////////////////////////////////////////

/** @ignore */
lib.oracle.iot.client.device = lib.oracle.iot.client.device || {};

/**
 * If this is set long polling feature is disabled and global
 * monitor is used for receiving messages by the device client
 * library.
 *
 * @name iotcs․oracle․iot․client․device․disableLongPolling
 * @global
 * @type {boolean}
 * @default false
 */
lib.oracle.iot.client.device.disableLongPolling = lib.oracle.iot.client.device.disableLongPolling || false;

/**
 * Offset time (in milliseconds) added by the framework when
 * using the device client receive method with timeout parameter
 * set.
 *
 * @name iotcs․oracle․iot․client․device․longPollingTimeoutOffset
 * @global
 * @type {number}
 * @default 100
 */
lib.oracle.iot.client.device.longPollingTimeoutOffset = lib.oracle.iot.client.device.longPollingTimeoutOffset || 100;

/**
 * If this is set the device client library is allowed to
 * use draft device models when retrieving the models and
 * when activating clients. If this is not set and getDeviceModel
 * method returns a draft devices models an error will be thrown.
 *
 * @name iotcs․oracle․iot․client․device․allowDraftDeviceModels
 * @global
 * @type {boolean}
 * @default false
 */
lib.oracle.iot.client.device.allowDraftDeviceModels = lib.oracle.iot.client.device.allowDraftDeviceModels || false;

/**
 * The size of the buffer (in bytes) used to store received
 * messages by each device client.
 *
 * @name iotcs․oracle․iot․client․device․requestBufferSize
 * @global
 * @type {number}
 * @default 4192
 */
lib.oracle.iot.client.device.requestBufferSize = lib.oracle.iot.client.device.requestBufferSize || 4192;

/**
 * The MessageDispatcher queue size (in number of messages),
 * for store and forward functionality.
 *
 * @name iotcs․oracle․iot․client․device․maximumMessagesToQueue
 * @global
 * @type {number}
 * @default 1000
 */
lib.oracle.iot.client.device.maximumMessagesToQueue = lib.oracle.iot.client.device.maximumMessagesToQueue || 1000;

/**
 * The StorageDispatcher queue size (in number of storage objects),
 * for store and forward functionality.
 *
 * @name iotcs․oracle․iot․client․maximumStorageObjectsToQueue
 * @global
 * @type {number}
 * @default 50
 */
lib.oracle.iot.client.maximumStorageObjectsToQueue = lib.oracle.iot.client.maximumStorageObjectsToQueue || 50;

/**
 * The Storage Cloud server token validity period in minutes
 *
 * @name iotcs․oracle․iot․client․storageTokenPeriod
 * @global
 * @type {number}
 * @default 30
 */
lib.oracle.iot.client.storageTokenPeriod = lib.oracle.iot.client.storageTokenPeriod || 30;

/**
 * The Storage Cloud server hostname
 *
 * @name iotcs․oracle․iot․client․storageCloudHost
 * @global
 * @type {string}
 * @default "storage.oraclecloud.com"
 */
lib.oracle.iot.client.storageCloudHost = lib.oracle.iot.client.storageCloudHost || "storage.oraclecloud.com";

/**
 * The Storage Cloud server port
 *
 * @name iotcs․oracle․iot․client․storageCloudPort
 * @global
 * @type {number}
 * @default 443
 */
lib.oracle.iot.client.storageCloudPort = lib.oracle.iot.client.storageCloudPort || 443;

/**
 * The maximum number of messages sent by the MessagesDispatcher
 * in one request.
 *
 * @name iotcs․oracle․iot․client․device․maximumMessagesPerConnection
 * @global
 * @type {number}
 * @default 100
 */
lib.oracle.iot.client.device.maximumMessagesPerConnection = lib.oracle.iot.client.device.maximumMessagesPerConnection || 100;

/**
 * The actual polling interval (in milliseconds) used by the
 * MessageDispatcher for sending/receiving messages. If this is
 * lower than iotcs․oracle․iot․client․monitor․pollingInterval than
 * then that variable will be used as polling interval.
 * <br>
 * This is not used for receiving messages when
 * iotcs․oracle․iot․client․device․disableLongPolling is
 * set to false.
 *
 * @name iotcs․oracle․iot․client․device․defaultMessagePoolingInterval
 * @global
 * @type {number}
 * @default 3000
 */
lib.oracle.iot.client.device.defaultMessagePoolingInterval = lib.oracle.iot.client.device.defaultMessagePoolingInterval || 3000;

//////////////////////////////////////////////////////////////////////////////

/** @ignore */
lib.oracle.iot.tam = lib.oracle.iot.tam || {};

/** @ignore */
lib.oracle.iot.tam.store = lib.oracle.iot.tam.store || './trustedAssetsStore.json';

/** @ignore */
lib.oracle.iot.tam.storePassword = lib.oracle.iot.tam.storePassword || null;
