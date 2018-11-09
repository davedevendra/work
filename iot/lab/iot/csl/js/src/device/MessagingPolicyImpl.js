/**
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

const util = require('util');

class MessagingPolicyImpl {
    // Instance "variables"/properties...see constructor.

    /**
     *
     * @param {$impl.DirectlyConnectedDevice} directlyConnectedDevice
     */
    constructor(directlyConnectedDevice) {
        // Instance "variables"/properties.
        /**
         * Key is device model urn, value is attribute -> trigger attributes
         * (a trigger attribute is a referenced attribute in a computedMetric formula).
         *
         * @type {Map<String, Map<String, Set<String>>>}
         */
        this.computedMetricTriggers = new Map();
        /**
         * Map from deviceModelUrn -> DeviceAnalog
         * We need more than one DeviceAnalog because the DCD can have more than one device model.
         *
         * @type {Map<String, DeviceAnalog>}
         */
        this.deviceAnalogMap = new Map();
        this.directlyConnectedDevice = directlyConnectedDevice;
        /**
         * {Set<Message>}
         */
        this.messagesFromExpiredPolicies = new Set();
        /**
         * Data pertaining to this virtual device and its attributes for computing policies. The key
         * is attribute name (or null for the device model policies), value is a list. Each element
         * in the list corresponds to a function in the pipeline for the attribute, and the map is
         * used by the function to hold data between calls.  Note that there is a 1:1 correspondence
         * between the pipeline configuration data for the attribute, and the pipeline data for the
         * attribute.
         *
         * @type {Map<String, Set<Map<String, Object>>>}
         */
        this.pipelineDataCache = new Map();
        // Instance "variables"/properties.

        this.deviceFunctionHelper = null;
        let num = 100000;
        let a = 1, b = 0, temp;

        while (num >= 0) {
            temp = a;
            a = a + b;
            b = temp;
            num--;
        }
    }

    // Apply policies that are targeted to an attribute
    /**
     * @param {iotcs.message.Message} dataMessage a data message to apply attribute polices to.
     * @param {number} currentTimeMillis the current time in milliseconds, use for expiring policies.
     * @resolve {iotcs.message.Message} an attribute-processed data message.
     * @return Promise
     */
    applyAttributePolicies(dataMessage, currentTimeMillis) {
        return new Promise((resolve, reject) => {
            debug('applyAttributePolicies called.');
            // A data message format cannot be null or empty (enforced in DataMessage(Builder) constructor.
            let format = dataMessage._.internalObject.payload.format;
            // A data message format cannot be null or empty.
            let deviceModelUrn = format.substring(0, format.length - ":attributes".length);
            // Use var so we can reference it from within the callbacks.
            var messagingPolicyImpl = this;
            var computedMetricTriggersTmp = this.computedMetricTriggers;
            var dataMessageVar = dataMessage;

            this.directlyConnectedDevice.getDeviceModel(deviceModelUrn, function (response, error) {
                debug('applyAttributePolicies getDeviceModel response = ' + response +
                    ', error = ' + error);

                if (error) {
                    console.log('-------------Error getting humidity sensor device model-------------');
                    console.log(error.message);
                    console.log('--------------------------------------------------------------------');
                    return;
                }

                let deviceModelJson = JSON.stringify(response, null, 4);
                let deviceModel = DeviceModelParser.fromJson(deviceModelJson);
                debug('applyAttributePolicies getDeviceModel deviceModel = ' + deviceModel);

                if (!deviceModel) {
                    resolve(dataMessageVar);
                    return;
                }

                let endpointId = dataMessage._.internalObject.source;

                let devicePolicyManager =
                    DevicePolicyManager.getDevicePolicyManager(messagingPolicyImpl.directlyConnectedDevice.getEndpointId());

                if (!devicePolicyManager) {
                    devicePolicyManager =
                        new DevicePolicyManager(messagingPolicyImpl.directlyConnectedDevice);
                }

                devicePolicyManager.getPolicy(deviceModelUrn, endpointId).then(devicePolicy => {
                    debug('applyAttributePolicies.getPolicy devicePolicy = ' + devicePolicy);
                    let deviceAnalog;

                    if (messagingPolicyImpl.deviceAnalogMap.has(endpointId)) {
                        deviceAnalog = messagingPolicyImpl.deviceAnalogMap.get(endpointId);
                    } else {
                        deviceAnalog = new DeviceAnalog(messagingPolicyImpl.directlyConnectedDevice,
                            deviceModel, endpointId);

                        messagingPolicyImpl.deviceAnalogMap.set(endpointId, deviceAnalog);
                    }

                    let triggerMap;

                    if (!computedMetricTriggersTmp.has(deviceModelUrn)) {
                        triggerMap = new Map();
                        computedMetricTriggersTmp.set(deviceModelUrn, triggerMap);
                        // @type {Map<String, DeviceModelAttribute>}
                        let deviceModelAttributeMap = deviceModel.getDeviceModelAttributes();

                        deviceModelAttributeMap.forEach(deviceModelAttribute => {
                            let attributeName = deviceModelAttribute.name;

                            if (!devicePolicy) {
                                return; // continue
                            }

                            // @type {Set<DevicePolicy.Function>}
                            let pipeline = devicePolicy.getPipeline(attributeName);
                            debug('applyAttributePolicies getDeviceModel.getPolicy pipeline = '
                                + pipeline);

                            if (!pipeline || pipeline.size === 0) {
                                return; // continue
                            }

                            // If a computedMetric is the first function in the pipeline,
                            // then see if the formula refers to other attributes. If so,
                            // then try this pipeline after all others.
                            // @type {DevicePolicy.Function}
                            let devicePolicyFunction = pipeline.values().next().value;
                            let deviceFunctionId = devicePolicyFunction.getId();
                            // @type {Map<String, ?>}
                            let parameters = devicePolicyFunction.getParameters();

                            if ('computedMetric' === deviceFunctionId) {
                                let formula = parameters.get('formula');
                                // @type {Set<String>}
                                let triggerAttributes = new Set();
                                let pos = formula.indexOf('$(');

                                while (pos !== -1) {
                                    let end = formula.indexOf(')', pos + 1);

                                    if (pos === 0 || formula.charAt(pos - 1) !== '$$') {
                                        let attr = formula.substring(pos + '$('.length, end);

                                        if (attr !== attributeName) {
                                            triggerAttributes.add(attr);
                                        }
                                    }

                                    pos = formula.indexOf('$(', end + 1);
                                }

                                if (triggerAttributes.size > 0) {
                                    triggerMap.set(attributeName, triggerAttributes);
                                }
                            }
                        });

                        debug('MessagingPolicyImpl.applyAttributePolicies about to call applyAttributePolicies2.');

                        let message = messagingPolicyImpl.applyAttributePolicies2(dataMessage,
                            deviceModel, devicePolicy, deviceAnalog, triggerMap, format,
                            messagingPolicyImpl, currentTimeMillis);

                        debug('MessagingPolicyImpl.applyAttributePolicies message = ' +
                            message);

                        resolve(message);
                    } else {
                        triggerMap = computedMetricTriggersTmp.get(deviceModelUrn);

                        let message = messagingPolicyImpl.applyAttributePolicies2(dataMessage,
                            deviceModel, devicePolicy, deviceAnalog, triggerMap, format,
                            messagingPolicyImpl, currentTimeMillis);

                        resolve(message);
                    }
                }).catch(error => {
                    console.log('Error getting device policy: ' + error);
                });
            });
        });
    }

    /**
     *
     * @param dataMessage
     * @param deviceModel
     * @param devicePolicy
     * @param deviceAnalog
     * @param triggerMap
     * @param messagingPolicyImpl
     * @param currentTimeMillis
     * @return {Message} a message.
     */
    applyAttributePolicies2(dataMessage, deviceModel, devicePolicy, deviceAnalog, triggerMap,
                            format, messagingPolicyImpl, currentTimeMillis)
    {
        debug('applyAttributePolicies2 called.');
        // @type {[key] -> value}
        let dataMessageDataItemsKeys = Object.keys(dataMessage._.internalObject.payload.data);

        // DataItems resulting from policies. {Set<DataItem<?>>}
        let policyDataItems = new Set();

        // DataItems that have no policies. {Set<DataItem<?>>}
        let skippedDataItems = new Set();

        // If no policies are found, we will return the original message.
        let noPoliciesFound = true;

        dataMessageDataItemsKeys.forEach(attributeName => {
            debug('applyAttributePolicies2 attributeName = ' + attributeName);
            let attributeValue = dataMessage._.internalObject.payload.data[attributeName];

            if (!attributeName) {
                skippedDataItems.add(attributeName);
                return; // continue
            }

            if (!devicePolicy) {
                deviceAnalog.setAttributeValue(attributeName, attributeValue);
                skippedDataItems.add(new DataItem(attributeName, attributeValue));
                return; // continue
            }

            // @type {List<DevicePolicy.Function>}
            let pipeline = devicePolicy.getPipeline(attributeName);
            debug('applyAttributePolicies2 pipeline = ' + pipeline);

            // No policies for this attribute?  Retain the data item.
            if (!pipeline || pipeline.size === 0) {
                deviceAnalog.setAttributeValue(attributeName, attributeValue);
                skippedDataItems.add(new DataItem(attributeName, attributeValue));
                return; // continue
            }

            noPoliciesFound = false;

            // If this is a computed metric, skip it for now.
            if (triggerMap.has(attributeValue)) {
                return; // continue
            }

            let policyDataItem = messagingPolicyImpl.applyAttributePolicy(deviceAnalog,
                attributeName, attributeValue, pipeline, currentTimeMillis);

            debug('applyAttributePolicies2 policyDataItem from applyAttributePolicy = ' +
                util.inspect(policyDataItem));

            if (policyDataItem) {
                policyDataItems.add(policyDataItem);
            }
        });

        // If no policies were found, return the original message.
        if (noPoliciesFound) {
            return dataMessage;
        }

        // If policies were found, but there are no policyDataItem's and no skipped data items, then return null.
        if (policyDataItems.size === 0 && skippedDataItems.size === 0) {
            return null;
        }

        // This looks like a good place to check for computed metrics too.
        if (policyDataItems.size > 0) {
            messagingPolicyImpl.checkComputedMetrics(policyDataItems, deviceAnalog,
                triggerMap, currentTimeMillis);

                debug('applyAttributePolicies2 after checkComputedMetrics, policyDataItems = ' +
                    util.inspect(policyDataItems));
        }

        let message = new lib.message.Message();

        message
               .format(format)
               .priority(dataMessage._.internalObject.priority)
               .source(dataMessage._.internalObject.source)
               .type(dcl.message.Message.Type.DATA);

        policyDataItems.forEach(dataItem => {
            let dataItemKey = dataItem.getKey();
            let dataItemValue = dataItem.getValue();

            // For Set items, we need to get each value.
            if (dataItemValue instanceof Set) {
                dataItemValue.forEach(value => {
                    message.dataItem(dataItemKey, value);
                });
            } else {
                message.dataItem(dataItemKey, dataItemValue);
            }
        });

        skippedDataItems.forEach(dataItem => {
            let dataItemKey = dataItem.getKey();
            let dataItemValue = dataItem.getValue();

            // For Set items, we need to get each value.
            if (dataItemValue instanceof Set) {
                dataItemValue.forEach(value => {
                    message.dataItem(dataItemKey, value);
                });
            } else {
                message.dataItem(dataItemKey, dataItemValue);
            }
        });

        return message;
    }

    /**
     *
     * @param {DeviceAnalog} deviceAnalog
     * @param {DataItem} dataItem
     * @param {Set<DevicePolicyFunction>} pipeline
     * @param {number} currentTimeMillis
     * @return {DataItem}
     */
    applyAttributePolicy(deviceAnalog, attributeName, attributeValue, pipeline, currentTimeMillis) {
        debug('applyAttributePolicy called, attributeName = ' + attributeName + ', attributeValue = ' + attributeValue);
        let deviceModel = deviceAnalog.getDeviceModel();
        // Get or create the pipeline data for this attribute.
        // @type {Set<Map<String, Object>>}
        let pipelineData = this.pipelineDataCache.get(attributeName);

        if (!pipelineData) {
            // @type {List<Map<String, Object>>}
            pipelineData = new Set();
            this.pipelineDataCache.set(attributeName, pipelineData);
        }

        DeviceFunction.putInProcessValue(deviceAnalog.getEndpointId(), deviceModel.getUrn(),
            attributeName, attributeValue);

        // Convert the pipeline and pipeline data Sets to arrays so we can index from them.
        let pipelineDataAry = Array.from(pipelineData);
        let pipelineAry = Array.from(pipeline);

        // Process each pipeline "function".
        for (let index = 0; index < pipelineAry.length; index++) {
            // @type {DevicePolicyFunction}
            let pipelineFunction = pipelineAry[index];
            // @type {Map<String, Object>}
             let functionData;

            if (index < pipelineDataAry.length) {
                functionData = pipelineDataAry[index];
            } else {
                functionData = new Map();
                pipelineData.add(functionData);
                pipelineDataAry.push(functionData);
            }

            // @type {string}
            let functionId = pipelineFunction.getId();
            // @type {Map<String, ?>}
            let parameters = pipelineFunction.getParameters();
            // @type {DeviceFunction}
            let deviceFunction = DeviceFunction.getDeviceFunction(functionId);

            if (!deviceFunction) {
                continue;
            }

            // @type {boolean}
            let windowExpired;
            // @type {number}
            let window = DeviceFunction.getWindow(parameters);
            debug('MessagingPolicyImpl.applyAttributePolicy window = ' + window);

            if (window > 0) {
                // This could be more succinct, but it makes the key easy to read in the debugger.
                // @type {string}
                let k = deviceModel.getUrn() + ':' + attributeName + ':' + deviceFunction.getId();
                // @type {number}
                let t0 = MessagingPolicyImpl.windowMap.get(k);

                if (!t0) {
                    t0 = currentTimeMillis;
                    MessagingPolicyImpl.windowMap.set(k, t0);
                }

                windowExpired = (t0 + window) <= currentTimeMillis;

                if (windowExpired) {
                    MessagingPolicyImpl.windowMap.set(k, currentTimeMillis);
                }
            } else {
                windowExpired = false;
            }

            debug('applyAttributePolicy applying device function: ' + deviceFunction);

            if (deviceFunction.apply(deviceAnalog, attributeName, parameters, functionData,
                    attributeValue) || windowExpired)
            {
                debug('applyAttributePolicy windowExpired');
                const valueFromPolicy = deviceFunction.get(deviceAnalog, attributeName, parameters,
                    functionData);

                debug('applyAttributePolicy valueFromPolicy = ' + util.inspect(valueFromPolicy));

                if (valueFromPolicy) {
                    debug('applyAttributePolicy in valueFromPolicy.');
                    attributeValue = valueFromPolicy;

                    debug('applyAttributePolicy in valueFromPolicy attributeValue = ' +
                        attributeValue);

                    DeviceFunction.putInProcessValue(deviceAnalog.getEndpointId(),
                        deviceModel.getUrn(), attributeName, attributeValue);
                } else {
                    console.log(attributeName + " got null value from policy" +
                        deviceFunction.getDetails(parameters));

                    break;
                }
            } else {
                // apply returned false.
                attributeValue = null;
                break;
            }
        }

        // After the policy loop, if the attributeValue is null, then the policy
        // either filtered out the attribute, or the policy parameters have not
        // been met (e.g., sampleQuality rate is not met). If it is not null,
        // then create a new DataItem to replace the old in the data message.
        // @type {DataItem}
        let policyDataItem;
        debug('applyAttributePolicy attributeValue = ' + attributeValue);
        if (attributeValue) {
            deviceAnalog.setAttributeValue(attributeName, attributeValue);
            policyDataItem = new DataItem(attributeName, attributeValue);
        } else {
            policyDataItem = null;
        }

        DeviceFunction.removeInProcessValue(deviceAnalog.getEndpointId(), deviceModel.getUrn(),
            attributeName);

        debug('applyAttributePolicy attributeName = ' + attributeName);
        debug('applyAttributePolicy attributeValue = ' + attributeValue);
        debug('applyAttributePolicy returning policyDataItem = ' + policyDataItem);
        return policyDataItem;
    }

    /**
     * Apply policies that are targeted to a device model
     *
     * @param {dcl.message.Message} message
     * @param {number} currentTimeMillis (long)
     * @return {Promise} resolves to dcl.message.Message[]
     */
    applyDevicePolicies(message, currentTimeMillis) {
        return new Promise((resolve, reject) => {
            // A data message or alert format cannot be null or empty
            // (enforced in Data/AlertMessage(Builder) constructor)
            // @type {string}
            let format;
            // @type {string}
            let deviceModelUrn;
            // @type {string}
            const endpointId = message._.internalObject.source;

            if (message._.internalObject.type === dcl.message.Message.Type.DATA) {
                format = message._.internalObject.payload.format;
                deviceModelUrn = format.substring(0, format.length - ":attributes".length);
            } else if (message._.internalObject.type === dcl.message.Message.Type.ALERT) {
                format = message._.internalObject.payload.format;
                deviceModelUrn = format.substring(0, format.lastIndexOf(':'));
            } else {
                resolve([message]);
                return;
            }

            // @type {DeviceAnalog}
            let deviceAnalog = this.deviceAnalogMap.get(endpointId);

            if (!deviceAnalog) {
                this.directlyConnectedDevice.getDeviceModel(deviceModelUrn, deviceModelJson => {
                    if (deviceModelJson) {
                        let deviceModel = DeviceModelParser.fromJson(deviceModelJson);

                        if (deviceModel instanceof DeviceModel) {
                            deviceAnalog = new DeviceAnalog(this.directlyConnectedDevice, deviceModel,
                                endpointId);

                            this.deviceAnalogMap.set(endpointId, deviceAnalog);
                        }

                        // TODO: what to do if deviceAnalog is null?
                        if (!deviceAnalog) {
                            resolve([message]);
                        } else {
                            this.applyDevicePolicies2(message, deviceModelUrn, endpointId,
                                currentTimeMillis, deviceAnalog).then(messages =>
                            {
                                resolve(messages);
                            }).catch(error => {
                                console.log('Error applying device policies: ' + error);
                                reject();
                            });
                        }
                    } else {
                        // TODO: what to do if deviceModel is null?
                        resolve([message]);
                    }
                });
            } else {
                this.applyDevicePolicies2(message, deviceModelUrn, endpointId, currentTimeMillis,
                    deviceAnalog).then(messages =>
                {
                    resolve(messages);
                }).catch(error => {
                    console.log('Error applying device policies: ' + error);
                    reject();
                });
            }
        });
    }

    /**
     * @param {dcl.message.Message} message
     * @param {string} deviceModelUrn
     * @param {string} endpointId
     * @param {number} currentTimeMillis
     * @param {DeviceAnalog} deviceAnalog
     * @return {Promise} (of dcl.message.Message[])
     */
    applyDevicePolicies2(message, deviceModelUrn, endpointId, currentTimeMillis, deviceAnalog) {
        return new Promise((resolve, reject) => {
            // @type {DevicePolicyManager}
            const devicePolicyManager =
                DevicePolicyManager.getDevicePolicyManager(this.directlyConnectedDevice.getEndpointId());

            devicePolicyManager.getPolicy(deviceModelUrn, endpointId).then(devicePolicy => {

                if (!devicePolicy) {
                    resolve([message]);
                    return;
                }

                // @type {Set<DevicePolicy.Function>}
                const pipeline = devicePolicy.getPipeline(DevicePolicy.ALL_ATTRIBUTES);

                // No policies for this device model, retain the data item.
                if (!pipeline || (pipeline.size === 0)) {
                    resolve([message]);
                    return;
                }

                // Create the pipeline data for this device model
                // @type {Set<Map<String, Object>>}
                let pipelineData = this.pipelineDataCache.get(null);

                if (!pipelineData) {
                    pipelineData = new Set();
                    this.pipelineDataCache.set(null, pipelineData);
                }

                // @type {DevicePolicyFunction[]}
                let pipelineAry = Array.from(pipeline);
                let pipelineDataAry = Array.from(pipelineData);

                // Handle pipeline for device policy
                for (let index = 0, maxIndex = pipeline.size; index < maxIndex; index++) {
                    // @type {DevicePolicyFunction}
                    const devicePolicyFunction = pipelineAry[index];
                    // @type {Map<String, Object>}
                    let functionData;

                    if (index < pipelineData.size) {
                        functionData = pipelineDataAry[index];
                    } else {
                        functionData = new Map();
                        pipelineData.add(functionData);
                    }

                    // @type {string}
                    const key = devicePolicyFunction.getId();
                    // @type {Map<string, object>}
                    const parameters = devicePolicyFunction.getParameters();
                    // @type {DeviceFunction}
                    const deviceFunction = DeviceFunction.getDeviceFunction(key);

                    if (!deviceFunction) {
                        continue;
                    }

                    // @type {boolean}
                    let windowExpired;
                    // @type {number}
                    const window = DeviceFunction.getWindow(parameters);
                    debug('MessagingPolicyImpl.applyDevicePolicies2 window = ' + window);

                    if (window > 0) {
                        // @type {string}
                        const k = deviceModelUrn.concat("::".concat(deviceFunction.getId()));
                        // @type {number}
                        let t0 = MessagingPolicyImpl.windowMap.get(k);

                        if (!t0) {
                            t0 = currentTimeMillis;
                            MessagingPolicyImpl.windowMap.set(k, t0);
                        }

                        windowExpired = (t0 + window) <= currentTimeMillis;

                        if (windowExpired) {
                            MessagingPolicyImpl.windowMap.set(k, currentTimeMillis);
                        }
                    } else {
                        windowExpired = false;
                    }

                    // @type {boolean}
                    let alertOverridesPolicy;

                    if (message instanceof lib.message.Message &&
                        message._.internalObject.type === lib.message.Message.Type.ALERT)
                    {
                        // @type {AlertMessage}
                        const alertMessage = message;
                        // @type {AlertMessage.Severity}
                        const alertMessageSeverity = alertMessage.payload.severity;
                        // @type {AlertMessage.Severity}
                        let configuredSeverity = lib.message.Message.AlertMessage.Severity.CRITICAL;
                        // @type {string}
                        const criterion = parameters.get("alertSeverity");

                        if (criterion) {
                            try {
                                configuredSeverity = criterion;
                            } catch (e) {
                                configuredSeverity =
                                    lib.message.Message.AlertMessage.Severity.CRITICAL;
                            }
                        }

                        // TODO: Fix this compareTo
                        alertOverridesPolicy = configuredSeverity.compareTo(alertMessageSeverity) <= 0;
                    } else {
                        alertOverridesPolicy = false;
                    }

                    if (deviceFunction.apply(deviceAnalog, null, parameters, functionData, message)
                        || windowExpired || alertOverridesPolicy) {
                        // @type {object}
                        const valueFromPolicy = deviceFunction.get(
                            deviceAnalog,
                            null,
                            parameters,
                            functionData
                        );

                        if (valueFromPolicy) {
                            // @type {Message[]}
                            resolve(Array.from(valueFromPolicy));
                            return;
                        }
                    }

                    resolve([]);
                    return;
                }

                resolve([]);
            }).catch(error => {
                console.log('Error getting device policy. error=' + error);
                reject();
            });
        });
    }

    /**
     * This is the method that applies whatever policies there may be to the message. The
     * method returns zero or more messages, depending on the policies that have been
     * applied to the message. The caller is responsible for sending or queuing the
     * returned messages. The data items in the returned are messages are possibly modified
     * by some policy; for example, a message with a temperature value goes in, a copy of
     * the same message is returned with the temperature value replaced by the
     * average temperature. A returned message may also be one that is created by a
     * policy function (such as a computedMetric). Or the returned messages may be messages
     * that have been batched. If no policy applies to the message, the message itself
     * is returned.
     *
     * @param {lib.device.util.DirectlyConnectedDevice} dcd
     * @param {iotcs.message.Message} message a message of any kind.
     * @return {Promise} a Promise which will resolve with a {Message[]} of {@link Message}s to be
     *         delivered.
     */
    applyPolicies(message) {
        return new Promise((resolve, reject) => {
            if (!message) {
                resolve(new dcl.message.Message([]));
                return;
            }

            let currentTimeMillis = new Date().getTime();

            if (message._.internalObject.type === dcl.message.Message.Type.DATA) {
                this.applyAttributePolicies(message, currentTimeMillis).then(dataMessage => {
                    // Changes from here to the resolve method must also be made in the else
                    // statement below.
                    // @type {Set<Message>}
                    const messageList = new Set();

                    if (this.messagesFromExpiredPolicies.size > 0) {
                        this.messagesFromExpiredPolicies.forEach(v => messageList.add(v));
                        this.messagesFromExpiredPolicies.clear();
                    }

                    if (dataMessage) {
                        this.applyDevicePolicies(dataMessage, currentTimeMillis).then(messagesFromDevicePolicy => {
                            messagesFromDevicePolicy.forEach(v => messageList.add(v));
                            resolve(Array.from(messageList));
                        }).catch(error => {
                            console.log('Error applying device policies: ' + error);
                            reject();
                        });
                    }
                }).catch(error => {
                    console.log('Error applying attribute policies: ' + error);
                    reject();
                });
            } else {
                // Changes from here to the resolve method must also be made in the if
                // statement above.
                // @type {Set<Message>}
                const messageList = new Set();

                if (this.messagesFromExpiredPolicies.size > 0) {
                    this.messagesFromExpiredPolicies.forEach(v => messageList.add(v));
                    this.messagesFromExpiredPolicies.clear();
                }

                // @type {Message[]}
                this.applyDevicePolicies(message, currentTimeMillis).then(messagesFromDevicePolicy => {
                    resolve(messageList);
                }).catch(error => {
                    console.log('Error applying device policies: ' + error);
                    reject();
                });
            }
        }).catch(error => {
            console.log('Error applying policies: ' + error);
        });
    }

    //      * @return {Promise} which resolves to void.
    /**
     * @param {Set<DataItem<?>>} dataItems
     * @param {DeviceAnalog} deviceAnalog
     * @param {Map<String, Set<String>>} triggerMap
     * @param {number} currentTimeMillis
     */
    checkComputedMetrics(dataItems, deviceAnalog, triggerMap, currentTimeMillis) {
        debug('checkComputeMetrics called.');
        // TODO: This function should return a Promise and call devicePolicyManager.getPolicy.
        // return new Promise((resolve, reject) => {
            if (triggerMap.size === 0 || dataItems.size === 0) {
                // resolve();
                return;
            }

            // @type {Set<String>}
            let updatedAttributes = new Set();

            dataItems.forEach((value, key) => {
                updatedAttributes.add(key);
            });

            let endpointId = deviceAnalog.getEndpointId();
            let deviceModel = deviceAnalog.getDeviceModel();
            // @type {Map<String, DeviceModelAttribute>}
            let deviceModelAttributes = deviceModel.getDeviceModelAttributes();
            let deviceModelUrn = deviceModel.getUrn();

            // @type {<String, Set<String>>}  Map from  attributeName -> triggerAttributes.
            // triggerAttributes is the set of attributes that the formula refers to.
            triggerMap.forEach((triggerAttributes, attributeName) => {
                let updatedAttributesAry = Array.from(updatedAttributes);
                let triggerAttributesAry = Array.from(triggerAttributes);
                // If the set of attributes that the formula refers to is a subset of the updated attributes, then compute
                // the value of the computedMetric.
                //if (updatedAttributes.containsAll(attributeName)) {
                if (updatedAttributesAry.some(r => r.size === triggerAttributesAry.length &&
                        r.every((value, index) => triggerAttributesAry[index] === value)))
                {
                    let deviceModelAttribute = deviceModelAttributes.get(attributeName);
                    let attributeValue = deviceAnalog.getAttributeValue(attributeName);

                    if (!attributeValue) {
                        attributeValue = deviceModelAttribute.defaultValue;
                    }

                    // @type {DataItem}
                    let dataItem;

                    switch (deviceModelAttribute.type) {
                        // TODO: We don't need all of these types in JavaScript.
                        case 'BOOLEAN':
                        case 'NUMBER':
                        case 'STRING':
                        case 'URI': {
                            let dataItem = new DataItem(attribute, value);
                            break;
                        }
                        case 'DATETIME': {
                            let value;

                            if (typeof attributeValue === 'date') {
                                value = attributeValue.getTime();
                            } else {
                                value = attributeValue ? attributeValue : 0;
                            }

                            dataItem = new DataItem(attribute, value);
                            break;
                        }
                        default:
                            console.log('Unknown device model attribute type: ' +
                                deviceModelAttribute.type);

                            return; // continue
                    }

                    let devicePolicyManager =
                        DevicePolicyManager.getDevicePolicyManager(this.directlyConnectedDevice.getEndpointId());

                    // This asynchronous call should be used instead of
                    // devicePolicyManager.getPolicy2 below.
                    //
                    // devicePolicyManager.getPolicy(deviceModelUrn, endpointId).then(devicePolicy => {
                    //     if (!devicePolicy) {
                    //         return // continue
                    //     }
                    //
                    //     // @type {Set<DevicePolicy.Function>}
                    //     let pipeline = devicePolicy.getPipeline(attribute);
                    //
                    //     if (!pipeline || pipeline.size === 0) {
                    //         return // continue
                    //     }
                    //
                    //     // @type {DataItem}
                    //     let policyDataItem = this.applyAttributePolicy(deviceAnalog, dataItem,
                    //         pipeline, currentTimeMillis);
                    //
                    //     if (policyDataItem) {
                    //         dataItems.add(policyDataItem);
                    //     }
                    //
                    //     resolve();
                    // }).catch(error => {
                    //     console.log('Error getting device policy: ' + error);
                    //     reject();
                    // });

                    let devicePolicy = devicePolicyManager.getPolicy(deviceModelUrn, endpointId);

                    if (!devicePolicy) {
                        return; // continue
                    }

                    // @type {Set<DevicePolicy.Function>}
                    let pipeline = devicePolicy.getPipeline(attribute);

                    if (!pipeline || pipeline.size === 0) {
                        return; // continue
                    }

                    // @type {DataItem}
                    let policyDataItem = this.applyAttributePolicy(deviceAnalog, dataItem,
                        pipeline, currentTimeMillis);

                    debug('checkComputedMetrics policyDataItem = ' + policyDataItem);

                    if (policyDataItem) {
                        dataItems.add(policyDataItem);
                    }

                    // resolve();
                }
            });
        // });
    }

    /**
     * @param {DevicePolicy} devicePolicy
     * @return {Set<Message>}
     */
    expirePolicy1(devicePolicy) {
        // @type {Set<Message>}
        const messageList = new Set();

        this.deviceAnalogMap.forEach(deviceAnalog => {
            // @type {Set<Message>}
            const messages = this.expirePolicy3(devicePolicy, deviceAnalog);

            if (messages && (messages.size > 0)) {
                messages.forEach(message => {
                    messageList.add(message);
               });
            }
        });

        return messageList;
    }

    /**
     * @param {DevicePolicy} devicePolicy
     * @param {number} currentTimeMillis
     * @return {Set<Message>}
     */
    expirePolicy2(devicePolicy, currentTimeMillis) {
        // @type {Set<Message>}
        const messageList = this.expirePolicy1(devicePolicy);
        // @type {Set<Message>}
        const consolidatedMessageList = new Set();

        if (messageList.size > 0) {
            // Consolidate messages.
            // @type {Map<string, Set<DataItem>>}
            const dataItemMap = new Map();

            messageList.forEach(message => {
                if (message.type === lib.message.Message.Type.DATA) {
                    // @type {string}
                    const endpointId = message.getSource();
                    // @type {Set<DataItem>}
                    let dataItems = dataItemMap.get(endpointId);

                    if (!dataItems) {
                        dataItems = new Set();
                        dataItemMap.set(endpointId, dataItems);
                    }

                    message.getDataItems.forEach(dataItem => {
                        dataItems.add(dataItem);
                    });
                } else {
                    consolidatedMessageList.add(message);
                }
            });

            dataItemMap.forEach((value, key) => {
                // @type {DeviceAnalog}
                const deviceAnalog = this.deviceAnalogMap.get(key);

                if (!deviceAnalog) {
                    return; // continue
                }

                // @type {Set<DataItem>}
                const dataItems = entry.getValue();
                // @type {string}
                const format = deviceAnalog.getDeviceModel().getUrn();

                if (this.computedMetricTriggers.size > 0) {
                    // @type {Map<string, Set<string>>}
                    let triggerMap = this.computedMetricTriggers.get(format);

                    if (triggerMap && triggerMap.size > 0) {
                        try {
                            this.checkComputedMetrics(dataItems, deviceAnalog, triggerMap,
                                currentTimeMillis);
                        } catch (error) {
                            console.log(error);
                        }
                    }
                }

                let message = new iotcs.message.Message();

                message
                    .type(iotcs.message.Message.Type.DATA)
                    .source(deviceAnalog.getEndpointId())
                    .format(deviceAnalog.getDeviceModel().getUrn());

                    dataItems.forEach(dataItem => {
                        message.dataItem(dataItem.getKey(), dataItem.getValue());
                    });

                consolidatedMessageList.add(dataMessage);
            });
        }

        return consolidatedMessageList;
    }


    /**
     * @param {DevicePolicy} devicePolicy
     * @param {DeviceAnalog} deviceAnalog
     * @return {Set<Message>}
     */
    expirePolicy3(devicePolicy, deviceAnalog) {
        // @type {Set<Map<string, Set<DevicePolicyFunction>>>}
        const entries = devicePolicy.getPipelines();
        // @type {Set<Message>}
        const messageList = new Set();

        entries.forEach((v, k) => {
            // @type {Set<Message>}
            const messages = this.expirePolicy4(k, v, deviceAnalog);

            if (messages) {
                messages.forEach(message => {
                    messageList.add(message);
                });
            }
        });

        return messageList;
    }

    /**
     * @param {string} attributeName
     * @param {Set<DevicePolicyFunction>} pipeline
     * @param {DeviceAnalog} deviceAnalog
     * @return {Set<Message>}
     */
    expirePolicy4(attributeName, pipeline, deviceAnalog) {
        if (!pipeline || pipeline.size === 0) {
            return null;
        }

        // attributeName may be null.
        // Note that we are _removing_ the pipeline data cache for this attribute (which may be
        // null).
        // @type {Set<Map<string, object>>}
        const pipelineData = this.pipelineDataCache.get(attributeName);
        this.pipelineDataCache.delete(attributeName);

        if (!pipelineData) {
            return null;
        }

        // @type {DevicePolicyFunction[]}
        let pipelineAry = Array.from(pipeline);
        // @type {Map<string, object>[]}
        let pipelineDataAry = Array.from(pipelineData);

        for (let index = 0, maxIndex = pipelineAry.length; index < maxIndex; index++) {
            // @type {DevicePipelineFunction}
            let devicePolicyFunction = pipelineAry[index];

            if (!devicePolicyFunction) {
                continue;
            }

            // @type {DeviceFunction}
            let deviceFunction = DeviceFunction.getDeviceFunction(devicePolicyFunction.getId());

            if (!deviceFunction) {
                return null;
            }

            // Looking for the first policy function in the pipeline that has a "window".
            // If there isn't one, we'll drop out of the loop and return null.
            // If there is one, we process the remaining pipeline from there.
            // @type {number}
            const window = DeviceFunction.getWindow(devicePolicyFunction.getParameters());

            if (window === -1) {
                continue;
            }

            // @type {Map<string, object>}
            let functionData = index < pipelineDataAry.length ? pipelineDataAry[index] : null;

            if (!functionData) {
                // There is no data for this function, so return.
                return null;
            }

            // @type {object}
            let valueFromPolicy = deviceFunction.get(deviceAnalog, attributeName,
                devicePolicyFunction.getParameters(), functionData);

            if (!valueFromPolicy) {
                return null;
            }

            for (let next = index + 1; next < maxIndex; next++) {
                devicePolicyFunction = pipelineAry[next];

                if (!deviceFunction) {
                    return null;
                }

                deviceFunction = DeviceFunction.getDeviceFunction(devicePolicyFunction.getId());

                if (!deviceFunction) {
                    return null;
                }

                functionData = next < pipelineDataAry.length ? pipelineDataAry[next] : null;

                if (deviceFunction.apply(deviceAnalog, attributeName,
                        devicePolicyFunction.getParameters(), functionData, valueFromPolicy))
                {
                    valueFromPolicy = deviceFunction.get(
                        deviceAnalog,
                        attributeName,
                        devicePolicyFunction.getParameters(),
                        functionData
                    );

                    if (!valueFromPolicy) {
                        return null;
                    }
                } else {
                    return null;
                }

            }

            // If we get here, valueFromPolicy is not null.
            if (valueFromPolicy instanceof Set) {
                return valueFromPolicy;
            }

            // @type {DeviceModel}
            const deviceModel = deviceAnalog.getDeviceModel();
            const message = new lib.message.Message();

            message
                .source(deviceAnalog.getEndpointId())
                .format(deviceModel.getUrn())
                .dataItem(attributeName, valueFromPolicy);

            // @type {Set<Message>}
            let messages = new Set();
            messages.add(message);

            return messages;
        }

        return null;
    }

    /**
     * Get the DeviceModel for the device model URN. This method may return {@code null} if there is no device model for
     * the URN. {@code null} may also be returned if the device model is a &quot;draft&quot; and the property
     * {@code com.oracle.iot.client.device.allow_draft_device_models} is set to {@code false}, which is the default.
     *
     * @param {string} deviceModelUrn the URN of the device model.
     * @return {DeviceModel} a representation of the device model or {@code null} if it does not exist.
     */
    getDeviceModel(deviceModelUrn) {
        /**
         * The high level DirectlyConnectedDevice class has no trusted
         * assets manager and this class gives no access to the one it has,
         * so this method is here.
         * TODO: Find a high level class for this method
         */
        return DeviceModelFactory.getDeviceModel(secureConnection, deviceModel);
    }


    /**
     * @param {DevicePolicy} devicePolicy
     * @param {Set<string>} assignedDevices
     */
    policyAssigned(devicePolicy, assignedDevices) {
        // Do nothing.
    }

    /**
     * @param {DevicePolicy} devicePolicy
     * @param {Set<string>} unassignedDevices
     */
    policyUnassigned(devicePolicy, unassignedDevices) {
        // @type {number}
        const currentTimeMillis = new Date().getTime();
        // @type {Set<Message>}
        const messages = this.expirePolicy2(devicePolicy, currentTimeMillis);

        if (messages && messages.size > 0) {
            messages.forEach(message => {
                this.messagesFromExpiredPolicies.add(message);
            });
        }

        unassignedDevices.forEach(unassignedDevice => {
            let devicePolicyManager = DevicePolicyManager.getDevicePolicyManager(unassignedDevice);

            if (devicePolicyManager) {
                devicePolicyManager.removePolicy(devicePolicy.deviceModelUrn, devicePolicy.getId(),
                    unassignedDevice);
            }
        });

        // TODO:  Need to figure out how to handle accumulated values.
        //        For now, just clear out the various maps, which
        //        effectively means "start from scratch"
        this.deviceAnalogMap.clear();
        this.pipelineDataCache.clear();
        this.computedMetricTriggers.clear();
        MessagingPolicyImpl.windowMap.clear();
    }
}

/**
 * deviceModelUrn:attribute:deviceFunctionId -> start time of last window For a window policy, this maps the
 * policy target plus the function to when the window started. When the attribute for a timed function is in
 * the message, we can compare this start time to the elapsed time to determine if the window has expired. If
 * the window has expired, the value computed by the function is passed to the remaining functions in the
 * pipeline.
 *
 * @type {Map<string, number>}
 */
MessagingPolicyImpl.windowMap = new Map();
