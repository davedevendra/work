/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.device;

import com.oracle.iot.client.impl.device.ActivationManager;
import com.oracle.iot.client.impl.device.IndirectActivationRequest;
import com.oracle.iot.client.impl.device.IndirectActivationResponse;
import com.oracle.iot.client.trust.TrustException;
import com.oracle.iot.client.trust.TrustedAssetsManager;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A GatewayDevice is capable of registering indirectly&ndash;connected devices
 * and proxies messages for indirectly&ndash;connected devices.
 */
public class GatewayDevice extends DirectlyConnectedDevice {

    /**
     * The {@code manufacturer} attribute. This attribute is to be used
     * when setting the {@code manufacturer} value in the meta-data.
     */
    public static final String MANUFACTURER = "manufacturer";

    /**
     * The {@code modelNumber} attribute. This attribute is to be used
     * when setting the {@code modelNumber} value in the meta-data.
     */
    public static final String MODEL_NUMBER = "modelNumber";

    /**
     * The {@code serialNumber} attribute. This attribute is to be used
     * when setting the {@code serialNumber} value in the meta-data.
     */
    public static final String SERIAL_NUMBER = "serialNumber";

    /**
     * The {@code deviceClass} attribute. This attribute is to be used
     * when setting the {@code deviceClass} value in the meta-data.
     */
    public static final String DEVICE_CLASS = "deviceClass";

    /**
     * The {@code protocol} attribute. This attribute is to be used
     * when setting the {@code protocol} value in the meta-data.
     */
    public static final String PROTOCOL = "protocol";

    /**
     * The {@code protocolDeviceClass} attribute. This attribute is to be used
     * when setting the {@code protocolDeviceClass} value in the meta-data.
     */
    public static final String PROTOCOL_DEVICE_CLASS = "protocolDeviceClass";

    /**
     * The {@code protocolDeviceId} attribute. This attribute is to be used
     * when setting the {@code protocolDeviceId} value in the meta-data.
     */
    public static final String PROTOCOL_DEVICE_ID = "protocolDeviceId";

    /**
     * Constructs a new {@code GatewayDevice} instance that will use a
     * custom or default {@code TrustedAssetsManager} to store, load and handle the device
     * <a href="../../../../../overview-summary.html#configuration">configuration</a>.
     *
     * @throws GeneralSecurityException if the configuration could not be loaded.
     */
    public GatewayDevice() throws GeneralSecurityException {
        super();
    }

    /**
     * Constructs a new {@code GatewayDevice} instance  with a
     * platform specific context. A custom or default {@code TrustedAssetsManager}
     * will be used to store, load and handle the device trust material.
     * See <a href="../../../../../overview-summary.html#configuration">configuration</a> for details.
     *
     * @param context a platform specific object (e.g. application context),
     *                that needs to be associated with this client. In
     *                the case of Android, this is an {@code android.content.Context}
     *                provided by the application or service. In the case of Java SE,
     *                the parameter is not used and the value may be {@code null}.
     *
     * @throws GeneralSecurityException if the trust material could not be
     *                                  loaded.
     */
    public GatewayDevice(Object context) throws GeneralSecurityException {
        super(context);
    }

    /**
     * Constructs a new {@code GatewayDevice} instance that will load
     * the device configuration from the given file path and password.
     * See <a href="../../../../../overview-summary.html#configuration">configuration</a> for details.
     *
     * @param configFilePath the path of the configuration file
     * @param configFilePassword the configuration file password,
     *                   or {@code null} if the configurationFile is not encrypted
     *
     * @throws GeneralSecurityException if the configuration could not be
     *                                  loaded.
     */
    public GatewayDevice(String configFilePath, String configFilePassword)
            throws GeneralSecurityException {
        super(configFilePath, configFilePassword);
    }

    /**
     * Constructs a new {@code GatewayDevice} instance with platform
     * specific context. The device configuration will be loaded
     * from the given file path and password.
     * See <a href="../../../../../overview-summary.html#configuration">configuration</a> for details.
     *
     * @param configFilePath the path of the configuration file
     * @param configFilePassword the configuration file password,
     *                   or {@code null} if the configurationFile is not encrypted
     * @param context a platform specific object (e.g. application context),
     *                that needs to be associated with this client. In
     *                the case of Android, this is an {@code android.content.Context}
     *                provided by the application or service. In the case of Java SE,
     *                the parameter is not used and the value may be {@code null}.
     *
     * @throws GeneralSecurityException if the configuration could not be
     *                                  loaded.
     */
    public GatewayDevice(String configFilePath, String configFilePassword,
                         Object context) throws GeneralSecurityException {
        super(configFilePath, configFilePassword, context);
    }

    /**
     * This constructor is used internally and is not intended for general use.
     *
     * @param trustedAssetManager the {@code TrustedAssetManager} to store,
     *                            load and handle the device trust material.
     *
     * @throws NullPointerException if {@code trustedAssetManager} is {@code null}.
     * @throws GeneralSecurityException if the trust material could not be
     *                                  loaded.
     */
    GatewayDevice(TrustedAssetsManager trustedAssetManager) throws GeneralSecurityException {
        // NOTE: This is intentionally packaged protected to remove from public API in v1.1
        super(trustedAssetManager);
    }

