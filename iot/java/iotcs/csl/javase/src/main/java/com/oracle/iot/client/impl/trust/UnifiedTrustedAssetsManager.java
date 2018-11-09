/*
 * Copyright (c) 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.impl.trust;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

import java.security.cert.X509Certificate;

import java.util.Map;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import com.oracle.iot.client.trust.TrustException;

import com.oracle.iot.client.impl.util.Base64;

/**
 * This class provides an implementation of the {@code TrustedAssetsManager}
 * that stores values as tag-length-value in a Base64 encoded AES encrypted
 * file.
 */
/*
 * Unified client provisioning format:
 *
 * format = version & blob & *comment
 * version = 1 byte, value 33
 * blob = MIME base64 of encrypted & new line
 * encrypted = IV & AES-128/CBC/PKCS5Padding of values
 * IV = 16 random bytes
 * values = *TLV
 * TLV = tag & length & value
 * tag = byte
 * length = 2 byte BE unsigned int
 * value = length bytes
 * comment = # & string & : & string & new line
 * string = UTF-8 chars
 *
 * The password based encryption key is the password processed by 10000
 * interations of PBKDF2WithHmacSHA1 with the IV as the salt.
 */
public class UnifiedTrustedAssetsManager extends TrustedAssetsManagerBase {
    static final int AES_BLOCK_SIZE = 16;
    static final int AES_KEY_SIZE = 128;
    static final int PBKDF2_ITERATIONS = 10000;

    /*
     * To have a simple 1 byte version and be readable outside of the blob,
     * the first version number will be first printable ASCII character 33 (!).
     *
     * Since the last printable char is 126 (~), we can have 94 versions.
     */
    static final byte FORMAT_VERSION = 33;
    static final byte MAX_FORMAT_VERSION = 126;

    /**
     * The URI of the server, e.g., https://iotinst-mydomain.iot.us.oraclecloud.com:443
     */
    static final int SERVER_URI_TAG = 1;

    /** A client id is either an integration id (for enterprise clients), or an
     * activation id (for device clients). An activation id may also be
     * referred to a hardware id.
     */
    static final int CLIENT_ID_TAG = 2;

    /**
     * The shared secret as plain text
     */
    static final int SHARED_SECRET_TAG = 3;

    /**
     * For devices, the endpoint id TLV is omitted from the provisioning file
     * (unless part of a CONNECTED_DEVICE_TAG TLV).
     * For enterpise integrations, the endpoint id is set in the provisioning file
     * by the inclusion of the second ID argument.
     */
    static final int ENDPOINT_ID_TAG = 4;

    /**
     * The trust anchor is the X509 cert
     */
    static final int TRUST_ANCHOR_TAG = 5;

    /** Not used */
    static final int PRIVATE_KEY_TAG = 6;

    /** Not used */
    static final int PUBLIC_KEY_TAG = 7;

    /**
     * The client id and shared secret of a device that can connect
     * indirectly through the device client
     *
     * Connected device TLV =
     * [CONNECTED_DEVICE_TAG|<length>|[CLIENT_ID_TAG|<icd activation id length>|<icd activation id>][SHARED_SECRET_TAG|<icd shared secrect length>|<icd shared secret>]]
     */
    static final int CONNECTED_DEVICE_TAG = 8;

    final static Logger logger = Logger.getAnonymousLogger();
    File taStoreFile;
    String taStorePwd;
    byte[] sharedSecretUtf;

    /**
     * Creates a new {@code TrustedAssetsManager} instance from
     * the provided file path
     *
     * @param path the file path to the store.
     * @param password password used to encrypt store
     * @param context NOT used on this platform
     *
     * @throws TrustException if any error occurs opening and loading the
     * manager
     */
    public UnifiedTrustedAssetsManager(String path, String password,
            Object context) throws TrustException {
        load(new File(path), password);
    }

    // For unit testing. Unit test will call initialize instead of load
    UnifiedTrustedAssetsManager() {}

