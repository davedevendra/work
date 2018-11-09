/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * VirtualDevice is a representation of a device model
 * implemented by an endpoint. A device model is a
 * specification of the attributes, formats, and resources
 * available on the endpoint.
 * <p>
 * This VirtualDevice API is specific to the device
 * client. This implements the alerts defined in the
 * device model and can be used for raising alerts to
 * be sent to the server for the device. Also it has
 * action handlers for actions that come as requests
 * from the server side.
 * <p>
 * A device model can be obtained by it's afferent urn with the
 * DirectlyConnectedDevice if it is registered on the cloud.
 * <p>
 * The VirtualDevice has the attributes, actions and alerts of the device
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
 * <code>device.temperature.onError = function (errorTuple);</code><br>
 * <code>device.temperature.value = 27;</code><br>
 * where errorTuple is an object of the form
 * <code>{attribute: ... , newValue: ... , tryValue: ... , errorResponse: ...}</code>.
 * The library will throw an error in the value to update is invalid
 * according to the device model.
 * <p>
 * <b>Monitor a specific attribute for any value change (that comes from the cloud):</b><br>
 * <code>device.maxThreshold.onChange = function (changeTuple);</code><br>
 * where changeTuple is an object of the form
 * <code>{attribute: ... , newValue: ... , oldValue: ...}</code>.
 * To tell the cloud that the attribute update has failed
 * an exception must be thrown in the onChange function, otherwise the
 * library will send an OK response message to the cloud.
 * <p>
 * <b>Monitor a specific action that was requested from the server:</b><br>
 * <code>device.reset.onExecute = function (value);</code><br>
 * where value is an optional parameter given if the action has parameters
 * defined in the device model. To tell the cloud that an action has failed
 * an exception must be thrown in the onExecute function, otherwise the
 * library will send an OK response message to the cloud.
 * <p>
 * <b>Monitor all attributes for any value change (that comes from the cloud):</b><br>
 * <code>device.onChange = function (changeTuple);</code><br>
 * where changeTuple is an object with array type properties of the form
 * <code>[{attribute: ... , newValue: ... , oldValue: ...}]</code>.
 * To tell the cloud that the attribute update has failed
 * an exception must be thrown in the onChange function, otherwise the
 * library will send an OK response message to the cloud.
 * <p>
 * <b>Monitor all update errors:</b><br>
 * <code>device.onError = function (errorTuple);</code><br>
 * where errorTuple is an object with array type properties (besides errorResponse) of the form
 * <code>{attributes: ... , newValues: ... , tryValues: ... , errorResponse: ...}</code>.
 * <p>
 * <b>Raising alerts:</b><br>
 * <code>var alert = device.createAlert('urn:com:oracle:iot:device:temperature_sensor:too_hot');</code><br>
 * <code>alert.fields.temp = 100;</code><br>
 * <code>alert.fields.maxThreshold = 90;</code><br>
 * <code>alert.raise();</code><br>
 * If an alert was not sent the error is handled by the device.onError handler where errorTuple has
 * the following structure:<br>
 * <code>{attributes: ... , errorResponse: ...}</code><br>
 * where attributes are the alerts that failed with fields already set, so the alert can be retried
 * only by raising them.
 * <p>
 * <b>Sending custom data fields:</b><br>
 * <code>var data = device.createData('urn:com:oracle:iot:device:motion_sensor:rfid_detected');</code><br>
 * <code>data.fields.detecting_motion = true;</code><br>
 * <code>data.submit();</code><br>
 * If the custom data fields were not sent, the error is handled by the device.onError handler where errorTuple has
 * the following structure:<br>
 * <code>{attributes: ... , errorResponse: ...}</code><br>
 * where attributes are the Data objects that failed to be sent with fields already set, so the Data objects can be retried
 * only by sending them.
 * <p>
 * A VirtualDevice can also be created with the appropriate
 * parameters from the DirectlyConnectedDevice.
 *
 * @alias iotcs.device.VirtualDevice
 * @class
 * @extends iotcs.AbstractVirtualDevice
 * @memberof iotcs.device
 *
 * @param {string} endpointId - The endpoint ID of this device.
 * @param {object} deviceModel - The device model object holding the full description of that device
 *        model that this device implements.
 * @param {DirectlyConnectedDevice} client - The device client used as message dispatcher for this
 *        virtual device.
 *
 * @see {@link DirectlyConnectedDevice#getDeviceModel|iotcs.device.DirectlyConnectedDevice#getDeviceModel}
 * @see {@link DirectlyConnectedDevice#createVirtualDevice|iotcs.device.DirectlyConnectedDevice#createVirtualDevice}
 */
lib.device.VirtualDevice = function (endpointId, deviceModel, client) {
    // Instance "variables"/properties...see constructor.
    _mandatoryArg(endpointId, 'string');
    _mandatoryArg(deviceModel, 'object');
    _mandatoryArg(client, lib.device.DirectlyConnectedDevice);

    lib.AbstractVirtualDevice.call(this, endpointId, deviceModel);

    this.client = client;

    let persistenceStore = PersistenceStoreManager.get(endpointId);
    this.devicePolicyManager = new DevicePolicyManager(client);

    if (this.devicePolicyManager) {
        persistenceStore
            .openTransaction()
            .putOpaque('DevicePolicyManager', this.devicePolicyManager)
            .commit();
    }

    /**
     * @param {VirtualDevice} virtualDevice
     * @param {DeviceModel} deviceModel
     * @return {Map<string, VirtualDeviceAttribute>}
     */
    Object.defineProperty(this._, 'createAttributeMap', {
        configurable: false,
        enumerable: false,
        writable: false,
        value: function(virtualDevice, deviceModel) {
            // @type {Map<String, VirtualDeviceAttributeBase<VirtualDevice, Object>>}
            const map = new Map();
            const deviceModelObj = DeviceModelParser.fromJson(deviceModel);

            deviceModelObj.getDeviceModelAttributes().forEach((attribute, attributeName) => {
                // @type {VirtualDeviceAttributeImpl<Object>}
                let vda = new VirtualDeviceAttribute(virtualDevice, attribute);
                map.set(attributeName, vda);
                // @type {string}
                let alias = attribute.getName();

                if (alias && (alias.length > 0)) {
                    map.set(alias, vda);
                }
            });

            return map;
        }
    });

    this.attributeMap = this._.createAttributeMap(this, deviceModel);
    this.messageDispatcher = new lib.device.util.MessageDispatcher(this.client._.internalDev);
    let messageDispatcher = this.messageDispatcher; // TODO: fix references to local dispatcher.

    var self = this;
    // @type {Map<string, VirtualDeviceAttribute}
    this.attributes = this;

    // The key is the set of attributes that are referred to in the computedMetric formula.
    // The value is the attribute that is computed.
    // @type {Set<Pair<Set<string>, string>>}
    this.computedMetricTriggerMap = new Set();
    // @type {DevicePolicyManager}
    this.devicePolicyManager = DevicePolicyManager.getDevicePolicyManager(endpointId);
    this.devicePolicyManager.addChangeListener(this);
    // Millisecond time in the future at which the policy value should be computed.
    // @type {number}
    this.expiry = 0;
    // {Map<string, Set<Map<string, object>>>}
    this.pipelineDataCache = new Map();
    // { attributeName : pipelineIndex }
    // @type {Map<string, number}
    this.pipelineIndices = new Map();

    // Window based policy support (as in "window", not Windows OS). Have one scheduled task for
    // each policy "slide" value. The slide value is how much to move the window, so we want to run
    // the policy when the slide expires. When the slide expires, the runnable will call back each
    // VirtualDeviceAttribute that has a policy for that slide value.
    // Window and slide are the key.
    // { {window,slide} : ScheduledPolicyData }
    // @type {Map<ScheduledPolicyDataKey, ScheduledPolicyData}
    this.scheduledPolicies = new Map();
    // How much the window moves is used to calculate expiry.
    // @type {number}
    this.slide = 0;
    // @type {TimedPolicyThread}
    this.timedPolicyThread = new TimedPolicyThread(this);

    var attributeHandler = function (requestMessage) {
        var method = _getMethodForRequestMessage(requestMessage);

        if (!method || (method !== 'PUT')) {
            return lib.message.Message.buildResponseMessage(requestMessage, 405, {}, 'Method Not Allowed', '');
        }

        var urlAttribute = requestMessage.payload.url.substring(requestMessage.payload.url.lastIndexOf('/') + 1);

        if ((urlAttribute in self.attributes) &&
            (self.attributes[urlAttribute] instanceof $impl.Attribute))
        {
            try {
                var attribute = self.attributes[urlAttribute];
                var data = null;
                var isDone = false;

                try {
                    data = JSON.parse($port.util.atob(requestMessage.payload.body));
                } catch (e) {
                    return lib.message.Message.buildResponseMessage(requestMessage, 400, {}, 'Bad Request', '');
                }

                var oldValue = attribute.value;

                if (!data || (typeof data.value === 'undefined') || !attribute._.isValidValue(data.value)) {
                    return lib.message.Message.buildResponseMessage(requestMessage, 400, {}, 'Bad Request', '');
                }

                attribute._.getNewValue(data.value, self, function(attributeValue, isSync) {
                    var onChangeTuple = {
                        attribute: attribute,
                        newValue: attributeValue,
                        oldValue: oldValue
                    };

                    if (attribute.onChange) {
                        attribute.onChange(onChangeTuple);
                    }

                    if (self.onChange) {
                        self.onChange([onChangeTuple]);
                    }

                    attribute._.remoteUpdate(attributeValue);
                    var message = new lib.message.Message();
                    message
                        .type(lib.message.Message.Type.DATA)
                        .source(self.getEndpointId())
                        .format(self.model.urn+":attributes");

                    message.dataItem(urlAttribute, attributeValue);
                    messageDispatcher.queue(message);

                    if (isSync) {
                        isDone = true;
                    } else {
                        messageDispatcher.queue(lib.message.Message.buildResponseMessage(requestMessage, 200, {}, 'OK', ''));
                    }
                });

                if (isDone) {
                    return lib.message.Message.buildResponseMessage(requestMessage, 200, {}, 'OK', '');
                } else {
                    return lib.message.Message.buildResponseWaitMessage();
                }
            } catch (e) {
                return lib.message.Message.buildResponseMessage(requestMessage, 400, {}, 'Bad Request', '');
            }
        } else {
            return lib.message.Message.buildResponseMessage(requestMessage, 404, {}, 'Not Found', '');
        }
    };

    var attributes = this.deviceModel.attributes;
    for (var indexAttr in attributes) {
        var attribute = new $impl.Attribute(attributes[indexAttr]);

        if (attributes[indexAttr].alias) {
            _link(attributes[indexAttr].alias, this, attribute);
            messageDispatcher.getRequestDispatcher().registerRequestHandler(endpointId, 'deviceModels/'+this.deviceModel.urn+'/attributes/'+attributes[indexAttr].alias, attributeHandler);
        }

        _link(attributes[indexAttr].name, this, attribute);
        messageDispatcher.getRequestDispatcher().registerRequestHandler(endpointId, 'deviceModels/'+this.deviceModel.urn+'/attributes/'+attributes[indexAttr].name, attributeHandler);
    }

    this.actions = this;

    var actionHandler = function (requestMessage) {
        var method = _getMethodForRequestMessage(requestMessage);
        var urlAction = requestMessage.payload.url.substring(requestMessage.payload.url.lastIndexOf('/') + 1);
        if (!method || (method !== 'POST')) {
            return lib.message.Message.buildResponseMessage(requestMessage, 405, {}, 'Method Not Allowed', '');
        }
        if ((urlAction in self.actions)
            && (self.actions[urlAction] instanceof $impl.Action)
            && self.actions[urlAction].onExecute) {
            try {
                var action = self.actions[urlAction];
                var data = null;
                var isDone = false;
                try {
                    data = JSON.parse($port.util.atob(requestMessage.payload.body));
                } catch (e) {
                    return lib.message.Message.buildResponseMessage(requestMessage, 400, {}, 'Bad Request', '');
                }

                if (!data) {
                    return lib.message.Message.buildResponseMessage(requestMessage, 400, {}, 'Bad Request', '');
                }

                action.checkAndGetVarArg(data.value, self, function (actionValue, isSync) {
                    action.onExecute(actionValue);
                    if (isSync) {
                        isDone = true;
                    } else {
                        messageDispatcher.queue(lib.message.Message.buildResponseMessage(requestMessage, 200, {}, 'OK', ''));
                    }
                });
                if (isDone) {
                    return lib.message.Message.buildResponseMessage(requestMessage, 200, {}, 'OK', '');
                } else {
                    return lib.message.Message.buildResponseWaitMessage();
                }
            } catch (e) {
                return lib.message.Message.buildResponseMessage(requestMessage, 500, {}, 'Internal Server Error', '');
            }
        } else {
            return lib.message.Message.buildResponseMessage(requestMessage, 404, {}, 'Not Found', '');
        }
    };

    var actions = this.deviceModel.actions;
    for (var indexAction in actions) {
        var action = new $impl.Action(actions[indexAction]);
        if (actions[indexAction].alias) {
            _link(actions[indexAction].alias, this.actions, action);
            messageDispatcher.getRequestDispatcher().registerRequestHandler(endpointId, 'deviceModels/'+this.deviceModel.urn+'/actions/'+actions[indexAction].alias, actionHandler);
        }
        _link(actions[indexAction].name, this.actions, action);
        messageDispatcher.getRequestDispatcher().registerRequestHandler(endpointId, 'deviceModels/'+this.deviceModel.urn+'/actions/'+actions[indexAction].name, actionHandler);
    }

    if (this.deviceModel.formats) {
        this.alerts = this;
        this.dataFormats = this;
        this.deviceModel.formats.forEach(function (format) {
            if (format.type && format.urn) {
                if (format.type === 'ALERT') {
                    self.alerts[format.urn] = format;
                }
                if (format.type === 'DATA') {
                    self.dataFormats[format.urn] = format;
                }
            }
        });
    }

    Object.defineProperty(this, '_',{
        enumerable: false,
        configurable: false,
        writable: false,
        value: {}
    });

    Object.defineProperty(this._, 'offer', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (attributeName, value) {
            let tmp = {attributeName, value};
            // @type {VirtualDeviceAttribute}
            const attribute = self.getAttribute(attributeName);
            debug('VirtualDevice._.offer attribute=' + attribute);

            if (!attribute) {
                throw new Error("No such attribute '" + attributeName +
                    "'.\n\tVerify that the URN for the device model you created " +
                    "matches the URN that you use when activating the device in " +
                    "the Java application.\n\tVerify that the attribute name " +
                    "(and spelling) you chose for your device model matches the " +
                    "attribute you are setting in the Java application.");
            }

            if (!attribute.isSettable()) {
                throw new Error("Attempt to modify read-only attribute '" + attributeName + "'.");
            }

            debug('VirtualDevice.offer self.deviceModel.urn=' + self.deviceModel.urn);

            // @type {DevicePolicy}
            self.devicePolicyManager.getPolicy(self.deviceModel.urn, endpointId).then(devicePolicy => {
                debug('VirtualDevice._.offer = devicePolicy = ' + devicePolicy);
                if (!devicePolicy) {
                    const updateObj = {};
                    updateObj[attributeName] = value;
                    return self.update(updateObj);
                }

                // @type {Set<DevicePolicyFunction>}
                const pipeline = devicePolicy.getPipeline(attributeName);
                debug('VirtualDevice._.offer pipeline=' + pipeline);

                if (!pipeline || (pipeline.size === 0)) {
                    const updateObj = {};
                    updateObj[attributeName] = value;
                    return self.update(updateObj);
                }

                // @type {Set<Map<string, object>>}
                self.getPipelineData(attributeName, function (pipelineData) {
                    debug('VirtualDevice._.offer pipelineData=' + pipelineData);
                    // @type {}
                    const policyValue = self.offer0(attribute.getDeviceModelAttribute(), value,
                        pipeline, pipelineData);

                    debug('VirtualDevice._.offer policyValue = ' + policyValue);

                    if (policyValue) {
                        debug(self.endpointId + ' : Set   : "' + attributeName + '=' +
                            policyValue);

                        // Handle calling offer outside of an update when there are computed metrics
                        // involved.  Call updateFields to ensure the computed metrics get run, and
                        // will put this attribute and computed attributes into one data message.
                        // @type {Pair}
                        const updatedAttributes = new Set();
                        updatedAttributes.add(new Pair(attribute, policyValue));
                        self.updateFields(updatedAttributes);
                    }
                });
            }).catch(error => {
                console.log('Error offering value: ' + error);
            });
        }
    });

    Object.defineProperty(this._, 'updateAttributes', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (attributes) {
            var message = new lib.message.Message();

            message
                .type(lib.message.Message.Type.DATA)
                .source(self.getEndpointId())
                .format(self.deviceModel.urn + ":attributes");

            var storageObjects = [];

            for (var attribute in attributes) {
                var value = attributes[attribute];

                if (attribute in self.attributes) {
                    if (value instanceof lib.StorageObject) {
                        var syncStatus = value.getSyncStatus();

                        if (syncStatus === lib.device.StorageObject.SyncStatus.NOT_IN_SYNC ||
                            syncStatus === lib.device.StorageObject.SyncStatus.SYNC_PENDING) {
                            storageObjects.push(value);
                        }

                        value._.setSyncEventInfo(attribute, self);
                        value.sync();
                    }

                    message.dataItem(attribute,value);
                } else {
                    lib.error('unknown attribute "'+attribute+'"');
                    return;
                }
            }

            storageObjects.forEach(function (storageObject) {
                messageDispatcher._.addStorageDependency(storageObject,
                    message._.internalObject.clientId);
            });

            messageDispatcher.queue(message);
        }
    });

    Object.defineProperty(this._, 'handleStorageObjectStateChange', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (storage) {
            messageDispatcher._.removeStorageDependency(storage);
        }
    });

    messageDispatcher.getRequestDispatcher().registerRequestHandler(endpointId, 'deviceModels/'+this.deviceModel.urn+'/attributes', function (requestMessage) {
        var method = _getMethodForRequestMessage(requestMessage);
        if (!method || (method !== 'PATCH')) {
            return lib.message.Message.buildResponseMessage(requestMessage, 405, {}, 'Method Not Allowed', '');
        }
        if (self.onChange) {
            try {
                var data = null;
                try {
                    data = JSON.parse($port.util.atob(requestMessage.payload.body));
                } catch (e) {
                    return lib.message.Message.buildResponseMessage(requestMessage, 400, {}, 'Bad Request', '');
                }
                if (!data) {
                    return lib.message.Message.buildResponseMessage(requestMessage, 400, {}, 'Bad Request', '');
                }
                var tupleArray = [];
                var index = 0;
                var isDoneForEach = new Array(Object.keys(data).length);
                isDoneForEach.fill(false);
                Object.keys(data).forEach(function(attributeName) {
                    var attribute = self.attributes[attributeName];
                    if (!attribute) {
                        return lib.message.Message.buildResponseMessage(requestMessage, 400, {}, 'Bad Request', '');
                    }
                    var oldValue = attribute.value;
                    if (!attribute._.isValidValue(data[attributeName])) {
                        return lib.message.Message.buildResponseMessage(requestMessage, 400, {}, 'Bad Request', '');
                    }

                    attribute._.getNewValue(data[attributeName], self, function (attributeValue, isSync) {
                        var onChangeTuple = {
                            attribute: attribute,
                            newValue: attributeValue,
                            oldValue: oldValue
                        };
                        if (attribute.onChange) {
                            attribute.onChange(onChangeTuple);
                        }
                        tupleArray.push(onChangeTuple);
                        if (isSync) {
                            isDoneForEach[index] = true;
                        }
                        if (++index === Object.keys(data).length) {
                            // run after last attribute handle
                            self.onChange(tupleArray);

                            var message = new lib.message.Message();
                            message
                                .type(lib.message.Message.Type.DATA)
                                .source(self.getEndpointId())
                                .format(self.deviceModel.urn + ":attributes");
                            Object.keys(data).forEach(function (attributeName1) {
                                var attribute1 = self.attributes[attributeName1];
                                var attributeValue1 = tupleArray.filter(function(tuple) {
                                    return tuple.attribute === attribute1;
                                }, attribute1)[0].newValue;
                                attribute1._.remoteUpdate(attributeValue1);
                                message.dataItem(attributeName1, attributeValue1);
                            });
                            messageDispatcher.queue(message);
                            // one of async attribute handle will be the last
                            // check if at least one async attribute handle was called
                            if (isDoneForEach.indexOf(false) !== -1) {
                                messageDispatcher.queue(lib.message.Message.buildResponseMessage(requestMessage, 200, {}, 'OK', ''));
                            }
                        }
                    });
                });
                if (isDoneForEach.indexOf(false) === -1) {
                    return lib.message.Message.buildResponseMessage(requestMessage, 200, {}, 'OK', '');
                } else {
                    return lib.message.Message.buildResponseWaitMessage();
                }
            } catch (e) {
                return lib.message.Message.buildResponseMessage(requestMessage, 500, {}, 'Internal Server Error', '');
            }
        } else {
            return lib.message.Message.buildResponseMessage(requestMessage, 404, {}, 'Not Found', '');
        }
    });

    // seal object
    Object.preventExtensions(this);
    this.client._.addVirtualDevice(this);
};

