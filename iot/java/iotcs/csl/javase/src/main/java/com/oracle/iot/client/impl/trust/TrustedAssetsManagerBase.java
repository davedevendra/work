/*
 * Copyright (c) 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.impl.trust;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.net.URI;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

import javax.crypto.spec.SecretKeySpec;

import com.oracle.iot.client.trust.TrustException;
import com.oracle.iot.client.trust.TrustedAssetsManager;

/**
 * This class provides a shared code for implementations of the
 * {@code TrustedAssetsManager} interface.
 */
public abstract class TrustedAssetsManagerBase implements TrustedAssetsManager {
    public static final String TA_STORE_PROPERTY =
        "oracle.iot.client.trustedAssetsStore";
    public static final String TA_STORE_PASSWORD_PROPERTY =
        "oracle.iot.client.trustedAssetsStorePassword";

    protected String clientId;
    protected SecretKey sharedSecret;
    protected String serverScheme;
    protected String serverHost;
    protected int serverPort = -1;
    protected String endpointId;
    protected PrivateKey privateKey;
    protected PublicKey publicKey;
    protected Set<X509Certificate> trustAnchors = null;
    protected Map<String,SecretKey> icdMap;

    protected TrustedAssetsManagerBase() {}

    @Override
    public String getServerScheme() {
        return serverScheme;
    }

    @Override
    public String getServerHost() {
        return serverHost;
    }

