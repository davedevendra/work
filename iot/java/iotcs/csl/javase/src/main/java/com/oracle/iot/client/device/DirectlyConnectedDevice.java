/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.device;

import com.oracle.iot.client.SecureConnection;

import com.oracle.iot.client.StorageObject;
import com.oracle.iot.client.impl.StorageConnectionBase;
import com.oracle.iot.client.impl.device.DevicePolicyManager;
import com.oracle.iot.client.impl.device.MessagingPolicyImpl;
import com.oracle.iot.client.impl.device.PersistenceStore;
import com.oracle.iot.client.impl.device.PersistenceStoreManager;
import com.oracle.iot.client.impl.http.HttpSecureConnection;
import com.oracle.iot.client.impl.device.http.HttpSendReceiveImpl;
import com.oracle.iot.client.impl.device.mqtt.MqttSendReceiveImpl;
import com.oracle.iot.client.impl.device.mqtt.MqttSecureConnection;
import com.oracle.iot.client.message.*;
import oracle.iot.client.DeviceModel;

import com.oracle.iot.client.impl.DeviceModelFactory;
import com.oracle.iot.client.impl.device.ActivationManager;
import com.oracle.iot.client.impl.device.ActivationPolicyResponse;
import com.oracle.iot.client.impl.device.DirectActivationRequest;
import com.oracle.iot.client.impl.device.DirectActivationResponse;
import com.oracle.iot.client.impl.device.SendReceiveImpl;
import com.oracle.iot.client.trust.TrustedAssetsManager;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A directly-connected device is able to send messages to, and receive messages
 * from, the IoT server. The directly-connected device is assigned an
 * <em>activation identifier</em> when the device is registered on the server.
 * When the directly-connected device is activated, the server assigns an
 * <em>endpoint identifer</em> that is used by the <code>DirectlyConnectedDevice</code>
 * for sending messages to, and receiving messages from, the server.
 */
public class DirectlyConnectedDevice implements Closeable {
    private static final int USE_DEFAULT_TIMEOUT_VALUE = -1;

    private SendReceiveImpl sendReceiveImpl;
    final SecureConnection secureConnection;

    private final boolean isMqtt;
    protected final TrustedAssetsManager trustedAssetsManager;

    /**
     * Constructs a new {@code DirectlyConnectedDevice} instance that will use a
     * custom or default {@code TrustedAssetsManager} to store, load and handle the device
     * <a href="../../../../../overview-summary.html#configuration">configuration</a>.
     *
     * @throws GeneralSecurityException if the configuration could not be loaded.
     */
    public DirectlyConnectedDevice() throws GeneralSecurityException {
        this(TrustedAssetsManager.Factory.getTrustedAssetsManager(null));
    }

    /**
     * Constructs a new {@code DirectlyConnectedDevice} instance  with a
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
    public DirectlyConnectedDevice(Object context) throws GeneralSecurityException {
        this(TrustedAssetsManager.Factory.getTrustedAssetsManager(context));
    }

    /**
     * Constructs a new {@code DirectlyConnectedDevice} instance that will load
     * the device configuration from the given file path and password.
     * See <a href="../../../../../overview-summary.html#configuration">configuration</a> for details.
     * @param configFilePath the path of the configuration file
     * @param configFilePassword the configuration file password,
     *                   or {@code null} if the configurationFile is not encrypted
     *
     * @throws GeneralSecurityException if the configuration could not be
     *                                  loaded.
     */
    public DirectlyConnectedDevice(String configFilePath, String configFilePassword)
            throws GeneralSecurityException {
        this(configFilePath, configFilePassword, null);
    }

