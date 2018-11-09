/*
 * Copyright (c) 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device.mqtt;

import com.oracle.iot.client.impl.util.Base64;
import com.oracle.iot.client.trust.TrustException;
import com.oracle.iot.client.trust.TrustedAssetsManager;

import java.nio.charset.Charset;
import java.security.GeneralSecurityException;

/**
 * MqttCredentials is a singleton that handles creating client credentials
 * for MQTT connections.
 */
final class MqttCredentials {

    // Only access is static interface
    private MqttCredentials() {
    }

    private static final Charset UTF_8                            = Charset.forName("UTF-8");
    private static final String  DEFAULT_MESSAGE_DIGEST_ALGORITHM = "HmacSHA256";

    // JWT expiration claim adjustment value, in milliseconds. This is added
    // to the current millisecond time since January 1, 1970 in order to get
    // some time "not to far" in the future for the JWT "exp" claim.
    private final static long EXP_CLAIM_DELTA = 15L * 1000L * 60L; // 15 minutes

    // HACK: Create some overloaded methods to be able to call
    // buildClientAssertion with extra parameters to contol whether or
    // not the credentials are constructed with the shared secret or not.

    static char[] getClientAssertionCredentials(TrustedAssetsManager trustedAssetsManager)
            throws GeneralSecurityException {

        String assertion = buildClientAssertion(trustedAssetsManager);
        return assertion.toCharArray();
    }

    static char[] getClientAssertionCredentials(TrustedAssetsManager trustedAssetsManager, final String id, final boolean useSharedSecret)
            throws GeneralSecurityException {

        String assertion = buildClientAssertion(trustedAssetsManager, id,
            useSharedSecret);
        return assertion.toCharArray();
    }

    private static String buildClientAssertion(TrustedAssetsManager trustedAssetsManager)
            throws GeneralSecurityException {

        final String id;
        final boolean useSharedSecret;
        if (!trustedAssetsManager.isActivated()) {
            id = trustedAssetsManager.getClientId();
            useSharedSecret = true;
        } else {
            id = trustedAssetsManager.getEndpointId();
            useSharedSecret = false;
        }
        return buildClientAssertion(trustedAssetsManager, id, useSharedSecret);
    }

    private static String buildClientAssertion(TrustedAssetsManager trustedAssetsManager, final String id, final boolean useSharedSecret)
            throws GeneralSecurityException {

        // Expiration claim is in units of seconds since January 1, 1970 UTC.
        // Note that EXP_CLAIM_DELTA is in units of milliseconds.
        final long exp = (System.currentTimeMillis() + EXP_CLAIM_DELTA) / 1000L;

        final String alg = useSharedSecret ?  "HS256" : "RS256";

        final String header = "{\"typ\":\"JWT\",\"alg\":\"" + alg + "\"}";
        final String claims = "{\"iss\":\"" + id + "\"" +
                ", \"sub\":\"" + id + "\"" +
                ", \"aud\":\"oracle/iot/oauth2/token\"" +
                ", \"exp\":" + exp  +
                "}";

        StringBuilder inputToSign = new StringBuilder();

        inputToSign.append(Base64.getUrlEncoder().encodeToString(header.getBytes(UTF_8)));
        inputToSign.append(".");
        inputToSign.append(Base64.getUrlEncoder().encodeToString(claims.getBytes(UTF_8)));

        byte[] bytesToSign = inputToSign.toString().getBytes(UTF_8);
        byte[] signedBytes = null;
        try {
            if (useSharedSecret) {
                signedBytes= trustedAssetsManager.signWithSharedSecret(bytesToSign, DEFAULT_MESSAGE_DIGEST_ALGORITHM, null);
            } else {
                signedBytes= trustedAssetsManager.signWithPrivateKey(bytesToSign, "SHA256withRSA");
            }
        } catch (TrustException e) {
            throw new GeneralSecurityException(e.getMessage(), e);
        }
        String signature = Base64.getUrlEncoder().encodeToString(signedBytes);

        inputToSign.append(".");
        inputToSign.append(signature);
        return inputToSign.toString();
    }

}
