/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * @classdesc
 * AbstractVirtualDevice is a representation of a device model implemented by an endpoint.  A device
 * model is a specification of the attributes, formats, and resources available on the endpoint.
 * <p>
 * The AbstractVirtualDevice API is identical for both the enterprise client and the device client.
 * The semantics of the API are also the same. The processing model on the enterprise client is
 * different, however, from the processing model on the device client.
 * <p>
 * A device model can be obtained by it's afferent URN with the Client if it is registered on the
 * cloud.
 * <p>
 * An AbstractVirtualDevice can also be created with the appropriate parameters from the Client.
 *
 * @alias iotcs.AbstractVirtualDevice
 * @class
 * @see {@link iotcs.Client#getDeviceModel}
 * @see {@link iotcs.Client#createVirtualDevice}
 *
 * @param {string} endpointId - The endpoint id of this device.
 * @param {object} deviceModel - The device model object holding the full description of that device
 *        model that this device implements.
 */
lib.AbstractVirtualDevice = function (endpointId, deviceModel) {
    _mandatoryArg(endpointId, 'string');
    _mandatoryArg(deviceModel, 'object');

    this.endpointId = endpointId;
    this.deviceModel = deviceModel;

    Object.defineProperty(this, '_', {
        enumerable: false,
        configurable: false,
        writable: true,
        value: {}
    });

    Object.defineProperty(this, 'onChange', {
        enumerable: true,
        configurable: false,
        get: function () {
            return this._.onChange;
        },
        set: function (newValue) {
            if (!newValue || (typeof newValue !== 'function')) {
                lib.error('Trying to set onChange to something that is not a function.');
                return;
            }
            this._.onChange = newValue;
        }
    });
    this._.onChange = null;

    Object.defineProperty(this, 'onError', {
        enumerable: true,
        configurable: false,
        get: function () {
            return this._.onError;
        },
        set: function (newValue) {
            if (!newValue || (typeof newValue !== 'function')) {
                lib.error('Trying to set onError to something that is not a function.');
                return;
            }
            this._.onError = newValue;
        }
    });
    this._.onError = null;
};

/**
 * Get the device model of this device object. This is the exact model
 * that was used at construction of the device object.
 *
 * @function getDeviceModel
 * @memberof iotcs.AbstractVirtualDevice.prototype
 *
 * @returns {object} The object representing the device model for this device.
 */
lib.AbstractVirtualDevice.prototype.getDeviceModel = function () {
    return this.deviceModel;
};

/**
 * Get the endpoint id of the device.
 *
 * @memberof iotcs.AbstractVirtualDevice.prototype
 * @function getEndpointId
 *
 * @returns {string} The endpoint id of this device as given at construction of the virtual device.
 */
lib.AbstractVirtualDevice.prototype.getEndpointId = function () {
    return this.endpointId;
};

//@TODO: accessing directly a very internal object is not clean: e.g. "this.attributes[attribute]._."

/**
 * The update call allows more than one value to be set on this Device object and in the end, it is
 * sending the values to the server.
 * <p>
 * The values are sent to the server when the method is called, which also marks the end of the
 * update transaction.
 * <p>
 * For example <code>device.update({"min":10, "max":20});</code>
 * <p>
 * If the virtual device has the onError property set with a callback method or any/all of the
 * attributes given in the update call have the onError attribute set with a callback method, in
 * case of error on update the callbacks will be called with related attribute information.  See
 * VirtualDevice description for more info on onError.
 *
 * @function update
 * @memberof iotcs.AbstractVirtualDevice.prototype
 * @see {@link VirtualDevice|iotcs.enterprise.VirtualDevice}
 *
 * @param {object} attributes - An object holding a list of attribute name/
 * value pairs to be updated as part of this transaction,
 * e.g. <code>{ "temperature":23, ... }</code>. Note that keys shall refer
 * to device attribute names.
 */
lib.AbstractVirtualDevice.prototype.update = function (attributes) {
};

/**
 * Close this virtual device and all afferent resources used for monitoring or controlling the
 * device.
 *
 * @function close
 * @memberof iotcs.AbstractVirtualDevice.prototype
 */
lib.AbstractVirtualDevice.prototype.close = function () {
    this.endpointId = null;
    this.deviceModel = null;
    this.onChange = function (arg) {};
    this.onError = function (arg) {};
};

//////////////////////////////////////////////////////////////////////////////

/** @ignore */
function _link(name, device, element) {
    _mandatoryArg(name, 'string');
    _mandatoryArg(device, 'object'); //@TODO: should be checked against instance name
    _mandatoryArg(element, 'object');
    if (device[name]) {
        return;
    }
    Object.defineProperty(device, name, {
        enumerable: true,
        configurable: false,
        writable: false,
        value: element
    });
    Object.defineProperty(element, 'device', {
        enumerable: true,
        configurable: false,
        writable: false,
        value: device
    });
}
