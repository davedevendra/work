/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import com.oracle.iot.client.impl.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * DirectActivationRequest
 */
public class DirectActivationRequest {

    public static final String FIELD_CERTIFICATION_REQUEST_INFO = "certificationRequestInfo";
    public static final String FIELD_SIGNATURE_ALGORITHM = "signatureAlgorithm";
    public static final String FIELD_SIGNATURE = "signature";
    public static final String FIELD_DEVICE_MODELS = "deviceModels";

    public static final String PUBLIC_KEY_ENCODING_FORMAT_X509 = "X.509";

    private Set<String> deviceModels = new HashSet<String>();

    public static class SubjectPublicKeyInfo {
        public static final String FIELD_ALGORITHM = "algorithm";
        public static final String FIELD_PUBLIC_KEY = "publicKey";
        public static final String FIELD_FORMAT = "format";
        public static final String FIELD_SECRET_HASH_ALGORITHM = "secretHashAlgorithm";

        private String algorithm;
        private byte[] publicKey;
        private String format = PUBLIC_KEY_ENCODING_FORMAT_X509;
        private String secretHashAlgorithm;

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        public byte[] getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(byte[] subjectPublicKey) {
            this.publicKey = subjectPublicKey;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public String getSecretHashAlgorithm() {
            return secretHashAlgorithm;
        }

        public void setSecretHashAlgorithm(String secretHashAlgorithm) {
            this.secretHashAlgorithm = secretHashAlgorithm;
        }

        public JSONObject toJson() {
            JSONObject objectBuilder = new JSONObject();

            try {
                objectBuilder.put(FIELD_ALGORITHM, algorithm);
                objectBuilder.put(FIELD_PUBLIC_KEY,
                    Base64.getEncoder().encodeToString(publicKey));
                objectBuilder.put(FIELD_FORMAT, format.toString());
                objectBuilder.put(FIELD_SECRET_HASH_ALGORITHM,
                    secretHashAlgorithm.toString());
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }

            return objectBuilder;
        }

        public void fromJson(JSONObject jsonObject) {
            if (jsonObject != null) {
                setFormat(jsonObject.opt(FIELD_FORMAT).toString());
                setAlgorithm(jsonObject.opt(FIELD_ALGORITHM).toString());

                String encodedPublicKey =
                    jsonObject.opt(FIELD_PUBLIC_KEY).toString();
                setPublicKey(Base64.getDecoder().decode(encodedPublicKey));

                setSecretHashAlgorithm(
                    jsonObject.opt(FIELD_SECRET_HASH_ALGORITHM).toString());
            }
        }

        @Override
        public String toString() {
            return "SubjectPublicKeyInfo{" +
                    "algorithm='" + algorithm + '\'' +
                    ", publicKey=" + Arrays.toString(publicKey) +
                    ", format='" + format + '\'' +
                    ", secretHashAlgorithm='" + secretHashAlgorithm + '\'' +
                    '}';
        }
    }

    public static class CertificationRequestInfo {
        public static final String FIELD_SUBJECT = "subject";
        public static final String FIELD_SUBJECT_PUBLIC_KEY_INFO = "subjectPublicKeyInfo";
        public static final String FIELD_ATTRIBUTES = "attributes";
        private String subject;
        private SubjectPublicKeyInfo subjectPublicKeyInfo;
        private Map<String, Object> attributes;

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public Map<String, Object> getAttributes() {
            return attributes;
        }

        public void setAttributes(Map<String, Object> attributes) {
            this.attributes = attributes;
        }


        public SubjectPublicKeyInfo getSubjectPublicKeyInfo() {
            return subjectPublicKeyInfo;
        }

        public void setSubjectPublicKeyInfo(SubjectPublicKeyInfo subjectPublicKeyInfo) {
            this.subjectPublicKeyInfo = subjectPublicKeyInfo;
        }

        @Override
        public String toString() {
            return "CertificationRequestInfo{" +
                    "subject='" + subject + '\'' +
                    ", subjectPublicKeyInfo=" + subjectPublicKeyInfo +
                    ", attributes=" + attributes +
                    '}';
        }

        public JSONObject toJson() {
            JSONObject objectBuilder = new JSONObject();

            try {
                objectBuilder.put(FIELD_SUBJECT, subject);
                if (subjectPublicKeyInfo != null) {
                    objectBuilder.put(FIELD_SUBJECT_PUBLIC_KEY_INFO,
                                      subjectPublicKeyInfo.toJson());
                }

                JSONObject items = new JSONObject();
                if (attributes != null) {
                    addEntries(attributes, items);
                }

                objectBuilder.put(FIELD_ATTRIBUTES, items);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }

            return objectBuilder;
        }


        // TODO: create a JsonUtils class and move all the repeated JSON
        // actions there

