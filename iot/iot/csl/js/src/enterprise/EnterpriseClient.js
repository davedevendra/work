/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * EnterpriseClient is a enterprise application which is a client of the Oracle IoT Cloud Service.
 * <p>
 * This function is meant to be used for constructing EnterpriseClient objects only when the actual
 * ID of the application associated with the object is known.  An actual validation of the
 * application ID with the cloud is not made at construction and if the application ID is incorrect,
 * a NOT FOUND error from the cloud will be given when the object is actually used (e.g. when
 * calling {@link iotcs.enterprise.EnterpriseClient#getDevices}).
 * <p>
 * If the actual application ID is not known is is better to use the
 * {@link iotcs.enterprise.EnterpriseClient#newClient} method for creating EnterpriseClient objects,
 * an asynchronous method that will first make a request at the cloud server for validation and
 * then pass in the callback the validated object.  This will ensure that no NOT FOUND error is
 * given at first usage of the object.
 *
 * @alias iotcs.enterprise.EnterpriseClient
 * @class
 * @extends iotcs.Client
 * @memberof iotcs.enterprise
 * @see {@link iotcs.enterprise.EnterpriseClient.newClient}
 *
 * @param {string} appid - The application identifier as it is in the cloud.  This is the actual
 *        application ID generated by the server when creating a new application from the cloud UI.
 *        It is different than the integration ID or application mane.
 * @param {string} [taStoreFile] - The trusted assets store file path to be used for trusted assets
 *        manager creation.  This is optional.  If none is given the default global library
 *        parameter is used: lib.oracle.iot.tam.store.  This is used only in the context of endpoint
 *        authentication.
 * @param {string} [taStorePassword] - The trusted assets store file password to be used for trusted
 *        assets manager creation.  This is optional.  If none is given, the default global library
 *        parameter is used: lib.oracle.iot.tam.storePassword.  This is used only in the context of
 *        endpoint authentication.
 */
lib.enterprise.EnterpriseClient = function (appid, taStoreFile, taStorePassword) {

    _mandatoryArg(appid, 'string');
    _optionalArg(taStoreFile, 'string');
    _optionalArg(taStorePassword, 'string');

    lib.Client.call(this);

    if(appid.indexOf('/') > -1){
        lib.error('invalid app id parameter given');
        return;
    }

    this.cache = this.cache || {};

    Object.defineProperty(this, '_',{
        enumerable: false,
        configurable: false,
        writable: false,
        value: {}
    });

    if (!$port.userAuthNeeded()) {
        var internal = new $impl.EnterpriseClientImpl(taStoreFile, taStorePassword);
        if (internal && internal._.tam && internal._.tam.getClientId()) {
            Object.defineProperty(this._, 'internalClient', {
                enumerable: false,
                configurable: false,
                writable: false,
                value: internal
            });
        }
    }

    Object.defineProperty(this, 'appid',{
        enumerable: true,
        configurable: false,
        writable: false,
        value: appid
    });

    Object.defineProperty(this._, 'bulkMonitorInProgress',{
        enumerable: false,
        configurable: false,
        writable: true,
        value: false
    });

    Object.defineProperty(this._, 'lastUntil',{
        enumerable: false,
        configurable: false,
        writable: true,
        value: null
    });

    Object.defineProperty(this._, 'virtualDevices',{
        enumerable: false,
        configurable: false,
        writable: false,
        value: {}
    });

    var self = this;

    Object.defineProperty(this._, 'removeVirtualDevice',{
        enumerable: false,
        configurable: false,
        writable: false,
        value: function(device) {
            if (self._.virtualDevices[device.getEndpointId()]) {
                if (self._.virtualDevices[device.getEndpointId()][device.getDeviceModel().urn]) {
                    delete self._.virtualDevices[device.getEndpointId()][device.getDeviceModel().urn];
                }
                if (Object.keys(self._.virtualDevices[device.getEndpointId()]).length === 0) {
                    delete self._.virtualDevices[device.getEndpointId()];
                }
            }
        }
    });

    Object.defineProperty(this._, 'addVirtualDevice',{
        enumerable: false,
        configurable: false,
        writable: false,
        value: function(device){
            self._.removeVirtualDevice(device);
            if (!self._.virtualDevices[device.getEndpointId()]) {
                self._.virtualDevices[device.getEndpointId()] = {};
            }
            self._.virtualDevices[device.getEndpointId()][device.getDeviceModel().urn] = device;
        }
    });

    if (!$port.userAuthNeeded()) {
        Object.defineProperty(this._, 'isStorageAuthenticated', {
            enumerable: false,
            configurable: false,
            writable: false,
            value: function () {
                return internal._.storageContainerUrl && internal._.storage_authToken;
            }
        });

        Object.defineProperty(this._, 'isStorageTokenExpired', {
            enumerable: false,
            configurable: false,
            writable: false,
            value: function () {
                // period in minutes recalculated in milliseconds
                return ((internal._.storage_authTokenStartTime + lib.oracle.iot.client.storageTokenPeriod * 60000) < Date.now());
            }
        });

        Object.defineProperty(this._, 'sync_storage', {
            enumerable: false,
            configurable: false,
            writable: false,
            value: function (storage, deliveryCallback, errorCallback, processCallback, timeout) {
                if (!self._.isStorageAuthenticated() || self._.isStorageTokenExpired()) {
                    internal._.refresh_storage_authToken(function() {
                        self._.sync_storage(storage, deliveryCallback, errorCallback, processCallback, timeout);
                    });
                    return;
                }
                var urlObj = require('url').parse(storage.getURI(), true);
                var options = {
                    path: urlObj.path,
                    host: urlObj.host,
                    hostname: urlObj.hostname,
                    port: urlObj.port || lib.oracle.iot.client.storageCloudPort,
                    protocol: urlObj.protocol.slice(0, -1),
                    headers: {
                        'X-Auth-Token': internal._.storage_authToken
                    }
                };

                if (storage.getInputStream()) {
                    // Upload file
                    options.method = "PUT";
                    options.headers['Transfer-Encoding'] = "chunked";
                    options.headers['Content-Type'] = storage.getType();
                    var encoding = storage.getEncoding();
                    if (encoding) options.headers['Content-Encoding'] = encoding;
                } else {
                    // Download file
                    options.method = "GET";
                }

                $port.https.storageReq(options, storage, deliveryCallback, function(error) {
                    if (error) {
                        var exception = null;
                        try {
                            exception = JSON.parse(error.message);
                            if (exception.statusCode && (exception.statusCode === 401)) {
                                internal._.refresh_storage_authToken(function () {
                                    self._.sync_storage(storage, deliveryCallback, errorCallback, processCallback, timeout);
                                });
                                return;
                            }
                        } catch (e) {}
                        errorCallback(storage, error, -1);
                    }
                }, processCallback);
            }
        });

        Object.defineProperty(this._, 'createStorageObject', {
            enumerable: false,
            configurable: false,
            writable: false,
            value: function (arg1, arg2) {
                _mandatoryArg(arg1, "string");
                if ((typeof arg2 === "string") || (typeof arg2 === "undefined") || arg2 === null) {
                    // createStorageObject(name, type)
                    return new lib.StorageObject(null, arg1, arg2);
                } else {
                    // createStorageObject(uri, callback)
                    _mandatoryArg(arg2, "function");
                    var url = arg1;
                    var callback = arg2;
                    if (!self._.isStorageAuthenticated() || self._.isStorageTokenExpired()) {
                        internal._.refresh_storage_authToken(function() {
                            self._.createStorageObject(url, callback);
                        });
                    } else {
                        var fullContainerUrl = internal._.storageContainerUrl + "/";
                        // url starts with fullContainerUrl
                        if (url.indexOf(fullContainerUrl) !== 0) {
                            callback(null, new Error("Storage Cloud URL is invalid."));
                            return;
                        }
                        var name = url.substring(fullContainerUrl.length);
                        var urlObj = require('url').parse(url, true);
                        var options = {
                            path: urlObj.path,
                            host: urlObj.host,
                            hostname: urlObj.hostname,
                            port: urlObj.port || lib.oracle.iot.client.storageCloudPort,
                            protocol: urlObj.protocol,
                            method: "HEAD",
                            headers: {
                                'X-Auth-Token': internal._.storage_authToken
                            },
                            rejectUnauthorized: true,
                            agent: false
                        };
                        var req = require('https').request(options, function (response) {
                            var body = '';
                            response.on('data', function (d) {
                                body += d;
                            });
                            response.on('end', function () {
                                if (response.statusCode === 200) {
                                    var type = response.headers["content-type"];
                                    var encoding = response.headers["content-encoding"];
                                    var date = new Date(Date.parse(response.headers["last-modified"]));
                                    var len = parseInt(response.headers["content-length"]);
                                    var storage = new lib.StorageObject(url, name, type, encoding, date, len);
                                    callback(storage);
                                } else if (response.statusCode === 401) {
                                    internal._.refresh_storage_authToken(function () {
                                        self._.createStorageObject(url, callback);
                                    });
                                } else {
                                    var e = new Error(JSON.stringify({
                                        statusCode: response.statusCode,
                                        statusMessage: (response.statusMessage ? response.statusMessage : null),
                                        body: body
                                    }));
                                    callback(null, e);
                                }
                            });
                        });
                        req.on('timeout', function () {
                            callback(null, new Error('connection timeout'));
                        });
                        req.on('error', function (error) {
                            callback(null, error);
                        });
                        req.end();
                    }
                }
            }
        });

        var storageHandler = function (progress, error) {
            var storage = progress.getStorageObject();
            if (error) {
                if (storage._.deviceForSync && storage._.deviceForSync.onError) {
                    var tryValues = {};
                    tryValues[storage._.nameForSyncEvent] = storage.getURI();
                    var onDeviceErrorTuple = {
                        newValues: tryValues,
                        tryValues: tryValues,
                        errorResponse: error
                    };
                    storage._.deviceForSync.onError(onDeviceErrorTuple);
                }
                return;
            }
            if (storage) {
                var state = progress.getState();
                var oldSyncStatus = storage.getSyncStatus();
                switch (state) {
                    case lib.StorageDispatcher.Progress.State.COMPLETED:
                        storage._.internal.syncStatus = lib.enterprise.StorageObject.SyncStatus.IN_SYNC;
                        break;
                    case lib.StorageDispatcher.Progress.State.CANCELLED:
                    case lib.StorageDispatcher.Progress.State.FAILED:
                        storage._.internal.syncStatus = lib.enterprise.StorageObject.SyncStatus.SYNC_FAILED;
                        break;
                    case lib.StorageDispatcher.Progress.State.IN_PROGRESS:
                    case lib.StorageDispatcher.Progress.State.INITIATED:
                    case lib.StorageDispatcher.Progress.State.QUEUED:
                    // do nothing
                }
                if (oldSyncStatus !== storage.getSyncStatus()) {
                    if (storage._.onSync) {
                        var syncEvent;
                        while ((syncEvent = storage._.internal.syncEvents.pop()) !== null) {
                            storage._.onSync(syncEvent);
                        }
                    }
                }
            }
        };
        new lib.enterprise.StorageDispatcher(this).onProgress = storageHandler;
    }

    this.monitor = new $impl.Monitor(function(){
        _remoteBulkMonitor(self);
    });
    this.monitor.start();
};

lib.enterprise.EnterpriseClient.prototype = Object.create(lib.Client.prototype);
lib.enterprise.EnterpriseClient.constructor = lib.enterprise.EnterpriseClient;

/**
 * Creates an enterprise client based on the application name.
 *
 * @function newClient
 * @memberof iotcs.enterprise.EnterpriseClient
 * @see {@link iotcs.enterprise.EnterpriseClient}
 *
 * @param {string} appName - The application name as it is on the cloud server.
 * @param {function} callback - The callback function.  This function is called with an object as
 *        parameter that is a created and initialized instance of an EnterpriseClient with the
 *        application endpoint id associated with the application name given as parameter.  If the
 *        client creation fails the client object will be <code>null</code> and an error object is
 *        passed as the second parameter in the callback: callback(client, error) where the reason
 *        is in error.message.
 * @param {string} [taStoreFile] - The trusted assets store file path to be used for trusted assets
 *        manager creation.  This is optional.  If none is given the default global library
 *        parameter is used: lib.oracle.iot.tam.store.  Also this is used only in the context of
 *        endpoint authentication.
 * @param {string} [taStorePassword] - The trusted assets store file password to be used for trusted
 *        assets manager creation.  This is optional.  If none is given the default global library
 *        parameter is used: lib.oracle.iot.tam.storePassword.  Also this is used only in the
 *        context of endpoint authentication.
 */
lib.enterprise.EnterpriseClient.newClient = function (appName, callback, taStoreFile, taStorePassword) {

    switch (arguments.length) {
        case 0:
            break;
        case 1:
            callback = appName;
            break;
        case 2:
            _mandatoryArg(appName, 'string');
            break;
        case 3:
            callback = arguments[0];
            taStoreFile = arguments[1];
            taStorePassword = arguments[2];
            appName = null;
            break;
    }

    _mandatoryArg(callback, 'function');
    _optionalArg(taStoreFile, 'string');
    _optionalArg(taStorePassword, 'string');

    var client = null;
    var f = null;

    if (!$port.userAuthNeeded()) {
        client = new lib.enterprise.EnterpriseClient('none', taStoreFile, taStorePassword);
    }
    if (client && client._.internalClient._.tam.getClientId()) {
        f = (new lib.enterprise.Filter()).eq('integrations.id', client._.internalClient._.tam.getClientId());
    } else {
        f = (new lib.enterprise.Filter()).eq('name', appName);
    }

    var request = null;

    request = function () {
        $impl.https.bearerReq({
            method: 'GET',
            path: $impl.reqroot
            + '/apps'
            + (f ? ('?q=' + f.toString()) : '')
        }, '', function (response, error) {
            if ((!response) || error
                || (!response.items)
                || (!Array.isArray(response.items))
                || (response.items.length !== 1)
                || (!response.items[0].id)) {
                if (typeof callback === 'function')
                    callback(null, lib.createError('invalid response on client creation request', error));
                return;
            }
            try {
                if (appName && (response.items[0].name !== appName)) {
                    if (typeof callback === 'function')
                        callback(null, lib.createError('application name does not match the name parameter'));
                    return;
                }
                if (client) {
                    client.close();
                }
                client = new lib.enterprise.EnterpriseClient(response.items[0].id, taStoreFile, taStorePassword);
                if (typeof callback === 'function') {
                    callback(client);
                }
            } catch (e) {
                if (typeof callback === 'function')
                    callback(null, lib.createError('invalid response on client creation request', e));
            }
        }, request, (client ? client._.internalClient : null));
    };
    request();
};

/**
 * Get the all the applications that the user has access to.
 *
 * @returns {iotcs.enterprise.Pageable} A pageable instance with
 * which pages can be requested that contain application info
 * objects as items
 *
 * @memberof iotcs.enterprise.EnterpriseClient
 * @function getApplications
 */
lib.enterprise.EnterpriseClient.getApplications = function () {

    if (!$port.userAuthNeeded()) {
        lib.error('invalid usage; user authentication framework needed');
        return null;
    }

    return new lib.enterprise.Pageable({
        method: 'GET',
        path:   $impl.reqroot
        + '/apps'
    }, '', null, null);

};

/**
 * Create a VirtualDevice instance with the given device model
 * for the given device identifier. This method creates a new
 * VirtualDevice instance for the given parameters. The client
 * library does not cache previously created VirtualDevice
 * objects.
 * <p>
 * A device model can be obtained by it's afferent urn with the
 * EnterpriseClient if it is registered on the cloud.
 *
 * @param {string} endpointId - The endpoint identifier of the
 * device being modeled.
 * @param {object} deviceModel - The device model object
 * holding the full description of that device model that this
 * device implements.
 * @returns {iotcs.enterprise.VirtualDevice} The newly created virtual device
 *
 * @see {@link iotcs.enterprise.EnterpriseClient#getDeviceModel}
 * @memberof iotcs.enterprise.EnterpriseClient.prototype
 * @function createVirtualDevice
 */
lib.enterprise.EnterpriseClient.prototype.createVirtualDevice = function (endpointId, deviceModel) {
    _mandatoryArg(endpointId, 'string');
    _mandatoryArg(deviceModel, 'object');
    return new lib.enterprise.VirtualDevice(endpointId, deviceModel, this);
};

/**
 * Get the application information that this enterprise client is associated with.
 *
 * @param {function} callback - The callback function. This function is called with the following argument:
 * an appinfo object holding all data and metadata associated to that appid e.g.
 * <code>{ id:"", name:"", description:"", metadata: { key1:"value1", key2:"value2", ... } }</code>.
 * If an error occurs or the response is invalid an error object is passed in callback
 * as the second parameter with the reason in error.message: callback(response, error)
 *
 * @memberof iotcs.enterprise.EnterpriseClient.prototype
 * @function getApplication
 */
lib.enterprise.EnterpriseClient.prototype.getApplication = function (callback) {
    _mandatoryArg(callback, 'function');

    var self = this;

    $impl.https.bearerReq({
        method: 'GET',
        path:   $impl.reqroot
            + '/apps/' + this.appid
    }, '', function(response, error) {
        if(!response || error || !response.id){
            callback(null, lib.createError('invalid response on application request', error));
            return;
        }
        var appinfo = response;
        Object.freeze(appinfo);
        callback(appinfo);
    }, function () {
        self.getApplication(callback);
    }, self._.internalClient);
};

/**
 * Get the device models associated with the application of
 * this enterprise client.
 *
 * @returns {iotcs.enterprise.Pageable} A pageable instance with
 * which pages can be requested that contain device models
 * associated with the application as items. An item can be used
 * to create VirtualDevices.
 *
 * @memberof iotcs.enterprise.EnterpriseClient.prototype
 * @function getDeviceModels
 */
lib.enterprise.EnterpriseClient.prototype.getDeviceModels = function () {
    return new lib.enterprise.Pageable({
        method: 'GET',
        path:   $impl.reqroot
            + '/apps/' + this.appid
            + '/deviceModels'
    }, '', null, this);
};

/**@inheritdoc*/
lib.enterprise.EnterpriseClient.prototype.getDeviceModel = function (deviceModelUrn, callback) {
    _mandatoryArg(deviceModelUrn, 'string');
    _mandatoryArg(callback, 'function');

    var deviceModel = this.cache.deviceModels[deviceModelUrn];
    if (deviceModel) {
        callback(deviceModel);
        return;
    }

    var f = (new lib.enterprise.Filter()).eq('urn', deviceModelUrn);
    var self = this;
    $impl.https.bearerReq({
        method: 'GET',
        path:   $impl.reqroot
            + '/apps/' + this.appid
            + '/deviceModels'
            + '?q=' + f.toString()
    }, '', function (response, error) {
        if((!response) || error
           || (!response.items)
           || (!Array.isArray(response.items))
           || (response.items.length !== 1)) {
            callback(null, lib.createError('invalid response on get device model request', error));
            return;
        }
        var deviceModel = response.items[0];
        Object.freeze(deviceModel);
        self.cache.deviceModels[deviceModelUrn] = deviceModel;
        callback(deviceModel);
    }, function () {
        self.getDeviceModel(deviceModelUrn, callback);
    }, self._.internalClient);
};

/**
 * Get the list of all active devices implementing the
 * specified device model and application of the client.
 *
 * @param {string} deviceModelUrn - The device model expected.
 * @returns {iotcs.enterprise.Pageable} A pageable instance with
 * which pages can be requested that contain devices as items.
 * A standard device item would have the "id" property that can
 * be used as endpoint id for creating virtual devices.
 *
 * @memberof iotcs.enterprise.EnterpriseClient.prototype
 * @function getActiveDevices
 */
lib.enterprise.EnterpriseClient.prototype.getActiveDevices = function (deviceModelUrn) {
    _mandatoryArg(deviceModelUrn, 'string');
    var f = new lib.enterprise.Filter();
    f = f.and([f.eq('deviceModels.urn', deviceModelUrn), f.eq('connectivityStatus', 'ONLINE'), f.eq('state','ACTIVATED')]);
    return this.getDevices(f, null);
};


//@TODO: (JY) simplify query builder ...

/**
 * Return a list of Devices associated with the application of the client.  The returned fields are
 * limited to the fields defined in fields. Filters forms a query.  Only endpoints that satisfy all
 * the statements in filters are returned.
 *
 * @param {iotcs.enterprise.Filter} filter - A filter as generated by the Filter class.
 * @param {string[]} [fields] - Array of fields for the selected endpoint. Can be null.
 * @returns {iotcs.enterprise.Pageable} A pageable instance with which pages can be requested that
 *          contain devices as items
 *
 * @memberof iotcs.enterprise.EnterpriseClient.prototype
 * @function getDevices
 */
lib.enterprise.EnterpriseClient.prototype.getDevices = function (filter, fields) {
    _mandatoryArg(filter, lib.enterprise.Filter);
    _optionalArg(fields, 'array');

    var query = '?q=' + filter.toString();
    if (fields) {
        query += '&fields=' + fields.toString();
    }
    query = query + '&includeDecommissioned=false&expand=location,metadata';

    return new lib.enterprise.Pageable({
        method: 'GET',
        path:   $impl.reqroot
            + '/apps/' + this.appid
            + '/devices'
            + query
    }, '', null, this);
};

/**
 * Closes the resources used by this Client.
 * This will close all the virtual devices
 * created and associated with this enterprise
 * client.
 *
 * @see {@link iotcs.AbstractVirtualDevice#close}
 * @memberof iotcs.enterprise.EnterpriseClient.prototype
 * @function close
 */
lib.enterprise.EnterpriseClient.prototype.close = function () {
    this.monitor.stop();
    this.cache.deviceModels = {};
    for (var key in this._.virtualDevices) {
        for (var key1 in this._.virtualDevices[key]) {
            this._.virtualDevices[key][key1].close();
        }
    }
};

/**
 * Create a new {@link iotcs.enterprise.StorageObject}.
 *
 * <p>
 * createStorageObject method works in two modes:
 * </p><p>
 * 1. client.createStorageObject(name, type) -
 * Create a new {@link iotcs.enterprise.StorageObject} with given object name and mime&ndash;type.
 * </p><pre>
 * Parameters:
 * {string} name - the unique name to be used to reference the content in storage
 * {?string} [type] - The mime-type of the content. If <code>type</code> is <code>null</code> or omitted,
 * the mime&ndash;type defaults to {@link iotcs.StorageObject.MIME_TYPE}.
 *
 * Returns:
 * {iotcs.enterprise.StorageObject} StorageObject
 * </pre><p>
 * 2. client.createStorageObject(uri, callback) -
 * Create a new {@link iotcs.enterprise.StorageObject} from the URL for a named object in storage and return it in a callback.
 * Create a new {@link iotcs.ExternalObject} if used external URI.
 * </p><pre>
 * Parameters:
 * {string} url - the URL of the object in the storage cloud
 * {function(storage, error)} callback - callback called once the getting storage data completes.
 * </pre>
 *
 * @param {string} arg1 - first argument
 * @param {string | function} arg2 - second argument
 *
 * @see {@link http://www.iana.org/assignments/media-types/media-types.xhtml|IANA Media Types}
 * @memberof iotcs.enterprise.EnterpriseClient.prototype
 * @function createStorageObject
 */
lib.enterprise.EnterpriseClient.prototype.createStorageObject = function (arg1, arg2) {
    _mandatoryArg(arg1, "string");
    var self = this;
    if ((typeof arg2 === "string") || (typeof arg2 === "undefined") || arg2 === null) {
        // createStorageObject(name, type)
        var storage = new lib.enterprise.StorageObject(null, arg1, arg2);
        storage._.setDevice(self);
        return storage;
    } else {
        // createStorageObject(uri, callback)
        _mandatoryArg(arg2, "function");
        var url = arg1;
        var callback = arg2;
        if (_isStorageCloudURI(url)) {
            this._.createStorageObject(url, function (storage, error) {
                if (error) {
                    callback(null, error);
                    return;
                }
                var storageObject = new lib.enterprise.StorageObject(storage.getURI(), storage.getName(),
                    storage.getType(), storage.getEncoding(), storage.getDate(), storage.getLength());
                storageObject._.setDevice(self);
                callback(storageObject);
            });
        } else {
            callback(new lib.ExternalObject(url));
        }
    }
};

/** @ignore */
function _deviceMonitorInitialization(virtualDevice) {

    var deviceId = virtualDevice.getEndpointId();
    var urn = virtualDevice.getDeviceModel().urn;

    var postData = {};
    postData[deviceId] = [urn];

    if (!virtualDevice.client._.lastUntil) {
        virtualDevice.client._.lastUntil = Date.now()-lib.oracle.iot.client.monitor.pollingInterval;
    }

    $impl.https.bearerReq({
        method: 'POST',
        path:   $impl.reqroot
        + (virtualDevice.client.appid ? ('/apps/' + virtualDevice.client.appid) : '')
        + '/devices/data'
        + '?formatLimit=' + lib.oracle.iot.client.monitor.formatLimit
        + '&formatSince=' + virtualDevice.client._.lastUntil
    }, JSON.stringify(postData), function (response, error) {

        if (!response || error || !response.data || !response.until
            || !(deviceId in response.data)
            || !(urn in response.data[deviceId])) {
            lib.createError('Invalid response on device initialization data.');
        } else {
            virtualDevice.client._.lastUntil = response.until;
            _processMonitorData(response.data, virtualDevice);
        }

        virtualDevice.client._.addVirtualDevice(virtualDevice);

    }, function () {
        _deviceMonitorInitialization(virtualDevice);
    }, virtualDevice.client._.internalClient);

}

//////////////////////////////////////////////////////////////////////////////

/** @ignore */
function _remoteBulkMonitor(client) {

    if (client._.bulkMonitorInProgress) {
        return;
    }

    client._.bulkMonitorInProgress = true;

    if (!client._.virtualDevices) {
        client._.bulkMonitorInProgress = false;
        return;
    }

    var devices = client._.virtualDevices;
    var postData = {};

    for (var devId in devices) {
        var deviceModels = devices[devId];
        postData[devId] = [];
        for (var urn in deviceModels) {
            postData[devId].push(urn);
        }
    }

    if (Object.keys(postData).length > 0) {
        $impl.https.bearerReq({
            method: 'POST',
            path:   $impl.reqroot
            + (client.appid ? ('/apps/' + client.appid) : '')
            + '/devices/data'
            + '?since=' + client._.lastUntil
            + '&formatLimit=' + lib.oracle.iot.client.monitor.formatLimit
        }, JSON.stringify(postData), function (response, error) {
            client._.bulkMonitorInProgress = false;
            if (!response || error || !response.until || !response.data) {
                lib.createError('invalid response on monitoring');
                return;
            }
            client._.lastUntil = response.until;
            var data = response.data;
            for (var devId in data) {
                for (var urn in data[devId]){
                    if (devices[devId] && devices[devId][urn]) {
                        _processMonitorData(data, devices[devId][urn]);
                    }
                }
            }
        }, function () {
            _remoteBulkMonitor(client);
        }, client._.internalClient);
    } else {
        client._.bulkMonitorInProgress = false;
    }

}

/** @ignore */
function _processMonitorData(data, virtualDevice) {
    var deviceId = virtualDevice.getEndpointId();
    var urn = virtualDevice.getDeviceModel().urn;
    var onChangeArray = [];
    if (data[deviceId][urn].attributes) {
        var attributesIndex = 0;
        var attributes = data[deviceId][urn].attributes;
        var attributeCallback = function (attributeValue) {
            var onChangeTuple = {
                attribute: attribute,
                newValue: attributeValue,
                oldValue: oldValue
            };
            if (attribute.onChange) {
                attribute.onChange(onChangeTuple);
            }
            attribute._.remoteUpdate(attributeValue);
            onChangeArray.push(onChangeTuple);
            if (++attributesIndex === Object.keys(attributes).length) {
                // run after last attribute handle
                if (virtualDevice.onChange) {
                    virtualDevice.onChange(onChangeArray);
                }
            }
        };
        for (var attributeName in attributes) {
            var attribute = virtualDevice[attributeName];
            if (!attribute) {
                lib.createError('device model attribute mismatch on monitoring');
                return;
            }
            var oldValue = attribute.value;
            if (!attribute._.isValidValue(attributes[attributeName])) {
                continue;
            }
            attribute._.getNewValue(attributes[attributeName], virtualDevice, attributeCallback);
        }
    }

    if (data[deviceId][urn].formats) {
        var formats = data[deviceId][urn].formats;
        var alerts = {};
        var dataFormats = {};
        var formatsIndex = 0;
        var formatsUpdateCallback = function () {
            if (obj.onAlerts) {
                alerts[formatUrn] = formats[formatUrn];
                obj.onAlerts(formats[formatUrn]);
            }
            else if (obj.onData) {
                dataFormats[formatUrn] = formats[formatUrn];
                obj.onData(formats[formatUrn]);
            }
            if (++formatsIndex === Object.keys(formats).length) {
                // run after last format handle
                if (virtualDevice.onAlerts && (Object.keys(alerts).length > 0)) {
                    virtualDevice.onAlerts(alerts);
                }
                if (virtualDevice.onData && (Object.keys(dataFormats).length > 0)) {
                    virtualDevice.onData(dataFormats);
                }
            }
        };
        for (var formatUrn in formats) {
            var obj = virtualDevice[formatUrn];
            if (!obj) {
                lib.createError('device model alert/data format mismatch on monitoring');
                return;
            }

            obj._.formatsLocalUpdate(formats[formatUrn], virtualDevice, formatsUpdateCallback);
        }
    }
}