    /**
     * Constructs a new {@code DirectlyConnectedDevice} instance with a
     * platform specific context. The device configuration will be loaded
     * from the given file path and password.
     * See <a href="../../../../../overview-summary.html#configuration">configuration</a> for details.
     *
     * @param configFilePath the path of the configuration file
     * @param configFilePassword the configuration file password,
     *                   or {@code null} if the configurationFile is not encrypted
     *
     * @param context a platform specific object (e.g. application context),
     *                that needs to be associated with this client. In
     *                the case of Android, this is an {@code android.content.Context}
     *                provided by the application or service. In the case of Java SE,
     *                the parameter is not used and the value may be {@code null}.
     *
     * @throws GeneralSecurityException if the configuration could not be
     *                                  loaded.
     */
    public DirectlyConnectedDevice(String configFilePath, String configFilePassword,
                                   Object context) throws GeneralSecurityException {
        this(TrustedAssetsManager.Factory.getTrustedAssetsManager(configFilePath,
                configFilePassword, context));
    }

    /**
     * This constructor is used internally and is not intended for general use.
     *
     *
     * @param trustedAssetsManager the {@code TrustedAssetsManager} to store,
     *                            load and handle the device trust material.
     *
     * @throws IllegalArgumentException if {@code trustedAssetsManager} is {@code null}.
     * @throws GeneralSecurityException if the trust material could not be
     *         loaded, or the server scheme is not supported.
     */
    DirectlyConnectedDevice(TrustedAssetsManager trustedAssetsManager)
            throws GeneralSecurityException {
        // NOTE: This is intentionally packaged protected to remove from public API in v1.1
        this.trustedAssetsManager = trustedAssetsManager;

        final String scheme = trustedAssetsManager.getServerScheme();
        this.isMqtt = ("mqtts".equals(scheme));
        if (!this.isMqtt && ! "https".equals(scheme)) {
            throw new GeneralSecurityException("Unsupported server scheme: " + scheme);
        }

        this.secureConnection = createSecureConnection();

        if (trustedAssetsManager.isActivated()) {
            this.sendReceiveImpl = createSendReceiveImpl();

            final PersistenceStore persistenceStore =
                    PersistenceStoreManager.getPersistenceStore(getEndpointId());
            persistenceStore
                    .openTransaction()
                    .putOpaque(DevicePolicyManager.class.getName(), new DevicePolicyManager(secureConnection))
                    .commit();
            }
    }

    /**
     * Create a new {@code StorageObject} that will have a name with the given
     * object name prefixed with the device's endpoint ID and a directory
     * separator. The prefix addition can be disabled by setting the
     * {@code oracle.iot.client.disable_storage_object_prefix}
     * to {@code true}.
     * <p>
     * If {@code contentType} is {@code null}, the mime-type defaults to
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
    public StorageObject createStorageObject(String name,
            String contentType) throws IOException, GeneralSecurityException {
        final StorageConnectionBase storageConnection =
                StorageConnectionMap.getStorageConnection(secureConnection);
        return storageConnection.createStorageObject(getEndpointId(),
            name, contentType);
    }

    /**
     * Create a new {@code StorageObject} from the URI for a named object in storage.
     * @param uri the URI of the object in the storage cloud
     * @return a StorageObject for the object in storage
     * @throws IOException if there is an {@code IOException} raised by the runtime,
     * or a failure reported by the storage cloud
     * @throws GeneralSecurityException if there is an exception establishing a secure connection to the storage cloud
     */
    public StorageObject createStorageObject(String uri)
            throws IOException, GeneralSecurityException {
        final StorageConnectionBase storageConnection =
                StorageConnectionMap.getStorageConnection(secureConnection);
        return storageConnection.createStorageObject(uri);
    }

