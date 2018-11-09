/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * @alias iotcs.device.util
 * @memberof iotcs.device
 * @namespace
 */
lib.device.util = {};

/**
 * A directly-connected device is able to send messages to, and receive messages from, the IoT
 * server.  When the directly-connected device is activated on the server, the server assigns a
 * logical-endpoint identifier.  This logical-endpoint identifier is required for sending messages
 * to, and receiving messages from, the server.
 * <p>
 * The directly-connected device is able to activate itself using the direct activation capability.
 * The data required for activation and authentication is retrieved from a TrustedAssetsStore
 * generated using the TrustedAssetsProvisioner tool using the Default TrustedAssetsManager.
 * <p>
 * This object represents the low-level API for the directly-connected device and uses direct
 * methods for sending or receiving messages.
 *
 * @alias iotcs.device.util.DirectlyConnectedDevice
 * @class
 * @memberof iotcs.device.util
 *
 * @param {string} [taStoreFile] - The trusted assets store file path to be used for trusted assets
 *        manager creation.  This is optional.  If none is given the default global library
 *        parameter is used: lib.oracle.iot.tam.store.
 * @param {string} [taStorePassword] - The trusted assets store file password to be used for trusted
 *        assets manager creation.  This is optional.  If none is given the default global library
 *        parameter is used: lib.oracle.iot.tam.storePassword.
 * @param {boolean} [gateway] - <code>true</code> to indicate creation of a GatewayDevice
 *        representation.
 */
