/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * @alias iotcs.message
 * @memberof iotcs
 * @namespace
 */
lib.message = {};

/**
 * This object helps in the construction of a general type message to be sent to the server.  This
 * object and it's components are used as utilities by the Messaging API clients, like the
 * DirectlyConnectedDevice or GatewayDevice or indirectly by the MessageDispatcher.
 *
 * @alias iotcs.message.Message
 * @class
 * @memberof iotcs.message
 */
lib.message.Message = function () {
    this.onError = null;

    Object.defineProperty(this, '_',{
        enumerable: false,
        configurable: false,
        writable: true,
        value: {}
    });

    Object.defineProperty(this._, 'internalObject',{
        enumerable: false,
        configurable: false,
        writable: false,
        value: {
            clientId: $port.util.uuidv4(),
            source: null,
            destination: '',
            sender: '',
            priority: 'MEDIUM',
            reliability: 'BEST_EFFORT',
            eventTime: new Date().getTime(),
            type: null,
            properties: {},
            payload: {},
            remainingRetries: 3
        }
    });

    /**
     * Constant which defines the number of times sending of a message should be retried.  The
     * minimum is 3.
     *
     * @constant BASIC_NUMBER_OF_RETRIES
     * @default 3
     * @memberof iotcs.message.Message
     * @type {number}
     */
    Object.defineProperty(this._.internalObject, 'BASIC_NUMBER_OF_RETRIES', {
        enumerable: false,
        configurable: false,
        writable: true,
        value: 3
    });

    // TODO: Set the value of BASIC_NUMBER_OF_RETRIES after getting the environment variable
    // setting and then prevent the value from being changed.
    // Set the values of BASIC_NUMBER_OF_RETRIES and remainingRetries from environment variable.
    let maxRetries =
        (process.env['oracle.iot.client.device.dispatcher_basic_number_of_retries'] || 3);

    this._.internalObject.BASIC_NUMBER_OF_RETRIES = maxRetries > 3 ? maxRetries : 3;
    this._.internalObject.remainingRetries = this._.internalObject.BASIC_NUMBER_OF_RETRIES;
};

/**
 * Sets the payload of the message as object.
 *
 * @function payload
 * @memberof iotcs.message.Message.prototype
 *
 * @param {object} payload - The payload to set.
 * @returns {iotcs.message.Message} This object
 */
lib.message.Message.prototype.payload = function (payload) {
    _mandatoryArg(payload, 'object');

    this._.internalObject.payload = payload;
    return this;
};

/**
 * Gets the number of remaining retries for this message.  Not intended for general use.  Used
 * internally by the message dispatcher implementation.
 *
 * @function getRemainingRetries
 * @memberof iotcs.message.Message.prototype
 *
 * @returns {integer} remainingRetries - The new number of remaining retries.
 */
lib.message.Message.prototype.getRemainingRetries = function () {
    return this._.internalObject.remainingRetries;
};

/**
 * Sets the number of remaining retries for this message.  Not intended for general use.  Used
 * internally by the message dispatcher implementation.
 *
 * @function setRemainingRetries
 * @memberof iotcs.message.Message.prototype
 *
 * @param {integer} remainingRetries - The new number of remaining retries.
 * @returns {iotcs.message.Message} This object.
 */
lib.message.Message.prototype.setRemainingRetries = function (remainingRetries) {
    _mandatoryArg(remainingRetries, 'integer');
    this._.internalObject.remainingRetries = remainingRetries;
    return this;
};

/**
 * Sets the source of the message.
 *
 * @function source
 * @memberof iotcs.message.Message.prototype
 *
 * @param {string} source - The source to set.
 * @returns {iotcs.message.Message} This object.
 */
lib.message.Message.prototype.source = function (source) {
    _mandatoryArg(source, 'string');

    if(this._.internalObject.source === null) {
        this._.internalObject.source = source;
    }
    return this;
};

/**
 * Sets the destination of the message.
 *
 * @function destination
 * @memberof iotcs.message.Message.prototype
 *
 * @param {string} destination - The destination.
 * @returns {iotcs.message.Message} This object.
 */
lib.message.Message.prototype.destination = function (destination) {
    _mandatoryArg(destination, 'string');

    this._.internalObject.destination = destination;
    return this;
};

/**
 * This returns the built message as JSON to be sent to the server as it is.
 *
 * @function getJSONObject
 * @memberof iotcs.message.Message.prototype
 *
 * @returns {object} A JSON representation of the message to be sent.
 */
lib.message.Message.prototype.getJSONObject = function () {
    return this._.internalObject;
};