    /**
     * Activate the device. The device will be activated on the server if
     * necessary. When the device is activated on the server, the server
     * assigns an endpoint identifier to the device.
     * <p>
     * If the device is already activated, this method will throw an
     * IllegalStateException. The user should call the {@link #isActivated()}
     * method prior to calling activate.
     *
     * @param deviceModels should contain the device model type URNs of this directly connected device.
     *                The device is activated with the given device models.
     *                The {@code deviceModels} parameter is zero or more, comma separated, device model URNs.
     * @throws IOException if there is an I/O exception.
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid
     * @throws IllegalStateException if the device is already activated
     * @see #isActivated()
     */
    public final void activate(String ... deviceModels) throws IOException, GeneralSecurityException {
        if (isActivated()) {
            throw new IllegalStateException("already activated");
        }

        final ActivationPolicyResponse activationPolicyResponse =
                ActivationManager.getActivationPolicy(secureConnection, trustedAssetsManager.getClientId());

        if (getLogger().isLoggable(Level.FINEST)) {
            getLogger().log(Level.FINEST, "activationPolicyResponse: " + String.valueOf(activationPolicyResponse));
        }

        trustedAssetsManager.generateKeyPair(
                activationPolicyResponse.getKeyType(),
                activationPolicyResponse.getKeySize());

        final Set<String> deviceModelSet = new HashSet<String>();
        if (deviceModels != null) {
            for (String deviceModel : deviceModels) {
                deviceModelSet.add(deviceModel);
            }
        }
        deviceModelSet.add(ActivationManager.DIRECT_ACTIVATION_URN);
        if (this instanceof GatewayDevice) {
            deviceModelSet.add(ActivationManager.INDIRECT_ACTIVATION_URN);
        }
        final DirectActivationRequest directActivationRequest =
                ActivationManager.createDirectActivationRequest(trustedAssetsManager,
                        activationPolicyResponse.getHashAlgorithm(), deviceModelSet);

        if (getLogger().isLoggable(Level.FINEST)) {
            getLogger().log(Level.FINEST, "directActivationRequest: " + directActivationRequest.toString());
        }

        final DirectActivationResponse directActivationResponse =
                ActivationManager.postDirectActivationRequest(
                        secureConnection, directActivationRequest);

        if (getLogger().isLoggable(Level.FINEST)) {
            getLogger().log(Level.FINEST, "directActivationResponse: Endpoint state is: " +
                    directActivationResponse.getEndpointState());
        }
        String endpointId = directActivationResponse.getEndpointId();
        byte[] certificate = directActivationResponse.getCertificate();

        trustedAssetsManager.setEndPointCredentials(endpointId, certificate);

        // request to update accessToken
        secureConnection.disconnect();

        // need endpoint id to create sendReceiveImpl - see comments in ctor
        sendReceiveImpl = createSendReceiveImpl();

        final PersistenceStore persistenceStore =
                PersistenceStoreManager.getPersistenceStore(getEndpointId());
        persistenceStore
                .openTransaction()
                .putOpaque(DevicePolicyManager.class.getName(), new DevicePolicyManager(secureConnection))
                .commit();

        registerDevicePolicyResource();

    }

    /**
     * Returns whether the device is activated.
     *
     * @return whether the device is activated.
     */
    public final boolean isActivated() {
        return trustedAssetsManager.isActivated();
    }

    /**
     * Send messages to the server. This call will block until the server
     * responds to the message delivery, or a network timeout occurs. The
     * network timeout can be configured by setting the system property
     * {@code oracle.iot.client.responseTimeout}.
     * @param messages zero or more {@link DataMessage}, {@link AlertMessage},
     *                {@link ResponseMessage} and/or {@link ResourceMessage}
     *                 objects.
     * @throws IOException if there is an I/O exception when trying to send
     * messages.
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid
     * @throws IllegalStateException if the gateway device has not been
     * authenticated with the server.
     */
    public final void send(Message... messages) throws IOException, GeneralSecurityException {
        if (isActivated()) {

            if (getLogger().isLoggable(Level.FINE)) {
                if (messages.length > 1) {
                    getLogger().log(Level.FINE, getEndpointId() + " : Send  : ("
                            + messages.length + " messages follow)");
                }
                for (int n=0; n<messages.length; n++) {
                    final String dumpedMessage = dumpMessage(messages[n]);
                    final String counter =
                            messages.length > 1 ? String.format(" : % 5d : ", n+1) : " : Send  : ";

                    final StringBuilder stringBuilder =
                            new StringBuilder(getEndpointId())
                                    .append(counter)
                                    .append(dumpedMessage);
                    final String logMessage = stringBuilder.toString();
                    getLogger().log(Level.FINE, logMessage);
                }
            }

            sendReceiveImpl.send(messages);
        } else {
            throw new IllegalStateException("not activated");
        }
    }

