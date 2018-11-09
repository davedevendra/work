/**
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */
class DevicePolicyManager {
    // Instance "variables"/properties...see constructor.

    /**
     * Returns the device policy manager for this directly connected device.
     *
     * @param {string} endpointId the endpoint ID of the device this device policy manager is for.
     * @return {DevicePolicyManager} the device policy manager for this endpoint ID.
     */
    static getDevicePolicyManager(endpointId) {
        // @type {PersistenceStorage}
        let persistenceStore = PersistenceStoreManager.get(endpointId);
        // @type {DevicePolicyManager}
        return persistenceStore.getOpaque('DevicePolicyManager', null);
    }

    /**
     *
     * @param {$impl.DirectlyConnectedDevice} directlyConnectedDevice
     */
    constructor(directlyConnectedDevice) {
        // Instance "variables"/properties.
        // @type {Set<ChangeListener>}
        this.changeListeners = new Set();
        this.directlyConnectedDevice = directlyConnectedDevice;
        /**
         * Map a device id to the policies that are available to it.  The key is the device id.  The
         * value is a map of device model URN to policy id. The policy id gets us to the
         * configuration data.
         * @type {Map<string, Map<string, string>>}
         */
        this.policiesByDeviceId = new Map();
        /**
         * Map of policy ID to policies. The key is a policy id. The value is a map of policy
         * attributes and values.
         * @type {Map<String, DevicePolicy>}
         */
        this.policiesByPolicyId = new Map();
        /**
         * @type {Map<string, Map<String, Set<String>>>}
         */
        this.policiesByDeviceModelUrn = new Map();
        // Instance "variables"/properties.
        this.test = false;
    }

    /**
     * Add a listener for receiving notification of policies being assigned or unassigned.
     * Must have a policyAssigned(DevicePolicy devicePolicy, Set<String> assignedDevices) function.
     * Must have a policyUnassigned(DevicePolicy devicePolicy, Set<String> unassignedDevices) function.
     *
     * @param {object} changeListener the ChangeListener to add.
     */
    addChangeListener(changeListener) {
        debug('DevicePolicyManager.addChangeListener called.');

        if (!changeListener ||
            typeof(changeListener.policyAssigned) !== 'function' ||
            typeof(changeListener.policyUnassigned) !== 'function')
        {
            return;
        }

        this.changeListeners.add(changeListener);
    }

    /**
     * @param {string} deviceModelUrn
     * @param {string} policyId
     * @param {string} deviceId
     * @param {number} lastModified
     */
    assignPolicyToDevice(deviceModelUrn, policyId, deviceId, lastModified) {
        // If the device has a policy currently,
        // it needs to be unassigned.
        // @type {string}
        let currentPolicyId;
        // @type {Map<string, string}
        const policies = this.policiesByDeviceId.get(deviceId);

        if (policies) {
            currentPolicyId = policies.get(deviceModelUrn);
        } else {
            currentPolicyId = null;
        }

        // If the current policy is the same as the policy that is being
        // assigned, no need to continue.
        if (policyId === currentPolicyId) {
            return;
        }

        // Make sure we have the policy before assigning the new policy.
        // @type {DevicePolicy}
        let devicePolicy = this.policiesByPolicyId.get(policyId);

        if (!devicePolicy) {
            this.downloadPolicy(deviceModelUrn, policyId).then(devicePolicy => {
                if (devicePolicy) {
                    this.policiesByPolicyId.set(policyId, devicePolicy);
    
                    // replaceAll just fills space where device id should be since
                    // the deviceId here doesn't matter for debug print, but it would
                    // be nice to have the printout line up.
                    debug(deviceId.replace(".", " ") + " : Policy : " + devicePolicy.getId() +
                    '\n' + devicePolicy.toString());

                    assignPolicyToDevice2(devicePolicy);
                }
            });
        } else {
            assignPolicyToDevice2(devicePolicy);
        }
    }

    assignPolicyToDevice2(devicePolicy) {
        if (devicePolicy) {
            // @type {Map<string, string>}
            let devicePolicies = this.policiesByDeviceId.get(deviceId);

            if (!devicePolicies) {
                devicePolicies = new Map();
                this.policiesByDeviceId.set(deviceId, devicePolicies);
            }

            devicePolicies.set(deviceModelUrn, policyId);

            // @type {Map<string, Set<string>>}
            let deviceModelPolicies = policiesByDeviceModelUrn.get(deviceModelUrn);

            if (!deviceModelPolicies) {
                deviceModelPolicies = new Map();
                policiesByDeviceModelUrn.set(deviceModelUrn, deviceModelPolicies);
            }

            // @type {Set<string>}
            let assignedDevices = deviceModelPolicies.get(policyId);

            if (!assignedDevices) {
                assignedDevices = new Set();
                deviceModelPolicies.set(policyId, assignedDevices);
            }

            assignedDevices.add(deviceId);

            if (currentPolicyId) {
                removePersistedAssociation(deviceModelUrn, currentPolicyId, deviceId);
            }

            persistAssociation(deviceModelUrn, policyId, deviceId);
        }
    }

