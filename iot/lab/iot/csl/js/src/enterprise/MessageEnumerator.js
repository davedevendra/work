/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

//@TODO: jsdoc issue: MessageEnumerator appears in iotcs.* and not at index level (probably due to missing class jsdoc on lib.enterprise.MessageEnumerator) @DONE
//@TODO: move default value pageSize to lib.oracle.iot.... global @DONE
//@TODO: move default value messageListenMaxSize to lib.oracle.iot.... global @DONE: changed name also

/**
 * A class that implements a way of getting the history of all messages from the cloud and also
 * register listeners to the messages received.
 *
 * @alias iotcs.enterprise.MessageEnumerator
 * @class
 * @memberof iotcs.enterprise
 *
 * @param {iotcs.enterprise.EnterpriseClient} client - The enterprise client associated with the
 *        application context of the messages that need to be enumerated or listened to.
 */
lib.enterprise.MessageEnumerator = function (client) {
    _mandatoryArg(client, lib.enterprise.EnterpriseClient);
    this.client = client;
    this._message = {
        callbacks: {},
        monitors: {},
        lastTime: {},
        inProgress: {},
        maxLimit: 1000,
        types: ['DATA', 'ALERT', 'REQUEST', 'RESPONSE', 'WAKEUP', 'UPDATE_BUNDLE', 'RESOURCES_REPORT'],
        allKey: 'ALL'
    };
};

//@TODO: (jy) check why model is param,paramValue
/**
 * Return a list of messages according to the given parameters.  The method will generate a query
 * and make a request to the cloud and a list of messages will be returned based on the query in
 * descendant order of arrival of the messages to the cloud.
 * <p>
 * The query for messages must be made based on one of the following criteria or both:
 * <br>
 * - "device": messages from a specific device<br>
 * - "type": messages of a given type<br>
 *
 * @function getMessages
 * @memberof iotcs.enterprise.MessageEnumerator.prototype
 *
 * @param {?string} [deviceId] - The ID of the device as the source of the messages from the
 *        enumerator. If this is <code>null</code> the messages for all devices will be enumerated.
 * @param {?string} [messageType] - The type of the messages to be enumerated.  If this is
 *        <code>null</code> then messages of all types will be enumerated.  The only types are:
 *        ['DATA', 'ALERT', 'REQUEST', 'RESPONSE', 'WAKEUP', 'UPDATE_BUNDLE', 'RESOURCES_REPORT'].
 * @param {?boolean} [expand] - A flag that would say if the messages in the response contains
 *        expanded data. If this is not present the value is false.
 * @param {?number} [since] - The timestamp in milliseconds since EPOC that would represent that
 *        minimum time when the messages were received.
 * @param {?number} [until] - The timestamp in milliseconds since EPOC that would represent that
 *        maximum time when the messages were received.
 * @returns {iotcs.enterprise.Pageable} A pageable instance with which pages can be requested that
 *          contain messages as items.
 */
lib.enterprise.MessageEnumerator.prototype.getMessages = function(deviceId, messageType, expand, since, until){
    _optionalArg(deviceId, 'string');
    _optionalArg(messageType, 'string');
    _optionalArg(expand, 'boolean');
    _optionalArg(since, 'number');
    _optionalArg(until, 'number');

    if (messageType && this._message.types.indexOf(messageType) === -1) {
        lib.error('invalid parameter');
        return;
    }

    var separator = '&';
    var query = '?orderBy=deliveredTime:asc';

    if (deviceId) {
        query = query + separator + 'device=' + deviceId;
    }
    if (messageType) {
        query = query + separator + 'type=' + messageType;
    }
    if (expand) {
        query = query + separator + 'expand=true';
    }
    if (since) {
        query = query + separator + 'since=' + since;
    }
    if (until) {
        query = query + separator + 'until=' + until;
    }

    return new lib.enterprise.Pageable({
        method: 'GET',
        path:   $impl.reqroot
            + '/apps/' + this.client.appid
            + '/messages'
            + query
    }, '', null, this.client);

};

