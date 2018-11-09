/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * This class provides an implementation of the trusted assets format as values of the
 * tag-length-value form in a Base64 encoded AES encrypted file.
 * <p>
 * Unified client provisioning format:
 * <p>
 * format = version & blob & *comment<br>
 * version = 1 byte, value 33<br>
 * blob = MIME base64 of encrypted & new line<br>
 * encrypted = IV & AES-128/CBC/PKCS5Padding of values<br>
 * IV = 16 random bytes<br>
 * values = *TLV<br>
 * TLV = tag & length & value<br>
 * tag = byte<br>
 * length = 2 byte BE unsigned int<br>
 * value = length bytes<br>
 * comment = # & string & : & string & new line<br>
 * string = UTF-8 chars<br>
 * <p>
 * The password based encryption key is the password processed by 10000 interations of
 * PBKDF2WithHmacSHA1 with the IV as the salt.
 * <p>
 * This class is internally used by the trusted assets store managers to read/write files in the
 * unified format.
 *
 * @alias iotcs.UnifiedTrustStore
 * @class
 * @memberof iotcs
 */
lib.UnifiedTrustStore = function (taStoreFileExt, taStorePasswordExt, forProvisioning) {

    this.trustStoreValues = {
        clientId: null,
        sharedSecret: null,
        serverHost: null,
        serverPort: null,
        endpointId: null,
        serverScheme: null,
        privateKey: null,
        publicKey: null,
        trustAnchors: null,
        certificate: null,
        connectedDevices: null
    };
    this.userInfo = "#";

    var taStoreFile = taStoreFileExt || lib.oracle.iot.tam.store;
    var taStorePassword = taStorePasswordExt || lib.oracle.iot.tam.storePassword;

    if (!taStoreFile) {
        lib.error('No TA Store file defined');
        return;
    }
    if (!taStorePassword) {
        lib.error('No TA Store password defined');
        return;
    }

    var self = this;

    this.load = function () {
        var input = $port.file.load(taStoreFile);
        if (input.charCodeAt(0) != lib.UnifiedTrustStore.constants.version) {
            lib.error('Invalid unified trust store version');
            return;
        }
        var base64BlockStr = input.substring(1, input.indexOf('#'));
        this.userInfo = input.substring(input.indexOf('#')) || this.userInfo;
        var encryptedData = forge.util.decode64(base64BlockStr);
        if (encryptedData.length <= 0) {
            lib.error('Invalid unified trust store');
            return;
        }
        var iv = forge.util.createBuffer();
        var encrypted = forge.util.createBuffer();
        for (var i = 0; i < lib.UnifiedTrustStore.constants.AES_BLOCK_SIZE; i++) {
            iv.putInt(encryptedData.charCodeAt(i), 8);
        }
        iv = iv.getBytes();
        for (i = lib.UnifiedTrustStore.constants.AES_BLOCK_SIZE; i < encryptedData.length; i++) {
            encrypted.putInt(encryptedData.charCodeAt(i), 8);
        }
        var key = forge.pkcs5.pbkdf2(taStorePassword, iv, lib.UnifiedTrustStore.constants.PBKDF2_ITERATIONS, lib.UnifiedTrustStore.constants.AES_KEY_SIZE);
        var decipher = forge.cipher.createDecipher('AES-CBC', key);
        decipher.start({iv: iv});
        decipher.update(encrypted);
        decipher.finish();
        var output = decipher.output;
        while (!output.isEmpty()) {
            var tag = output.getInt(8);
            var length = (output.getInt(16) >> 0);
            var buf = output.getBytes(length);
            switch (tag) {
                case lib.UnifiedTrustStore.constants.TAGS.serverUri:
                    var urlObj = forge.util.parseUrl(buf);
                    self.trustStoreValues.serverHost = urlObj.host;
                    self.trustStoreValues.serverPort = urlObj.port;
                    self.trustStoreValues.serverScheme = urlObj.scheme;
                    break;

                case lib.UnifiedTrustStore.constants.TAGS.clientId:
                    self.trustStoreValues.clientId = buf;
                    break;

                case lib.UnifiedTrustStore.constants.TAGS.sharedSecret:
                    self.trustStoreValues.sharedSecret = buf;
                    break;

                case lib.UnifiedTrustStore.constants.TAGS.endpointId:
                    self.trustStoreValues.endpointId = buf;
                    break;

                case lib.UnifiedTrustStore.constants.TAGS.trustAnchor:
                    if (!self.trustStoreValues.trustAnchors) {
                        self.trustStoreValues.trustAnchors = [];
                    }
                    self.trustStoreValues.trustAnchors.push(forge.pki.certificateToPem(forge.pki.certificateFromAsn1(forge.asn1.fromDer(buf))));
                    break;

                case lib.UnifiedTrustStore.constants.TAGS.privateKey:
                    self.trustStoreValues.privateKey = forge.pki.privateKeyFromAsn1(forge.asn1.fromDer(buf));
                    break;

                case lib.UnifiedTrustStore.constants.TAGS.publicKey:
                    self.trustStoreValues.publicKey = forge.pki.publicKeyFromAsn1(forge.asn1.fromDer(buf));
                    break;

                case lib.UnifiedTrustStore.constants.TAGS.connectedDevice:
                    if (!self.trustStoreValues.connectedDevices) {
                        self.trustStoreValues.connectedDevices = {};
                    }
                    var _data = { error: false };
                    var _output = new forge.util.ByteStringBuffer().putBytes(buf);
                    connectedDevice_loop:
                    while (!_output.isEmpty()) {
                        var _tag = _output.getInt(8);
                        var _length = (_output.getInt(16) >> 0);
                        var _buf = _output.getBytes(_length);
                        switch (_tag) {
                            case lib.UnifiedTrustStore.constants.TAGS.clientId:
                                _data.deviceId = _buf;
                                break;

                            case lib.UnifiedTrustStore.constants.TAGS.sharedSecret:
                                _data.sharedSecret = _buf;
                                break;

                            default:
                                lib.error("Invalid TAG inside indirect connected device data.");
                                _data.error = true;
                                break connectedDevice_loop;
                        }
                    }
                    if (!_data.error && _data.deviceId && _data.sharedSecret) {
                        self.trustStoreValues.connectedDevices[_data.deviceId] = _data.sharedSecret;
                    }
                    break;

                default:
                    lib.error('Invalid unified trust store TAG');
                    return;
            }
        }
    };

    this.store = function (values) {
        if (values) {
            Object.keys(values).forEach(function (key) {
                self.trustStoreValues[key] = values[key];
            });
        }
        var buffer = forge.util.createBuffer();
        var serverUri = self.trustStoreValues.serverScheme + '://' + self.trustStoreValues.serverHost + ':' + self.trustStoreValues.serverPort;
        buffer.putInt(lib.UnifiedTrustStore.constants.TAGS.serverUri, 8);
        buffer.putInt(serverUri.length, 16);
        buffer.putBytes(serverUri);
        buffer.putInt(lib.UnifiedTrustStore.constants.TAGS.clientId, 8);
        buffer.putInt(self.trustStoreValues.clientId.length, 16);
        buffer.putBytes(self.trustStoreValues.clientId);
        buffer.putInt(lib.UnifiedTrustStore.constants.TAGS.sharedSecret, 8);
        buffer.putInt(self.trustStoreValues.sharedSecret.length, 16);
        buffer.putBytes(self.trustStoreValues.sharedSecret);
        if (self.trustStoreValues.endpointId) {
            buffer.putInt(lib.UnifiedTrustStore.constants.TAGS.endpointId, 8);
            buffer.putInt(self.trustStoreValues.endpointId.length, 16);
            buffer.putBytes(self.trustStoreValues.endpointId);
        }
        if (Array.isArray(self.trustStoreValues.trustAnchors)) {
            self.trustStoreValues.trustAnchors.forEach(function (trustAnchor) {
                var trust = forge.asn1.toDer(forge.pki.certificateToAsn1(forge.pki.certificateFromPem(trustAnchor))).getBytes();
                buffer.putInt(lib.UnifiedTrustStore.constants.TAGS.trustAnchor, 8);
                buffer.putInt(trust.length, 16);
                buffer.putBytes(trust);
            });
        }
        if (self.trustStoreValues.privateKey) {
            buffer.putInt(lib.UnifiedTrustStore.constants.TAGS.privateKey, 8);
            var tempBytes = forge.asn1.toDer(forge.pki.wrapRsaPrivateKey(forge.pki.privateKeyToAsn1(self.trustStoreValues.privateKey))).getBytes();
            buffer.putInt(tempBytes.length, 16);
            buffer.putBytes(tempBytes);
        }
        if (self.trustStoreValues.publicKey) {
            buffer.putInt(lib.UnifiedTrustStore.constants.TAGS.publicKey, 8);
            var tempBytes1 = forge.asn1.toDer(forge.pki.publicKeyToAsn1(self.trustStoreValues.publicKey)).getBytes();
            buffer.putInt(tempBytes1.length, 16);
            buffer.putBytes(tempBytes1);
        }
        if (self.trustStoreValues.connectedDevices) {
            for (var deviceId in self.trustStoreValues.connectedDevices) {
                buffer.putInt(lib.UnifiedTrustStore.constants.TAGS.connectedDevice, 8);
                // deviceId.length + sharedSecret.length + 6
                // where 6 bytes contains [ACTIVATION_ID_TAG|<icd activation id length> and [SHARED_SECRET_TAG|<icd shared secret length>
                buffer.putInt(deviceId.length + self.trustStoreValues.connectedDevices[deviceId].length + 6, 16);
                buffer.putInt(lib.UnifiedTrustStore.constants.TAGS.clientId, 8);
                buffer.putInt(deviceId.length, 16);
                buffer.putBytes(deviceId);
                buffer.putInt(lib.UnifiedTrustStore.constants.TAGS.sharedSecret, 8);
                buffer.putInt(self.trustStoreValues.connectedDevices[deviceId].length, 16);
                buffer.putBytes(self.trustStoreValues.connectedDevices[deviceId]);
            }
        }
        var iv = forge.random.getBytesSync(lib.UnifiedTrustStore.constants.AES_BLOCK_SIZE);
        var key = forge.pkcs5.pbkdf2(taStorePassword, iv, lib.UnifiedTrustStore.constants.PBKDF2_ITERATIONS, lib.UnifiedTrustStore.constants.AES_KEY_SIZE);
        var cipher = forge.cipher.createCipher('AES-CBC', key);
        cipher.start({iv: iv});
        cipher.update(buffer);
        cipher.finish();
        var finalBuffer = forge.util.createBuffer();
        finalBuffer.putInt(lib.UnifiedTrustStore.constants.version, 8);
        finalBuffer.putBytes(forge.util.encode64(iv+cipher.output.getBytes()));
        finalBuffer.putBytes("\n" + this.userInfo);
        $port.file.store(taStoreFile, finalBuffer.getBytes());
    };

    this.setValues = function (otherManager) {
        Object.keys(otherManager).forEach(function (key) {
            if (self.trustStoreValues[key]) {
                otherManager[key] = self.trustStoreValues[key];
            }
        });
    };

    this.update = function (otherManager) {
        Object.keys(otherManager).forEach(function (key) {
            if (otherManager[key] && (typeof self.trustStoreValues[key] !== 'undefined')) {
                self.trustStoreValues[key] = otherManager[key];
            }
        });
        self.store();
    };

    if (!forProvisioning) {
        this.load();
    }

};

