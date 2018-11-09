/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.impl.device;

import com.oracle.iot.client.TransportException;
import com.oracle.iot.client.HttpResponse;
import com.oracle.iot.client.RestApi;
import com.oracle.iot.client.SecureConnection;
import com.oracle.iot.client.device.DirectlyConnectedDevice;
import com.oracle.iot.client.device.GatewayDevice;
import com.oracle.iot.client.message.RequestMessage;
import com.oracle.iot.client.message.ResponseMessage;
import com.oracle.iot.client.message.StatusCode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A manager for /iot/privateapi/v2/devicePolicies
 */
public final class DevicePolicyManager {

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }

    // A path to where the device policy files are stored locally.
    // LOCAL_STORE defaults to device_model_store if device_policy_store is not set.
    // If neither device_policy_store nor device_model_store are set, LOCAL_STORE will be null.
    // Device policies will not be stored locally if LOCAL_STORE is null or is not a valid path.
    final private static String LOCAL_STORE;

    static {
        // LOCAL_STORE defaults to device_model_store if device_policy_store is not set.
        // If neither device_policy_store nor device_model_store are set, LOCAL_STORE will be null.
        final String deviceModelStore = System.getProperty("oracle.iot.client.device_model_store", null);
        final String localStorePathname = System.getProperty("oracle.iot.client.device_policy_store", deviceModelStore);

        // If device_policy_store is set, then make sure it is readable/writable,
        // or try to create the directory if it does not exist.
        LOCAL_STORE = checkLocalStorePath(localStorePathname);
    }

    /**
     * An interface for receiving notification of policies being assigned or unassigned.
     */
    public interface ChangeListener {

        /**
         * A callback for notification that a policy has been assigned to some devices. The
         * listener should check to see if the device is in the set of assigned devices. If
         * the device is not in the set of assigned devices, the listener should take no action.
         * @param devicePolicy the policies that have been assigned.
         * @param assignedDevices the set of devices that to which the policy has been assigned.
         */
        void policyAssigned(DevicePolicy devicePolicy, Set<String> assignedDevices);

        /**
         * A callback for notification that a policy has been unassigned from some devices. The
         * listener should check to see if the device is in the set of unassigned devices. If
         * the device is not in the set of unassigned devices, the listener should take no action.
         * @param devicePolicy the policies that have been unassigned.
         * @param unassignedDevices the set of devices from which the policy has been unassigned.
         */
        void policyUnassigned(DevicePolicy devicePolicy, Set<String> unassignedDevices);
    }

    /**
     * Add a listener for receiving notification of policies being assigned or unassigned.
     * @param changeListener the {@link DevicePolicyManager.ChangeListener} to add
     */
    public void addChangeListener(ChangeListener changeListener) {
        if (changeListener == null) return;
        synchronized (changeListeners) {
            changeListeners.add(changeListener);
        }
    }

    /**
     * Remove a listener from receiving notification of policies being added or removed.
     * @param changeListener the {@link DevicePolicyManager.ChangeListener} to remove
     */
    public void removeChangeListener(ChangeListener changeListener) {
        if (changeListener == null) return;
        synchronized (changeListeners) {
            changeListeners.remove(changeListener);
        }

    }

    /**
     * Invoke {@code policyAssigned} method on change listeners.
     * @param devicePolicy the assigned policy
     * @param assignedDevices the devices to which the policy was assigned.
     */
    private void notifyPolicyAssigned(DevicePolicy devicePolicy, Set<String> assignedDevices) {

        if (devicePolicy == null || assignedDevices.isEmpty()) {
            return;
        }

        synchronized (changeListeners) {
            final Iterator<ChangeListener> iterator = changeListeners.iterator();
            while (iterator.hasNext()) {
                final ChangeListener changeListener = iterator.next();
                try {
                    changeListener.policyAssigned(devicePolicy, assignedDevices);
                } catch (Exception e) {
                    // listener may throw exception.
                    getLogger().log(Level.SEVERE, e.getMessage(), e);
                }
            }

        }
    }

    /**
     * Invoke {@code policyAssigned} method on change listeners.
     * @param devicePolicy the assigned policy
     * @param unassignedDevices the devices to which the policy was assigned.
     */
    private void notifyPolicyUnassigned(DevicePolicy devicePolicy, Set<String> unassignedDevices) {

        if (devicePolicy == null || unassignedDevices.isEmpty()) {
            return;
        }

        synchronized (changeListeners) {
            final Iterator<ChangeListener> iterator = changeListeners.iterator();
            while (iterator.hasNext()) {
                final ChangeListener changeListener = iterator.next();
                try {
                    changeListener.policyUnassigned(devicePolicy, unassignedDevices);
                } catch (Exception e) {
                    // listener may throw exception.
                    getLogger().log(Level.SEVERE, e.getMessage(), e);
                }
            }

        }
    }

    public static DevicePolicyManager getDevicePolicyManager(DirectlyConnectedDevice directlyConnectedDevice) {

            final PersistenceStore persistenceStore =
                    PersistenceStoreManager.getPersistenceStore(directlyConnectedDevice.getEndpointId());

            final Object dpmObj = persistenceStore.getOpaque(DevicePolicyManager.class.getName(), null);
            if (dpmObj == null) {
                getLogger().log(Level.SEVERE, "cannot access DevicePolicyManager for " + directlyConnectedDevice.getEndpointId());
                return null;
            }

            return DevicePolicyManager.class.cast(dpmObj);
    }

    /**
     * Get the {@code DevicePolicy} for the given device model and device id.
     * @param deviceModelURN the device model URN, which may not be {@code null}
     * @param deviceId the device id, which may not be {@code null}
     * @return a {@code DevicePolicy}, possibly {@code null}
     */
    public DevicePolicy getPolicy(String deviceModelURN, String deviceId) {

        // If we already have the policy for this device model and device, return it.
        // Otherwise, look it up. If there isn't an entry in policiesByDeviceId
        // for the device model urn and device id when this method is called,
        // there will be by the time this method completes.
        //
        synchronized (policiesByDeviceId) {

            // policiesByDeviceId looks like { <device-id> : { <device-model-urn> : <policy-id> }}
            final Map<String, String> devicePolicies = policiesByDeviceId.get(deviceId);

            // If devicePolicies is null, we drop through and do the lookup.
            if (devicePolicies != null) {
                //
                // If the deviceModelURN is not found in the map, we drop through and do the lookup.
                // There may be a mapping for the device model urn, but the value may be null,
                // which means that there is no policy for the combination of device model and device.
                //
                if (devicePolicies.containsKey(deviceModelURN)) {
                    final String policyId = devicePolicies.get(deviceModelURN);
                    final DevicePolicy devicePolicy;
                    if (policyId != null) {
                        // Very important that the client has gotten
                        // the policy before we get here!
                        devicePolicy = policiesByPolicyId.get(policyId);
                    } else {
                        devicePolicy =  null;
                    }
                    return devicePolicy;
                }
            }
        }

        // stop policyChanged while doing this lookup.
        policyChangeLock.lock();
        try {

            // If we get to here, then there was no mapping for the deviceId in policiesByDeviceId,
            // or there was a mapping for the deviceId, but not for the device model. So we need
            // to do a lookup and update policiesByDeviceId
            DevicePolicy devicePolicy = lookupPolicyForDevice(deviceModelURN, deviceId);

            // Add the mapping so the code doesn't try to fetch a policy for this
            // device again. The only way the device will get a policy after this
            // is from an "assigned" policyChanged, or when the client restarts.
            Map<String, String> polices = policiesByDeviceId.get(deviceId);
            if (polices == null) {
                polices = new HashMap<String, String>();
                policiesByDeviceId.put(deviceId, polices);
            }

            // Note: devicePolicy may be null, but the entry is made anyway.
            // This just means the device has no policy for this device model.
            // Adding null prevents another lookup.
            final String policyId = devicePolicy != null ? devicePolicy.getId() : null;
            polices.put(deviceModelURN, policyId);

            if (devicePolicy != null) {
                final Set<String> assignedDevices = new HashSet<String>();
                assignedDevices.add(deviceId);
                notifyPolicyAssigned(devicePolicy, assignedDevices);
            }

            return devicePolicy;
        } finally {
            policyChangeLock.unlock();
        }

    }

    /**
     * Handle {@code deviceModels/urn:oracle:iot:dcd:capability:device_policy/policyChanged}
     * @param requestMessage the RequestMessage from the server
     * @throws Exception if there is an exception from handling the request
     */
    public ResponseMessage policyChanged(DirectlyConnectedDevice directlyConnectedDevice,
                                                      RequestMessage requestMessage) {

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

        final boolean dcdIsGatewayDevice = directlyConnectedDevice instanceof GatewayDevice;
        final String endpointId = directlyConnectedDevice.getEndpointId();

        final String body = requestMessage.getBodyString();
        // block second half of getPolicy while doing an update.
        policyChangeLock.lock();
        try {

            final JSONArray items = new JSONArray(body);

            for (int n = 0, nMax = items.length(); n < nMax; n++) {

                final JSONObject item = items.getJSONObject(n);
                final String op = item.optString("op", "changed");
                final String deviceModelUrn = item.getString("deviceModelUrn");
                final String policyId = item.getString("id");
                final long lastModified = item.getLong("lastModified");

                if ("unassigned".equals(op)) {

                    processUnassign(
                            deviceModelUrn,
                            policyId,
                            endpointId,
                            dcdIsGatewayDevice,
                            lastModified);

                } else if ("assigned".equals(op)) {

                    processAssign(
                            deviceModelUrn,
                            policyId,
                            endpointId,
                            dcdIsGatewayDevice,
                            lastModified);

                } else if ("changed".equals(op)) {

                    final DevicePolicy policyBeforeChange = policiesByPolicyId.get(policyId);

                    // If device policy is null, then something is wrong in our mappings.
                    // Remove the references to this device model urn an policy id.
                    if (policyBeforeChange == null) {
                        final Map<String,Set<String>> policies = policiesByDeviceModelUrn.get(deviceModelUrn);
                        if (policies != null) {
                            final Set<String> assignedDevices = policies.remove(policyId);
                            if (assignedDevices != null) {
                                synchronized (policiesByDeviceId) {
                                    for (String deviceId : assignedDevices) {
                                        final Map<String, String> devicePolicies = policiesByDeviceId.get(deviceId);
                                        if (devicePolicies != null) {
                                            devicePolicies.remove(policyId);
                                        }
                                    }
                                }
                            }
                        }
                        continue;
                    }

                    // Before updating the policy, notify the devices the policy is unassigned.
                    // This gives the code in VirtualDeviceImpl or MessagingPolicyImpl a chance
                    // to clean up the exisiting pipeline before the new pipeline comes in.
                    final Set<String> assignedDevices;
                    final Map<String,Set<String>> policies =
                            policiesByDeviceModelUrn.get(deviceModelUrn);
                    if (policies != null) {
                        assignedDevices = policies.get(policyId);
                    } else {
                        assignedDevices = null;
                    }

                    if (assignedDevices != null) {
                        if (policyBeforeChange != null) {
                            notifyPolicyUnassigned(policyBeforeChange, assignedDevices);
                        }
                    }

                    processPipelineChanged(directlyConnectedDevice, deviceModelUrn, policyId, lastModified);

                    if (assignedDevices != null) {
                        final DevicePolicy policyAfterChange = policiesByPolicyId.get(policyId);
                        if (policyAfterChange != null) {
                            notifyPolicyAssigned(policyAfterChange, assignedDevices);
                        }
                    }


                } else {
                    getLogger().log(Level.WARNING, requestMessage.getURL() + " invalid operation: " + item);
                }
            }

        } catch (JSONException e) {
            getLogger().log(Level.SEVERE, requestMessage.getURL() + " body=" + body, e);
            final ResponseMessage responseMessage =
                    new ResponseMessage.Builder(requestMessage)
                            .statusCode(StatusCode.BAD_REQUEST)
                            .body(e.getMessage())
                            .build();
            return responseMessage;
        } finally {
            policyChangeLock.unlock();
        }

        final ResponseMessage responseMessage =
                new ResponseMessage.Builder(requestMessage)
                        .statusCode(StatusCode.OK)
                        .build();
        return responseMessage;
    }

    /**
     * Process the "change" operation from policyChanged.
     * The method needs to fetch the policy.
     * @param deviceModelUrn the device model urn of the policy that is unassigned
     * @param policyId the id of the policy that is unassigned.
     * @throws JSONException if there is an error parsing the JSON
     * @throws IOException if thrown from SecureConnection API
     * @throws GeneralSecurityException if thrown from SecureConnection API
     */
    private void processPipelineChanged(DirectlyConnectedDevice directlyConnectedDevice,
                                        String deviceModelUrn, String policyId, long lastModified) {

        // first, check to see if we have a copy,
        // and if so, whether or not it is more recent.
        final DevicePolicy currentDevicePolicy = policiesByPolicyId.get(policyId);

        if (currentDevicePolicy != null) {
            if (lastModified < currentDevicePolicy.getLastModified()) {
                // our copy is more recent, return.
                return;
            }
        }

        // Download the policy
        // block getPolicy from accessing policiesByDeviceId while the policy is being updated.
        final DevicePolicy devicePolicy;
        synchronized (policiesByDeviceId) {
            devicePolicy = downloadPolicy(deviceModelUrn, policyId);
        }
        if (devicePolicy != null && getLogger().isLoggable(Level.FINE))  {
            getLogger().log(Level.FINE,
                    directlyConnectedDevice.getEndpointId() + " : Policy changed : \"" + devicePolicy.toString());

        }
        // Nothing else to do here...
    }

    /**
     * Process the "assign" operation from policyChanged.
     * The method needs to notify listeners that the policy was assigned,
     * then update data structures and persistence to add the association
     * of the policy to the device.
     * @param deviceModelUrn the device model urn of the policy that is unassigned
     * @param policyId the id of the policy that is unassigned.
     * @param endpointId the endpointId of the device that called the policyChanged method
     * @param dcdIsGatewayDevice whether or not that device is a gateway device
     */
    private void processAssign(final String deviceModelUrn,
                               final String policyId,
                               final String endpointId,
                               final boolean dcdIsGatewayDevice,
                               final long lastModified) {


        // get the set of devices to which this policy is assigned.
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
        Set<String> assignedDevices = null;
        if (dcdIsGatewayDevice) {
            boolean couldNotGetIcds = true;
            try {
                assignedDevices =
                        getIndirectlyConnectedDeviceIdsForPolicy(deviceModelUrn, policyId, endpointId);

                couldNotGetIcds = false;

            } catch (IOException e) {
                // ignore
            } catch (GeneralSecurityException e) {
                // ignore
            } finally {

                if (couldNotGetIcds) {
                    // remove the mappings for all devices that reference this policy
                    // since we no longer know which policy they refer to. On next call
                    // to getPolicy, this should self-correct.
                    final Map<String, Set<String>> deviceModelPolicies = policiesByDeviceModelUrn.get(deviceModelUrn);
                    if (deviceModelPolicies != null) {

                        final Set<String> assignedDeviceIds = deviceModelPolicies.remove(policyId);
                        if (assignedDeviceIds != null) {
                            for (String deviceId : assignedDeviceIds) {
                                final Map<String, String> assignedPolicies = policiesByDeviceId.get(deviceId);
                                if (assignedPolicies != null) {
                                    assignedPolicies.remove(deviceModelUrn);
                                }
                                removePersistedAssociation(deviceModelUrn, policyId, deviceId);
                            }
                        }
                    }
                    return;
                }
            }

        } else {
            assignedDevices = new HashSet<String>();
            assignedDevices.add(endpointId);
        }

        if (assignedDevices == null || assignedDevices.isEmpty()) {
            return;
        }


        synchronized (policiesByDeviceId) {

            // Download the policy. The reason we have to download again
            // on assign is that the policy may have been modified
            // while it was not assigned to this device or ICDs.
            final DevicePolicy newPolicy = downloadPolicy(deviceModelUrn, policyId);
            policiesByPolicyId.put(policyId, newPolicy);

            for (String deviceId : assignedDevices) {
                assignPolicyToDevice(
                        deviceModelUrn,
                        policyId,
                        deviceId,
                        lastModified);
            }
        }

        final DevicePolicy devicePolicy =
                policiesByPolicyId.get(policyId);
        if (devicePolicy != null) {
            notifyPolicyAssigned(devicePolicy, assignedDevices);
        }

    }

    private void assignPolicyToDevice(final String deviceModelUrn,
                                      final String policyId,
                                      final String deviceId,
                                      final long lastModified) {

        // If the device has a policy currently,
        // it needs to be unassigned.
        final String currentPolicyId;
        final Map<String,String> policies = policiesByDeviceId.get(deviceId);
        if (policies != null) {
            currentPolicyId = policies.get(deviceModelUrn);
        } else {
            currentPolicyId = null;
        }

        // if the current policy is the same as the policy that is being
        // assigned, no need to continue.
        if (policyId.equals(currentPolicyId)) {
            return;
        }

        // Make sure we have the policy
        // before assigning the new policy.
        DevicePolicy devicePolicy= policiesByPolicyId.get(policyId);

        if (devicePolicy == null) {
            devicePolicy = loadLocalDevicePolicy(deviceModelUrn, policyId);
            if (devicePolicy == null) {
                devicePolicy = downloadPolicy(deviceModelUrn, policyId);
            }
            policiesByPolicyId.put(policyId, devicePolicy);

            if (devicePolicy != null && getLogger().isLoggable(Level.FINE)) {
                // replaceAll just fills space where device id should be since
                // the deviceId here doesn't matter for debug print, but it would
                // be nice to have the printout line up.
                getLogger().log(Level.FINE,
                        deviceId.replaceAll(".", " ") + " : Policy : "
                                + devicePolicy.getId() + "\n" + devicePolicy.toString());
            }
        }

        if (devicePolicy != null) {

            Map<String,String> devicePolicies = policiesByDeviceId.get(deviceId);
            if (devicePolicies == null) {
                devicePolicies = new HashMap<String,String>();
                policiesByDeviceId.put(deviceId, devicePolicies);
            }
            devicePolicies.put(deviceModelUrn, policyId);

            Map<String, Set<String>> deviceModelPolicies = policiesByDeviceModelUrn.get(deviceModelUrn);
            if (deviceModelPolicies == null) {
                deviceModelPolicies = new HashMap<String, Set<String>>();
                policiesByDeviceModelUrn.put(deviceModelUrn, deviceModelPolicies);
            }
            Set<String> assignedDevices = deviceModelPolicies.get(policyId);
            if (assignedDevices == null) {
                assignedDevices = new HashSet<String>();
                deviceModelPolicies.put(policyId, assignedDevices);
            }
            assignedDevices.add(deviceId);

            if (currentPolicyId != null) {
                removePersistedAssociation(deviceModelUrn, currentPolicyId, deviceId);
            }
            persistAssociation(deviceModelUrn, policyId, deviceId);
        }

    }

    /**
     * Process the "unassign" operation from policyChanged.
     * The method updates the data structures and persistence to remove the association
     * of the policy to the device.
     * @param deviceModelUrn the device model urn of the policy that is unassigned
     * @param policyId the id of the policy that is unassigned.
     * @param endpointId the endpointId of the device that called the policyChanged method
     * @param dcdIsGatewayDevice whether or not that device is a gateway device
     * @param lastModified is the time the policy was last modified on the server.
     * @return the set of devices affected by the unassign
     */
    private void processUnassign(final String deviceModelUrn,
                                 final String policyId,
                                 final String endpointId,
                                 final boolean dcdIsGatewayDevice,
                                 final long lastModified) {

        // Get the set of devices to which this policy is assigned. This
        // This will be the difference of the set of devices the client
        // says are assigned and the set of devices the server says are
        // assigned (the server doesn't say who was unassigned, we can
        // only ask who is assigned).

        final Set<String> unassignedDevices;
        final Map<String, Set<String>> policies = policiesByDeviceModelUrn.get(deviceModelUrn);
        if (policies != null) {
            unassignedDevices = policies.get(policyId);
            if (unassignedDevices == null) {
                return;
            }
        } else {
            // client doesn't have any devices assigned to this policy
            return;
        }

        // If we aren't a gateway device, then make sure the
        // assigned devices contains the directly connected device
        // endpoint id, and ensure that the only element of
        // unassignedDevices is the directly connected device
        // endpont id.
        if (!dcdIsGatewayDevice) {
            if (!unassignedDevices.contains(endpointId)) {
                // this endpoint is not currently assigned to the policy
                return;
            }
            unassignedDevices.clear();
            unassignedDevices.add(endpointId);
        }

        // Now get the set of devices to which this policy is assigned,
        // according to the server. Remove the set of server assigned
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
        boolean couldNotGetIcds = dcdIsGatewayDevice;
        if (dcdIsGatewayDevice) {
            try {
                final Set<String> serverAssignedDevices =
                        getIndirectlyConnectedDeviceIdsForPolicy(deviceModelUrn, policyId, endpointId);

                // returned without exception, couldNotGetIcds is false.
                couldNotGetIcds = false;

                unassignedDevices.removeAll(serverAssignedDevices);

                // if unassignedDevices is empty now that we removed
                // all the ICD ids from the server, we should return
                // since there are no devices on the client affected
                // by the change.
                if (unassignedDevices.isEmpty()) {
                    return;
                }
            } catch (IOException e) {
                // ignored
            } catch (GeneralSecurityException e) {
                // ignored
            }
        }

        final DevicePolicy devicePolicy = policiesByPolicyId.get(policyId);
        assert  devicePolicy != null;
        notifyPolicyUnassigned(devicePolicy, unassignedDevices);

        synchronized (policiesByDeviceId) {
            // Now unassignedDevices is the set of device ids that
            // have been unassigned from this policy.
            for (String deviceId : unassignedDevices) {

                unassignPolicyFromDevice(deviceModelUrn, policyId, deviceId, lastModified);

                if (couldNotGetIcds) {
                    // unassignPolicyFromDevice takes care of the entry in policiesByDeviceModelUrn
                    // and takes care of unpersisting the device to policy association.
                    final Map<String, String> devicePolicies = policiesByDeviceId.get(deviceId);
                    if (devicePolicies != null) {
                        devicePolicies.remove(deviceModelUrn);
                    }
                }
            }
        }
    }

    /**
     * Handle the logic for unassigning a policy from a device. The only reason for this
     * method to return false is if the client has a more recent change than what it
     * was told by the server.
     * @param deviceModelUrn the device model urn from which the policy is unassigned
     * @param policyId the policy id of the policy that is unassigned
     * @param deviceId the device from which the policy is unassigned
     * @param lastModified the lastModification time from the change request on the server
     * @return whether or not the policy was unassigned.
     */
    private boolean unassignPolicyFromDevice(String deviceModelUrn,
                                              String policyId,
                                              String deviceId,
                                              long lastModified) {

        // Sanity check... does this device have the unassigned policy?
        final String currentPolicyId;
        // policiesByDeviceId is { <device-id> : { <device-model-urn> : <policy-id> } }
        final Map<String, String> policies = policiesByDeviceId.get(deviceId);
        if (policies != null) {
            currentPolicyId = policies.get(deviceModelUrn);
        } else {
            currentPolicyId = null;
        }

        if (currentPolicyId == null) {
            // device doesn't have a policy id right now, move on.
            return true;
        }

        // if badMapping is set to true, the policiesByDeviceId entry for
        // the device-model urn of this device is removed. On the next
        // call to getPolicy, the map will auto-correct.
        boolean badMapping = false;

        if (!policyId.equals(currentPolicyId)) {

            // server is telling me to unassign a policy id
            // that the client doesn't have assigned. If
            // the policy that is assigned is newer than
            // lastModified, then the client is right and
            // we move on. Otherwise, unassign whatever
            // policy the device has and let the state
            // auto-correct on the next call to getPolicy.

            final DevicePolicy devicePolicy = policiesByPolicyId.get(currentPolicyId);

            if (devicePolicy != null) {
                if (devicePolicy.getLastModified() > lastModified) {
                    // client info is newer, move on to the next device id
                    return false;
                }
                // else, server info is newer so indicate that
                // this device has a bad mapping and let the
                // code fall through to continue processing
                // this device id.
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
                final Map<String, String> devicePolicies = policiesByDeviceId.get(deviceId);
                if (devicePolicies != null) {
                    devicePolicies.remove(deviceModelUrn);
                }
                removePersistedAssociation(deviceModelUrn, currentPolicyId, deviceId);
                return true;
            }
        }

        // If the sanity check passes, then we are good to remove
        // the mapping to the device-model-urn from policiesByDeviceId
        // for this device.
        if (policies != null) {
            if (!badMapping) {
                // If nothing is wrong in our mapping, then
                // set the current policy for this device and
                // device model urn to null. This state causes
                // getPolicy to return null without futher lookup.
                policies.put(deviceModelUrn, null);
            } else {
                // if there was something bad in our mapping,
                // the remove the deviceModelUrn entry altogether.
                // On the next call to getPolicy for this device
                // and device model, the map will be corrected.
                policies.remove(deviceModelUrn);
            }
        }

        removePersistedAssociation(deviceModelUrn, policyId, deviceId);

        return true;
    }

    /**
     * GET iot/privateapi/v2/deviceModels/{urn}/devicePolicies/{policyId}.
     * The gotten policy is persisted, the policiesByPolicyId map
     * is updated, and an entry is made (if necessary) in the
     * policiesByDeviceModelUrn map.
     * @param deviceModelUrn the device model urn
     * @param policyId the policy id
     * @return the DevicePolicy, or null if an error occured.
     */
    private DevicePolicy downloadPolicy(String deviceModelUrn, String policyId) {

        // get the policy from the server.
        final String urn;
        try {
            urn = URLEncoder.encode(deviceModelUrn, "UTF-8");
        } catch (UnsupportedEncodingException cannot_happen) {
            // UTF-8 is a required encoding.
            // Throw an exception here to make the compiler happy
            throw new RuntimeException(cannot_happen);
        }

        final String query;
        try {
            final String fields = URLEncoder.encode("id,pipelines,enabled,lastModified", "UTF-8");
            query = "?fields=" + fields;
        } catch (UnsupportedEncodingException cannot_happen) {
            // UTF-8 is a required encoding.
            // Throw an exception here to make the compiler happy
            throw new RuntimeException(cannot_happen);
        }

        final String uri = RestApi.V2.getPrivateRoot() + "/deviceModels/" + urn + "/devicePolicies/" + policyId + query;

        byte[] data = null;
        try {
            final HttpResponse res = secureConnection.get(uri);
            final int status = res.getStatus();
            if (status != StatusCode.OK.getCode()) {
                throw new TransportException(status, uri);
            }

            data = res.getData();

        } catch (IOException e) {
            getLogger().log(Level.WARNING, "GET " + uri, e);
        } catch (GeneralSecurityException e) {
            getLogger().log(Level.WARNING, "GET " + uri, e);
        }

        DevicePolicy devicePolicy = null;
        // if the GET doesn't find a match, data could be null or {},
        if (data != null && data.length > 2) {

            final String jsonString;
            try {
                jsonString = new String(data, "UTF-8");
            } catch (UnsupportedEncodingException cannot_happen) {
                // UTF-8 is a required encoding.
                // Throw runtime exception to make the compiler happy
                throw new RuntimeException(cannot_happen);
            }

            try {
                final JSONObject policyJson = new JSONObject(jsonString);
                devicePolicy = devicePolicyFromJSON(deviceModelUrn, policyJson);

                persistPolicy(policyJson);

                // update state
                policiesByPolicyId.put(policyId, devicePolicy);
                Map<String,Set<String>> policies = policiesByDeviceModelUrn.get(deviceModelUrn);
                if (policies == null) {
                    policies = new HashMap<String,Set<String>>();
                    policiesByDeviceModelUrn.put(deviceModelUrn, policies);
                }

                if (devicePolicy != null && getLogger().isLoggable(Level.FINE)) {
                    // replaceAll just fills space where device id should be since
                    // the device id here doesn't matter for debug print, but it would
                    // be nice to have the printout line up.
                    getLogger().log(Level.FINE,
                            policyId.replaceAll(".", " ") + " : Policy : "
                                    + devicePolicy.getId() + "\n" + devicePolicy.toString());
                }

            } catch (JSONException e) {
                getLogger().log(Level.SEVERE, e.getMessage());
            }
        }

        return devicePolicy;
    }


    /**
     * Lookup the policy for the combination of device model and device id on the server.
     * Should only be called from getPolicy when there is no policy for the device.
     * @param deviceModelUrn the device model urn
     * @param deviceId the device id to query for
     * @return the JSON policy, or {@code null} if there is no policy for the combination of deviceModelUrn and deviceId
     * @throws IOException is thrown by SecureConnection if there is a network error or unexpected HTTP response
     * @throws GeneralSecurityException is thrown by SecureConnection if there is an error from trusted assets
     */
    private DevicePolicy lookupPolicyForDevice(String deviceModelUrn, String deviceId) {

        // Do we already have the policy?
        Map<String,Set<String>> policies = policiesByDeviceModelUrn.get(deviceModelUrn);
        if (policies != null) {
            for (Map.Entry<String,Set<String>> entry : policies.entrySet()) {
                final String policyId = entry.getKey();
                final Set<String> deviceIds = entry.getValue();
                if (deviceIds.contains(deviceId)) {
                    final DevicePolicy devicePolicy = policiesByPolicyId.get(policyId);
                    if (devicePolicy != null) {
                        return devicePolicy;
                    }
                }
            }
        }

        // If we don't have the policy, then get it from the server.
        final String urn;
        try {
            urn = URLEncoder.encode(deviceModelUrn, "UTF-8");
        } catch (UnsupportedEncodingException cannot_happen) {
            // UTF-8 is a required encoding.
            // Throw an exception here to make the compiler happy
            throw new RuntimeException(cannot_happen);
        }

        final String query;
        try {
            final String devicesDotId = URLEncoder.encode("{\"devices.id\":\"" + deviceId + "\"}", "UTF-8");
            final String fields = URLEncoder.encode("id,pipelines,enabled,lastModified", "UTF-8");
            query = "?q=" + devicesDotId + "&fields=" + fields;
        } catch (UnsupportedEncodingException cannot_happen) {
            // UTF-8 is a required encoding.
            // Throw an exception here to make the compiler happy
            throw new RuntimeException(cannot_happen);
        }

        // GET iot/privateapi/v2/deviceModels/{urn}/devicePolicies/?q={"devices.id" : "device-id"}
        final String uri = RestApi.V2.getPrivateRoot() + "/deviceModels/" + urn + "/devicePolicies" + query;

        byte[] data = null;
        try {
            final HttpResponse res = secureConnection.get(uri);
            final int status = res.getStatus();
            if (status != StatusCode.OK.getCode()) {
                throw new TransportException(status, uri);
            }

            data = res.getData();

        } catch (IOException e) {
            getLogger().log(Level.WARNING, "GET " + uri, e);
        } catch (GeneralSecurityException e) {
            getLogger().log(Level.WARNING, "GET " + uri, e);
        }

        DevicePolicy devicePolicy = null;
        // if the GET doesn't find a match, data could be null or {},
        if (data != null && data.length > 2) {

            final String jsonString;
            try {
                jsonString = new String(data, "UTF-8");
            } catch (UnsupportedEncodingException cannot_happen) {
                // UTF-8 is a required encoding.
                // Throw runtime exception to make the compiler happy
                throw new RuntimeException(cannot_happen);
            }

            try {
                final JSONObject devicePolicies = new JSONObject(jsonString);
                final JSONArray items = devicePolicies.getJSONArray("items");

                //
                // items length should only ever be one or zero!
                // If zero items, then no policy for the device model matches the device id
                //
                if (items.length() > 0) {
                    JSONObject item = items.getJSONObject(0);
                    devicePolicy = devicePolicyFromJSON(deviceModelUrn, item);
                    persistPolicy(item);
                }

            } catch (JSONException e) {
                getLogger().log(Level.SEVERE, e.getMessage());
            }
        }

        // If we found a device policy, update our local state
        if (devicePolicy != null) {

            final String policyId = devicePolicy.getId();

            // Only put the policy in policiesByPolicyId if it isn't there already.
            // This prevents putting a changed policy, which should be processed
            // through policyChanged.
            if (!policiesByPolicyId.containsKey(policyId)) {
                policiesByPolicyId.put(policyId, devicePolicy);

                if (devicePolicy != null && getLogger().isLoggable(Level.FINE)) {
                    // replaceAll just fills space where device id should be since
                    // the device id here doesn't matter for debug print, but it would
                    // be nice to have the printout line up.
                    getLogger().log(Level.FINE,
                            policyId.replaceAll(".", " ") + " : Policy : "
                                    + devicePolicy.getId() + "\n" + devicePolicy.toString());
                }

            }

            // remember this policy maps to this device model.
            // Do not add the device id to the set of device ids here.
            // Do that in getPolicy (just to keep all of the device
            // id state updates in one place)
            policies = policiesByDeviceModelUrn.get(deviceModelUrn);
            if (policies == null) {
                policies = new HashMap<String,Set<String>>();
                policiesByDeviceModelUrn.put(deviceModelUrn, policies);
            }

            Set<String> deviceIds = policies.get(policyId);
            if (deviceIds == null) {
                deviceIds = new HashSet<String>();
                policies.put(policyId, deviceIds);
            }
            deviceIds.add(deviceId);

            persistAssociation(deviceModelUrn, devicePolicy.getId(), deviceId);

        }
        return devicePolicy;

    }

    /**
     * Get the ids of indirectly connected devices that are assigned to the policy.
     * @param deviceModelUrn the device model urn
     * @param policyId the policy id to query for
     * @return the set of indirectly connected device ids for this policy
     * @throws IOException is thrown by SecureConnection if there is a network error or unexpected HTTP response
     * @throws GeneralSecurityException is thrown by SecureConnection if there is an error from trusted assets
     */
    private Set<String> getIndirectlyConnectedDeviceIdsForPolicy(String deviceModelUrn,
                                                                String policyId,
                                                                String directlyConnectedOwner)
        throws IOException, GeneralSecurityException {


        final String urn;
        try {
            urn = URLEncoder.encode(deviceModelUrn, "UTF-8");
        } catch (UnsupportedEncodingException cannot_happen) {
            // UTF-8 is a required encoding.
            // Throw an exception here to make the compiler happy
            throw new RuntimeException(cannot_happen);
        }

        final String query;
        try {
            final String icdFilter =
                    URLEncoder.encode("{\"directlyConnectedOwner\":\"" + directlyConnectedOwner + "\"}", "UTF-8");
            query ="?q=" + icdFilter + "&fields=id";
        } catch (UnsupportedEncodingException cannot_happen) {
            // UTF-8 is a required encoding.
            // Throw an exception here to make the compiler happy
            throw new RuntimeException(cannot_happen);
        }

        // GET iot/privateapi/v2/deviceModels/{urn}/devicePolicies/{id}/devices?q={"directlyConnectedOwner" : "GW-endpoint-id"}
        final String uri =
                RestApi.V2.getPrivateRoot() + "/deviceModels/" + urn + "/devicePolicies/" + policyId + "/devices" + query;

        final HttpResponse res = secureConnection.get(uri);
        final int status = res.getStatus();

        if (status != StatusCode.OK.getCode()) {
            getLogger().log(Level.WARNING, res.getVerboseStatus("GET", uri));
        }

        byte[] data = res.getData();

        // no data, or empty JSON body
        if (data == null || data.length == 2) {
            return Collections.<String>emptySet();
        }

        final String body;
        try {
            body = new String(data, "UTF-8");
        } catch (UnsupportedEncodingException cannot_happen) {
            // UTF-8 is a required encoding.
            // Throw runtime exception to make the compiler happy
            throw new RuntimeException(cannot_happen);
        }

        final Set<String> icdIds = new HashSet<String>();
        try {
            final JSONObject jsonObject = new JSONObject(body);
            final JSONArray items = jsonObject.getJSONArray("items");
            for (int n=0, nMax=items.length(); n<nMax; n++) {
                final JSONObject item = items.getJSONObject(n);
                final String icdId = item.getString("id");
                icdIds.add(icdId);
            }

        } catch (JSONException e){
            getLogger().log(Level.SEVERE, e.getMessage());
        }

        return icdIds;

    }


    /**
     * Write the JSON policy to local persistence.
     * @param policy the policy
     */
    private void persistPolicy(JSONObject policy) {

        if (LOCAL_STORE == null) {
            return;
        }

        final String policyId;
        try {
            policyId = policy.getString("id");
        } catch (JSONException e) {
            getLogger().log(Level.WARNING, "policy not persisted: " + e.getMessage());
            return;
        }

        final File policyFile = new File(LOCAL_STORE, policyId);

        FileOutputStream fileOutputStream = null;
        try {
            // pass indent level = 4 to JSONObject toString() method to make file easier to read.
            final byte[] data = policy.toString(4).getBytes("UTF-8");
            fileOutputStream = new java.io.FileOutputStream(policyFile);
            fileOutputStream.write(data);

        } catch (UnsupportedEncodingException e) {
            // UTF-8 is a required encoding so we should never get here.
            throw new RuntimeException(e);

        } catch (JSONException e) {
            getLogger().log(Level.WARNING, policyFile.getName() + " could not be written: " + e.getMessage());
            return;

        } catch (FileNotFoundException e) {
            getLogger().log(Level.WARNING, policyFile.getName() + " could not be written: " + e.toString());

        } catch (IOException e) {
            getLogger().log(Level.WARNING, e.toString());

        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Update device-associations.json by adding an association between the device model urn and the policy,
     * and also between the policy and device id. A device can only be associated with one policy;
     * if an association for the device already exists, it is removed and the new association is added.
     *
     * @param deviceModelURN the device model urn
     * @param policyId the policy id
     * @param deviceId the device id
     */
    private synchronized void persistAssociation(String deviceModelURN, String policyId, String deviceId) {

        //
        // Add an association between the device model urn and the policy,
        // and also between the policy and device id to device-associations.json.
        //
        JSONObject associations = null;

        final File associationsFile = new File(LOCAL_STORE, "device-associations.json");
        FileInputStream fileInputStream = null;
        try {
            // Have to read byte by byte because Android's JSON API
            // does not support InputStream directly.
            fileInputStream = new FileInputStream(associationsFile);
            StringBuffer buffer = new StringBuffer();
            int c = -1;
            while ((c = fileInputStream.read()) != -1) {
                buffer.append((char) c);
            }
            associations = new JSONObject(buffer.toString());
        } catch (FileNotFoundException e) {
            // file not created yet, ignore.
            // TODO: Really need to check. FNFE could be because file is a directory
        } catch (IOException e) {
            // error reading file
            getLogger().log(Level.SEVERE, associationsFile.getName() + " could not be read: " + e.getMessage());
        } catch (JSONException e) {
            getLogger().log(Level.SEVERE, associationsFile.getName() + " corrupted: " + e.getMessage());
            return;
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException ignored) {
                }
            }
        }


        // Associations should look like this:
        // {
        //  "devicePolicyIdsToEndpointIds": {
        //    "devicePolicyId1": [
        //      "endpoint1",
        //      "endpoint2"
        //    ],
        //    "devicePolicyId2", [
        //      "endpointId3",
        //      "endpointId4"
        //    ]
        //  },
        //  "deviceModelUrnsToDevicePolicies": {[
        //    "deviceModelUrn1": [
        //      "devicePolicyId1",
        //      "devicePolicyId2"
        //    ],
        //    "deviceModelUrn2": [
        //      "devicePolicyId3",
        //      "devicePolicyId4"
        //    ]
        //  }
        // }
        if (associations == null) {
            try {
                associations = new JSONObject()
                        .put("devicePolicyIdsToEndpointIds", new JSONObject())
                        .put("deviceModelUrnsToDevicePolicies", new JSONObject());

            } catch (JSONException e) {
                getLogger().log(Level.WARNING, associationsFile.getName() + " could not be written: " + e.getMessage());
                return;
            }
        }

        try {
            // add device id to device policy association
            final JSONObject policyAssociations = associations.getJSONObject("devicePolicyIdsToEndpointIds");
            final JSONArray associatedEndpoints = policyAssociations.optJSONArray(policyId);
            if (associatedEndpoints != null) {
                // Look for endpoint id in associated endpoints. If not found, add it.
                boolean found = false;
                for (int n = 0, nMax = associatedEndpoints.length(); !found && n < nMax; n++) {
                    found = deviceId.equals(associatedEndpoints.getString(n));
                }
                if (!found) {
                    associatedEndpoints.put(deviceId);
                }
            } else {
                // associated endpoints not found for this policy id, create it
                policyAssociations.put(policyId, new JSONArray().put(deviceId));
            }

            // Now do the same thing for associating the device policy with the device model
            final JSONObject modelAssociations = associations.getJSONObject("deviceModelUrnsToDevicePolicies");
            final JSONArray associatedPolicies = modelAssociations.optJSONArray(deviceModelURN);
            if (associatedPolicies != null) {
                // Look for policy id in associated policies. If not found, add it.
                boolean found = false;
                for (int n = 0, nMax = associatedPolicies.length(); !found && n < nMax; n++) {
                    found = policyId.equals(associatedPolicies.getString(n));
                }
                if (!found) {
                    associatedPolicies.put(policyId);
                }
            } else {
                // associated policies not found for this device model urn, create it
                modelAssociations.put(deviceModelURN, new JSONArray().put(policyId));
            }

        } catch (JSONException e) {
            getLogger().log(Level.SEVERE, associationsFile.getName() + " corrupted: " + e.getMessage());
            return;
        }

        // Write the associations back
        FileOutputStream fileOutputStream = null;
        try {
            // pass indent level = 4 to JSONObject toString() method to make file easier to read.
            final byte[] bytes = associations.toString(4).getBytes("UTF-8");
            fileOutputStream = new java.io.FileOutputStream(associationsFile);
            fileOutputStream.write(bytes);

        } catch (UnsupportedEncodingException e) {
            // UTF-8 is a required encoding so we should never get here.
            throw new RuntimeException(e);

        } catch (JSONException e) {
            getLogger().log(Level.WARNING, associationsFile.getName() + " could not be written: " + e.getMessage());
            return;

        } catch (IOException e) {
            getLogger().log(Level.SEVERE, e.toString());

        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Update device-associations.json by removing the association between the policy and device id.
     *
     * @param deviceModelURN the device model urn
     * @param policyId the policy id
     * @param deviceId the device id
     */
    private synchronized void removePersistedAssociation(String deviceModelURN, String policyId, String deviceId) {

        //
        // Remove the association between the policy and device id in device-associations.json.
        //
        JSONObject associations = null;

        final File associationsFile = new File(LOCAL_STORE, "device-associations.json");
        FileInputStream fileInputStream = null;
        try {
            // Have to read byte by byte because Android's JSON API
            // does not support InputStream directly.
            fileInputStream = new FileInputStream(associationsFile);
            StringBuffer buffer = new StringBuffer();
            int c = -1;
            while ((c = fileInputStream.read()) != -1) {
                buffer.append((char) c);
            }
            associations = new JSONObject(buffer.toString());
        } catch (FileNotFoundException e) {
            return;
        } catch (IOException e) {
            // error reading file
            getLogger().log(Level.SEVERE, associationsFile.getName() + " could not be read: " + e.getMessage());
            return;
        } catch (JSONException e) {
            getLogger().log(Level.SEVERE, associationsFile.getName() + " corrupted: " + e.getMessage());
            return;
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException ignored) {
                }
            }
        }

        assert associations != null;
        if (associations == null) {
            return;
        }

        // Associations should look like this:
        // {
        //  "devicePolicyIdsToEndpointIds": {
        //    "devicePolicyId1": [
        //      "endpoint1",
        //      "endpoint2"
        //    ],
        //    "devicePolicyId2", [
        //      "endpointId3",
        //      "endpointId4"
        //    ]
        //  },
        //  "deviceModelUrnsToDevicePolicies": {[
        //    "deviceModelUrn1": [
        //      "devicePolicyId1",
        //      "devicePolicyId2"
        //    ],
        //    "deviceModelUrn2": [
        //      "devicePolicyId3",
        //      "devicePolicyId4"
        //    ]
        //  }
        // }

        try {
            // remove device id from device policy association
            final JSONObject policyAssociations = associations.getJSONObject("devicePolicyIdsToEndpointIds");
            final JSONArray associatedEndpoints = policyAssociations.optJSONArray(policyId);
            if (associatedEndpoints != null) {

                for (int n = associatedEndpoints.length()-1; 0 <= n; --n) {
                    if(deviceId.equals(associatedEndpoints.getString(n))) {
                        associatedEndpoints.remove(n);
                    }
                }
            }

        } catch (JSONException e) {
            getLogger().log(Level.SEVERE, associationsFile.getName() + " corrupted: " + e.getMessage());
            return;
        }

        // Write the associations back
        FileOutputStream fileOutputStream = null;
        try {
            // pass indent level = 4 to JSONObject toString() method to make file easier to read.
            final byte[] bytes = associations.toString(4).getBytes("UTF-8");
            fileOutputStream = new java.io.FileOutputStream(associationsFile);
            fileOutputStream.write(bytes);

        } catch (UnsupportedEncodingException e) {
            // UTF-8 is a required encoding so we should never get here.
            throw new RuntimeException(e);

        } catch (JSONException e) {
            getLogger().log(Level.WARNING, associationsFile.getName() + " could not be written: " + e.getMessage());
            return;

        } catch (IOException e) {
            getLogger().log(Level.SEVERE, e.toString());

        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Initialize policy maps from persisted data.
     */
    private void initializeFromPersistedData() {

        // NOTE: This is only called from the constructor and is not synchronized!
        if (LOCAL_STORE == null) {
            return;
        }

        final JSONObject associations;

        final File associationsFile = new File(LOCAL_STORE, "device-associations.json");
        FileInputStream fileInputStream = null;
        try {
            // Have to read byte by byte because Android's JSON API
            // does not support InputStream directly.
            fileInputStream = new FileInputStream(associationsFile);
            StringBuffer buffer = new StringBuffer();
            int c = -1;
            while ((c = fileInputStream.read()) != -1) {
                buffer.append((char)c);
            }
            associations = new JSONObject(buffer.toString());
        } catch (FileNotFoundException e) {
            // file does not exist, return null.
            return;
        } catch (IOException e) {
            // error reading file
            getLogger().log(Level.SEVERE, associationsFile.getName() + " could not be read: " + e.getMessage());
            return;
        } catch (JSONException e) {
            getLogger().log(Level.SEVERE, associationsFile.getName() + " corrupted: " + e.getMessage());
            return;
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException ignored) {
                }
            }
        }

        // Associations should look like this:
        // {
        //  "devicePolicyIdsToEndpointIds": {
        //    "devicePolicyId1": [
        //      "endpoint1",
        //      "endpoint2"
        //    ],
        //    "devicePolicyId2", [
        //      "endpointId3",
        //      "endpointId4"
        //    ]
        //  },
        //  "deviceModelUrnsToDevicePolicies": {[
        //    "deviceModelUrn1": [
        //      "devicePolicyId1",
        //      "devicePolicyId2"
        //    ],
        //    "deviceModelUrn2": [
        //      "devicePolicyId3",
        //      "devicePolicyId4"
        //    ]
        //  }
        // }

        try {
            final JSONObject modelAssociations = associations.getJSONObject("deviceModelUrnsToDevicePolicies");
            final JSONObject policyAssociations = associations.getJSONObject("devicePolicyIdsToEndpointIds");

            // This map is to remember what device model the policy maps to.
            // This makes it easy to populate policiesByDeviceId, which needs the
            // device model urn to go along with the policy id.
            // Map is { <policy id> : <device model urn> }
            final Map<String,String> policyIdToDeviceModelUrn = new HashMap<String,String>();

            // build up policesByDeviceModelUrn and policiesByPolicyId
            final Iterator<String> deviceModelUrns = modelAssociations.keys();
            while (deviceModelUrns.hasNext()) {
                final String deviceModelUrn = deviceModelUrns.next();

                final Map<String, Set<String>> deviceModelPolicies = new HashMap<String,Set<String>>();
                policiesByDeviceModelUrn.put(deviceModelUrn, deviceModelPolicies);

                final JSONArray policyIds = modelAssociations.getJSONArray(deviceModelUrn);
                for (int n=0, nMax=policyIds.length(); n < nMax; n++) {
                    final String policyId = policyIds.getString(n);
                    DevicePolicy devicePolicy = loadLocalDevicePolicy(deviceModelUrn, policyId);
                    if (devicePolicy == null) {
                        devicePolicy = downloadPolicy(deviceModelUrn, policyId);
                    }
                    if (devicePolicy != null) {

                        // add entry for policy-id to device-model-urn in policiesByDeviceModelUrn
                        // policiesByDeviceModelUrn is { <device-model-urn> : { <policy-id> : <set of device-id> }}
                        deviceModelPolicies.put(policyId, new HashSet<String>());

                        // remember which device model urn this policy came from
                        policyIdToDeviceModelUrn.put(policyId, deviceModelUrn);

                        // policiesByPolicyId is { <policy-id> : DevicePolicy }
                        policiesByPolicyId.put(policyId, devicePolicy);

                        if (getLogger().isLoggable(Level.FINE)) {
                            // replaceAll just fills space where device id should be since
                            // the device id here doesn't matter for debug print, but it would
                            // be nice to have the printout line up.
                            getLogger().log(Level.FINE,
                                    policyId.replaceAll(".", " ") + " : Policy : "
                                            + devicePolicy.getId() + "\n" + devicePolicy.toString());
                        }

                    }
                }
            }

            final Iterator<String> policyIds = policyAssociations.keys();
            while (policyIds.hasNext()) {
                final String policyId = policyIds.next();
                final String deviceModelUrn = policyIdToDeviceModelUrn.get(policyId);
                if (deviceModelUrn == null) {
                    // Something is wrong with the associations file.
                    // Skip the policy associations for this policyId. When
                    // the device looks for a policy, it won't have an entry
                    // in policiesByDeviceId, which will force a lookup and
                    // the associations will self-correct.
                    continue;
                }

                // policiesByDeviceModelUrn is { <device-model-urn> : { <policy-id> : <set of device-id> }}
                final Map<String, Set<String>> deviceModelPolicies =
                        policiesByDeviceModelUrn.get(deviceModelUrn);

                // deviceModelPolices should not be null since an entry for this
                // device-model-urn is made in policiesByDeviceModelUrn in the
                // previous loop.
                assert deviceModelPolicies != null;
                if (deviceModelPolicies == null) {
                    continue;
                }

                final Set<String> assignedDevices = deviceModelPolicies.get(policyId);
                // If assignedDevices is null, then there was no device policy
                // locally or on the server (see previous loop where it checks if
                // devicePolicy is null), otherwise an entry would have been made
                // for this policy-id in policiesByDeviceModelUrn for this device-model-urn.
                if (assignedDevices == null) {
                    continue;
                }

                final JSONArray deviceIds = policyAssociations.getJSONArray(policyId);
                for (int n=0, nMax=deviceIds.length(); n < nMax; n++) {
                    final String deviceId = deviceIds.getString(n);

                    // Add this device to the set of devices that have this policy
                    assignedDevices.add(deviceId);

                    // policiesByDeviceId is { <device-id> : { <device-model-urn> : <policy-id> }}
                    Map<String,String> devicePolicies = policiesByDeviceId.get(deviceId);
                    if (devicePolicies == null) {
                        devicePolicies = new HashMap<String,String>();
                        policiesByDeviceId.put(deviceId, devicePolicies);
                    }
                    devicePolicies.put(deviceModelUrn, policyId);
                }
            }

            // Note: The locally stored policy files are not read in at this time.
            // Better to do that lazily as some may not be in use.

        } catch (JSONException e) {
            getLogger().log(Level.SEVERE, associationsFile.getName() + " corrupted: " + e.getMessage());
        }
    }

    /**
     * Remove the policy from the local store.
     * NOT USED AT THIS TIME. LEAVE HERE FOR FUTURE.
     * @param devicePolicy
     */
    private void removePolicyFromLocalStore(DevicePolicy devicePolicy) {

        if (LOCAL_STORE == null) {
            return;
        }

        // Need policy id and device model urn to update device-associations.json
        final String policyId = devicePolicy.getId();
        final String deviceModelUrn = devicePolicy.getDeviceModelURN();

        final File policyFile = new File(LOCAL_STORE, policyId);
        if (policyFile.exists()) {
            policyFile.delete();
        }

        // remove the associations
        JSONObject associations = null;

        final File associationsFile = new File(LOCAL_STORE, "device-associations.json");
        FileInputStream fileInputStream = null;
        try {
            // Have to read byte by byte because Android's JSON API
            // does not support InputStream directly.
            fileInputStream = new FileInputStream(associationsFile);
            StringBuffer buffer = new StringBuffer();
            int c = -1;
            while ((c = fileInputStream.read()) != -1) {
                buffer.append((char)c);
            }
            associations = new JSONObject(buffer.toString());
        } catch (FileNotFoundException e) {
            // file does not exist
            return;
        } catch (IOException e) {
            // error reading file
            getLogger().log(Level.SEVERE, associationsFile.getName() + " could not be read: " + e.getMessage());
            return;
        } catch (JSONException e) {
            getLogger().log(Level.SEVERE, associationsFile.getName() + " corrupted: " + e.getMessage());
            return;
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException ignored) {
                }
            }
        }

        assert (associations != null);

        // Associations should look like this:
        // {
        //  "devicePolicyIdsToEndpointIds": {
        //    "devicePolicyId1": [
        //      "endpoint1",
        //      "endpoint2"
        //    ],
        //    "devicePolicyId2", [
        //      "endpointId3",
        //      "endpointId4"
        //    ]
        //  },
        //  "deviceModelUrnsToDevicePolicies": {
        //    "deviceModelUrn1": [
        //      "devicePolicyId1",
        //      "devicePolicyId2"
        //    ],
        //    "deviceModelUrn2": [
        //      "devicePolicyId3",
        //      "devicePolicyId4"
        //    ]
        //  }
        // }

        try {
            // Find the device model associated with the policy and remove the association.
            final JSONObject modelAssociations = associations.getJSONObject("deviceModelUrnsToDevicePolicies");
            final JSONArray associatedPolicies = modelAssociations.optJSONArray(deviceModelUrn);
            if (associatedPolicies != null) {
                for (int n = 0, nMax = associatedPolicies.length(); n < nMax; n++) {
                    final String id = associatedPolicies.getString(n);
                    if (policyId.equals(id)) {
                        associatedPolicies.remove(n);
                        break;
                    }
                }
            }

            // Remove all associations to the endpoint ids for policy that was removed.
            final JSONObject policyAssociations = associations.getJSONObject("devicePolicyIdsToEndpointIds");
            policyAssociations.remove(policyId);

        } catch (JSONException e) {
            getLogger().log(Level.SEVERE, associationsFile.getName() + " corrupted: " + e.getMessage());
        }

        // Write the associations back
        FileOutputStream fileOutputStream = null;
        try {
            // pass indent level = 4 to JSONObject toString() method to make file easier to read.
            final byte[] bytes = associations.toString(4).getBytes("UTF-8");
            fileOutputStream = new java.io.FileOutputStream(associationsFile);
            fileOutputStream.write(bytes);

        } catch (UnsupportedEncodingException e) {
            // UTF-8 is a required encoding so we should never get here.
            throw new RuntimeException(e);

        } catch (JSONException e) {
            getLogger().log(Level.WARNING, associationsFile.getName() + " could not be written: " + e.getMessage());
            return;

        } catch (IOException e) {
            getLogger().log(Level.SEVERE, e.toString());

        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException ignored) {
                }
            }
        }

    }

    // returns null if LOCAL_STORE is null, or there is no file for the device policy id
    private DevicePolicy loadLocalDevicePolicy(String deviceModelUrn, String devicePolicyId) {

        if (LOCAL_STORE == null || devicePolicyId == null) {
            return null;
        }

        final File policyFile = new File(LOCAL_STORE, devicePolicyId);

        Reader reader = null;
        try {

            // Because the Android JSON API does not have JSONTokener(InputStream inputStream)...
            reader = new InputStreamReader(new FileInputStream(policyFile));
            // The server sends the policies in an array named "items".
            // We do the same here so that the calling code doesn't have to
            // care whether it was a local file or from the server.
            final StringBuilder sb = new StringBuilder();
            int c;
            while ((c = reader.read()) != -1) {
                sb.append((char) c);
            }

            final String string = sb.toString();
                final JSONObject policyJson = new JSONObject(string);
            return devicePolicyFromJSON(deviceModelUrn, policyJson);

        } catch (JSONException e) {
            getLogger().log(Level.SEVERE, e.getMessage());

        } catch (FileNotFoundException e) {
            // The model is not local, so get it from the server
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is a required encoding so we should never get here.
            throw new RuntimeException(e);
        } catch (IOException e) {
            // An IOException other than FileNotFound or UnsupportedEncodingException
            // is going to be from Reader#read()
            getLogger().log(Level.SEVERE, policyFile.getName() + " could not be read: " + e.getMessage());
        } finally {
            if (reader != null) {
                try { reader.close(); }
                catch (IOException ignored) {}
            }
        }

        return null;
    }

    // GET privateapi/v2/deviceModels/{urn}/devicePolicies/{id}
    // {
    //     "id": "A unique ID for this policy configuration",
    //         "name": "This is the name for this policy configuration",
    //         "description": "This is the description for this policy",
    //         "pipelines" : [
    //     {
    //         "attribute": "name of the attribute", // if omitted or * it applies to all attributes
    //             "description" "description of this pipeline",
    //             "pipeline": [
    //         {
    //             "id": "This is the policy function",
    //                 "description": "This is the pipeline description",
    //                 "parameters": {
    //             "parameter-name": "parameter-value",
    //                 ...
    //         }
    //             ...
    //         ],
    //         },
    //     ... 
    // ]
    private static DevicePolicy devicePolicyFromJSON(String deviceModelURN, JSONObject jsonObject) throws JSONException {

        final String id = jsonObject.getString("id");
        final String description = jsonObject.optString("description");
        final Long lastModified = jsonObject.getLong("lastModified");
        final Boolean enabled = jsonObject.optBoolean("enabled", true);

        final Map<String,List<DevicePolicy.Function>> pipelines =
                new HashMap<String,List<DevicePolicy.Function>>();

        if (!jsonObject.has("pipelines")) {
            // just in case this is an old policy...
            return null;
        }

        final JSONArray pipelineArray = jsonObject.getJSONArray("pipelines");
        if (pipelineArray.length() == 0) {
            // empty pipeline...
            return null;
        }

        for (int index=0, indexMax=pipelineArray.length(); index<indexMax; index++) {
            final JSONObject attributePipeline = pipelineArray.getJSONObject(index);
            final String attributeName = attributePipeline.optString("attributeName", DevicePolicy.ALL_ATTRIBUTES());
            final JSONArray pipeline = attributePipeline.getJSONArray("pipeline");
            final List<DevicePolicy.Function> functions = new ArrayList<DevicePolicy.Function>();

            for (int n = 0, nMax = pipeline.length(); n < nMax; n++) {

                final JSONObject function = pipeline.getJSONObject(n);
                final String functionId = function.getString("id");
                final Map<String, Object> parameterMap = new HashMap<String, Object>();

                final JSONObject parameters = function.getJSONObject("parameters");
                final Iterator<String> keys = parameters.keys();
                while (keys.hasNext()) {
                    final String parameterName = keys.next();
                    final Object parameterValue = parameters.get(parameterName);

                    if ("action".equals(parameterName)) {

                        final JSONObject actionObject = (JSONObject) parameterValue;
                        final String actionName = actionObject.getString("name");
                        parameterMap.put("name", actionName);

                        final JSONArray actionArguments = actionObject.optJSONArray("arguments");
                        if (actionArguments != null && actionArguments.length() > 0) {
                            final List<Object> argumentList = new ArrayList<Object>();
                            parameterMap.put("arguments", argumentList);
                            for (int arg = 0, maxArg = actionArguments.length(); arg < maxArg; arg++) {
                                final Object argument = actionArguments.get(arg);
                                argumentList.add(argument);
                            }
                        }

                    } else if ("alert".equals(parameterName)) {

                        final JSONObject alertObject = (JSONObject) parameterValue;
                        final String urn = alertObject.getString("urn");
                        parameterMap.put("urn", urn);

                        final JSONObject fields = alertObject.getJSONObject("fields");
                        final Map<String, Object> fieldList = new HashMap<String, Object>();
                        final Iterator<String> fieldNames = fields.keys();
                        while (fieldNames.hasNext()) {
                            final String fieldName = fieldNames.next();
                            final Object fieldValue = fields.get(fieldName);
                            fieldList.put(fieldName, fieldValue);
                        }
                        parameterMap.put("fields", fieldList);

                        if (alertObject.has("severity")) {
                            parameterMap.put("severity", alertObject.getString("severity"));
                        }

                    } else {
                        parameterMap.put(parameterName, parameterValue);
                    }
                }

                functions.add(new DevicePolicy.Function(functionId, parameterMap));
            }

            pipelines.put(attributeName, Collections.unmodifiableList(functions));
        }


        return new DevicePolicy(
                id,
                deviceModelURN,
                pipelines,
                description,
                lastModified,
                enabled
        );
    }

    /**
     * Validate path provided in oracle.iot.client.device_policy_store
     * @param localStorePathname the value of the local store property
     * @return the absolute path to the local store directory, or {@code null} if the path is not valid
     */
    private static String checkLocalStorePath(String localStorePathname) {

        if (localStorePathname == null || "".equals(localStorePathname)) {
            return null;
        }

        final File file = new File(localStorePathname);
        if (file.exists()) {
            if (!file.isDirectory()) {
                getLogger().log(Level.WARNING, "Cannot local device policy store is not a directory: " + localStorePathname);
                return null;
            }
            if (file.canRead() && file.canWrite()) {
                return file.getAbsolutePath();
            }
            if (!file.canRead()) {
                getLogger().log(Level.WARNING, "Cannot read from local device policy store: " + localStorePathname);
            }
            if (!file.canWrite()) {
                getLogger().log(Level.WARNING, "Cannot write to local device policy store: " + localStorePathname);
            }

        } else if (file.mkdir()) {
            return file.getAbsolutePath();

        } else {
            getLogger().log(Level.WARNING, "Cannot create local device policy store: " + localStorePathname);
        }
        return null;
    }

    public DevicePolicyManager(SecureConnection secureConnection) {
        this.secureConnection = secureConnection;
        initializeFromPersistedData();
    }

    private final SecureConnection secureConnection;

    // Map a device id to the policies that are available to it.
    // The key is the device id.
    // The value is a map of device model URN to plicy id. The policy id
    // gets us to the configuration data.
    //
    // { <device-id> : { <device-model-urn> : <policy-id> }}
    private final Map<String, Map<String, String>> policiesByDeviceId =
            new HashMap<String, Map<String, String>>();

    // map policy id to policies.
    // The key is a policy id.
    // The value is a DevicePolicy
    // { <policy-id> : DevicePolicy }
    private final Map<String, DevicePolicy> policiesByPolicyId =
            new HashMap<String, DevicePolicy>();

    // model deviceModels/<device-model-urn>/devicePolicies/<policy-id>/devices
    // policiesByDeviceModelUrn is { <device-model-urn> : { <policy-id> : [ <device-id>...] }
    private final Map<String, Map<String, Set<String>>> policiesByDeviceModelUrn =
            new HashMap<String, Map<String, Set<String>>>();


    private final List<ChangeListener> changeListeners = new ArrayList<ChangeListener>();

    // Have to use lock between getPolicy and policyChanged, not synchronized method,
    // because policyChanged will call notifyPolicyAssigned/Unassigned, which will cause
    // the virutual device impl or the messaging policy impl to call getPolicy. If
    // we use syncrhonization at the method level, policyChanged will block getPolicy.
    private final Lock policyChangeLock = new ReentrantLock();

}