lib.device.VirtualDevice.prototype = Object.create(lib.AbstractVirtualDevice.prototype);
lib.device.VirtualDevice.constructor = lib.device.VirtualDevice;

/**
 * @ignore
 *
 * @param {number} window
 * @param {number} slide
 * @param {number} timeZero
 * @param {string} attributeName
 * @param {number} pipelineIndex
 */
lib.device.VirtualDevice.prototype.addScheduledPolicy = function(window, slide, timeZero,
                                                                 attributeName, pipelineIndex)
{
    debug('VirtualDevice.addScheduledPolicy called.');
    debug('VirtualDevice.addScheduledPolicy window = ' + window);
    // @type {ScheduledPolicyDataKey}
    const key = new ScheduledPolicyDataKey(window, slide).toString();
    // @type {ScheduledPolicyData}
    let scheduledPolicyData = this.scheduledPolicies.get(key);
    debug('VirtualDevice.addScheduledPolicy scheduledPolicyData = ' + scheduledPolicyData);

    if (!scheduledPolicyData) {
        scheduledPolicyData = new ScheduledPolicyData(window, slide, timeZero);
        this.scheduledPolicies.set(key, scheduledPolicyData);
        this.timedPolicyThread.addTimedPolicyData(scheduledPolicyData);

        if (!this.timedPolicyThread.isAlive() && !this.timedPolicyThread.isCancelled()) {
            this.timedPolicyThread.start();
        }
    }

    scheduledPolicyData.addAttribute(attributeName, pipelineIndex);
};

