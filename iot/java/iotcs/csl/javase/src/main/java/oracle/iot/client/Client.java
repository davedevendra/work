/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package oracle.iot.client;


import java.io.Closeable;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Client of the Oracle IoT Cloud Service. A client is a directly-connected
 * device, a gateway, or an enterprise application.
 * @param <V> the type of the VirtualDevice instance
 */
public abstract class Client<V extends AbstractVirtualDevice>  implements Closeable {

    /**
     * Create an {@link AbstractVirtualDevice} instance with the given device model
     * for the given device identifier. This method creates a new {@code VirtualDevice}
     * instance for the given parameters. The client library does not
     * cache previously created VirtualDevice objects.
     * @param deviceId The device identifier of the device being modeled
     * @param deviceModel The device model URN that this device implements
     * @throws IllegalArgumentException if the device does not implement the model
     * @throws NullPointerException if deviceId or deviceModel are {@code null}
     * @return a new VirtualDevice
     */
    public abstract V createVirtualDevice(String deviceId, DeviceModel deviceModel);

    /**
     * Get the {@link DeviceModel} for the device model URN. This method may
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
     *                                  private key is invalid
     */
    public abstract DeviceModel getDeviceModel(String deviceModelUrn)
            throws IOException, GeneralSecurityException;

    /**
     * Create a new {@code StorageObject} with the given object name and mime-type.
     * If {@code contentType} is null, the mime-type defaults to "application/octet-stream".
     * @param name the unique name to be used to reference the content in storage
     * @param contentType The mime-type of the content
     * @return a StorageObject
     * @throws IOException if there is an {@code IOException} raised by the runtime,
     * or an abnormal response from the storage cloud
     * @throws GeneralSecurityException if there is an exception establishing a secure connection to the storage cloud
     */
    public abstract StorageObject createStorageObject(String name, String contentType)
            throws IOException, GeneralSecurityException;

    /**
     * Constructs a new {@code Client} instance. The specified context
     * (if any) could be used by platform specific implementation to
     * retrieve information like internal storage area.
     *
     * @param context a platform specific object (e.g. application context),
     *                that needs to be associated with this client. In
     *                the case of Android, this is an {@code android.content.Context}
     *                provided by the application or service. In the case of Java SE,
     *                the parameter is not used and the value may be {@code null}.
     */
    protected Client(Object context) {
        this.context = context;
    }

    /**
      * A platform specific object (e.g. application context),
      * that needs to be associated with this client. In
      * the case of Android, this is an {@code android.content.Context}
      * provided by the application or service. In the case of Java SE,
      * the parameter is not used and the value may be {@code null}.
      */
    protected final Object context;
}
