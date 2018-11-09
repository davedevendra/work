/**
* Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
*
* This software is dual-licensed to you under the MIT License (MIT) and
* the Universal Permissive License (UPL). See the LICENSE file in the root
* directory for license terms. You may choose either license, or both.
*
*/

/*
* This sample presents a simple sensor as virtual device to the IoT server.
*
* The simple sensor is polled every half second and the virtual device is updated.
* An alert is generated when the value from the sensor exceeds the maximum threshold
* of the sensor's data model.
*/

dcl = require("device-library.node");
dcl = dcl({debug: true});

var EOL = require('os').EOL;
var humiditySensorDeviceModel;

// Arguments
var trustedAssetsStoreFile = (process.argv[2]);
var trustedAssetsStorePassword = (process.argv[3]);

/**
 * Sensor polling interval between sending "readings".  Could be configured using
 * {@code com.oracle.iot.sample.sensor_polling_interval} property.
 */
var sensorPollingInterval = 3000;

/**
 * This sample can be used with policies, or without policies. By default, the sample does not use
 * policies. Set the 'com_oracle_iot_sample_use_policy' environment variable to 'true' (without
 * quotes) to use policies.
 */
var usePolicy = (process.env['com_oracle_iot_sample_use_policy'] || null);

function showUsage() {
    console.log(EOL + "Usage:");
    console.log(" run-device-node-sample.[sh,bat] DirectlyConnectedDeviceSample.js <trusted assets file> <trusted assets password>" + EOL);
    console.log("To run the sample using device policies, supply the true parameter at the end:")
    console.log(" run-device-node-sample.[sh,bat] DirectlyConnectedDeviceSample.js <trusted assets file> <trusted assets password> <optional_true>" + EOL);
}

/**
* Starts the humidity "device".
*
* @param {dcl.device.DirectlyConnectedDevice} device a humidity sensor device.
* @param {string} endpointId the endpoint ID of the device.
*/
function startVirtualHumidity(device, endpointId) {
    // Create a virtual device implementing the device model
    dcd.getDeviceModel("urn:com:oracle:iot:device:humidity_sensor", function(deviceModel) {
        var virtualDevice = dcd.createVirtualDevice(endpointId, humiditySensorDeviceModel);

        var sensor = {
            humidity: 0
        };

        var alertOnErrorCallback = function(error) {
            console.log('------------------Error sending alert!---------------------');
            console.log(error);
            console.log('-----------------------------------------------------------');
        };

        /*
        * Monitor the virtual device for requested attribute changes.
        *
        * Since there is only one attribute, maxThreshold, this could have done with using an attribute specific on change
        * handler.
        *
        * @param tuples {?} ?
        */
        virtualDevice.onChange = function (tuples) {
            tuples.forEach( function (tuple) {
                var show = {
                    name: tuple.attribute.id,
                    lastUpdate: tuple.attribute.lastUpdate,
                    oldValue: tuple.oldValue,
                    newValue: tuple.newValue
                };

                console.log('------------------Humidity sensor - on change---------------------');
                console.log(JSON.stringify(show, null, 4));
                console.log('------------------------------------------------------------------');
                sensor[tuple.attribute.id] = tuple.newValue;
            });

            if ((virtualDevice.maxThreshold.value !== null) && (sensor.humidity > virtualDevice.maxThreshold.value)) {
                var alert = virtualDevice.createAlert('urn:com:oracle:iot:device:humidity_sensor:too_humid');
                alert.fields.humidity = sensor.humidity;
                alert.onError = alertOnErrorCallback;
                alert.raise();
                console.log("humidity ALERT: " + sensor.humidity + " higher than max " + virtualDevice.maxThreshold.value);
            }
        };

        /*
        * Monitor the virtual device for requested attribute changes and errors.
        *
        * Since there is only one attribute, maxThreshold, this could have
        * done with using an attribute specific on change handler.
        *
        * @param tuples
        */
        virtualDevice.onError = function (tuple) {
            var show = {
                newValues: tuple.newValues,
                tryValues: tuple.tryValues,
                errorResponse: tupple.errorResponse
            };

            console.log('------------------Humidity sensor - on error---------------------');
            console.log(JSON.stringify(show,null,4));
            console.log('-----------------------------------------------------------------');

            for (var key in tuple.newValues) {
                sensor[key] = tuple.newValues[key];
            }
        };

        var processSensorData;

        if (usePolicy && (usePolicy === 'true')) {
            console.log('Using device policies.');
            /**
            * With policies.
            *
            * When running with policies, we only need to "offer" the
            * humidity value. Policies can be set on the server to
            * generate alerts, and filter bad data - which had to be
            * handled by the sample code when running without policies.
            * Compare this implementation of processSensorData to that
            * of the without policies one.
            */
            processSensorData = function () {
                // min threshold = 0; max threshold = 100
                sensor.humidity = Math.floor(Math.random() * 100);

                var consoleMsg = EOL + new Date() + " : " + virtualDevice.getEndpointId()
                + " : Offer : \"humidity\"=" + sensor.humidity;

                console.log(consoleMsg);

                // Convert to Object.
                virtualDevice.offer("humidity", sensor.humidity);
            };
        } else {
            /**
            * Without policies.
            *
            * When running the sample without policies, the sample must do more work.
            * The sample has to generate alerts if the sensor value exceeds the
            * threshold. This logic can be handled by the client library with the
            * right set of policies in place. Compare this method to that of the
            * with policies one.
            */
            processSensorData = function() {
                // min threshold = 0; max threshold = 100
                sensor.humidity = Math.floor(Math.random() * 100);

                var consoleMsg = EOL + new Date() + " : " + virtualDevice.getEndpointId()
                    + " : Offer : \"humidity\"=" + sensor.humidity;

                console.log(consoleMsg);

                if ((virtualDevice.maxThreshold.value !== null) &&
                    (sensor.humidity > virtualDevice.maxThreshold.value))
                {
                    var alert = virtualDevice.createAlert('urn:com:oracle:iot:device:humidity_sensor:too_humid');
                    alert.fields.humidity = sensor.humidity;
                    alert.onError = alertOnErrorCallback;
                    alert.raise();

                    console.log("humidity ALERT: " + sensor.humidity + " higher than max " +
                        virtualDevice.maxThreshold.value);
                }

                virtualDevice.update(sensor);
            };
        }

        // Set the interval for the withoutDevicePolicies function to be invoked.
        setInterval(processSensorData, sensorPollingInterval);
    });
}

