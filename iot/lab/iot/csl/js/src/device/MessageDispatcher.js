/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * This object is used for store and forward messages to the cloud by using a priority queue and
 * handling the priority attribute of messages.  It is also used for monitoring received messages
 * and any errors that can arise when sending messages.
 * <p>
 * There can be only one MessageDispatcher instance per DirectlyConnectedDevice at a time and it is
 * created at first use. To close an instance of a MessageDispatcher the
 * DirectlyConnectedDevice.close method must be used.
 * <p>
 * The message dispatcher uses the RequestDispatcher for dispatching automatically request messages
 * that come from the server and generate response messages to the server.
 * <p>
 * The onDelivery and onError attributes can be used to set handlers that are called when messages
 * are successfully delivered or an error occurs:
 * <br>
 * <code>messageDispatcher.onDelivery = function (messages);</code><br>
 * <code>messageDispatcher.onError = function (messages, error);</code><br>
 * Where messages is an array of the iotcs.message.Message object representing the messages that
 * were sent or not and error is an Error object.
 * <p>
 * Also the MessageDispatcher implements the message dispatcher, diagnostics and connectivity test
 * capabilities.
 *
 * @alias iotcs.device.util.MessageDispatcher
 * @class
 * @memberof iotcs.device.util
 * @see {@link iotcs.message.Message}
 * @see {@link iotcs.message.Message.Priority}
 * @see {@link iotcs.device.util.RequestDispatcher}
 * @see {@link iotcs.device.util.DirectlyConnectedDevice#close}
 *
 * @param {iotcs.device.util.DirectlyConnectedDevice} dcd - The directly connected device (Messaging
 *        API) associated with this message dispatcher.
 */
lib.device.util.MessageDispatcher = function (dcd) {
    _mandatoryArg(dcd, lib.device.util.DirectlyConnectedDevice);
    if (dcd.dispatcher) {
        return dcd.dispatcher;
    }
    var self = this;

    Object.defineProperty(this, '_', {
        enumerable: false,
        configurable: false,
        writable: true,
        value: {}
    });

    Object.defineProperty(this._, 'dcd', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: dcd
    });

    Object.defineProperty(this, 'onDelivery', {
        enumerable: false,
        configurable: false,
        get: function () {
            return self._.onDelivery;
        },
        set: function (newValue) {
            if (!newValue || (typeof newValue !== 'function')) {
                lib.error('Trying to set onDelivery to something that is not a function.');
                return;
            }
            self._.onDelivery = newValue;
        }
    });

    this._.onDelivery = function (arg) {};

    Object.defineProperty(this, 'onError', {
        enumerable: false,
        configurable: false,
        get: function () {
            return self._.onError;
        },
        set: function (newValue) {
            if (!newValue || (typeof newValue !== 'function')) {
                lib.error('Trying to set onDelivery to something that is not a function.');
                return;
            }

            self._.onError = newValue;
        }
    });

    this._.onError = function (arg1, arg2) {};

    var queue = new $impl.PriorityQueue(lib.oracle.iot.client.device.maximumMessagesToQueue);
    var client = dcd;

    Object.defineProperty(this._, 'push', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (message) {
            queue.push(message);
        }
    });

    Object.defineProperty(this._, 'storageDependencies', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: {
            keys: [],
            values: []
        }
    });

    Object.defineProperty(this._, 'failMessageClientIdArray', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: []
    });

    Object.defineProperty(this._, 'addStorageDependency', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (storage, msgClientId) {
            var index = self._.storageDependencies.keys.indexOf(storage);
            if (index == -1) {
                // add new KV in storageDependencies
                self._.storageDependencies.keys.push(storage);
                self._.storageDependencies.values.push([msgClientId]);
            } else {
                // add value for key
                self._.storageDependencies.values[index].push(msgClientId);
            }
        }
    });

    Object.defineProperty(this._, 'removeStorageDependency', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (storage) {
            var completed = (storage.getSyncStatus() === lib.device.StorageObject.SyncStatus.IN_SYNC);
            var index = self._.storageDependencies.keys.indexOf(storage);
            self._.storageDependencies.keys.splice(index, 1);
            var msgClientIds = self._.storageDependencies.values.splice(index, 1)[0];
            if (!completed && msgClientIds.length > 0) {
                //save failed clientIds
                msgClientIds.forEach(function (msgClientId) {
                    if (self._.failMessageClientIdArray.indexOf(msgClientId) === -1) self._.failMessageClientIdArray.push(msgClientId);
                });
            }
        }
    });

    // TODO: This really should be renamed to 'isStorageDependent' to be consistent.
    Object.defineProperty(this._, 'isContentDependent', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (clientId) {
            for (var i = 0; i < self._.storageDependencies.values.length; ++i) {
                if (self._.storageDependencies.values[i].indexOf(clientId) !== -1) {
                    return true;
                }
            }

            return false;
        }
    });

    var poolingInterval = lib.oracle.iot.client.device.defaultMessagePoolingInterval;
    var startPooling = null;
    var startTime = dcd._.internalDev._.getCurrentServerTime();

    var totalMessagesSent = 0;
    var totalMessagesReceived = 0;
    var totalMessagesRetried = 0;
    var totalBytesSent = 0;
    var totalBytesReceived = 0;
    var totalProtocolErrors = 0;

    var connectivityTestObj = new $impl.TestConnectivity(this);

    var handlers = {
        "deviceModels/urn:oracle:iot:dcd:capability:device_policy/policyChanged": function(requestMessage) {
            var method = _getMethodForRequestMessage(requestMessage);

            if (!method || method !== 'POST') {
                return lib.message.Message.buildResponseMessage(requestMessage, 405, {}, 'Method Not Allowed', '');
            }

            let devicePolicyManager = DevicePolicyManager.getDevicePolicyManager(dcd.getEndpointId());
            return devicePolicyManager.policyChanged(dcd, requestMessage);
        },
        "deviceModels/urn:oracle:iot:dcd:capability:message_dispatcher/counters": function (requestMessage) {
                var method = _getMethodForRequestMessage(requestMessage);
                if (!method || method !== 'GET') {
                    return lib.message.Message.buildResponseMessage(requestMessage, 405, {}, 'Method Not Allowed', '');
                }
                var counterObj = {
                    totalMessagesSent: totalMessagesSent,
                    totalMessagesReceived: totalMessagesReceived,
                    totalMessagesRetried: totalMessagesRetried,
                    totalBytesSent: totalBytesSent,
                    totalBytesReceived: totalBytesReceived,
                    totalProtocolErrors: totalProtocolErrors
                };
                return lib.message.Message.buildResponseMessage(requestMessage, 200, {}, JSON.stringify(counterObj), '');
            },
        "deviceModels/urn:oracle:iot:dcd:capability:message_dispatcher/reset": function (requestMessage) {
                var method = _getMethodForRequestMessage(requestMessage);
                if (!method || (method !== 'PUT')) {
                    return lib.message.Message.buildResponseMessage(requestMessage, 405, {}, 'Method Not Allowed', '');
                }
                totalMessagesSent = 0;
                totalMessagesReceived = 0;
                totalMessagesRetried = 0;
                totalBytesSent = 0;
                totalBytesReceived = 0;
                totalProtocolErrors = 0;
                return lib.message.Message.buildResponseMessage(requestMessage, 200, {}, '', '');
            },
        "deviceModels/urn:oracle:iot:dcd:capability:message_dispatcher/pollingInterval": function (requestMessage) {
                var method = _getMethodForRequestMessage(requestMessage);
                if (!method || ((method !== 'PUT') && (method !== 'GET'))) {
                    return lib.message.Message.buildResponseMessage(requestMessage, 405, {}, 'Method Not Allowed', '');
                }
                if (method === 'GET') {
                    return lib.message.Message.buildResponseMessage(requestMessage, 200, {}, JSON.stringify({pollingInterval: poolingInterval}), '');
                } else {
                    var data = null;
                    try {
                        data = JSON.parse($port.util.atob(requestMessage.payload.body));
                    } catch (e) {
                        return lib.message.Message.buildResponseMessage(requestMessage, 400, {}, 'Bad Request', '');
                    }
                    if (!data || (typeof data.pollingInterval !== 'number') || (data.pollingInterval % 1 !== 0)) {
                        return lib.message.Message.buildResponseMessage(requestMessage, 400, {}, 'Bad Request', '');
                    }
                    poolingInterval = (data.pollingInterval < lib.oracle.iot.client.monitor.pollingInterval ? lib.oracle.iot.client.monitor.pollingInterval : data.pollingInterval);
                    return lib.message.Message.buildResponseMessage(requestMessage, 200, {}, '', '');
                }
            },
        "deviceModels/urn:oracle:iot:dcd:capability:diagnostics/info": function (requestMessage) {
            var method = _getMethodForRequestMessage(requestMessage);
            if (!method || method !== 'GET') {
                return lib.message.Message.buildResponseMessage(requestMessage, 405, {}, 'Method Not Allowed', '');
            }
            var obj = {
                freeDiskSpace: 'Unknown',
                ipAddress: 'Unknown',
                macAddress: 'Unknown',
                totalDiskSpace: 'Unknown',
                version: 'Unknown',
                startTime: startTime
            };
            if ($port.util.diagnostics) {
                obj = $port.util.diagnostics();
            }
            return lib.message.Message.buildResponseMessage(requestMessage, 200, {}, JSON.stringify(obj), '');
        },
        "deviceModels/urn:oracle:iot:dcd:capability:diagnostics/testConnectivity": function (requestMessage) {
            var method = _getMethodForRequestMessage(requestMessage);
            var data = null;
            try {
                data = JSON.parse($port.util.atob(requestMessage.payload.body));
            } catch (e) {
                return lib.message.Message.buildResponseMessage(requestMessage, 400, {}, 'Bad Request', '');
            }
            if (!data || ((method === 'PUT') && (typeof data.active !== 'boolean'))) {
                return lib.message.Message.buildResponseMessage(requestMessage, 400, {}, 'Bad Request', '');
            }
            if (method === 'PUT') {
                if (data.active) {
                    return connectivityTestObj.startHandler(requestMessage);
                } else {
                    return connectivityTestObj.stopHandler(requestMessage);
                }
            } else {
                return connectivityTestObj.testHandler(requestMessage);
            }
        }
    };

    // Note: Any changes here must also be changed in
    // $impl.DirectlyConnectedDevice.prototype.registerDevicePolicyResource.
    var handlerMethods = {
        "deviceModels/urn:oracle:iot:dcd:capability:device_policy/policyChanged": "PUT",
        "deviceModels/urn:oracle:iot:dcd:capability:message_dispatcher/counters": 'GET',
        "deviceModels/urn:oracle:iot:dcd:capability:message_dispatcher/reset": 'PUT',
        "deviceModels/urn:oracle:iot:dcd:capability:message_dispatcher/pollingInterval": 'GET,PUT',
        "deviceModels/urn:oracle:iot:dcd:capability:diagnostics/info": 'GET',
        "deviceModels/urn:oracle:iot:dcd:capability:diagnostics/testConnectivity": 'GET,PUT'
    };

    var deliveryCallback = function (messages) {
        totalMessagesSent = totalMessagesSent + messages.length;
        messages.forEach(function (message) {
            totalBytesSent = totalBytesSent + _getUtf8BytesLength(JSON.stringify(message));
        });
        self.onDelivery(messages);
    };

    var errorCallback = function (messages, error) {
        totalProtocolErrors = totalProtocolErrors + 1;
        self.onError(messages, error);
    };

    /**
     * Callback handler for DirectlyConnectedDeviceUtil.send_receive_messages when messages are
     * successfully sent and when errors occur sending messages.  If error is supplied, any
     * specified error callback handlers are called.
     *
     * @ignore
     * @private
     *
     * @param {Message[]} messages - The messages to be sent.
     * @param {error} [error] - The error when sending messages, if there is one.
     */
    var handleSentAndErrorMessages = function (messages, error) {
        try {
            if (error) {
                errorCallback(messages, error);
            } else {
                deliveryCallback(messages);
            }
        } catch (e) {

        }
        var message = client._.get_received_message();
        while (message) {
            totalMessagesReceived = totalMessagesReceived + 1;
            totalBytesReceived = totalBytesReceived + _getUtf8BytesLength(JSON.stringify(message));
            if (message.type === lib.message.Message.Type.REQUEST) {
                var responseMessage = self.getRequestDispatcher().dispatch(message);
                if (responseMessage) {
                    self.queue(responseMessage);
                }
            }
            message = client._.get_received_message();
        }
    };

    var longPollingStarted = false;

    var pushMessage = function (array, message) {
        var inArray = array.forEach(function (msg) {
            if (JSON.stringify(msg.getJSONObject()) === JSON.stringify(message.getJSONObject())) {
                return true;
            }
        });
        if (!inArray) array.push(message);
    };

    /**
     * Handles pulling messages from the queue and sending messages them to the Iot cS, and
     * receiving messages from the IoT CS.
     *
     * @type {$impl.Monitor}
     */
    var sendMonitor = new $impl.Monitor(function () {
        var currentTime = Date.now();

        if (currentTime >= (startPooling + poolingInterval)) {
            if (!dcd.isActivated() || dcd._.internalDev._.activating || dcd._.internalDev._.refreshing) {
                startPooling = currentTime;
                return;
            }

            var sent = false;
            var message;
            var waitMessageArray = [];
            var sendMessageArray = [];
            var errorMessageArray = [];
            var inProgressSources = [];

            // Go through the queue and add the messages to the send message or wait message arrays
            // depending on whether it's a request message, if it has a storage dependency, or if
            // messages to this source are in-progress (so we can group messages to the same source
            // together in the same connection).
            while ((message = queue.pop()) !== null) {
                var clientId = message._.internalObject.clientId;
                var source = message._.internalObject.source;

                if (self._.failMessageClientIdArray.indexOf(clientId) > -1) {
                    if (errorMessageArray.indexOf(message) === -1) {
                        errorMessageArray.push(message);
                    }

                    continue;
                }

                if (message._.internalObject.type === lib.message.Message.Type.REQUEST ||
                    !(inProgressSources.indexOf(source) !== -1 ||
                        self._.isContentDependent(clientId)))
                {
                    pushMessage(sendMessageArray, message);

                    if (sendMessageArray.length === lib.oracle.iot.client.device.maximumMessagesPerConnection) {
                        break;
                    }
                } else {
                    if (inProgressSources.indexOf(source) === -1) {
                        inProgressSources.push(source);
                    }

                    pushMessage(waitMessageArray, message);
                }
            }

            sent = true;
            var messageArr = [];

            if (sendMessageArray.length > 0) {
                messageArr = sendMessageArray;
            }

            waitMessageArray.forEach(function (message) {
                self.queue(message);
            });

            client._.send_receive_messages(messageArr, handleSentAndErrorMessages, handleSentAndErrorMessages);

            if (errorMessageArray.length > 0) {
                errorCallback(errorMessageArray, new Error("Content sync failed"));
            }

            if (!sent && !client._.receiver && (lib.oracle.iot.client.device.disableLongPolling || client._.internalDev._.mqttController)) {
                client._.send_receive_messages([], handleSentAndErrorMessages, handleSentAndErrorMessages);
            }

            if (!client._.receiver && !lib.oracle.iot.client.device.disableLongPolling && !client._.internalDev._.mqttController) {
                var longPollCallback = null;

                longPollCallback = function (messages, error) {
                    if (!error) {
                        client._.send_receive_messages([], longPollCallback, longPollCallback, true);
                    } else {
                        longPollingStarted = false;
                    }

                    handleSentAndErrorMessages(messages, error);
                };

                if (!longPollingStarted) {
                    client._.send_receive_messages([], longPollCallback, longPollCallback, true);
                    longPollingStarted = true;
                }
            }

            startPooling = currentTime;
        }
    });

    if (client._.receiver) {
        var oldReceiver = client._.receiver;
        client._.receiver = function (messages, error) {
            oldReceiver(messages, error);
            var message = client._.get_received_message();
            while (message) {
                totalMessagesReceived = totalMessagesReceived + 1;
                totalBytesReceived = totalBytesReceived + _getUtf8BytesLength(JSON.stringify(message));
                if (message.type === lib.message.Message.Type.REQUEST) {
                    var responseMessage = self.getRequestDispatcher().dispatch(message);
                    if (responseMessage) {
                        self.queue(responseMessage);
                    }
                }
                message = client._.get_received_message();
            }
        };
    }

    var resourceMessageMonitor = null;

    resourceMessageMonitor = new $impl.Monitor(function () {
        if (!dcd.isActivated()) {
            return;
        }

        if (resourceMessageMonitor) {
            resourceMessageMonitor.stop();
        }

        for (var path in handlers) {
            self.getRequestDispatcher().registerRequestHandler(dcd.getEndpointId(), path, handlers[path]);
        }
        var resources = [];

        for (var path1 in handlerMethods) {
            resources.push(lib.message.Message.ResourceMessage.Resource.buildResource(path1, path1, handlerMethods[path1], lib.message.Message.ResourceMessage.Resource.Status.ADDED));
        }
        var message = lib.message.Message.ResourceMessage.buildResourceMessage(resources, dcd.getEndpointId(), lib.message.Message.ResourceMessage.Type.UPDATE, lib.message.Message.ResourceMessage.getMD5ofList(Object.keys(handlerMethods)))
            .source(dcd.getEndpointId())
            .priority(lib.message.Message.Priority.HIGHEST);
        self.queue(message);
    });

    resourceMessageMonitor.start();

    Object.defineProperty(this._, 'stop', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function () {
            sendMonitor.stop();
            if (resourceMessageMonitor) {
                resourceMessageMonitor.stop();
            }
        }
    });

    Object.defineProperty(dcd, 'dispatcher', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: this
    });

    startPooling = Date.now();
    sendMonitor.start();
    startTime = dcd._.internalDev._.getCurrentServerTime();

    // Do this last after everything else is established.
    // Populate outgoing message queue from persisted messages, but leave the
    // messages in persistence. The messages are removed from persistence when
    // they are delivered successfully.
    // @type {MessagePersistenceImpl}
    const messagePersistence = MessagePersistenceImpl.getInstance();

    if (dcd.isActivated() === true) {
        messagePersistence.load(dcd.getEndpointId()).then(messages => {
            if (messages && messages.size > 0) {
                messages.forEach(message => {
                    this.queue(message);
                });
            }
        }).catch(error => {
            //console.log('Error loading persistent messages: ' + error);
        });
    }
};