        /**
         * Adds each entry from the <i>map</i> to the <i>items</i> object.
         * @param map entries to be added to the items object
         * @param items the JSONObject to which the map entries are added
         */
        private static void addEntries(Map<String, ?> map,
                JSONObject items) {
            for (Map.Entry<String, ?> entry : map.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                try {
                    if (value instanceof Number ||
                            value instanceof String ||
                            value instanceof Boolean ||
                            value instanceof JSONArray ||
                            value instanceof JSONObject) {
                        items.put(key, value);
                    } else {
                        items.put(key, value.toString());
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        public void fromJson(JSONObject jsonObject) {
            if (jsonObject != null) {
                try {
                    setSubject(jsonObject.get(FIELD_SUBJECT).toString());
                    subjectPublicKeyInfo = new SubjectPublicKeyInfo();
                    JSONObject subjectPublicKeyInfoJsonObj =
                        jsonObject.optJSONObject(FIELD_SUBJECT_PUBLIC_KEY_INFO);
                    if (subjectPublicKeyInfoJsonObj != null) {
                        subjectPublicKeyInfo.fromJson(
                            subjectPublicKeyInfoJsonObj);
                    }

                    setAttributes(getJsonMap(jsonObject.get(FIELD_ATTRIBUTES),
                        FIELD_ATTRIBUTES));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        // TODO: taken from server JsonUtils, refactor!
        private static Map<String, Object> getJsonMap(Object value,
                String name) {
            if (value == null) {
                return null;
            }

            if (!(value instanceof JSONObject)) {
                throw new IllegalArgumentException("expected '" + name +
                        "' to be a map");
            }

            Object items = ((JSONObject)value).opt("items");
            if (items == null) {
                if (((JSONObject)value).opt("links") == null) {
                    items = value;
                } else {
                    return new HashMap<String, Object>();
                }
            }

            if (!(items instanceof JSONObject)) {
                throw new IllegalArgumentException("expected '" + name +
                        ".items' to be a map, but found " + items);
            }

            JSONObject jsonItems = (JSONObject)items;
            Iterator<String> keys = jsonItems.keys();

            Map<String, Object> result = new HashMap<String,Object>();

            while (keys.hasNext()) {
                String key = keys.next();
                Object raw = jsonItems.opt(key);
                Object v;

                if (raw == null || raw instanceof String ||
                    raw instanceof Boolean) {
                    v = raw;
                } else if (raw instanceof Number) {
                    v = ((Number)raw).longValue();
                } else if (raw instanceof JSONArray) {
                    ArrayList<String> values = new ArrayList<String>();
                    JSONArray jsonArray = (JSONArray)raw;
                    for (int i = 0, size = jsonArray.length(); i < size; i++) {
                        values.add(jsonArray.opt(i).toString());
                    }

                    v = values.toArray(new String[0]);
                } else {
                    throw new IllegalArgumentException(
                        "unsupported map value '" + raw + "'");
                }

                result.put(key, v);
            }

            return result;
        }

    }

    private CertificationRequestInfo certificationRequestInfo;
    private String signatureAlgorithm;
    private byte[] signature;

    public CertificationRequestInfo getCertificationRequestInfo() {
        return certificationRequestInfo;
    }

    public void setCertificationRequestInfo(CertificationRequestInfo certificationRequestInfo) {
        this.certificationRequestInfo = certificationRequestInfo;
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    public void setSignatureAlgorithm(String signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public Set<String> getDeviceModels() {
        return this.deviceModels;
    }

    public static DirectActivationRequest fromJson(String jsonString) {
        DirectActivationRequest request = new DirectActivationRequest();
        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            if (jsonObject != null) {
                request.fromJson(jsonObject);
            }
        } catch (JSONException ex) {
            // TODO
        }

        return request;
    }

    public void fromJson(JSONObject jsonObject) {
        certificationRequestInfo = new CertificationRequestInfo();

        JSONObject fieldJsonObject =
            jsonObject.optJSONObject(FIELD_CERTIFICATION_REQUEST_INFO);
        if (fieldJsonObject != null) {
            certificationRequestInfo.fromJson(fieldJsonObject);
        }

        setSignatureAlgorithm(jsonObject.opt(
            FIELD_SIGNATURE_ALGORITHM).toString());

        String encodedSignature = jsonObject.opt(FIELD_SIGNATURE).toString();
        setSignature(Base64.getDecoder().decode(encodedSignature));
        JSONArray deviceModelsArray =
            jsonObject.optJSONArray(FIELD_DEVICE_MODELS);
        if (deviceModelsArray != null) {
            for (int i = 0, size = deviceModelsArray.length(); i < size; i++) {
                deviceModels.add(deviceModelsArray.opt(i).toString());
            }
        }
    }

    public String toJson() {
        JSONObject objectBuilder = new JSONObject();
        JSONArray deviceModels = new JSONArray();

        try {
            if (certificationRequestInfo != null) {
                objectBuilder.put(FIELD_CERTIFICATION_REQUEST_INFO,
                                  certificationRequestInfo.toJson());
            }

            objectBuilder.put(FIELD_SIGNATURE_ALGORITHM, signatureAlgorithm);
            objectBuilder.put(FIELD_SIGNATURE,
                              Base64.getEncoder().encodeToString(signature));

            if (this.deviceModels != null) {
                Iterator<String> iterator = this.deviceModels.iterator();
                while(iterator.hasNext()) {
                    deviceModels.put(iterator.next());
                }

                objectBuilder.put(FIELD_DEVICE_MODELS, deviceModels);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return objectBuilder.toString();
    }

    @Override
    public String toString() {
        return "DirectActivationRequest{" +
            "deviceModels="+ deviceModels.toString() +
            ", certificationRequestInfo=" + certificationRequestInfo +
            ", signatureAlgorithm='" + signatureAlgorithm + '\'' +
            ", signature=" + Arrays.toString(signature) +
            '}';
    }
}
