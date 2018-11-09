/**
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

class DevicePolicy {
    // Instance "variables"/properties...see constructor.

    /**
     *
     * @param id
     * @param deviceModelUrn
     * @param description
     * @param {Map<string, Set<DevicePolicyFunction>>} pipelines
     * @param enabled
     * @param lastModified
     */
    constructor(id, deviceModelUrn, description, pipelines, enabled, lastModified) {
        // Instance "variables"/properties.
        this.id = id;
        this.deviceModelUrn = deviceModelUrn;
        this.description = description;
        this.pipelines = pipelines;
        this.enabled = enabled;
        this.lastModified = lastModified;
        // Instance "variables"/properties.
    }

    /**
     * Converts a JSON representation of a device policy to a DevicePolicy object.
     *
     * @param {string} deviceModelUrn
     * @param {string} devicePolicyJson
     * @return {DevicePolicy} a device policy from a JSON representation of a device policy.
     */
    static fromJson(deviceModelUrn, devicePolicyJson) {
        // This *should* be a JSON representation of a device policy, but it might also be an array
        // of items of device policies.
        let devicePolicyJsonTmp = JSON.parse(devicePolicyJson);
        let devicePolicyJsonObj;

        if (devicePolicyJsonTmp && devicePolicyJsonTmp.items && (devicePolicyJsonTmp.count > 0)) {
            devicePolicyJsonObj = devicePolicyJsonTmp.items[0];
        } else if (devicePolicyJsonTmp && devicePolicyJsonTmp.pipelines) {
            devicePolicyJsonObj = devicePolicyJsonTmp;
        } else {
            return null;
        }

        /** @type {Map<string, Set<DevicePolicyFunction>>} */
        let pipelines = new Map();
        let pipelinesAry = devicePolicyJsonObj.pipelines;

        for (let i = 0; i < devicePolicyJsonObj.pipelines.length; i++) {
            /** @type {string} */
            let attributeName = devicePolicyJsonObj.pipelines[i].attributeName;
            /** @type {pipeline[]} */
            let pipelineAry = devicePolicyJsonObj.pipelines[i].pipeline;
            /** @type {Set<DevicePolicyFunction>} */
            let functions = new Set();

            for (let j = 0; j < pipelineAry.length; j++) {
                let functionObj = pipelineAry[j];
                /** @type {string} */
                let functionId = functionObj.id;
                /** @type {Map<string, object>} */
                let parameterMap = new Map();
                let parameters = functionObj.parameters;

                for (let parameterName of Object.keys(parameters)) {
                    let parameterValue = parameters[parameterName];

                    if ("action" === parameterName) {
                        parameterMap.set("name", parameterValue.name);
                        let args = parameterValue.arguments;

                        if (args && args.length > 0) {
                            /** @type {Set<object>} */
                            let argumentList = new Set();

                            for (let a = 0; a < arguments.length; a++) {
                                /** @type {object} */
                                let argTmp = arguments[a];
                                argumentList.add(argTmp);
                            }

                            parameterMap.set("arguments", argumentList);
                        }
                    } else if ("alert" === parameterName) {
                        let urn = parameterValue.urn;
                        parameterMap.set("urn", urn);
                        let fields = parameterValue.fields;
                        /** @type {Map<string, object>} */
                        let fieldMap = new Map();

                        for (let fieldName of Object.keys(fields)) {
                            let fieldValue = fields[fieldName];
                            fieldMap.set(fieldName, fieldValue);
                        }

                        parameterMap.set("fields", fieldMap);

                        if (parameterValue.severity) {
                            parameterMap.set("severity", parameterValue.severity);
                        }
                    } else {
                        parameterMap.set(parameterName, parameterValue);
                    }
                }

                functions.add(new DevicePolicyFunction(functionId, parameterMap));
            }

            pipelines.set(attributeName, functions);
        }

        return new DevicePolicy(devicePolicyJsonObj.id, deviceModelUrn,
            devicePolicyJsonObj.description, pipelines, devicePolicyJsonObj.enabled,
            devicePolicyJsonObj.lastModified);
    }

    /**
     * Get the free form description of the device policy.
     *
     * @return {string} the description of the model.
     */
    getDescription() {
        return this.description;
    }

    /**
     * Get the target device model URN.
     *
     * @return {string} the URN of the target device model
     */
    getDeviceModelUrn() {
        return this.deviceModelUrn;
    }

    /**
     * Returns the policy ID.
     *
     * @return {string} the policy ID.
     */
    getId() {
        return this.id;
    }

    /**
     * Get the date of last modification.
     *
     * @return {number} the date of last modification.
     */
    getLastModified() {
        return this.lastModified;
    }

    /**
     * Get the function pipeline of this policy for an attribute.
     *
     * @param {string} attributeName the name of the attribute to retrieve the pipeline for.
     * @return {Set} a read-only Set of {@link DevicePolicyFunction}.
     */
    getPipeline(attributeName) {
        if (attributeName) {
            return this.pipelines.get(attributeName);
        } else {
            return this.pipelines.get(DevicePolicy.ALL_ATTRIBUTES);
        }
    }

    /**
     * Get all the pipelines of this policy. The map key is an attribute name, the value is the
     * pipeline for that attribute.
     *
     * @return {Map<string, Set<DevicePolicyFunction>>} the pipelines of this policy.
     */
    getPipelines() {
        return this.pipelines;
    }

    /**
     * Get the {@code enabled} state of the device policy.
     *
     * @return {boolean} {@code true} if the policy is enabled.
     */
    isEnabled() {
        return this.enabled;
    }

// @Override public boolean equals(Object obj) {
//     if (this == obj) return true;
//     if (obj == null || obj.getClass() != DevicePolicy.class) {
//         return false;
//     }
//     return this.id.equals(((DevicePolicy)obj).id);
// }
//
// @Override
// public int hashCode() {
//     return this.id.hashCode();
// }
//
// public DevicePolicy(String id,
//     String deviceModelURN,
// Map<String, List<Function>> pipelines,
//     String description,
//     long lastModified,
//     boolean enabled)
// {
//     this.id = id;
//     this.deviceModelURN = deviceModelURN;
//
//     this.deviceIds = new HashSet<String>();
//
//     this.pipelines = new HashMap<String,List<Function>>();
//     if (pipelines) {
//         this.pipelines.putAll(pipelines);
//     }
//
//     this.description = description;
//     this.lastModified = lastModified;
//     this.enabled = enabled;
// }
}

DevicePolicy.ALL_ATTRIBUTES = '*';
