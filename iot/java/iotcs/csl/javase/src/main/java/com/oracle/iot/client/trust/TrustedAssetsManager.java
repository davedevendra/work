/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.trust;

import java.io.Closeable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Vector;
import com.oracle.iot.client.impl.trust.TrustedAssetsManagerFactory;

/**
 * The {@code TrustedAssetsManager} interface defines methods for handling trust
 * material used for activation and authentication to the IoT CS. Depending on
 * the capability of the client or device as well as on the security
 * requirements implementations of this interface may simply store sensitive
 * trust material in a plain persistent store, in some keystore or in a secure
 * token.
 * <dl>
 * <dt>Authentication of Devices with the IoT CS</dt>
 * <dd>
 * <dl>
 * <dt>Before/Upon Device Activation</dt>
 * <dd>
 * A device must authenticate with the OAuth service using a client assertion
 * signed with the shared secret in order to retrieve an access token to perform
 * activation with the IoT CS server.
 * </dd>
 * <dt>After Device Activation</dt>
 * <dd>
 * A device must authenticate with the OAuth service using a client assertion
 * signed with the generated private key in order to retrieve an access token to perform
 * activation with the IoT CS server.
 * </dd>
 * </dl>
 * </dd>
 * <dt>Authentication of <em>Pre-activated</em> Enterprise Applications with the
 * IoT CS</dt>
 * <dd>
 * <dl>
 * <dt>Before/Upon Application Activation</dt>
 * <dd>
 * If an enterprise application is pre-activated, the application (client) has
 * already been provisioned with an endpoint ID and a shared secret or key pair
 * and this step does not apply and must not be performed.</dd>
 * <dt>After Application Activation</dt>
 * <dd>
 * Depending on the provisioned credentials (a shared secret or key pair), a pre-activated
 * enterprise application must either: <ul>
 * <li>
 * if only provisioned with a shared secret, authenticate with the OAuth service using a client assertion
 * signed with the shared secret in order to retrieve an access token to perform
 * activation with the IoT CS server.</li>
 * <li>
 * <em>[Not Supported in 1.1]</em>
 * if provisioned with a private key and certificate, authenticate with the OAuth service using a client assertion
 * signed with the generated private key in order to retrieve an access token to perform
 * activation with the IoT CS server.</li>
 * </ul></dd>
 * </dl>
 * </dd>
 * <dt>Authentication of Enterprise Applications with the IoT CS (non
 * pre-activated) [<em>Not Supported in 1.1</em>]</dt>
 * <dd>
 * <dl>
 * <dt>Before/Upon Application Activation</dt>
 * <dd>
 * An enterprise application which is not pre-activated  must authenticate with the OAuth service using a client assertion
 * signed with the shared secret in order to retrieve an access token to perform
 * activation with the IoT CS server.</dd>
 * <dt>After Application Activation</dt>
 * <dd>
 * An enterprise application must must authenticate with the OAuth service using a client assertion
 * signed with the generated private key in order to retrieve an access token to perform
 * activation with the IoT CS server.</dd>
 * </dl>
 * </dd>
 * </dl>
 */
public interface TrustedAssetsManager extends Closeable {

	String DISABLE_LONG_POLLING_PROPERTY = "com.oracle.iot.client.disable_long_polling";

	class Factory {
		/**
		 * The system property name for configuring a custom {@code TrustedAssetsManager} 
		 * implementation.
		 */
	    public final static String TAM_CLASS_PROPERTY = "oracle.iot.client.tam";

