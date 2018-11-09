/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

//@TODO: jsdoc issue: MessageEnumerator appears in iotcs.* and not at index level (probably due to missing class jsdoc on lib.enterprise.MessageEnumerator) @DONE

/**
 * VirtualDevice is a representation of a device model
 * implemented by an endpoint. A device model is a
 * specification of the attributes, formats, and resources
 * available on the endpoint.
 * <p>
 * The VirtualDevice API is specific to the enterprise
 * client. Also it implements the device monitoring and
 * control specific to the enterprise client and the call
 * to an action method. Actions are defined in the device
 * model.
 * <p>
 * A device model can be obtained by it's afferent urn with the
 * EnterpriseClient if it is registered on the cloud.
 * <p>
 * The VirtualDevice has the attributes and actions of the device
 * model as properties and it provides functionality to the device
 * model in the following ways:
 * <p>
 * <b>Get the value of an attribute:</b><br>
 * <code>var value = device.temperature.value;</code><br>
 * <p>
 * <b>Get the last known value of an attribute:</b><br>
 * <code>var lastValue = device.temperature.lastKnownValue;</code><br>
 * <p>
 * <b>Set the value of an attribute (with update on cloud and error callback handling):</b><br>
 * <code>device.maxThreshold.onError = function (errorTuple);</code><br>
 * <code>device.maxThreshold.value = 27;</code><br>
 * where errorTuple is an object of the form
 * <code>{attribute: ... , newValue: ... , tryValue: ... , errorResponse: ...}</code>.
 * The library will throw an error in the value to update is invalid
 * according to the device model.
 * <p>
 * <b>Monitor a specific attribute for any value change (that comes from the cloud):</b><br>
 * <code>device.temperature.onChange = function (changeTuple);</code><br>
 * where changeTuple is an object of the form
 * <code>{attribute: ... , newValue: ... , oldValue: ...}</code>.
 * <p>
 * <b>Monitor all attributes for any value change (that comes from the cloud):</b><br>
 * <code>device.onChange = function (changeTuple);</code><br>
 * where changeTuple is an object with array type properties of the form
 * <code>[{attribute: ... , newValue: ... , oldValue: ...}]</code>.
 * <p>
 * <b>Monitor all update errors:</b><br>
 * <code>device.onError = function (errorTuple);</code><br>
 * where errorTuple is an object with array type properties (besides errorResponse) of the form
 * <code>{attributes: ... , newValues: ... , tryValues: ... , errorResponse: ...}</code>.
 * <p>
 * <b>Monitor a specific alert format for any alerts that where generated:</b><br>
 * <code>device.tooHot.onAlerts = function (alerts);</code><br>
 * where alerts is an array containing all the alerts generated of the specific format. An
 * alert is an object of the form:
 * <code>{eventTime: ... , severity: ... , fields: {field1: value1, field2: value2 ... }}</code>.
 * The onAlerts can be set also by urn:
 * <code>device['temperature:format:tooHot'].onAlerts = function (alerts);</code><br>
 * <p>
 * <b>Monitor all alerts generated for all formats:</b><br>
 * <code>device.onAlerts = function (alerts);</code><br>
 * where alerts is an object containing all the alert formats as keys and each has as value the above described array:
 * <code>{formatUrn1: [ ... ], formatUrn2: [ ... ], ... }</code>.
 * <p>
 * <b>Monitor a specific custom message format for any messages that where generated:</b><br>
 * <code>device.rfidDetected.onData = function (data);</code><br>
 * where data is an array containing all the custom data messages generated of the specific format. A
 * data object is an object of the form:
 * <code>{eventTime: ... , severity: ... , fields: {field1: value1, field2: value2 ... }}</code>.
 * The onData can be set also by urn:
 * <code>device['temperature:format:rfidDetected'].onData = function (data);</code><br>
 * <p>
 * <b>Monitor all custom data messages generated for all formats:</b><br>
 * <code>device.onData = function (data);</code><br>
 * where data is an object containing all the custom formats as keys and each has as value the above described array:
 * <code>{formatUrn1: [ ... ], formatUrn2: [ ... ], ... }</code>.
 * <p>
 * A VirtualDevice can also be created with the appropriate
 * parameters from the EnterpriseClient.
 *
 * @alias iotcs.enterprise.VirtualDevice
 * @class
 * @extends iotcs.AbstractVirtualDevice
 * @memberof iotcs.enterprise
 * @see {@link iotcs.enterprise.EnterpriseClient#getDeviceModel}
 * @see {@link iotcs.enterprise.EnterpriseClient#createVirtualDevice}
 *
 * @param {string} endpointId - The endpoint id of this device.
 * @param {object} deviceModel - The device model object holding the full description of that device
 *        model that this device implements.
 * @param {iotcs.enterprise.EnterpriseClient} client - The enterprise client associated with the
 *        device application context.
 */
