/**
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * This sample demonstrates the iotcs.StorageObject and related API.
 *
 * The sample uses the messaging API to simulate a motion activated camera.
 * An image is uploaded to the storage cloud every 5 seconds.
 * The sample also handles the "recording" action.
 */

dcl = require("device-library.node");
dcl = dcl({debug: true});

var fs = require('fs');
var EOL = require('os').EOL;
var path = require("path");

var MOTION_ACTIVATED_CAMERA_MODEL_URN = "urn:com:oracle:iot:device:motion_activated_camera";
var MESSAGE_TIMEOUT = 5;

var storeFile = (process.argv[2]);
var storePassword = (process.argv[3]);

var resourcesFolder = process.argv[1].substring(0, process.argv[1].lastIndexOf(path.sep + "advanced"));
var imagesFolder = path.join(resourcesFolder, "images");
var videosFolder = path.join(resourcesFolder, "videos");

var images = [];
var videos = [];

function showUsage() {
    console.log(EOL + "Usage:");
    console.log(" run-device-node-sample.[sh,bat] " + path.join("advanced", "MotionActivatedCameraSample.js") + " <trusted assets file> <trusted assets password>");
    console.log("Note:");
    console.log(" Images and videos used by sample are expected to be in " + path.join("iotcs", "csl", "js") + " samples folder" + EOL);
}

function getMilliseconds(sec) {
    return sec * 1000;
}

function _getMethodForRequestMessage(requestMessage){
    var method = null;
    if (requestMessage.payload && requestMessage.payload.method) {
        method = requestMessage.payload.method.toUpperCase();
    }
    if (requestMessage.payload.headers &&
        Array.isArray(requestMessage.payload.headers['x-http-method-override']) &&
        (requestMessage.payload.headers['x-http-method-override'].length > 0)) {
        method = requestMessage.payload.headers['x-http-method-override'][0].toUpperCase();
    }
    return method;
}

function startMotionActivatedCamera(device) {
    var imageIndex = 0;
    var messageDispatcher = new dcl.device.util.MessageDispatcher(device);

    var handleSend = function (messages, error) {
        if (error) {
            console.log('-----------------ERROR ON SENDING MESSAGES------------------');
            console.log(error.message);
            console.log('------------------------------------------------------------');
        }
    };
    var uploadCallback = function (storage, error) {
        if (error) {
            console.log('-----------------ERROR ON UPLOADING IMAGE-------------------');
            console.log(error.message);
            console.log('------------------------------------------------------------');
            return;
        }
        var message = new dcl.message.Message();
        message.type(dcl.message.Message.Type.DATA)
            .source(device.getEndpointId())
            .format(MOTION_ACTIVATED_CAMERA_MODEL_URN + ":attributes");
        message.dataItem("image", storage);
        message.dataItem("imageTime", new Date());
        device.send([message], handleSend);

        console.log(EOL + new Date() + " : " + device.getEndpointId() + " : DATA : "
            + "\"image\"=" + storage.getURI()
            + " (" + storage.getLength() + " bytes) from " + storage.getInputStream().path);
    };
    var sendImage = function () {
        var image = images[imageIndex++ % images.length];
        var storage = device.createStorageObject(
            "motion_activated_camera_" + device.getEndpointId() + "_" + image, "image/jpeg");
        storage.setInputStream(fs.createReadStream(path.join(imagesFolder, image)));
        storage.sync(uploadCallback);
    };

    var actionsHandler = function (requestMessage) {
        var startRecordingTime;
        var duration;
        var recordingCallback = function (storage, error) {
            if (error) {
                console.log('-----------------ERROR ON RECORDING VIDEO-------------------');
                console.log(error);
                console.log('------------------------------------------------------------');
                // send response message for server request with 400 status
                messageDispatcher.queue(dcl.message.Message.buildResponseMessage(requestMessage, 400, {}, 'OK', ''));
                return;
            }
            // send response message for server request with 200 status
            messageDispatcher.queue(dcl.message.Message.buildResponseMessage(requestMessage, 200, {}, 'OK', ''));

            var message = new dcl.message.Message();
            message.type(dcl.message.Message.Type.DATA)
                .source(device.getEndpointId())
                .format(MOTION_ACTIVATED_CAMERA_MODEL_URN + ":recording");
            message.dataItem('video', storage);
            message.dataItem('startTime', startRecordingTime);
            message.dataItem('duration', duration);
            device.send([message], handleSend);

            console.log(EOL + new Date() + " : " + device.getEndpointId() + " : DATA : "
                + " \"duration\"=" + duration + " : \"video\"=" + storage.getURI()
                + " (" + storage.getLength() + " bytes) from " + storage.getInputStream().path);
        };
        var sendVideo = function () {
            // assumes videos.length > 0 and videos are 15 second increments.
            var video = videos[Math.min(duration/15 - 1, videos.length - 1)];
            var storage = device.createStorageObject(
                "motion_activated_camera_" + device.getEndpointId() + "_" + video, "video/mp4");
            storage.setInputStream(fs.createReadStream(path.join(videosFolder, video)));
            storage.sync(recordingCallback);
        };

        var method = _getMethodForRequestMessage(requestMessage);
        if (!method || (method !== 'POST')) {
            return dcl.message.Message.buildResponseMessage(requestMessage, 405, {}, 'Method Not Allowed', '');
        }
        var urlAction = requestMessage.payload.url.substring(requestMessage.payload.url.lastIndexOf('/') + 1);
        if (urlAction === 'record') {
            var data = null;
            try {
                data = JSON.parse(dcl.$port.util.atob(requestMessage.payload.body));
            } catch (e) {
                return dcl.message.Message.buildResponseMessage(requestMessage, 400, {}, 'Bad Request', '');
            }
            if (!data || (typeof data.value !== 'number') || (data.value <= 0)) {
                return dcl.message.Message.buildResponseMessage(requestMessage, 400, {}, 'Bad Request', '');
            }

            // round to nearest increment of 15
            duration = Math.ceil(data.value / 15) * 15;
            startRecordingTime = new Date();
            console.log(EOL + new Date() + " : " + device.getEndpointId() + " : Call : record : " + data.value);
            // Simulate the time it takes to record the video by timeout
            setTimeout(sendVideo, getMilliseconds(duration));
            // real response will be sent later in recordingCallback
            return dcl.message.Message.buildResponseWaitMessage();
        }
    };

    messageDispatcher.getRequestDispatcher().registerRequestHandler(device.getEndpointId(),
        'deviceModels/' + MOTION_ACTIVATED_CAMERA_MODEL_URN + '/actions/record', actionsHandler);

    console.log(EOL + "Press Ctr + C to exit...");
    setInterval(sendImage, getMilliseconds(MESSAGE_TIMEOUT));
}

try {
    var dcd = new dcl.device.util.DirectlyConnectedDevice(storeFile, storePassword);

    fs.readdirSync(imagesFolder).forEach(function(file) {
        images.push(file);
    });
    fs.readdirSync(videosFolder).forEach(function(file) {
        videos.push(file);
    });
} catch (err) {
    console.log(err);
    showUsage();
    process.exit(1);
}

if (dcd.isActivated()) {
    startMotionActivatedCamera(dcd);
} else {
    dcd.activate([MOTION_ACTIVATED_CAMERA_MODEL_URN], function (device, error) {
        if (error) {
            console.log('-----------------ERROR ON ACTIVATION------------------------');
            console.log(error.message);
            console.log('------------------------------------------------------------');
            process.exit(1);
        }
        dcd = device;
        if (dcd.isActivated()) {
            startMotionActivatedCamera(dcd);
        }
    });
}

