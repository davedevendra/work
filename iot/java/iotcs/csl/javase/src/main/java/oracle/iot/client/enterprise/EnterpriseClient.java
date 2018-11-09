/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package oracle.iot.client.enterprise;

import com.oracle.iot.client.HttpResponse;
import com.oracle.iot.client.SecureConnection;
import com.oracle.iot.client.impl.DeviceModelFactory;
import com.oracle.iot.client.impl.DeviceModelImpl;
import com.oracle.iot.client.impl.StorageConnectionBase;
import com.oracle.iot.client.impl.enterprise.ApplicationEnumerator.ApplicationEnumerationRequest;
import com.oracle.iot.client.impl.enterprise.ApplicationImpl;
import com.oracle.iot.client.impl.enterprise.DeviceEnumerator;
import com.oracle.iot.client.impl.enterprise.DeviceModelIterator;
import com.oracle.iot.client.impl.enterprise.SecureHttpConnectionMap;
import com.oracle.iot.client.impl.enterprise.VirtualDeviceImpl;
import com.oracle.iot.client.impl.http.HttpSecureConnection;
import com.oracle.iot.client.message.StatusCode;
import com.oracle.iot.client.trust.TrustedAssetsManager;
import oracle.iot.client.Client;
import oracle.iot.client.DeviceModel;
import oracle.iot.client.StorageObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * {@code EnterpriseClient} is a enterprise application, client of the Oracle IoT Cloud Service.
 */
public class EnterpriseClient extends Client<VirtualDevice> {

    private final HttpSecureConnection secureConnection;

    private final Application iotApp;

    /**
     * Creates an instance of {@code EnterpriseClient} associated with the
     * specified IoT application.
     * The created instance will use a custom or default {@code TrustedAssetsManager}
     * to store, load and handle the client configuration.
     * See <a href="../../../../overview-summary.html#configuration">configuration</a> for details.
     *
     * @param appName the name of the IoT application to connect with.
     *                <ul>
     *                <li>If not {@code null}, a REST call is performed to find the application-id matching with the
     *                specified name.</li>
     *                <li>If {@code null}, a REST call is performed to find the application that has, in its list of
     *                integrations identifier, the one retrieved from the {@code TrustedAssetsManager}.</li>
     *                </ul>
     *
     * @return an {@code EnterpriseClient} instance to communicate with the specified IoT application
     *
     * @throws IOException              if an I/O error occurred when trying to retrieve data from server
     * @throws GeneralSecurityException if the trust material could not be
     *                                  loaded. If User Authentication is used,
     *                                  then {@link UserAuthenticationException} will be thrown
     *                                  if the session cookie has expired.
     *
     * @see #getApplication()
     */
    public static EnterpriseClient newClient(String appName) throws IOException, GeneralSecurityException {
        return newClient(appName, (Object)null);
    }

    /**
     * Creates an instance of {@code EnterpriseClient} associated with
     * the specified IoT application and with platform specific context.
     * The created instance will use a custom or default {@code TrustedAssetsManager}
     * to store, load and handle the client configuration.
     * See <a href="../../../../overview-summary.html#configuration">configuration</a> for details.
     * <p>
     * If the {@code appName} is not {@code null}, a REST call is performed to find the
     * application-id matching with the specified name.<br>
     * If the {@code appName} is {@code null}, a REST call is performed to find the application that has, in its list of
     * integrations identifier, the one retrieved from the {@code TrustedAssetsManager}.
     * </p>
     * <p>The specified context
     * (if any) could be used by platform specific implementation to
     * retrieve information like internal storage area.</p>
     * @param appName the name of the IoT application to connect with, or {@code null}.
     * @param context a platform specific object (e.g. application context),
     *                that needs to be associated with this client. In
     *                the case of Android, this is an {@code android.content.Context}
     *                provided by the application or service. In the case of Java SE,
     *                the parameter is not used and the value may be {@code null}.
     *
     * @return an {@code EnterpriseClient} instance to communicate with the specified IoT application
     *
     * @throws IOException              if an I/O error occurred when trying to retrieve data from server
     * @throws GeneralSecurityException if the trust material could not be
     *                                  loaded. If User Authentication is used,
     *                                  then {@link UserAuthenticationException} will be thrown
     *                                  if the session cookie has expired.
     */
    public static EnterpriseClient newClient(String appName, Object context)
            throws IOException, GeneralSecurityException {
        TrustedAssetsManager tam =
            TrustedAssetsManager.Factory.getTrustedAssetsManager(context);
        return newClientInternal(appName, context, tam);
    }

