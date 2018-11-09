/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * This represents a GatewayDevice in the Messaging API.  It has the exact same specifications and
 * capabilities as a directly connected device from the Messaging API and additionally it has the
 * capability to register indirectly connected devices.
 *
 * @alias iotcs.device.util.GatewayDevice
 * @class
 * @extends iotcs.device.util.DirectlyConnectedDevice
 * @memberof iotcs.device.util
 *
 * @param {string} [taStoreFile] - The trusted assets store file path to be used for trusted assets
 *        manager creation.  This is optional.  If none is given the default global library
 *        parameter is used: lib.oracle.iot.tam.store.
 * @param {string} [taStorePassword] - The trusted assets store file password to be used for trusted
 *        assets manager creation.  This is optional.  If none is given the default global library
 *        parameter is used: lib.oracle.iot.tam.storePassword.
 */
lib.device.util.GatewayDevice = function (taStoreFile, taStorePassword) {
    lib.device.util.DirectlyConnectedDevice.call(this, taStoreFile, taStorePassword, true);
};

lib.device.util.GatewayDevice.prototype = Object.create(lib.device.util.DirectlyConnectedDevice.prototype);
lib.device.util.GatewayDevice.constructor = lib.device.util.GatewayDevice;

/**
 * Activate the device.  The device will be activated on the server if necessary. When the device is
 * activated on the server.  The activation would tell the server the models that the device
 * implements.  Also the activation can generate additional authorization information that will be
 * stored in the TrustedAssetsStore and used for future authentication requests.  This can be a
 * time/resource consuming operation for some platforms.
 * <p>
 * If the device is already activated, this method will throw an exception.  The user should call
 * the isActivated() method prior to calling activate.
 *
 * @function activate
 * @memberof iotcs.device.util.GatewayDevice.prototype
 *
 * @param {string[]} deviceModelUrns - An array of deviceModel URNs implemented by this directly
 *        connected device.
 * @param {function} callback - The callback function.  This function is called with this object but
 *        in the activated state.  If the activation is not successful then the object will be null
 *        and an error object is passed in the form callback(device, error) and the reason can be
 *        taken from error.message.
 */
lib.device.util.GatewayDevice.prototype.activate = function (deviceModelUrns, callback) {
    if (this.isActivated()) {
        lib.error('cannot activate an already activated device');
        return;
    }

    _mandatoryArg(deviceModelUrns, 'array');
    _mandatoryArg(callback, 'function');

    deviceModelUrns.forEach(function (urn) {
        _mandatoryArg(urn, 'string');
    });

    var deviceModels = deviceModelUrns;
    deviceModels.push('urn:oracle:iot:dcd:capability:direct_activation');
    deviceModels.push('urn:oracle:iot:dcd:capability:indirect_activation');
    deviceModels.push('urn:oracle:iot:dcd:capability:device_policy');
    var self = this;
    this._.internalDev.activate(deviceModels, function(activeDev, error) {
        if (!activeDev || error) {
            callback(null, error);
            return;
        }
        callback(self);
    });
};