/**
 * @ignore
 *
 * @param {Set<string>} updatedAttributes
 * @returns {Set<string>}
 */
lib.device.VirtualDevice.prototype.checkComputedMetrics = function(updatedAttributes) {
    if (!updatedAttributes || (updatedAttributes.size === 0)) {
        return new Set();
    }

    if (this.computedMetricTriggerMap.size === 0) {
        return new Set();
    }

    // This will be the set of attributes that have computed metrics
    // that are triggered by the set of updated attributes.
    // @type {Set<string>}
    let computedAttributes = new Set();

    // key is @type {Set<string>}, value is @type {string}.
    this.computedMetricTriggerMap.forEach((value, key) => {
        // If the set of attributes that the formula refers to
        // is a subset of the updated attributes, then compute
        // the value of the attribute.
        if (key.every(val => updatedAttributes.has(val))) {
            computedAttributes.add(value);
        }
    });

    if (computedAttributes.size > 0) {
        // @type {Iterator<string>}
        let computedAttributesAry = Array.from(computedAttributes.entries());

        for (let i = computedAttributesAry.length - 1; i > 0; i--) {
            // @type {string}
            const attributeName = computedAttributesAry[i];
            const attribute = this.getAttribute(attributeName);

            if (!attribute.isSettable()) {
                debug('Attempt to modify read-only attribute "' + attributeName + '"');
                computedAttributes.delete(attributeName);
                continue;
            }
            // TODO: djmdjmdjm
            // @type {DevicePolicy}
            const devicePolicy = this.devicePolicyManager.getPolicy(this.deviceModel.urn,
                endpointId);

            if (!devicePolicy) {
                continue;
            }

            // @type {Set<DevicePolicyFunction>}
            const pipeline = devicePolicy.getPipeline(attributeName);

            if (!pipeline || (pipeline.size === 0)) {
                continue;
            }

            // @type {Set<Map<string, object>>}
            const pipelineData = this.getPipelineData(attributeName);

            // offer0 returns true if the attribute was set. If the attribute was not set,
            // then remove the attribute name from the list of attributesToCompute.
            // @type {object}
            const policyValue = this.offer0(attribute.getDeviceModelAttribute(), attribute.get(),
                pipeline, pipelineData);

            if (policyValue) {
                debug(endpointId + ' : Set   : ' + attributeName + '" = ' + policyValue);
                attribute.update(policyValue);
            } else {
                computedAttributesAry.splice(i, 1);
            }

            computedAttributes = new Set(computedAttributesAry);
        }
    }

    return computedAttributes;
};

