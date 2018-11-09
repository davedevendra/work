/**
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * This is the private, internal directly-connected device which supports the low-level API
 * lib.device.util.DirectlyConnectedDevice.
 */
/** @ignore */
$impl.DirectlyConnectedDevice = function (taStoreFile, taStorePassword, dcd, gateway) {
    Object.defineProperty(this, '_',{
        enumerable: false,
        configurable: false,
        writable: false,
        value: {}
    });

    if (dcd) {
        // The "parent", low-level API DCD associated with this internal DCD.
        Object.defineProperty(this._, 'parentDcd', {
            enumerable: false,
            configurable: false,
            writable: false,
            value: dcd
        });
    }

    if (gateway) {
        Object.defineProperty(this._, 'gateway', {
            enumerable: false,
            configurable: false,
            writable: false,
            value: gateway
        });
    }

    Object.defineProperty(this._, 'tam',{
        enumerable: false,
        configurable: false,
        writable: false,
        value: new lib.device.TrustedAssetsManager(taStoreFile, taStorePassword)
    });

    Object.defineProperty(this._, 'bearer',{
        enumerable: false,
        configurable: true,
        writable: false,
        value: ""
    });

    Object.defineProperty(this._, 'activating',{
        enumerable: false,
        configurable: false,
        writable: true,
        value: false
    });

    Object.defineProperty(this._, 'refreshing',{
        enumerable: false,
        configurable: false,
        writable: true,
        value: false
    });

    var self = this;

    Object.defineProperty(this._, 'getCurrentServerTime',{
        enumerable: false,
        configurable: false,
        writable: false,
        value: function () {
            if (typeof self._.serverDelay === 'undefined') {
                return Date.now();
            } else {
                return (Date.now() + self._.serverDelay);
            }
        }
    });

    Object.defineProperty(this._, 'refresh_bearer',{
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (activation, callback) {
            self._.refreshing = true;

            var inputToSign = self._.tam.buildClientAssertion();

            if (!inputToSign) {
                self._.refreshing = false;
                var error1 = lib.createError('error on generating oauth signature');
                if (callback) {
                    callback(error1);
                }
                return;
            }

            var dataObject = {
                grant_type: 'client_credentials',
                client_assertion_type: 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer',
                client_assertion: inputToSign,
                scope: (activation ? 'oracle/iot/activation' : '')
            };

            var payload = $port.util.query.stringify(dataObject, null, null, {encodeURIComponent: $port.util.query.unescape});

            payload = payload.replace(new RegExp(':', 'g'),'%3A');

            var options = {
                path: $impl.reqroot + '/oauth2/token',
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                },
                tam: self._.tam
            };

            $impl.protocolReq(options, payload, function (response_body, error) {
                self._.refreshing = false;

                if (!response_body || error || !response_body.token_type || !response_body.access_token) {
                    if (error) {
                        var exception = null;
                        try {
                            exception = JSON.parse(error.message);
                            var now = Date.now();
                            if (exception.statusCode && (exception.statusCode === 400)) {
                                if (exception.body) {
                                    try{
                                        var body = JSON.parse(exception.body);
                                        if ((body.currentTime) && (typeof self._.serverDelay === 'undefined') && (now < parseInt(body.currentTime))) {
                                            Object.defineProperty(self._, 'serverDelay', {
                                                enumerable: false,
                                                configurable: false,
                                                writable: false,
                                                value: (parseInt(body.currentTime) - now)
                                            });
                                            Object.defineProperty(self._.tam, 'serverDelay', {
                                                enumerable: false,
                                                configurable: false,
                                                writable: false,
                                                value: (parseInt(body.currentTime) - now)
                                            });
                                            self._.refresh_bearer(activation, callback);
                                            return;
                                        }
                                    } catch (e) {}
                                }
                                if (activation) {
                                    self._.tam.setEndpointCredentials(self._.tam.getClientId(), null);
                                    self._.refresh_bearer(false, function (error) {
                                        self._.activating = false;
                                        if (error) {
                                            callback(null, error);
                                            return;
                                        }
                                        callback(self);
                                    });
                                    return;
                                }
                            }
                        } catch (e) {}
                        if (callback) {
                            callback(error);
                        }
                    } else {
                        if (callback) {
                            callback(new Error(JSON.stringify(response_body)));
                        }
                    }
                    return;
                }

                delete self._.bearer;
                Object.defineProperty(self._, 'bearer',{
                    enumerable: false,
                    configurable: true,
                    writable: false,
                    value: (response_body.token_type + ' ' + response_body.access_token)
                });

                if (callback) {
                    callback();
                }
            }, null, self);
        }
    });

    Object.defineProperty(this._, 'storage_authToken',{
        enumerable: false,
        configurable: true,
        writable: false,
        value: ""
    });

    Object.defineProperty(this._, 'storageContainerUrl',{
        enumerable: false,
        configurable: true,
        writable: false,
        value: ""
    });

    Object.defineProperty(this._, 'storage_authTokenStartTime',{
        enumerable: false,
        configurable: true,
        writable: false,
        value: ""
    });

    Object.defineProperty(this._, 'storage_refreshing',{
        enumerable: false,
        configurable: false,
        writable: true,
        value: false
    });

    Object.defineProperty(this._, 'refresh_storage_authToken',{
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (callback) {
            self._.storage_refreshing = true;

            var options = {
                path: $impl.reqroot + '/provisioner/storage',
                method: 'GET',
                headers: {
                    'Authorization': self._.bearer,
                    'X-EndpointId': self._.tam.getEndpointId()
                },
                tam: self._.tam
            };
            var refresh_function = function (response, error) {
                self._.storage_refreshing = false;

                if (!response || error || !response.storageContainerUrl || !response.authToken) {
                    if (error) {
                        if (callback) {
                            callback(error);
                        }
                    } else {
                        self._.refresh_storage_authToken(callback);
                    }
                    return;
                }

                delete self._.storage_authToken;
                Object.defineProperty(self._, 'storage_authToken',{
                    enumerable: false,
                    configurable: true,
                    writable: false,
                    value: response.authToken
                });

                delete self._.storageContainerUrl;
                Object.defineProperty(self._, 'storageContainerUrl',{
                    enumerable: false,
                    configurable: true,
                    writable: false,
                    value: response.storageContainerUrl
                });

                delete self._.storage_authTokenStartTime;
                Object.defineProperty(self._, 'storage_authTokenStartTime',{
                    enumerable: false,
                    configurable: true,
                    writable: false,
                    value: Date.now()
                });

                if (callback) {
                    callback();
                }
            };
            $impl.protocolReq(options, "", refresh_function, function() {
                self._.refresh_storage_authToken(callback);
            }, self);
        }
    });

    if (this.isActivated()) {
        var persistenceStore = PersistenceStoreManager.get(this._.tam.getEndpointId());
        let devicePolicyManager = new DevicePolicyManager(this);

        if (devicePolicyManager) {
            persistenceStore
                .openTransaction()
                .putOpaque('DevicePolicyManager', devicePolicyManager)
                .commit();
        }
    }
};

