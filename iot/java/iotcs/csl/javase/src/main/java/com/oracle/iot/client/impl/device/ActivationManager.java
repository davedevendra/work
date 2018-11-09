/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import com.oracle.iot.client.TransportException;
import com.oracle.iot.client.RestApi;
import com.oracle.iot.client.HttpResponse;
import com.oracle.iot.client.SecureConnection;
import com.oracle.iot.client.trust.TrustException;
import com.oracle.iot.client.trust.TrustedAssetsManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ActivationManager handles client activation
 */
public final class ActivationManager {

    public static final String MESSAGE_DISPATCHER_URN =
        "urn:oracle:iot:dcd:capability:message_dispatcher";
    public static final String DIAGNOSTICS_URN =
        "urn:oracle:iot:dcd:capability:diagnostics";
    public static final String DIRECT_ACTIVATION_URN =
        "urn:oracle:iot:dcd:capability:direct_activation";
    public static final String INDIRECT_ACTIVATION_URN =
        "urn:oracle:iot:dcd:capability:indirect_activation";

	private static final Charset UTF_8 = Charset.forName("UTF-8");
	private static final String DEFAULT_MESSAGE_DIGEST_ALGORITHM = "HmacSHA256";

    /**
     * REST resource for activation policy
     */
    private final static String REST_ACTIVATION_POLICY =
        RestApi.V2.getReqRoot()+"/activation/policy";
    /**
     * REST resource for direct activation of a device
     */
    private final static String REST_DIRECT_ACTIVATION;
    /**
     * REST resource for indirect activation of a device
     */
    private final static String REST_INDIRECT_ACTIVATION;

    static {
        String query = "?createDraft=false";

        if (Boolean.getBoolean(
            "com.oracle.iot.client.device.allow_draft_device_models")) {
            // The server default for createDraft is true
            query = "";
        }

        REST_DIRECT_ACTIVATION =
            RestApi.V2.getReqRoot()+"/activation/direct" + query;
        REST_INDIRECT_ACTIVATION =
            RestApi.V2.getReqRoot()+"/activation/indirect/device" + query;
    }

    public static ActivationPolicyResponse getActivationPolicy(
            SecureConnection secureConnection, String deviceId)
            throws IOException, GeneralSecurityException {


		final ActivationPolicyRequest policyRequest = createActivationPolicyRequest();

		final String restRsc = REST_ACTIVATION_POLICY + policyRequest.toQuery();

		final HttpResponse response = secureConnection.get(restRsc);

		int status = response.getStatus();
		if (status == 401) {
			// Assume the endpoint is already activated.
			throw new IllegalStateException(deviceId);
		}
		if (status != 200) {
			throw new TransportException(status, response.getVerboseStatus("GET", restRsc));
		}


		String jsonResponse = new String(response.getData(), "UTF-8");
		JSONObject json;
		try {
			json = new JSONObject(jsonResponse);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}

		ActivationPolicyResponse activationPolicyResponse = ActivationPolicyResponse.fromJson(json);
		if (getLogger().isLoggable(Level.FINEST)) {
			getLogger().log(Level.FINEST, activationPolicyResponse.toString());
		}
		return activationPolicyResponse;
	}

    private static ActivationPolicyRequest createActivationPolicyRequest() {
        final String osName = System.getProperty("os.name");
        final String osVersion = System.getProperty("os.version");
        return  new ActivationPolicyRequest(osName, osVersion);
    }

    public static DirectActivationRequest createDirectActivationRequest(
            TrustedAssetsManager trustedAssetsManager, String hashAlgorithm,
            Set<String> deviceModels) throws GeneralSecurityException {

        final DirectActivationRequest.SubjectPublicKeyInfo
            subjectPublicKeyInfo =
            new DirectActivationRequest.SubjectPublicKeyInfo();

        final DirectActivationRequest.CertificationRequestInfo 
            certificationRequestInfo =
            new DirectActivationRequest.CertificationRequestInfo();
        certificationRequestInfo.setSubjectPublicKeyInfo(subjectPublicKeyInfo);
        certificationRequestInfo.setSubject(trustedAssetsManager.getClientId());

        final DirectActivationRequest request = new DirectActivationRequest();
        request.setCertificationRequestInfo(certificationRequestInfo);
        request.getDeviceModels().addAll(deviceModels);

        signRequest(request, trustedAssetsManager, hashAlgorithm);

        return request;
    }