	    /**
	     * Loads and instantiates a custom or the default {@code TrustedAssetsManager}.
	     * The class name of a custom {@code TrustedAssetsManager} to instantiate can be specified 
	     * using the "oracle.iot.client.tam" system property (see {@link #TAM_CLASS_PROPERTY}); the specified {@code TrustedAssetsManager}
	     * class must have a public constructor accepting an {@code Object} parameter.
	     * If no custom {@code TrustedAssetsManager}-implementing class is specified or if
	     * the specified class cannot be found the default {@code TrustedAssetsManager} implementation
	     * is used.
	     * 
             * @param context a platform specific object (e.g. application context),
             *                that needs to be associated with this client. In
             *                the case of Android, this is an {@code android.content.Context}
             *                provided by the application or service. In the case of Java SE,
             *                the parameter is not used and the value may be {@code null}.
	     * @return the {@code TrustedAssetsManager} instance.
	     * 
	     * @throws GeneralSecurityException if any error occurs loading and instantiating the {@code TrustedAssetsManager}.
	     */
	    public static TrustedAssetsManager getTrustedAssetsManager(
                    Object context) throws GeneralSecurityException {

	        String tamClass = System.getProperty(TAM_CLASS_PROPERTY);

	        if (tamClass != null) {
                    Exception ex;
                    try {
                        Class<TrustedAssetsManager> clazz =
                            TrustedAssetsManager.class;
                        Class<?> cl = Class.forName(tamClass);
                        Constructor<?> constructor =
                            cl.getConstructor(Object.class);
                        Object tam = constructor.newInstance(context);
                        if (!clazz.isInstance(tam)) {
                            throw new ClassCastException(
                                "TrustedAssetsManager.getTrustedAssetsManager: "
                                + "can't convert " + tam);
                        }

                        return (clazz.cast(tam));
                    } catch (ClassNotFoundException e) {
                        // fall through to default manager
                    } catch (Exception e) {
                        if (e instanceof InvocationTargetException) {
                            InvocationTargetException i =
                                (InvocationTargetException)e;
                            if (i.getTargetException() instanceof
                                    GeneralSecurityException) {
                                throw (GeneralSecurityException)
                                    i.getTargetException();
                            }
                        }

                        throw new GeneralSecurityException(
                            "Cannot instantiate configure " +
                            "TrustedAssetsManager: " + tamClass, e);
                    }

                }

                return TrustedAssetsManagerFactory.create(context);
            }
	    
        /**
         * Loads and instantiates a custom or the default
         * {@code TrustedAssetsManager}. The class name of a custom
         * {@code TrustedAssetsManager} to instantiate can be specified 
         * using the "oracle.iot.client.tam" system property (see
         * {@link #TAM_CLASS_PROPERTY}); the specified {@code
         * TrustedAssetsManager} class must have a public constructor
         * accepting an {@code Object} parameter. If no custom {@code
         * TrustedAssetsManager}-implementing class is specified or if
         * the specified class cannot be found the default {@code
         * TrustedAssetsManager} implementation is used.
         * 
	 * @param path the path of the assets file
	 * @param password the password, if neede of the assets file
         * @param context a platform specific object (e.g. application context),
         *                that needs to be associated with this client. In
         *                the case of Android, this is an {@code android.content.Context}
         *                provided by the application or service. In the case of Java SE,
         *                the parameter is not used and the value may be {@code null}.
         *
         * @return the {@code TrustedAssetsManager} instance.
         * 
         * @throws GeneralSecurityException if any error occurs loading and
         *     instantiating the {@code TrustedAssetsManager}.
         */
        public static TrustedAssetsManager getTrustedAssetsManager(
                String path, String password, Object context)
                throws GeneralSecurityException {
            if (path == null) {
                throw new GeneralSecurityException("Path is null");
            }

            String tamClass = System.getProperty(TAM_CLASS_PROPERTY, null);

            if (tamClass != null) {
                Exception ex;
                try {
                    Class<TrustedAssetsManager> clazz =
                        TrustedAssetsManager.class;
                    Class<?> cl = Class.forName(tamClass);
                    Constructor<?> constructor =
                        cl.getConstructor(String.class, String.class,
                                          Object.class);
                    Object tam = constructor.newInstance(path, password,
                        context);
                    if (!clazz.isInstance(tam)) {
                        throw new ClassCastException(
                            "TrustedAssetsManager.getTrustedAssetsManager: "
                            + "can't convert " + tam);
                    }

                    return (clazz.cast(tam));
                } catch (ClassNotFoundException e) {
                    // fall through to default manager
                } catch (Exception e) {
                    if (e instanceof InvocationTargetException) {
                        InvocationTargetException i =
                            (InvocationTargetException)e;
                        if (i.getTargetException() instanceof
                                GeneralSecurityException) {
                            throw (GeneralSecurityException)
                                i.getTargetException();
                        }
                    }

                    throw new GeneralSecurityException(
                        "Cannot instantiate configure " +
                        "TrustedAssetsManager: " + tamClass, e);
                }
            }

            return TrustedAssetsManagerFactory.create(path, password, context);
        }

