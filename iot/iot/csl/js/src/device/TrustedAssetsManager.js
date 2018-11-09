/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * The <code>TrustedAssetsManager</code> interface defines methods for handling trust
 * material used for activation and authentication to the IoT CS. Depending on
 * the capability of the client or device as well as on the security
 * requirements implementations of this interface may simply store sensitive
 * trust material in a plain persistent store, in some keystore or in a secure
 * token.
 * <dl>
 * <dt>Authentication of Devices with the IoT CS</dt>
 * <dd>
 * <dl>
 * <dt>Before/Upon Device Activation</dt>
 * <dd>
 * A device must use client secret-based authentication to authenticate with the
 * OAuth service and retrieve an access token to perform activation with the IoT
 * CS server. This is done by using an activation ID and a shared secret.
 * </dd>
 * <dt>After Device Activation</dt>
 * <dd>
 * A device must use client assertion-based authentication to authenticate with
 * the OAuth service and retrieve an access token to perform send and retrieve
 * messages from the IoT CS server. This is done by using the assigned endpoint ID
 * and generated private key.</dd>
 * </dl>
 * </dd>
 * <dt>Authentication of <em>Pre-activated</em> Enterprise Applications with the
 * IoT CS</dt>
 * <dd>
 * <dl>
 * <dt>Before/After Application Activation</dt>
 * <dd>
 * An enterprise integration must use client secret-based authentication to authenticate with the
 * OAuth service and retrieve an access token to perform any REST calls with the IoT
 * CS server. This is done by using the integration ID and a shared secret.</dd>
 * </dd>
 * </dl>
 *
 * @alias iotcs.device.TrustedAssetsManager
 * @class
 * @memberof iotcs.device
 *
 * @param {string} [taStoreFile] - The trusted assets store file path to be used for trusted assets
 *        manager creation. This is optional.  If none is given the default global library parameter
 *        is used: lib.oracle.iot.tam.store.
 * @param {string} [taStorePassword] - The trusted assets store file password to be used for trusted
 *        assets manager creation. This is optional.  If none is given the default global library
 *        parameter is used: lib.oracle.iot.tam.storePassword.
 */
lib.device.TrustedAssetsManager = function (taStoreFile, taStorePassword) {
    this.clientId = null;
    this.sharedSecret = null;
    this.serverHost = null;
    this.serverPort = null;
    this.endpointId = null;
    this.serverScheme = 'https';
    this.taStoreFile = null;

    this.privateKey = null;
    this.publicKey = null;
    this.certificate = null;
    this.trustAnchors = [];
    this.connectedDevices = {};

    var _taStoreFile = taStoreFile || lib.oracle.iot.tam.store;
    var _taStorePassword = taStorePassword || lib.oracle.iot.tam.storePassword;

    if (!_taStoreFile) {
        lib.error('No TA Store file defined');
        return;
    }
    if (!_taStorePassword) {
        lib.error('No TA Store password defined');
        return;
    }

    if (!_taStoreFile.endsWith('.json')) {
        this.unifiedTrustStore = new lib.UnifiedTrustStore(_taStoreFile, _taStorePassword, false);
        this.unifiedTrustStore.setValues(this);
        this.taStoreFile = _taStoreFile;
    } else {
        this.load = function () {
            var input = $port.file.load(_taStoreFile);
            var entries = JSON.parse(input);

            if (!_verifyTaStoreContent(entries, _taStorePassword)) {
                lib.error('TA Store not signed or tampered with');
                return;
            }

            this.clientId = entries.clientId;
            this.serverHost = entries.serverHost;
            this.serverPort = entries.serverPort;
            this.serverScheme = entries.serverScheme;
            this.sharedSecret = _decryptSharedSecret(entries.sharedSecret, _taStorePassword);
            this.trustAnchors = entries.trustAnchors;
            this.connectedDevices = entries.connectedDevices;

            {
                var keyPair = entries.keyPair;
                if (keyPair) {
                    var p12Der = forge.util.decode64(entries.keyPair);
                    var p12Asn1 = forge.asn1.fromDer(p12Der, false);
                    var p12 = forge.pkcs12.pkcs12FromAsn1(p12Asn1, false, _taStorePassword);

                    var bags = p12.getBags({
                        bagType: forge.pki.oids.certBag
                    });
                    this.certificate = bags[forge.pki.oids.certBag][0].cert;
                    bags = p12.getBags({
                        bagType: forge.pki.oids.pkcs8ShroudedKeyBag
                    });
                    var bag = bags[forge.pki.oids.pkcs8ShroudedKeyBag][0];
                    this.privateKey = bag.key;
                    this.endpointId = bag.attributes.friendlyName[0];
                }
            }
        };

        this.store = function () {
            lib.log('store ' + ((this.privateKey !== null) ? 'true' : 'false') + ' ' + this.endpointId);
            var keyPairEntry = null;
            if (this.privateKey) {
                var p12Asn1 = forge.pkcs12.toPkcs12Asn1(
                    this.privateKey,
                    this.certificate,
                    _taStorePassword, {
                        'friendlyName': this.endpointId
                    });
                var p12Der = forge.asn1.toDer(p12Asn1).getBytes();
                keyPairEntry = forge.util.encode64(p12Der);
            }
            var entries = {
                'clientId': this.clientId,
                'serverHost': this.serverHost,
                'serverPort': this.serverPort,
                'serverScheme': this.serverScheme,
                'sharedSecret': _encryptSharedSecret(this.sharedSecret, _taStorePassword),
                'trustAnchors': this.trustAnchors,
                'keyPair': keyPairEntry,
                'connectedDevices': this.connectedDevices
            };

            entries = _signTaStoreContent(entries, _taStorePassword);

            var output = JSON.stringify(entries);
            $port.file.store(_taStoreFile, output);
        };
        this.load();
    }
};