/**
 * Enumeration of unified trust store format constants.
 *
 * @alias constants
 * @class
 * @memberof iotcs.UnifiedTrustStore
 * @readonly
 * @enum {Integer}
 * @static
 */
lib.UnifiedTrustStore.constants = {
    version: 33,
    AES_BLOCK_SIZE: 16,
    AES_KEY_SIZE: 16,
    PBKDF2_ITERATIONS: 10000,
    TAGS: {}
};

lib.UnifiedTrustStore.constants.TAGS = {
    /**
     * The URI of the server, e.g., https://iotinst-mydomain.iot.us.oraclecloud.com:443
     */
    serverUri: 1,
    /** A client id is either an integration id (for enterprise clients), or an
     * activation id (for device clients). An activation id may also be
     * referred to a hardware id.
     */
    clientId: 2,
    /**
     * The shared secret as plain text
     */
    sharedSecret: 3,
    /**
     * For devices, the endpoint id TLV is omitted from the provisioning file
     * (unless part of a CONNECTED_DEVICE_TAG TLV).
     * For enterpise integrations, the endpoint id is set in the provisioning file
     * by the inclusion of the second ID argument.
     */
    endpointId: 4,
    /**
     * The trust anchor is the X509 cert
     */
    trustAnchor: 5,
    privateKey: 6,
    publicKey: 7,
    /**
     * The client id and shared secret of a device that can connect
     * indirectly through the device client
     *
     * Connected device TLV =
     * [CONNECTED_DEVICE_TAG|<length>|[CLIENT_ID_TAG|<icd activation id length>|<icd activation id>][SHARED_SECRET_TAG|<icd shared secrect length>|<icd shared secret>]]
     */
    connectedDevice: 8
};