lib.enterprise.VirtualDevice = function (endpointId, deviceModel, client) {
    _mandatoryArg(endpointId, 'string');
    _mandatoryArg(deviceModel, 'object');
    _mandatoryArg(client, lib.enterprise.EnterpriseClient);

    lib.AbstractVirtualDevice.call(this, endpointId, deviceModel);

    this.client = client;
    this.controller = new $impl.Controller(this);

    this.attributes = this;
    var attributes = this.deviceModel.attributes;
    for (var indexAttr in attributes) {
        var attribute = new $impl.Attribute(attributes[indexAttr]);
        if (attributes[indexAttr].alias) {
            _link(attributes[indexAttr].alias, this, attribute);
        }
        _link(attributes[indexAttr].name, this, attribute);
    }

    this.actions = this;

    var actions = this.deviceModel.actions;
    for (var indexAction in actions) {
        var action = new $impl.Action(actions[indexAction]);
        if (actions[indexAction].alias) {
            _link(actions[indexAction].alias, this.actions, action);
        }
        _link(actions[indexAction].name, this.actions, action);
    }


    Object.defineProperty(this, 'onAlerts', {
        enumerable: true,
        configurable: false,
        get: function () {
            return this._.onAlerts;
        },
        set: function (newValue) {
            if (!newValue || (typeof newValue !== 'function')) {
                lib.error('trying to set something to onAlerts that is not a function!');
                return;
            }
            this._.onAlerts = newValue;
        }
    });
    this._.onAlerts = function (arg) {};

    Object.defineProperty(this, 'onData', {
        enumerable: true,
        configurable: false,
        get: function () {
            return this._.onData;
        },
        set: function (newValue) {
            if (!newValue || (typeof newValue !== 'function')) {
                lib.error('trying to set something to onData that is not a function!');
                return;
            }
            this._.onData = newValue;
        }
    });
    this._.onData = function (arg) {};

    var self = this;

    if (this.deviceModel.formats) {
        this.alerts = this;
        this.dataFormats = this;
        this.deviceModel.formats.forEach(function (format) {
            if (format.type && format.urn) {
                if (format.type === 'ALERT') {
                    var alert = new $impl.Alert(format);
                    if (format.name) {
                        _link(format.name, self.alerts, alert);
                    }
                    _link(format.urn, self.alerts, alert);
                }
                if (format.type === 'DATA') {
                    var data = new $impl.Data(format);
                    if (format.name) {
                        _link(format.name, self.dataFormats, data);
                    }
                    _link(format.urn, self.dataFormats, data);
                }
            }
        });
    }

    this._.isDeviceApp = 0;

    Object.preventExtensions(this);

    _deviceMonitorInitialization(self);

};

lib.enterprise.VirtualDevice.prototype = Object.create(lib.AbstractVirtualDevice.prototype);
lib.enterprise.VirtualDevice.constructor = lib.enterprise.VirtualDevice;

/**
 * @ignore
 * @inheritdoc
 */
lib.enterprise.VirtualDevice.prototype.update = function (attributes) {
    _mandatoryArg(attributes, 'object');
    if (Object.keys(attributes).length === 0) {
        return;
    }
    for (var attribute in attributes) {
        var value = attributes[attribute];
        if (attribute in this.attributes) {
            this.attributes[attribute]._.localUpdate(value, true); //XXX not clean
        } else {
            lib.error('unknown attribute "'+attribute+'"');
            return;
        }
    }
    if (this.controller) {
        this.controller.updateAttributes(attributes, false);
    }
};

/**
 * @ignore
 * @inheritdoc
 */
lib.enterprise.VirtualDevice.prototype.close = function () {
    if (this.controller) {
        this.controller.close();
    }
    if (this.client) {
        this.client._.removeVirtualDevice(this);
    }
    this.endpointId = null;
    this.onChange = function (arg) {};
    this.onError = function (arg) {};
    this.onAlerts = function (arg) {};
    this.controller = null;
};

/**
 * Execute an action. The library will throw an error if the action is not in the model or if the
 * argument is invalid (or not present when it should be).  The actions are as attributes properties
 * of the virtual device.
 * <p>
 * The response from the cloud to the execution of the action can be retrieved by setting a callback
 * function to the onExecute property of the action:<br> <code>device.reset.onExecute = function
 * (response);</code><br> <code>device.call('reset');</code><br> where response is a JSON
 * representation of the response from the cloud if any.
 *
 * @function call
 * @memberof iotcs.enterprise.VirtualDevice.prototype
 *
 * @param {string} actionName - The name of the action to execute.
 * @param {object} [arg] - An optional unique argument to pass for action execution.  This is
 *        specific to the action and description of it is provided in the device model.
 */
lib.enterprise.VirtualDevice.prototype.call = function (actionName, arg) {
    _mandatoryArg(actionName, 'string');
    if (arguments.length > 2) {
        lib.error('invalid number of arguments');
    }
    var action = this[actionName];
    if (!action) {
        lib.error('action "'+actionName+'" is not executable');
        return;
    }
    this.controller.invokeAction(action.name, arg);
};