    /**
     * Get the ids of indirectly connected devices that are assigned to the policy.
     * 
     * @param {string} deviceModelUrn the device model urn
     * @param {string} policyId the policy id to query for
     * @param {string} directlyConnectedOwner
     * @return {Promise} that resolves to a Set<string> the set of indirectly connected device IDs
     *          for this policy.
     */
    getIndirectlyConnectedDeviceIdsForPolicy(deviceModelUrn, policyId, directlyConnectedOwner) {
        return new Promise((resolve, reject) => {
            // @type {string}
            let urn;

            try {
                urn = encodeURI(deviceModelUrn);
            } catch (uriError) {
                // UTF-8 is a required encoding.
                // Throw an exception here to make the compiler happy
                console.log('Error encoding device model URN: ' + uriError);
            }

            // @type {string}
            let query;

            try {
                // @type {string}
                let icdFilter = encodeURI('{"directlyConnectedOwner":"' + directlyConnectedOwner +
                    '"}');

                query = "?q=" + icdFilter + "&fields=id";
            } catch (uriError) {
                // UTF-8 is a required encoding.
                // Throw an exception here to make the compiler happy
                console.log('Error encoding ICD filter: ' + uriError);
            }

            let dcd = this.directlyConnectedDevice;

            let options = {
                headers: {
                    'Authorization': dcd._.bearer,
                    'X-EndpointId': dcd._.tam.getEndpointId()
                },
                method: 'GET',
                // GET iot/privateapi/v2/deviceModels/{urn}/devicePolicies/{id}/devices?q={"directlyConnectedOwner" : "GW-endpoint-id"}
                path: $impl.privateRoot + '/deviceModels/' + urn + '/devicePolicies/' + policyId +
                      '/devices' + query,
                tam: dcd._.tam
            };

            debug('path=' + options.path);

            $impl.protocolReq(options, '', function (response, error) {
                let icdIds = new Set();

                if (error) {
                    console.log('Invalid response getting ICDs: ' + error);
                    reject(icdIds);
                } else {
                    debug('DevicePolicyManager.getIndirectlyConnectedDeviceIdsForPolicy response = ' +
                        response);

                    if (!response || !response.items || response.items.length === 0 ||
                        !(response.items[0].id))
                    {
                        return resolve(icdIds);
                    }

                    response.items.forEach(item => {
                        icdIds.add(item.id);
                    });

                    resolve(icdIds);
                }
            }, null, this.directlyConnectedDevice);
        });
    }

    /**
     * Get the {@code DevicePolicy} for the given device model and device ID.
     *
     * @param {string} deviceModelUrn
     * @param {string} deviceId
     * @returns {Promise} a Promise which resolves a DevicePolicy.
     */
    getPolicy(deviceModelUrn, deviceId) {
        return new Promise((resolve, reject) => {
            debug('DevicePolicyManager.getPolicy called.');
            // If we already have the policy for this device model and device, return it.
            // Otherwise, look it up. If there isn't an entry in policiesByDeviceId
            // for the device model urn and device id when this method is called,
            // there will be by the time this method completes.

            // policiesByDeviceId looks like { <device-id> : { <device-model-urn> : <policy-id> }}.
            // @type {Map<string, string>}
            const devicePolicies = this.policiesByDeviceId.get(deviceId);
            debug('DevicePolicyManager.getPolicy devicePolicies = ' + devicePolicies);

            // If devicePolicies is null, we drop through and do the lookup.
            if (devicePolicies) {
                // If the deviceModelUrn is not found in the map, we drop through and do the lookup.
                // There may be a mapping for the device model urn, but the value may be null,
                // which means that there is no policy for the combination of device model and device.
                if (devicePolicies.has(deviceModelUrn)) {
                    // @type {string}
                    const policyId = devicePolicies.get(deviceModelUrn);
                    // @type {DevicePolicy}
                    let devicePolicy;

                    if (policyId) {
                        // Very important that the client has gotten the policy before we get here!
                        devicePolicy = this.policiesByPolicyId.get(policyId);
                    } else {
                        devicePolicy = null;
                    }

                    debug('DevicePolicyManager.getPolicy returning devicePolicy: ' + devicePolicy);
                    return resolve(devicePolicy);
                }
            }

            // Add the mapping so the code doesn't try to fetch a policy for this
            // device again. The only way the device will get a policy after this
            // is from an "assigned" policyChanged, or when the client restarts.
            // @type {Map<string, string>}
            let policies = this.policiesByDeviceId.get(deviceId);
            debug('DevicePolicyManager.getPolicy policies = ' + policies);

            if (!policies) {
                policies = new Map();
                this.policiesByDeviceId.set(deviceId, policies);
            }

            // Stop policyChanged while doing this lookup.
            // If we get to here, then there was no mapping for the deviceId in policiesByDeviceId,
            // or there was a mapping for the deviceId, but not for the device model. So we need
            // to do a lookup and update policiesByDeviceId
            // @type {DevicePolicy}
            this.lookupPolicyForDevice(deviceModelUrn, deviceId).then(devicePolicy => {
                debug('DevicePolicyManager.getPolicy.lookupPolicyForDevice devicePolicy = ' + util.inspect(devicePolicy));
                // Add the mapping so the code doesn't try to fetch a policy for this
                // device again. The only way the device will get a policy after this
                // is from an "assigned" policyChanged, or when the client restarts.
                // @type {Map<string, string>}
                let policies = this.policiesByDeviceId.get(deviceId);

                if (!policies) {
                    // @type {Map<string, string>}
                    policies = new Map();
                    this.policiesByDeviceId.set(deviceId, policies);
                }

                // Note: devicePolicy may be null, but the entry is made anyway.
                // This just means the device has no policy for this device model.
                // Adding null prevents another lookup.
                // @type {string}
                const policyId = devicePolicy != null ? devicePolicy.getId() : null;
                debug('DevicePolicyManager.getPolicy.lookupPolicyForDevice policyId = ' + policyId);
                policies.set(deviceModelUrn, policyId);

                if (devicePolicy) {
                    // @type {Set<string>}
                    const assignedDevices = new Set();
                    assignedDevices.add(deviceId);
                    debug('DevicePolicyManager.getPolicy.lookupPolicyForDevice calling notifyPolicyAssigned.');
                    this.notifyPolicyAssigned(devicePolicy, assignedDevices);
                }

                debug('DevicePolicyManager.getPolicy returning devicePolicy: ' + devicePolicy);
                resolve(devicePolicy);
            }).catch(error => {
                debug('DevicePolicyManager.getPolicy returning null.');
                resolve(null);
            });
        });
    }

