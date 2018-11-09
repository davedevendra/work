/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package oracle.iot.client.device;

import com.oracle.iot.client.device.persistence.BatchByPersistence;
import com.oracle.iot.client.device.util.MessageDispatcher;
import com.oracle.iot.client.device.persistence.MessagePersistence;
import com.oracle.iot.client.impl.DeviceModelImpl;
import com.oracle.iot.client.impl.device.ActivationManager;
import com.oracle.iot.client.impl.device.MessageDispatcherImpl;
import com.oracle.iot.client.impl.device.VirtualDeviceImpl;
import oracle.iot.client.Client;
import oracle.iot.client.DeviceModel;
import oracle.iot.client.StorageObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.logging.Logger;

/**
 * A directly-connected device is able to send messages to, and receive messages
 * from, the IoT server. The directly-connected device is assigned an
 * <em>activation identifier</em> when the device is registered on the server.
 * When the directly-connected device is activated, the server assigns an
 * <em>endpoint identifer</em> that is used by the <code>DirectlyConnectedDevice</code>
 * for sending messages to, and receiving messages from, the server.
 */
public class DirectlyConnectedDevice extends Client<VirtualDevice> {

    final com.oracle.iot.client.device.DirectlyConnectedDevice dcdImpl;

    /**
     * Constructs a new {@code DirectlyConnectedDevice} instance that will use a
     * custom or default {@code TrustedAssetsManager} to store, load and handle the device
     * <a href="../../../../overview-summary.html#configuration">configuration</a>.
     *
     * @throws GeneralSecurityException if the configuration could not be loaded,
     *         or the server scheme is not supported.
     */
    public DirectlyConnectedDevice() throws GeneralSecurityException {
        this(null);
    }

    /**
     * Constructs a new {@code DirectlyConnectedDevice} instance with a
     * platform specific context. A custom or default {@code TrustedAssetsManager}
     * will be used to store, load and handle the device trust material.
     * See <a href="../../../../overview-summary.html#configuration">configuration</a> for details.
     *
     * @param context a platform specific object (e.g. application context),
     *                that needs to be associated with this client. In
     *                the case of Android, this is an {@code android.content.Context}
     *                provided by the application or service. In the case of Java SE,
     *                the parameter is not used and the value may be {@code null}.
     *
     * @throws GeneralSecurityException if the trust material could not be
     *         loaded, or the server scheme is not supported.
     */
    public DirectlyConnectedDevice(Object context) throws GeneralSecurityException {
        super(context);
        this.dcdImpl = new com.oracle.iot.client.device.DirectlyConnectedDevice(context);

        // bootstrap message persistence
        final MessagePersistence messagePersistence = MessagePersistence.initMessagePersistence(context);

        // bootstrap BatchByPersistence
        final BatchByPersistence batchByPersistence = BatchByPersistence.initBatchByPersistence(messagePersistence);

        bootstrapMessageDispatcher();
    }

    /**
     * Constructs a new {@code DirectlyConnectedDevice} instance that will load
     * the device configuration from the given file path and password.
     * See <a href="../../../../overview-summary.html#configuration">configuration</a> for details.
     * @param configFilePath the path of the configuration file
     * @param configFilePassword the configuration file password,
     *                   or {@code null} if the configurationFile is not encrypted
     *
     * @throws GeneralSecurityException if the configuration could not be
     *         loaded, or the server scheme is not supported.
     */
    public DirectlyConnectedDevice(String configFilePath, String configFilePassword)
            throws GeneralSecurityException {
        this(configFilePath, configFilePassword, (Object)null);
    }

    /**
     * Constructs a new {@code DirectlyConnectedDevice} instance with a
     * platform specific context. The device configuration will be loaded
     * from the given file path and password.
     * See <a href="../../../../overview-summary.html#configuration">configuration</a> for details.
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
     *         loaded, or the server scheme is not supported.
     */
    public DirectlyConnectedDevice(String configFilePath, String configFilePassword,
                                   Object context) throws GeneralSecurityException {
        super(context);
        this.dcdImpl =
                new com.oracle.iot.client.device.DirectlyConnectedDevice(configFilePath,
                        configFilePassword, context);

        // bootstrap message persistence
        final MessagePersistence messagePersistence = MessagePersistence.initMessagePersistence(context);

        // bootstrap BatchByPersistence
        final BatchByPersistence batchByPersistence = BatchByPersistence.initBatchByPersistence(messagePersistence);

        bootstrapMessageDispatcher();

    }

    /**
     * Constructs used by GatewayDevice. This method is used internally and
     * is not intended for general use.
     *
     * @param dcdImpl an implementation of a low-level DirectlyConnectedDevice
     * @param context a platform specific object (e.g. application context),
     *                that needs to be associated with this client. In
     *                the case of Android, this is an {@code android.content.Context}
     *                provided by the application or service. In the case of Java SE,
     *                the parameter is not used and the value may be {@code null}.
     */
    DirectlyConnectedDevice(com.oracle.iot.client.device.DirectlyConnectedDevice dcdImpl, Object context) {
        // NOTE: This is intentionally packaged protected to remove from public API in v1.1
        super(context);
        this.dcdImpl = dcdImpl;

        // bootstrap message persistence
        final MessagePersistence messagePersistence = MessagePersistence.initMessagePersistence(context);

        // bootstrap BatchByPersistence
        final BatchByPersistence batchByPersistence = BatchByPersistence.initBatchByPersistence(messagePersistence);

        bootstrapMessageDispatcher();
    }