/**
 * This method returns an Alert object created based on the
 * format given as parameter. An Alert object can be used to
 * send alerts to the server by calling the raise method,
 * after all mandatory fields of the alert are set.
 *
 * @param {string} formatUrn - the urn format of the alert spec
 * as defined in the device model that this virtual device represents
 *
 * @returns {iotcs.device.Alert} The Alert instance
 *
 * @memberof iotcs.device.VirtualDevice.prototype
 * @function createAlert
 */
lib.device.VirtualDevice.prototype.createAlert = function (formatUrn) {
    return new lib.device.Alert(this, formatUrn);
};

/**
 * This method returns a Data object created based on the
 * format given as parameter. A Data object can be used to
 * send custom data fields to the server by calling the submit method,
 * after all mandatory fields of the data object are set.
 *
 * @param {string} formatUrn - the urn format of the custom data spec
 * as defined in the device model that this virtual device represents
 *
 * @returns {iotcs.device.Data} The Data instance
 *
 * @memberof iotcs.device.VirtualDevice.prototype
 * @function createData
 */
lib.device.VirtualDevice.prototype.createData = function (formatUrn) {
    return new lib.device.Data(this, formatUrn);
};

/**@inheritdoc */
lib.device.VirtualDevice.prototype.update = function (attributes) {
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

    this._.updateAttributes(attributes);
};

/**
 * @ignore
 *
 * @param {string} attributeName
 * @return {VirtualDeviceAttribute}
 */
lib.device.VirtualDevice.prototype.getAttribute = function(attributeName) {
    // @type {VirtualDeviceAttribute}
    const virtualDeviceAttribute = this.attributeMap.get(attributeName);

    if (!virtualDeviceAttribute) {
        throw new Error('No such attribute "' + attributeName +
            '".\n\tVerify that the URN for the device model you created ' +
            'matches the URN that you use when activating the device in ' +
            'the Java application.\n\tVerify that the attribute name ' +
            '(and spelling) you chose for your device model matches the ' +
            'attribute you are setting in the Java application.');
    }

    return virtualDeviceAttribute;
};