    /**
     * GET iot/privateapi/v2/deviceModels/{urn}/devicePolicies/{policyId}.
     * The policiesByPolicyId map is updated, and an entry is made (if necessary) in the
     * policiesByDeviceModelUrn map.
     * 
     * @param {string} deviceModelUrn the device model URN.
     * @param {string} policyId the policy ID.
     * @return {Promise} a Promise which resolves to the DevicePolicy, or null if an error occured.
     */
    downloadPolicy(deviceModelUrn, policyId) {
        return new Promise((resolve, reject) => {
            let tam;
            let bearer;
            let endpointId;

            // We may have one of several DirectlyConnectedDevice's
            if (this.directlyConnectedDevice._.hasOwnProperty('internalDev') &&
                this.directlyConnectedDevice._.internalDev.hasOwnProperty('_') &&
                this.directlyConnectedDevice._.internalDev._.hasOwnProperty('internalDev') &&
                this.directlyConnectedDevice._.internalDev._.internalDev.hasOwnProperty('_')) {
                tam = this.directlyConnectedDevice._.internalDev._.internalDev._.tam;
                bearer = this.directlyConnectedDevice._.internalDev._.internalDev._.bearer;
                endpointId = tam.endpointId;
            } else if (this.directlyConnectedDevice._.hasOwnProperty('internalDev') &&
                this.directlyConnectedDevice._.internalDev.hasOwnProperty('_'))
            {
                tam = this.directlyConnectedDevice._.internalDev._.tam;
                bearer = this.directlyConnectedDevice._.internalDev._.bearer;
                endpointId = tam.endpointId;
            } else {
                tam = this.directlyConnectedDevice._.tam;
                bearer = this.directlyConnectedDevice._.bearer;
                endpointId = tam.endpointId;
            }

            const fields = encodeURI('id,pipelines,enabled,lastModified');
            const query = '?fields=' + fields;

            let options = {
                headers: {
                    'Authorization': bearer,
                    'X-EndpointId': endpointId
                },
                method: 'GET',
                path: $impl.privateRoot + '/deviceModels/' + deviceModelUrn + '/devicePolicies/' +
                      policyId + query,
                tam: tam
            };

            let self = this;

            $impl.protocolReq(options, '', function (response, error) {
                if (error) {
                    console.log('Invalid response getting device policy: ' + error);
                    resolve(null);
                } else {
                    if (!response ||
                        !(response.id || response.items) ||
                        (response.items && !(response.items && (response.items.length === 0) && response.items[0].id)) ||
                            error)
                    {
                        return resolve(null);
                    }

                    let devicePolicyJson = JSON.stringify(response, null, 4);
                    let devicePolicy = DevicePolicy.fromJson(deviceModelUrn, devicePolicyJson);

                    if (devicePolicy) {
                        Object.freeze(devicePolicy);
                        self.policiesByPolicyId.set(policyId, devicePolicy);
                        resolve(devicePolicy);
                    } else {
                        reject('Error retrieving device policy.');
                    }
                }
            }, this.directlyConnectedDevice);
        });
    }