/**
 * Retrieves the IoT CS server host name.
 *
 * @function getServerHost
 * @memberof iotcs.device.TrustedAssetsManager.prototype
 *
 * @returns {?string} The IoT CS server host name or <code>null</code> if any error occurs
 *          retrieving the server host name.
 */
lib.device.TrustedAssetsManager.prototype.getServerHost = function () {
    return this.serverHost;
};

/**
 * Retrieves the IoT CS server port.
 *
 * @function getServerPort
 * @memberof iotcs.device.TrustedAssetsManager.prototype
 *
 * @returns {?number} The IoT CS server port (a positive integer) or <code>null</code> if any error
 *          occurs retrieving the server port.
 */
lib.device.TrustedAssetsManager.prototype.getServerPort = function () {
    return this.serverPort;
};

/**
 * Retrieves the ID of this client.  If the client is a device the client ID is the device
 * activation ID; if the client is a pre-activated enterprise application the client ID corresponds
 * to the assigned integration ID. The client ID is used along with a client secret derived from the
 * shared secret to perform secret-based client authentication with the IoT CS server.
 *
 * @function getClientId
 * @memberof iotcs.device.TrustedAssetsManager.prototype
 *
 * @returns {?string} The ID of this client or <code>null</code> if any error occurs retrieving the
 *          client ID.
 */
lib.device.TrustedAssetsManager.prototype.getClientId = function () {
    return this.clientId;
};

/**
 * Retrieves the IoT CS connected devices.
 *
 * @function getConnectedDevices
 * @memberof iotcs.device.TrustedAssetsManager.prototype
 *
 * @returns {?object} The IoT CS connected devices or <code>null</code> if any error occurs
 *          retrieving connected devices.
 */
lib.device.TrustedAssetsManager.prototype.getConnectedDevices = function () {
    return this.connectedDevices;
};

/**
 * Retrieves the public key to be used for certificate request.
 *
 * @function getPublicKey
 * @memberof iotcs.device.TrustedAssetsManager.prototype
 *
 * @returns {?string} The device public key as a PEM-encoded string or <code>null</code> if any
 *          error occurs retrieving the public key.
 */
lib.device.TrustedAssetsManager.prototype.getPublicKey = function () {
    if ((!this.publicKey) && (!this.certificate)) {
        throw new Error('Key pair not yet generated or certificate not yet assigned');
    }
    var key = (this.publicKey) ? this.publicKey : this.certificate.publicKey;
    return forge.pki.publicKeyToPem(key);
};

/**
 * Retrieves the trust anchor or most-trusted Certification Authority (CA) to be used to validate
 * the IoT CS server certificate chain.
 *
 * @function getTrustAnchorCertificates
 * @memberof iotcs.device.TrustedAssetsManager.prototype
 *
 * @returns {?Array} The PEM-encoded trust anchor certificates, or <code>null</code> if any error
 *          occurs retrieving the trust anchor.
 */