/** @ignore */
$impl.DirectlyConnectedDevice.prototype.activate = function (deviceModelUrns, callback) {
    _mandatoryArg(deviceModelUrns, 'array');
    _mandatoryArg(callback, 'function');

    var self = this;

    if (this.isActivated()) {
        lib.error('Cannot activate an already activated device.');
        return;
    }

    // #############################################################
    // CS 1.1 Server still has enrollment compliant with REST API v1
    //function enroll(host, port, id, secret, cert, device_register_handler) {

    function private_get_policy(error) {
        if (error) {
            callback(null, lib.createError('error on get policy for activation', error));
            return;
        }

        var options = {
            path: $impl.reqroot + '/activation/policy?OSName=' + $port.os.type() + '&OSVersion=' + $port.os.release(),
            method: 'GET',
            headers: {
                'Authorization': self._.bearer,
                'X-ActivationId': self._.tam.getClientId()
            },
            tam: self._.tam
        };

        $impl.protocolReq(options, "", function (response_body, error) {
            if (!response_body || error || !response_body.keyType || !response_body.hashAlgorithm || !response_body.keySize) {
                self._.activating = false;
                callback(null, lib.createError('error on get policy for activation', error));
                return;
            }
            private_key_generation_and_activation(response_body);
        }, null, self);
    }

    function private_key_generation_and_activation(parsed) {
        var algorithm = parsed.keyType;
        var hashAlgorithm = parsed.hashAlgorithm;
        var keySize = parsed.keySize;
        var isGenKeys = null;

        try {
            isGenKeys = self._.tam.generateKeyPair(algorithm, keySize);
        } catch (e) {
            self._.activating = false;
            callback(null, lib.createError('keys generation failed on activation',e));
            return;
        }

        if (!isGenKeys) {
            self._.activating = false;
            callback(null, lib.createError('keys generation failed on activation'));
            return;
        }

        var content = self._.tam.getClientId();

        var payload = {};

        try {
            var client_secret = self._.tam.signWithSharedSecret(content, 'sha256', null);
            var publicKey = self._.tam.getPublicKey();
            publicKey = publicKey.substring(publicKey.indexOf('----BEGIN PUBLIC KEY-----')
                + '----BEGIN PUBLIC KEY-----'.length,
                publicKey.indexOf('-----END PUBLIC KEY-----')).replace(/\r?\n|\r/g, "");

            var toBeSigned = forge.util.bytesToHex(forge.util.encodeUtf8(self._.tam.getClientId() + '\n' + algorithm + '\nX.509\nHmacSHA256\n'))
                + forge.util.bytesToHex(client_secret)
                + forge.util.bytesToHex(forge.util.decode64(publicKey));
            toBeSigned = forge.util.hexToBytes(toBeSigned);

            var signature = forge.util.encode64(self._.tam.signWithPrivateKey(toBeSigned, 'sha256'));

            payload = {
                certificationRequestInfo: {
                    subject: self._.tam.getClientId(),
                    subjectPublicKeyInfo: {
                        algorithm: algorithm,
                        publicKey: publicKey,
                        format: 'X.509',
                        secretHashAlgorithm: 'HmacSHA256'
                    },
                    attributes: {}
                },
                signatureAlgorithm: hashAlgorithm,
                signature: signature,
                deviceModels: deviceModelUrns
            };
        } catch (e) {
            self._.activating = false;
            callback(null, lib.createError('certificate generation failed on activation',e));
            return;
        }

        var options = {
            path : $impl.reqroot + '/activation/direct'
                    + (lib.oracle.iot.client.device.allowDraftDeviceModels ? '' : '?createDraft=false'),
            method : 'POST',
            headers : {
                'Authorization' : self._.bearer,
                'X-ActivationId' : self._.tam.getClientId()
            },
            tam: self._.tam
        };

        $impl.protocolReq(options, JSON.stringify(payload), function (response_body, error) {

            if (!response_body || error || !response_body.endpointState || !response_body.endpointId) {
                self._.activating = false;
                callback(null,lib.createError('invalid response on activation',error));
                return;
            }

            if(response_body.endpointState !== 'ACTIVATED') {
                self._.activating = false;
                callback(null,lib.createError('endpoint not activated: '+JSON.stringify(response_body)));
                return;
            }

            try {
                self._.tam.setEndpointCredentials(response_body.endpointId, response_body.certificate);
                var persistenceStore = PersistenceStoreManager.get(self._.tam.getEndpointId());

                persistenceStore
                    .openTransaction()
                    .putOpaque('DevicePolicyManager', new DevicePolicyManager(self))
                    .commit();
            } catch (e) {
                self._.activating = false;
                callback(null,lib.createError('error when setting credentials on activation',e));
                return;
            }

            self._.refresh_bearer(false, function (error) {
                self._.activating = false;

                if (error) {
                    callback(null,lib.createError('error on authorization after activation',error));
                    return;
                }

                try {
                    self.registerDevicePolicyResource();
                } catch (e) {
                    console.log("Could not register device policy resource: " + e);
                }

                callback(self);
            });
        }, null, self);
    }

    self._.activating = true;

    // implementation-end of end-point auth/enroll method

    // ####################################################################################
    self._.refresh_bearer(true, private_get_policy);
};