lib.device.util.DirectlyConnectedDevice = function (taStoreFile, taStorePassword, gateway) {
    Object.defineProperty(this, '_',{
        enumerable: false,
        configurable: false,
        writable: false,
        value: {}
    });

    var maxAcceptBytes = lib.oracle.iot.client.device.requestBufferSize;
    var receive_message_queue = [];
    var sending = false;
    var self = this;
    var dcd = new $impl.DirectlyConnectedDevice(taStoreFile, taStorePassword, self, gateway);

    /** This keeps the dcd object private and unavailable to applications. */
    Object.defineProperty(this._, 'internalDev',{
        enumerable: false,
        configurable: false,
        writable: false,
        value: dcd
    });

    Object.defineProperty(this._, 'get_received_message',{
        enumerable: false,
        configurable: false,
        writable: false,
        value: function () {
            if (receive_message_queue.length > 0) {
                return receive_message_queue.splice(0, 1)[0];
            } else {
                return null;
            }
        }
    });

    /**
     * Sends the messages in 'messages' and receives incoming messages and calls deliveryCallback or
     * errorCallback.
     *
     * @param {message[]} messages
     * @param {function(message[])} deliveryCallback Callback for successfully sent messages.
     * @param {function(message[], error)} errorCallback Callback for errors for sent messages.
     * @param {boolean} longPolling {@code true} to enable long polling.
     * @param {number} timeout the number of milliseconds to wait to hear a response from the
     *                 server before giving up.
     */
    Object.defineProperty(this._, 'send_receive_messages',{
        enumerable: false,
        configurable: false,
        writable: false,
        value: function(messages, deliveryCallback, errorCallback, longPolling, timeout) {
            if (!dcd.isActivated()) {
                var error = lib.createError('device not yet activated');
                if (errorCallback) {
                    errorCallback(messages, error);
                }
                return;
            }

            try {
                lib.message.Message.checkMessagesBoundaries(messages);
            } catch (e) {
                if (errorCallback) {
                    errorCallback(messages, e);
                }
                return;
            }

            var bodyArray = [];
            var i;
            var len = messages.length;

            // Construct the messages to be sent.
            for (i = 0; i < len; i++) {
                var messagePush = messages[i].getJSONObject();
                if (self._.internalDev._.serverDelay) {
                    bodyArray.push(_optimizeOutgoingMessage({
                        clientId: messagePush.clientId,
                        source: messagePush.source,
                        destination: messagePush.destination,
                        sender: messagePush.sender,
                        priority: messagePush.priority,
                        reliability: messagePush.reliability,
                        eventTime: messagePush.eventTime + self._.internalDev._.serverDelay,
                        type: messagePush.type,
                        properties: messagePush.properties,
                        payload: _updateURIinMessagePayload(messagePush.payload)
                    }));
                } else {
                    messagePush.payload = _updateURIinMessagePayload(messagePush.payload);
                    bodyArray.push(_optimizeOutgoingMessage(messagePush));
                }
            }

            var post_body = JSON.stringify(bodyArray);
            debug('DirectlyConnectedDeviceUtil.send_receive_messages post_body = ' + post_body);
            var acceptBytes = maxAcceptBytes - _getUtf8BytesLength(JSON.stringify(receive_message_queue));

            if ((typeof acceptBytes !== 'number') || isNaN(acceptBytes) || (acceptBytes < 0) || (acceptBytes > maxAcceptBytes)) {
                var error1 = lib.createError('bad acceptBytes query parameter');
                if (errorCallback) {
                    errorCallback(messages, error1);
                }
                return;
            }
            var options = {
                path: $impl.reqroot + '/messages?acceptBytes=' + acceptBytes + (longPolling ? '&iot.sync' : '') + (timeout ? ('&iot.timeout=' + timeout) : ''),
                method: 'POST',
                headers: {
                    'Authorization': dcd._.bearer,
                    'X-EndpointId': dcd._.tam.getEndpointId(),
                    'Content-Length': Buffer.byteLength(post_body, 'utf-8')
                },
                tam: dcd._.tam
            };

            // Send the messages.
            $impl.protocolReq(options, post_body, function (response_body, error, dcdUtil) {
                if (!response_body || error) {
                    var err = error;

                    if (messages.length > 0) {
                        err = lib.createError('Error on sending messages, will re-try some.',
                            error);

                        // Re-queue messages with retries remaining and which are
                        // GUARANTEED_DELIVERY.
                        messages.forEach(message => {
                            if ((message._.internalObject.remainingRetries > 0) ||
                                (message._.internalObject.reliability &&
                                (message._.internalObject.reliability === 'GUARANTEED_DELIVERY')))
                            {
                                if (dcdUtil.dispatcher) {
                                    message._.internalObject.remainingRetries =
                                        message._.internalObject.remainingRetries - 1;

                                    dcdUtil.dispatcher.queue(message);
                                }
                            } else if (((message._.internalObject.type === lib.message.Message.Type.ALERT) ||
                                (message._.internalObject.type === lib.message.Message.Type.DATA)) &&
                                (message._.internalObject.remainingRetries === 0) &&
                                (message.onError))
                            {
                                message.onError(error);
                            }
                        });
                    }

                if (errorCallback) {
                        errorCallback(messages, err);
                    }

                    return;
                }

                // Messages were successfully sent.
                var messagePersistenceImpl = MessagePersistenceImpl.getInstance();

                // Guaranteed delivery messages are the only ones persisted.  Now that they're
                // sent, we need delete them from message persistence.
                if (messagePersistenceImpl) {
                    messages.forEach(message => {
                        if (message._.internalObject.reliability ===
                            lib.message.Message.Reliability.GUARANTEED_DELIVERY)
                        {
                            messagePersistenceImpl.delete(messages);
                        }
                    });
                }

                // Receive any messages coming in and add them to the receive_message_queue.
                if (Array.isArray(response_body) && response_body.length > 0) {
                    var i;
                    for (i = 0; i < response_body.length; i++) {
                        receive_message_queue.push(response_body[i]);
                    }
                } else if ((typeof response_body === 'object') && (response_body['x-min-acceptbytes'] !== 0)) {
                    var acceptBytes1 = maxAcceptBytes - _getUtf8BytesLength(JSON.stringify(receive_message_queue));
                    var bytes = parseInt(response_body['x-min-acceptbytes']);
                    if (bytes > maxAcceptBytes) {
                        lib.createError('The server has a request of ' + bytes +
                            ' bytes for this client, which is too large for the '+maxAcceptBytes+
                            ' byte request buffer. Please restart the client with larger value for the maxAcceptBytes property.');
                    } else if (bytes > acceptBytes1) {
                        lib.createError('The server has a request of ' + bytes +
                            ' which cannot be sent because the ' + maxAcceptBytes +
                            ' byte request buffer is filled with ' + (maxAcceptBytes - acceptBytes1) +
                            ' of unprocessed requests.');
                    }
                }

                if (deliveryCallback) {
                    deliveryCallback(messages);
                }

            }, function () {
                self._.send_receive_messages(messages, deliveryCallback, errorCallback, longPolling, timeout);
            }, dcd, self);
        }
    });

    Object.defineProperty(this._, 'isStorageAuthenticated', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function () {
            return (dcd._.storageContainerUrl && dcd._.storage_authToken);
        }
    });

    Object.defineProperty(this._, 'isStorageTokenExpired', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function () {
            // period in minutes recalculated in milliseconds
            return ((dcd._.storage_authTokenStartTime + lib.oracle.iot.client.storageTokenPeriod * 60000) < Date.now());
        }
    });

    Object.defineProperty(this._, 'sync_storage', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (storage, deliveryCallback, errorCallback, processCallback, timeout) {
            if (!dcd.isActivated()) {
                var error = lib.createError('device not yet activated');
                if (errorCallback) {
                    errorCallback(storage, error);
                }
                return;
            }

            if (!self._.isStorageAuthenticated() || self._.isStorageTokenExpired()) {
                dcd._.refresh_storage_authToken(function() {
                    self._.sync_storage(storage, deliveryCallback, errorCallback, processCallback, timeout);
                });
                return;
            }

            if (!storage.getURI()) {
                var path;
                var disableVirtualStorageDirectories =
                    process.env.com_oracle_iot_client_disable_storage_object_prefix;

                if (disableVirtualStorageDirectories && (disableVirtualStorageDirectories == "true")) {
                    path = storage.getName();
                } else {
                    path = dcd._.tam.getEndpointId() + '/' + storage.getName();
                }

                storage._.setURI(dcd._.storageContainerUrl + "/" + path);
            }

            var urlObj = require('url').parse(storage.getURI(), true);

            var options = {
                path: urlObj.path,
                host: urlObj.host,
                hostname: urlObj.hostname,
                port: urlObj.port || lib.oracle.iot.client.storageCloudPort,
                protocol: urlObj.protocol.slice(0, -1),
                headers: {
                    'X-Auth-Token': dcd._.storage_authToken
                }
            };

            if (storage.getInputStream()) {
                // Upload file
                options.method = "PUT";
                options.headers['Transfer-Encoding'] = "chunked";
                options.headers['Content-Type'] = storage.getType();
                var encoding = storage.getEncoding();
                if (encoding) options.headers['Content-Encoding'] = encoding;
            } else {
                // Download file
                options.method = "GET";
            }

            $port.https.storageReq(options, storage, deliveryCallback, function(error) {
                if (error) {
                    var exception = null;
                    try {
                        exception = JSON.parse(error.message);
                        if (exception.statusCode && (exception.statusCode === 401)) {
                            dcd._.refresh_storage_authToken(function () {
                                self._.sync_storage(storage, deliveryCallback, errorCallback, processCallback, timeout);
                            });
                            return;
                        }
                    } catch (e) {}
                    errorCallback(storage, error, -1);
                }
            }, processCallback);
        }
    });

    if (dcd._.tam.getServerScheme && (dcd._.tam.getServerScheme().indexOf('mqtt') > -1)) {

        /*Object.defineProperty(this._, 'receiver',{
            enumerable: false,
            configurable: false,
            writable: true,
            value: function (messages, error) {
                if (!messages || error) {
                    lib.createError('invalid message', error);
                    return;
                }
                if (Array.isArray(messages) && messages.length > 0) {
                    var acceptBytes = maxAcceptBytes - _getUtf8BytesLength(JSON.stringify(receive_message_queue));
                    if (acceptBytes >= _getUtf8BytesLength(JSON.stringify(messages))) {
                        var i;
                        for (i = 0; i < messages.length; i++) {
                            receive_message_queue.push(messages[i]);
                        }
                    } else {
                        lib.createError('not enough space for receiving messages');
                    }
                }
            }
        });*/

        var messageRegisterMonitor = null;
        messageRegisterMonitor = new $impl.Monitor(function () {
            if (!dcd.isActivated()) {
                return;
            }

            if (messageRegisterMonitor) {
                messageRegisterMonitor.stop();
            }

            /*$impl.protocolRegister($impl.reqroot + '/messages', function (message, error) {
                self._.receiver(message, error);
            }, dcd);*/

            $impl.protocolRegister($impl.reqroot + '/messages/acceptBytes', function (message, error) {
                var acceptBytes1 = maxAcceptBytes - _getUtf8BytesLength(JSON.stringify(receive_message_queue));
                var logMessage = (error ? error.message : JSON.stringify(message));
                var buffer = forge.util.createBuffer(logMessage, 'utf8');
                var bytes = buffer.getInt32();
                if (bytes > maxAcceptBytes) {
                    lib.createError('The server has a request of ' + bytes +
                        ' bytes for this client, which is too large for the '+maxAcceptBytes+
                        ' byte request buffer. Please restart the client with larger value for the maxAcceptBytes property.');
                } else if (bytes > acceptBytes1) {
                    lib.createError('The server has a request of ' + bytes +
                        ' which cannot be sent because the ' + maxAcceptBytes +
                        ' byte request buffer is filled with ' + (maxAcceptBytes - acceptBytes1) +
                        ' of unprocessed requests.');
                }
            }, dcd);
        });
        messageRegisterMonitor.start();
    }

    try {
        dcd.registerDevicePolicyResource();
    } catch (error) {
        console.log("Could not register device policy resource: " + error);
    }
};

