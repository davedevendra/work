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
 * @alias iotcs.enterprise
 * @memberof iotcs
 */
lib.enterprise = {};

//////////////////////////////////////////////////////////////////////////////

/** @ignore */
lib.oracle = lib.oracle || {};

/** @ignore */
lib.oracle.iot = lib.oracle.iot || {};

/** @ignore */
lib.oracle.iot.client = lib.oracle.iot.client || {};

//////////////////////////////////////////////////////////////////////////////

/** @ignore */
lib.oracle.iot.client.pageable = lib.oracle.iot.client.pageable || {};

/**
 * Default limit of items retrieved for each page when using
 * Pageable functionality
 *
 * @name iotcs․oracle․iot․client․pageable․defaultLimit
 * @global
 * @type {number}
 * @default 50
 */
lib.oracle.iot.client.pageable.defaultLimit = lib.oracle.iot.client.pageable.defaultLimit || 50;

//////////////////////////////////////////////////////////////////////////////

/**
 * Default timeout (in milliseconds) used when doing http/https requests.
 * This can be overridden in certain contexts, like when
 * using long polling feature.
 *
 * @name iotcs․oracle․iot․client․httpConnectionTimeout
 * @global
 * @type {number}
 * @default 15000
 */
lib.oracle.iot.client.httpConnectionTimeout = lib.oracle.iot.client.httpConnectionTimeout || 15000;

/** @ignore */
lib.oracle.iot.client.monitor = lib.oracle.iot.client.monitor || {};

/**
 * The time interval (in milliseconds) used by the
 * monitor (JS interval), used as the global thread pool
 * of the iotcs client library.
 * <br>
 * In the enterprise client library this is the actual
 * polling interval used for virtual device monitoring,
 * message monitoring and async request response monitoring.
 * <br>
 * In the device client library this is the minimum polling
 * interval used by the MessageDispatcher for sending/receiving
 * messages.
 *
 * @name iotcs․oracle․iot․client․monitor․pollingInterval
 * @global
 * @type {number}
 * @default device: 1000, enterprise: 3000
 */
lib.oracle.iot.client.monitor.pollingInterval = lib.oracle.iot.client.monitor.pollingInterval || 3000;

/**
 * The maximum number of alerts/custom formats retrieved
 * by the enterprise client when doing monitoring of
 * virtual devices.
 *
 * @name iotcs․oracle․iot․client․monitor․formatLimit
 * @global
 * @type {number}
 * @default 10
 */
lib.oracle.iot.client.monitor.formatLimit = lib.oracle.iot.client.monitor.formatLimit || 10;

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

//////////////////////////////////////////////////////////////////////////////

/** @ignore */
lib.oracle.iot.client.controller = lib.oracle.iot.client.controller || {};

/**
 * The maximum time (in milliseconds) the enterprise client
 * will wait for a response from any async request made to
 * a device via the cloud service. These include virtual device
 * attribute updates, actions and also resource invocations.
 *
 * @name iotcs․oracle․iot․client․controller․asyncRequestTimeout
 * @global
 * @type {number}
 * @default 60000
 */
lib.oracle.iot.client.controller.asyncRequestTimeout = lib.oracle.iot.client.controller.asyncRequestTimeout || 60000;

//////////////////////////////////////////////////////////////////////////////

/** @ignore */
lib.oracle.iot.tam = lib.oracle.iot.tam || {};

/**
 * The trusted assets store file path used as global
 * configuration when initializing clients without
 * the trusted assets manager specific parameters.
 * This is required in browser environment.
 *
 * @name iotcs․oracle․iot․tam․store
 * @global
 * @type {string}
 * @default 'trustedAssetsStore.json'
 */
lib.oracle.iot.tam.store = lib.oracle.iot.tam.store || 'trustedAssetsStore.json';

/**
 * The trusted assets store password used as global
 * configuration when initializing clients without
 * the trusted assets manager specific parameters.
 * This is required in browser environment.
 *
 * @name iotcs․oracle․iot․tam․storePassword
 * @global
 * @type {string}
 * @default null
 */
lib.oracle.iot.tam.storePassword = lib.oracle.iot.tam.storePassword || null;

/**
 * Config variable used by the enterprise library only in browser
 * environment to get the iotcs server host and port instead of the
 * trusted assets manager. If this is not set, the default trusted
 * assets manager is used.
 *
 * @name iotcs․oracle․iot․client․serverUrl
 * @global
 * @type {string}
 * @default null
 */
lib.oracle.iot.client.serverUrl = lib.oracle.iot.client.serverUrl || null;

//////////////////////////////////////////////////////////////////////////////

/** @ignore */
lib.oracle.iot.client.test = lib.oracle.iot.client.test || {};

/** @ignore */
lib.oracle.iot.client.test.reqroot = lib.oracle.iot.client.test.reqroot || '/iot/webapi/v2';

/** @ignore */
lib.oracle.iot.client.test.auth = lib.oracle.iot.client.test.auth || {};

/** @ignore */
lib.oracle.iot.client.test.auth.activated = lib.oracle.iot.client.test.auth.activated || false;

/** @ignore */
lib.oracle.iot.client.test.auth.user = lib.oracle.iot.client.test.auth.user || 'iot';

/** @ignore */
lib.oracle.iot.client.test.auth.password = lib.oracle.iot.client.test.auth.password || 'welcome1';

/** @ignore */
lib.oracle.iot.client.test.auth.protocol = lib.oracle.iot.client.test.auth.protocol || 'https';

//////////////////////////////////////////////////////////////////////////////