    /**
     * Creates an instance of {@code EnterpriseClient} associated with the
     * specified IoT application. The configuration will be loaded from the
     * given path.
     * See <a href="../../../../overview-summary.html#configuration">configuration</a> for details.
     *
     * <p>
     * If the {@code appName} is not {@code null}, a REST call is performed to
     * find the application-id matching with the specified name.<br>
     * If the {@code appName} is {@code null}, a REST call is performed to find
     * the application that has, in its list of
     * integrations identifier, the one retrieved from the {@code
     * TrustedAssetsManager}.
     * </p>
     * <p>The specified context
     * (if any) could be used by platform specific implementation to
     * retrieve information like internal storage area.</p>
     *
     * @param configFilePath the path of the configuration file and may not be {@code null}
     * @param configFilePassword the configuration file password,
     *                   or {@code null} if the configurationFile is not encrypted
     *
     * @param appName the name of the IoT application to connect with, or
     * {@code null}.
     * @param context a platform specific object (e.g. application context),
     *                that needs to be associated with this client. In
     *                the case of Android, this is an {@code android.content.Context}
     *                provided by the application or service. In the case of Java SE,
     *                the parameter is not used and the value may be {@code null}.
     *
     * @return an {@code EnterpriseClient} instance to communicate with the
     *         specified IoT application
     *
     * @throws IOException              if an I/O error occurred when trying
     *                                  to retrieve data from server
     * @throws GeneralSecurityException if the trust material could not be
     *                                  loaded. If User Authentication is used,
     *                                  then {@link
     *                                  UserAuthenticationException} will be
     *                                  thrown if the session cookie has
     *                                  expired.
     * @throws IllegalArgumentException if configFilePath is null
     */
    public static EnterpriseClient newClient(String configFilePath, String configFilePassword,
            String appName, Object context) throws IOException,
            GeneralSecurityException {

        if (configFilePath == null) {
            throw new IllegalArgumentException("configFilePath may not be null");
        }

        TrustedAssetsManager tam = TrustedAssetsManager.Factory.getTrustedAssetsManager(configFilePath,
                    configFilePassword, context);
        return newClientInternal(appName, context, tam);
    }

    /**
     * Creates an instance of {@code EnterpriseClient} using the configuration
     * from the given file path and password.
     * See <a href="../../../../overview-summary.html#configuration">configuration</a> for details.
     *
     * @param configFilePath the path of the configuration file
     * @param configFilePassword the configuration file password,
     *                   or {@code null} if the configurationFile is not encrypted
     *
     * @return an {@code EnterpriseClient} instance to communicate with the
     *         specified IoT application
     *
     * @throws IOException              if an I/O error occurred when trying
     *                                  to retrieve data from server
     * @throws GeneralSecurityException if the trust material could not be
     *                                  loaded. If User Authentication is used,
     *                                  then {@link
     *                                  UserAuthenticationException} will be
     *                                  thrown if the session cookie has
     *                                  expired.
     */
    public static EnterpriseClient newClient(String configFilePath,
            String configFilePassword) throws IOException,
            GeneralSecurityException {
        return newClient(configFilePath, configFilePassword, (String)null, (Object)null);
    }

