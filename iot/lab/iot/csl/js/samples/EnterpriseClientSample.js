/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

ecl = require("enterprise-library.node");
ecl = ecl({debug: true});

var EOL = require('os').EOL;
var fs = require('fs');
var path = require("path");
var client = null;
var deviceIds = [];

models = {
    'urn:com:oracle:iot:device:humidity_sensor': null,
    'urn:com:oracle:iot:device:temperature_sensor': null,
    'urn:com:oracle:iot:device:motion_activated_camera': null
};

var cliParams = process.argv.slice(2);

var trustFile = cliParams[0];
var trustPass = cliParams[1];

var type = 0;

function showUsage(){
    console.log("Usage:");
    console.log("node " + process.argv[1] + " <trusted assets file> <trusted assets password>" + EOL);
    console.log(" devices");
    console.log("  List all devices or all device-applications." + EOL);
    console.log(" <deviceId>[,<deviceId>]");
    console.log("  Monitor virtual device(s) & print its measurements every");
    console.log("  time it changes, until return key is pressed" + EOL);
    console.log(" <deviceId> [reset|on|off]");
    console.log("  Reset the thermometer or turn on thermometer or ");
    console.log("  turn off the thermometer." + EOL);
    console.log(" <deviceId> <maxThreshold>");
    console.log("  Set the maximum threshold." + EOL);
    console.log(" <deviceId> <maxThreshold> <minThreshold>");
    console.log("  Set the maximum and minimum temperature thresholds." + EOL);
    console.log(" <deviceId> record [<duration>]");
    console.log("  Record at least <duration> seconds of video from a motion activated camera." + EOL);
}

switch (cliParams.length) {
    case 3:
        type = 11;
        break;

    case 4:
        switch (cliParams[3]) {
            case 'devices':
                type = 1;
                break;
            case 'reset':
                type = 111;
                break;
            case 'on':
                type = 112;
                break;
            case 'off':
                type = 113;
                break;
            case 'record':
                type = 114;
                break;
            default:
                type = 115;
                break;
        }
        break;

    case 5:
        if (cliParams[3] === 'record') {
            type = 114;
        } else {
            type = 116;
        }
        break;

    default:
        showUsage();
        return;
}

function getSupportedDeviceModel(response) {
    if (Array.isArray(response.items)
        && response.items.length
        && Array.isArray(response.items[0].deviceModels)
        && response.items[0].deviceModels.length) {
        var deviceModelUrn = "";
        for (var index in response.items[0].deviceModels) {
            if (Object.keys(models).indexOf(response.items[0].deviceModels[index].urn) > -1) {
                deviceModelUrn = response.items[0].deviceModels[index].urn;
                break;
            }
        }
        return deviceModelUrn;
    }
    return "";
}

function syncCallback (event) {
    var virtualDevice = event.getVirtualDevice();
    var storage = event.getSource();
    var eventName = event.getName();
    var consoleMsg = EOL + new Date().toISOString() + " : " + virtualDevice.getEndpointId()
        + " : onSync : " + eventName + " : "+ storage.getURI() + " = \"" + storage.getSyncStatus() + "\"";
    if (storage.getSyncStatus() === ecl.enterprise.StorageObject.SyncStatus.IN_SYNC) {
        consoleMsg += " (" + storage.getLength() + " bytes)";
    }
    consoleMsg += " into " + storage.getOutputPath();
    console.log(consoleMsg);
}

function getDeviceModels(client, callback) {
    client.getDeviceModel('urn:com:oracle:iot:device:humidity_sensor', function (response, error) {
        if (error) {
            console.log('-------------ERROR ON GET HUMIDITY DEVICE MODEL-------------');
            console.log(error.message);
            console.log('------------------------------------------------------------');
            client.close();
        }

        models['urn:com:oracle:iot:device:humidity_sensor'] = response;

        client.getDeviceModel('urn:com:oracle:iot:device:temperature_sensor', function (response, error){
            if (error) {
                console.log('-------------ERROR ON GET TEMPERATURE DEVICE MODEL----------');
                console.log(error.message);
                console.log('------------------------------------------------------------');
                client.close();
            }

            models['urn:com:oracle:iot:device:temperature_sensor'] = response;

            client.getDeviceModel('urn:com:oracle:iot:device:motion_activated_camera', function (response, error) {
                if (error) {
                    console.log('-------------ERROR ON GET MOTION ACTIVATED CAMERA DEVICE MODEL----------');
                    console.log(error.message);
                    console.log('------------------------------------------------------------');
                    client.close();
                }

                models['urn:com:oracle:iot:device:motion_activated_camera'] = response;

                if (callback) {
                    callback();
                }
            });
        });
    });
}