    /**
     * Activate the device. The device will be activated on the server if
     * necessary. When the device is activated on the server, the server
     * assigns an endpoint identifier to the device.
     * <p>
     * If the device is already activated, this method will throw an
     * IllegalArgumentException. The user should call the {@link #isActivated()}
     * method prior to calling activate.
     * @param deviceModels should contain the device model type URNs of this directly connected device.
     *                The device is activated with the given device models.
     *                The {@code deviceModels} parameter is zero or more, comma separated, device model URNs.
     * @throws IOException if there is an I/O exception.
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid
     * @throws IllegalStateException if the device has already been activated
     * @see #isActivated()
     */
    public final void activate(String ... deviceModels) throws IOException, GeneralSecurityException {

        // Make sure to add diagnostics and message_dispatcher capability URNs.
        // DCD impl activate takes the array and stuffs the elements into a Set,
        // so there is no need to worry about duplicates in the array.
        String[] dmArray = new String[deviceModels != null ? deviceModels.length+3 : 3];
        dmArray[0] = ActivationManager.DIAGNOSTICS_URN;
        dmArray[1] = ActivationManager.MESSAGE_DISPATCHER_URN;
        dmArray[2] = "urn:oracle:iot:dcd:capability:device_policy";
        if (deviceModels != null) System.arraycopy(deviceModels, 0, dmArray, 3, deviceModels.length);
        dcdImpl.activate(dmArray);

        bootstrapMessageDispatcher();
    }

    /**
     * Returns whether the device has been activated.
     *
     * @return whether the device has been activated.
     */
    public final boolean isActivated() {
        return dcdImpl.isActivated();
    }

    /**
     * Return the endpoint identifier of this directly-connected
     * device. The endpoint identifier is assigned by the server
     * as part of the activation process.
     * @return the endpoint identifier of this directly-connected
     * device.
     * @see #activate(String...)
     */
    public final String getEndpointId() {
        return dcdImpl.getEndpointId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final VirtualDevice createVirtualDevice(String endpointId, DeviceModel deviceModel) {
        if (endpointId == null) {
            throw new NullPointerException("endpointId may not be null");
        }
        if (deviceModel == null) {
            throw new NullPointerException("deviceModel may not be null");
        }
        if (!(deviceModel instanceof DeviceModelImpl)) {
            throw new IllegalArgumentException("device model must be an instanceof com.oracle.iot.client.impl.DeviceModelImpl");
        }
        return new VirtualDeviceImpl(dcdImpl, endpointId, (DeviceModelImpl)deviceModel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final DeviceModel getDeviceModel(String deviceModelUrn)
            throws IOException, GeneralSecurityException {

        final DeviceModel deviceModel = dcdImpl.getDeviceModel(deviceModelUrn);
        return deviceModel;
    }

    /**
     * Implementation of java.io.Closeable interface required of Client
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        try {
            // if not activated, 'close()' call produce Exception, and not needed in fact
            if (isActivated()) {
                final MessageDispatcher messageDispatcher =
                        MessageDispatcher.removeMessageDispatcher(dcdImpl);
                if (messageDispatcher != null) {
                    messageDispatcher.close();
                }
            }
        } finally {
            // If MessageDispatcher close throws an exception, we still call
            // messagingDeviceClient.close(). If MessageDispatcher close throws an
            // exception AND messagingDeviceClient.close() throws an exception,
            // then the messagingDeviceClient.close() exception is thrown. If
            // MessageDispatcher close throws an exception and messagingDeviceClient.close()
            // exits normally, then the exception from MessageDispatcher close
            // is thrown.
            dcdImpl.close();
        }
    }

    /**
     * Create a new {@code StorageObject} that will have a name with the given
     * object name prefixed with the device's endpoint ID and a directory
     * separator. The prefix addition can be disabled by setting the
     * {@code oracle.iot.client.disable_storage_object_prefix}
     * to {@code true}.
     * <p>
     * If {@code contentType} is null, the mime-type defaults to
     * "application/octet-stream".
     * @param name the unique name to be used to reference the content in
     *             storage
     * @param contentType The mime-type of the content or {@code null}
     * @return a StorageObject
     * @throws IOException if there is an {@code IOException} raised by the
     * runtime, or an abnormal response from the storage cloud
     * @throws GeneralSecurityException if there is an exception establishing
     * a secure connection to the storage cloud
     */
    @Override
    public StorageObject createStorageObject(String name, String contentType) throws IOException, GeneralSecurityException {
        final com.oracle.iot.client.StorageObject delegate =
            dcdImpl.createStorageObject(name, contentType);
        return new com.oracle.iot.client.impl.device.StorageObjectImpl(dcdImpl, delegate);
    }

    private void bootstrapMessageDispatcher() {

        if (!isActivated()) return;

        final MessageDispatcher messageDispatcher = new MessageDispatcherImpl(dcdImpl);
        MessageDispatcher.setMessageDispatcher(dcdImpl, messageDispatcher);

    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }

}