/**
 * Registers a callback method to be called when new messages of a given type and/or for a given
 * device are received.
 *
 * @function setListener
 * @memberof iotcs.enterprise.MessageEnumerator.prototype
 * @see {@link iotcs.enterprise.MessageEnumerator#getMessages}
 *
 * @param {string} [deviceId] - The ID of the device for which the callback is called when new
 *        messages arrives. If this is null the callback will be called when messages for any device
 *        arrives.
 * @param {string} [messageType] - The type of the messages that the listener listens to.  The types
 *        are described in the getMessages method. If this is <code>null</code> the callback will be
 *        called for all message types.
 * @param {function} callback - The callback function that will be called when a new message from
 *        the associated device is received.
 */
lib.enterprise.MessageEnumerator.prototype.setListener = function (deviceId, messageType, callback) {

    if (deviceId && (typeof deviceId === 'function')) {
        callback = deviceId;
        deviceId = null;
    } else if (messageType && (typeof messageType === 'function')) {
        callback = messageType;
        messageType = null;
    }

    _optionalArg(messageType, 'string');
    _optionalArg(deviceId, 'string');
    _mandatoryArg(callback, 'function');

    if (messageType && this._message.types.indexOf(messageType) === -1) {
        lib.error('invalid parameter');
        return;
    }

    if (!deviceId) {
        deviceId = this._message.allKey;
    }

    if (!messageType) {
        messageType = this._message.allKey;
    }

    if (!this._message.callbacks[messageType]) {
        this._message.callbacks[messageType] = {};
    }
    this._message.callbacks[messageType][deviceId] = callback;
    var self = this;
    _addMessageMonitor(self, messageType);
};

/**
 * The library will no longer monitor messages for the specified device and/or message type.
 *
 * @function unsetListener
 * @memberof iotcs.enterprise.MessageEnumerator.prototype
 * @see {@link iotcs.enterprise.MessageEnumerator#getMessages}
 *
 * @param {string} [deviceId] - The ID of the device for which the monitoring of messages will be
 *        stopped.
 * @param {string} [messageType] - The type of messages for which the monitoring will be stopped.
 *        The types are described in the getMessages method.
 */
lib.enterprise.MessageEnumerator.prototype.unsetListener = function (deviceId, messageType) {
    _optionalArg(deviceId, 'string');
    _optionalArg(messageType, 'string');

    if (messageType && this._message.types.indexOf(messageType) === -1) {
        lib.error('invalid parameter');
        return;
    }

    if (!deviceId) {
        deviceId = this._message.allKey;
    }

    if (!messageType) {
        messageType = this._message.allKey;
    }

    if (messageType in this._message.callbacks) {
        if (deviceId in this._message.callbacks[messageType]) {
            delete this._message.callbacks[messageType][deviceId];
        }
        if (Object.keys(this._message.callbacks[messageType]).length === 0) {
            delete this._message.callbacks[messageType];
            _removeMessageMonitor(this, messageType);
        }
    }
};

//////////////////////////////////////////////////////////////////////////////

/**ignore*/
function _addMessageMonitor(enumerator, messageType) {
    if (messageType === enumerator._message.allKey) {
        enumerator._message.types.forEach(function(type){
            if (!enumerator._message.monitors[type]) {
                enumerator._message.monitors[type] = new $impl.Monitor(function (){
                    _messagesMonitor(enumerator, type);
                });
            }
            if (enumerator._message.monitors[type] && !enumerator._message.monitors[type].running) {
                enumerator._message.lastTime[type] = Date.now();
                enumerator._message.inProgress[type] = false;
                enumerator._message.monitors[type].start();
            }
        });
    } else {
        if (!enumerator._message.monitors[messageType]) {
            enumerator._message.monitors[messageType] = new $impl.Monitor(function (){
                _messagesMonitor(enumerator, messageType);
            });
        }
        if (enumerator._message.monitors[messageType] && !enumerator._message.monitors[messageType].running) {
            enumerator._message.lastTime[messageType] = Date.now();
            enumerator._message.inProgress[messageType] = false;
            enumerator._message.monitors[messageType].start();
        }
    }
}