    /**
     * Offer messages to be sent to the server. Whether or not the messages
     * are sent depends on policies that have been configured for the
     * attributes in the message. If there are no policies for the attributes
     * in the message, then this call is equivalent to calling {@link #send(Message...)}.
     * Depending on the policies, it is possible that all, some, or none of
     * the messages will be sent.
     * <p>
     * If messages are sent, this call will block until the server
     * responds to the message delivery, or a network timeout occurs. The
     * network timeout can be configured by setting the system property
     * {@code oracle.iot.client.responseTimeout}.
     * @param messages zero or more {@link DataMessage}, {@link AlertMessage},
     *                {@link ResponseMessage} and/or {@link ResourceMessage}
     *                 objects.
     * @throws IOException if there is an I/O exception when trying to send
     * messages.
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid
     * @throws IllegalStateException if the gateway device has not been
     * authenticated with the server.
     */
    public final void offer(Message... messages) throws IOException, GeneralSecurityException {
        if (isActivated()) {
            // We need to distinguish between an empty list of messages
            // that has been passed in versus an empty list of message
            // that has resulted from policies being applied.
            // So if the list we receive is empty, let send handle it.
            if ((messages == null || messages.length == 0)) {
                send(messages);
            }

            final PersistenceStore persistenceStore =
                    PersistenceStoreManager.getPersistenceStore(getEndpointId());
            final MessagingPolicyImpl messagingPolicyImpl;
            synchronized (persistenceStore) {
                final Object mpiObj = persistenceStore.getOpaque(MessagingPolicyImpl.class.getName(), null);
                if (mpiObj == null) {
                    messagingPolicyImpl = new MessagingPolicyImpl(this);
                    persistenceStore
                            .openTransaction()
                            .putOpaque(MessagingPolicyImpl.class.getName(), messagingPolicyImpl)
                            .commit();

                    final DevicePolicyManager devicePolicyManager
                            = DevicePolicyManager.getDevicePolicyManager(this);
                    devicePolicyManager.addChangeListener(messagingPolicyImpl);

                } else {
                    messagingPolicyImpl = MessagingPolicyImpl.class.cast(mpiObj);
                }
            }

            // Now we know here that messages list is not empty.
            // If the message list is not empty after applying policies,
            // then send the messages. If it is empty after applying the
            // policies, then there is nothing to send because messages
            // were filtered, or are aggregating values (e.g., mean policy).
            final List<Message> messagesToPost = new ArrayList<Message>();
            for(Message message : messages) {
                final Message[] messagesFromPolicies = messagingPolicyImpl.applyPolicies(message);
                if (messagesFromPolicies != null) {
                    Collections.addAll(messagesToPost, messagesFromPolicies);
                }
            }

            if (messagesToPost.size() > 0) {
                send(messagesToPost.toArray(new Message[messagesToPost.size()]));
            }
        } else {
            throw new IllegalStateException("not activated");
        }
    }

