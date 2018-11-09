/**
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * DeviceFunction is an abstraction of a policy device function.
 */
class DeviceFunction {
    // Instance "variables"/properties...see constructor.

    /**
     * @param {lib.message.Message} alertMessage
     * @param {DeviceModelFormatField} field
     * @param {object} value
     */
    static addDataItem(alertMessage, field, value) {
        switch (field.getType()) {
            case 'integer':
            case 'number':
                if (value instanceof Number) {
                    if (field.getType() === DeviceModelAttribute.Type.INTEGER) {
                        alertMessage.dataItem(field.getName(), value);
                    } else {
                        alertMessage.dataItem(field.getName(), Number(value));
                    }
                } else {
                    throw new Error("value of attribute '" + field.getName() + "' is not a " +
                        field.getType());
                }

                break;
            case 'string':
            case 'uri':
            default:
                alertMessage.dataItem(field.getName(), String(value));
                break;
            case 'boolean':
                if (value instanceof Boolean) {
                    alertMessage.dataItem(field.getName(), value);
                } else {
                    throw new Error("Value of attribute '" + field.getName() + "' is not a " +
                        field.getType());
                }

                break;
            case 'datetime':
                if (value instanceof Number) {
                    alertMessage.dataItem(field.getName(), value);
                } else if (value instanceof Date) {
                    alertMessage.dataItem(field.getName(), new Date(value).getTime());
                } else {
                    throw new Error("value of attribute '" + field.getName() + "' is not a " +
                        field.getType());
                }

                break;
        }
    }

    /**
     *
     * @param {FormulaParserNode} node
     * @param {DeviceAnalog} deviceAnalog
     * @return {number}
     */
    static compute(node, deviceAnalog) {
        if (!node) {
            return NaN;
        }

        if (node instanceof FormulaParserTerminal) {
            const attr = node.getValue();

            switch (node.type) {
                case FormulaParserTerminal.Type.CURRENT_ATTRIBUTE: {
                    // {number}
                    const value = deviceAnalog.getAttributeValue(attr);

                    if (typeof value === 'number') {
                        return value;
                    } else if (typeof value === 'boolean') {
                        return value ? 1 : 0;
                    }

                    break;
                }
                case FormulaParserTerminal.Type.IN_PROCESS_ATTRIBUTE:
                    // @type {number}
                    let value = DeviceFunction.getInProcessValue(deviceAnalog.getEndpointId(),
                        deviceAnalog.getDeviceModel().getUrn(), attr);

                    if (value || deviceAnalog.getAttributeValue(attr)) {
                        if (typeof value === 'number') {
                            return value;
                        } else if (typeof value === 'boolean') {
                            return value ? 1 : 0;
                        }
                    }

                    break;
                case FormulaParserTerminal.Type.NUMBER:
                    return parseFloat(attr);
            }

            return NaN;
        }

        if (node.getOperation() === FormulaParserOperation.Op.TERNARY) {
            // @type {number}
            let cond = DeviceFunction.compute(node.getLeftHandSide(), deviceAnalog);

            if (cond === 1.0) {
                return DeviceFunction.compute(node.getRightHandSide().getLeftHandSide(),
                    deviceAnalog);
            } else {
                return DeviceFunction.compute(node.getRightHandSide().getRightHandSide(),
                    deviceAnalog);
            }
        } else if (node.getOperation() === FormulaParserOperation.Op.GROUP) {
            return DeviceFunction.compute(node.getLeftHandSide(), deviceAnalog);
        }

        // @type {number}
        let lhs = DeviceFunction.compute(node.getLeftHandSide(), deviceAnalog);
        // @type {number}
        let rhs = DeviceFunction.compute(node.getRightHandSide(), deviceAnalog);
        // @type {Operation}
        const operation = node.getOperation();

        switch (operation) {
            case FormulaParserOperation.Op.UNARY_MINUS:
                return -lhs;
            case FormulaParserOperation.Op.UNARY_PLUS:
                return +lhs;
            case FormulaParserOperation.Op.DIV:
                return lhs / rhs;
            case FormulaParserOperation.Op.MUL:
                return lhs * rhs;
            case FormulaParserOperation.Op.PLUS:
                return lhs + rhs;
            case FormulaParserOperation.Op.MINUS:
                return lhs - rhs;
            case FormulaParserOperation.Op.MOD:
                return lhs % rhs;
            case FormulaParserOperation.Op.OR:
                // Let NaN or NaN be false.
                if (isNaN(lhs)) {
                    return isNaN(rhs) ? 0.0 : 1.0;
                } else {
                    return lhs !== 0.0 || rhs!== 0.0 ? 1.0 : 0.0;
                }
            case FormulaParserOperation.Op.AND:
                // If lhs or rhs is NaN, return false
                if (isNaN(lhs) || isNaN(rhs)) {
                    return 0.0;
                } else {
                    return lhs !== 0.0 && rhs !== 0.0 ? 1.0 : 0.0;
                }
            case FormulaParserOperation.Op.EQ:
                // NaN.compareTo(42) == 1, 42.compareTo(NaN) == -1
                return lhs === rhs ? 1.0 : 0.0;
            case FormulaParserOperation.Op.NEQ:
                return lhs === rhs ? 0.0 : 1.0;
            case FormulaParserOperation.Op.GT:
                // NaN.compareTo(42) == 1, 42.compareTo(NaN) == -1
                // Let NaN > 42 return false, and 42 > NaN return true
                if (isNaN(lhs)) {return 0.0;}
                if (isNaN(rhs)) {return 1.0;}
                return lhs > rhs ? 1.0 : 0.0;
            case FormulaParserOperation.Op.GTE:
                // NaN.compareTo(42) == 1, 42.compareTo(NaN) == -1
                // Let NaN >= 42 return false, and 42 >= NaN return true
                if (isNaN(lhs)) {return isNaN(rhs) ? 1.0 : 0.0;}
                if (isNaN(rhs)) {return 1.0;}
                return lhs >= rhs ? 1.0 : 0.0;
            case FormulaParserOperation.Op.LT:
                // NaN.compareTo(42) == 1, 42.compareTo(NaN) == -1
                // Let NaN < 42 return false, and 42 < NaN return true
                if (isNaN(lhs)) {return 0.0;}
                if (isNaN(rhs)) {return 1.0;}
                return lhs < rhs ? 1.0 : 0.0;
            case FormulaParserOperation.Op.LTE:
                // NaN.compareTo(42) == 1, 42.compareTo(NaN) == -1
                // Let NaN <= 42 return false, and 42 <= NaN return true
                if (isNaN(lhs)) {return isNaN(rhs) ? 1.0 : 0.0;}
                if (isNaN(rhs)) {return 1.0;}
                return lhs <= rhs ? 1.0 : 0.0;
            case FormulaParserOperation.Op.TERNARY:
                break;
            case FormulaParserOperation.Op.ALTERNATIVE:
                break;
            case FormulaParserOperation.Op.NOT:
                return lhs === 1.0 ? 0.0 : 1.0;
            case FormulaParserOperation.Op.FUNCTION:
                break;
            case FormulaParserOperation.Op.GROUP:
                break;
            case FormulaParserOperation.Op.TERMINAL:
                break;
        }

        return NaN;
    }