/**
 * Activate the device.  The device will be activated on the server if necessary. When the device is
 * activated on the server.  The activation would tell the server the models that the device
 * implements.  Also the activation can generate additional authorization information that will be
 * stored in the TrustedAssetsStore and used for future authentication requests.  This can be a
 * time/resource consuming operation for some platforms.
 * <p>
 * If the device is already activated, this method will throw an exception.  The user should call
 * the isActivated() method prior to calling activate.
 *
 * @function activate
 * @memberof DirectlyConnectedDevice.prototype
 *
 * @param {string[]} deviceModelUrns - An array of deviceModel URNs implemented by this directly
 *        connected device.
 * @param {function} callback - The callback function.  This function is called with this object but
 *        in the activated state.  If the activation is not successful then the object will be
 *        <code>null</code> and an error object is passed in the form callback(device, error) and
 *        the reason can be taken from error.message.
 */
lib.device.util.DirectlyConnectedDevice.prototype.activate = function (deviceModelUrns, callback) {
    if (this.isActivated()) {
        lib.error('cannot activate an already activated device');
        return;
    }

    _mandatoryArg(deviceModelUrns, 'array');
    _mandatoryArg(callback, 'function');

    deviceModelUrns.forEach(function (urn) {
        _mandatoryArg(urn, 'string');
    });

    var deviceModels = deviceModelUrns;
    deviceModels.push('urn:oracle:iot:dcd:capability:direct_activation');
    deviceModels.push('urn:oracle:iot:dcd:capability:device_policy');
    var self = this;
    this._.internalDev.activate(deviceModels, function(activeDev, error) {
        if (!activeDev || error) {
            callback(null, error);
            return;
        }
        callback(self);
    });
};