    public static DirectActivationResponse postDirectActivationRequest(
            SecureConnection secureConnection,
            DirectActivationRequest directActivationRequest)
        throws IOException, GeneralSecurityException {

        String payloadString = directActivationRequest.toJson();
        byte[] payload = payloadString.getBytes(UTF_8);

        HttpResponse response =
            secureConnection.post(REST_DIRECT_ACTIVATION, payload);

        int status = response.getStatus();
        if (status == 401) {
            throw new IllegalStateException(response.getVerboseStatus("POST", REST_DIRECT_ACTIVATION));
        }

        if (status != 200) {
            throw new TransportException(status, response.getVerboseStatus("POST",
                REST_DIRECT_ACTIVATION));
        }

        String jsonResponse  = new String(response.getData(), "UTF-8");
        JSONObject json;
        try {
            json = new JSONObject(jsonResponse);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        DirectActivationResponse directActivationResponse =
            DirectActivationResponse.fromJson(json);
        if (getLogger().isLoggable(Level.FINEST)) {
            getLogger().log(Level.FINEST,
                directActivationResponse.toString());
        }

        return directActivationResponse;
    }

    public static IndirectActivationRequest createIndirectActivationRequest(
            String hardwareId, Map<String, String> metadata,
            Set<String> deviceModels, byte[] signature) {

        final IndirectActivationRequest request =
                new IndirectActivationRequest(hardwareId, metadata, deviceModels, signature);
        return request;
    }

    public static IndirectActivationResponse postIndirectActivationRequest(
            SecureConnection secureConnection, IndirectActivationRequest
            indirectActivationRequest)
            throws IOException, GeneralSecurityException {

        String payloadString = indirectActivationRequest.toJson();
        byte[] payload = payloadString.getBytes(UTF_8);

        HttpResponse response =
            secureConnection.post(REST_INDIRECT_ACTIVATION, payload);

        int status = response.getStatus();
        if (status == 401) {
            throw new IllegalStateException("endpoint already activated");
        }

        if (status != 200) {
            throw new TransportException(status, response.getVerboseStatus("POST",
                REST_INDIRECT_ACTIVATION));
        }

        String jsonResponse = new String(response.getData(), "UTF-8");
        JSONObject json;
        try {
            json = new JSONObject(jsonResponse);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        IndirectActivationResponse indirectActivationResponse =
            IndirectActivationResponse.fromJson(json);
        if (getLogger().isLoggable(Level.FINEST)) {
            getLogger().log(Level.FINEST,
                indirectActivationResponse.toString());
        }

        return indirectActivationResponse;
    }

    private static void signRequest(DirectActivationRequest directActivationRequest,
            TrustedAssetsManager trustedAssetsManager, String hashAlgorithm)
            throws TrustException {

        DirectActivationRequest.CertificationRequestInfo
            certificationRequestInfo = directActivationRequest
            .getCertificationRequestInfo();
        DirectActivationRequest.SubjectPublicKeyInfo subjectPublicKeyInfo =
            certificationRequestInfo.getSubjectPublicKeyInfo();

        PublicKey publicKey = trustedAssetsManager.getPublicKey();
        subjectPublicKeyInfo.setAlgorithm(publicKey.getAlgorithm());
        subjectPublicKeyInfo.setPublicKey(publicKey.getEncoded());
        subjectPublicKeyInfo.setFormat(publicKey.getFormat());
        subjectPublicKeyInfo.setSecretHashAlgorithm(
            DEFAULT_MESSAGE_DIGEST_ALGORITHM);

        final byte[] clientSecretData =
            trustedAssetsManager.getClientId().getBytes(UTF_8);

        byte[] clientSecret =
            trustedAssetsManager.signWithSharedSecret(clientSecretData,
            subjectPublicKeyInfo.getSecretHashAlgorithm(), trustedAssetsManager.getClientId());

        byte[] signature = trustedAssetsManager.signWithPrivateKey(
            getSignaturePayload(certificationRequestInfo, clientSecret),
            hashAlgorithm);

        directActivationRequest.setSignatureAlgorithm(hashAlgorithm);
        directActivationRequest.setSignature(signature);
    }

    private static byte[] getSignaturePayload(
            DirectActivationRequest.CertificationRequestInfo requestInfo,
            byte[] clientSecret) {
        DirectActivationRequest.SubjectPublicKeyInfo subjectPublicKeyInfo =
            requestInfo.getSubjectPublicKeyInfo();
        StringBuffer payload =
            new StringBuffer(requestInfo.getSubject());
        payload.append("\n");
        payload.append(subjectPublicKeyInfo.getAlgorithm());
        payload.append("\n");
        payload.append(subjectPublicKeyInfo.getFormat());
        payload.append("\n");
        payload.append(subjectPublicKeyInfo.getSecretHashAlgorithm());
        payload.append("\n");

        Map<String, Object> map = requestInfo.getAttributes();
        if (map != null) {
            Set<Map.Entry<String,Object>> attributes = map.entrySet();
            for (Map.Entry<String,Object> attribute : attributes) {
                payload.append(attribute.getKey());
                payload.append("=");

                Object attributeValue = attribute.getValue();
                if (attributeValue != null) {
                    payload.append("\'");
                    payload.append(attributeValue);
                    payload.append("\'");
                } else {
                    payload.append("null");
                }

                payload.append("\n");
            }
        }

        byte[] payloadBytes = payload.toString().getBytes(UTF_8);
        byte[] signatureBytes = new byte[payloadBytes.length +
            clientSecret.length + subjectPublicKeyInfo.getPublicKey().length];
        System.arraycopy(payloadBytes, 0, signatureBytes, 0,
            payloadBytes.length);
        System.arraycopy(clientSecret, 0, signatureBytes, payloadBytes.length,
            clientSecret.length);
        System.arraycopy(subjectPublicKeyInfo.getPublicKey(), 0,
            signatureBytes, payloadBytes.length + clientSecret.length,
            subjectPublicKeyInfo.getPublicKey().length);

        return signatureBytes;
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