    /**
     * @param {DeviceAnalog} deviceAnalog
     * @param {Map<String, object>} configuration
     * @return {Message} - alert message.
     */
    static createAlert(deviceAnalog, configuration) {
        // @type {DeviceModel}
        const deviceModel = deviceAnalog.getDeviceModel();

        // @type {Map<String, DeviceModelFormat>}
        const deviceModelFormatMap = deviceModel.getDeviceModelFormats();

        if (!deviceModelFormatMap) {
            throw new Error(deviceModel.getUrn() + " does not contain alert formats.");
        }

        // @type {string}
        const format = configuration.get("urn");
        // @type {DeviceModelFormat}
        const deviceModelFormat = deviceModelFormatMap.get(format);

        if (!deviceModelFormat) {
            throw new Error(deviceModel.getUrn() + " does not contain alert format '" + format +
                "'");
        }

        // @type {List<DeviceModelFormatField>}
        const fields = deviceModelFormat.getFields();

        // @type {AlertMessage.Severity}
        let alertSeverity;

        try {
            // @type {string}
            const severityConfig = configuration.get("severity");

            alertSeverity = severityConfig ?
                severityConfig : lib.message.Message.AlertMessage.Severity.NORMAL;
        } catch (error) {
            alertSeverity = lib.message.Message.AlertMessage.Severity.NORMAL;
        }

        // @type {AlertMessage}
        let alertMessage = lib.message.Message.AlertMessage.buildAlertMessage(format,
            deviceModelFormat.getName(), alertSeverity);

        alertMessage
            .format(format)
            .source(deviceAnalog.getEndpointId());

        // @type {Map<String,Object>}
        const fieldsFromPolicy = configuration.get("fields");

        fields.forEach (field => {
            // @type {object}
            const policyValue = fieldsFromPolicy.get(field.getName());

            if (!policyValue) {
                return;  //continue
            }

            try {
                // @type {object}
                let value = DeviceFunction.convertArg(deviceAnalog, field.getType(), policyValue);
                DeviceFunction.addDataItem(alertMessage, field, value);
            } catch (error) {
                console.log("Bad value for '" + field.getName() + "' in '" + deviceModel.getUrn() +
                    "' :" + error);
            }
        });

        return alertMessage;
    }

    /**
     * @param {string} endpointId
     * @param {string} deviceModelUrn
     * @param {string} attribute
     * @return {string}
     */
    static createInProcessMapKey(endpointId, deviceModelUrn, attribute) {
        return endpointId + '/deviceModels/' + deviceModelUrn + ':attributes/' + attribute;
    }

    /**
     * @param {DeviceAnalog} deviceAnalog
     * @param {string} type ({DeviceModelAttribute.Type})
     * @param {object} arg
     * @return {object} {@code null} if arg is undefined.
     */
    static convertArg(deviceAnalog, type, arg) {
        if (!arg) {
            return null;
        }

        switch (type) {
            case 'string':
                return DeviceFunction.convertFormulaToString(deviceAnalog, String(arg));
            case 'uri':
            case 'boolean':
            case 'datetime':
            default:
                // No conversion
                return arg;
            case 'number':
                // Treat as formula.
                // @type {number}
                let num;

                if (typeof arg === 'string') {
                    num = DeviceFunction.convertFormula(deviceAnalog, arg);
                } else if (typeof arg === 'number') {
                    num =  arg;
                } else {
                    throw new Error("Expected NUMBER or STRING, found '" + typeof arg + "'");
                }

                if (type === DeviceModelAttribute.Type.INTEGER) {
                    return num;
                }

                return num;
        }
    }

    /**
     * @param {DeviceAnalog} deviceAnalog
     * @param {string} formula
     * @return {number}
     */
    static convertFormula(deviceAnalog, formula) {
        try {
            // If arg is a string, it should be a FORMULA.
            // @type {Set<FormulaParser.token>}
            const tokens = FormulaParser.tokenize(formula);
            // @type {FormulaParserNode}
            const node = FormulaParser.parseFormula(tokens, formula);
            return DeviceFunction.compute(node, deviceAnalog);
        } catch (error) {
            console.log('Field in formula not in device model: ' + formula);
        }

        return NaN;
    }

    /**
     * @param {DeviceAnalog} deviceAnalog
     * @param {string} formula
     * @return {object}
     */
    static convertFormulaToString(deviceAnalog, formula) {
        // If arg is a string, it should be a FORMULA.
        try {
            // @type {Set<FormulaParserToken}
            const tokens = FormulaParser.tokenize(formula);
            // @type {Set<FormulaParserNode}
            const node = FormulaParser.parseFormula(tokens, formula);

            if (node instanceof FormulaParserTerminal) {
                // @type {FormulaParserTerminal }
                let terminal = node;
                // @type {string}
                const nodeValue = node.getValue();

                switch (terminal.type) {
                    case FormulaParserTerminal.Type.CURRENT_ATTRIBUTE: {
                        // @type {object}
                        const value = deviceAnalog.getAttributeValue(nodeValue);

                        if (typeof value === 'string') {
                            return value;
                        }

                        break;
                    }
                    case FormulaParserTerminal.Type.IN_PROCESS_ATTRIBUTE:
                        // @type {object}
                        let value = DeviceFunction.getInProcessValue(deviceAnalog.getEndpointId(),
                        deviceAnalog.getDeviceModel().getUrn(), nodeValue);

                        if (value != null ||
                            (value = deviceAnalog.getAttributeValue(nodeValue)) != null)
                        {
                            if (typeof value === 'string') {
                                return value;
                            }
                        }

                        break;
                    case FormulaParserTerminal.Type.IDENT:
                        return nodeValue;
                }
            }
        } catch (error) {
            console.log('Could not parse formula: ' + formula);
        }

        return formula;
    }

    /**
     * Greatest common factor, e.g., gcd(90,60) = 30.
     *
     * @param {number} x
     * @param {number} y
     * @return {number}
     */
    static gcd(x, y){
        return (y === 0) ? x : DeviceFunction.gcd(y, x % y);
    }

    /**
     * @param {DeviceAnalog} deviceAnalog
     * @param {Map<string, object>} configuration
     * @return {object[]}
     */
    static getActionArgs(deviceAnalog, configuration) {
        // This list comes from handling the "action" parameter in
        // com.oracle.iot.client.impl.device.DevicePolicyManager.devicePolicyFromJSON()
        // @type {Set<object>}
        let args = configuration.get('arguments');

        if (!args || args.size === 0) {
            return null;
        }

        // @type {object[]}
        let actionArgs = [args.size];

        for (let n = 0, nMax = args.size; n < nMax; n++) {
            // @type {DeviceModel}
            const deviceModel = deviceAnalog.getDeviceModel();
            // @type {Map<sgring, DeviceModelAction}
            const actionMap = deviceModel.getDeviceModelActions();

            if (!actionMap|| actionMap.size === 0) {
                // TODO: this could get annoying
                console.log('No actions in device model "' +
                    deviceModel.getUrn() + '"');

                actionArgs[n] = null;
                continue;
            }

            // @type {string}
            const actionName = configuration.get('name');
            // @type {DeviceModelAction}
            const deviceModelAction = actionMap.get(actionName);

            if (!deviceModelAction) {
                // TODO: this could also get annoying
                console.log('No action named "' + actionName
                    + '" in device model "' + deviceModel.getUrn() + '"');

                actionArgs[n] = null;
                continue;
            }

            // @type {string} ({DeviceModelAttribute.Type})
            const type = deviceModelAction.getArgType();

            try {
                actionArgs[n] = DeviceFunction.convertArg(deviceAnalog, type, args.get(n));
            } catch (error) {
                console.log('Bad argument to "' + actionName + '" in "' + deviceModel.getUrn() +
                    '" :' + error);

                // Maybe this was purposeful - let application handle.
                actionArgs[n] = args.get(n);
            }
        }

        return actionArgs;
    }

    /**
     * @param functionId (string)
     * @return DeviceFunction
     */
    static getDeviceFunction(functionId) {
        return DeviceFunction.POLICY_MAP.get(functionId);
    }

    /**
     * @param  {string} endpointId
     * @param  {string} deviceModelUrn
     * @param  {string} attribute
     * @return {object}
     */
    static getInProcessValue(endpointId, deviceModelUrn, attribute) {
        if (!DeviceFunction.inProcessValues) {
            DeviceFunction.inProcessValues = new Map();
        }

        if (!this.inProcessValues) {
            this.inProcessValues = new Map();
        }

        let k = DeviceFunction.createInProcessMapKey(endpointId, deviceModelUrn, attribute);
        return this.inProcessValues.get(k);
    }