/**
 * @ignore
 * @inheritdoc
 */
lib.device.VirtualDevice.getDeviceModel = function() {
    return self.getDeviceModel();
};

/**
 *
 * @ignore
 * @inheritdoc
 */
lib.device.VirtualDevice.prototype.close = function () {
    if (this.client) {
        this.client._.removeVirtualDevice(this);
    }

    this.endpointId = null;
    this.onChange = function (arg) {};
    this.onError = function (arg) {};
};

/**
 * Returns the pipeline data for the specified attribute.
 *
 * @ignore
 *
 * @param {string} attribute
 * @param {function} callback
 * @return {Set<Map<string, object>>} the pipeline.
 */
lib.device.VirtualDevice.prototype.getPipelineData = function (attribute, callback) {
    debug('VirtualDevice.getPipelineData called.');
    this.devicePolicyManager.getPolicy(this.getDeviceModel().urn, this.getEndpointId())
        .then(devicePolicy =>
    {
        if (!devicePolicy) {
            callback(new Set());
        }

        let pipeline = devicePolicy.getPipeline(attribute);

        if (!pipeline || (pipeline.size === 0)) {
            callback(new Set());
        }

        // {Set<Map<string, object>>}
        let pipelineData = this.pipelineDataCache.get(attribute);

        if (!pipelineData) {
            pipelineData = new Set();
            this.pipelineDataCache.set(attribute, pipelineData);
        }

        // Create missing function maps.
        if (pipelineData.size < pipeline.size) {
            // Create missing function data maps.
            for (let n = pipelineData.size, nMax = pipeline.size; n < nMax; n++) {
                pipelineData.add(new Map());
            }
        }

        callback(pipelineData);
    }).catch(error => {
        console.log('Error getting device policy.  error=' + error);
    });
};

/**
 * @ignore
 * @inheritdoc
 */
lib.device.VirtualDevice.prototype.offer = function (attributeName, value) {
    this._.offer(attributeName, value);
};

/**
 * The main logic for handling a policy pipeline.
 *
 * @ignore
 *
 * @param {DeviceModelAttribute} attribute
 * @param {object} value
 * @param {Set<DevicePolicyFunction>} pipeline
 * @param {Set<Map<string, object>>} pipelineData
 * @return {object} a policy value.
 */
lib.device.VirtualDevice.prototype.offer0 = function (attribute, value, pipeline, pipelineData) {
    debug('VirtualDevice.offer0 called.');
    let attributeName = attribute.getName();
    let policyValue = value;

    if (pipeline && (pipeline.size > 0)) {
        debug('VirtualDevice.offer0 we have a pipeline, size = ' + pipeline.size);
        DeviceFunction.putInProcessValue(this.endpointId, this.deviceModel.urn, attributeName,
            policyValue);

        let pipelineAry = Array.from(pipeline);
        let pipelineDataAry = Array.from(pipelineData);

        for (let index = 0, maxIndex = pipelineAry.length; index < maxIndex; index++) {
            let devicePolicyFunction = pipelineAry[index];
            debug('VirtualDevice.offer0 devicePolicyFunction = ' + devicePolicyFunction);

            /** @type {Map<string, object>} */
            let functionData;

            if (index < pipelineData.size) {
                functionData = pipelineDataAry[index];
            } else {
                functionData = new Map();
                pipelineData.add(functionData);
            }

            // @type {string}
            const key = devicePolicyFunction.getId();
            // @type {Map<string, object}
            const parameters = devicePolicyFunction.getParameters();
            // @type {DeviceFunction}
            const deviceFunction = DeviceFunction.getDeviceFunction(key);
            debug('VirtualDevice.offer0 deviceFunction = ' + deviceFunction);

            if (!deviceFunction) {
                continue;
            }

            if (deviceFunction.apply(this, attributeName, parameters, functionData, policyValue)) {
                debug('VirtualDevice.offer0 in deviceFunction.apply.');

                // @type {object}
                let valueFromPolicy = deviceFunction.get(this, attributeName, parameters,
                    functionData);

                if (valueFromPolicy) {
                    policyValue = valueFromPolicy;

                    DeviceFunction.putInProcessValue(endpointId, this.deviceModel.urn,
                        attributeName, policyValue);
                } else {
                    debug(attributeName + ' got null value from policy.' +
                        deviceFunction.getDetails(parameters));

                    return null;
                }
            } else {
                debug('VirtualDevice.offer0 in deviceFunction.apply else.');
                if (deviceFunction.getId().startsWith("filter")) {
                    debug('VirtualDevice: ' + endpointId + ': offer "' + attributeName +
                        '" = ' + policyValue + ' rejected by policy "' +
                        deviceFunction.getDetails(parameters) + '"');
                }

                return null;
            }

        }
    }

    return policyValue;
};

/**
 * DevicePolicyManager.ChangeListener interface
 *
 * @ignore
 *
 * @param {DevicePolicy} devicePolicy
 * @param {Set<string>} assignedDevices
 */
lib.device.VirtualDevice.prototype.policyAssigned = function(devicePolicy, assignedDevices) {
    debug('VirtualDevice.policyAssigned called.');
    if (!assignedDevices || !assignedDevices.has(this.endpointId)) {
        return;
    }

    debug(this.endpointId + " : Policy assigned : " + devicePolicy.getId());
    // @type {number}
    const timeZero = new Date().getTime();

    devicePolicy.getPipelines().forEach((value, key) => {
        this.policyAssigned2(key, value, timeZero);
    });
};

/**
 *
 * @ignore
 *
 * @param {string} attributeName
 * @param {Set<DevicePolicyFunction>} newPipeline
 * @param {number} timeZero
 */
lib.device.VirtualDevice.prototype.policyAssigned2 = function(attributeName, newPipeline, timeZero) {
    debug('VirtualDevice.policyAssigned2 called.');
    if (newPipeline && (newPipeline.size > 0)) {
        // @type {DevicePolicyFunction[]}
        let newPipelineAry = Array.from(newPipeline);

        for (let index = 0, indexMax = newPipeline.size; index < indexMax; index++) {
            // @type {DevicePolicyFunction}
            const pipelineFunction = newPipelineAry[index];
            // @type {string}
            const id = pipelineFunction.getId();
            // @type {Map<string, object}
            const parameters = pipelineFunction.getParameters();
            // @type {number}
            const newWindow = DeviceFunction.getWindow(parameters);

            if (newWindow > -1 && ('eliminateDuplicates' !== id)) {
                // @type {number}
                const newSlide = DeviceFunction.getSlide(parameters, newWindow);
                this.addScheduledPolicy(newWindow, newSlide, timeZero, attributeName, index);
            }

            // If the first policy in the chain is a computed metric,
            // see if it refers to other attributes.
            if ((index === 0) && ('computedMetric' === id)) {
                // @type {string}
                const formula = parameters.get('formula');
                // @type {Set<string>}
                const triggerAttributes = new Set();
                // @type {number}
                let pos = formula.indexOf('$(');

                while (pos !== -1) {
                    // @type {number}
                    const end = formula.indexOf(')', pos + 1);

                    if ((pos === 0) || (formula.charAt(pos - 1) !== '$$')) {
                        // @type {string}
                        const attr = formula.substring(pos + '$('.length, end);

                        if (!attr.equals(attributeName)) {
                            triggerAttributes.add(attr);
                        }
                    }

                    pos = formula.indexOf('$(', end + 1);
                }

                if (triggerAttributes.size > 0) {
                    this.computedMetricTriggerMap.add(new Pair(triggerAttributes, attributeName));
                }
            }
        }
    }
};

