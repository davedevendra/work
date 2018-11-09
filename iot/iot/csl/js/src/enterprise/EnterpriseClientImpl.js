/**
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/** @ignore */
$impl.EnterpriseClientImpl = function (taStoreFile, taStorePassword) {

    Object.defineProperty(this, '_',{
        enumerable: false,
        configurable: false,
        writable: false,
        value: {}
    });

    Object.defineProperty(this._, 'tam',{
        enumerable: false,
        configurable: false,
        writable: false,
        value: new lib.enterprise.TrustedAssetsManager(taStoreFile, taStorePassword)
    });

    Object.defineProperty(this._, 'bearer',{
        enumerable: false,
        configurable: true,
        writable: false,
        value: ""
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
        value: function (callback) {

            self._.refreshing = true;

            var id = self._.tam.getClientId();

            var exp = parseInt((self._.getCurrentServerTime() + 900000)/1000);
            var header = {
                typ: 'JWT',
                alg: 'HS256'
            };
            var claims = {
                iss: id,
                sub: id,
                aud: 'oracle/iot/oauth2/token',
                exp: exp
            };

            var inputToSign =
                $port.util.btoa(JSON.stringify(header))
                + '.'
                + $port.util.btoa(JSON.stringify(claims));

            var signed;

            try {
                var digest = self._.tam.signWithSharedSecret(inputToSign, "sha256");
                signed = forge.util.encode64(forge.util.hexToBytes(digest.toHex()));
            } catch (e) {
                self._.refreshing = false;
                var error1 = lib.createError('error on generating oauth signature', e);
                if (callback) {
                    callback(error1);
                }
                return;
            }

            inputToSign = inputToSign + '.' + signed;
            inputToSign = inputToSign.replace(/\+/g, '-').replace(/\//g, '_').replace(/\=+$/, '');
            var dataObject = {
                grant_type: 'client_credentials',
                client_assertion_type: 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer',
                client_assertion: inputToSign,
                scope: ''
            };

            var payload = $port.util.query.stringify(dataObject, null, null, {encodeURIComponent: $port.util.query.unescape});

            payload = payload.replace(new RegExp(':', 'g'),'%3A');

            var options = {
                path: $impl.reqroot.replace('webapi','api') + '/oauth2/token',
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                },
                tam: self._.tam
            };

            $impl.https.req(options, payload, function (response_body, error) {

                self._.refreshing = false;

                if (!response_body || error || !response_body.token_type || !response_body.access_token) {

                    if (error) {
                        var exception = null;
                        try {
                            exception = JSON.parse(error.message);
                            var now = Date.now();
                            if (exception.statusCode && (exception.statusCode === 400)
                                && (exception.body)) {
                                var body = JSON.parse(exception.body);
                                if ((body.currentTime) && (typeof self._.serverDelay === 'undefined') && (now < parseInt(body.currentTime))) {
                                    Object.defineProperty(self._, 'serverDelay', {
                                        enumerable: false,
                                        configurable: false,
                                        writable: false,
                                        value: (parseInt(body.currentTime) - now)
                                    });
                                    self._.refresh_bearer(callback);
                                    return;
                                }
                            }
                        } catch (e) {

                        }
                    }

                    if (callback) {
                        callback(error);
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

            });
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
                    'X-EndpointId': self._.tam.getClientId()
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
            $impl.https.req(options, "", refresh_function, function() {
                self._.refresh_storage_authToken(callback);
            }, self);
        }
    });
};