    /**
     *
     * @param {DeviceAnalog} DeviceAnalog
     * @return {Set<Pair<Message, StorageObject>>}
     */
    static getPersistedBatchedData(deviceAnalog) {
        // @type {Set<Message>}
        const messages = batchByPersistence.get(deviceAnalog.getEndpointId());
        batchByPersistence.delete(messages);
        // @type {Set<Pair<Message, StorageObject>>}
        const pairs = new Set();

        messages.forEach(message => {
            pairs.add(new Pair(message, null));
        });

        return pairs;
    }


    /**
     * Utility for getting a "slide" value from a configuration.
     *
     * @param {Map<string, object>} configuration the parameters for this function from the device
     *        policy.
     * @param {number} window the corresponding window for the slide.
     * @return {number} the configured slide value, or window if there is no slide or slide is zero
     */
    static getSlide(configuration, window) {
        // @type {number}
        const slide = configuration.get("slide");

        if (slide) {
            return slide > 0 ? slide : window;
        }

        return window;
    }

/**
     * Utility for getting a "window" value from a configuration.
     *
     * @param {Map<string, object>} configuration the parameters for this function from the device
     *        policy
     * @return {number} a window value, or -1 if the configuration is not time based
     */
    static getWindow(configuration) {
        let criterion = -1;
        ['window', 'delayLimit'].forEach(key => {
            let criterionTmp = configuration.get(key);

            if (criterionTmp) {
                criterion = criterionTmp;
            }
        });

        return criterion;
    }

    /**
     *
     * @param {string} endpointId
     * @param {string} deviceModelUrn
     * @param {string} attribute
     * @param {object} value
     * @return {void}
     */
    static putInProcessValue(endpointId, deviceModelUrn, attribute, value) {
        if (!DeviceFunction.inProcessValues) {
            DeviceFunction.inProcessValues = new Map();
        }

        let k = DeviceFunction.createInProcessMapKey(endpointId, deviceModelUrn, attribute);
        DeviceFunction.inProcessValues.set(k, value);
    }

    static removeInProcessValue(endpointId, deviceModelUrn, attribute) {
        let value = null;
        let key = DeviceFunction.createInProcessMapKey(endpointId, deviceModelUrn, attribute);

        if (DeviceFunction.inProcessValues.has(key)) {
            value = DeviceFunction.inProcessValues.get(key);
            DeviceFunction.inProcessValues.delete(key);
        }

        return value;
    }

    /**
     *
     * @param {string} id
     */
    constructor(id) {
        // Instance "variables"/properties.
        /**
         * The id of the function. This is the unique id from the function definition.
         *
         * @type {string}
         */
        this.id = id;
        Object.freeze(this.id);
        // @type {BatchByPersistence}
        this.batchByPersistence =
            PersistenceMetaData.isPersistenceEnabled() ? new BatchByPersistence() : null;
        // Instance "variables"/properties.

        if (!DeviceFunction.inProcessValues) {
            DeviceFunction.inProcessValues = new Map();
        }
    }

    /**
     * The {@code apply} method is where the logic for the function is coded.
     * This method returns {@code true} if the conditions for the function have
     * been met. Only when this function's apply method returns true
     * will the next function in the pipeline be applied.
     * <p>
     * After this method returns {@code true}, use
     * {@link #get(DeviceAnalog, String, Map, Map)} to retrieve
     * the value from the function.
     *
     * @param {DeviceAnalog} deviceAnalog the VirtualDevice, never {@code null}.
     * @param {(string|null)} attribute the DeviceModelAttribute, which may be {@code null} if the
     *                           function is being applied at the device model level
     * @param {Map<string, object>} configuration the parameters for this function from the device
     * policy
     * @param {Map<string, object>} data a place for storing data between invocations of the
     * function
     * @param {object} value the value to which the function is being applied
     * @return {boolean} {@code true} if the conditions for the function have been satisfied.
     */
    apply(deviceAnalog, attribute, configuration, data, value){
        throw new Error('Must implement the apply method in subclass.');
    }

    /**
     * Return the value from the function. This method should only be called after
     * {@link #apply(DeviceAnalog, String, Map, Map, Object)} returns {@code true}, or when a
     * window expires.
     *
     * @param {DeviceAnalog} deviceAnalog the VirtualDevice, never {@code null}.
     * @param {(string|null)} attribute the DeviceModelAttribute, which may be {@code null} if the
     *        function is being applied at the device model level.
     * @param {Map<string, object>} configuration the parameters for this function from the device
     *         policy.
     * @param {Map<string, object>} data a place for storing data between invocations of the
     *        function.
     * @return {object} the value from having applied the function
     */
    get(deviceAnalog, attribute, configuration, data) {
        throw new Error('Must implement the get method in subclass.');
    }

    /**
     * Return a string representation of this function. Useful for logging.
     *
     * @param {Map<string, object>} configuration the parameters for this function from the device
     *        policy.
     * @return {string} a string representation of this function.
     */
    getDetails(configuration) {
        return this.getId();
    }

