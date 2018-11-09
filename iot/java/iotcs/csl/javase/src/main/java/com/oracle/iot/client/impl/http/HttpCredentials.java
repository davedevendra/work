/*
 * Copyright (c) 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.http;

import com.oracle.iot.client.impl.TimeManager;
import com.oracle.iot.client.impl.util.Base64;
import com.oracle.iot.client.trust.TrustException;
import com.oracle.iot.client.trust.TrustedAssetsManager;

import java.nio.charset.Charset;
import java.security.GeneralSecurityException;

/**
 * Credentials is a singleton that handles client credentials
 */
final class HttpCredentials {

    // Only access is static interface
    private HttpCredentials() {
    }

    static byte[] getClientAssertionCredentials(TrustedAssetsManager tam, boolean useOnlySharedSecret)
            throws GeneralSecurityException {
        return getAssertionCredentialsPostData(tam, useOnlySharedSecret);
    }

    // JWT expiration claim adjustment value, in milliseconds. This is added
    // to the current millisecond time since January 1, 1970 in order to get
    // some time "not to far" in the future for the JWT "exp" claim.
    private final static long EXP_CLAIM_DELTA = 15L * 1000L * 60L; // 15 minutes

    private static final Charset UTF_8                            = Charset.forName("UTF-8");
    private static final String  DEFAULT_MESSAGE_DIGEST_ALGORITHM = "HmacSHA256";


    /*package for testing*/
    private static byte[] getAssertionCredentialsPostData(TrustedAssetsManager trustedAssetsManager, boolean useOnlySharedSecret)
            throws GeneralSecurityException{

        final String scope = trustedAssetsManager.isActivated() ? "" : "oracle/iot/activation";
        StringBuilder postData = new StringBuilder();
        postData.append("grant_type=client_credentials");
        postData.append("&client_assertion_type=urn%3Aietf%3Aparams%3Aoauth%3Aclient-assertion-type%3Ajwt-bearer"); // already url-encoded
        postData.append("&client_assertion=" + buildClientAssertion(trustedAssetsManager, useOnlySharedSecret));
        postData.append("&scope=" + scope);
        
        String dataString = postData.toString();
        return dataString.getBytes(UTF_8);
    }

    /*package for testing*/
    private static String buildClientAssertion(TrustedAssetsManager trustedAssetsManager, boolean useOnlySharedSecret)
            throws GeneralSecurityException {

        // Expiration claim is in units of seconds since January 1, 1970 UTC.
        // Note that EXP_CLAIM_DELTA is in units of milliseconds.
        final long exp = (TimeManager.currentTimeMillis() + EXP_CLAIM_DELTA) / 1000L;

        final String id;
        if (!useOnlySharedSecret && !trustedAssetsManager.isActivated()) {
        	id = trustedAssetsManager.getClientId();
        } else {
        	id = trustedAssetsManager.getEndpointId();
    	}
        
        final boolean useSharedSecret = useOnlySharedSecret || !trustedAssetsManager.isActivated();
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

        if (useSharedSecret) {
            signedBytes= trustedAssetsManager.signWithSharedSecret(bytesToSign, DEFAULT_MESSAGE_DIGEST_ALGORITHM, null);
        } else {
            signedBytes= trustedAssetsManager.signWithPrivateKey(bytesToSign, "SHA256withRSA");
        }

        String signature = Base64.getUrlEncoder().encodeToString(signedBytes);

        inputToSign.append(".");
        inputToSign.append(signature);
        return inputToSign.toString();
    }
}