/**
 * This sets the type of the message. Types are defined in the
 * Message.Type enumeration. If an invalid type is given an
 * exception is thrown.
 *
 * @function type
 * @memberof iotcs.message.Message.prototype
 * @see {@link iotcs.message.Message.Type}
 *
 * @param {string} type - The type to set.
 * @returns {iotcs.message.Message} This object.
 */
lib.message.Message.prototype.type = function (type) {
    _mandatoryArg(type, 'string');
    if (Object.keys(lib.message.Message.Type).indexOf(type) < 0) {
        lib.error('invalid message type given');
        return;
    }

    if(type === lib.message.Message.Type.RESOURCES_REPORT) {
        this._.internalObject.id = $port.util.uuidv4();
    }
    this._.internalObject.type = type;
    return this;
};

/**
 * This sets the format URN in the payload of the message.  This is mostly specific for the DATA or
 * ALERT type * of messages.
 *
 * @function format
 * @memberof iotcs.message.Message.prototype
 *
 * @param {string} format - The format to set.
 * @returns {iotcs.message.Message} This object.
 */
lib.message.Message.prototype.format = function (format) {
    _mandatoryArg(format, 'string');
    this._.internalObject.payload.format = format;
    return this;
};

/**
 * This sets a key/value pair in the data property of the payload of the message.  This is specific
 * to DATA or ALERT type messages.
 *
 * @function dataItem
 * @memberof iotcs.message.Message.prototype
 *
 * @param {string} dataKey - The key.
 * @param {object} [dataValue] - The value associated with the key.
 * @returns {iotcs.message.Message} This object.
 */
lib.message.Message.prototype.dataItem = function (dataKey, dataValue) {
    _mandatoryArg(dataKey, 'string');

    if (!('data' in this._.internalObject.payload)) {
        this._.internalObject.payload.data = {};
    }
    this._.internalObject.payload.data[dataKey] = dataValue;
    return this;
};

/**
 * This sets the priority of the message. Priorities are defined in the Message.Priority
 * enumeration. If an invalid type is given an exception is thrown. The MessageDispatcher implements
 * a priority queue and it will use this parameter.
 *
 * @function priority
 * @memberof iotcs.message.Message.prototype
 * @see {@link iotcs.device.util.MessageDispatcher}
 * @see {@link iotcs.message.Message.Priority}
 *
 * @param {string} priority - The priority to set.
 * @returns {iotcs.message.Message} This object.
 */
lib.message.Message.prototype.priority = function (priority) {
    _mandatoryArg(priority, 'string');
    if (Object.keys(lib.message.Message.Priority).indexOf(priority) < 0) {
        lib.error('invalid priority given');
        return;
    }

    this._.internalObject.priority = priority;
    return this;
};

/**
 * This sets the reliability of the message. Reliabilities are defined in the Message.Reliability
 * enumeration. If an invalid type is given, an exception is thrown.
 *
 * @function reliability
 * @memberof iotcs.message.Message.prototype
 * @see {@link iotcs.device.util.MessageDispatcher}
 * @see {@link iotcs.message.Message.Reliability}
 *
 * @param {string} priority - The reliability to set.
 * @returns {iotcs.message.Message} This object.
 */
lib.message.Message.prototype.reliability = function (reliability) {
    _mandatoryArg(reliability, 'string');
    if (Object.keys(lib.message.Message.Reliability).indexOf(reliability) < 0) {
        lib.error('Invalid reliability given.');
        return;
    }

    this._.internalObject.reliability = reliability;
    return this;
};


/**
 * @constant MAX_KEY_LENGTH
 * @default 2048
 * @memberof iotcs.message.Message
 * @type {number}
 */
Object.defineProperty(lib.message.Message, 'MAX_KEY_LENGTH',{
    enumerable: false,
    configurable: false,
    writable: false,
    value: 2048
});

/**
 * @constant MAX_STRING_VALUE_LENGTH
 * @default 65536
 * @memberof iotcs.message.Message
 * @type {number}
 */
Object.defineProperty(lib.message.Message, 'MAX_STRING_VALUE_LENGTH',{
    enumerable: false,
    configurable: false,
    writable: false,
    value: 64 * 1024
});

/**
 * Returns the number of remaining send retries available for this message.
 *
 * @memberof iotcs.message.Message
 * @type {number}
 * @default BASIC_NUMBER_OF_RETRIES
 */
Object.defineProperty(lib.message.Message, 'remainingRetries',{
    enumerable: false,
    configurable: false,
    writable: false,
    value: 3
});