/**
 * This will return the directly connected device state.
 *
 * @function isActivated
 * @memberof iotcs.device.util.DirectlyConnectedDevice.prototype
 *
 * @returns {boolean} <code>true</code> if the device is activated.
 */
lib.device.util.DirectlyConnectedDevice.prototype.isActivated = function () {
    return this._.internalDev.isActivated();
};

/**
 * Return the logical-endpoint identifier of this directly-connected device.  The logical-endpoint
 * identifier is assigned by the server as part of the activation process.
 *
 * @function getEndpointId
 * @memberof iotcs.device.util.DirectlyConnectedDevice.prototype
 *
 * @returns {string} The logical-endpoint identifier of this directly-connected device.
 */
lib.device.util.DirectlyConnectedDevice.prototype.getEndpointId = function () {
    return this._.internalDev.getEndpointId();
};

/**
 * Offer a message to be queued. Depending on the policies, if any, the message will be queued if it
 * is possible to do so without violating capacity restrictions.
 *
 * @function offer
 * @memberof iotcs.device.util.MessageDispatcher.prototype
 *
 * @param {iotcs.message.Message[]} messages - The message to be offered.
 * @throws ArrayStoreException if all the messages cannot be added to the queue.
 * @throws IllegalArgumentException if <code>messages</code> is <code>null</code> or empty.
 */