lib.device.TrustedAssetsManager.prototype.getTrustAnchorCertificates = function () {
    return this.trustAnchors;
};

/**
 * Sets the assigned endpoint ID and certificate as returned by the activation procedure.  Upon a
 * call to this method, a compliant implementation of the <code>TrustedAssetsManager</code>
 * interface must ensure the persistence of the provided endpoint credentials.  This method can only
 * be called once; unless the <code>TrustedAssetsManager</code> has been reset.
 * <p>
 * If the client is a pre-activated enterprise application, the endpoint ID has already been
 * provisioned and calling this method MUST fail with an <code>IllegalStateException</code>.
 * </p>
 *
 * @function setEndpointCredentials
 * @memberof iotcs.device.TrustedAssetsManager.prototype
 *
 * @param {string} endpointId - The assigned endpoint ID.
 * @param {string} certificate - The PEM-encoded certificate issued by the server or
 *        <code>null</code> if no certificate was provided by the server.
 * @returns {boolean} whether setting the endpoint credentials succeeded.
 */
lib.device.TrustedAssetsManager.prototype.setEndpointCredentials = function (endpointId, certificate) {
    /*if (!endpointId) {
        lib.error('EndpointId cannot be null');
        return false;
    }
    if (this.endpointId) {
        lib.error('EndpointId already assigned');
        return false;
    }*/
    if (!this.privateKey) {
        lib.error('Private key not yet generated');
        return false;
    }
    if (endpointId) {
        this.endpointId = endpointId;
    } else {
        this.endpointId = '';
    }
    try {
        if (!certificate || certificate.length <= 0) {
            this.certificate = _generateSelfSignedCert(this.privateKey, this.publicKey, this.clientId);
        } else {
            this.certificate = forge.pki.certificateFromPem(certificate);
        }
    } catch (e) {
        lib.error('Error generating certificate: ' + e);
        return false;
    }
    try {
        if (this.unifiedTrustStore) {
            this.unifiedTrustStore.update(this);
        } else {
            this.store();
        }
    } catch (e) {
        lib.error('Error storing the trust assets: ' + e);
        return false;
    }
    return true;
};

/**
 * Retrieves the assigned endpoint ID.
 *
 * @function getEndpointId
 * @memberof iotcs.device.TrustedAssetsManager.prototype
 *
 * @return {?string} The assigned endpoint ID or <code>null</code> if any error occurs retrieving
 *         the endpoint ID.
 */
lib.device.TrustedAssetsManager.prototype.getEndpointId = function () {
    if (!this.endpointId) {
        throw new Error('EndpointId not assigned');
    }
    return this.endpointId;
};

/**
 * Retrieves the assigned endpoint certificate.
 *
 * @function getEndpointCertificate
 * @memberof iotcs.device.TrustedAssetsManager.prototype
 *
 * @returns {?string} The PEM-encoded certificate or <code>null</code> if no certificate was
 *          assigned, or if any error occurs retrieving the endpoint certificate.
 */
lib.device.TrustedAssetsManager.prototype.getEndpointCertificate = function () {
    var certificate = null;
    if (!this.certificate) {
        lib.error('Endpoint certificate not assigned');
        return null;
    }
    try {
        if (!_isSelfSigned(this.certificate)) {
            certificate = forge.pki.certificateToPem(this.certificate);
        }
    } catch (e) {
        lib.error('Unexpected error retrieving certificate encoding: ' + 2);
        return null;
    }
    //XXX ??? is it an array or a string
    return certificate;
};

/**
 * Generates the key pair to be used for assertion-based client authentication with the IoT CS.
 *
 * @function generateKeyPair
 * @memberof iotcs.device.TrustedAssetsManager.prototype
 *
 * @param {string} algorithm - The key algorithm.
 * @param {number} keySize - The key size.
 * @returns {boolean} <code>true</code> if the key pair generation succeeded.
 */
lib.device.TrustedAssetsManager.prototype.generateKeyPair = function (algorithm, keySize) {
    if (!algorithm) {
        lib.error('Algorithm cannot be null');
        return false;
    }
    if (keySize <= 0) {
        lib.error('Key size cannot be negative or 0');
        return false;
    }
    if (this.privateKey) {
        lib.error('Key pair already generated');
        return false;
    }
    try {
        var keypair = forge.rsa.generateKeyPair({
            bits : keySize
            //, e: 0x10001
        });
        this.privateKey = keypair.privateKey;
        this.publicKey = keypair.publicKey;
    } catch (e) {
        lib.error('Could not generate key pair: ' + e);
        return false;
    }
    return true;
};