    /**
     * Register an indirectly-connected device with the cloud service. This is
     * the equivalent of calling {@code registerDevice(false, hardwareId, metaData, deviceModels)}.
     * @param hardwareId an identifier unique within the cloud service instance
     * @param metaData The meta-data of the device
     * @param deviceModels should contain the device model type URNs supported by the indirectly connected device.
     *                The {@code deviceModels} parameter is one or more, comma separated, device model URNs.
     * @return The endpoint id of the indirectly-connected device
     * @throws IOException if the message could not be sent
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid
     * @throws IllegalArgumentException if {@code hardwareId} is null,
     * or {@code deviceModels} is null or zero length
     * @see #registerDevice(boolean, String, Map, String...)
     */
    public String registerDevice(String hardwareId, Map<String,String> metaData, String... deviceModels)
            throws IOException, GeneralSecurityException {

        return registerDevice(false, hardwareId, metaData, deviceModels);
    }

    /**
     * Register an indirectly-connected device with the cloud service and specify whether
     * the gateway device is required to have the appropriate credentials for activating
     * the indirectly-connected device.
     *
     * <p>
     *     The {@code restricted} parameter controls whether or not the client
     *     library is <em>required</em> to supply credentials for activating
     *     the indirectly-connected device. The client library will
     *     <em>always</em> supply credentials for an indirectly-connected
     *     device whose trusted assets have been provisioned to the client.
     *     If, however, the trusted assets of the indirectly-connected device
     *     have not been provisioned to the client, the client library can
     *     create credentials that attempt to restrict the indirectly connected
     *     device to this gateway device.
     * </p>
     * <p>
     *     Pass {@code true} for the {@code restricted} parameter
     *     to ensure the indirectly-connected device cannot be activated
     *     by this gateway device without presenting credentials. If {@code restricted}
     *     is {@code true}, the client library will provide credentials to the server.
     *     The server will reject the activation request if the indirectly connected
     *     device is not allowed to roam to this gateway device.
     * </p>
     * <p>
     *     Pass {@code false} to allow the indirectly-connected device to be activated
     *     without presenting credentials if the trusted assets of the
     *     indirectly-connected device have not been provisioned to the client.
     *     If {@code restricted} is {@code false}, the client library will provide
     *     credentials if, and only if, the credentials have been provisioned to the
     *     client. The server will reject the activation if credentials are required
     *     but not supplied, or if the provisioned credentials do not allow the
     *     indirectly connected device to roam to this gateway device.
     * </p>
     * <p>
     *     The {@code hardwareId} is a unique identifier within the cloud service
     *     instance and may not be {@code null}. If one is not present for the device,
     *     it should be generated based on other metadata such as: model, manufacturer,
     *     serial number, etc.
     * </p>
     * <p>
     *     The {@code metaData} Map should typically contain all the standard
     *     metadata (the constants documented in this class) along with any other
     *     vendor defined metadata.
     * </p>
     * @param restricted indicate whether or not credentials are required for
     *                   activating the indirectly connected device
     * @param hardwareId an identifier unique within the Cloud Service instance
     * @param metaData The metadata of the device
     * @param deviceModels should contain the device model type URNs supported by the indirectly connected device.
     *                The {@code deviceModels} parameter is one or more, comma separated, device model URNs.
     * @return The endpoint id of the indirectly-connected device
     * @throws IOException if the message could not be sent
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid
     * @throws IllegalArgumentException if {@code hardwareId} is null,
     * or {@code deviceModels} is null or zero length
     */
    public String registerDevice(boolean restricted, String hardwareId, Map<String,String> metaData, String... deviceModels)
            throws IOException, GeneralSecurityException {

        // hardware id must be provided
        if (hardwareId == null) {
            throw new IllegalArgumentException("hardwareId cannot be null");
        }

        if (deviceModels == null || deviceModels.length == 0) {
            throw new IllegalArgumentException("at least one device model must be given");
        }
        final Set<String> deviceModelSet = new HashSet<String>();
        java.util.Collections.addAll(deviceModelSet, deviceModels);

        final byte[] data = getEndpointId().getBytes("UTF-8");

        // If the ICD has been provisioned, use the shared secret to generate the
        // signature for the indirect activation request.
        byte[] signature = null;
        try {
            // If this call throws a TrustException, then the ICD has not been provisioned.
            signature =
                    trustedAssetsManager.signWithSharedSecret(data, "HmacSHA256", hardwareId);
        } catch (TrustException e) {
            if (e.getCause() != null) {
                // This is tightly coupled to the implementation in TrustedAssetsManagerBase.
                // If cause is null, then the ICD was not provisioned.
                throw e;
            }
            signature = null;
        }

        // If the signature is null, then the ICD was not provisioned. But if
        // the restricted flag is true, then we generate a signature which will
        // cause the ICD to be locked (for roaming) to the gateway 
        if (restricted && signature == null) {
            signature =
                    trustedAssetsManager.signWithPrivateKey(data, "SHA256withRSA");
        }

        final IndirectActivationRequest indirectActivationRequest =
                ActivationManager.createIndirectActivationRequest(
                        hardwareId,
                        metaData,
                        deviceModelSet,
                        signature);

        final IndirectActivationResponse indirectActivationResponse =
                ActivationManager.postIndirectActivationRequest(secureConnection, indirectActivationRequest);
//        getLogger().info(
//            "indirectActivationResponse: Endpoint state is: " +
//            indirectActivationResponse.getEndpointState());
        return indirectActivationResponse.getEndpointId();
    }
    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