    private static EnterpriseClient newClientInternal(String appName,
            Object context, TrustedAssetsManager tam) throws IOException,
            GeneralSecurityException {

        HttpSecureConnection secureConnection = null;
        if (!tam.isActivated()) {
            // user authentication - main choice
            System.setProperty("com.oracle.iot.client.use_webapi", "true");
            secureConnection = HttpSecureConnection.createUserAuthSecureConnection(tam);
        }
        else {
            // endpoint authentication
            secureConnection = HttpSecureConnection.createHttpSecureConnection(tam, true);
        }

        final Filter filter;
        if (appName == null) {
            filter = Filter.eq("integrations.id", tam.getEndpointId());
        } else {
            filter = Filter.eq("name", appName);
        }
        final ApplicationEnumerationRequest r = new ApplicationEnumerationRequest(null, null, filter, -1, -1);
        final JSONObject res = httpGet(secureConnection, r.headers(), r.request());
        try {
            // The response is an array containing applications with a matching name or integration-id
            // Make sure there is one and only one (should be enforced by the IoT server)
            JSONArray apps = res.getJSONArray("items");
            if (apps.length() != 1) {
                throw new IOException("Application " + ((appName != null)? "with name=" + appName : "with integration.id=" + tam.getEndpointId()) + " cannot be retrieved");
            }
            Application app = ApplicationImpl.fromJson(apps.getJSONObject(0));
            // sanity check
            if (appName != null) {
                if (!appName.equals(app.getName())) {
                    throw new IOException("Incorrect server response. Received [" + app.getName() + "] instead of " + appName);
                }
            }

            EnterpriseClient client =
                new EnterpriseClient(app, secureConnection, context);
            SecureHttpConnectionMap.putSecureConnection(client,
                                                        secureConnection);
            return client;
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * Constructs a new {@code EnterpriseClient} instance. The specified context
     * (if any) could be used by platform specific implementation to retrieve
     * information like internal storage area.
     * @param iotApp the IoT application
     * @param context a platform specific object (e.g. application context),
     *                that needs to be associated with this client. In
     *                the case of Android, this is an {@code android.content.Context}
     *                provided by the application or service. In the case of Java SE,
     *                the parameter is not used and the value may be {@code null}.
     *
     */
    /* package */ EnterpriseClient(Application iotApp, HttpSecureConnection secureConnection, Object context) {
        super(context);
        if (iotApp == null) {
            throw new NullPointerException("iotApp cannot be null");
        }
        this.iotApp = iotApp;
        this.secureConnection = secureConnection;
    }

    /**
     * {@inheritDoc}
     *
     * @throws RuntimeException if the network request to initialize the 
     * device with its current state fails and an IOException or 
     * GeneralSecurityException occurs. The cause will reflect the relevant
     * exception.
     */
    @Override
    public VirtualDevice createVirtualDevice(String endpointId, DeviceModel deviceModel) {
        if (endpointId == null) {
            throw new NullPointerException("endpointId may not be null");
        }
        if (deviceModel == null) {
            throw new NullPointerException("deviceModel may not be null");
        }
        if (!(deviceModel instanceof DeviceModelImpl)) {
            throw new IllegalArgumentException("device model must be an instanceof com.oracle.iot.client.impl.DeviceModelImpl");
        }
        return new VirtualDeviceImpl(this, secureConnection, endpointId, (DeviceModelImpl)deviceModel);
    }

    /**
     * Closes the resources used by this client.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        this.secureConnection.close();
    }

    /**
     * Get details on the IoT application for this client.
     *
     * @return details on the specified application
     */
    public Application getApplication() {
        return this.iotApp;
    }

    /**
     * Get the list of device models available for this application.
     *
     * @return the list of device models
     * @throws IOException              if an I/O error occurred when trying to retrieve data from server
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid. If User Authentication is used,
     *                                  then {@link UserAuthenticationException} will be thrown
     *                                  if the session cookie has expired.
     */
    public Pageable<String> getDeviceModels() throws IOException, GeneralSecurityException {
        // start with default values: offset = 0, limit = 200
        return new DeviceModelIterator(this.iotApp.getId(), 0, 200, secureConnection);
    }

    /**
     * Get the {@link DeviceModel} for the device model urn. This method may
     * return {@code null} if there is no device model for the URN. Null may also be
     * returned if the device model is a &quot;draft&quot; and the property
     * {@code com.oracle.iot.client.device.allow_draft_device_models} is set to
     * {@code false}, which is the default.
     *
     * @param deviceModelUrn the URN of the device model
     *
     * @return A representation of the device model or {@code null} if it does not exist
     *
     * @throws NullPointerException if deviceModel is {@code null}
     * @throws IOException if there is an I/O error when communicating
     * with the server
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid. If User Authentication is used,
     *                                  then {@link UserAuthenticationException} will be thrown
     *                                  if the session cookie has expired.
     */
    @Override
    public DeviceModel getDeviceModel(String deviceModelUrn) throws IOException, GeneralSecurityException {
        return DeviceModelFactory.getDeviceModel(this.iotApp.getId(), secureConnection, deviceModelUrn);
    }

    /**
     * Get the active devices implementing the specified device model.
     *
     * @param deviceModel the URN of the device model
     * @return devices that implements the specified device model
     * @throws IOException              if an I/O error occurred when trying to retrieve data from server
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid. If User Authentication is used,
     *                                  then {@link UserAuthenticationException} will be thrown
     *                                  if the session cookie has expired.
     */
    public Pageable<Device> getActiveDevices(String deviceModel) throws IOException, GeneralSecurityException {

        return getDevices(
                    Device.Field.all(),
                    Filter.and(
                        Filter.eq(Device.Field.CONNECTIVITY_STATUS.alias(), "ONLINE"),
                        Filter.eq(Device.Field.STATE.alias(), "ACTIVATED"),
                        Filter.eq(Device.Field.DEVICE_MODELS.alias() + ".urn", deviceModel)
                    )
        );
    }

    /**
     * Return a {@link Pageable} collection of {@link Device}.
     *
     * The fields that will be available from returned {@link Device} instances will be limited to the one
     * defined in {@code fields}. Use {@link Device.Field#all()} to get them all.
     *
     * The {@code filter} forms a query. Only devices that satisfy the {@code filter} and have a connectivity status of
     * {@code status} are returned.
     *
     * @param fields set of fields {@link Device.Field} to return for the selected devices. Can be {@code null}.
     * @param filter A filter. Can be {@code null}.
     *
     * @return an {@link Pageable} collection of {@link Device} that satisfy the {@code filter} and {@code status}.
     * If no matching device is found, an {@link Pageable} with no elements is returned.
     *
     * @throws IOException              if an I/O error occurred when trying to retrieve data from server
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid. If User Authentication is used,
     *                                  then {@link UserAuthenticationException} will be thrown
     *                                  if the session cookie has expired.
     */
    public Pageable<Device> getDevices(Set<Device.Field> fields, Filter filter) throws IOException, GeneralSecurityException {

        // start with default values: offset = 0, limit = 200
        return new DeviceEnumerator(this.iotApp.getId(), 0, 200, false, fields, filter, secureConnection);
    }

    // If User Authentication is used, then UserAuthenticationException will
    // be thrown if the session cookie has expired.
    private static JSONObject httpGet(HttpSecureConnection secureConnection,
            Map<String, String> headers, String request) throws IOException,
            GeneralSecurityException {

        HttpResponse res = secureConnection.get(request);
        int status = res.getStatus();
        if (status != StatusCode.OK.getCode()) {
            throw new IOException(res.getVerboseStatus("GET", request));
        }

        byte[] data = res.getData();
        if (data == null) {
            throw new IOException("GET " + request +
                ": failed: no data received");
        }

        String json = new String(data, "UTF-8");
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            throw new IOException("GET " + request + ": " + e.getMessage());
        }
    }

    /**
     * Create a new {@code StorageObject} that will have a name with the given
     * object name prefixed with the application's ID and a directory
     * separator. The prefix addition can be disabled by setting the
     * {@code com.oracle.iot.client.disable_storage_object_prefix}
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
    public StorageObject createStorageObject(String name, String contentType)
            throws IOException, GeneralSecurityException {
        final StorageConnectionBase storageConnection = StorageConnectionMap.getStorageConnection(secureConnection);
        final com.oracle.iot.client.StorageObject delegate = storageConnection.createStorageObject(secureConnection.getEndpointId(), name, contentType);
        return new com.oracle.iot.client.impl.enterprise.StorageObjectImpl(delegate);
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
        final StorageConnectionBase storageConnection = StorageConnectionMap.getStorageConnection(secureConnection);
        final com.oracle.iot.client.StorageObject delegate = storageConnection.createStorageObject(uri);
        return new com.oracle.iot.client.impl.enterprise.StorageObjectImpl(delegate);
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
                storageConnection =  new com.oracle.iot.client.impl.enterprise.StorageConnectionImpl(secureConnection);
                STORAGE_CONNECTION_MAP.put(secureConnection, new WeakReference<StorageConnectionBase>(storageConnection));
            }
            return storageConnection;
        }
    }
}