/**
 * Signs the provided data using the specified algorithm and the private key.  This method is only
 * use for assertion-based client authentication with the IoT CS.
 *
 * @function signWithPrivateKey
 * @memberof iotcs.device.TrustedAssetsManager.prototype
 *
 * @param {Array|string} data - A byte string to sign.
 * @param {string} algorithm - The algorithm to use.
 * @returns {?Array} The signature bytes or <code>null</code> if any error occurs retrieving the
 *          necessary key material or performing the operation.
 */
lib.device.TrustedAssetsManager.prototype.signWithPrivateKey = function (data, algorithm) {
    var signature = null;
    if (!algorithm) {
        lib.error('Algorithm cannot be null');
        return null;
    }
    if (!data) {
        lib.error('Data cannot be null');
        return null;
    }
    if (!this.privateKey) {
        lib.error('Private key not yet generated');
        return null;
    }
    try {
        var md = null;
        switch (algorithm) {
        case 'md5': {
            md = forge.md.md5.create();
            break;
        }
        case 'sha1': {
            md = forge.md.sha1.create();
            break;
        }
        case 'sha256': {
            md = forge.md.sha256.create();
            break;
        }
        case 'sha512': {
            md = forge.md.sha512.create();
            break;
        }
        case 'sha512/224': {
            md = forge.md.sha512.sha224.create();
            break;
        }
        case 'sha512/256': {
            md = forge.md.sha512.sha256.create();
            break;
        }
        }
        if (md) {
            md.update(data);
            signature = this.privateKey.sign(md);
        }
    } catch (e) {
        lib.error('Error signing with private key: ' + e);
        return null;
    }
    return signature;
};

/**
 * Signs the provided data using the specified algorithm and the shared secret of the device
 * indicated by the given hardware id.  Passing <code>null</code> for <code>hardwareId</code> is
 * identical to passing {@link #getClientId()}.
 *
 * @function signWithSharedSecret
 * @memberof iotcs.device.TrustedAssetsManager.prototype
 *
 * @param {Array} data - The bytes to be signed.
 * @param {string} algorithm - The hash algorithm to use.
 * @param {?string} hardwareId - The hardware id of the device whose shared secret is to be used for
 *        signing.
 * @return {?Array} The signature bytes or <code>null</code> if any error occurs retrieving the
 *         necessary key material or performing the operation.
 */
lib.device.TrustedAssetsManager.prototype.signWithSharedSecret = function (data, algorithm, hardwareId) {
    var digest = null;
    if (!algorithm) {
        lib.error('Algorithm cannot be null');
        return null;
    }
    if (!data) {
        lib.error('Data cannot be null');
        return null;
    }
    var secretKey;
    if (hardwareId === null || hardwareId == this.clientId) {
        secretKey = this.sharedSecret;
    } else {
        secretKey = this.connectedDevices[hardwareId];
    }

    if (secretKey === null || (typeof secretKey === "undefined")) {
        lib.log("Shared secret is not provisioned for " + (hardwareId ? hardwareId : this.clientId) + " device");
        return null;
    }
    try {
        var hmac = forge.hmac.create();
        hmac.start(algorithm, secretKey);
        hmac.update(data);
        digest = hmac.digest();
        // lib.log(digest.toHex());
    } catch (e) {
        lib.error('Error signing with shared secret: ' + e);
        return null;
    }
    return digest;
};

/**
 * Returns whether the client is activated.  The client is deemed activated if it has at least been
 * assigned endpoint ID.
 *
 * @function isActivated
 * @memberof iotcs.device.TrustedAssetsManager.prototype
 *
 * @returns {boolean} <code>true</code> if the device is activated.
 */
lib.device.TrustedAssetsManager.prototype.isActivated = function () {
    return (this.endpointId && (this.endpointId !== null) && (this.endpointId !== '')) ? true : false;
};

/**
 * Resets the trust material back to its provisioning state; in particular, the key pair is erased.
 * The client will have to go, at least,through activation again; depending on the provisioning
 * policy in place, the client may have to go through registration again.
 *
 * @function reset
 * @memberof iotcs.device.TrustedAssetsManager.prototype
 *
 * @return {boolean} <code>true</code> if the operation was successful.
 */