/**
 * This method returns the RequestDispatcher used by this MessageDispatcher for dispatching
 * messages.
 *
 * @function getRequestDispatcher
 * @memberof iotcs.device.util.MessageDispatcher.prototype
 *
 * @returns {iotcs.device.util.RequestDispatcher} The RequestDispatcher instance.
 */
lib.device.util.MessageDispatcher.prototype.getRequestDispatcher = function () {
    return new lib.device.util.RequestDispatcher();
};

/**
 * This method adds a message to the queue of this MessageDispatcher to be sent to the cloud.
 *
 * @function queue
 * @ignore
 * @memberof iotcs.device.util.MessageDispatcher.prototype
 *
 * @param {iotcs.message.Message} message - The message to be sent.
 */
lib.device.util.MessageDispatcher.prototype.queue = function (message) {
    _mandatoryArg(message, lib.message.Message);

    const messagePersistenceImpl = MessagePersistenceImpl.getInstance();

    if (messagePersistenceImpl && message._.internalObject.reliability ===
        lib.message.Message.Reliability.GUARANTEED_DELIVERY)
    {
        const messages = new Set();
        messages.add(message);
        messagePersistenceImpl.save(messages, this._.dcd.getEndpointId());
    }

    this._.push(message);
};

function _getMethodForRequestMessage(requestMessage){
    var method = null;
    if (requestMessage.payload && requestMessage.payload.method) {
        method = requestMessage.payload.method.toUpperCase();
    }
    if (requestMessage.payload.headers && Array.isArray(requestMessage.payload.headers['x-http-method-override']) && (requestMessage.payload.headers['x-http-method-override'].length > 0)) {
        method = requestMessage.payload.headers['x-http-method-override'][0].toUpperCase();
    }
    return method;
}