    /**
     * GET iot/privateapi/v2/deviceModels/{urn}/devicePolicies/{policyId}.
     * The policiesByPolicyId map is updated, and an entry is made (if necessary) in the
     * policiesByDeviceModelUrn map.
     * 
     * @param {string} deviceModelUrn the device model URN.
     * @param {string} deviceId the device ID.
     * @return {Promise} a Promise which resolves to the DevicePolicy, or null if an error occured.
     */
    downloadPolicyByDeviceModelDeviceId(deviceModelUrn, deviceId) {
        return new Promise((resolve, reject) => {
            let tam;
            let bearer;
            let endpointId;

        if (this.directlyConnectedDevice._.hasOwnProperty('internalDev') &&
            this.directlyConnectedDevice._.internalDev.hasOwnProperty('_') &&
            this.directlyConnectedDevice._.internalDev._.hasOwnProperty('internalDev') &&
            this.directlyConnectedDevice._.internalDev._.internalDev.hasOwnProperty('_'))
        {
            tam = this.directlyConnectedDevice._.internalDev._.internalDev._.tam;
            bearer = this.directlyConnectedDevice._.internalDev._.internalDev._.bearer;
            endpointId = this.directlyConnectedDevice._.internalDev._.internalDev._.tam.endpointId;
        } else if (this.directlyConnectedDevice._.hasOwnProperty('internalDev')) {
            tam = this.directlyConnectedDevice._.internalDev._.tam;
            bearer = this.directlyConnectedDevice._.internalDev._.bearer;
            endpointId = this.directlyConnectedDevice._.internalDev._.tam.endpointId;
        } else {
            tam = this.directlyConnectedDevice._.tam;
            bearer = this.directlyConnectedDevice._.bearer;
            endpointId = this.directlyConnectedDevice._.tam.endpointId;
        }

            const devicesDotId = encodeURI('{"devices.id":"' + deviceId + '"}');
            const fields = encodeURI('id,pipelines,enabled,lastModified');
            const query = '?q=' + devicesDotId + '&fields=' + fields;

            let options = {
                headers: {
                    'Authorization': bearer,
                    'X-EndpointId': endpointId
                },
                method: 'GET',
                path: $impl.privateRoot + '/deviceModels/' + deviceModelUrn + '/devicePolicies' + query,
                tam: tam
            };

            let self = this;

            $impl.protocolReq(options, '', function (response, error) {
                if (error) {
                    console.log('Invalid response getting device policy: ' + error);
                    return resolve(null);
                }

                debug('response = ' + response);

                if (!response || !response.items || response.items.length === 0 ||
                    !(response.items[0].id) || error)
                {
                    return resolve(null);
                }

                let devicePolicyJson = JSON.stringify(response.items[0], null, 4);

                if (devicePolicyJson) {
                    debug('devicePoliciesJson = ' + devicePolicyJson);
                    // The response is an array of items, get the first one.
                    let devicePolicy = DevicePolicy.fromJson(deviceModelUrn, devicePolicyJson);

                    if (devicePolicy) {
                        Object.freeze(devicePolicy);
                        resolve(devicePolicy);
                    } else {
                        resolve(null);
                    }
                } else {
                    resolve(null);
                }
            }, this.directlyConnectedDevice);
        });
    }
    
    /**
     * Lookup the policy for the combination of device model and device ID on the server.
     * Should only be called from getPolicy when there is no policy for the device.
     *
     * @param {string} deviceModelUrn the device model urn
     * @param {string} deviceId the device id to query for
     * @return {Promise} a Promise which resolves to the JSON policy, or {@code null} if there is no
     *         policy for the combination of deviceModelUrn and deviceId.
     */
    lookupPolicyForDevice(deviceModelUrn, deviceId) {
        return new Promise((resolve, reject) => {
            // Do we already have the policy?
            // @type {Map<string, Set<string>>}
            let policies = this.policiesByDeviceModelUrn.get(deviceModelUrn);

            if (policies) {
                policies.forEach((deviceIds, policyId) => {
                    if (deviceIds.has(deviceId)) {
                        // @type {DevicePolicy}
                        const devicePolicy = this.policiesByPolicyId.get(policyId);

                        if (devicePolicy) {
                            return resolve(devicePolicy);
                        }
                    }
                });
            }

            this.downloadPolicyByDeviceModelDeviceId(deviceModelUrn, deviceId) .then(devicePolicy => {
                debug('DevicePolicyManager.lookupPolicyForDevice devicePolicy = ' +
                    devicePolicy);

                // If we found a device policy, update our local state
                if (!devicePolicy) {
                    return resolve(null);
                }

                // @type {string}
                const policyId = devicePolicy.getId();

                // Only put the policy in policiesByPolicyId if it isn't there already.
                // This prevents putting a changed policy, which should be processed
                // through policyChanged.
                if (!this.policiesByPolicyId.has(policyId)) {
                    this.policiesByPolicyId.set(policyId, devicePolicy);

                    // replaceAll just fills space where device ID should be since
                    // the device id here doesn't matter for debug print, but it would
                    // be nice to have the printout line up.
                    debug(policyId.replace(".", " ") + ' : Policy : ' +
                        devicePolicy.getId() + '\n' + devicePolicy.toString());
                }

                // Remember this policy maps to this device model.
                // Do not add the device ID to the set of device IDs here.
                // Do that in getPolicy (just to keep all of the device
                // ID state updates in one place).
                policies = this.policiesByDeviceModelUrn.get(deviceModelUrn);

                if (!policies) {
                    // @type {Map<string, Set<string>>}
                    policies = new Map();
                    this.policiesByDeviceModelUrn.set(deviceModelUrn, policies);
                }

                // @type {Set<string>}
                let deviceIds = policies.get(policyId);

                if (!deviceIds) {
                    deviceIds = new Set();
                    policies.set(policyId, deviceIds);
                }

                deviceIds.add(deviceId);
                resolve(devicePolicy);
            });
        });
    }

