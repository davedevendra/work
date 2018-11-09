/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * This sample presents a simple sensor as a directly connected device
 * to the IoT server. The sample is single threaded and messages are
 * created by the sample and delivered to the server via the
 * {@code DirectlyConnectedDevice} API.  The simple sensor is polled every 3 seconds.
 * <p>
 *     The sample runs in two modes: with policies or without policies. By
 *     default, the sample runs without policies. To enable the use of
 *     policies, set the property {@code com_oracle_iot_sample_use_policy}.
 *  <p>
 *      When running with policies, the device policies must be uploaded to
 *      the server. The sample reads the sensor data, creates a data message,
 *      and calls the {@code DirectlyConnectedDevice#offer} method,
 *      which applies the policies. Data and alert messages are sent
 *      to the server depending on the configuration of the device policies.
 *  <p>
 *      When running without policies, the sample reads the sensor data,
 *      creates data and alert messages, and calls the
 *      {@code DirectlyConnectedDevice#send} method, which results in a
 *      data message being sent to the server. The sample itself must
 *      generate alerts if the value from the sensor exceeds
 *      the maximum threshold of the sensor's data model.
 *  <p>
 * The client is a directly connected device using the advanced API.
 *
 * It uses the MessageDispatcher utility to send messages and to handle
 * resource requests from the server.
 *
 * The simple sensor is polled every 3 seconds and a data message is
 * sent to the server and an alert message if the when alert condition is met.
 *
 * The client is a directly connected device using the advanced API.
 */

dcl = require("device-library.node");
dcl = dcl({debug: true});

/** Required */
var trustedAssetsStoreFile = (process.argv[2]);
/** Required */
var trustedAssetsStorePassword = (process.argv[3]);
/** HumiditySensor polling interval before sending next readings. */
var sensorPollingInterval = (process.argv[4]);
var useLongPolling = (process.argv[5]);


if (!sensorPollingInterval) {
    sensorPollingInterval = 3000;
}

/**
 * This sample can be used with policies, or without policies. By default, the sample does not use
 * policies. Set the 'com_oracle_iot_sample_use_policy' environment variable to 'true' (without
 * quotes) to use policies.
 */
var usePolicy = (process.env['com_oracle_iot_sample_use_policy'] || null);

/**
 * Returns the HTTP method used in the supplied request message.  Returns {@code null} if there is a problem determining
 * the method.
 *
 * @param requestMessage an HTTP request message.
 * @returns the HTTP method (e.g. GET) used in the supplied request message, or {@code null}.
 * @private
 */
function _getMethodForRequestMessage(requestMessage){
    var method = null;

    if (requestMessage.payload && requestMessage.payload.method) {
        method = requestMessage.payload.method.toUpperCase();
    }

    if (requestMessage.payload.headers &&
        Array.isArray(requestMessage.payload.headers['x-http-method-override']) &&
        (requestMessage.payload.headers['x-http-method-override'].length > 0))
    {
        method = requestMessage.payload.headers['x-http-method-override'][0].toUpperCase();
    }

    return method;
}

/**
 * Starts the humidity sensor.  It will generate humidity sensor data and send the data to the IoT CS.
 *
 * @param device a dcl.device.util.DirectlyConnectedDevice.
 */
function startHumidity(device) {
    var sensor = {
        humidity: 0,
        maxThreshold: 100
    };

    /**
     * Callback to the send function.
     *
     * @param messages the messages to send to the IoT CS.
     * @param error the resulting error if there is any.
     */
    var handleSend = function (messages, error) {
        if (error) {
            console.log('-----------------ERROR ON SENDING MESSAGES------------------');
            console.log(error.message);
            console.log('------------------------------------------------------------');
        }
    };

    /**
     * Generates and sends humidity sensor "readings" to the IoT CS.  Also sends alerts if the humidity reading is above
     * the max humidity threshold.
     */
    var sendWithPolicy = function () {
        sensor.humidity = Math.floor(Math.random() * 100);
        var message = new dcl.message.Message();

        message
            .type(dcl.message.Message.Type.DATA)
            .source(device.getEndpointId())
            .format('urn:com:oracle:iot:device:humidity_sensor' + ":attributes");

        message.dataItem('humidity', sensor.humidity);
        device.offer([message]);

        console.log('Sent humidity DATA: '+sensor.humidity);
    };

    /**
     * Generates and sends humidity sensor "readings" to the IoT CS.  Also sends alerts if the humidity reading is above
     * the max humidity threshold.
     */
    var sendWithoutPolicy = function () {
        sensor.humidity = Math.floor(Math.random() * 100);

        // Send an alert if the humidity value is above the max threshold.
        if (sensor.humidity > sensor.maxThreshold) {
            var message =
                dcl.message.Message.AlertMessage.buildAlertMessage('urn:com:oracle:iot:device:humidity_sensor:too_humid',
                'Sample alert when humidity reaches the maximum humidity threshold',
                dcl.message.Message.AlertMessage.Severity.SIGNIFICANT);

            message.source(device.getEndpointId());
            message.dataItem('humidity', sensor.humidity);
            device.send([message], handleSend);
            console.log('Sent humidity ALERT: '+sensor.humidity);
        }

        var message = new dcl.message.Message();

        message
            .type(dcl.message.Message.Type.DATA)
            .source(device.getEndpointId())
            .format('urn:com:oracle:iot:device:humidity_sensor' + ":attributes");

        message.dataItem('humidity', sensor.humidity);
        message.dataItem('maxThreshold', sensor.maxThreshold);
        device.send([message], handleSend);
        console.log('Sent humidity DATA: '+sensor.humidity);
    };

    if (usePolicy && (usePolicy === 'true')) {
        console.log('Using device policies.');
        // Sets the interval to call the send function.
        setInterval(sendWithPolicy, sensorPollingInterval);
    } else {
        // Sets the interval to call the send function.
        setInterval(sendWithoutPolicy, sensorPollingInterval);
    }

    /**
     * Handles incoming requests.  For example, if an enterprise client requests the max threshold to change, this
     * handler will handle it.
     *
     * @param requestMessage an HTTP request message.
     * @returns {iotcs.message.Message} a response message.
     */
    var requestHandler = function (requestMessage) {
        var method = _getMethodForRequestMessage(requestMessage);

        if (!method || (method !== 'PUT')) {
            return dcl.message.Message.buildResponseMessage(requestMessage, 405, {}, 'Method Not Allowed', '');
        }

        var data = null;

        try {
            data = JSON.parse(dcl.$port.util.atob(requestMessage.payload.body));
        } catch (e) {
            return dcl.message.Message.buildResponseMessage(requestMessage, 400, {}, 'Bad Request', '');
        }

        if (!data ||
            (typeof data.value !== 'number') ||
            (data.value % 1 !== 0) ||
            (data.value < 60) ||
            (data.value > 100) )
        {
            return dcl.message.Message.buildResponseMessage(requestMessage, 400, {}, 'Bad Request', '');
        }

        console.log('received UPDATE REQUEST maxThreshold:' + data.value);
        sensor.maxThreshold = data.value;
        return dcl.message.Message.buildResponseMessage(requestMessage, 200, {}, 'OK', '');
    };

    var receiveMessage = null;

    /**
     * Function to handle incoming messages.  This simply logs the message to output and calls the request handler.
     *
     * @param message the received message.
     */
    receiveMessage = function (message) {
        if (message) {
            console.log('-------------------RECEIVED MESSAGE-------------------------');
            console.log(JSON.stringify(message, null, 4));
            console.log('------------------------------------------------------------');
            device.send([requestHandler(message)], handleSend);
        }

        device.receive(receiveMessage);
    };

    device.receive(receiveMessage);
}

var dcd = new dcl.device.util.DirectlyConnectedDevice(trustedAssetsStoreFile, trustedAssetsStorePassword);

if (dcd.isActivated()) {
    startHumidity(dcd);
} else {
    dcd.activate(['urn:com:oracle:iot:device:humidity_sensor'], function (device, error) {
        if (error) {
            console.log('-----------------ERROR ON ACTIVATION------------------------');
            console.log(error.message);
            console.log('------------------------------------------------------------');
            return;
        }

        dcd = device;
        console.log(dcd.isActivated());

        if (dcd.isActivated()) {
            startHumidity(dcd);
        }
    });
}
