/**
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */
$port.mqtt = {};

$port.mqtt.initAndReconnect = function (tam, callback, disconnectCallback, messageHandler) {

    var connectOptions = {};

    var id = (tam.isActivated() ? tam.getEndpointId() : tam.getClientId());

    connectOptions.host = tam.getServerHost();
    connectOptions.port = tam.getServerPort();
    connectOptions.protocol = 'mqtts';
    connectOptions.rejectUnauthorized = true;

    if ((typeof tam.getTrustAnchorCertificates === 'function')
        && Array.isArray(tam.getTrustAnchorCertificates())
        && (tam.getTrustAnchorCertificates().length > 0)) {
        connectOptions.ca = tam.getTrustAnchorCertificates();
    }

    connectOptions.clientId = id;
    connectOptions.username = id;
    connectOptions.password = tam.buildClientAssertion();

    if (!connectOptions.password) {
        callback(null, lib.createError('error on generating oauth signature'));
        return;
    }

    connectOptions.clean = true;
    connectOptions.connectTimeout = 30 * 1000;
    connectOptions.reconnectPeriod = 60 * 1000;

    var client = require('mqtt').connect(connectOptions);

    client.on('error', function (error) {
        callback(null, error);
    });

    client.on('connect', function (connack) {
        callback(client);
    });

    client.on('close', function () {
        disconnectCallback();
    });

    client.on('message', function (topic, message, packet) {
        messageHandler(topic, message);
    });

};

$port.mqtt.subscribe = function (client, topics, callback) {
    client.subscribe(topics, function (err, granted) {
        if (err && (err instanceof Error)) {
            callback(lib.createError('error on topic subscription: ' + topics.toString(), err));
            return;
        }
        callback();
    });
};

$port.mqtt.unsubscribe = function (client, topics) {
    client.unsubscribe(topics);
};

$port.mqtt.publish = function (client, topic, message, waitForResponse, callback) {
    var qos = (waitForResponse ? 1 : 0);
    client.publish(topic, message, {qos: qos, retain: false}, function (err) {
        if (err && (err instanceof Error)) {
            callback(err);
            return;
        }
        callback();
    });
};

$port.mqtt.close = function (client, callback) {
    client.end(true, callback);
};