    /**
     * Invoke {@code policyAssigned} method on change listeners.
     *
     * @param {DevicePolicy} devicePolicy the assigned policy.
     * @param {Set<string>} assignedDevices the devices to which the policy was assigned.
     */
    notifyPolicyAssigned(devicePolicy, assignedDevices) {
        debug('DevicePolicyManager.notifyPolicyAssigned called.');
        if (!devicePolicy || assignedDevices.size === 0) {
            return;
        }

        debug('DevicePolicyManager.notifyPolicyAssigned changeListeners = ' + util.inspect(this.changeListeners));

        this.changeListeners.forEach(changeListener => {
            try {
                debug('DevicePolicyManager.notifyPolicyAssigned calling changeListener.');
                changeListener.policyAssigned(devicePolicy, assignedDevices);
            } catch (error) {
                // The ChangeListener may throw an error.
                console.log(error);
            }
        });
    }

    /**
     * Invoke {@code policyAssigned} method on change listeners.
     *
     * @param {DevicePolicy} devicePolicy the assigned policy.
     * @param {Set<string>} unassignedDevices the devices to which the policy was assigned.
     */
    notifyPolicyUnassigned(devicePolicy, unassignedDevices) {
        if (!devicePolicy || unassignedDevices.size === 0) {
            return;
        }

        this.changeListeners.forEach(changeListener => {
            try {
                changeListener.policyUnassigned(devicePolicy, unassignedDevices);
            } catch (error) {
                // The ChangeListener may throw an error.
                console.log(error);
            }
        });
    }

    /**
     * Handle {@code deviceModels/urn:oracle:iot:dcd:capability:device_policy/policyChanged}
     *
     * @param {DirectlyConnectedDevice} directlyConnectedDevice.
     * @param {RequestMessage} requestMessage the RequestMessage from the server.
     * @return {iotcs.message.Message} a ResponseMessage.
     */
    policyChanged(directlyConnectedDevice, requestMessage) {
        //
        // The server will not send a policyChanged to a device for which the policy is not intended.
        // If this is a DCD, then the policy  is meant for this DCD.
        // If this is a GW, then the policy is meant for one or more of its ICDs.
        //
        // RequestMessage body looks like:
        // [{
        //    "deviceModelUrn": "urn:com:oracle:iot:device:humidity_sensor",
        //    "id": "547B66F3-5DC8-4F60-835F-7B7773C8EE7A",
        //    "lastModified": 1511805927387,
        //    "op": "changed"
        // }]
        //
        // Where op is:
        //   "changed"    - The policy pipeline was changed. The client needs to GET the policy.
        //   "assigned"   - The policy was assigned to device(s). The policy pipeline itself
        //                  has not changed. The server will not send this to the client
        //                  unless the client has the device(s). A gateway client needs to get
        //                  a list of devices the policy applies to, but a directly connected
        //                  device can assume the policy is for it. If necessary, the client
        //                  will GET the policy.
        //   "unassigned" - The policy was unassigned from device(s). The policy pipeline itself
        //                  has not changed. The server will not send this to the client
        //                  unless the client has the device(s). A gateway client needs to get
        //                  a new list of devices the policy applies to, but a directly connected
        //                  device can assume the policy is for it.
        //
        let responseMessage = null;

        // @type {boolean}
        const dcdIsGatewayDevice = true; //directlyConnectedDevice instanceof GatewayDevice;
        // @type {string}
        const endpointId = directlyConnectedDevice.getEndpointId();

        try {
            // @type {object}
            const body = JSON.parse(forge.util.decode64(requestMessage.payload.body));

            for (let n = 0, nMax = body.length; n < nMax; n++) {
                let item = body[n];
                // @type {string}
                const op = item.op !== null ? item.op : 'changed';
                // @type {string}
                const deviceModelUrn = item.deviceModelUrn;
                // @type {string}
                const policyId = item.id;
                // @type {number}
                const lastModified = item.lastModified;

                debug('policyChanged notification received: deviceModelUrn=' + deviceModelUrn +
                    ', operation=' + op + ', policyId=' + policyId + ', lastModified=' +
                    lastModified);

                if ('unassigned' === op) {
                    this.processUnassign(deviceModelUrn, policyId, endpointId, dcdIsGatewayDevice,
                        lastModified);
                } else if ('assigned' === op) {
                    this.processAssign(deviceModelUrn, policyId, endpointId, dcdIsGatewayDevice,
                        lastModified);
                } else if ('changed' === op) {
                    // @type {DevicePolicy}
                    const policyBeforeChange = this.policiesByPolicyId.get(policyId);

                    // If device policy is null, then something is wrong in our mappings.
                    // Remove the references to this device model URN an policy ID.
                    if (!policyBeforeChange) {
                        // @type {Map<string, Set<string>>}
                        const policies = this.policiesByDeviceModelUrn.get(deviceModelUrn);

                        if (policies) {
                            // @type {Set<string>}
                            const assignedDevices = policies.delete(policyId);

                            if (assignedDevices) {
                                assignedDevices.forEach(deviceId => {
                                    // @type {Map<string, string>}
                                    const devicePolicies = this.policiesByDeviceId.get(deviceId);

                                    if (devicePolicies != null) {
                                        devicePolicies.delete(policyId);
                                    }
                                });
                            }
                        }

                        return responseMessage; // continue
                    }

                    // Before updating the policy, notify the devices the policy is unassigned.
                    // This gives the code in VirtualDeviceImpl or MessagingPolicyImpl a chance
                    // to clean up the existing pipeline before the new pipeline comes in.
                    // @type {Set<string>}
                    let assignedDevices;
                    // @type {Map<string, Set<string>>}
                    const policies = this.policiesByDeviceModelUrn.get(deviceModelUrn);

                    if (policies) {
                        assignedDevices = policies.get(policyId);
                    } else {
                        assignedDevices = null;
                    }

                    if (assignedDevices) {
                        if (policyBeforeChange) {
                            this.notifyPolicyUnassigned(policyBeforeChange, assignedDevices);
                        }
                    }

                    this.processPipelineChanged(directlyConnectedDevice, deviceModelUrn, policyId,
                        lastModified).then(() =>
                    {
                        if (assignedDevices) {
                            // @type {DevicePolicy}
                            const policyAfterChange = this.policiesByPolicyId.get(policyId);

                            if (policyAfterChange) {
                                this.notifyPolicyAssigned(policyAfterChange, assignedDevices);
                            }
                        }
                    });
                } else {
                    console.log(requestMessage.payload.url + ' invalid operation: ' + item);
                }
            }
        } catch (error) {
            console.log('Error processing policyChanged notification: ' + error);
            // @type {iotcs.message.Message}
            return lib.message.Message.buildResponseMessage(requestMessage, 400, {}, error, '');
        }

        // @type {iotcs.message.Message}
        return lib.message.Message.buildResponseMessage(requestMessage, 200, {}, '', '');
    }