lib.device.util.DirectlyConnectedDevice.prototype.offer = function (messages) {
    if (this.isActivated()) {
        // We need to distinguish between an empty list of messages
        // that has been passed in versus an empty list of message
        // that has resulted from policies being applied.
        // So if the list we receive is empty, let send handle it.
        if (!messages || (messages.size === 0)) {
            this.send(messages, function(messages, error) {
                if (error) {
                    console.log('Error sending offered messages: ' + error);
                }
            });
        }

        // @type {PersistenceStore}
        const persistenceStore = PersistenceStoreManager.get(this.getEndpointId());
        // @type {MessagePolicyImpl}
        let messagingPolicyImpl;
        // @type {object}
        const mpiObj = persistenceStore.getOpaque('MessagingPolicyImpl', null);

        if (!mpiObj) {
                messagingPolicyImpl = new MessagingPolicyImpl(this);

                persistenceStore
                    .openTransaction()
                    .putOpaque('MessagingPolicyImpl', messagingPolicyImpl)
                    .commit();

                // @type {DevicePolicyManager}
                const devicePolicyManager =
                    DevicePolicyManager.getDevicePolicyManager(this.getEndpointId());
                devicePolicyManager.addChangeListener(messagingPolicyImpl);
            } else {
                messagingPolicyImpl = mpiObj;
            }

        // Now we know here that messages list is not empty.
        // If the message list is not empty after applying policies,
        // then send the messages. If it is empty after applying the
        // policies, then there is nothing to send because messages
        // were filtered, or are aggregating values (e.g., mean policy).
        // @type {Set<Message>}
        messages.forEach(message => {
            // @type {Message[]}
            messagingPolicyImpl.applyPolicies(message).then(messagesFromPolicies => {
                if (messagesFromPolicies) {
                    this.send(messagesFromPolicies, function(messages, error) {
                        if (error) {
                            console.log('Error sending offered messages: ' + error);
                        }
                    });
                }
            });
        });
    } else {
        throw new Error("Device not activated.");
    }
};

/**
 * This method is used for sending messages to the server.  If the directly connected device is not
 * activated an exception will be thrown. If the device is not yet authenticated the method will try
 * first to authenticate the device and then send the messages.
 *
 * @memberof iotcs.device.util.DirectlyConnectedDevice.prototype
 * @function send
 *
 * @param {iotcs.message.Message[]} messages - An array of the messages to be sent.
 * @param {function} callback - The callback function. This function is called with the messages
 *        that have been sent and in case of error the actual error from sending as the second
 *        parameter.
 */
lib.device.util.DirectlyConnectedDevice.prototype.send = function (messages, callback) {
    if (!this.isActivated()) {
        lib.error('device not activated yet');
        return;
    }

    _mandatoryArg(messages, 'array');
    _mandatoryArg(callback, 'function');

    messages.forEach(function (message) {
        _mandatoryArg(message, lib.message.Message);
    });

    this._.send_receive_messages(messages, callback, callback);
};

/**
 * This method is used for retrieving messages.  The DirectlyConnectedDevice uses an internal buffer
 * for the messages received that has a size of 4192 bytes.  When this method is called and there is
 * at least one message in the buffer, the first message from the buffer is retrieved.  If no
 * message is in the buffer, a force send of an empty message is tried so to see if any messages are
 * pending on the server side for the device and if there are, the buffer will be filled with them
 * and the first message retrieved.
 *
 * @function receive
 * @memberof iotcs.device.util.DirectlyConnectedDevice.prototype
 *
 * @param {number} [timeout] - The forcing for retrieving the pending messages will be done this
 *        amount of time.
 * @param {function} callback - The callback function.  This function is called with the first
 *        message received or null is no message is received in the timeout period.
 */