ecl.enterprise.EnterpriseClient.newClient(function (entClient, error) {
    if (error) {
        console.log('-------------ERROR ON CREATING CLIENT-----------------------');
        console.log(error.message);
        console.log('------------------------------------------------------------');
        return;
    }
    client = entClient;

    if (type < 10) {
        var finish = 0;
        var recursive;
        recursive = function (pageable, sensorName, first) {
            pageable.page(first ? 'first' : 'next').then(function (response) {
                if (Array.isArray(response.items)) {
                    response.items.forEach(function (item) {
                        console.log(item.id + " " + sensorName);
                    });
                }
                if (response.hasMore) {
                    recursive(pageable, sensorName, false);
                } else {
                    finish++;
                    if (finish === 2) {
                        client.close();
                    }
                }
            }, function (error) {
                if (error) {
                    console.log('-------------ERROR ON LISTING DEVICES-----------------------');
                    console.log(error.message);
                    console.log('------------------------------------------------------------');
                }
                client.close();
                process.exit(0);
            });
        };
        recursive(client.getActiveDevices('urn:com:oracle:iot:device:humidity_sensor'), "[Humidity Sensor]", true);
        recursive(client.getActiveDevices('urn:com:oracle:iot:device:temperature_sensor'), "[Temperature Sensor]", true);
        recursive(client.getActiveDevices('urn:com:oracle:iot:device:motion_activated_camera'), "[Motion Activated Camera]", true);
    } else {
        deviceIds = cliParams[2].split(',');
        if (type > 100) {
            deviceIds = deviceIds.splice(0, 1);
        }
        getDeviceModels(client, function () {
            deviceIds.forEach(function (deviceId) {
            var filter = new ecl.enterprise.Filter();
            filter = filter.eq('id', deviceId);
            client.getDevices(filter).page('first').then(function (response) {
                var deviceModelUrn;
                if ((deviceModelUrn = getSupportedDeviceModel(response))) {
                    var device = client.createVirtualDevice(response.items[0].id, models[deviceModelUrn]);
                    if (type < 100) {
                        device.onChange = function (tupples) {
                            tupples.forEach(function (tupple) {
                                var show = {
                                    deviceId: tupple.attribute.device.getEndpointId(),
                                    attribute: tupple.attribute.id,
                                    newValue: null
                                };
                                show.newValue = (tupple.newValue instanceof ecl.StorageObject) ?
                                    tupple.newValue.getURI() : tupple.newValue;
                                console.log('---------------------ON CHANGE---------------------------');
                                console.log(JSON.stringify(show, null, 4));
                                console.log('---------------------------------------------------------');

                                if (tupple.newValue instanceof ecl.StorageObject) {
                                    var storage = tupple.newValue;
                                    var dir = path.join(".", "downloads");
                                    if (!fs.existsSync(dir)) {
                                        fs.mkdirSync(dir);
                                    }
                                    storage.setOutputPath(path.join(dir, storage.getName()));
                                    storage.onSync = syncCallback;
                                    storage.sync();
                                }
                            });
                        };
                        device.onAlerts = function (alertsObject) {
                            for (var formatUrn in alertsObject) {
                                alertsObject[formatUrn].forEach(function (object) {
                                    var show = {
                                        alert: formatUrn,
                                        fields: object.fields
                                    };
                                    console.log('---------------------ON ALERT----------------------------');
                                    console.log(JSON.stringify(show, null, 4));
                                    console.log('---------------------------------------------------------');
                                });
                            }
                        };
                    } else {
                        switch (type) {
                            case 111:
                                if (device.reset) {
                                    device.reset.onExecute = function (response) {
                                        console.log('---------------------ON RESET----------------------------');
                                        console.log(JSON.stringify(response, null, 4));
                                        console.log('---------------------------------------------------------');
                                    };
                                    device.call('reset');
                                } else {
                                    console.log('----------------------ERROR ON ACTION--------------------');
                                    console.log('invalid model for reset action');
                                    console.log('---------------------------------------------------------');
                                }
                                break;
                            case 112:
                                if (device.power) {
                                    device.power.onExecute = function (response) {
                                        console.log('---------------------ON POWER ON-------------------------');
                                        console.log(JSON.stringify(response, null, 4));
                                        console.log('---------------------------------------------------------');
                                    };
                                    device.call('power', true);
                                } else {
                                    console.log('----------------------ERROR ON ACTION--------------------');
                                    console.log('invalid model for power action');
                                    console.log('---------------------------------------------------------');
                                }
                                break;
                            case 113:
                                if (device.power) {
                                    device.power.onExecute = function (response) {
                                        console.log('---------------------ON POWER OFF------------------------');
                                        console.log(JSON.stringify(response, null, 4));
                                        console.log('---------------------------------------------------------');
                                        client.close();
                                    };
                                    device.call('power', false);
                                } else {
                                    console.log('----------------------ERROR ON ACTION--------------------');
                                    console.log('invalid model for power action');
                                    console.log('---------------------------------------------------------');
                                    client.close();
                                }
                                break;
                            case 114:
                                // if duration is not set then use minimal duration 1 second
                                var duration = parseInt(cliParams[4]) || 1;
                                if (device.record) {
                                    device['urn:com:oracle:iot:device:motion_activated_camera:recording'].onData = function (data) {
                                        var show = data;
                                        var storage = data[0].fields.video;
                                        show[0].fields.video = storage.getURI();
                                        console.log('---------------------ON RECORDING------------------------');
                                        console.log(JSON.stringify(show, null, 4));
                                        console.log('---------------------------------------------------------');

                                        var dir = path.join(".", "downloads");
                                        if (!fs.existsSync(dir)) {
                                            fs.mkdirSync(dir);
                                        }
                                        storage.setOutputPath(path.join(dir, storage.getName()));
                                        storage.onSync = syncCallback;
                                        storage.sync();
                                    };
                                    device.record.onExecute = function (response) {
                                        console.log('---------------------ON RECORD---------------------------');
                                        console.log(JSON.stringify(response, null, 4));
                                        console.log('---------------------------------------------------------');
                                    };
                                    device.call('record', duration);
                                } else {
                                    console.log('----------------------ERROR ON ACTION--------------------');
                                    console.log('invalid model for record action');
                                    console.log('---------------------------------------------------------');
                                }
                                break;
                            case 115:
                                var max = cliParams[3];

                                if (device.maxThreshold) {
                                    device.maxThreshold.onChange = function (tupple) {
                                        var show = {
                                            deviceId: tupple.attribute.device.getEndpointId(),
                                            attribute: tupple.attribute.id,
                                            newValue: tupple.newValue
                                        };
                                        console.log('---------------------ON CHANGE---------------------------');
                                        console.log(JSON.stringify(show, null, 4));
                                        console.log('---------------------------------------------------------');
                                    };

                                    device.maxThreshold.onError = function (tupple) {
                                        console.log('-----ERROR ON UPDATE ATTRIBUTE MAX THRESHOLD-------------');
                                        console.log(JSON.stringify(tupple.errorResponse, null, 4));
                                        console.log('---------------------------------------------------------');
                                    };

                                    device.update({maxThreshold: parseInt(max)});
                                } else {
                                    console.log('------------ERROR ON UPDATE ATTRIBUTE--------------------');
                                    console.log('invalid model for maxThreshold attribute');
                                    console.log('---------------------------------------------------------');
                                }

                                break;
                            case 116:
                                var max = cliParams[3];
                                var min = cliParams[4];
                                if (device.maxThreshold && device.minThreshold) {
                                    device.maxThreshold.onChange = function (tupple) {
                                        var show = {
                                            deviceId: tupple.attribute.device.getEndpointId(),
                                            attribute: tupple.attribute.id,
                                            newValue: tupple.newValue
                                        };
                                        console.log('---------------------ON CHANGE---------------------------');
                                        console.log(JSON.stringify(show, null, 4));
                                        console.log('---------------------------------------------------------');
                                    };
                                    device.minThreshold.onChange = function (tupple) {
                                        var show = {
                                            deviceId: tupple.attribute.device.getEndpointId(),
                                            attribute: tupple.attribute.id,
                                            newValue: tupple.newValue
                                        };
                                        console.log('---------------------ON CHANGE---------------------------');
                                        console.log(JSON.stringify(show, null, 4));
                                        console.log('---------------------------------------------------------');
                                    };
                                    device.maxThreshold.onError = function (tupple) {
                                        console.log('-----ERROR ON UPDATE ATTRIBUTE MAX THRESHOLD-------------');
                                        console.log(JSON.stringify(tupple.errorResponse, null, 4));
                                        console.log('---------------------------------------------------------');
                                    };
                                    device.minThreshold.onError = function (tupple) {
                                        console.log('-----ERROR ON UPDATE ATTRIBUTE MIN THRESHOLD-------------');
                                        console.log(JSON.stringify(tupple.errorResponse, null, 4));
                                        console.log('---------------------------------------------------------');
                                    };
                                    device.update({maxThreshold: parseInt(max), minThreshold: parseInt(min)});
                                } else {
                                    console.log('------------ERROR ON UPDATE ATTRIBUTE---------------------');
                                    console.log('invalid model for maxThreshold and minThreshold attributes');
                                    console.log('----------------------------------------------------------');
                                }
                                break;
                        }
                    }
                } else {
                    console.log('-------------ERROR ON GETTING DEVICE DATA-------------------');
                    console.log('invalid device or device model on device');
                    console.log('------------------------------------------------------------');
                    client.close();
                    process.exit(0);
                }
            }, function (response, error) {
                if (error) {
                    console.log('-------------ERROR ON GETTING DEVICE DATA-------------------');
                    console.log(error.message);
                    console.log('------------------------------------------------------------');
                }
                client.close();
                process.exit(0);
            });
        });
        console.log("Press Ctr + C to exit...");
        });
    }
}, trustFile, trustPass);