    /**
     * Process the "assign" operation from policyChanged.
     * The method needs to notify listeners that the policy was assigned,
     * then update data structures and persistence to add the association
     * of the policy to the device.
     *
     * @param {string} deviceModelUrn the device model urn of the policy that is unassigned.
     * @param {string} policyId the ID of the policy that is unassigned.
     * @param {string} endpointId the endpointId of the device that called the policyChanged method.
     * @param {boolean} dcdIsGatewayDevice whether or not that device is a gateway device
     * @param {number} lastModified
     */
    processAssign(deviceModelUrn, policyId, endpointId, dcdIsGatewayDevice, lastModified) {
        return new Promise((resolve, reject) => {
            // Get the set of devices to which this policy is assigned.
            // If the directly connected device is a gateway, then get
            // the assigned devices from the server. Otherwise, the
            // assigned device is the directly connected device with endpointId.
            //
            // Note that if the call to get the ICD ids that are assigned
            // to the policy throws an exception, unassign every device
            // with that device model urn. In such a case, we  remove the
            // mapping from device id the device-model urn from
            // policiesByDeviceId. If there is no mapping, getPolicy
            // will try to create a new mapping by getting the policy
            // for the device from the server.
            // @type {Set<string}
            let assignedDevices = null;

            if (dcdIsGatewayDevice) {
                assignedDevices = this.getIndirectlyConnectedDeviceIdsForPolicy(deviceModelUrn,
                    policyId, endpointId).then((assignedDevices, error) =>
                {
                    this.processAssignCouldNotGetIcds(false, deviceModelUrn, policyId);

                    if (!assignedDevices || assignedDevices.size === 0) {
                        return resolve();
                    }

                    this.processAssignHandleAssignedDevices(assignedDevices, deviceModelUrn,
                        policyId, lastModified);
                }).catch(error => {
                    this.processAssignCouldNotGetIcds(true, deviceModelUrn, policyId);
                    resolve();
                });
            } else {
                // @type {Set<string>}
                assignedDevices = new Set();
                assignedDevices.add(endpointId);

                if (!assignedDevices || assignedDevices.size === 0) {
                    return resolve();
                }

                this.processAssignHandleAssignedDevices(assignedDevices, deviceModelUrn, policyId,
                    lastModified);
            }
        });
    }

    processAssignHandleAssignedDevices(assignedDevices, deviceModelUrn, policyId, lastModified) {
        // Download the policy. The reason we have to download again on assign is that the policy
        // may have been modified while it was not assigned to this device or ICDs.
        // @type {DevicePolicy}
        const newPolicy = this.downloadPolicy(deviceModelUrn, policyId);
        this.policiesByPolicyId.put(policyId, newPolicy);

        // @type {string}
        assignedDevices.forEach(deviceId => {
            this.assignPolicyToDevice(deviceModelUrn, policyId, deviceId, lastModified);
        });

        // @type {DevicePolicy}
        const devicePolicy = this.policiesByPolicyId.get(policyId);

        if (devicePolicy != null) {
            this.notifyPolicyAssigned(devicePolicy, assignedDevices);
        }
    }

    /**
     *
     * @param {boolean} couldNotGetIcds
     * @param {string} deviceModelUrn
     * @param {string} policyId
     */
    processAssignCouldNotGetIcds(couldNotGetIcds, deviceModelUrn, policyId) {
        if (couldNotGetIcds) {
            // Remove the mappings for all devices that reference this policy
            // since we no longer know which policy they refer to. On next call
            // to getPolicy, this should self-correct.
            // @type {Map<string, Set<string>>}
            const deviceModelPolicies = this.policiesByDeviceModelUrn.get(deviceModelUrn);

            if (deviceModelPolicies) {
                // @type {Set<string>}
                const assignedDeviceIds = deviceModelPolicies.delete(policyId);

                if (assignedDeviceIds) {
                    assignedDeviceIds.forEach(deviceId => {
                        // @type {Map<string, string>}
                        const assignedPolicies = this.policiesByDeviceId.get(deviceId);

                        if (assignedPolicies) {
                            assignedPolicies.delete(deviceModelUrn);
                        }

                        //this.removePersistedAssociation(deviceModelUrn, policyId, deviceId);
                    });
                }
            }
        }
    }