    /**
     * Loads a new {@code UnifiedTrustedAssetsManager} instance from
     * the provided file path.
     *
     * @param path the file path to the store.
     * @param password store password 
     *
     * @throws TrustException if any error occurs opening and loading the
     * manager
     */
    void load(File path, String password) throws TrustException {
        taStoreFile = path;
        taStorePwd = password;
        byte[] base64;
        FileInputStream fis;
        
        if (path == null) {
            throw new TrustException("Path is null");
        }

        if (password == null) {
            throw new TrustException("Password is null");
        }

        try {
            base64 = new byte[(int)taStoreFile.length()];
            fis = new FileInputStream(taStoreFile);
        } catch (java.io.FileNotFoundException fnfe) {
            logger.log(Level.SEVERE, taStoreFile + " not found");
            throw new TrustException("Error loading trusted assets...",
                                     fnfe);
        }

        try {
            fis.read(base64);
            initialize(password, base64);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "caught '" + e +
               "' while loading trusted assets file " + taStoreFile);
            throw new TrustException("Error loading trusted assets file " +
                taStoreFile, e);
        } finally {
            try {
                fis.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    /**
     * Decodes the Base64 and then decrypts trusted assets.
     *
     * @param password key to use for decryption
     * @param base64 Base64 encoded / AES encrypted trusted assets
     *
     * @throws Exception if the properties cannot be decoded
     */
    void initialize(String password, byte[] base64) throws Exception {
        taStorePwd = password;

        // The first char is really the version
        if (base64[0] != FORMAT_VERSION) {
            throw new Exception("Unknown trusted asset store version");
        }

        // find the comment line, if one, after the Base64 lines
        int base64Len;
        for (base64Len = 1; base64Len < base64.length; base64Len++) {
            if (base64[base64Len] == (byte)'#') {
                break;
            }
        }

        // The server's sun.misc.BASE64Encoder is MIME encoder
        byte[] cipherText =
            Base64.getMimeDecoder().decode(base64, 1, base64Len - 1);

        // Copy the salt, since the PBE key factory does not take an salt length
        byte[] salt = new byte[AES_BLOCK_SIZE];
        System.arraycopy(cipherText, 0, salt, 0, salt.length);
        SecretKey key = createKey(password, salt);
        byte[] plainText = decrypt(key, cipherText);

        TLV tlv = null;
        int length = plainText.length;
        for (int offset = 0; offset < length; offset = tlv.offsetToNext) {
            tlv = new TLV(plainText, offset);
            switch (tlv.tag) {
            case SERVER_URI_TAG:
                setServer(new String(tlv.value));
                continue;

            case CLIENT_ID_TAG:
                clientId = new String(tlv.value);
                continue;
                
            case SHARED_SECRET_TAG:
                sharedSecretUtf = tlv.value;
                setSharedSecret(sharedSecretUtf);
                continue;
                
            case ENDPOINT_ID_TAG:
                endpointId = new String(tlv.value);
                continue;

            case TRUST_ANCHOR_TAG:
                addTrustAnchor(tlv.value);
                continue;

            case PRIVATE_KEY_TAG:
                setPrivateKey(tlv.value);
                continue;

            case PUBLIC_KEY_TAG:
                setPublicKey(tlv.value);
                continue;

            case CONNECTED_DEVICE_TAG: {
                final TLV hardwareId = new TLV(tlv.value, 0);
                final TLV sharedSecret = new TLV(tlv.value, hardwareId.offsetToNext);
                addSharedSecret(new String(hardwareId.value), sharedSecret.value);
                continue;
            }
            default:
                logger.log(Level.FINEST, "Unknown value tag " + tlv.tag);
            }   
        }
    }

    String getSharedSecret() {
        return new String(sharedSecretUtf);
    }

    @Override
    protected void store() throws Exception {
        X509Certificate trustAnchor = null;
        if (trustAnchors != null) {
            for (X509Certificate anchor: trustAnchors) {
                trustAnchor = anchor;
                break;
            }
        }

        byte[] tas = createTas(taStorePwd, serverScheme, serverHost, serverPort,
           clientId, new String(sharedSecretUtf), endpointId, trustAnchor,
           privateKey, publicKey, icdMap);

        FileOutputStream fos = new FileOutputStream(taStoreFile);
        try {
            fos.write(tas);
            fos.flush();
        } finally {
            try {
                fos.close();
            } catch (IOException ioe) {
            }
        }
    }

    static byte[] createTas(String password, String serverScheme,
                            String serverHost, int serverPort, String clientId,
                            String sharedSecret, String endpointId, X509Certificate trustAnchor,
                            PrivateKey privateKey, PublicKey publicKey, Map<String, SecretKey> icdMap) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        StringBuffer uri = new StringBuffer(serverScheme);
        uri.append("://");
        uri.append(serverHost);

        if (serverPort >= 0) {
            uri.append(':');
            uri.append(serverPort);
        }

        byte[] uriUtf = uri.toString().getBytes("UTF-8");
        TLV.writeValue(bos, SERVER_URI_TAG, uriUtf);

        byte[] clientIdUtf = clientId.getBytes("UTF8");
        TLV.writeValue(bos, CLIENT_ID_TAG, clientIdUtf);

        TLV.writeValue(bos, SHARED_SECRET_TAG, sharedSecret.getBytes("UTF8"));

        if (endpointId != null) {
            TLV.writeValue(bos, ENDPOINT_ID_TAG, endpointId.getBytes("UTF8"));
        }

        if (trustAnchor != null) {
            TLV.writeValue(bos, TRUST_ANCHOR_TAG, trustAnchor.getEncoded());
        }

        if (privateKey != null) {
            TLV.writeValue(bos, PRIVATE_KEY_TAG, privateKey.getEncoded());
        }

        if (publicKey != null) {
            TLV.writeValue(bos, PUBLIC_KEY_TAG, publicKey.getEncoded());
        }

        if (icdMap != null) {
            ByteArrayOutputStream icdOutputStream = new ByteArrayOutputStream();
            for(Map.Entry<String,SecretKey> entry : icdMap.entrySet()) {
                TLV.writeValue(icdOutputStream, CLIENT_ID_TAG, entry.getKey().getBytes("UTF-8"));
                TLV.writeValue(icdOutputStream, SHARED_SECRET_TAG, entry.getValue().getEncoded());
                TLV.writeValue(bos, CONNECTED_DEVICE_TAG, icdOutputStream.toByteArray());
                icdOutputStream.reset();
            }
        }

        byte[] values = bos.toByteArray();
        byte[] iv = generateIv();
        SecretKey key = createKey(password, iv /* IV is also the salt */);
        byte[] cipherText = encrypt(key, iv, values);

        // The server's sun.misc.BASE64Encoder is MIME encoder, so match it
        byte[] base64 = Base64.getMimeEncoder().encode(cipherText);

        bos.reset();

        bos.write(FORMAT_VERSION);
        bos.write(base64);
        bos.write("\n#serverUri:".getBytes());
        bos.write(uriUtf);
        bos.write("\n#clientId:".getBytes());
        bos.write(clientIdUtf);
        bos.write("\n".getBytes());
        return bos.toByteArray();
    }

    /**
     * Generates a AES initialization vector (IV).
     *
     * @return 16 bytes of random data
     */
    static byte[] generateIv() {
        SecureRandom random = new SecureRandom();
        byte iv[] = new byte[AES_BLOCK_SIZE];
        random.nextBytes(iv);
        return iv;
    }

    /**
     * Creates an AES key from a passphase.
     *
     * @param password password as {@code String}
     * @param salt random bytes generated during the encryption process
     *
     * @return the key
     */
    static SecretKey createKey(String password, byte[] salt) {
        try {
            PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt,
                PBKDF2_ITERATIONS, AES_KEY_SIZE);
            SecretKeyFactory keyFactory =
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            SecretKey temp = keyFactory.generateSecret(keySpec);
            return new SecretKeySpec(temp.getEncoded(), "AES");
        } catch (Exception e) {
            // Should not happen if above code is correct, but just in case
            throw new RuntimeException(e);
        }
    }