    @Override
    public int getServerPort() {
        return serverPort;
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    @Override
    public PublicKey getPublicKey() {
        // Not all managers persistently store the generated public key
        if (privateKey == null) {
            throw new IllegalStateException("Key pair not yet generated.");
        }

        return publicKey;
    }

    @Override
    public Vector<byte[]> getTrustAnchorCertificates() {
        Vector<byte[]> anchors = new Vector<byte[]>();

        if (trustAnchors != null) {
            for (X509Certificate anchor: trustAnchors) {
                try {
                    anchors.addElement(anchor.getEncoded());
                } catch (CertificateEncodingException e) {
                    // skip erroneous certificate
                }
            }
        }

        return anchors;
    }

    @Override
    public void setEndPointCredentials(String endpointId, byte[] certificate)
            throws TrustException {
        if (privateKey == null) {
            throw new IllegalStateException("Key pair not yet generated.");
        }

        this.endpointId = endpointId;
        try {
            store();
        } catch (Exception e) {
            throw new TrustException("Error storing the trusted assets...", e);
        }
    }

    @Override
    public String getEndpointId() {
        if (endpointId == null) {
            throw new IllegalStateException("EndpointId not assigned.");
        }
        return endpointId;
    }

    @Override
    public byte[] getEndpointCertificate() {
        if (!isActivated()) {
            throw new IllegalStateException("Endpoint not activated.");
        }

        return new byte[0];
    }

    @Override
    public void generateKeyPair(String algorithm, int keySize)
            throws TrustException {
        if (endpointId != null) {
            throw new IllegalStateException(
                "Already activated: EndpointId already assigned.");
        }

        if (algorithm == null) {
            throw new NullPointerException(
                "Algorithm cannot be null.");
        }

        if (keySize <= 0) {
            throw new IllegalArgumentException(
                "Key size cannot be negative or 0.");
        }

        KeyPairGenerator keyPairGenerator;
        try {
            try {
                keyPairGenerator = KeyPairGenerator.getInstance(algorithm);
            } catch (NoSuchAlgorithmException nsae) {
                throw new TrustException(
                    "Can't find public key algorithm " + algorithm, nsae);
            }
        } catch (GeneralSecurityException e) {
            throw new TrustException(e.getMessage(), e);
        }

        keyPairGenerator.initialize(keySize);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        privateKey = keyPair.getPrivate();
        publicKey = keyPair.getPublic();
    }

    @Override
    public byte[] signWithPrivateKey(byte[] data, String algorithm)
            throws TrustException {
        if (privateKey == null) {
            throw new IllegalStateException("key not yet generated.");
        }

        if (algorithm == null) {
            throw new NullPointerException("Algorithm cannot be null.");
        }
        if (data == null) {
            throw new NullPointerException("Data cannot be null.");
        }

        byte[] sig;
        try {
            Signature signature;
            try {
                signature = Signature.getInstance(algorithm);
            } catch (NoSuchAlgorithmException nsae) {
                throw new TrustException(
                    "Can't find signing algorithm " + algorithm, nsae);
            }

            signature.initSign(privateKey);
            signature.update(data);
            sig = signature.sign();
        } catch (GeneralSecurityException e) {
            throw new TrustException("Error signing with key...", e);
        }
        return sig;
    }

    @Deprecated
    @Override
    final public byte[] getEncryptedSharedSecret() throws TrustException {
        throw new TrustException("Unsupported operation...");
    }

    @Override
    public byte[] signWithSharedSecret(byte[] data, String algorithm, String hardwareId)
            throws TrustException {

        if (algorithm == null) {
            throw new NullPointerException("Algorithm cannot be null.");
        }

        if (data == null) {
            throw new NullPointerException("Data cannot be null.");
        }

        SecretKey secretKey = null;
        if (hardwareId == null || hardwareId.equals(getClientId())) {
            secretKey = sharedSecret;
        } else if (icdMap != null){
            secretKey = icdMap.get(hardwareId);
        }

        if (secretKey == null) {
            throw new TrustException("Shared secret not provisioned.");
        }

        byte[] digest;
        try {
            Mac mac;
            try {
                mac = Mac.getInstance(algorithm);
            } catch (NoSuchAlgorithmException nsae) {
                throw new TrustException(
                    "Can't find signing algorithm " + algorithm, nsae);
            }

            mac.init(secretKey);
            mac.update(data);
            digest = mac.doFinal();
        } catch (GeneralSecurityException e) {
            throw new TrustException("Error signing with shared secret...", e);
        }
        return digest;
    }

    @Override
    public boolean isActivated() {
        return endpointId != null;
    }

    @Override
    public void reset() throws TrustException {
        endpointId = null;
        privateKey = null;
        publicKey = null;
        try {
            store();
        } catch (Exception e) {
            throw new 
                TrustException("Error resetting the trusted assets...", e);
        }
    }

    @Override
    public void close() throws IOException {
    }


    protected void addSharedSecret(String activationId, byte[] sharedSecret) {
        if (icdMap == null) {
            icdMap = new HashMap<String, SecretKey>();
        }
        final SecretKey secretKey = new SecretKeySpec(sharedSecret, "Hmac");
        icdMap.put(activationId, secretKey);
    }

    /**
     * Called to store the fields to persisent storage.
     *
     * @throws Exception if the content cannot be stored
     */
    protected abstract void store() throws Exception;

    /**
     * Sets the server from a URI string.
     *
     * @param uriString URI
     *
     * @throws Exception if the URI cannot be parsed
     */
    protected void setServer(String uriString) throws Exception {
        URI uri = new URI(uriString);

        serverScheme = uri.getScheme();
        if (!("https".equals(serverScheme))) {
            System.setProperty(DISABLE_LONG_POLLING_PROPERTY, "true");
        } else {
            // Treat -Dcom.oracle.iot.client.disable_long_polling
            // the same as -Dcom.oracle.iot.client.disable_long_polling=true
            final String value = (String) System.getProperty("com.oracle.iot.client.disable_long_polling");
            if ("".equals(value)) {
                // If value is an empty String, then property was defined, but not assigned
                System.setProperty(DISABLE_LONG_POLLING_PROPERTY, "true");
            }
        }
        serverHost = uri.getHost();
        serverPort = uri.getPort();
    }
        
    /**
     * Sets the shared secret key from a ASCII bytes.
     *
     * @param sharedSecretString shared secret or {@code null}
     */
    protected void setSharedSecret(byte[] sharedSecretString) {
        if (sharedSecretString == null) {
            return;
        }

        sharedSecret = new SecretKeySpec(sharedSecretString, "Hmac");
    }

    /**
     * Adds a trust anchor.
     *
     * @param encodedCert X509 encoded certificate
     *
     * @throws Exception if the cert cannot be decoded
     */
    protected void addTrustAnchor(byte[] encodedCert) throws Exception {
        if (trustAnchors == null) {
            trustAnchors = new HashSet<X509Certificate>();
        }

        ByteArrayInputStream bis =
            new ByteArrayInputStream(encodedCert);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        trustAnchors.add((X509Certificate)cf.generateCertificate(bis));
    }

    /**
     * Sets the private key from PCKS8 encoding.
     *
     * @param encodedKey encoded key
     *
     * @throws Exception if the key cannot be decoded
     */
    protected void setPrivateKey(byte[] encodedKey) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        KeySpec ks = new PKCS8EncodedKeySpec(encodedKey);
        privateKey = (PrivateKey)keyFactory.generatePrivate(ks);
    }

    /**
     * Sets the public key from X.509 encoding.
     *
     * @param encodedKey encoded key
     *
     * @throws Exception if the key cannot be decoded
     */
    protected void setPublicKey(byte[] encodedKey) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        KeySpec ks = new X509EncodedKeySpec(encodedKey);
        publicKey = (PublicKey)keyFactory.generatePublic(ks);
    }
}