    /**
     * Receive a {@link RequestMessage} from the server, if any. This call may
     * block if the implementation sends a message to the server to poll
     * for requests (see {@link #send(Message...)}). This call may return {@code null}
     * if there are no requests from the server.
     * @param timeout maximum time, in milliseconds, to wait for a response from
     *                the server or -1 for an infinite timeout
     * @return a request from the server, or {@code null}.
     * @throws IOException if there is an I/O exception when trying to receive
     * messages.
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid
     * @throws IllegalStateException if the gateway device has not been
     * authenticated with the server.
     */
    public final RequestMessage receive(long timeout) throws IOException, GeneralSecurityException {
        if (!isActivated()) {
            throw new IllegalStateException("not activated");
        }

        long connectionTimeout;

        // Convert timeout to an java.net connection timeout
        if (timeout == 0) {
            connectionTimeout = USE_DEFAULT_TIMEOUT_VALUE;
        } else if (timeout < 0) {
            connectionTimeout = 0;
        } else {
            connectionTimeout = timeout;
        }

        final long t0 = System.currentTimeMillis();
        final RequestMessage requestMessage = sendReceiveImpl.receive(connectionTimeout);

        if (requestMessage != null) {
            final String path = requestMessage.getURL();
            if ("deviceModels/urn:oracle:iot:dcd:capability:device_policy/policyChanged".equals(path)) {
                try {
                    ResponseMessage responseMessage =
                            DevicePolicyManager.getDevicePolicyManager(this).policyChanged(this, requestMessage);
                    send(responseMessage);
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, e.getMessage());
                }

                final long remainingTime = connectionTimeout - (System.currentTimeMillis() - t0);
                if (remainingTime > 0) {
                    return receive(remainingTime);
                }

            } else {
                return requestMessage;
            }
        }