    /**
     * Process the "change" operation from policyChanged. The method needs to fetch the policy.
     *
     * @param {DirectlyConnectedDevice} directlyConnectedDevice
     * @param {string} deviceModelUrn the device model URN of the policy that is unassigned.
     * @param {string} policyId the ID of the policy that is unassigned.
     * @param {number} lastModified
     * @return {Promise} a Promise which resolves to nothing.
     */
    processPipelineChanged(directlyConnectedDevice, deviceModelUrn, policyId, lastModified) {
        return new Promise((resolve, reject) => {
            // First, check to see if we have a copy, and if so, whether or not it is more recent.
            // @// @type {}pe {DevicePolicy}
            const currentDevicePolicy = this.policiesByPolicyId.get(policyId);

            if (currentDevicePolicy) {
                if (lastModified < currentDevicePolicy.getLastModified()) {
                    // Our copy is more recent, return.
                    return;
                }
            }

            // Our copy is older, download the policy.
            // Block getPolicy from accessing policiesByDeviceId while the policy is being updated.
            // @type {DevicePolicy}
            this.downloadPolicy(deviceModelUrn, policyId).then(devicePolicy => {
                if (devicePolicy) {
                    debug(directlyConnectedDevice._.internalDev._.tam.endpointId +
                        ' : Policy changed : "' + devicePolicy.toString());
                }

                resolve();
                // Nothing else to do here...
            });
        });
    }

    /**
     * Process the "unassign" operation from policyChanged.
     * The method updates the data structures and persistence to remove the association
     * of the policy to the device.
     *
     * @param {string} deviceModelUrn the device model URN of the policy that is unassigned.
     * @param {string} policyId the ID of the policy that is unassigned.
     * @param {string} endpointId the endpointId of the device that called the policyChanged method.
     * @param {boolean} dcdIsGatewayDevice whether or not that device is a gateway device.
     * @param {number} lastModified is the time the policy was last modified on the server.
     */
    processUnassign(deviceModelUrn, policyId, endpointId, dcdIsGatewayDevice, lastModified) {
        // Get the set of devices to which this policy is assigned.
        // This will be the difference of the set of devices the client
        // says are assigned and the set of devices the server says are
        // assigned (the server doesn't say who was unassigned, we can
        // only ask who is assigned).
        // @type {Set<string>}
        let unassignedDevices;
        // @type {Map<string, Set<string>>}
        const policies = this.policiesByDeviceModelUrn.get(deviceModelUrn);

        if (policies) {
            unassignedDevices = policies.get(policyId);

            if (!unassignedDevices) {
                return;
            }
        } else {
            // The client doesn't have any devices assigned to this policy.
            return;
        }

        // If we aren't a gateway device, then make sure the
        // assigned devices contains the directly connected device
        // endpoint ID, and ensure that the only element of
        // unassignedDevices is the directly connected device
        // endpont ID.
        if (!dcdIsGatewayDevice) {
            if (!unassignedDevices.has(endpointId)) {
                // This endpoint is not currently assigned to the policy.
                return;
            }

            unassignedDevices.clear();
            unassignedDevices.add(endpointId);
        }

        // Now get the set of devices to which this policy is assigned,
        // according to the server. Remove the set of server-assigned
        // devices from the client assigned devices so that
        // unassignedDevices is now the set of devices that have
        // been unassigned from this policy.
        //
        // If the directly connected device is not a gateway, then we
        // know that the subject of the unassign is the directly connected
        // device and there is no need to make a call to the server for
        // the assigned devices.
        //
        // Note that if the call to get the set of ICD ids that are assigned
        // to the policy might fail, throwing an exception. In this case,
        // there is no way to tell what devices belong to the policy or not.
        // To handle this situation, every device on the client that has
        // this policy will be be will unassign from the policy _and_
        // the device's mapping to the device model urn in policiesByDeviceId
        // will be removed. Removing the mapping ensures that getPolicy
        // will fetch the policy anew and the mapping will self correct.
        // The flag "couldNotGetIcds" lets us know that the call failed.
        // @type {boolean}
        let couldNotGetIcds = dcdIsGatewayDevice;

        if (dcdIsGatewayDevice) {
            try {
                // @type {Set<string>}
                const serverAssignedDevices =
                    this.getIndirectlyConnectedDeviceIdsForPolicy(deviceModelUrn, policyId,
                        endpointId);

                // Returned without error...couldNotGetIcds is false.
                couldNotGetIcds = false;
                unassignedDevices.clear(serverAssignedDevices);

                // If unassignedDevices is empty now that we removed
                // all the ICD ids from the server, we should return
                // since there are no devices on the client affected
                // by the change.
                if (unassignedDevices.size === 0) {
                    return;
                }
            } catch (error) {
                // ignored
            }
        }

        // @type {DevicePolicy}
        const devicePolicy = this.policiesByPolicyId.get(policyId);

        if (!devicePolicy) {
            throw new Error('Device policy is null.');
        }

        this.notifyPolicyUnassigned(devicePolicy, unassignedDevices);

        // Now unassignedDevices is the set of device IDs that have been unassigned from this policy.
        unassignedDevices.forEach(deviceId => {
            if (couldNotGetIcds) {
                // unassignPolicyFromDevice takes care of the entry in policiesByDeviceModelUrn
                // and takes care of un-persisting the device to policy association.
                // @type {Map<string, string}
                const devicePolicies = this.policiesByDeviceId.get(deviceId);

                if (devicePolicies != null) {
                    devicePolicies.delete(deviceModelUrn);
                }
            }
        });

        this.unassignPolicyFromDevice(deviceModelUrn, policyId, deviceId, lastModified);
}

