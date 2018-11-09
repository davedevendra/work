/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

//@TODO: add and validate this.tam = new $impl.TrustedAssetsManager();

/**
 * Client of the Oracle IoT Cloud Service. A client is a
 * directly-connected device, a gateway, or an enterprise
 * application.
 *
 * @class
 */
lib.Client = function () {
    this.cache = this.cache || {};
    this.cache.deviceModels = {};
};

/**
 * Create an AbstractVirtualDevice instance with the given device model for the given device
 * identifier.  This method creates a new AbstractVirtualDevice instance for the given parameters.
 * The client library does not cache previously created AbstractVirtualDevice objects.
 * <p>
 * A device model can be obtained by it's afferent urn with the Client if it is registered on the
 * cloud.
 *
 * @function createVirtualDevice
 * @memberof iotcs.Client.prototype
 * @see {@link iotcs.Client#getDeviceModel}
 *
 * @param {string} endpointId - The endpoint identifier of the device being modeled.
 * @param {object} deviceModel - The device model object holding the full description of that device
 *        model that this device implements.
 * @returns {iotcs.AbstractVirtualDevice} The newly created virtual device
 */
lib.Client.prototype.createVirtualDevice = function (endpointId, deviceModel) {
    _mandatoryArg(endpointId, 'string');
    _mandatoryArg(deviceModel, 'object');
    return new lib.AbstractVirtualDevice(endpointId, deviceModel);
};

/**
 * Get the device model for the specified device model URN.
 *
 * @function getDeviceModel
 * @memberof iotcs.Client.prototype
 *
 * @param {string} deviceModelUrn - The URN of the device model.
 * @param {function} callback - The callback function.  This function is called with the following
 *        argument: a deviceModel object holding full description e.g. <code>{ name:"",
 *        description:"", fields:[...], created:date, isProtected:boolean, lastModified:date ... }
 *        </code>.  If an error occurs the deviceModel object is <code>null</code> and an error
 *        object is passed: callback(deviceModel, error) and the reason can be taken from error.
 *        message.
 */
lib.Client.prototype.getDeviceModel = function (deviceModelUrn, callback) {
    _mandatoryArg(deviceModelUrn, 'string');
    _mandatoryArg(callback, 'function');
    var deviceModel = this.cache.deviceModels[deviceModelUrn];
    if (deviceModel) {
        callback(deviceModel);
        return;
    }
    var self = this;
    $impl.https.bearerReq({
        method: 'GET',
        path:   $impl.reqroot
            + '/deviceModels/' + deviceModelUrn
    }, '', function (response, error) {
        if(!response || error || !(response.urn)){
            callback(null, lib.createError('invalid response on get device model', error));
            return;
        }
        var deviceModel = response;
        Object.freeze(deviceModel);
        self.cache.deviceModels[deviceModelUrn] = deviceModel;
        callback(deviceModel);
    }, function () {
        self.getDeviceModel(deviceModelUrn, callback);
    }, (lib.$port.userAuthNeeded() ? null : (lib.$impl.DirectlyConnectedDevice ? new lib.$impl.DirectlyConnectedDevice() : new lib.$impl.EnterpriseClientImpl())));
};

/**
 * Create a new {@link iotcs.device.StorageObject} with the given object name and mime&ndash;type.
 *
 * @function createStorageObject
 * @memberof iotcs.Client.prototype
 *
 * @param {string} name - The unique name to be used to reference the content in storage.
 * @param {string} type - The mime-type of the content.  If not set, the mime&ndash;type defaults to
 *        {@link lib.device.StorageObject.MIME_TYPE}.
 * @returns {iotcs.device.StorageObject} A storage object.
 */
lib.Client.prototype.createStorageObject = function (name, type) {
    _mandatoryArg(name, "string");
    _optionalArg(type, "string");
    var storage = new lib.device.StorageObject(null, name, type);
    storage._.setDevice(this._.internalDev);
    return storage;
};
