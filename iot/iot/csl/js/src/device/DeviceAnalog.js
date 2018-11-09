/**
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

class DeviceAnalog {
    // Instance "variables" & properties...see constructor.

    /**
     *
     * @param {$impl.DirectlyConnectedDevice} directlyConnectedDevice
     * @param {DeviceModel} deviceModel
     * @param {string} endpointId the endpoint ID of the DirectlyConnectedDevice with this device
     * model.
     */
    constructor(directlyConnectedDevice, deviceModel, endpointId) {
        // Instance "variables" & properties.
        /**
         *
         * @type {Map<String, Object>}
         */
        this.attributeValueMap = new Map();
        /**
         *
         * @type {$impl.DirectlyConnectedDevice}
         */
        this.directlyConnectedDevice = directlyConnectedDevice;
        /**
         *
         * @type {DeviceModel}
         */
        this.deviceModel = deviceModel;
        /**
         *
         * @type {string}
         */
        this.endpointId = endpointId;
        // Instance "variables" & properties.
    }

    /**
     *
     * @param {string} actionName
     * @param {object[]} args
     */
    call(actionName, args) {
        // @type {Map<string, DeviceModelAction}
        const deviceModelActionMap = this.deviceModel.getDeviceModelActions();

        if (!deviceModelActionMap) {
            return;
        }

        // @type {DeviceModelAction}
        const deviceModelAction = deviceModelActionMap.get(actionName);

        if (!deviceModelAction) {
            return;
        }

        // @type {DeviceModelAttribute.Type}
        const argType = deviceModelAction.getArgType();

        // TODO: currently, call only supports one arg
        // @type {object}
        const arg = args != null && args.length > 0 ? args[0] : null;

        // What this is doing is pushing a request message into the message
        // queue for the requested action. To the LL, it is handled just like
        // any other RequestMessage. But we don't want this RM going to the
        // server, so the source and destination are set to be the same
        // endpoint id. In SendReceiveImpl, if there is a RequestMessage
        // with a source == destination, it is treated it specially.
        // @type {object}
        let requestMessage = {};

        requestMessage.payload = {
            body: '',
            method: 'POST',
            url: "deviceModels/" + this.getDeviceModel().getUrn() + "/actions/" + actionName
        };

        requestMessage.destination = this.getEndpointId();
        requestMessage.source = this.getEndpointId();
        requestMessage.type = lib.message.Message.Type.REQUEST;

        // Check arg for correct type.
        if (argType) {
            if (!arg) {
                return;
            }

            // @type {boolean}
            let goodArg = false;

            switch (argType) {
                case 'number':
                    goodArg = typeof arg === 'number';

                    if (goodArg) {
                        // @type {number}
                        const number = arg;
                        // @type {string}
                        let value;

                        if (argType === DeviceModelAttribute.Type.INTEGER) {
                            value = Math.round(number);
                        } else {
                            value = number;
                        }

                        requestMessage.body = '{"value":' + value + '}';

                        // Assumption here is that lowerBound <= upperBound.
                        // @type {number}
                        const val = arg;

                        if (deviceModelAction.getUpperBound()) {
                            // @type {number}
                            const upper = deviceModelAction.getUpperBound();

                            if (val > upper) {
                                // This is a warning.
                                console.log(this.getDeviceModel().getUrn() + ' action "' +
                                    actionName + '" arg out of range: ' + val + ' > ' + upper);
                                // Even though the arg is out of range, pass it to the action.
                                // TODO is this the right thing to do?
                            }
                        }

                        if (deviceModelAction.getLowerBound()) {
                            // @type {number}
                            const lower = deviceModelAction.getLowerBound();

                            if(val < lower) {
                                // This is a warning.
                                console.log(this.getDeviceModel().getUrn() + ' action "' +
                                    actionName + '" arg out of range: ' + val + ' < ' + lower);
                                // Even though the arg is out of range, pass it to the action.
                                // TODO is this the right thing to do?
                            }
                        }

                    }

                    break;
                case 'datetime':
                    goodArg = (arg instanceof Date) || (typeof arg === 'number');

                    if (goodArg) {
                        // @type {string}
                        let value;

                        if (arg instanceof Date) {
                            value = arg.getTime();
                        } else {
                            value = arg;
                        }

                        requestMessage.body = '{"value":' + value + '}';
                    }

                    break;
                case 'boolean':
                    goodArg = typeof arg === 'boolean';

                    if (goodArg) {
                        requestMessage.body = '{"value":' + arg + '}';
                    }

                    break;
                case 'string':
                case 'uri':
                    goodArg = typeof arg === 'string';

                    if (goodArg) {
                        requestMessage.body = '{"value":' + arg + '}';
                    }

                    break;
                default:
                    // This is a warning.
                    console.log('Unexpected type ' + argType);
                    goodArg = false;
            }

            if (!goodArg) {
                // This is a warning.
                console.log(this.getDeviceModel().getUrn() + ' action "' + actionName +
                    '": Wrong argument type. "' + 'Expected ' + argType + ' found ' + typeof arg);

                return;
            }
        }

        // @type {boolean}
        // const useLongPolling = (process.env['com.oracle.iot.client.disable_long_polling']);
        const useLongPolling = false;
        // Assumption here is that, if you are using long polling, you are using message dispatcher.
        // This could be a bad assumption. But if long polling is disabled, putting the message on
        // the request buffer will work regardless of whether message dispatcher is used.
        if (useLongPolling) {
            try {
                // @type {Message} (ResponseMessage)
                const responseMessage =
                    new lib.device.util.RequestDispatcher().dispatch(requestMessage);
            } catch (error) {
                console.log(error);
            }
        } else {
            // Not long polling, push request message back on request buffer.
            try {
                // @type {Message} (ResponseMessage)
                const responseMessage =
                    new lib.device.util.RequestDispatcher().dispatch(requestMessage);
            } catch (error) {
                console.log(error);
            }
        }
    }

    /**
     * @param {string} attributeName
     * @return {object}
     */
    getAttributeValue(attributeName) {
        /** {$impl.Attribute} */
        let deviceModelAttribute = this.deviceModel.getDeviceModelAttributes().get(attributeName);

        if (deviceModelAttribute === null) {
            throw new Error(this.deviceModel.getUrn() + " does not contain attribute " + attributeName);
        }

        let value = this.attributeValueMap.get(attributeName);

        if (value === null) {
            value = deviceModelAttribute.defaultValue;
        }

        return value;
    }


    /**
     * {DeviceModel}
     */
    getDeviceModel() {
        return this.deviceModel;
    }

    /**
     *
     * @return {string}
     */
    getEndpointId() {
        return this.directlyConnectedDevice.getEndpointId();
    }

    /**
     * @param {Message} message
     */
    queueMessage(message) {
        try {
            this.directlyConnectedDevice.dispatcher.queue(message);
        } catch(error) {
            console.log('Error queueing message: ' + error);
        }
    }

    /**
     * Set the named attribute to the given value.
     *
     * @param {string} attribute the attribute to set
     * @param {object} value the value of the attribute
     * @throws IllegalArgumentException if the attribute is not in the device model,
     * the value is {@code null}, or the value does not match the attribute type.
     */
    setAttributeValue(attribute, value) {
        if (value === null) {
            throw new Error("value cannot be null");
        }

        let deviceModelAttribute = this.deviceModel.getDeviceModelAttributes().get(attribute);

        if (!deviceModelAttribute) {
            throw new Error(this.deviceModel.getUrn() + " does not contain attribute " + attribute);
        }

        // {DeviceModelAttribute.Type}
        let type = deviceModelAttribute.type;
        let badValue;
        let typeOfValue = null;

        switch (type) {
            // TODO: e don't need all of these types in JavaScript.
            case DeviceModelAttribute.Type.DATETIME:
            case DeviceModelAttribute.Type.INTEGER:
            case DeviceModelAttribute.Type.NUMBER:
                typeOfValue = typeof value === 'number';
                badValue = !typeOfValue;
                break;
            case DeviceModelAttribute.Type.STRING:
            case DeviceModelAttribute.Type.URI:
                typeOfValue = typeof value === 'string';
                badValue = !typeOfValue;
                break;
            case DeviceModelAttribute.Type.BOOLEAN:
                typeOfValue = typeof value === 'boolean';
                badValue = !typeOfValue;
                break;
            default:
                throw new Error('Unknown type ' + type);
        }

        if (badValue) {
            throw new Error("Cannot set '"+ this.deviceModel.getUrn() + ":attribute/" + attribute + "' to " +
                value.toString());
        }

        this.attributeValueMap.set(attribute, value);
    }
}