    /**
     * Get the ID of the function. This is the unique ID from the function definition.
     *
     * @return {string} the policy ID.
     */
    getId() {
        return this.id;
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// Policy definitions
////////////////////////////////////////////////////////////////////////////////////////////////////
class ACTION_CONDITION extends DeviceFunction {
    constructor() {
        super('actionCondition');
    }

    /**
     *
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @param {object} value
     * @return {boolean}
     */
    apply(deviceAnalog, attribute, configuration, data, value) {
        // @type {FormulaParserNode}
        let condition = data.get('actionCondition.condition');

        if (!condition) {
            // @type {string}
            const str = configuration.get('condition');
            // @type {Set<FormulaParserToken>}
            let tokens = FormulaParser.tokenize(String(str));
            // @type {Stack<FormulaParserNode>}
            let stack = new Stack();
            FormulaParser.parseConditionalOrExpression(stack, tokens, str, 0);
            condition = stack.pop();
            data.set('actionCondition.condition', condition);
        }

        // @type {number}
        const computedValue = DeviceFunction.compute(condition, deviceAnalog);

        if (!isFinite(computedValue) || (computedValue === 0.0)) { //zero is false.
            data.set('actionCondition.value', value);
            return true;
        }

        // getActionArgs may return null.
        // @type {object[]}
        const actionArgs = DeviceFunction.getActionArgs(deviceAnalog, configuration);
        // @type {string}
        let actionName = configuration.get('name');
        deviceAnalog.call(String(actionName), actionArgs);
        // @type {boolean}
        let filter = configuration.get('filter');

        if (filter === null || filter) {
            // If this is a filter, returning false stops the pipeline.
            return false;
        }

        data.set('actionCondition.value', value);
        return true;
    }

    /**
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     *
     * @return {object}
     */
    get(deviceAnalog, attribute, configuration, data) {
        const value = data.get('actionCondition.value');
        data.delete('actionCondition.value');
        return value;
    }

    /**
     * @param {Map<string, object>} config
     * @return {string}
     */
    getDetails(config) {
        // @type {object}
        const filter = config.containsKey('filter') ? config.get('filter') : true;

        return super.getDetails(config) +
            '[condition="' + config.get('condition') +
            '", action="'+ config.get('name')+
            '", arguments="'+ config.get('arguments') +
            '", filter="' + filter + ']';
    }
}

class ALERT_CONDITION extends DeviceFunction {
    constructor() {
        super('alertCondition');
    }

    /**
     *
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @param {object} value
     *
     * @return {boolean}
     */
    apply(deviceAnalog, attribute, configuration, data, value) {
        // @type {FormulaParserNode}
        let condition = data.get('alertCondition.condition');

        if (!condition) {
            // @type {string}
            const str = configuration.get('condition');
            // @type {Set<FormulaParser.Token}
            let tokens = FormulaParser.tokenize(String(str));
            // @type {Stack<FormulaParserNode>}
            let stack = new Stack();
            FormulaParser.parseConditionalOrExpression(stack, tokens, str, 0);
            condition = stack.pop();
            data.set('alertCondition.condition', condition);
        }

        // @type {number}
        const computedValue = DeviceFunction.compute(condition, deviceAnalog);

        if (!isFinite(computedValue) || (computedValue === 0.0))  // zero is false.
        {
            data.set('alertCondition.value', value);
            return true;
        }

        // @type {AlertMessage}
        const alertMessage = DeviceFunction.createAlert(deviceAnalog, configuration);
        deviceAnalog.queueMessage(alertMessage);
        // @type {boolean}
        let filter = configuration.get('filter');

        if (!filter || filter) {
            // if this is a filter, returning false stops the pipeline
            return false;
        }

        data.set('alertCondition.value', value);
        return true;
    }

    /**
     * @Override
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @return {object}
     */
    get(deviceAnalog, attribute, configuration, data) {
        // @type {object}
        const value = data.get('alertCondition.value');
        data.delete('alertCondition.value');
        return value;
    }

    /**
     * @param {Map<string, object>} config
     * @return {string}
     */
    getDetails(config) {
        // @type {object}
        const filter = config.has('filter') ? config.get('filter') : true;

        return super.getDetails(config) +
            '[condition="' + config.get('condition') +
            '", urn="'+ config.get('urn') + '", fields=' +
            config.get('fields') +
            '", filter='+ filter +']';
    }
}

// Will batch data until networkCost (Satellite > Cellular > Ethernet) lowers to the configured value
class BATCH_BY_COST extends DeviceFunction {
    constructor() {
        super('batchByCost');
    }

    /**
     *
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @param {object} value
     *
     * @return {boolean}
     */
    apply(deviceAnalog, attribute, configuration, data, value) {
        if (this.batchByPersistence) {
            // @type {Message}
            const message = value.getKey();
            this.batchByPersistence.save(deviceAnalog.getEndpointId(), message);
        } else {
            // @type {Set<object>}
            let list = data.get("batchByCost.value");

            if (!list) {
                list = new Set();
                data.set("batchByCost.value", list);
            }

            list.add(value);
        }

         // Assume the configured cost is the most expensive
        // @type {number}
        const configuredCost = NetworkCost.getCost(configuration.get("networkCost"),
                        "networkCost", NetworkCost.Type.SATELLITE);

        // Assume the client cost is the least expensive
        // @type {number}
        const networkCost = NetworkCost.getCost((process['env_oracle_iot_client_network_cost']),
            'oracle_iot_client_network_cost', NetworkCost.Type.ETHERNET);

        // If the cost of the network the client is on (networkCost) is greater than
        // the cost of the network the policy is willing to bear (configuredCost),
        // then return false (the value is filtered).
        if (networkCost > configuredCost) {
            return false;
        }

        return true;
    }

    /**
     * @Override
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @return {object}
     */
    get(deviceAnalog, attribute, configuration, data) {
        if (this.batchByPersistence) {
            // @type {Set<Pair<Message, StorageObject>>}
            const value = getPersistedBatchedData(deviceAnalog);
            return value;
        } else {
            // @type {object}
            const value = data.get("batchByCost.value");
            data.delete("batchByCost.value");
            return value;
        }
    }

    /**
     * @param {Map<string, object>} config
     * @return {string}
     */
    getDetails(config) {
        return super.getDetails(config) + '[networkCost=' + config.get('networkCost') + ']';
    }
}

class BATCH_BY_SIZE extends DeviceFunction {
    constructor() {
        super('batchBySize');
    }

    /**
     *
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @param {object} value
     *
     * @return {boolean}
     */
    apply(deviceAnalog, attribute, configuration, data, value) {
        if (this.batchByPersistence) {
            // @type {Message}
            const message = value.getKey();
            this.batchByPersistence.save(deviceAnalog.getEndpointId(), message);

        } else {
            // @type {Set<object>}
            let list = data.get("batchBySize.value");

            if (!list) {
                list = new Set();
                data.set("batchBySize.value", list);
            }

            list.add(value);
        }

        // @type {number}
        let batchCount = data.get("batchBySize.batchCount");

        if (!batchCount) {
            batchCount = 0;
        }

        batchCount += 1;
        data.set("batchBySize.batchCount", batchCount);

        // @type {number}
        let batchSize = configuration.get('batchSize');
        return !batchSize || batchSize === list.size;
    }

    /**
     * @Override
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @return {object}
     */
    get(deviceAnalog, attribute, configuration, data) {
        data.set("batchBySize.batchCount", 0);

        if (this.batchByPersistence) {
            // @type {Set<Pair<Message, StorageObject>>}
            const value = getPersistedBatchedData(deviceAnalog);
            return value;
        } else {
            // @type {object}
            const value = data.get("batchBySize.value");
            data.delete("batchBySize.value");
            return value;
        }
    }

    /**
     * @param {Map<string, object>} config
     * @return {string}
     */
    getDetails(config) {
        return super.getDetails(config) + '[batchSize=' + config.get('batchSize') + ']';
    }
}

class BATCH_BY_TIME extends DeviceFunction {
    constructor() {
        super('batchByTime');
    }

    /**
     *
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @param {object} value
     *
     * @return {boolean}
     */
    apply(deviceAnalog, attribute, configuration, data, value) {
        debug('DeviceFunction.BATCH_BY_TIME.apply called.');
        // @type {Set<object>}
        let list = data.get('batchByTime.value');

        if (this.batchByPersistence) {
            // @type {Message}
            const message = value.getKey();
            this.batchByPersistence.save(deviceAnalog.getEndpointId(), message);
        } else {
            // @type {Set<object>}
            let list = data.get("batchByTime.value");

            if (!list) {
                list = new Set();
                data.set("batchByTime.value", list);
            }

            list.add(value);
        }

        return false;
    }

    /**
     * @Override
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @return {object}
     */
    get(deviceAnalog, attribute, configuration, data) {
        debug('DeviceFunction.BATCH_BY_TIME.get called @' + new Date());
        let value = data.get('batchByTime.value');
        debug('DeviceFunction.BATCH_BY_TIME.get value = ' + util.inspect(value));

        if (this.batchByPersistence) {
            // @type {Set<Pair<Message, StorageObject>>}
            const value = getPersistedBatchedData(deviceAnalog);
            return value;
        } else {
            // @type {object}
            const value = data.get("batchByTime.value");
            data.delete("batchByTime.value");
            return value;
        }
    }

    /**
     * @param {Map<string, object>} config
     * @return {string}
     */
    getDetails(config) {
        return super.getDetails(config) + '[delayLimit=' + config.get('delayLimit') + ']';
    }
}

class COMPUTED_METRIC extends DeviceFunction {
    constructor() {
        super('computedMetric');
    }

    /**
     *
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @param {object} value
     *
     * @return {boolean}
     */
    apply(deviceAnalog, attribute, configuration, data, value) {
        // @type {FormulaParserNode}
        let formula = data.get('computedMetric.formula');

        if (!formula) {
            // @type {string}
            const str = configuration.get('formula');
            // @type {Set<FormulaParser.Token>}
            let tokens = FormulaParser.tokenize(str);
            formula = FormulaParser.parseFormula(tokens, str);
            data.set('computedMetric.formula', formula);
        }

        // @type {number}
        const computedValue = DeviceFunction.compute(formula, deviceAnalog);

        if (!isFinite(computedValue)) {
            return false;
        }

        data.set('computedMetric.value', computedValue);
        return true;
    }

    /**
     * @Override
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @return {object}
     */
    get(deviceAnalog, attribute, configuration, data) {
        const value = data.get('computedMetric.value');
        data.delete('computedMetric.value');
        return value;
    }


    /**
     * @param {Map<string, object>} config
     * @return {string}
     */
    getDetails(config) {
        return super.getDetails(config) + '[formula="' + config.get('formula') + '"]';
    }
}

class DETECT_DUPLICATES extends DeviceFunction {
    constructor() {
        super('detectDuplicates');
    }

    /**
     *
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @param {object} value
     *
     * @return {boolean}
     */
    apply(deviceAnalog, attribute, configuration, data, value) {
        // @type {number}
        const now = new Date().getTime();
        // @type {object}
        const lastValue = data.get('detectDuplicates.lastValue');
        data.set('detectDuplicates.lastValue', value);

        // If value equals lastValue, then this is a duplicate value.
        // If value is the first duplicate value, then lastValue has already
        // been passed along and we want to filter out the current value and
        // all other sequential duplicates within the window.
        if (value === lastValue) {
            // windowEnd is the end time of the current window.
            // @type {number}
            const windowEnd = data.get("detectDuplicates.windowEnd");

            // If the current window has expired (i.e., windowEnd <= now), then update windowEnd.
            if (windowEnd <= now) {
                // windowEnd is the current time plus the window. window is normalized so that
                // window is greater than or equal to zero.
                // @type {number}
                const window = DeviceFunction.getWindow(configuration);
                data.set("detectDuplicates.windowEnd", now + (window > 0 ? window : 0));
                // When the window moves, we need to send an alert.
                data.set("detectDuplicates.alertSent", false);
            }

            // The first time we get here, alertSent will be false (because of the "else" part
            // below) and an alert will be sent. alertSent will then be true until the window
            // expires or a non-duplicate value is received.
            // @type {boolean}
            const alertSent = data.get("detectDuplicates.alertSent");

            if (!alertSent) {
                data.set("detectDuplicates.alertSent", true);
                // @type {AlertMessage}
                const alertMessage = DeviceFunction.createAlert(deviceAnalog, configuration);
                deviceAnalog.queueMessage(alertMessage);
            }
        } else {
            // Values are not duplicates. Move window. windowEnd is the current time plus the
            // window. window is normalized so that window is greater than or equal to zero.
            // @type {number}
             const window = DeviceFunction.getWindow(configuration);
            data.set("detectDuplicates.windowEnd", now + (window > 0 ? window : 0));
            data.set("detectDuplicates.alertSent", false);
        }

        // detectDuplicates does not filter data. Return true.
        return true;
    }

    /**
     * @Override
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @return {object}
     */
    get(deviceAnalog, attribute, configuration, data) {
        // @type {object}
        return data.get('detectDuplicates.lastValue');
    }

    /**
     * @param {Map<string, object>} config
     * @return {string}
     */
    getDetails(config) {
        return super.getDetails(config) + '[window=' + config.get('window') +
            ', alertFormatURN="' + config.get('alertFormatURN') + '"]';
    }
}

class ELIMINATE_DUPLICATES extends DeviceFunction {
    constructor() {
        super('eliminateDuplicates');
    }

    /**
     *
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @param {object} value
     *
     * @return {boolean}
     */
    apply(deviceAnalog, attribute, configuration, data, value) {
        // @type {boolean}
        let isDuplicate = false;
        // @type {number}
        const now = new Date().getTime();
        // @type {object}
        const lastValue = data.get('eliminateDuplicates.lastValue');
        data.set('eliminateDuplicates.lastValue', value);

        // If value equals lastValue, then this is a duplicate value.
        // If value is the first duplicate value, then lastValue has already
        // been passed along and we want to filter out the current value and
        // all other sequential duplicates within the window.
        if (value === lastValue) {
            // windowEnd is the end time of the current window.
            // @type {number}
            const windowEnd = data.get("eliminateDuplicates.windowEnd");

            // If the current window has not expired (i.e., now <= windowEnd), then the value is
            // filtered out.
            isDuplicate = (now <= windowEnd);

            // If the current window has expired (i.e., windowEnd <= now),
            // then update windowEnd.
            if (windowEnd <= now) {
                // windowEnd is the current time plus the window.
                // window is normalized so that window is greater than or equal to zero.
                // @type {number}
                const window = DeviceFunction.getWindow(configuration);
                data.set("eliminateDuplicates.windowEnd", now + (window > 0 ? window : 0));
            }
        } else {
            // Values are not duplicates. Move window. windowEnd is the current time plus the
            // window. window is normalized so that window is greater than or equal to zero.
            // @type {number}
            const window = DeviceFunction.getWindow(configuration);
            data.set("eliminateDuplicates.windowEnd", now + (window > 0 ? window : 0));
        }

        return !isDuplicate;
    }

    /**
     * @Override
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @return {object}
     */
    get(deviceAnalog, attribute, configuration, data) {
        return data.get('eliminateDuplicates.lastValue');
    }

    /**
     * @param {Map<string, object>} config
     * @return {string}
     */
    getDetails(config) {
        return super.getDetails(config) + '[window=' + config.get('window') + ']';
    }
}

class FILTER_CONDITION extends DeviceFunction {
    constructor() {
        super('filterCondition');
    }

    /**
     * @Override
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @param {object} value
     * @return {boolean}
     */
    apply(deviceAnalog, attribute, configuration, data, value) {
        data.set('filterCondition.value', value);
        // @type {FormulaParserNode}
        let condition = data.get('filterCondition.condition');

        if (!condition) {
            // @type {string}
            const str = configuration.get('condition');
            // @type {Set<Token>}
            let tokens = FormulaParser.tokenize(String(str));

            // @type {Stack<FormulaParserNode>}
            let stack = new Stack();
            FormulaParser.parseConditionalOrExpression(stack, tokens, str, 0);
            condition = stack.pop();
            data.set('filterCondition.condition', condition);
        }

        // @type {number}
        const computedValue = DeviceFunction.compute(condition, deviceAnalog);
        // For a filter condition, if the computation returns 0.0, meaning
        // the condition evaluated to false, then we want to return 'true'
        // because "filter" means out, not in.
        return -1.0 < computedValue && computedValue < 1.0;
    }


    /**
     * @Override
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @return {object}
     */
    get(deviceAnalog, attribute, configuration, data) {
        // @type {object}
        const value = data.get('filterCondition.value');
        data.delete('filterCondition.value');
        return value;
    }

    /**
     * @param {Map<string, object>} config
     * @return {string}
     */
    getDetails(config) {
        return super.getDetails(config) + '[condition="' + config.get('condition') + '"]';
    }
}

class MAX extends DeviceFunction {
    constructor() {
        super('max');
    }

    /**
     *
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @param {object} value
     *
     * @return {boolean}
     */
    apply(deviceAnalog, attribute, configuration, data, value) {
        // See DeviceFunction("mean") for details on handling slide
        // and what all this bucket stuff is about
        // @type {number}
        const now = new Date().getTime();
        // @type {number}
        let windowStartTime = data.get("max.windowStartTime");

        if (!windowStartTime) {
            windowStartTime = now;
            data.set("max.windowStartTime", windowStartTime);
        }

        // @type {number}
        const window =DeviceFunction.getWindow(configuration);
        // @type {number}
        const slide =DeviceFunction.getSlide(configuration, window);
        // @type {number}
        const span =DeviceFunction.gcd(window, slide);
        // @type {Bucket[]}
        let buckets = data.get("max.buckets");

        if (!buckets) {
            // @type {number}
            const numberOfBuckets = (Math.max(slide,window) / span) + 1;
            buckets = new Array(numberOfBuckets);

            for (let i = 0; i < numberOfBuckets; i++) {
                buckets[i] = new Bucket(Number.MIN_VALUE);
            }

            data.set("max.buckets", buckets);
        }

        // @type {number}
        let bucketZero = data.get("max.bucketZero");

        if (!bucketZero && (bucketZero !== 0)) {
            bucketZero = 0;
            data.set("max.bucketZero", bucketZero);
        }

        // @type {number}
        const bucketIndex = Math.trunc((now - windowStartTime) / span);
        // @type {number}
        const bucket = (bucketZero + bucketIndex) % buckets.length;

        // @type {number}
        let max = buckets[bucket].value;

        buckets[bucket].value = (value <= max) ? max : value;
        return false;
    }

    /**
     * @Override
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @return {object}
     */
    get(deviceAnalog, attribute, configuration, data) {
        // See DeviceFunction("mean")#get for explanation of slide and buckets
        // @type {Bucket[]}
        const buckets = data.get("max.buckets");

        if (!buckets) {
            // Must have called get before apply.
            return null;
        }

        // @type {number}
        const bucketZero = data.get("max.bucketZero");

        if (!bucketZero && (bucketZero !== 0)) {
            // If buckets is not null, but bucketZero is, something is wrong with our implementation.
            return null;
        }

        // @type {number}
        const window =DeviceFunction.getWindow(configuration);
        // @type {number}
        const slide =DeviceFunction.getSlide(configuration, window);
        // @type {number}
        const span =DeviceFunction.gcd(window, slide);
        // @type {number}
        const bucketsPerWindow = window / span;
        // @type {number}
        const bucketsPerSlide = slide / span;
        // @type {number}
        let windowStartTime = data.get("max.windowStartTime");

        if (!windowStartTime) {
            windowStartTime = new Date().getTime();
        }

        data.set("max.windowStartTime", windowStartTime + span * bucketsPerSlide);
        data.set("max.bucketZero", (bucketZero + bucketsPerSlide) % buckets.length);

        // @type {number}
        let max = Number.MIN_VALUE;

        for (let i = 0; i < bucketsPerWindow; i++) {
            // @type {number}
            const index = (bucketZero + i) % buckets.length;
            // @type {Bucket}
            let bucket = buckets[index];
            // @type {number}
            let num = bucket.value;
            max = (num <= max) ? max : num;
        }

        for (let i = 0; i < bucketsPerSlide; i++) {
            // @type {Bucket}
            let bucket = buckets[(bucketZero + i) % buckets.length];
            bucket.value = Number.MIN_VALUE;
        }

        return max;
    }

    /**
     * @param {Map<string, object>} config
     * @return {string}
     */
    getDetails(config) {
        // @type {string}
        let details = super.getDetails(config);
        // @type {object}
        const window = config.get("window");

        if (window) {
            details += '[window=' + window;
        }

        // @type {object}
        const slide = config.get("slide");

        if (slide) {
            details += (window) ? ',' : '[';
            details += 'slide=' + slide;
        }

        details += ']';
        return details;
    }
}

class MEAN extends DeviceFunction {
    constructor() {
        super('mean');
    }

    /**
     * @Override
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @param {object} value
     * @return {boolean}
     */
    apply(deviceAnalog, attribute, configuration, data, value) {
        // Handling slide:
        //
        // Slide is how much the window moves after the
        // window expires. If there is a window of 5 seconds
        // with a slide of 2 seconds, then at the end of
        // 5 seconds, the window slides over by two seconds.
        // That means that the next window's worth of data
        // would include 3 seconds of data from the previous
        // window, and 2 seconds of new data.
        //
        // To handle this, we divide up the window into buckets.
        // Each bucket represents a period of time, such
        // that the time period is the greatest common factor
        // between the window and the slide. For example, if
        // the window is 60 seconds and the slide is 90
        // seconds, a bucket would span 30 seconds, and
        // there would be three buckets.
        //
        // When the window expires and the get method is called,
        // the return value of the mean policy function will
        // include the value and number of terms of bucket[0]
        // through bucket[n]. Then the buckets that don't
        // contribute to the next window are emptied (so to speak)
        // and the cycle continues.
        //
        // Best case is that the slide equal to the window.
        // In this case, there is only ever one bucket.
        // The worst case is when greatest common factor between
        // slide and window is small. In this case, you end up
        // with a lot of buckets, potentially one bucket per
        // slide time unit (e.g., 90 seconds, 90 buckets).
        // But this is no worse (memory wise) than keeping
        // an array of values and timestamps.
        //
        // @type {number}
        const now = new Date().getTime();

        // windowStartTime is the time at which the first
        // call to apply was made for the current window
        // of time. We need to know when this window
        // started so that we can figure out what bucket
        // the data goes into.
        // @type {number}
        let windowStartTime = data.get("mean.windowStartTime");

        if (!windowStartTime) {
            windowStartTime = now;
            data.set("mean.windowStartTime", windowStartTime);
        }

        // The greatest common factor between the
        // window and the slide represents the greatest
        // amount of time that goes evenly into
        // both window and slide.
        // @type {number}
        const window = DeviceFunction.getWindow(configuration);
        // @type {number}
        const slide = DeviceFunction.getSlide(configuration, window);
        // Each bucket spans this amount of time.
        // @type {number}
        const span = DeviceFunction.gcd(window, slide);
        // @type {Bucket[]}
        let buckets = data.get("mean.buckets");

        if (!buckets) {
            // The number of buckets is the window or span
            // (which ever is greater) divided
            // by the amount of time it spans. Thus, if
            // there is a 5 second window with a 2 second slide,
            // the greatest common factor is 1 second and we end
            // up with 5 buckets. But if the slide was 7 seconds,
            // you'd end up with 7 buckets. This lets us fill
            // up buckets without worrying about whether the
            // window is greater than, equal to, or less than
            // the slide.
            // Note: we add 1 so there is a bucket for when
            // a value comes in for the next window, but before
            // the window has been moved.
            // @type {number}
            const numberOfBuckets = (Math.max(slide, window) / span) + 1;
            buckets = new Array(numberOfBuckets);

            for (let i = 0; i < numberOfBuckets; i++) {
                buckets[i] = new Bucket(0);
            }

            data.set("mean.buckets", buckets);
        }

        // bucketZero is the index of the zeroth bucket
        // in the buckets array. This allows the buckets array
        // to be treated as a circular buffer so we don't have
        // to move array elements when the window slides.
        // @type {number}
        let bucketZero = data.get("mean.bucketZero");

        if (!bucketZero && (bucketZero !== 0)) {
            bucketZero = 0;
            data.set("mean.bucketZero", bucketZero);
        }

        // Which bucket are we working on is calculated
        // by the dividing the amount of time we are into
        // the window by the span of time represented by
        // one bucket. For example, say we have a 2 second
        // slide and a 10 second window giving us 5 buckets.
        // Say our window started at 20 seconds and the
        // value arrives at 25 seconds (5 seconds into the
        // window). The value, then should be added to the
        // third bucket (buckets[2]) since that bucket
        // represents the time from 4 seconds to 6 seconds
        // into the current window.
        // @type {number}
        const bucketIndex = Math.trunc((now - windowStartTime) / span);
        // @type {number}
        const bucket = (bucketZero + bucketIndex) % buckets.length;
        buckets[bucket].value += value;
        buckets[bucket].terms += 1;

        return false;
    }

    /**
     * @Override
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @return {object}
     */
    get(deviceAnalog, attribute, configuration, data) {
        // @type {Bucket[]}
        const buckets = data.get("mean.buckets");

        if (!buckets) {
            // Must have called get before apply.
            return null;
        }

        // @type {number}
        const bucketZero = data.get("mean.bucketZero");

        if (!bucketZero && (bucketZero !== 0)) {
            // If buckets is not null, but bucketZero is, something is wrong with our implementation.
            return null;
        }

        // The greatest common factor between the
        // window and the slide represents the greatest
        // amount of time that goes evenly into
        // both window and slide.
        // @type {number}
        const window = DeviceFunction.getWindow(configuration);
        // @type {number}
        const slide = DeviceFunction.getSlide(configuration, window);

        // Each bucket spans this amount of time.
        // @type {number}
        const span = DeviceFunction.gcd(window, slide);

        // The number of buckets that make up a window.
        // @type {number}
        const bucketsPerWindow = window / span;

        // The number of buckets that make up the slide.
        // @type {number}
        const bucketsPerSlide = slide / span;

        // Update windowStartTime for the next window.
        // The new windowStartTime is just the current window
        // start time plus the slide.
        // @type {number}
        let windowStartTime = data.get("mean.windowStartTime");

        if (!windowStartTime) {
            windowStartTime = new Date().getTime();
        }

        data.set("mean.windowStartTime", windowStartTime + span * bucketsPerSlide);

        // Update bucketZero index. bucketZero is the index
        // of the zeroth bucket in the circular buckets array.
        data.set("mean.bucketZero", (bucketZero + bucketsPerSlide) % buckets.length);
        // @type {number}
        let sum = 0;
        // @type {number}
        let terms = 0;

        // Loop through the number of buckets in the window and sum them up.
        for (let i = 0; i < bucketsPerWindow; i++) {
            // @type {number}
            const index = (bucketZero + i) % buckets.length;
            // @type {Bucket}
            let bucket = buckets[index];
            sum += bucket.value;
            terms += bucket.terms;
        }

        // Now slide the window.
        for (let i = 0; i < bucketsPerSlide; i++) {
            // @type {Bucket}
            let bucket = buckets[(bucketZero + i) % buckets.length];
            bucket.value = 0;
            bucket.terms = 0;
        }

        if ((sum === DeviceFunction.ZERO) || (terms === 0)) {
            return null;
        }

        return sum / terms;
    }

    /**
     * @param {Map<string, object>} config
     * @return {string}
     */
    getDetails(config) {
        // @type {string}
        let details = super.getDetails(config);
        // @type {object}
        const window = config.get("window");

        if (window) {
            details += '[window=' + window;
        }

        // @type {object}
        const slide = config.get("slide");

        if (slide) {
            details += (window) ? ',' : '[';
            details += 'slide=' + slide;
        }

        details += ']';
        return details;
    }
}

class MIN extends DeviceFunction {
    constructor() {
        super('min');
    }

    /**
     *
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @param {object} value
     *
     * @return {boolean}
     */
    apply(deviceAnalog, attribute, configuration, data, value) {
        // See DeviceFunction("mean") for details on handling slide
        // and what all this bucket stuff is about
        // @type {number}
        const now = new Date().getTime();
        // @type {number}
        let windowStartTime = data.get("min.windowStartTime");

        if (!windowStartTime) {
            windowStartTime = now;
            data.set("min.windowStartTime", windowStartTime);
        }

        // @type {number}
        const window = DeviceFunction.getWindow(configuration);
        // @type {number}
        const slide = DeviceFunction.getSlide(configuration, window);
        // @type {number}
        const span = DeviceFunction.gcd(window, slide);
        // @type {Bucket[]}
        let buckets = data.get("min.buckets");

        if (!buckets) {
            // @type {number}
            const numberOfBuckets = (Math.min(slide,window) / span) + 1;
            buckets = new Array(numberOfBuckets);

            for (let i = 0; i < numberOfBuckets; i++) {
                buckets[i] = new Bucket(Number.MAX_VALUE);
            }

            data.set("min.buckets", buckets);
        }

        // @type {number}
        let bucketZero = data.get("min.bucketZero");

        if (!bucketZero && (bucketZero !== 0)) {
            bucketZero = 0;
            data.set("min.bucketZero", bucketZero);
        }

        // @type {number}
        const bucketIndex = Math.trunc((now - windowStartTime) / span);
        // @type {number}
        const bucket = (bucketZero + bucketIndex) % buckets.length;
        // @type {number}
        const min = buckets[bucket].value;
        buckets[bucket].value = (value <= min) ? value : min;
        return false;
    }

    /**
     * @Override
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @return {object}
     */
    get(deviceAnalog, attribute, configuration, data) {
        // See DeviceFunction("mean")#get for explanation of slide and buckets.
        // @type {Bucket[]}
        const buckets = data.get("min.buckets");

        if (!buckets) {
            // Must have called get before apply.
            return null;
        }

        // @type {number}
        const bucketZero = data.get("min.bucketZero");

        if (!bucketZero && (bucketZero !== 0)) {
            // If buckets is not null, but bucketZero is, something is wrong with our implementation.
            return null;
        }

        // @type {number}
        const window = DeviceFunction.getWindow(configuration);
        // @type {number}
        const slide = DeviceFunction.getSlide(configuration, window);
        // @type {number}
        const span = DeviceFunction.gcd(window, slide);
        // @type {number}
        const bucketsPerWindow = window / span;
        // @type {number}
        const bucketsPerSlide = slide / span;
        // @type {number}
        let windowStartTime = data.get("min.windowStartTime");

        if (!windowStartTime) {
            windowStartTime = new Date().getTime();
        }

        data.set("min.windowStartTime", windowStartTime + span * bucketsPerSlide);
        data.set("min.bucketZero", (bucketZero + bucketsPerSlide) % buckets.length);
        // @type {number}
        let min = Number.MAX_VALUE;

        for (let i = 0; i < bucketsPerWindow; i++) {
            // @type {number}
            const index = (bucketZero + i) % buckets.length;
            // @type {Bucket}
            let bucket = buckets[index];
            // @type {number}
            let num = bucket.value;
            min = num <=  min ? num : min;
        }

        for (let i = 0; i < bucketsPerSlide; i++) {
            // @type {Bucket}
            let bucket = buckets[(bucketZero + i) % buckets.length];
            bucket.value = Number.MAX_VALUE;
        }

        return min;
    }

    /**
     * @param {Map<string, object>} config
     * @return {string}
     */
    getDetails(config) {
        // @type {string}
        let details = super.getDetails(config);
        // @type {object}
        const window = config.get("window");

        if (window) {
            details += '[window=' + window;
        }

        // @type {object}
        const slide = config.get("slide");

        if (slide) {
            details += (window) ? ',' : '[';
            details += 'slide=' + slide;
        }

        details += ']';
        return details;
    }
}

class SAMPLE_QUALITY extends DeviceFunction {
    constructor() {
        super('sampleQuality');
    }

    /**
     *
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @param {object} value
     *
     * @return {boolean}
     */
    apply(deviceAnalog, attribute, configuration, data, value) {
        // Always put the value in the data map.
        data.set("sample.value", value);
        // @type {number}
        let terms = data.get("sample.terms");

        if (!terms || terms === Number.MAX_VALUE) {
            terms = 0;
        }

        data.set("sample.terms", ++terms);
        // @type {number}
        const criterion = configuration.get("rate");

        // -1 is random, 0 is all
        if (criterion === 0) {
            return true;
        } else if (criterion === -1) {
            // TODO: make configurable
            return (Math.floor(Math.random() * 30) === 0);
        }

        return (criterion === terms);
    }

    /**
     * @Override
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @return {object}
     */
    get(deviceAnalog, attribute, configuration, data) {
        const sample = data.get("sample.value");
        data.delete("sample.value");
        data.delete("sample.terms");
        return sample;
    }


    getDetails(config) {
        // @type {object}
        const rate = config.get("rate");
        // @type {string}
        const isString = ("all" === rate) || ("none" === rate) || ("random" === rate);
        return super.getDetails(config) + '[rate=' + (isString ? '"' + rate + '"' : rate) + ']';
    }
}


class STANDARD_DEVIATION extends DeviceFunction {
    constructor() {
        super('standardDeviation');
    }

    /**
     *
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @param {object} value
     *
     * @return {boolean}
     */
    apply(deviceAnalog, attribute, configuration, data, value) {
        // See DeviceFunction("mean") for details on handling slide
        // and what all this bucket stuff is about
        // @type {number}
        const now = new Date().getTime();
        // @type {number}
        let windowStartTime = data.get("standardDeviation.windowStartTime");

        if (!windowStartTime) {
            windowStartTime = now;
            data.set("standardDeviation.windowStartTime", windowStartTime);
        }

        // @type {number}
        const window = DeviceFunction.getWindow(configuration);
        // @type {number}
        const slide = DeviceFunction.getSlide(configuration, window);
        // @type {number}
        const span = DeviceFunction.gcd(window, slide);
        // @type {Bucket<Set>>[]}
        let buckets = data.get("standardDeviation.buckets");

        if (!buckets) {
            // @type {number}
            const numberOfBuckets = (Math.min(slide, window) / span) + 1;
            buckets = new Array(numberOfBuckets);

            for (let i = 0; i < numberOfBuckets; i++) {
                buckets[i] = new Bucket(new Set());
            }

            data.set("standardDeviation.buckets", buckets);
        }

        // @type {number}
        let bucketZero = data.get("standardDeviation.bucketZero");

        if (!bucketZero && (bucketZero !== 0)) {
            bucketZero = 0;
            data.set("standardDeviation.bucketZero", bucketZero);
        }

        // @type {number}
        const bucketIndex = Math.trunc((now - windowStartTime) / span);
        // @type {number}
        const bucket = (bucketZero + bucketIndex) % buckets.length;
        buckets[bucket].value.add(value);
        return false;
    }

    /**
     * @Override
     * @param {DeviceAnalog} deviceAnalog
     * @param {(string|null)} attribute
     * @param {Map<string, object>} configuration
     * @param {Map<string, object>} data
     * @return {object}
     */
    get(deviceAnalog, attribute, configuration, data) {
        // See DeviceFunction("mean")#get for explanation of slide and buckets
        // @type {Bucket<Set>[]}
        const buckets = data.get("standardDeviation.buckets");

        if (!buckets) {
            // Must have called get before apply.
            return null;
        }

        // @type {number}
        const  bucketZero = data.get("standardDeviation.bucketZero");

        if (!bucketZero && (bucketZero !== 0)) {
            // If buckets is not null, but bucketZero is, something is wrong with our implementation.
            return null;
        }

        // @type {number}
        const window = DeviceFunction.getWindow(configuration);
        // @type {number}
        const slide = DeviceFunction.getSlide(configuration, window);
        // @type {number}
        const span = DeviceFunction.gcd(window, slide);
        // @type {number}
        const bucketsPerWindow = window / span;
        // @type {number}
        const bucketsPerSlide = slide / span;
        // @type {number}
        let windowStartTime = data.get("standardDeviation.windowStartTime");

        if (!windowStartTime) {
            windowStartTime = new Date().getTime();
        }

        data.set("standardDeviation.windowStartTime", windowStartTime + span * bucketsPerSlide);
        data.set("standardDeviation.bucketZero", (bucketZero + bucketsPerSlide) % buckets.length);
        // @type {Set<number>}
        let terms = new Set();

        for (let i = 0; i < bucketsPerWindow; i++) {
            // @type {number}
            const index = (bucketZero + i) % buckets.length;
            // @type {Bucket<Set<number>>}
            let bucket = buckets[index];
            // @type {Set<number>}
            let values = bucket.value;

            values.forEach(val => {
                terms.add(val);
            });
        }

        // @type {number}
        let sum = 0;
        let termsAry = Array.from(terms);

        for (let n = 0, nMax = termsAry.length; n < nMax; n++) {
            // @type {number}
            sum += termsAry[n];
        }

        // @type {number}
        let mean = sum / termsAry.length;

        for (let n = 0, nMax = termsAry.length; n < nMax; n++) {
            // @type {number}
            let d = termsAry[n] - mean;
            termsAry[n] = Math.pow(d, 2);
        }

        sum = 0;

        for (let n = 0, nMax = termsAry.length; n < nMax; n++) {
            // @type {number}
            sum += termsAry[n];
        }

        mean = sum / termsAry.length;

        // @type {number}
        let stdDeviation = Math.sqrt(mean);

        for (let i = 0; i < bucketsPerSlide; i++) {
            // @type {Bucket<Set<number>>}
            let bucket = buckets[(bucketZero + i) % buckets.length];
            bucket.value.clear();
        }

        return stdDeviation;
    }

    /**
     * @param {Map<string, object>} config
     * @return {string}
     */
    getDetails(config) {
        // @type {string}
        let details = super.getDetails(config);
        // @type {object}
        const window = config.get("window");

        if (window) {
            details += '[window=' + window;
        }

        // @type {object}
        const slide = config.get("slide");

        if (slide) {
            details += (window) ? ',' : '[';
            details += 'slide=' + slide;
        }

        details += ']';
        return details;
    }
}

DeviceFunction.ZERO = 0.0;
DeviceFunction.POLICY_MAP = new Map();
let actionConditionDeviceFunction = new ACTION_CONDITION();
DeviceFunction.POLICY_MAP.set(actionConditionDeviceFunction.getId(), actionConditionDeviceFunction);
let alertConditionDeviceFunction = new ALERT_CONDITION();
DeviceFunction.POLICY_MAP.set(alertConditionDeviceFunction.getId(), alertConditionDeviceFunction);
let batchByCostDeviceFunction = new BATCH_BY_COST();
DeviceFunction.POLICY_MAP.set(batchByCostDeviceFunction.getId(), batchByCostDeviceFunction);
let batchBySizeDeviceFunction = new BATCH_BY_SIZE();
DeviceFunction.POLICY_MAP.set(batchBySizeDeviceFunction.getId(), batchBySizeDeviceFunction);
let batchByTimeDeviceFunction = new BATCH_BY_TIME();
DeviceFunction.POLICY_MAP.set(batchByTimeDeviceFunction.getId(), batchByTimeDeviceFunction);
let computedMetricDeviceFunction = new COMPUTED_METRIC();
DeviceFunction.POLICY_MAP.set(computedMetricDeviceFunction.getId(), computedMetricDeviceFunction);
let detectDuplicatesDeviceFunction = new DETECT_DUPLICATES();
DeviceFunction.POLICY_MAP.set(detectDuplicatesDeviceFunction.getId(), detectDuplicatesDeviceFunction);
let eliminateDuplicatesDeviceFunction = new ELIMINATE_DUPLICATES();
DeviceFunction.POLICY_MAP.set(eliminateDuplicatesDeviceFunction.getId(),
    eliminateDuplicatesDeviceFunction);
let filterConditionDeviceFunction = new FILTER_CONDITION();
DeviceFunction.POLICY_MAP.set(filterConditionDeviceFunction.getId(), filterConditionDeviceFunction);
let maxDeviceFunction = new MAX();
DeviceFunction.POLICY_MAP.set(maxDeviceFunction.getId(), maxDeviceFunction);
let meanDeviceFunction = new MEAN();
DeviceFunction.POLICY_MAP.set(meanDeviceFunction.getId(), meanDeviceFunction);
let minDeviceFunction = new MIN();
DeviceFunction.POLICY_MAP.set(minDeviceFunction.getId(), minDeviceFunction);
let sampleQualityDeviceFunction = new SAMPLE_QUALITY();
DeviceFunction.POLICY_MAP.set(sampleQualityDeviceFunction.getId(), sampleQualityDeviceFunction);
let standardDeviationDeviceFunction = new STANDARD_DEVIATION();
DeviceFunction.POLICY_MAP.set(standardDeviationDeviceFunction.getId(),
    standardDeviationDeviceFunction);