/** @ignore */
function _recursiveSearchInMessageObject(obj, callback){
    var arrKeys = Object.keys(obj);
    for (var i = 0; i < arrKeys.length; i++) {
        callback(arrKeys[i], obj[arrKeys[i]]);
        if (typeof obj[arrKeys[i]] === 'object') {
            _recursiveSearchInMessageObject(obj[arrKeys[i]], callback);
        }
    }
}

/**
 * This is a helper method for checking if an array of created messages pass the boundaries on
 * key/value length test.  If the test does not pass an error is thrown.
 *
 * @function checkMessagesBoundaries
 * @memberof iotcs.message.Message
 * @see {@link iotcs.message.Message.MAX_KEY_LENGTH}
 * @see {@link iotcs.message.Message.MAX_STRING_VALUE_LENGTH}
 *
 * @param {iotcs.message.Message[]} messages - The array of messages that need to be tested.
 */
lib.message.Message.checkMessagesBoundaries = function (messages) {
    _mandatoryArg(messages, 'array');
    messages.forEach(function (message) {
        _mandatoryArg(message, lib.message.Message);
        _recursiveSearchInMessageObject(message.getJSONObject(), function (key, value) {
            if (_getUtf8BytesLength(key) > lib.message.Message.MAX_KEY_LENGTH) {
                lib.error('Max length for key in message item exceeded');
            }
            if ((typeof value === 'string') && (_getUtf8BytesLength(value) > lib.message.Message.MAX_STRING_VALUE_LENGTH)) {
                lib.error('Max length for value in message item exceeded');
            }
        });
    });
};

/**
 * Enumeration of message types.
 *
 * @alias iotcs.message.Message.Type
 * @class
 * @enum {string}
 * @memberof iotcs.message.Message
 * @readonly
 * @static
 */
lib.message.Message.Type = {
    DATA: 'DATA',
    ALERT: 'ALERT',
    REQUEST: 'REQUEST',
    RESPONSE: 'RESPONSE',
    RESOURCES_REPORT: 'RESOURCES_REPORT'
};

/**
 * Enumeration of message priorities.
 *
 * @alias iotcs.message.Message.Priority
 * @class
 * @enum {string}
 * @memberof iotcs.message.Message
 * @readonly
 * @static
 */
lib.message.Message.Priority = {
    LOWEST: 'LOWEST',
    LOW: 'LOW',
    MEDIUM: 'MEDIUM',
    HIGH: 'HIGH',
    HIGHEST: 'HIGHEST'
};

/**
 * Enumeration of message reliability options.
 *
 * @alias iotcs.message.Message.Reliability
 * @class
 * @enum {string}
 * @memberof iotcs.message.Message
 * @readonly
 * @static
 */
lib.message.Message.Reliability = {
    BEST_EFFORT: 'BEST_EFFORT',
    GUARANTEED_DELIVERY: 'GUARANTEED_DELIVERY',
    NO_GUARANTEE: 'NO_GUARANTEE'
};

/**
 * This is a helper method for building a response message to be sent to the server as response to a
 * request message sent from the server.  This is mostly used by handlers registered with the
 * RequestDispatcher.  If no requestMessage is given the id for the response message will be a
 * random UUID.
 *
 * @function buildResponseMessage
 * @memberof iotcs.message.Message
 * @see {@link iotcs.device.util.RequestDispatcher}
 *
 * @param {object} [requestMessage] - The message received from the server as JSON.
 * @param {number} statusCode - The status code to be added in the payload of the response message.
 * @param {object} [headers] - The headers to be added in the payload of the response message.
 * @param {string} [body] - The body to be added in the payload of the response message.
 * @param {string} [url] - The URL to be added in the payload of the response message.
 *
 * @returns {iotcs.message.Message} The response message instance built on the given parameters.
 */
lib.message.Message.buildResponseMessage = function (requestMessage, statusCode, headers, body, url) {
    _optionalArg(requestMessage, 'object');
    _mandatoryArg(statusCode, 'number');
    _optionalArg(headers, 'object');
    _optionalArg(body, 'string');
    _optionalArg(url, 'string');

    var payload = {
        statusCode: statusCode,
        url: (url ? url : ''),
        requestId: ((requestMessage && requestMessage.id) ? requestMessage.id : $port.util.uuidv4()),
        headers: (headers ? headers : {}),
        body: (body ? $port.util.btoa(body) : '')
    };
    var message = new lib.message.Message();
    message.type(lib.message.Message.Type.RESPONSE)
        .source((requestMessage && requestMessage.destination) ? requestMessage.destination : '')
        .destination((requestMessage && requestMessage.source) ? requestMessage.source : '')
        .payload(payload);
    return message;
};