	    // Prevents instantiation
	    private Factory() {}
	}

	/**
	 * Retrieves the IoT CS server host name.
	 *
	 * @return the IoT CS server host name.
	 * @throws IllegalStateException
	 *             if this method is called prior to the
	 *             {@code TrustedAssetsManager} is fully initialized.
	 */
	String getServerHost();

	/**
	 * Retrieves the IoT CS server port.
	 *
	 * @return the IoT CS server port.
	 * @throws IllegalStateException
	 *             if this method is called prior to the
	 *             {@code TrustedAssetsManager} is fully initialized.
	 */
	int getServerPort();

	/**
	 * Retrieves the protocol scheme that should be used to talk to the IoT CS.
	 * The IoT CS client library code recognizes {@code "https"},
	 * {@code "mqtts"} (for MQTT over SSL), and {@code "mqtt-wss"} (for
	 * MQTT over websocket secure).
	 * @return the IoT CS server protocol scheme.
	 * @throws IllegalStateException
	 *             if this method is called prior to the
	 *             {@code TrustedAssetsManager} is fully initialized.
     */
	String getServerScheme();

	/**
	 * Retrieves the ID of this client. If the client is a device, the client ID
	 * is the activation ID; if the client is a pre-activated enterprise application,
	 * the client ID corresponds to the assigned endpoint ID. The client ID is
	 * used along with a client secret derived from the shared secret to perform
	 * secret-based client authentication with the IoT CS server.
	 *
	 * @return the ID of this client.
	 * @throws IllegalStateException
	 *             if this method is called prior to the
	 *             {@code TrustedAssetsManager} is fully initialized.
	 */
	String getClientId();

	/**
	 * Retrieves the public key to be used for certificate request.
	 * <p>
	 * Note: on ME platform SATSA is required.
	 * </p>
	 *
	 * @return the public key or null if not present
	 * @throws IllegalStateException
	 *             if this method is called prior to the key pair is generated.
	 */
	PublicKey getPublicKey();

	/**
	 * Retrieves the trust anchor or most-trusted Certification Authority (CA)
	 * certificates to be used to validate the IoT CS server certificate chain.
	 *
	 * @return a {@code Vector} of DER-encoded trust anchor certificates (byte
	 *         arrays).
	 */
	Vector<byte[]> getTrustAnchorCertificates();

	/**
	 * Sets the assigned endpoint ID and certificate as returned by the
	 * activation procedure. Upon a call to this method, a compliant
	 * implementation of the {@code TrustedAssetsManager} interface must ensure
	 * the persistence of the provided endpoint credentials. This method can
	 * only be called once; unless the {@code TrustedAssetsManager} has been
	 * reset.
	 * <p>
	 * If the client is a pre-activated enterprise application, the endpoint ID
	 * has already been provisioned and calling this method MUST fail with an
	 * {@code IllegalStateException}.
	 * </p>
	 *
	 * @param endpointId
	 *            the assigned endpoint ID.
	 * @param certificate
	 *            the DER-encoded certificate issued by the server or an empty array if no certificate was provided
	 *            by the server.
	 * @throws IllegalStateException
	 *             if this method is called prior to the
	 *             {@code TrustedAssetsManager} is fully initialized. or if this
	 *             method is called while endpoint credentials have already been
	 *             assigned.
	 * @throws NullPointerException
	 *             if {@code endpointId} or {@code certificate} is {@code null}.
	 * @throws TrustException
	 *             if any error occurs performing the operation.
	 */
	void setEndPointCredentials(String endpointId, byte[] certificate) throws TrustException;