/** @ignore */
$impl.DirectlyConnectedDevice.prototype.isActivated = function () {
    return this._.tam.isActivated();
};

/** @ignore */
$impl.DirectlyConnectedDevice.prototype.getEndpointId = function () {
    return this._.tam.getEndpointId();
};

/** @ignore */
$impl.DirectlyConnectedDevice.prototype.registerDevicePolicyResource = function() {
    if (!this.isActivated()) {
        return;
    }

    // Note: Any changes here should also be made in MessageDispatcher.  This should really not be
    // here.  It should reference the handlerMethods in MessageDispatcher.
    var handlerMethods = {
        "deviceModels/urn:oracle:iot:dcd:capability:device_policy/policyChanged": "PUT",
        "deviceModels/urn:oracle:iot:dcd:capability:message_dispatcher/counters": 'GET',
        "deviceModels/urn:oracle:iot:dcd:capability:message_dispatcher/reset": 'PUT',
        "deviceModels/urn:oracle:iot:dcd:capability:message_dispatcher/pollingInterval": 'GET,PUT',
        "deviceModels/urn:oracle:iot:dcd:capability:diagnostics/info": 'GET',
        "deviceModels/urn:oracle:iot:dcd:capability:diagnostics/testConnectivity": 'GET,PUT'
    };

    var resources = [];

    resources.push(lib.message.Message.ResourceMessage.Resource.buildResource(
        "urn:oracle:iot:dcd:capability:device_policy",
        "deviceModels/urn:oracle:iot:dcd:capability:device_policy/policyChanged",
        'PUT',
        lib.message.Message.ResourceMessage.Resource.Status.ADDED,
        this._.tam.getEndpointId()));

        var resourceMessage = lib.message.Message.ResourceMessage.buildResourceMessage(
            resources,
            this._.parentDcd.getEndpointId(),
            lib.message.Message.ResourceMessage.Type.UPDATE,
            lib.message.Message.ResourceMessage.getMD5ofList(Object.keys(handlerMethods)))
        .source(this._.parentDcd.getEndpointId())
        .priority(lib.message.Message.Priority.HIGHEST);

    this._.parentDcd.send([resourceMessage], function(messages, error) {
        if (error) {
            console.log('Error registering device policy resources.  ' + error);
        }
    });
};

/** @ignore */
function _getUtf8BytesLength(string) {
    return forge.util.createBuffer(string, 'utf8').length();
}

/** @ignore */
function _optimizeOutgoingMessage(obj) {
    if (!__isArgOfType(obj, 'object')) { return; }
    if (_isEmpty(obj.properties)) { delete obj.properties; }
    return obj;
}

/** @ignore */
function _updateURIinMessagePayload(payload) {
    if (payload.data) {
        Object.keys(payload.data).forEach(function (key) {
            if (payload.data[key] instanceof lib.ExternalObject) {
                payload.data[key] = payload.data[key].getURI();
            }
        });
    }
    return payload;
}