/**
 *
 * @ignore
 *
 * @param {DevicePolicy} devicePolicy
 * @param {Set<string>} unassignedDevices
 */
lib.device.VirtualDevice.prototype.policyUnassigned = function(devicePolicy, unassignedDevices) {
    if (!unassignedDevices || !unassignedDevices.has(this.getEndpointId())) {
        return;
    }

    debug(this.getEndpointId() + " : Policy un-assigned : " + devicePolicy.getId());

    // @type {Set<Pair<VirtualDeviceAttribute<VirtualDevice, object>, object>>}
    const updatedAttributes = new Set();

    devicePolicy.getPipelines().forEach((value, key) => {
        this.policyUnassigned2(updatedAttributes, key, value);
    });

    if (updatedAttributes.size > 0) {
        // Call updateFields to ensure the computed metrics get run,
        // and will put all attributes into one data message.
        this.updateFields(updatedAttributes);
    }
};

/**
 *
 * @ignore
 *
 * @param {Set<Pair<VirtualDeviceAttribute, object>>} updatedAttributes
 * @param {string} attributeName
 * @param {Set<DevicePolicyFunction>} oldPipeline
 */
lib.device.VirtualDevice.prototype.policyUnassigned2 = function(updatedAttributes, attributeName,
                                                                oldPipeline)
{
    if (oldPipeline && (oldPipeline.size > 0)) {
        const oldPipelineAry = Array.from(oldPipeline);
        // The order in which the oldPipeline is finalized is important.
        // First, remove any scheduled policies so they don't get executed. Any
        // pending data will be committed in the next step.
        // Second, commit any "in process" values. This may cause a computedMetric
        // to be triggered.
        // Third, remove any computed metric triggers.
        // Lastly, remove any data for this pipeline from the policy data cache
        for (let index = 0, indexMax = oldPipelineAry.length; index < indexMax; index++) {
            // @type {DevicePolicyFunction}
            const oldPipelineFunction = oldPipelineAry[index];
            // @type {string}
            const id = oldPipelineFunction.getId();
            // @type {Map<string, object>}
            const parameters = oldPipelineFunction.getParameters();
            // @type {number}
            const window = DeviceFunction.getWindow(parameters);

            if ((window > -1) && ('eliminateDuplicates' !== id)) {
                // @type {number}
                const slide = DeviceFunction.getSlide(parameters, window);
                this.removeScheduledPolicy(slide, attributeName, index, window);
            }
        }

        // Commit values from old pipeline.
        // @type {Set<Map<string, object>>}
        this.getPipelineData(attributeName, function(pipelineData) {
            if (pipelineData && (pipelineData.size > 0)) {
                if (DevicePolicy.ALL_ATTRIBUTES !== attributeName) {
                    this.processExpiredFunction2(updatedAttributes, attributeName, oldPipeline,
                        pipelineData);
                } else {
                    this.processExpiredFunction1(oldPipeline, pipelineData);
                }
            }

            if (attributeName) {
                // Remove this attribute from the computedMetricTriggerMap.
                this.computedMetricTriggerMap.forEach(computedMetricTriggerPair => {
                    if (attributeName === computedMetricTriggerPair.getValue()) {
                        this.computedMetricTriggerMap.delete(computedMetricTriggerPair);
                    }
                });
            }

            // Remove data from cache.
            this.pipelineDataCache.delete(attributeName);
        });
    }
};

/**
 * Routine for handling invocation of a policy function when the window's
 * slide expires. This routine gets the value of the function, and then
 * processes the remaining functions in the pipeline (if any).
 *
 * @ignore
 *
 * @param {Set<DevicePolicyFunction>} pipeline
 * @param {Map<string, object>} pipelineData
 */
lib.device.VirtualDevice.prototype.processExpiredFunction1 = function(pipeline, pipelineData) {
    debug('VirtualDevice.processExpiredFunction1 called.');

    if (!pipeline || pipeline.size === 0) {
        return;
    }

    try {
        const pipelineAry = Array.from(pipeline);
        const pipelineDataAry = Array.from(pipelineData);
        // @type {DevicePolicyFunction}
        const devicePolicyFunction = pipelineAry[0];
        // @type {string}
        const functionId = devicePolicyFunction.getId();
        // @type {Map<string, object}
        const config = devicePolicyFunction.getParameters();
        // @type {Map<string, object>}
        const data = pipelineDataAry[0];
        // @type {DeviceFunction}
        const deviceFunction = DeviceFunction.getDeviceFunction(functionId);

        if (!deviceFunction) {
            console.log('Device function "' + functionId + '" not found.');
            return;
        }

        // @type {object}
        let value = deviceFunction.get(this, null, config, data);

        if (value && (pipeline.size > 1)) {
            // Process remaining policies in the pipeline.
            value = this.offer0(null, value, pipeline.subList(1, pipeline.size),
                pipelineData.subList(1, pipelineData.size));
        }

        if (value) {
            // @type {Set<Pair<Message, StorageObjectImpl>>}
            const pairs = value;

            if (pairs.size === 0) {
                return;
            }

            // @type {Message[]}
            let messages = new Array(pairs.size);

            for (let n = 0, nMax = pairs.size; n < nMax; n++) {
                // @type {Pair<Message, StorageObjectImpl>}
                const messagePair = pairs.get(n);
                messages[n] = messagePair.getKey();
                // @type {StorageObject}
                const storageObject = messagePair.getValue();

                if (storageObject) {
                    this.messageDispatcher.addStorageObjectDependency(storageObject,
                        messages[n].getClientId());

                    storageObject.sync();
                }
            }

            this.messageDispatcher.queue(messages);

        }
    } catch (error) {
        console.log('Error occurred: ' + error);
    }
};

/**
 * Routine for handling invocation of a policy function when the window's
 * slide expires. This routine gets the value of the function, and then
 * processes the remaining functions in the pipeline (if any).
 *
 * @ignore
 *
 * @param {Set<Pair<VirtualDeviceAttribute<VirtualDevice, object>, object>>} updatedAttributes
 * @param {string} attributeName
 * @param {Set<DevicePolicyFunction>} pipeline
 * @param {Set<Map<string, object>>} pipelineData
 */
