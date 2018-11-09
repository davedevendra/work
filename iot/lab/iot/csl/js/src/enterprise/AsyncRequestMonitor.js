/**
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/** @ignore */
$impl.AsyncRequestMonitor = function (requestId, callback, internalClientExt) {
    _mandatoryArg(requestId, ['string','number']);
    _mandatoryArg(callback, 'function');
    this.requestId = requestId;
    this.callback = callback;
    this.monitor = null;
    this.startTime = null;
    this.internalClient = internalClientExt;
};

/** @ignore */
$impl.AsyncRequestMonitor.prototype.start = function () {
    var self = this;
    if (!this.monitor) {
        this.monitor = new $impl.Monitor(function () {
            _requestMonitor(self);
        });
    }
    if (!this.monitor.running) {
        this.monitor.start();
        this.startTime = Date.now();
    }
};

/** @ignore */
$impl.AsyncRequestMonitor.prototype.stop = function () {
    if (this.monitor) {
        this.monitor.stop();
    }
    this.startTime = null;
};

/** @ignore */
function _requestMonitor (monitorObject){
    if (monitorObject.startTime
        && (Date.now() > (monitorObject.startTime + lib.oracle.iot.client.controller.asyncRequestTimeout))) {
        monitorObject.stop();
        var response = {
            complete: true,
            id: monitorObject.requestId,
            status: 'TIMEOUT'
        };
        monitorObject.callback(response);
        return;
    }
    $impl.https.bearerReq({
        'method': 'GET',
        'path': $impl.reqroot
        + '/requests/'
        + monitorObject.requestId
    }, '', function (response, error) {
        try {
            if (!response || error) {
                monitorObject.stop();
                monitorObject.callback(response, lib.createError('invalid response',error));
                return;
            }
            if (!(response.status) || (typeof response.complete === 'undefined')) {
                monitorObject.stop();
                monitorObject.callback(response, lib.createError('invalid response type', error));
                return;
            }
            if (response.complete) {
                monitorObject.stop();
                monitorObject.callback(response);
            }
        } catch(e) {
            monitorObject.stop();
            monitorObject.callback(response, lib.createError('error on response',e));
        }
    }, function () {
        _requestMonitor (monitorObject);
    }, monitorObject.internalClient);
}