    /**
     * Encrypts the plainText array using the key.
     *
     * @param key key to use for encryption
     * @param iv 16 byte initialization vector
     * @param plainText input data array to be encrypted
     * @return IV + encrypted array
     */
    static byte[] encrypt(SecretKey key, byte[] iv, byte[] plainText) {
        return encrypt(key, iv, plainText, 0, plainText.length);
    }

    /**
     * Encrypts the plainText array using the key.
     *
     * @param key key to use for encryption
     * @param iv 16 byte initialization vector
     * @param plainText input data array to be encrypted
     * @param offset offset into plainText array to start encryption
     * @param plainTextLength length of enciphered data to be decrypted
     *
     * @return IV + encrypted array
     */
    static byte[] encrypt(SecretKey key, byte[] iv, byte[] plainText,
            int offset, int plainTextLength) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));

            byte[] cipherText =
                new byte[iv.length + cipher.getOutputSize(plainTextLength)];

            System.arraycopy(iv, 0, cipherText, 0, iv.length);

            cipher.doFinal(plainText, offset, plainTextLength,
                cipherText, iv.length);

            return cipherText;
        } catch (Exception e) {
            // Should not happen if above code is correct, but just in case
            throw new RuntimeException(e);
        }
    }

    /**
     * Decrypts the plainText array using the key.
     *
     * @param key key to use for decryption
     * @param cipherText input data array to be decrypted
     *
     * @return decrypted array
     */
    static byte[] decrypt(SecretKey key, byte[] cipherText) {
        return decrypt(key, cipherText, 0, cipherText.length);
    }

    /**
     * Decrypts the plainText array using the key.
     *
     * @param key key to use for decryption
     * @param cipherText input data array to be decrypted
     * @param offset offset into cipherText array to start decryption
     * @param cipherTextLength length of enciphered data to be decrypted
     *
     * @return decrypted array
     */
    static byte[] decrypt(SecretKey key, byte[] cipherText, int offset,
            int cipherTextLength) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

            // IV is the first block
            cipher.init(Cipher.DECRYPT_MODE, key,
                new IvParameterSpec(cipherText, 0, AES_BLOCK_SIZE));

            return cipher.doFinal(cipherText, offset + AES_BLOCK_SIZE,
                cipherTextLength - AES_BLOCK_SIZE);
        } catch (Exception e) {
            // Should not happen if above code is correct, but just in case
            throw new RuntimeException(e);
        }
    }

    /**
     * Holds a property as tag, length, and value. 
     */
    static class TLV {
        int tag;
        int length;
        byte[] value;
        int offsetToNext;

        TLV(byte[] buffer, int offset) {
            tag = ((int)buffer[offset]) & 0x000000FF;
            offset++;

            length = ((int)buffer[offset]) & 0x000000FF;
            length = length << 8;
            offset++;
            length += ((int)buffer[offset]) & 0x000000FF;
            offset++;

            value = new byte[length];
            System.arraycopy(buffer, offset, value, 0, length);
            offsetToNext = offset + length;
            return;
        }

        static void writeValue(OutputStream os, int tag, byte[] value)
                throws IOException {
            os.write(tag);
            os.write(value.length >>> 8);
            os.write(value.length);
            os.write(value);
        }
    }


    private static int decodeInt(byte[] buf) {

        // NOTE: assuming buf.length <= INTEGER_BYTES!
        int ival = 0;
        for (int n=0; n<buf.length; n++) {
            ival <<= 8;
            ival |= (buf[n] & 0xFF);
        }
        return ival;
    }

    private final static int INTEGER_BYTES = Integer.SIZE / Byte.SIZE;

    private static byte[] encodeInt(int ival) {

        final byte[] buf = new byte[INTEGER_BYTES];
        for (int n=INTEGER_BYTES-1; 0 <= n; n--) {
            int byteval = (ival & 0xff);
            buf[n] = (byte)byteval;
            ival >>>= 8;
        }
        return buf;
    }

}