    /**
     * Remove a listener from receiving notification of policies being added or removed.
     *
     * @param {object} changeListener the ChangeListener to remove.
     */
    removeChangeListener(changeListener) {
        if (!changeListener) {
            return;
        }

        this.changeListeners.delete(changeListener);
    }

    /**
     *
     * @param {string} deviceModelUrn
     * @param {string} policyId
     * @param {string} deviceId
     */
    removePolicy(deviceModelUrn, policyId, deviceId) {
        this.policiesByDeviceModelUrn.delete(deviceModelUrn);
        this.policiesByPolicyId.delete(policyId);
        this.policiesByDeviceId.delete(deviceId);
    }

    /**
     * Handle the logic for unassigning a policy from a device. The only reason for this
     * method to return false is if the client has a more recent change than what it
     * was told by the server.
     *
     * @param {string} deviceModelUrn the device model urn from which the policy is unassigned
     * @param {string} policyId the policy id of the policy that is unassigned
     * @param {string} deviceId the device from which the policy is unassigned
     * @param {number} lastModified the lastModification time from the change request on the server
     * @return {boolean} whether or not the policy was unassigned.
     */
    unassignPolicyFromDevice(deviceModelUrn, policyId, deviceId, lastModified) {
        // Sanity check... does this device have the unassigned policy?
        // @type {string}
        let currentPolicyId;
        // policiesByDeviceId is { <device-id> : { <device-model-urn> : <policy-id> } }
        // @type {Map<string, string>}
        const policies = this.policiesByDeviceId.get(deviceId);

        if (policies) {
            currentPolicyId = policies.get(deviceModelUrn);
        } else {
            currentPolicyId = null;
        }

        if (!currentPolicyId) {
            // Device doesn't have a policy ID right now, move on.
            return true;
        }

        // If badMapping is set to true, the policiesByDeviceId entry for
        // the device-model URN of this device is removed. On the next
        // call to getPolicy, the map will auto-correct.
        // @type {boolean}
        let badMapping = false;

        if (policyId !== currentPolicyId) {
            // Server is telling me to unassign a policy ID
            // that the client doesn't have assigned. If
            // the policy that is assigned is newer than
            // lastModified, then the client is right and
            // we move on. Otherwise, unassign whatever
            // policy the device has and let the state
            // auto-correct on the next call to getPolicy.
            // @type {DevicePolicy}
            const devicePolicy = this.policiesByPolicyId.get(currentPolicyId);

            if (devicePolicy) {
                if (devicePolicy.getLastModified() > lastModified) {
                    // Client info is newer, move on to the next device ID.
                    return false;
                }

                // Else, server info is newer so indicate that
                // this device has a bad mapping and let the
                // code fall through to continue processing
                // this device ID.
                badMapping = true;
            } else {
                // Oh my. The device maps to some policy that
                // the client doesn't know about. Remove the mapping
                // and policiesByPolicyId will self correct for this
                // device the next time getPolicy is called.
                // Note that since devicePolicy is null, getPolicy
                // will return null for this device and device model anyway,
                // so taking an axe to policiesByPolicyId is okay here.
                //
                // policiesByDeviceId is { <device-id> : { <device-model-urn> : <policy-id> } }
                // @type {Map<string, string>}
                const devicePolicies = this.policiesByDeviceId.get(deviceId);

                if (devicePolicies) {
                    devicePolicies.delete(deviceModelUrn);
                }

                //this.removePersistedAssociation(deviceModelUrn, currentPolicyId, deviceId);
                return true;
            }
        }

        // If the sanity check passes, then we are good to remove
        // the mapping to the device-model-urn from policiesByDeviceId
        // for this device.
        if (policies) {
            if (!badMapping) {
                // If nothing is wrong in our mapping, then
                // set the current policy for this device and
                // device model urn to null. This state causes
                // getPolicy to return null without further lookup.
                policies.set(deviceModelUrn, null);
            } else {
                // if there was something bad in our mapping,
                // the remove the deviceModelUrn entry altogether.
                // On the next call to getPolicy for this device
                // and device model, the map will be corrected.
                policies.delete(deviceModelUrn);
            }
        }

        //this.removePersistedAssociation(deviceModelUrn, policyId, deviceId);
        return true;
    }
}