lib.device.VirtualDevice.prototype.processExpiredFunction2 = function(updatedAttributes,
    attributeName, pipeline, pipelineData)
{
    debug('VirtualDevice.processExpiredFunction2 called.');

    if (!pipeline || (pipeline.size === 0)) {
        return;
    }

    try {
        // Convert the pipeline and pipeline data Sets to arrays so we can index from them.
        let pipelineDataAry = Array.from(pipelineData);
        let pipelineAry = Array.from(pipeline);
        // @type {VirtualDeviceAttribute}
        const attribute = this.getAttribute(attributeName);
        // @type {DeviceModelAttribute}
        const deviceModelAttribute = attribute.getDeviceModelAttribute();
        // @type {DevicePolicyFunction}
        const devicePolicyFunction = pipelineAry[0];
        // @type {string}
        const functionId = devicePolicyFunction.getId();
        // @type {Map<string, object>}
        const config = devicePolicyFunction.getParameters();
        // @type {Map<string, object>}
        const data = pipelineDataAry[0];
        // @type {DeviceFunction}
        const deviceFunction = DeviceFunction.getDeviceFunction(functionId);

        if (!deviceFunction) {
            console.log('Device function "' + functionId + '" not found.');
            return;
        }

        // @type {object}
        let value = deviceFunction.get(null, attributeName, config, data);

        if (value && pipeline.size > 1) {
            // Process remaining policies in the pipeline.
            value = this.offer0(deviceModelAttribute, value, pipeline.subList(1, pipeline.size),
                pipelineData.subList(1, pipelineData.size));
        }

        if (value) {
            // @type {object}
            let policyValue = value;

            if (policyValue) {
                debug('VirtualDevice.processExpiredFunction 2 adding to updatedAttributes:"' + attributeName + '" = ' + policyValue);
                updatedAttributes.add(new Pair(attribute, policyValue));
            }
        }
    } catch (error) {
        console.log('Error occurred: ' + error);
    }
};

/**
 * Called from updateFields.
 *
 * @ignore
 *
 * @param {Set<Pair<VirtualDeviceAttribute, object>>} updatedAttributes
 */
lib.device.VirtualDevice.prototype.processOnChange1 = function(updatedAttributes) {
    debug('VirtualDevice.processOnChange1 called.');
    if (updatedAttributes.size === 0) {
        return;
    }

    // @type {Set<VirtualDeviceAttribute>}
    const keySet = new Set();
    let dataMessage = new lib.message.Message();
    dataMessage.type(dcl.message.Message.Type.DATA);
    let storageObject = new WritableValue();

    // Use for here so we can break out of the loop.
    // @type {Pair<VirtualDeviceAttribute, object>}
    for (let entry of updatedAttributes) {
        // @type {VirtualDeviceAttribute}
        const attribute = entry.getKey();
        keySet.add(attribute);
        // @type {object}
        const newValue = entry.getValue();

        try {
            this.processOnChange2(dataMessage, attribute, newValue, storageObject);
        } catch(error) {
            console.log(error);
            break;
        }
    }

    dataMessage.type(dcl.message.Message.Type.DATA);

    try {
        this.queueMessage(dataMessage, storageObject.getValue());
    } catch(error) {
        console.log('Message queue error: ' + error);
    }
};

/**
 *
 * @ignore
 *
 * @param {lib.message.Message} dataMessage
 * @param {VirtualDeviceAttribute} virtualDeviceAttribute
 * @param {object} newValue
 * @param {WritableValue} storageObject
 */
lib.device.VirtualDevice.prototype.processOnChange2 = function(dataMessage, virtualDeviceAttribute,
                                                              newValue, storageObject)
{
    debug('VirtualDevice.processOnChange2 called.');
    // @type {DeviceModelAttribute}
    const deviceModelAttribute = virtualDeviceAttribute.getDeviceModelAttribute();
    // @type {string}
    const attributeName = deviceModelAttribute.getName();

    dataMessage
    .format(this.deviceModel.urn + ":attributes")
    .source(this.endpointId);

    switch (deviceModelAttribute.getType()) {
        case 'INTEGER':
        case 'NUMBER':
        case 'STRING':
            dataMessage.dataItem(attributeName, newValue);
            break;
        case 'URI':
            if (newValue instanceof StorageObject) {
                if ((newValue.getSyncStatus() === lib.device.StorageObject.SyncStatus.NOT_IN_SYNC) ||
                    (newValue.getSyncStatus() === lib.device.StorageObject.SyncStatus.SYNC_PENDING))
                {
                    storageObject.setValue(newValue);
                }

                newValue._.setSyncEventInfo(this, attributeName);
            }

            dataMessage.dataItem(attributeName, newValue.getUri());
            break;
        case 'DATETIME':
            if (newValue instanceof Date) {
                dataMessage.dataItem(attributeName, newValue.getTime());
            } else if (newValue instanceof Number) {
                dataMessage.dataItem(attributeName, newValue);
            }

            break;
        default:
            console.log('Unknown attribute type: ' + deviceModelAttribute.getType());
            throw new Error("Unknown attribute type " + deviceModelAttribute.getType());
    }
};

/**
 *
 * @ignore
 *
 * @param {Message} message
 * @param {StorageObject} storageObject
 */
