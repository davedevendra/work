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
 * @alias iotcs.enterprise.TrustedAssetsManager
 * @class
 * @memberof iotcs.enterprise
 *
 * @param {string} [taStoreFile] - The trusted assets store file path to be used for trusted assets
 *        manager creation.  This is optional.  If none is given the default global library
 *        parameter is used: lib.oracle.iot.tam.store.
 * @param {string} [taStorePassword] - The trusted assets store file password to be used for trusted
 *        assets manager creation.  This is optional.  If none is given the default global library
 *        parameter is used: lib.oracle.iot.tam.storePassword.
 */
lib.enterprise.TrustedAssetsManager = function (taStoreFile,taStorePassword) {

    this.clientId = null;
    this.sharedSecret = null;
    this.serverHost = null;
    this.serverPort = null;

    this.trustAnchors = null;

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
            this.sharedSecret = (entries.sharedSecret ? _decryptSharedSecret(entries.sharedSecret, _taStorePassword) : null);
            this.trustAnchors = entries.trustAnchors;

        };

        this.load();
    }
};

/**
 * Retrieves the IoT CS server host name.
 *
 * @function getServerHost
 * @memberof iotcs.enterprise.TrustedAssetsManager.prototype
 *
 * @returns {?string} The IoT CS server host name, or <code>null</code> if any error occurs
 *          retrieving the server host name.
 */
lib.enterprise.TrustedAssetsManager.prototype.getServerHost = function () {
    return this.serverHost;
};

/**
 * Retrieves the IoT CS server port.
 *
 * @function getServerPort
 * @memberof iotcs.enterprise.TrustedAssetsManager.prototype
 *
 * @returns {?number} The IoT CS server port (a positive integer), or <code>null</code> if any error
 *          occurs retrieving the server port.
 */
lib.enterprise.TrustedAssetsManager.prototype.getServerPort = function () {
    return this.serverPort;
};

/**
 * Retrieves the ID of this client.  If the client is a device, the client ID is the device ID.  If
 * the client is a pre-activated enterprise application, the client ID corresponds to the assigned
 * endpoint ID.  The client ID is used along with a client secret derived from the shared secret to
 * perform secret-based client authentication with the IoT CS server.
 *
 * @function getClientId
 * @memberof iotcs.enterprise.TrustedAssetsManager.prototype
 *
 * @returns {?string} The ID of this client, or <code>null</code> if any error occurs retrieving the
 *          client ID.
 */
lib.enterprise.TrustedAssetsManager.prototype.getClientId = function () {
    return this.clientId;
};

/**
 * Retrieves the trust anchor or most-trusted Certification Authority (CA) to be used to validate
 * the IoT CS server certificate chain.
 *
 * @function getTrustAnchorCertificates
 * @memberof iotcs.enterprise.TrustedAssetsManager.prototype
 *
 * @returns {?Array} The PEM-encoded trust anchor certificates, or <code>null</code> if any error
 *          occurs retrieving the trust anchor.
 */
lib.enterprise.TrustedAssetsManager.prototype.getTrustAnchorCertificates = function () {
    return this.trustAnchors;
};

/**
 * Signs the provided data using the specified algorithm and the shared secret.  This method is only
 * use for secret-based client authentication with the IoT CS server.
 *
 * @function signWithSharedSecret
 * @memberof iotcs.enterprise.TrustedAssetsManager.prototype
 *
 * @param {Array} data - The bytes to be signed.
 * @param {string} algorithm - The hash algorithm to use.
 * @return {?Array} - The signature bytes, or <code>null</code> if any error occurs retrieving the
 *         necessary key material or performing the operation.
 */
lib.enterprise.TrustedAssetsManager.prototype.signWithSharedSecret = function (data, algorithm) {
    var digest = null;
    if (!algorithm) {
        lib.error('Algorithm cannot be null');
        return null;
    }
    if (!data) {
        lib.error('Data cannot be null');
        return null;
    }
    try {
        var hmac = forge.hmac.create();
        hmac.start(algorithm, this.sharedSecret);
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
 * Provisions the designated Trusted Assets Store with the provided provisioning assets.  The
 * provided shared secret will be encrypted using the provided password.
 *
 * @memberof iotcs.enterprise.TrustedAssetsManager
 * @function provision
 *
 * @param {string} taStoreFile - The Trusted Assets Store file name.
 * @param {string} taStorePassword - The Trusted Assets Store password.
 * @param {string} serverHost - The IoT CS server host name.
 * @param {number} serverPort - The IoT CS server port.
 * @param {?string} clientId - The ID of the client.
 * @param {?string} sharedSecret - The client's shared secret.
 * @param {?string} truststore - The truststore file containing PEM-encoded trust anchors
 *        certificates to be used to validate the IoT CS server certificate chain.
 */
lib.enterprise.TrustedAssetsManager.provision = function (taStoreFile, taStorePassword, serverHost, serverPort, clientId, sharedSecret, truststore) {
    if (!taStoreFile) {
        throw 'No TA Store file provided';
    }
    if (!taStorePassword) {
        throw 'No TA Store password provided';
    }
    var entries = {};
    entries.serverHost = serverHost;
    entries.serverPort = serverPort;
    if (clientId) {
        entries.clientId = clientId;
    }
    if (sharedSecret) {
        entries.sharedSecret = _encryptSharedSecret(sharedSecret, taStorePassword);
    }
    if (truststore) {
        entries.trustAnchors = (Array.isArray(truststore) ? truststore : _loadTrustAnchors(truststore));
    }
    entries = _signTaStoreContent(entries, taStorePassword);
    var output = JSON.stringify(entries);
    $port.file.store(taStoreFile, output);
};

//////////////////////////////////////////////////////////////////////////////

/** @ignore */
function _signTaStoreContent (taStoreEntries, password) {
    var data = (taStoreEntries.clientId ? ('{' + taStoreEntries.clientId + '}') : '')
        + '{' + taStoreEntries.serverHost + '}'
        + '{' + taStoreEntries.serverPort + '}'
        + (taStoreEntries.sharedSecret ? ('{' + taStoreEntries.sharedSecret + '}') : '')
        + (taStoreEntries.trustAnchors ? ('{' + taStoreEntries.trustAnchors + '}') : '');
    var key = _pbkdf(password);
    var hmac = forge.hmac.create();
    hmac.start('sha256', key);
    hmac.update(data);
    var ret = {};
    if (taStoreEntries.clientId) {
        ret.clientId = taStoreEntries.clientId;
    }
    ret.serverHost = taStoreEntries.serverHost;
    ret.serverPort = taStoreEntries.serverPort;
    if (taStoreEntries.sharedSecret) {
        ret.sharedSecret = taStoreEntries.sharedSecret;
    }
    if (taStoreEntries.trustAnchors) {
        ret.trustAnchors = taStoreEntries.trustAnchors;
    }
    ret.signature = hmac.digest().toHex();
    return ret;
}

/** @ignore */
function _verifyTaStoreContent (taStoreEntries, password) {
    var data = (taStoreEntries.clientId ? ('{' + taStoreEntries.clientId + '}') : '')
        + '{' + taStoreEntries.serverHost + '}'
        + '{' + taStoreEntries.serverPort + '}'
        + (taStoreEntries.sharedSecret ? ('{' + taStoreEntries.sharedSecret + '}') : '')
        + (taStoreEntries.trustAnchors ? ('{' + taStoreEntries.trustAnchors + '}') : '');
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