lib.device.util.DirectlyConnectedDevice.prototype.receive = function (timeout, callback) {
    if (!this.isActivated()) {
        lib.error('device not activated yet');
        return;
    }

    if (typeof  timeout === 'function') {
        callback = timeout;
    } else {
        _optionalArg(timeout, 'number');
    }
    _mandatoryArg(callback, 'function');

    var message = this._.get_received_message();
    if (message) {
        callback(message);
    } else {
        var self = this;
        var startTime = Date.now();
        var monitor = null;
        var handleReceivedMessages = function () {
            message = self._.get_received_message();
            if (message || (timeout && (Date.now() > (startTime + timeout)))) {
                if (monitor) {
                    monitor.stop();
                }
                callback(message);
            }
        };
        var handleSendReceiveMessages = function () {
            if (self._.internalDev._.refreshing) {
                return;
            }
            self._.send_receive_messages([], handleReceivedMessages, handleReceivedMessages);
        };
        if (self._.receiver) {
            monitor = new $impl.Monitor(handleReceivedMessages);
            monitor.start();
        } else if (lib.oracle.iot.client.device.disableLongPolling || self._.internalDev._.mqttController) {
            monitor = new $impl.Monitor(handleSendReceiveMessages);
            monitor.start();
        } else {
            self._.send_receive_messages([], handleReceivedMessages, handleReceivedMessages, true, (typeof timeout === 'number' ? Math.floor(timeout/1000) : null));
        }
    }
};

/**
 * Get the device model for the specified device model URN.
 *
 * @function getDeviceModel
 * @memberof iotcs.device.util.DirectlyConnectedDevice.prototype
 *
 * @param {string} deviceModelUrn - The URN of the device model.
 * @param {function} callback - The callback function.  This function is called with the following
 *        argument: a deviceModel object holding full description e.g. <code>{ name:"",
 *        description:"", fields:[...], created:date, isProtected:boolean, lastModified:date ... }
 *        </code>.  If an error occurs the deviceModel object is <code>null</code> and an error
 *        object is passed: callback(deviceModel, error) and the reason can be taken from the
 *        error.message.
 */
lib.device.util.DirectlyConnectedDevice.prototype.getDeviceModel = function (deviceModelUrn, callback) {
    new $impl.DeviceModelFactory().getDeviceModel(this, deviceModelUrn, callback);
};

/**
 * This method will close this directly connected device (client) and all it's resources.  All
 * monitors required by the message dispatcher associated with this client will be stopped, if there
 * is one.
 *
 * @function close
 * @memberof iotcs.device.util.DirectlyConnectedDevice.prototype
 * @see {@link iotcs.device.util.MessageDispatcher}
 */
lib.device.util.DirectlyConnectedDevice.prototype.close = function () {
    if (this.dispatcher) {
        this.dispatcher._.stop();
    }
    if (this.storageDispatcher) {
        this.storageDispatcher._.stop();
    }
};