/**
 * This is a helper method for building a response wait message to notify RequestDispatcher that
 * response for server will be sent to the server later.  RequestDispatcher doesn't send these kind
 * of messages to the server.  This is mostly used by handlers registered with the
 * RequestDispatcher in asynchronous cases, for example, when device creates storage object by URI.
 *
 * @function buildResponseWaitMessage
 * @memberof iotcs.message.Message
 * @see {@link iotcs.device.util.RequestDispatcher}
 * @see {@link iotcs.device.util.DirectlyConnectedDevice#createStorageObject}
 *
 * @returns {iotcs.message.Message} The response message that notified about waiting final response.
 */
lib.message.Message.buildResponseWaitMessage = function() {
    var message = new lib.message.Message();
    message._.internalObject.type = "RESPONSE_WAIT";
    return message;
};

/**
 * Helpers for building alert messages.
 *
 * @alias iotcs.message.Message.AlertMessage
 * @class
 * @memberof iotcs.message.Message
 */
lib.message.Message.AlertMessage = {};

/**
 * Enumeration of severities for alert messages
 *
 * @alias Severity
 * @class
 * @enum {string}
 * @memberof iotcs.message.Message.AlertMessage
 * @readonly
 * @static
 */
lib.message.Message.AlertMessage.Severity = {
    LOW: 'LOW',
    NORMAL: 'NORMAL',
    SIGNIFICANT: 'SIGNIFICANT',
    CRITICAL: 'CRITICAL'
};

/**
 * Helper method used for building alert messages to be sent to the server.  The severity is defined
 * in the AlertMessage.Severity enumeration.  If an invalid value is given an exception is thrown.
 *
 * @function buildAlertMessage
 * @memberOf iotcs.message.Message.AlertMessage
 * @see {@link iotcs.message.Message.AlertMessage.Severity}
 *
 * @param {string} format - The format added in the payload of the generated message.
 * @param {string} description - The description added in the payload of the generated message.
 * @param {string} severity - The severity added in the payload of the generated message.
 * @returns {iotcs.message.Message} The instance of the alert message built based on the given
 *          parameters.
 */
lib.message.Message.AlertMessage.buildAlertMessage = function (format, description, severity) {
    _mandatoryArg(format, 'string');
    _mandatoryArg(description, 'string');
    _mandatoryArg(severity, 'string');
    if (Object.keys(lib.message.Message.AlertMessage.Severity).indexOf(severity) < 0) {
        lib.error('invalid severity given');
        return;
    }

    var payload = {
        format: format,
        reliability: 'BEST_EFFORT',
        severity: severity,
        description: description,
        data: {}
    };
    var message = new lib.message.Message();
    message.type(lib.message.Message.Type.ALERT)
        .priority(lib.message.Message.Priority.HIGHEST)
        .payload(payload);
    return message;
};

/**
 * Helpers for building resource report messages.
 *
 * @alias iotcs.message.Message.ResourceMessage
 * @class
 * @memberof iotcs.message.Message
 */
lib.message.Message.ResourceMessage = {};

/**
 * Enumeration of the type of resource report messages.
 *
 * @alias Type
 * @class
 * @enum {string}
 * @memberof iotcs.message.Message.ResourceMessage
 * @readonly
 * @static
 */
lib.message.Message.ResourceMessage.Type = {
    UPDATE: 'UPDATE',
    DELETE: 'DELETE',
    RECONCILIATION: 'RECONCILIATION'
};

/**
 * This generates an MD5 hash of an array of strings.  This must to be used to generate the
 * reconciliationMark of the resource report message.
 *
 * @function getMD5ofList
 * @memberof iotcs.message.Message.ResourceMessage
 *
 * @param {string[]} stringArray - The array of strings to use to generate the hash.
 * @returns {string} The MD5 hash.
 */
lib.message.Message.ResourceMessage.getMD5ofList = function (stringArray) {
    _mandatoryArg(stringArray, 'array');
    stringArray.forEach( function (str) {
        _mandatoryArg(str, 'string');
    });

    var hash = forge.md.md5.create();
    var i;
    for (i = 0; i < stringArray.length; i++) {
        hash.update(stringArray[i]);
    }
    return hash.digest().toHex();
};