lib.device.TrustedAssetsManager.prototype.reset = function () {
    this.endpointId = null;
    this.privateKey = null;
    this.publicKey = null;
    this.certificate = null;
    try {
        if (this.unifiedTrustStore) {
            this.unifiedTrustStore.update(this);
        } else {
            this.store();
        }
    } catch (e) {
        lib.error('Error resetting the trust assets: ' + e);
        return false;
    }
    return true;
};

lib.device.TrustedAssetsManager.prototype.buildClientAssertion = function () {
    var id = (!this.isActivated() ? this.getClientId() : this.getEndpointId());
    var now = ((typeof this.serverDelay === 'undefined') ? Date.now() : (Date.now() + this.serverDelay));
    var exp = parseInt((now + 900000)/1000);
    var header = {
        typ: 'JWT',
        alg: (!this.isActivated() ? 'HS256' : 'RS256')
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
        if (!this.isActivated()) {
            var digest = this.signWithSharedSecret(inputToSign, "sha256", null);
            signed = forge.util.encode64(forge.util.hexToBytes(digest.toHex()));
        } else {
            var signatureBytes = this.signWithPrivateKey(inputToSign, "sha256");
            signed = forge.util.encode64(signatureBytes);
        }
    } catch (e) {
        var error = lib.createError('error on generating oauth signature', e);
        return null;
    }

    inputToSign = inputToSign + '.' + signed;
    inputToSign = inputToSign.replace(/\+/g, '-').replace(/\//g, '_').replace(/\=+$/, '');
    return inputToSign;
};

/**
 * Retrieves the IoT CS server scheme.
 *
 * @function getServerScheme
 * @memberof iotcs.device.TrustedAssetsManager.prototype
 *
 * @returns {?string} The IoT CS server scheme, or <code>null</code> if any error occurs retrieving
 *          the server scheme.
 */
lib.device.TrustedAssetsManager.prototype.getServerScheme = function () {
    return this.serverScheme;
};

/**
 * Provisions the designated Trusted Assets Store with the provided provisioning assets.  The
 * provided shared secret will be encrypted using the provided password.
 *
 * @alias iotcs.device.TrustedAssetsManager
 * @function provision
 * @memberof iotcs.device.TrustedAssetsManager
 * @static
 *
 * @param {string} taStoreFile - The Trusted Assets Store file name.
 * @param {string} taStorePassword - The Trusted Assets Store password.
 * @param {string} serverScheme - The scheme used to communicate with the server. Possible values are http(s) or mqtt(s).
 * @param {string} serverHost - The IoT CS server host name.
 * @param {number} serverPort - The IoT CS server port.
 * @param {string} clientId - The ID of the client.
 * @param {string} sharedSecret - The client's shared secret.
 * @param {string} truststore - The truststore file containing PEM-encoded trust anchors
 *        certificates to be used to validate the IoT CS server certificate chain.
 * @param {object} connectedDevices - The indirect connect devices.
 */
lib.device.TrustedAssetsManager.provision = function (taStoreFile, taStorePassword, serverScheme, serverHost, serverPort, clientId, sharedSecret, truststore, connectedDevices) {
	if (!taStoreFile) {
		throw 'No TA Store file provided';
	}
	if (!taStorePassword) {
		throw 'No TA Store password provided';
	}
	var entries = {
		'clientId' : clientId,
		'serverHost' : serverHost,
		'serverPort' : serverPort,
        'serverScheme' : (serverScheme ? serverScheme : 'https'),
		'sharedSecret' : _encryptSharedSecret(sharedSecret, taStorePassword),
		'trustAnchors' : (truststore ? (Array.isArray(truststore) ? truststore : _loadTrustAnchors(truststore)) : []),
        'connectedDevices': (connectedDevices ? connectedDevices : {})
	};
	entries = _signTaStoreContent(entries, taStorePassword);
	var output = JSON.stringify(entries);
	$port.file.store(taStoreFile, output);
};

//////////////////////////////////////////////////////////////////////////////

/** @ignore */
function _isSelfSigned (certificate) {
    return certificate.isIssuer(certificate);
}

/** @ignore */
function _generateSelfSignedCert (privateKey, publicKey, clientId) {
    var cert = forge.pki.createCertificate();
    cert.publicKey = publicKey;
    cert.serialNumber = '01';
    cert.validity.notBefore = new Date();
    cert.validity.notAfter = new Date();
    cert.validity.notAfter.setFullYear(cert.validity.notBefore.getFullYear() + 1);
    var attrs = [{
        name: 'commonName',
        value: clientId
    }];
    cert.setSubject(attrs);
    cert.setIssuer(attrs);
    cert.sign(privateKey);
    return cert;
}

/** @ignore */
function _signTaStoreContent (taStoreEntries, password) {
    var data = '{' + taStoreEntries.clientId + '}'
    	+ '{' + taStoreEntries.serverHost + '}'
    	+ '{' + taStoreEntries.serverPort + '}'
        + '{' + taStoreEntries.serverScheme + '}'
    	+ '{' + taStoreEntries.sharedSecret + '}'
    	+ '{' + taStoreEntries.trustAnchors + '}'
    	+ '{' + (taStoreEntries.keyPair ? taStoreEntries.keyPair : null) + '}'
        + '{' + (taStoreEntries.connectedDevices ? taStoreEntries.connectedDevices : {}) + '}';
    var key = _pbkdf(password);
    var hmac = forge.hmac.create();
	hmac.start('sha256', key);
	hmac.update(data);
    return {
        clientId: taStoreEntries.clientId,
        serverHost: taStoreEntries.serverHost,
        serverPort: taStoreEntries.serverPort,
        serverScheme: taStoreEntries.serverScheme,
        sharedSecret: taStoreEntries.sharedSecret,
        trustAnchors: taStoreEntries.trustAnchors,
        keyPair: (taStoreEntries.keyPair ? taStoreEntries.keyPair : null),
        connectedDevices: (taStoreEntries.connectedDevices ? taStoreEntries.connectedDevices : {}),
        signature: hmac.digest().toHex()
    };
}

/** @ignore */
function _verifyTaStoreContent (taStoreEntries, password) {
    var data = '{' + taStoreEntries.clientId + '}'
	+ '{' + taStoreEntries.serverHost + '}'
	+ '{' + taStoreEntries.serverPort + '}'
    + (taStoreEntries.serverScheme ? ('{' + taStoreEntries.serverScheme + '}') : '')
	+ '{' + taStoreEntries.sharedSecret + '}'
	+ '{' + taStoreEntries.trustAnchors + '}'
	+ '{' + (taStoreEntries.keyPair ? taStoreEntries.keyPair : null) + '}'
    + (taStoreEntries.connectedDevices ? '{' + taStoreEntries.connectedDevices + '}' : '');
    var key = _pbkdf(password);
    var hmac = forge.hmac.create();
    hmac.start('sha256', key);
    hmac.update(data);
	return taStoreEntries.signature && hmac.digest().toHex() === taStoreEntries.signature;
}

/** @ignore */
//PBKDF2 (RFC 2898)
function _pbkdf (password) {
	return forge.pkcs5.pbkdf2(password, '', 1000, 16);
}

/** @ignore */
function _encryptSharedSecret (sharedSecret, password) {
	var key = _pbkdf(password);
	var cipher = forge.cipher.createCipher('AES-CBC', key);
	cipher.start({iv: forge.util.createBuffer(16).fillWithByte(0, 16)});
	cipher.update(forge.util.createBuffer(sharedSecret, 'utf8'));
	cipher.finish();
	return cipher.output.toHex();
}

/** @ignore */
function _decryptSharedSecret (encryptedSharedSecret, password) {
	var key = _pbkdf(password);
	var cipher = forge.cipher.createDecipher('AES-CBC', key);
	cipher.start({iv: forge.util.createBuffer(16).fillWithByte(0, 16)});
	cipher.update(forge.util.createBuffer(forge.util.hexToBytes(encryptedSharedSecret), 'binary'));
	cipher.finish();
	return cipher.output.toString();
}

/** @ignore */
function _loadTrustAnchors (truststore) {
	return $port.file.load(truststore)
		.split(/\-{5}(?:B|E)(?:[A-Z]*) CERTIFICATE\-{5}/)
		.filter(function(elem) { return ((elem.length > 1) && (elem.indexOf('M') > -1)); })
		//.filter(elem => elem.length > 0)
		.map(function(elem) { return '-----BEGIN CERTIFICATE-----' + elem.replace(new RegExp('\r\n', 'g'),'\n') + '-----END CERTIFICATE-----'; });
	    //.map(elem => elem = '-----BEGIN CERTIFICATE-----' + elem + '-----END CERTIFICATE-----');
}