/**
* Gets the device model for the device and sets it to the global variable.
*
* @param {dcl.device.DirectlyConnectedDevice} device a humidity sensor device.
*/
function getHumiditySensorDeviceModel(device) {
    device.getDeviceModel('urn:com:oracle:iot:device:humidity_sensor', function (response, error) {
        if (error) {
            console.log('-------------Error getting humidity sensor device model-------------');
            console.log(error.message);
            console.log('--------------------------------------------------------------------');
            return;
        }

        console.log('-----------------Humidity sensor device model----------------------');
        console.log(JSON.stringify(response,null,4));
        console.log('-------------------------------------------------------------------');
        humiditySensorDeviceModel = response;
        startVirtualHumidity(dcd, dcd.getEndpointId());
    });
}

// Start of main code.
try {
    // Initialize the device client
    var dcd = new dcl.device.DirectlyConnectedDevice(trustedAssetsStoreFile, trustedAssetsStorePassword);
} catch (err) {
    console.error(err);
    showUsage();
    process.exit(1);
}

// Activate device if it's not already activated.
if (!dcd.isActivated()) {
    dcd.activate(['urn:com:oracle:iot:device:humidity_sensor'], function (device, error) {
        if (error) {
            console.log('-----------------Error activating device------------------------');
            console.log(error.message);
            console.log('----------------------------------------------------------------');
            return;
        }

        dcd = device;

        // If the device is activated, start generating data and sending it to the IoT CS.
        if (dcd.isActivated()) {
            console.log('Device is activated: ' + dcd.isActivated());
            getHumiditySensorDeviceModel(dcd);
        } else {
            console.log('-------------Error activating humidity sensor, exiting.-------------');
            process.exit(1);
        }
    });
} else {
    console.log('Device is activated: ' + dcd.isActivated());
    getHumiditySensorDeviceModel(dcd);
}