/**ignore*/
function _removeMessageMonitor(enumerator, messageType) {
    if (messageType === enumerator._message.allKey) {
        enumerator._message.types.forEach(function(type){
            if (enumerator._message.monitors[type]
                && enumerator._message.monitors[type].running
                && !(type in enumerator._message.callbacks)) {
                enumerator._message.monitors[type].stop();
            }
        });
    } else {
        if (enumerator._message.monitors[messageType]
            && enumerator._message.monitors[messageType].running
            && !(messageType in enumerator._message.callbacks)) {
            enumerator._message.monitors[messageType].stop();
        }
    }
}

/**ignore*/
function _messagesMonitor(enumerator, messageType) {

    if (enumerator._message.inProgress[messageType]) {
        return;
    }

    enumerator._message.inProgress[messageType] = true;

    var pageable = enumerator.getMessages(null, messageType, false, enumerator._message.lastTime[messageType], null);
    var hasMore = false;
    pageable.page('first', enumerator._message.maxLimit).then(function (response) {
        _handleMessagesResponse(enumerator, response, messageType);
        hasMore = response.hasMore;
        var nextCheck = function () {
            pageable.page('next').then(function (response) {
                _handleMessagesResponse(enumerator, response, messageType);
                hasMore = response.hasMore;
                if (hasMore) {
                    nextCheck();
                } else {
                    enumerator._message.inProgress[messageType] = false;
                }
            }, function (error) {lib.createError('invalid response on message monitoring');});
        };
        if (hasMore) {
            nextCheck();
        } else {
            enumerator._message.inProgress[messageType] = false;
        }
    }, function (error) {lib.createError('invalid response on message monitoring');} );
}

/**ignore*/
function _handleMessagesResponse(enumerator, response, messageType){
    if (response
        && (response.items)
        && (Array.isArray(response.items))
        && (response.items.length > 0)) {
        for (var i = 0; i < response.items.length; i++ ){
            if (response.items[i].receivedTime && (response.items[i].receivedTime === enumerator._message.lastTime[messageType])) {
                continue;
            }
            var key2 = response.items[i].source;
            var key1 = response.items[i].type;
            if (enumerator._message.callbacks[key1] && enumerator._message.callbacks[key1][key2]) {
                enumerator._message.callbacks[key1][key2]([response.items[i]]);
            }
            key2 = enumerator._message.allKey;
            key1 = response.items[i].type;
            if (enumerator._message.callbacks[key1] && enumerator._message.callbacks[key1][key2]) {
                enumerator._message.callbacks[key1][key2]([response.items[i]]);
            }
            key2 = response.items[i].source;
            key1 = enumerator._message.allKey;
            if (enumerator._message.callbacks[key1] && enumerator._message.callbacks[key1][key2]) {
                enumerator._message.callbacks[key1][key2]([response.items[i]]);
            }
            key2 = enumerator._message.allKey;
            key1 = enumerator._message.allKey;
            if (enumerator._message.callbacks[key1] && enumerator._message.callbacks[key1][key2]) {
                enumerator._message.callbacks[key1][key2]([response.items[i]]);
            }
        }
        if (!response.hasMore) {
            if ((response.items.length > 0) && response.items[response.items.length-1].receivedTime) {
                enumerator._message.lastTime[messageType] = response.items[response.items.length - 1].receivedTime;
            } else {
                enumerator._message.lastTime[messageType] = enumerator._message.lastTime[messageType] + 1;
            }
        }
    }
}