/**
 * This is a helper method for provisioning files used by the trusted assets store managers in the
 * unified trust store format.
 *
 * @function provision
 * @memberof iotcs.UnifiedTrustStore
 *
 * @param {string} taStoreFile - The Trusted Assets Store file name.
 * @param {string} taStorePassword - The Trusted Assets Store password.
 * @param {string} serverScheme - The scheme used to communicate with the server. Possible values are http(s) or mqtt(s).
 * @param {string} serverHost - The IoT CS server host name.
 * @param {number} serverPort - The IoT CS server port.
 * @param {string} clientId - The activation ID for devices or client ID for application
 *        integrations.
 * @param {string} sharedSecret - The client's shared secret.
 * @param {string} truststore - The truststore file containing PEM-encoded trust anchors
 *        certificates to be used to validate the IoT CS server certificate chain.
 * @param {string} connectedDevices - An array of indirect connect devices.
 */
lib.UnifiedTrustStore.provision = function (taStoreFile, taStorePassword, serverScheme, serverHost, serverPort, clientId, sharedSecret, truststore, connectedDevices) {
    if (!taStoreFile) {
        throw 'No TA Store file provided';
    }
    if (!taStorePassword) {
        throw 'No TA Store password provided';
    }
    var entries = {
        clientId: clientId,
        serverHost: serverHost,
        serverPort: serverPort,
        serverScheme: (serverScheme ? serverScheme : 'https'),
        sharedSecret: sharedSecret,
        trustAnchors: (truststore ? (Array.isArray(truststore) ? truststore : _loadTrustAnchorsBinary(truststore)) : []),
        connectedDevices: (connectedDevices ? connectedDevices : {})
    };
    new lib.UnifiedTrustStore(taStoreFile, taStorePassword, true).store(entries);
};

/** @ignore */
function _loadTrustAnchorsBinary (truststore) {
    return $port.file.load(truststore)
        .split(/\-{5}(?:B|E)(?:[A-Z]*) CERTIFICATE\-{5}/)
        .filter(function(elem) { return ((elem.length > 1) && (elem.indexOf('M') > -1)); })
        .map(function(elem) { return '-----BEGIN CERTIFICATE-----' + elem.replace(new RegExp('\r\n', 'g'),'\n') + '-----END CERTIFICATE-----'; });
}
