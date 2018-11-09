/**
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

$impl.mqtt = $impl.mqtt || {};

function _addArrayCallback(array, callback) {
    if (Array.isArray(array)
        && (typeof callback === 'function')) {
        array.push(callback);
    }
}

function _callArrayCallback(array, messages, error) {
    if (Array.isArray(array)
        && (array.length > 0)
        && (typeof array[0] === 'function')) {
        array.splice(0, 1)[0](messages, error);
    }
}

$impl.mqtt.MqttController = function (tam, topicsGenerator) {

    this.callbacks = [];
    this.apiHandlers = {};
    this.errorHandlers = {};
    this.staticApiHandlers = {};
    this.topicsGenerator = topicsGenerator;
    this.tam = tam;
    this.connected = false;

    var self = this;

    this.disconnectHandler = function () {
        self.client = null;
        self.connected = false;
    };

    this.messageHandler = function (topic, message) {
        var response_json = null;
        try {
            response_json = JSON.parse(message);
        } catch (e) {

        }
        if (!response_json || (typeof response_json !== 'object')) {
            if (self.staticApiHandlers[topic]) {
                self.staticApiHandlers[topic](null, new Error(message));
            }
            if (self.apiHandlers[topic]) {
                _callArrayCallback(self.apiHandlers[topic], null, new Error(message));
            }
            else if (self.errorHandlers[topic] && self.apiHandlers[self.errorHandlers[topic]]) {
                _callArrayCallback(self.apiHandlers[self.errorHandlers[topic]], null, new Error(message));
            }
            return;
        }
        if (self.staticApiHandlers[topic]) {
            self.staticApiHandlers[topic](response_json);
        }
        if (self.apiHandlers[topic]) {
            _callArrayCallback(self.apiHandlers[topic], response_json);
        }
        else if (self.errorHandlers[topic] && self.apiHandlers[self.errorHandlers[topic]]) {
            _callArrayCallback(self.apiHandlers[self.errorHandlers[topic]], null, new Error(message));
        }
    };

    this.connectHandler = function (client, error) {
        if (!client || error) {
            for (var topic in self.apiHandlers) {
                _callArrayCallback(self.apiHandlers[topic], null, error);
            }
            _callArrayCallback(self.callbacks, null, error);
            return;
        }

        var topicObjects = self.topicsGenerator();

        if (Array.isArray(topicObjects) && (topicObjects.length > 0)) {

            var topics = [];
            topicObjects.forEach(function (topicObject) {
                if (topicObject.responseHandler) {
                    topics.push(topicObject.responseHandler);
                }
                if (topicObject.errorHandler) {
                    self.errorHandlers[topicObject.errorHandler] = topicObject.responseHandler;
                    topics.push(topicObject.errorHandler);
                }
            });

            $port.mqtt.subscribe(client, topics, function (error) {
                if (error) {
                    var err = lib.createError('unable to subscribe', error);
                    for (var topic in self.apiHandlers) {
                        _callArrayCallback(self.apiHandlers[topic], null, err);
                    }
                    for (var topic1 in self.staticApiHandlers) {
                        self.staticApiHandlers[topic1](null, err);
                    }
                    _callArrayCallback(self.callbacks, null, err);
                    return;
                }
                self.client = client;
                self.connected = true;
                _callArrayCallback(self.callbacks, self);
            });
        } else {
            self.client = client;
            self.connected = true;
            _callArrayCallback(self.callbacks, self);
        }
    };

};

$impl.mqtt.MqttController.prototype.connect = function(callback) {
    if (callback) {
        _addArrayCallback(this.callbacks, callback);
    }
    $port.mqtt.initAndReconnect(this.tam, this.connectHandler, this.disconnectHandler, this.messageHandler);
};

$impl.mqtt.MqttController.prototype.disconnect = function(callback) {
    $port.mqtt.close(this.client, callback);
};

$impl.mqtt.MqttController.prototype.isConnected = function() {
    if (!this.client) {
        return false;
    }
    return this.connected;
};

$impl.mqtt.MqttController.prototype.register = function (topic, callback){
    if (callback) {
        this.staticApiHandlers[topic] = callback;
    }
};

$impl.mqtt.MqttController.prototype.req = function (topic, payload, expect, callback) {

    var self = this;

    var request = function(controller, error) {

        if (!controller || error) {
            callback(null, error);
            return;
        }

        if (expect && callback && (typeof callback === 'function')) {
            var tempCallback = function (message, error) {
                if (!message || error) {
                    callback(null, error);
                    return;
                }
                callback(message);
            };
            if (!self.apiHandlers[expect]) {
                self.apiHandlers[expect] = [];
            }
            _addArrayCallback(self.apiHandlers[expect], tempCallback);
        }

        $port.mqtt.publish(self.client, topic, payload, (callback ? true : false), function (error) {
            if (error && callback) {
                callback(null, error);
                return;
            }
            if (!expect && callback) {
                callback(payload);
            }
        });
    };

    if (!this.isConnected()) {
        this.connect(request);
    } else {
        request(this);
    }
};