        return null;
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
        return trustedAssetsManager.getEndpointId();
    }

    /**
     * Get the {@link DeviceModel} for the device model urn. This method may
     * return {@code null} if there is no device model for the URN. {@code Null} may also be
     * returned if the device model is a &quot;draft&quot; and the property
     * {@code com.oracle.iot.client.device.allow_draft_device_models} is set to
     * {@code false}, which is the default.
     *
     * @param deviceModel the URN of the device model
     *
     * @return A representation of the device model or {@code null} if it does not exist
     *
     * @throws NullPointerException if deviceModel is {@code null}
     * @throws IOException if there is an I/O error when communicating
     * with the server
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid
     */
    public final DeviceModel getDeviceModel(String deviceModel)
            throws IOException, GeneralSecurityException {
        /*
         * The high level DirectlyConnectedDevice class has no trusted
         * assets manager and this class gives no access to the one it has,
         * so this method is here.
         * TODO: Find a high level class for this method
         */
        return DeviceModelFactory.getDeviceModel(secureConnection, deviceModel);
    }

    /**
     * Compare {@link DirectlyConnectedDevice} devices
     * @param obj {@link DirectlyConnectedDevice} to compare with {@code this}
     * @return {@code true} if equal.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;

        if (obj == null || this.getClass() != obj.getClass()) return false;

        DirectlyConnectedDevice other = (DirectlyConnectedDevice)obj;

        if (!trustedAssetsManager.getServerHost().equals(
                other.trustedAssetsManager.getServerHost())) {
            return false;
        }

        return trustedAssetsManager.getClientId().equals(
                other.trustedAssetsManager.getClientId());

    }

    @Override
    public int hashCode() {
        int hash = 37;
        hash = 37 * hash + this.trustedAssetsManager.getServerHost().hashCode();
        hash = 37 * hash + this.trustedAssetsManager.getClientId().hashCode();
        return hash;
    }

    /**
     * Implementation of java.io.Closeable interface required of Client
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        // also calls TrustedAssetsManager.close
        secureConnection.close();
    }

    private SendReceiveImpl createSendReceiveImpl() {

        if (isMqtt) {
            MqttSendReceiveImpl impl =
                    new MqttSendReceiveImpl((MqttSecureConnection) secureConnection);
            impl.initialize();
            return impl;

        } else {
            return new HttpSendReceiveImpl((HttpSecureConnection) secureConnection);
        }

    }

    private SecureConnection createSecureConnection()
            throws GeneralSecurityException {
        try {
        return isMqtt ?
            MqttSecureConnection.createSecureMqttConnection(
                trustedAssetsManager, this instanceof GatewayDevice) :
            HttpSecureConnection.createHttpSecureConnection(
                trustedAssetsManager, false);
        } catch(NoClassDefFoundError e) {
            if (isMqtt) {
                throw new GeneralSecurityException("Unable to initialize Mqtt Secure Connection, NoClassDefFoundError: " + e.getMessage()+".");
            }
            else {
                throw new GeneralSecurityException("Unable to initialize Http Secure Connection, NoClassDefFoundError: " + e.getMessage());
            }
        }
    }

    private void registerDevicePolicyResource() throws IOException, GeneralSecurityException {

        if (!isActivated()) return;

        final Resource resource = new Resource.Builder()
                .endpointName(getEndpointId())
                .method(Resource.Method.PUT)
                .path("deviceModels/urn:oracle:iot:dcd:capability:device_policy/policyChanged")
                .name("urn:oracle:iot:dcd:capability:device_policy")
                .build();

        final ResourceMessage resourceMessage = new ResourceMessage.Builder()
                .endpointName(getEndpointId())
                .source(getEndpointId())
                .register(resource)
                .build();

        sendReceiveImpl.send(resourceMessage);

    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }

    // If data or alert message, return data items formatted
    // as "<key>"="<value>"[,"<key>"="<value>"...]
    // (whether there are quotes around value depends on type)
    // e.g., "temperature"=24,"humidity"=51
    // Otherwise, return the type of message.
    private static String dumpMessage(Message message) {

        final List<DataItem<?>> dataItems;
        if (message instanceof DataMessage) {
            dataItems = ((DataMessage)message).getDataItems();
        } else if (message instanceof AlertMessage) {
            dataItems = ((AlertMessage)message).getDataItems();
        } else {
            dataItems = Collections.<DataItem<?>>emptyList();
        }

        final StringBuilder stringBuilder = new StringBuilder();
        boolean first = true;
        for (DataItem<?> dataItem : dataItems) {
            if (!first) {
                stringBuilder.append(',');
            } else {
                first = false;
            }

            stringBuilder
                    .append('"').append(dataItem.getKey()).append("\"=");
            switch (dataItem.getType()) {
                case STRING:
                default: {
                    final String val = (String)dataItem.getValue();
                    stringBuilder.append('"').append(val).append('"');
                    break;
                }
                case DOUBLE: {
                    final String val = Double.toString(((Number)dataItem.getValue()).doubleValue());
                    stringBuilder.append(val);
                    break;
                }
                case BOOLEAN: {
                    final String val = ((Boolean)dataItem.getValue()).toString();
                    stringBuilder.append(val);
                    break;
                }
            }
        }
        // add (DATA)
        if (!dataItems.isEmpty()) {
            stringBuilder.append(' ');
        }
        stringBuilder
                .append('(')
                .append(message.getType().alias())
                .append(')');

        return stringBuilder.toString();
    }

    /*
     * Private only
     */
    private static class StorageConnectionMap  {

        private static final Map<SecureConnection,WeakReference<StorageConnectionBase>> STORAGE_CONNECTION_MAP =
                new WeakHashMap<SecureConnection,WeakReference<StorageConnectionBase>>();

        private static StorageConnectionBase getStorageConnection(SecureConnection secureConnection) {
            WeakReference<StorageConnectionBase> reference = STORAGE_CONNECTION_MAP.get(secureConnection);
            StorageConnectionBase storageConnection = null;
            if (reference != null) {
                storageConnection = reference.get();
            }

            if (storageConnection == null) {
                storageConnection =  new com.oracle.iot.client.impl.device.StorageConnectionImpl(secureConnection);
                STORAGE_CONNECTION_MAP.put(secureConnection, new WeakReference<StorageConnectionBase>(storageConnection));
            }
            return storageConnection;
        }
    }
}