/**
 * Create a new {@link iotcs.StorageObject}.
 *
 * <p>
 * createStorageObject method works in two modes:
 * </p><p>
 * 1. device.createStorageObject(name, type) -
 * Create a new {@link iotcs.StorageObject} with given object name and mime&ndash;type.  If
 * contentType is null, the mime type defaults to "application/octet-stream".  Create a new
 * <code>StorageObject</code> that will have a name with the given object name prefixed with the
 * device's endpoint ID and a directory separator.  The prefix addition can be disabled by setting
 * the <code>com_oracle_iot_client_disable_storage_object_prefix</code> environment variable to
 * <code>true</code>.  Used for uploading a storage object.
 * </p><pre>
 * Parameters:
 * {string} name - The unique name to be used to reference the content in storage.
 * {?string} [type] - The mime-type of the content. If <code>type</code> is <code>null</code> or
 *                    omitted, the mime&ndash;type defaults to {@link iotcs.StorageObject.MIME_TYPE}.
 *
 * Returns:
 * {iotcs.StorageObject} StorageObject
 * </pre><p>
 * 2. device.createStorageObject(uri, callback) -
 * Create a new {@link iotcs.StorageObject} from the URL for a named object in storage and return it
 * in a callback.
 * </p><pre>
 * Parameters:
 * {string} url - The URL of the object in the storage cloud.
 * {function(storage, error)} callback - The callback called once the getting storage data
 *                            completes.
 * </pre>
 *
 * @function createStorageObject
 * @memberof iotcs.device.util.DirectlyConnectedDevice.prototype
 * @see {@link http://www.iana.org/assignments/media-types/media-types.xhtml|IANA Media Types}
 *
 * @param {string} arg1 - The first argument.
 * @param {string | function} arg2 - The second argument.
 */
lib.device.util.DirectlyConnectedDevice.prototype.createStorageObject = function (arg1, arg2) {
    _mandatoryArg(arg1, "string");
    var storage = null;

    if ((typeof arg2 === "string") || (typeof arg2 === "undefined") || arg2 === null) {
        // DirectlyConnectedDevice.createStorageObject(name, type)
        storage = new lib.StorageObject(null, arg1, arg2);
        storage._.setDevice(this);
        return storage;
    } else {
        // DirectlyConnectedDevice.createStorageObject(uri, callback)
        _mandatoryArg(arg2, "function");
        if (!this.isActivated()) {
            lib.error('device not activated yet');
            return;
        }
        var url = arg1;
        var callback = arg2;
        var self = this;
        if (!this._.isStorageAuthenticated() || this._.isStorageTokenExpired()) {
            self._.internalDev._.refresh_storage_authToken(function() {
                self.createStorageObject(url, callback);
            });
        } else {
            var fullContainerUrl = this._.internalDev._.storageContainerUrl + "/";
            // url starts with fullContainerUrl
            if (url.indexOf(fullContainerUrl) !== 0) {
                callback(null, new Error("Storage Cloud URL is invalid."));
                return;
            }
            var name = url.substring(fullContainerUrl.length);
            var urlObj = require('url').parse(url, true);
            var options = {
                path: urlObj.path,
                host: urlObj.host,
                hostname: urlObj.hostname,
                port: urlObj.port || lib.oracle.iot.client.storageCloudPort,
                protocol: urlObj.protocol,
                method: "HEAD",
                headers: {
                    'X-Auth-Token': this._.internalDev._.storage_authToken
                },
                rejectUnauthorized: true,
                agent: false
            };

            debug("Request: " + new Date().getTime());
            debug(options.path);
            debug(options);

            var protocol = options.protocol.indexOf("https") !== -1 ? require('https') : require('http');
            var req = protocol.request(options, function (response) {

                debug();
                debug("Response: " + response.statusCode + ' ' + response.statusMessage);
                debug(response.headers);

                var body = '';
                response.on('data', function (d) {
                    body += d;
                });
                response.on('end', function () {
                    if (response.statusCode === 200) {
                        var type = response.headers["content-type"];
                        var encoding = response.headers["content-encoding"];
                        var date = new Date(Date.parse(response.headers["last-modified"]));
                        var len = parseInt(response.headers["content-length"]);
                        storage = new lib.StorageObject(url, name, type, encoding, date, len);
                        storage._.setDevice(self);
                        callback(storage);
                    } else if (response.statusCode === 401) {
                        self._.internalDev._.refresh_storage_authToken(function () {
                            self.createStorageObject(url, callback);
                        });
                    } else {
                        var e = new Error(JSON.stringify({
                            statusCode: response.statusCode,
                            statusMessage: (response.statusMessage ? response.statusMessage : null),
                            body: body
                        }));
                        callback(null, e);
                    }
                });
            });
            req.on('timeout', function () {
                callback(null, new Error('connection timeout'));
            });
            req.on('error', function (error) {
                callback(null, error);
            });
            req.end();
        }
    }
};