/**
 * Helper method used for building a resource report message to be sent to the server.  The
 * resources objects can be generated by using the ResourceMessage.Resource.buildResource method.
 * The reportType must be taken from the ResourceMessage.Type enumeration.  If an invalid value is
 * given an exception is thrown.  The rM parameter is the reconciliationMark that can be calculated
 * by using the ResourceMessage.getMD5ofList over the array of paths of the resources given as
 * objects.  A resource is an object that must have at least 2 properties as strings: path and
 * methods.  Also methods must be string that represents a concatenation of valid HTTP methods comma
 * separated.
 *
 * @function buildResourceMessage
 * @memberof iotcs.message.Message.ResourceMessage
 * @see {@link iotcs.message.Message.ResourceMessage.Resource.buildResource}
 * @see {@link iotcs.message.Message.ResourceMessage.Type}
 *
 * @param {object[]} resources - The array of resources that are included in the report message
 *        resource report message.
 * @param {string} endpointName - The endpoint that is giving the resource report.
 * @param {string} reportType - The type of the report.
 * @param {string} [rM] - The reconciliationMark used by the server to validate the report.
 * @returns {iotcs.message.Message} The instance of the resource report message to be sent to the
 *          server.
 */
lib.message.Message.ResourceMessage.buildResourceMessage = function (resources, endpointName, reportType, rM) {
    _mandatoryArg(resources, 'array');
    resources.forEach( function(resource) {
        _mandatoryArg(resource, 'object');
        _mandatoryArg(resource.path, 'string');
        _mandatoryArg(resource.methods, 'string');
        resource.methods.split(',').forEach( function (method) {
            if (['GET', 'PUT', 'POST', 'HEAD', 'OPTIONS', 'CONNECT', 'DELETE', 'TRACE'].indexOf(method) < 0) {
                lib.error('invalid method in resource message');
                return;
            }
        });
    });
    _mandatoryArg(endpointName, 'string');
    _mandatoryArg(reportType, 'string');
    if (Object.keys(lib.message.Message.ResourceMessage.Type).indexOf(reportType) < 0) {
        lib.error('invalid report type given');
        return;
    }
    _optionalArg(rM, 'string');

    var payload = {
        type: 'JSON',
        value: {}
    };
    payload.value.reportType = reportType;
    payload.value.endpointName = endpointName;
    payload.value.resources = resources;
    if (rM) {
        payload.value.reconciliationMark = rM;
    }
    var message = new lib.message.Message();
    message.type(lib.message.Message.Type.RESOURCES_REPORT)
        .payload(payload);
    return message;
};

/**
 * Helpers used to build resource objects, used by the resource report messages.
 *
 * @alias iotcs.message.Message.ResourceMessage.Resource
 * @class
 * @memberof iotcs.message.Message.ResourceMessage
 */
lib.message.Message.ResourceMessage.Resource = {};

/**
 * Enumeration of possible statuses of the resources.
 *
 * @alias Status
 * @class
 * @enum {string}
 * @memberof iotcs.message.Message.ResourceMessage.Resource
 * @readonly
 * @static
 */
lib.message.Message.ResourceMessage.Resource.Status = {
    ADDED: 'ADDED',
    REMOVED: 'REMOVED'
};

/**
 * Helper method used to build a resource object. The status parameter must be given from the
 * Resource.Status enumeration. If an invalid value is given the method will throw an exception.
 * Also the methods array must be an array of valid HTTP methods, otherwise an exception will be
 * thrown.
 *
 * @function buildResource
 * @memberof iotcs.message.Message.ResourceMessage.Resource
 * @see {@link iotcs.message.Message.ResourceMessage.Resource.Status}
 *
 * @param {string} name - The name of the resource.
 * @param {string} path - The path of the resource.
 * @param {string} methods - A comma-separated string of the methods that the resource implements.
 * @param {string} status - The status of the resource.  Must be one of
 *        lib.message.Message.ResourceMessage.Resource.Status.
 * @param {string} [endpointName] - The endpoint associated with the resource.
 * @returns {object} The instance of the object representing a resource.
 */
lib.message.Message.ResourceMessage.Resource.buildResource = function (name, path, methods, status,
                                                                       endpointName)
{
    _mandatoryArg(name, 'string');
    _mandatoryArg(path, 'string');
    _mandatoryArg(methods, 'string');
    methods.split(',').forEach( function (method) {
        if (['GET', 'PUT', 'POST', 'HEAD', 'OPTIONS', 'CONNECT', 'DELETE', 'TRACE'].indexOf(method) < 0) {
            lib.error('invalid method in resource message');
            return;
        }
    });
    _mandatoryArg(status, 'string');
    _optionalArg(endpointName, 'string');
    if (Object.keys(lib.message.Message.ResourceMessage.Resource.Status).indexOf(status) < 0) {
        lib.error('invalid status given');
        return;
    }

    var obj = {};
    obj.name = name;
    obj.path = path;
    obj.status = status;
    obj.methods = methods.toString();

    if (endpointName) {
        obj.endpointName = endpointName;
    }

    return obj;
};