	/**
	 * Retrieves the assigned endpoint ID.
	 *
	 * @return the assigned endpoint ID.
	 * @throws IllegalStateException
	 *             if this method is called prior to the
	 *             {@code TrustedAssetsManager} is fully initialized, in
	 *             particular if this method is called before the client is
	 *             successfully activated and the endpoint ID set.
	 */
	String getEndpointId();

	/**
	 * Retrieves the assigned endpoint certificate.
	 *
	 * @return the DER-encoded certificate or an empty array if no certificate was assigned.
	 * @throws IllegalStateException
	 *             if this method is called prior to the
	 *             {@code TrustedAssetsManager} is fully initialized, in
	 *             particular if this method is called before the device is
	 *             successfully activated and the endpoint certificate set.
	 */
	byte[] getEndpointCertificate();

	/**
	 * Generates the key pair to be used for assertion-based client
	 * authentication with the IoT CS.
	 *
	 * @param algorithm
	 *            the key algorithm.
	 * @param keySize
	 *            the key size.
	 * @throws TrustException
	 *             if any error occurs performing the operation.
	 * @throws IllegalStateException
	 *             if this method is called after the client has been activated.
	 * @throws NullPointerException
	 *             if {@code algorithm} is {@code null}.
	 * @throws IllegalArgumentException
	 *             if {@code size} is negative or zero or otherwise unsupported.
	 */
	void generateKeyPair(String algorithm, int keySize) throws TrustException;

	/**
	 * Signs the provided data using the specified algorithm and the private
	 * key. This method is only use for assertion-based client authentication
	 * with the IoT CS.
	 *
	 * @param data
	 *            the data to sign.
	 * @param algorithm
	 *            the signature algorithm to use.
	 * @return the signature.
	 * @throws TrustException
	 *             if any error occurs retrieving the necessary key material or
	 *             performing the operation.
	 * @throws NullPointerException
	 *             if {@code algorithm} or {@code data} is {@code null}.
	 */
	byte[] signWithPrivateKey(byte[] data, String algorithm) throws TrustException;

	/**
	 * Retrieves the device's shared secret to be used for post-production
	 * registration (optional). The shared secret may be generated if not
	 * already provisioned. The returned shared secret is encrypted using some
	 * pre-provisioned key - such as a public key / certificate of the IoT CS.
	 * @return the encrypted shared secret.
	 * @throws IllegalStateException
	 *             if this method is called prior to the
	 *             {@code TrustedAssetsManager} is fully initialized.
	 * @throws TrustException
	 *             if this operation is not supported or if any error occurs
	 *             performing the operation.
	 * @deprecated This method is not called by the client library
	 */
	@Deprecated
	byte[] getEncryptedSharedSecret() throws TrustException;

	/**
	 * Signs the provided data using the specified algorithm and the shared
	 * secret of the device indicated by the given hardware id.
	 * If the hardware id is not provisioned, a {@code TrustException} is thrown.
	 * Passing {@code null} for {@code hardwareId} is identical to passing
	 * {@link #getClientId()}.
	 * @param data
	 *            the data to be hashed.
	 * @param algorithm
	 *            the hash algorithm to use.
	 * @param hardwareId
	 *            the hardware id of the device whose shared secret is to be used for signing.
	 * @return the hash of the data.
	 * @throws TrustException
	 *             if any error occurs retrieving the necessary key material or
	 *             performing the operation.
	 * @throws NullPointerException
	 *             if {@code algorithm} or {@code data} is {@code null}.
	 */
	byte[] signWithSharedSecret(byte[] data, String algorithm, String hardwareId) throws TrustException;

	/**
	 * Returns whether the client is activated. The client is deemed activated
	 * if it has at least been assigned endpoint ID.
	 *
	 * @return whether the client is activated.
	 * @throws IllegalStateException
	 *             if this method is called prior to the
	 *             {@code TrustedAssetsManager} is fully initialized.
	 */
	boolean isActivated();

	/**
	 * Resets the trust material back to its provisioning state; in particular,
	 * the key pair is erased. The client will have to go, at least,through activation again;
	 * depending on the provisioning policy in place, the client may have to go 
	 * through registration again.
	 *
	 * @throws TrustException
	 *             if any exception occurs.
	 */
	void reset() throws TrustException;

}