/**
 * Register an indirectly-connected device with the cloud service and specify whether
 * the gateway device is required to have the appropriate credentials for activating
 * the indirectly-connected device.
 *
 * The <code>restricted</code> parameter controls whether or not the client
 * library is <em>required</em> to supply credentials for activating
 * the indirectly-connected device. The client library will
 * <em>always</em> supply credentials for an indirectly-connected
 * device whose trusted assets have been provisioned to the client.
 * If, however, the trusted assets of the indirectly-connected device
 * have not been provisioned to the client, the client library can
 * create credentials that attempt to restrict the indirectly connected
 * device to this gateway device.
 *
 * Pass <code>true</code> for the <code>restricted</code> parameter
 * to ensure the indirectly-connected device cannot be activated
 * by this gateway device without presenting credentials. If <code>restricted</code>
 * is <code>true</code>, the client library will provide credentials to the server.
 * The server will reject the activation request if the indirectly connected
 * device is not allowed to roam to this gateway device.
 *
 * Pass <code>false</code> to allow the indirectly-connected device to be activated
 * without presenting credentials if the trusted assets of the
 * indirectly-connected device have not been provisioned to the client.
 * If <code>restricted</code> is <code>false</code>, the client library will provide
 * credentials if, and only if, the credentials have been provisioned to the
 * client. The server will reject the activation if credentials are required
 * but not supplied, or if the provisioned credentials do not allow the
 * indirectly connected device to roam to this gateway device.
 *
 * The <code>hardwareId</code> is a unique identifier within the cloud service
 * instance and may not be <code>null</code>. If one is not present for the device,
 * it should be generated based on other metadata such as: model, manufacturer,
 * serial number, etc.
 *
 * The <code>metaData</code> Object should typically contain all the standard
 * metadata (the constants documented in this class) along with any other
 * vendor defined metadata.
 *
 * @function registerDevice
 * @memberof iotcs.device.util.GatewayDevice.prototype
 *
 * @param {boolean} restricted - <code>true</code> if credentials are required for activating the
 *        indirectly connected device.
 * @param {!string} hardwareId - An identifier unique within the Cloud Service instance.
 * @param {object} metaData - The metadata of the device.
 * @param {string[]} deviceModelUrns - An array of device model URNs supported by the indirectly
 *        connected device.
 * @param {function} callback - The callback function.  This function is called with the following
 *        argument: the endpoint ID of the indirectly-connected device is the registration was
 *        successful or <code>null</code> and an error object as the second parameter:
 *        callback(id, error).  The reason can be retrieved from error.message and it represents the
 *        actual response from the server or any other network or framework error that can appear.
 */
lib.device.util.GatewayDevice.prototype.registerDevice = function (restricted, hardwareId, metaData, deviceModelUrns, callback) {
    if (!this.isActivated()) {
        lib.error('device not activated yet');
        return;
    }

    if (typeof (restricted) !== 'boolean') {
        lib.log('type mismatch: got '+ typeof (restricted) +' but expecting any of boolean)');
        lib.error('illegal argument type');
        return;
    }
    _mandatoryArg(hardwareId, 'string');
    _mandatoryArg(metaData, 'object');
    _mandatoryArg(callback, 'function');
    deviceModelUrns.forEach(function (urn) {
        _mandatoryArg(urn, 'string');
    });

    var payload = metaData;
    payload.hardwareId = hardwareId;
    payload.deviceModels = deviceModelUrns;

    var self = this;
    var data = self._.internalDev._.tam.getEndpointId();
    // If the ICD has been provisioned, use the shared secret to generate the
    // signature for the indirect activation request.
    // If this call return null, then the ICD has not been provisioned.
    var signature = self._.internalDev._.tam.signWithSharedSecret(data, "sha256", hardwareId);

    // If the signature is null, then the ICD was not provisioned. But if
    // the restricted flag is true, then we generate a signature which will
    // cause the ICD to be locked (for roaming) to the gateway
    if (restricted && (signature === null)) {
        signature = self._.internalDev._.tam.signWithPrivateKey(data, "sha256");
    }

    if (signature !== null) {
        if (typeof signature === 'object') {
            payload.signature = forge.util.encode64(signature.bytes());
        } else {
            payload.signature = forge.util.encode64(signature);
        }
    }

    var indirect_request;

    indirect_request = function () {
        var options = {
            path: $impl.reqroot + '/activation/indirect/device'
            + (lib.oracle.iot.client.device.allowDraftDeviceModels ? '' : '?createDraft=false'),
            method: 'POST',
            headers: {
                'Authorization': self._.internalDev._.bearer,
                'X-EndpointId': self._.internalDev._.tam.getEndpointId()
            },
            tam: self._.internalDev._.tam
        };
        $impl.protocolReq(options, JSON.stringify(payload), function (response_body, error) {

            if (!response_body || error || !response_body.endpointState) {
                callback(null, lib.createError('invalid response on indirect registration', error));
                return;
            }

            if(response_body.endpointState !== 'ACTIVATED') {
                callback(null, lib.createError('endpoint not activated: '+JSON.stringify(response_body)));
                return;
            }

            callback(response_body.endpointId);

        },indirect_request, self._.internalDev);
    };

    indirect_request();
};