/**
 * Offer a message to be queued. Depending on the policies, if any, the message will be queued if it
 * is possible to do so without violating capacity restrictions.
 *
 * @function offer
 * @memberof iotcs.device.util.MessageDispatcher.prototype
 *
 * @param {iotcs.message.Message} message - The message to be offered.
 * @throws ArrayStoreException if all the messages cannot be added to the queue.
 * @throws IllegalArgumentException if <code>messages</code> is <code>null</code> or empty.
 */
lib.device.util.MessageDispatcher.prototype.offer = function (message) {
    _mandatoryArg(message, lib.message.Message);

    // @type {PersistenceStore}
    const persistenceStore =
        PersistenceStoreManager.get(this._.dcd.getEndpointId());

    // @type {MessagingPolicyImpl}
    const mpi = persistenceStore.getOpaque('MessagingPolicyImpl', null);
    // @type {MessagingPolicyImpl}
    let messagingPolicyImpl;

    if (mpi) {
        messagingPolicyImpl = mpi;
    } else {
        messagingPolicyImpl = new MessagingPolicyImpl(this._.dcd);

        persistenceStore
            .openTransaction()
            .putOpaque('MessagingPolicyImpl', messagingPolicyImpl)
            .commit();

        // @type {DevicePolicyManager}
        const devicePolicyManager =
            DevicePolicyManager.getDevicePolicyManager(this._.dcd.getEndpointId());

        devicePolicyManager.addChangeListener(messagingPolicyImpl);
    }

    var messageDispatcher = this;

    messagingPolicyImpl.applyPolicies(message).then(messageAry => {
        if (messageAry) {
            messageAry.forEach(function (message) {
                messageDispatcher._.push(message);
            });
        }
    }).catch(error => {
        console.log('MessageDispatcher.offer error: ' + error);
    });
};
