/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * This represents a GatewayDevice in the Virtualization API.  It has the exact same specifications
 * and capabilities as a directly connected device from the Virtualization API and additionally it
 * has the capability to register indirectly connected devices.
 *
 * @alias iotcs.device.GatewayDevice
 * @class
 * @extends iotcs.device.DirectlyConnectedDevice
 * @memberof iotcs.device
 *
 * @param {string} [taStoreFile] - The trusted assets store file path to be used for trusted assets
 *        manager creation.  This is optional.  If none is given the default global library
 *        parameter is used: lib.oracle.iot.tam.store.
 * @param {string} [taStorePassword] - The trusted assets store file password to be used for trusted
 *        assets manager creation.  This is optional.  If none is given the default global library
 *        parameter is used: lib.oracle.iot.tam.storePassword.
 */
lib.device.GatewayDevice = function (taStoreFile, taStorePassword) {
    lib.device.DirectlyConnectedDevice.call(this, (taStoreFile ? taStoreFile : null), (taStorePassword ? taStorePassword : null), true);
};

// Construct this Gateway's DCD (all devices are DCD's).
lib.device.GatewayDevice.prototype = Object.create(lib.device.DirectlyConnectedDevice.prototype);
lib.device.GatewayDevice.constructor = lib.device.GatewayDevice.prototype;

/**
 * Enumeration of the standard properties that can be used in the metadata object given as parameter
 * on indirect registration.
 *
 * @alias DeviceMetadata
 * @class
 * @enum {string}
 * @memberOf iotcs.device.GatewayDevice
 * @readonly
 * @see {@link iotcs.device.GatewayDevice#registerDevice}
 * @static
 */
lib.device.GatewayDevice.DeviceMetadata = {
    MANUFACTURER: 'manufacturer',
    MODEL_NUMBER: 'modelNumber',
    SERIAL_NUMBER: 'serialNumber',
    DEVICE_CLASS: 'deviceClass',
    PROTOCOL: 'protocol',
    PROTOCOL_DEVICE_CLASS: 'protocolDeviceClass',
    PROTOCOL_DEVICE_ID: 'protocolDeviceId'
};

/**
 * Register an indirectly-connected device with the cloud service and specify whether the gateway
 * device is required to have the appropriate credentials for activating the indirectly-connected
 * device.
 *
 * The <code>restricted</code> parameter controls whether or not the client library is
 * <em>required</em> to supply credentials for activating the indirectly-connected device.  The
 * client library will <em>always</em> supply credentials for an indirectly-connected device whose
 * trusted assets have been provisioned to the client.  If, however, the trusted assets of the
 * indirectly-connected device have not been provisioned to the client, the client library can
 * create credentials that attempt to restrict the indirectly connected device to this gateway
 * device.
 *
 * The <code>restricted</code> parameter could be omitted. This is the equivalent of calling
 * <code>iotcs.device.util.GatewayDevice.registerDevice(false, hardwareId, metaData, deviceModels, callback)</code>.
 *
 * Pass <code>true</code> for the <code>restricted</code> parameter to ensure the
 * indirectly-connected device cannot be activated by this gateway device without presenting
 * credentials.  If <code>restricted</code> is <code>true</code>, the client library will provide
 * credentials to the server.  The server will reject the activation request if the indirectly
 * connected device is not allowed to roam to this gateway device.
 *
 * Pass <code>false</code> to allow the indirectly-connected device to be activated without
 * presenting credentials if the trusted assets of the indirectly-connected device have not been
 * provisioned to the client.  If <code>restricted</code> is <code>false</code>, the client library
 * will provide credentials if, and only if, the credentials have been provisioned to the client.
 * The server will reject the activation if credentials are required but not supplied, or if the
 * provisioned credentials do not allow the indirectly connected device to roam to this gateway
 * device.
 *
 * The <code>hardwareId</code> is a unique identifier within the cloud service instance and may not
 * be <code>null</code>.  If one is not present for the device, it should be generated based on
 * other metadata such as: model, manufacturer, serial number, etc.
 *
 * The <code>metaData</code> Object should typically contain all the standard metadata (the
 * constants documented in this class) along with any other vendor defined metadata.
 *
 * @function registerDevice
 * @memberof iotcs.device.GatewayDevice.prototype
 * @see {@link iotcs.device.GatewayDevice.DeviceMetadata}
 *
 * @param {boolean} [restricted] - <code>true</code> if the client library is <em>required</em> to
 *        supply credentials for activating the indirectly-connected device.
 * @param {!string} hardwareId - An identifier unique within the Cloud Service instance.
 * @param {object} metaData - The metadata of the device.
 * @param {string[]} deviceModelUrns - An array of device model URNs supported by the indirectly
 *        connected device.
 * @param {function(Object)} callback - The callback function.  This function is called with the
 *        following argument: the endpoint ID of the indirectly-connected device is the registration
 *        was successful or <code>null</code> and an error object as the second parameter:
 *        callback(id, error).  The reason can be retrieved from error.message and it represents
 *        the actual response from the server or any other network or framework error that can
 *        appear.
 */
lib.device.GatewayDevice.prototype.registerDevice = function (restricted, hardwareId, metaData, deviceModelUrns, callback) {
    if (arguments.length == 4) {
        hardwareId = arguments[0];
        metaData = arguments[1];
        deviceModelUrns = arguments[2];
        callback = arguments[3];
        restricted = false;
    }
    this._.internalDev.registerDevice(restricted, hardwareId, metaData, deviceModelUrns, callback);
};