lib.device.VirtualDevice.prototype.queueMessage = function(message, storageObject) {
    debug('VirtualDevice.queueMessage called.');
    // @type {Pair<Message,StorageObjectImpl>}
    const pair = new Pair(message, storageObject);
    // @type {Pair<Message, StorageObjectImpl>[]}
    let pairs = [];
    pairs.push(pair);

    // @type {string}
    const deviceModelUrn = this.deviceModel.urn;
    const self = this;

    // @type {DevicePolicy}
    this.devicePolicyManager.getPolicy(this.deviceModel.urn, this.endpointId).then(devicePolicy => {
        // Handling of device model level policies here...
        if (devicePolicy && devicePolicy.getPipeline(DevicePolicy.ALL_ATTRIBUTES)) {
            // Some policies are negated by an alert of a given severity
            // (batching policies, for example)
            // @type {AlertMessage.Severity}
            let alertMessageSeverity = null;

            if (message._.internalObject.type === dcl.message.Message.Type.ALERT) {
                // @type {AlertMessage}
                alertMessageSeverity = message.getSeverity();
            }

            // @type {Set<DevicePolicyFunction>}
            const pipeline = devicePolicy.getPipeline(DevicePolicy.ALL_ATTRIBUTES);
            // @type {Set<Map<string, object>>}
            const pipelineData = this.getPipelineData(DevicePolicy.ALL_ATTRIBUTES);

            for (let index = 0, maxIndex = pipeline.size; index < maxIndex; index++) {
                // @type {DevicePolicyFunction}
                const devicePolicyFunction = pipeline.get(index);
                // @type {string}
                const id = devicePolicyFunction.getId();
                // @type {Map<string, object>}
                let parameters = devicePolicyFunction.getParameters();
                // @type {DeviceFunction}
                const deviceFunction = DeviceFunction.getDeviceFunction(id);

                if (!deviceFunction) {
                    continue;
                }

                // @type {boolean}
                let alertOverridesPolicy;

                if (alertMessageSeverity) {
                    // @type {AlertMessage.Severity}
                    let configuredSeverity = dcl.message.Message.Type.ALERT.CRITICAL;
                    // @type {string}
                    const criterion = parameters.get("alertSeverity");

                    if (criterion) {
                        try {
                            configuredSeverity =  AlertMessage.Severity.valueOf(criterion);
                        } catch (error) {
                            configuredSeverity = dcl.message.Message.Type.ALERT.CRITICAL;
                        }
                    }

                    alertOverridesPolicy = configuredSeverity.compareTo(alertMessageSeverity) <= 0;
                } else {
                    alertOverridesPolicy = false;
                }

                // @type {Map<string, object>}
                let functionData;

                if (index < pipelineData.size) {
                    functionData = pipelineData.get(index);
                } else {
                    functionData = new Map();
                    pipelineData.add(functionData);
                }

                if (deviceFunction.apply(this, null, parameters, functionData, pair) ||
                    alertOverridesPolicy)
                {
                    // If the policy was scheduled...
                    // @type {number}
                    const window = DeviceFunction.getWindow(parameters);

                    if (window > 0) {
                        // @type {number}
                        const slide = DeviceFunction.getSlide(parameters, window);
                        // @type {ScheduledPolicyDataKey}
                        const key = new ScheduledPolicyDataKey(window, slide).toString();
                        // @type {ScheduledPolicyData}
                        const scheduledPolicyData = this.scheduledPolicies.get(key);
                        // @type {number}
                        const timeZero = new Date().getTime();

                        if (scheduledPolicyData) {
                            // @type {Set<Pair<VirtualDeviceAttribute<VirtualDevice, Object>, Object>>}
                            const updatedAttributes = new Set();
                            scheduledPolicyData.processExpiredFunction(this, updatedAttributes, timeZero);

                            if (updatedAttributes.size > 0) {
                                this.updateFields(updatedAttributes);
                            }

                            return;
                        }
                    }

                    // @type {Set<Pair>}
                    let value = deviceFunction.get(this, null, parameters, functionData);
                    pairs = Array.from(value);

                    debug('VirtualDevice: ' + endpointId + ' dispatching ' + pairs.length +
                        ' messages per policy "' + deviceFunction.getDetails(parameters) + '"');
                } else {
                    return;
                }
            }
        }

        try {
            // @type {Message[]}
            let messages = new Array(pairs.length);
            // // @type {MessageDispatcher}
            // let messageDispatcher = new lib.device.util.MessageDispatcher(client);

            for (let n = 0; n < messages.length; n++) {
                messages[n] = pairs[n].getKey();
                // @type {StorageObject}
                let storageObject = pairs[n].getValue();

                if (storageObject) {
                    self.messageDispatcher._.addStorageDependency(storageObject,
                        message._.internalObject.clientId);

                    storageObject.sync();
                }
            }

            messages.forEach(message => {
                debug('VirtualDevice.queueMessage, sending message: ' + util.inspect(message));
                self.messageDispatcher.queue(message);
            });
        } catch (error) {
            console.log('Error: ' + error);
        }
    }).catch(error => {
        console.log('Error getting device policy: ' + error);
    });
};

/**
 *
 * @ignore
 *
 * @param {number} slide
 * @param {string} attributeName
 * @param {number} pipelineIndex
 * @param {number} window
 */
lib.device.VirtualDevice.prototype.removeScheduledPolicy = function(slide, attributeName,
                                                                    pipelineIndex, window)
{
    debug('removeScheduledPolicy called.');
    // @type {ScheduledPolicyDataKey}
    const key = new ScheduledPolicyDataKey(window, slide).toString();
    // @type {ScheduledPolicyData}
    const scheduledPolicyData = this.scheduledPolicies.get(key);

    if (scheduledPolicyData) {
        scheduledPolicyData.removeAttribute(attributeName, pipelineIndex);

        if (scheduledPolicyData.isEmpty()) {
            this.scheduledPolicies.delete(key);
            this.timedPolicyThread.removeTimedPolicyData(scheduledPolicyData);
        }
    }
};

/**
 * Set all the attributes in an update batch. Errors are handled in the set call, including calling
 * the on error handler.
 *
 * @ignore
 *
 * {@inheritDoc}
 * @param {Set<Pair<VirtualDeviceAttribute, object>>} updatedAttributes
 */
lib.device.VirtualDevice.prototype.updateFields = function(updatedAttributes) {
    debug('VirtualDevice.updateFields called.');
    // @type {Set<string}
    const updatedAttributesToProcess = new Set();
    let updatedAttributesAry = Array.from(updatedAttributes);

    for (let i = updatedAttributesAry.length - 1; i >= 0; i--) {
        const attribute = updatedAttributesAry[i].getKey();
        // @type {string}
        const attributeName = attribute.getDeviceModelAttribute().getName();

        try {
            // Here, we assume:
            // 1. That attribute is not null. If the attribute were not found
            //    an exception would have been thrown from the VirtualDevice
            //    set(String attributeName, T value) method.
            // 2. That the set method validates the value. The call to
            //    update here should not throw an exception because the
            //    value is bad.
            // 3. The author of this code knew what he was doing.
            if (!attribute.update(updatedAttributesAry[i].getValue())) {
                updatedAttributesAry.splice(i, 1);
            } else {
                updatedAttributesToProcess.add(attributeName);
            }
        } catch (error) {
            console.log('Error updating attributes: ' + error);
        }

        DeviceFunction.removeInProcessValue(this.endpointId, this.deviceModel.urn, attributeName);
    }

    // Here is the check to see if the updated attributes will trigger computedMetrics.
    // The returned set is the attributes whose computedMetrics resulted in an
    // attribute.update(value). Those attributes are added to the list of updated attributes
    // so that they are included in the resulting data message.
    // @type {Set<string>}
    const computedMetrics = this.checkComputedMetrics(updatedAttributesToProcess);

    computedMetrics.forEach(attr => {
        // @type {VirtualDeviceAttribute}
        const attribute = this.getAttribute(attr);
        // @type {object}
        const value = attribute.get();
        // @type {Pair<VirtualDeviceAttribute<VirtualDevice, Object>, Object>}
        const pair = new Pair(attribute, value);
        updatedAttributes.add(pair);
    });

    this.processOnChange1(updatedAttributes);
};

// Callback JSDocs.
/**
 * Callback for iotcs.device.VirtualDevice.onError with the error.
 *
 * @callback iotcs.device.VirtualDevice~onErrorCallback
 *
 * @param {string} error - The error when sending this Alert.
 */
