/**
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**@ignore*/
$impl.DeviceModelFactory = function () {
    if ($impl.DeviceModelFactory.prototype._singletonInstance) {
        return $impl.DeviceModelFactory.prototype._singletonInstance;
    }

    $impl.DeviceModelFactory.prototype._singletonInstance = this;
    this.cache = this.cache || {};
    this.cache.deviceModels = {};
};

/**@ignore*/
$impl.DeviceModelFactory.prototype.getDeviceModel = function (dcd, deviceModelUrn, callback) {
    debug('DeviceModelFactory.getDeviceModel Getting device model for deviceModelUrn: ' +
        deviceModelUrn);

    _mandatoryArg(dcd, lib.device.util.DirectlyConnectedDevice);

    if (!dcd.isActivated()) {
        lib.error('Device not activated yet.');
        return;
    }

    _mandatoryArg(deviceModelUrn, 'string');
    _mandatoryArg(callback, 'function');

    var deviceModel = this.cache.deviceModels[deviceModelUrn];

    if (deviceModel) {
        callback(deviceModel);
        return;
    }

    var self = this;

    var options = {
        headers: {
            'Authorization': dcd._.internalDev._.bearer,
            'X-EndpointId': dcd._.internalDev._.tam.getEndpointId()
        },
        method: 'GET',
        path: $impl.reqroot + '/deviceModels/' + deviceModelUrn,
        tam: dcd._.internalDev._.tam
    };

    $impl.protocolReq(options, '', function (response, error) {
        debug('DeviceModelFactory.getDeviceModel response = ' + response + ', error = ' +
            error);

        if (!response || !(response.urn) || error) {
            callback(null, lib.createError('Invalid response when getting device model.', error));
            return;
        }

        var deviceModel = response;

        if (!lib.oracle.iot.client.device.allowDraftDeviceModels && deviceModel.draft) {
            callback(null,
                lib.createError('Found draft device model.  Library is not configured for draft device models.'));

            return;
        }

        Object.freeze(deviceModel);
        self.cache.deviceModels[deviceModelUrn] = deviceModel;
        callback(deviceModel);
    }, function () {
        self.getDeviceModel(dcd, deviceModelUrn, callback);
    }, dcd._.internalDev);
};
