/**
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**@ignore*/
$impl.TestConnectivity = function (messageDispatcher) {
    this.count = 0;
    this.currentCount = 0;
    this.size = 0;
    this.interval = 0;

    this.messageDispatcher = messageDispatcher;
    this.startPooling = null;

    var self = this;

    this.monitor = new $impl.Monitor( function () {
        var currentTime = Date.now();
        if (currentTime >= (self.startPooling + self.interval)) {

            if (messageDispatcher._.dcd.isActivated()) {

                var message = new lib.message.Message();
                message
                    .type(lib.message.Message.Type.DATA)
                    .source(messageDispatcher._.dcd.getEndpointId())
                    .format("urn:oracle:iot:dcd:capability:diagnostics:test_message")
                    .dataItem("count", self.currentCount)
                    .dataItem("payload", _strRepeat('*', self.size))
                    .priority(lib.message.Message.Priority.LOWEST);

                self.messageDispatcher.queue(message);
                self.currentCount = self.currentCount + 1;

            }

            self.startPooling = currentTime;

            if (self.currentCount === self.count) {
                self.monitor.stop();
                self.count = 0;
                self.currentCount = 0;
                self.size = 0;
                self.interval = 0;
            }
        }
    });

};

/**@ignore*/
$impl.TestConnectivity.prototype.startHandler = function (requestMessage) {
    var method = _getMethodForRequestMessage(requestMessage);
    if (!method || (method !== 'PUT')) {
        return lib.message.Message.buildResponseMessage(requestMessage, 405, {}, 'Method Not Allowed', '');
    }
    var data = null;
    try {
        data = JSON.parse($port.util.atob(requestMessage.payload.body));
    } catch (e) {
        return lib.message.Message.buildResponseMessage(requestMessage, 400, {}, 'Bad Request', '');
    }
    if (!data || !data.interval || !data.size || !data.count
        || (typeof data.interval !== 'number') || (data.interval % 1 !== 0)
        || (typeof data.size !== 'number') || (data.size < 0) || (data.size % 1 !== 0)
        || (typeof data.count !== 'number') || (data.count < 0) || (data.count % 1 !== 0)) {
        return lib.message.Message.buildResponseMessage(requestMessage, 400, {}, 'Bad Request', '');
    }
    if (this.monitor.running) {
        return lib.message.Message.buildResponseMessage(requestMessage, 409, {}, 'Conflict', '');
    }
    this.size = data.size;
    this.interval = (data.interval < lib.oracle.iot.client.monitor.pollingInterval ? lib.oracle.iot.client.monitor.pollingInterval : data.interval);
    this.count = data.count;
    this.currentCount = 0;
    this.startPooling = Date.now();
    this.monitor.start();
    return lib.message.Message.buildResponseMessage(requestMessage, 200, {}, '', '');
};

/**@ignore*/
$impl.TestConnectivity.prototype.stopHandler = function (requestMessage) {
    var method = _getMethodForRequestMessage(requestMessage);
    if (!method || (method !== 'PUT')) {
        return lib.message.Message.buildResponseMessage(requestMessage, 405, {}, 'Method Not Allowed', '');
    }
    this.monitor.stop();
    return lib.message.Message.buildResponseMessage(requestMessage, 200, {}, '', '');
};

/**@ignore*/
$impl.TestConnectivity.prototype.testHandler = function (requestMessage) {
    var method = _getMethodForRequestMessage(requestMessage);
    if (!method || (method !== 'GET')) {
        return lib.message.Message.buildResponseMessage(requestMessage, 405, {}, 'Method Not Allowed', '');
    }
    var obj = {
        active: this.monitor.running,
        count: this.count,
        currentCount: this.currentCount,
        interval: this.interval,
        size: this.size
    };
    return lib.message.Message.buildResponseMessage(requestMessage, 200, {}, JSON.stringify(obj), '');
};

/** @ignore */
function _strRepeat(str, qty) {
    if (qty < 1) return '';
    var result = '';
    while (qty > 0) {
        if (qty & 1) {
            result += str;
        }
        qty >>= 1;
        str = str + str;
    }
    return result;
}
