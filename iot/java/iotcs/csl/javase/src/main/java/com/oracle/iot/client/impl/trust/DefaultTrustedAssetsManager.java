/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.impl.trust;

import com.oracle.iot.client.impl.util.Base64;
import com.oracle.iot.client.trust.TrustException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@code DefaultTrustedAssetsManager} class provides a default
 * implementation of the {@code TrustedAssetsManager} interface. The trusted assets
 * are stored in a {@link KeyStore KeyStore} protected by a password. The trust
 * assets are stored in the {@code KeyStore} as follows:
 * <dl>
 * <dt>
 * Shared Secret</dt>
 * <dd>
 * The shared secret is stored as a {@code SecretKeyEntry} under an alias in the
 * form of a {@code URI} constructed from the server host, the server port, the
 * client ID and, if defined, the endpoint ID, as follows:
 * 
 * <pre>
 *             {@code iotcs://client-id@server-host:server-port[#endpoint-id]}
 * </pre>
 * 
 * Only one such entry should exist in the {@code Keystore}.</dd>
 * <dt>
 * Private Key and Certificate</dt>
 * <dd>
 * The private key and corresponding certificate are stored as a
 * {@code PrivateKeyEntry} under an alias in the form of a {@code URI}
 * constructed from the server host, the server port, the device ID and the
 * endpoint ID, as follows:
 * 
 * <pre>
 *             {@code iotcs://client-id@server-host:server-port#endpoint-id}
 * </pre>
 * 
 * Only one such entry should exist in the {@code KeyStore}.</dd>
 * <dt>
 * Trusted Certificates (Trust Anchors)</dt>
 * <dd>
 * Each trusted certificate is stored as a {@code TrustedCertificateEntry}; the
 * alias does not carry any particular semantics and its format is unspecified.
 * Many such entries may exist in the {@code KeyStore}.</dd>
 * </dl>
 * For example, a trusted assets {@code KeyStore} may contain the following
 * entries:
 * 
 * <pre>
 *     <code>
 *          SecretKeyEntry(alias="iotcs://0-AI@acme.foo.iot.bar.oraclecloud.com:7001", secret-key=SecretKey("changeit"))
 *          PrivateKeyEntry(alias="iotcs://0-AI@acme.foo.iot.bar.oraclecloud.com:7001#XYZ", private-key=RSAPrivateKey(...), certificate=X509Certificate(...))
 *          TrustedCertificateEntry(alias="rootCA1", certificate=X509Certificate(...))
 *          TrustedCertificateEntry(alias="rootCA2", certificate=X509Certificate(...))
 *     </code>
 * </pre>
 */
public class DefaultTrustedAssetsManager extends TrustedAssetsManagerBase {
	private static final Charset UTF_8 = Charset.forName("UTF-8");
	private static final String IOT_SCHEME = "iotcs";
	// serverScheme is saved in the entry alias with the prefix "iotcs+",
	// e.g., "iotcs+https". This allows newer TAM to load an older
	// TAM file that just had "iotcs" for the scheme. If the scheme is
	// just "iotcs", then we assume the serverScheme is "https"
	private static final String IOT_SCHEME_PREFIX = IOT_SCHEME.concat("+");

        // HACK: A place holder to construct a valid URI that can be
        // used to store the private keys, before the "real" call
        // to setEndPointCredentials. If a null value is used
        // the assets file fails to load with 
        // Caused by: java.security.UnrecoverableKeyException:
        // Given final block not properly padded
        // See HACK in setEndPointCredentials.
        private static final String EP_NOT_SET = "__EP_NOT_SET__";

	private X509Certificate certificate;

	private KeyStore taStore;
	private KeyStore.ProtectionParameter taProtection;
	private File taStoreFile;

	private KeyStore.Builder taStoreBuilder;

	/**
	 * Creates a new {@code DefaultTrustedAssetsManager} instance for
         * unit testing.
	 *
	 * @param file the {@code KeyStore} file.
	 * @param password the password, null if not needed
         * @param context a platform specific object (e.g. application context),
         *                that needs to be associated with this client. In
         *                the case of Android, this is an {@code android.content.Context}
         *                provided by the application or service. In the case of Java SE,
         *                the parameter is not used and the value may be {@code null}.
	 *
	 * @return a new {@code DefaultTrustedAssetsManager} instance created
         * from the provided file and password protection
	 * @throws TrustException if any error occurs opening and
         * loading the manager
	 */
	static DefaultTrustedAssetsManager create(File file,
                KeyStore.PasswordProtection password, Object context)
                throws TrustException {
            DefaultTrustedAssetsManager trustedAssetsManager =
                new DefaultTrustedAssetsManager();
            trustedAssetsManager.load(file, password);
            return trustedAssetsManager;
	}

	/**
	 * For unit testing. Creates a new {@code DefaultTrustedAssetsManager} instance backed-up by a
	 * {@code KeyStore} that can be instantiated from the provided
	 * {@code KeyStore.Builder} object.
	 *
	 * @param builder
	 *            the {@code KeyStore.Builder} object.
         * @param context a platform specific object (e.g. application context),
         *                that needs to be associated with this client. In
         *                the case of Android, this is an {@code android.content.Context}
         *                provided by the application or service. In the case of Java SE,
         *                the parameter is not used and the value may be {@code null}.
	 * @return a new {@code DefaultTrustedAssetsManager} instance created from
	 *         the provided {@code KeyStore.Builder} object.
	 * @throws TrustException
	 *             if any error occurs opening and loading the {@code KeyStore}.
	 */
	static DefaultTrustedAssetsManager create(KeyStore.Builder builder, Object context)
			throws TrustException {
		DefaultTrustedAssetsManager trustedAssetsManager = new DefaultTrustedAssetsManager();
		trustedAssetsManager.load(builder);
		return trustedAssetsManager;
	}

	/**
	 * Creates a new {@code DefaultTrustedAssetsManager} instance from
         * the provided file path and password.
	 *
	 * @param path the file path to the store.
	 * @param password the store password, {@code null} if NOT needed
         * @param context a platform specific object (e.g. application context),
         *                that needs to be associated with this client. In
         *                the case of Android, this is an {@code android.content.Context}
         *                provided by the application or service. In the case of Java SE,
         *                the parameter is not used and the value may be {@code null}.
	 *
	 * @throws TrustException if any error occurs opening and loading the
         * manager
	 */
        public DefaultTrustedAssetsManager(String path, String password,
                Object context) throws TrustException {
            load(path, password);
	}

        // for unit testing
	private DefaultTrustedAssetsManager() {
	}

	private void load(KeyStore.Builder builder) throws TrustException {
		try {
			this.taStoreFile = null;
			this.taStore = builder.getKeyStore();
			this.taProtection = null;
			this.taStoreBuilder = builder;

			load();
		} catch (Exception e) {
			getLogger().log(Level.SEVERE, "Error loading trusted assets...", e);
			throw new TrustException("Error loading trusted assets...", e);
		}
	}

        private void load(String path, String password) throws TrustException {
            if (path == null) {
                throw new TrustException("Store path is null");
            }
                    
            if (password == null) {
                throw new TrustException("Store password is null");
            }
                    
            load(new File(path), new KeyStore.PasswordProtection(
                password.toCharArray()));
        }

	private void load(File file, KeyStore.PasswordProtection password) throws TrustException {
		FileInputStream fis = null;
		try {
			this.taStoreFile = file;
			String keyStoreType = KeyStore.getDefaultType();
			if (keyStoreType.equals("jks")) {
				keyStoreType = "jceks";
			}
			this.taStore = KeyStore.getInstance(keyStoreType);
			this.taProtection = password;

			fis = new FileInputStream(this.taStoreFile);
			this.taStore.load(fis, ((KeyStore.PasswordProtection) this.taProtection).getPassword());
			load();
		} catch (java.io.FileNotFoundException fnfe) {
			getLogger().log(Level.SEVERE, "File not found: " + file);
			throw new TrustException("Error loading trusted assets...", fnfe);
		} catch (IOException ie) {
			getLogger().log(Level.SEVERE, "Invalid trusted assets");
			throw new TrustException("Error loading trusted assets...", ie);
		} catch (Exception e) {
			getLogger().log(Level.SEVERE, "Error loading trusted assets file " + file, e);
			throw new TrustException("Error loading trusted assets...", e);
		} finally {
			if (fis != null) {
				// noinspection EmptyCatchBlock
				try {
					fis.close();
				} catch (IOException ioe) {
				}
			}
		}
	}

	private void load() throws GeneralSecurityException, URISyntaxException,
			UnsupportedEncodingException {
		this.trustAnchors = new HashSet<X509Certificate>();
		for (Enumeration<String> aliases = this.taStore.aliases(); aliases.hasMoreElements();) {
			// First load the shared secret entry so that it can be used to derive the password to the private key entry
			String alias = aliases.nextElement();
			if (taStore.entryInstanceOf(alias, KeyStore.SecretKeyEntry.class)) {
				URI uri = null;
				try {
					uri = new URI(alias);
				} catch (URISyntaxException ex) {}
				// uri.getScheme() may be "iotcs" for older TAM files, but
				// will be the serverScheme prefixed with "iotcs+" for newer
				// TAM files.
				if (uri == null ||
					uri.getScheme() == null ||
					!IOT_SCHEME.regionMatches(0, uri.getScheme(), 0, IOT_SCHEME.length())) {
					// Ignore entry aliases that are not URI as they may be non-iot related.
					continue;
				}
				parseURI(uri);
				KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) this.taStore.getEntry(alias, getProtection(alias));
				this.sharedSecret = entry.getSecretKey();
				break;
			}
		}
		for (Enumeration<String> aliases = this.taStore.aliases(); aliases.hasMoreElements();) {
			String alias = aliases.nextElement();
			if (this.taStore.entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class)) {
				URI uri = null;
				try {
					uri = new URI(alias);
				} catch (URISyntaxException ex) {}
				if (uri == null ||
					uri.getScheme() == null ||
					!IOT_SCHEME.regionMatches(0, uri.getScheme(), 0, IOT_SCHEME.length())) {
					// Ignore entry aliases that are not URI as they may be non-iot related.
					continue;
				}
				parseURI(uri);
				KeyStore.ProtectionParameter password = getProtection(alias);
				if (password instanceof KeyStore.PasswordProtection) {
					password = derivePassword((KeyStore.PasswordProtection) password);
				}
				KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) taStore.getEntry(alias, password);
				this.privateKey = entry.getPrivateKey();
				this.certificate = (X509Certificate) entry.getCertificate();
			} else if (this.taStore.entryInstanceOf(alias, KeyStore.TrustedCertificateEntry.class)) {
				KeyStore.TrustedCertificateEntry entry = (KeyStore.TrustedCertificateEntry) this.taStore.getEntry(alias, null);
				Certificate certificate = entry.getTrustedCertificate();
				this.trustAnchors.add((X509Certificate)certificate);
			}
		}
		if (this.serverHost == null || this.serverPort == -1) {
                    getLogger().log(Level.SEVERE, "Trusted assets not properly provisioned");
                    throw new GeneralSecurityException("Trusted assets not properly provisioned");
		}
	}

	private void parseURI(URI uri) throws GeneralSecurityException, UnsupportedEncodingException {
		String serverHost = uri.getHost();
		if (this.serverHost != null && serverHost != null && !this.serverHost.equals(serverHost))
			throw new KeyStoreException("Mismatching server host...");
		this.serverHost = serverHost;
		int serverPort = uri.getPort();
		if (this.serverPort >= 0 && serverPort >= 0 && this.serverPort != serverPort)
			throw new KeyStoreException("Mismatching server port...");
		this.serverPort = serverPort;
		String serverScheme = uri.getScheme();
		if (serverScheme != null &&
				IOT_SCHEME_PREFIX.regionMatches(0, uri.getScheme(), 0, IOT_SCHEME_PREFIX.length())) {
			serverScheme = serverScheme.substring(IOT_SCHEME_PREFIX.length());
		} else if (serverScheme == null || IOT_SCHEME.equals(serverScheme)) {
			// if scheme is just "iotcs", then this is an older TAM file
			// that didn't have serverScheme. Default serverScheme to "https"
			serverScheme = "https";
		}
		if (this.serverScheme != null && serverScheme != null && !this.serverScheme.equals(serverScheme))
			throw new KeyStoreException("Mismatching server scheme...");
		this.serverScheme = serverScheme;
		if (!("https".equals(this.serverScheme))) {
			System.setProperty(DISABLE_LONG_POLLING_PROPERTY, "true");
		} else {
			// Treat -Dcom.oracle.iot.client.disable_long_polling
			// the same as -Dcom.oracle.iot.client.disable_long_polling=true
			final String value = (String) System.getProperty("com.oracle.iot.client.disable_long_polling");
			if ("".equals(value)) {
				// If value is an empty String, then property was defined, but not assigned
				System.setProperty(DISABLE_LONG_POLLING_PROPERTY, "true");
			}
		}

		String clientId = uri.getUserInfo();
		if (clientId != null) {
			clientId = URLDecoder.decode(clientId, UTF_8.name());
		}
		if (this.clientId != null && clientId != null && !this.clientId.equals(clientId))
			throw new KeyStoreException("Mismatching client Id...");
		this.clientId = clientId;
		String endpointId = uri.getFragment();
		if (endpointId != null) {
			endpointId = URLDecoder.decode(endpointId, UTF_8.name());
                        // HACK: Don't set this.endpointId to EP_NOT_SET and
                        // don't compare if it this.endpointId has already
                        // been set.
                        if (EP_NOT_SET.equals(endpointId)) {
                            endpointId = null;
                        }
		}
                if (this.endpointId != null && endpointId != null && !this.endpointId.equals(endpointId))
                        throw new KeyStoreException("Mismatching endpoint Id...");
                // HACK: Don't null out a previously set endpointId in case
                // The second entry was the EP_NOT_SET entry
                // There will be two entries in the assets file. One
                // will be from an interim call to "setEndPointCredentials"
                // in order to guarantee that the private key will be 
                // stored. The second will be after the activation 
                // occurs and will contain the real endpoint id.
                if (this.endpointId == null && 
                        !EP_NOT_SET.equals(this.endpointId)) {
                    this.endpointId = endpointId;
                }
	}

	protected void store() throws Exception {
		if (this.privateKey != null) {
			KeyStore.PrivateKeyEntry entry = new KeyStore.PrivateKeyEntry(privateKey, new Certificate[] { certificate });
                        // HACK: Do not use null in the uri. It causes
                        // an exception when the assets file is loaded.
                        // this.endpointId may be null because of hack in
                        // setEndPointCredentials
			URI uri = new URI(IOT_SCHEME_PREFIX+serverScheme, caseEncode(clientId), serverHost, serverPort, null, null, caseEncode(endpointId != null ? endpointId : EP_NOT_SET));
			String alias = uri.toString();
			KeyStore.ProtectionParameter password = getProtection(alias);
			if (password instanceof KeyStore.PasswordProtection) {
				password = derivePassword((KeyStore.PasswordProtection) password);
			}
			taStore.setEntry(alias, entry, password);
		} else {
                        // HACK: Do not use null in the uri. It causes
                        // an exception when the assets file is loaded.
                        // this.endpointId may be null because of hack in
                        // setEndPointCredentials
			URI uri = new URI(IOT_SCHEME_PREFIX+serverScheme, caseEncode(clientId), serverHost, serverPort, null, null, caseEncode(endpointId != null ? endpointId : EP_NOT_SET));
			taStore.deleteEntry(uri.toString());
		}
		if (taStoreFile != null) {
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(taStoreFile);
				taStore.store(fos, ((KeyStore.PasswordProtection) this.taProtection).getPassword());
			} finally {
				if (fos != null) {
					// noinspection EmptyCatchBlock
					try {
						fos.close();
					} catch (IOException ioe) {
					}
				}
			}
		}
	}

	private KeyStore.ProtectionParameter getProtection(String alias) throws KeyStoreException {
		return this.taStoreBuilder != null ? this.taStoreBuilder.getProtectionParameter(alias) : this.taProtection;
	}

        private byte[] char2bytes(char[] chars) {
            CharBuffer charBuffer = CharBuffer.wrap(chars);
            ByteBuffer byteBuffer = Charset.forName("UTF-8").encode(charBuffer);
            byte[] bytes = Arrays.copyOfRange(byteBuffer.array(),
                                              byteBuffer.position(), byteBuffer.limit());
            Arrays.fill(byteBuffer.array(), (byte) 0); // clear sensitive data
            return bytes;
        }

	private KeyStore.PasswordProtection derivePassword(KeyStore.PasswordProtection password) throws GeneralSecurityException {
		if (sharedSecret != null) {
			byte[] data = char2bytes(password.getPassword());
			byte[] digest;
			String algorithm = "HmacSHA256";
			Mac mac;
			try {
				mac = Mac.getInstance(algorithm, taStore.getProvider());
			} catch (NoSuchAlgorithmException nsae) {
				mac = Mac.getInstance(algorithm);
			}
			mac.init(this.sharedSecret);
			mac.update(data);
			digest = mac.doFinal();
			String pass = Base64.getEncoder().encodeToString(digest);
			Arrays.fill(data, (byte) 0); // clear sensitive data
			return new KeyStore.PasswordProtection(pass.toCharArray());
		} 
		return password;
	}

	/**
	 * Retrieves the public key to be used for certificate request.
	 * <p>
	 * Note: on ME platform SATSA is required.
	 *
	 * @return the public key.
	 * @throws IllegalStateException
	 *             if this method is called prior to the key pair is generated.
	 */
	@Override
	public PublicKey getPublicKey() {
		if (this.publicKey == null && this.certificate == null) {
			throw new IllegalStateException("Key pair not yet generated or certificate not yet assigned.");
		}
		return this.publicKey != null ? this.publicKey : this.certificate.getPublicKey();
	}

	/**
	 * Sets the assigned endpoint ID and certificate as returned by the
	 * activation procedure. Upon a call to this method, a compliant
	 * implementation of the {@code TrustedAssetsManager} interface must ensure
	 * the persistence of the provided endpoint credentials. This method can
	 * only be called once; unless the trust manager has been reset.
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
	 */
	@Override
	public void setEndPointCredentials(String endpointId, byte[] certificate) throws TrustException {
		if (this.privateKey == null) {
			throw new IllegalStateException("Private key not yet generated.");
		}
		if (this.endpointId != null) {
			throw new IllegalStateException("EndpointId already assigned.");
		}
		if (endpointId == null) {
			throw new NullPointerException("EndpointId can't be null.");
		}
		if (certificate == null) {
			throw new NullPointerException("Certificate can't be null.");
		}

		this.endpointId = endpointId;
		try {
			if (certificate.length != 0) {
				CertificateFactory factory;
				try {
					factory = CertificateFactory.getInstance("X.509", taStore.getProvider());
				} catch (CertificateException ce) {
					factory = CertificateFactory.getInstance("X.509");
				}
				this.certificate = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certificate));
			} else {
				this.certificate = generateSelfSignedCertificate(this.privateKey, this.publicKey, this.clientId);
			}
		} catch (Exception e) {
			throw new TrustException("Error generating certificate...", e);
		}
		try {
			store();
		} catch (Exception e) {
			throw new TrustException("Error storing the trusted assets...", e);
		}
	}

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
	@Override
	public byte[] getEndpointCertificate() {
		if (this.certificate == null) {
			throw new IllegalStateException("Endpoint certificate not assigned.");
		}
		
		try {
			if (!isSelfSigned(this.certificate)) {
				return this.certificate.getEncoded();
			}
			return new byte[0];
		} catch (CertificateEncodingException e) {
			throw new RuntimeException("Unexpected error retrieving certificate encoding...", e);
		}
	}

	/**
	 * Generates the key pair to be used for authentication with the IoT CS
	 * (OAuth2 Assertion Flow).
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
	@Override
	public void generateKeyPair(String algorithm, int keySize) throws TrustException {
		if (this.endpointId != null) {
			throw new IllegalStateException("Already activated: EndpointId already assigned.");
		}
		if (algorithm == null) {
			throw new NullPointerException("Algorithm cannot be null.");
		}
		if (keySize <= 0) {
			throw new IllegalArgumentException("Key size cannot be negative or 0.");
		}

		KeyPairGenerator keyPairGenerator;
		try {
			try {
				keyPairGenerator = KeyPairGenerator.getInstance(algorithm, taStore.getProvider());
			} catch (NoSuchAlgorithmException nsae) {
				keyPairGenerator = KeyPairGenerator.getInstance(algorithm);
			}
		} catch (GeneralSecurityException e) {
			throw new TrustException(e.getMessage(), e);
		}
		keyPairGenerator.initialize(keySize);
		KeyPair keyPair = keyPairGenerator.generateKeyPair();
		this.privateKey = keyPair.getPrivate();
		this.publicKey = keyPair.getPublic();

		try {
			this.certificate = generateSelfSignedCertificate(this.privateKey, this.publicKey, this.clientId);
			store();
		} catch(GeneralSecurityException e) {
			throw new TrustException(e.getMessage(), e);
		} catch (Exception e) {
			throw new TrustException("Error storing the trusted assets...", e);
		}
	}

	/**
	 * Signs the provided data using the specified algorithm and the private key
	 * (OAuth2 Assertion Flow).
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
	@Override
	public byte[] signWithPrivateKey(byte[] data, String algorithm) throws TrustException {
		if (this.privateKey == null) {
			throw new IllegalStateException("Private key not yet generated.");
		}
		if (algorithm == null) {
			throw new NullPointerException("Algorithm cannot be null.");
		}
		if (data == null) {
			throw new NullPointerException("Data cannot be null.");
		}

		byte[] sig;
		try {
			Signature signature;
			try {
				signature = Signature.getInstance(algorithm, taStore.getProvider());
			} catch (NoSuchAlgorithmException nsae) {
				signature = Signature.getInstance(algorithm);
			}
			signature.initSign(this.privateKey);
			signature.update(data);
			sig = signature.sign();
		} catch (GeneralSecurityException e) {
			throw new TrustException("Error signing with private key...", e);
		}
		return sig;
	}

	/**
	 * Resets the trust material back to its provisioning state; in particular,
	 * the key pair is erased. The client will have to go, at least,through
	 * activation again; depending on the provisioning policy in place, the
	 * client may have to go through registration again.
	 *
	 * @throws TrustException
	 *             if any exception occurs.
	 */
	@Override
	public void reset() throws TrustException {
		this.certificate = null;
                super.reset();
        }

	private static X509Certificate generateSelfSignedCertificate(PrivateKey privateKey, PublicKey publicKey, String clientId)
			throws GeneralSecurityException {
		if (!(privateKey instanceof RSAPrivateKey && publicKey instanceof RSAPublicKey)) {
			throw new GeneralSecurityException("Unsupported Algorithm");
		}
		Date firstDate = new Date();
		Date lastDate = new Date(firstDate.getTime() + 365 * 24 * 60 * 60 * 1000L);
		return SelfSignedX509CertificateFactory.generateSelfSignedCertificate((RSAPrivateKey) privateKey, (RSAPublicKey) publicKey, 
				"SHA1WithRSA", clientId, firstDate, lastDate);
	}
	
	private static boolean isSelfSigned(X509Certificate cert) {
		return cert.getSubjectDN().equals(cert.getIssuerDN());
	}

	private static String caseEncode(String s) {
		if (s == null) {
			return null;
		}
		StringBuilder b = new StringBuilder();
		for (char c : s.toCharArray()) {
			if (Character.isUpperCase(c)) {
				b.append('%').append(Integer.toHexString(c));
			} else {
				b.append(c);
			}
		}
		return b.toString();
	}

	/**
	 * The {@code ProvisioningSupport} provides methods to create and provision
	 * a trusted assets store backed-up by a {@code KeyStore}.
	 */
	public static class ProvisioningSupport {
		private KeyStore.Builder taStoreBuilder = null;
		private File taStoreFile = null;
		private String taStorePassword = null;
		private String clientId;
		private String sharedSecret;
		private String serverScheme;
		private String serverHost;
		private int serverPort;
		private String endpointId;
		private PrivateKey privateKey;
		private final Map<String, Certificate> trustAnchors = new HashMap<String, Certificate>();
		private Certificate certificate;

		/**
		 * Creates a new {@code ProvisioningSupport} instance to create and
		 * provision a trusted assets store backed-up by {@code KeyStore}
		 * instantiated from the provided {@code KeyStore.Builder} object.
		 *
		 * @param taStoreBuilder
		 *            the {@code KeyStore.Builder} object.
		 * @return a new {@code ProvisioningSupport} instance.
		 * @throws IllegalArgumentException
		 *             if {@code taStoreBuilder} is {@code null}.
		 */
		public static ProvisioningSupport create(KeyStore.Builder taStoreBuilder) {
			return new ProvisioningSupport(taStoreBuilder);
		}

		/**
		 * Creates a new {@code ProvisioningSupport} instance to create and
		 * provision a trusted assets store backed-up by a file {@code KeyStore}
		 * - a Java Cryptography Extensions Key Store ("JCEKS").
		 * The name and path to the file {@code KeyStore} is retrieved from the
		 * {@code "trustedAssetsStore"} system property; if not provided, the name
		 * of the file {@code KeyStore} is assumed to be
		 * {@code "trustedAssetsStore.jks"}. The password of the file
		 * {@code KeyStore} is retrieved from the
		 * {@code "trustedAssetsStorePassword"} system property.
		 *
		 * @param taStoreFile
		 *            the key store file.
		 * @param taStorePassword
		 *            the key store password.
		 * @return a new {@code ProvisioningSupport} instance.
		 * @throws IllegalArgumentException
		 *             if {@code taStoreFile} or {@code taStorePassword} is {@code null}.
		 */
		public static ProvisioningSupport create(File taStoreFile, String taStorePassword) {
			return new ProvisioningSupport(taStoreFile, taStorePassword);
		}

		/**
		 * Creates a new {@code ProvisioningSupport} instance to create and
		 * provision a trusted assets store backed-up by a file {@code KeyStore}
		 * - a Java Cryptography Extensions Key Store ("JCEKS").
		 * The name and path to the file {@code KeyStore} is retrieved from the
		 * {@code "trustedAssetsStore"} system property; if not provided, the name
		 * of the file {@code KeyStore} is assumed to be
		 * {@code "trustedAssetsStore.jks"}. The password of the file
		 * {@code KeyStore} is retrieved from the
		 * {@code "trustedAssetsStorePassword"} system property.
		 *
		 * @return a new {@code ProvisioningSupport} instance.
		 */
		public static ProvisioningSupport create() {
			return new ProvisioningSupport(new File(System.getProperty(TA_STORE_PROPERTY, TrustedAssetsManagerFactory.DEFAULT_TA_STORE)),
					System.getProperty(TA_STORE_PASSWORD_PROPERTY));
		}

		/**
		 * Sets the server scheme, host, and port.
		 *
		 * @param serverScheme
		 *            the server scheme.
		 * @param serverHost
		 *            the server host.
		 * @param serverPort
		 *            the server port.
		 * @return this {@code ProvisioningSupport} instance.
		 * @throws IllegalArgumentException
		 *             if {@code serverHost} is {@code null} or
		 *             {@code serverPort} is negative.
		 */
                public ProvisioningSupport setServer(String serverScheme,
                        String serverHost, int serverPort) {
                    if (serverScheme == null) {
                        throw new IllegalArgumentException(
                            "Server scheme cannot be null");
                    }

                    if (serverHost == null) {
                        throw new IllegalArgumentException(
                            "Server host cannot be null");
                    }

                    if (serverPort < 0) {
                        throw new IllegalArgumentException(
                            "Server port cannot be negative");
                    }

                    this.serverScheme = serverScheme;
                    this.serverHost = serverHost;
                    this.serverPort = serverPort;
                    return this;
                }

		/**
		 * Sets the client Id and shared secret (secret-based client credentials
		 * authentication).
		 *
		 * @param endpointId
		 *            the endpoint Id (also the client Id).
		 * @param sharedSecret
		 *            the shared secret.
		 * @return this {@code ProvisioningSupport} instance.
		 * @throws IllegalArgumentException
		 *             if {@code clientId} or {@code sharedSecret} is
		 *             {@code null}.
		 */
		public ProvisioningSupport setEnterpriseClientCredentials(String endpointId, String sharedSecret) {
			if (this.privateKey != null) {
				throw new IllegalStateException("Client cannot be provisioned with both a shared secret and a private key...");
			}
			if (endpointId == null || sharedSecret == null) {
				throw new IllegalArgumentException("Client Id and shared secret cannot be null...");
			}

			this.clientId = endpointId;
			this.endpointId = endpointId;
			this.sharedSecret = sharedSecret;
			return this;
		}

		/**
		 * Sets the client Id and shared secret (secret-based client credentials
		 * authentication).
		 *
		 * @param clientId
		 *            the client Id.
		 * @param sharedSecret
		 *            the shared secret.
		 * @return this {@code ProvisioningSupport} instance.
		 * @throws IllegalArgumentException
		 *             if {@code clientId} or {@code sharedSecret} is
		 *             {@code null}.
		 */
		public ProvisioningSupport setClientCredentials(String clientId, String sharedSecret) {
			if (this.privateKey != null) {
				throw new IllegalStateException("Client cannot be provisioned with both a shared secret and a private key...");
			}
			if (clientId == null || sharedSecret == null) {
				throw new IllegalArgumentException("Client Id and shared secret cannot be null...");
			}

			this.clientId = clientId;
			this.sharedSecret = sharedSecret;
			return this;
		}

		/**
		 * Sets the endpoint Id and pre-generated private key and certificate
		 * (assertion-based client credentials authentication).
		 *
		 * @param endpointId
		 *            the endpoint Id.
		 * @param privateKey
		 *            the private key.
		 * @param certificate
		 *            the certificate.
		 * @return this {@code ProvisioningSupport} instance.
		 * @throws IllegalArgumentException
		 *             if {@code endpointId}, {@code privateKey} or
		 *             {@code certificate} is {@code null}.
		 */
		public ProvisioningSupport setClientCredentials(String endpointId, PrivateKey privateKey, Certificate certificate) {
			if (this.sharedSecret != null) {
				throw new IllegalStateException("Client cannot be provisioned with both a shared secret and a private key...");
			}
			if (endpointId == null || privateKey == null || certificate == null) {
				throw new IllegalArgumentException("Endpoint Id, private key and certificate cannot be null...");
			}

			this.clientId = endpointId;
			this.endpointId = endpointId;
			this.privateKey = privateKey;
			this.certificate = certificate;
			return this;
		}

		/**
		 * Adds a trust anchor certificate under the specified alias.
		 *
		 * @param alias
		 *            the alias.
		 * @param trustAnchor
		 *            the trust anchor certificate.
		 * @return this {@code ProvisioningSupport} instance.
		 * @throws IllegalArgumentException
		 *             if {@code trustAnchor} is {@code null}.
		 */
		public ProvisioningSupport addTrustAnchor(String alias, Certificate trustAnchor) {
			if (trustAnchor == null || alias == null) {
				throw new IllegalArgumentException("Trust Anchor or alias cannot be null...");
			}
			this.trustAnchors.put(alias, trustAnchor);
			return this;
		}

		/**
		 * Provisions the trusted assets store (backed-up by a file
		 * {@code KeyStore}) using the credentials and parameters set in this
		 * {@code ProvisioningSupport} instance.
		 *
		 * @throws TrustException
		 *             if any error occurs while creating and storing to the
		 *             {@code KeyStore} file.
		 */
		public void provision() throws TrustException {
			try {
				store();
			} finally {
				this.sharedSecret = null;
				this.privateKey = null;
			}
		}

		private ProvisioningSupport(KeyStore.Builder taStoreBuilder) {
			if (taStoreBuilder == null) {
				throw new IllegalArgumentException("taStoreBuilder cannot be null...");
			}
			this.taStoreBuilder = taStoreBuilder;
		}

		private ProvisioningSupport(File taStoreFile, String taStorePassword) {
			if (taStoreFile == null) {
				throw new IllegalArgumentException("taStoreFile cannot be null...");
			}
			if (taStorePassword == null) {
				throw new IllegalArgumentException("taStorePassword cannot be null...");
			}
			this.taStoreFile = taStoreFile;
			this.taStorePassword = taStorePassword;
		}

		private void store() throws TrustException {
			try {
				if (this.taStoreBuilder != null) {
					KeyStore taStore = this.taStoreBuilder.getKeyStore();
					store(taStore, null, this.taStoreBuilder);
				} else {
					FileOutputStream fos = null;
					FileInputStream fis = null;
					try {
						String keyStoreType = "jceks";
						KeyStore taStore = KeyStore.getInstance(keyStoreType);
						KeyStore.ProtectionParameter taProtection = null;
						if (this.taStorePassword != null) {
							taProtection = new KeyStore.PasswordProtection(this.taStorePassword.toCharArray());
						}
						fis = this.taStoreFile.exists() ? new FileInputStream(this.taStoreFile) : null;
						taStore.load(fis, taProtection != null ? ((KeyStore.PasswordProtection) taProtection).getPassword() : null);
						fos = new FileOutputStream(this.taStoreFile);
						store(taStore, taProtection, null);
						taStore.store(fos, taProtection != null ? ((KeyStore.PasswordProtection) taProtection).getPassword() : null);
					} finally {
						if (fis != null) {
							// noinspection EmptyCatchBlock
							try {
								fis.close();
							} catch (IOException ioe) {
							}
						}
						if (fos != null) {
							// noinspection EmptyCatchBlock
							try {
								fos.close();
							} catch (IOException ioe) {
							}
						}
					}
				}
			} catch (Exception e) {
				getLogger().log(Level.SEVERE, "Error loading trusted assets...", e);
				throw new TrustException("Error loading trusted assets...", e);
			}
		}

		private void store(KeyStore taStore, KeyStore.ProtectionParameter password, KeyStore.Builder builder) throws KeyStoreException, URISyntaxException {
			if (this.clientId != null || this.endpointId != null) { // Case of Endpoint Authentication
				if (this.sharedSecret != null) {
					final SecretKeySpec keySpec = new SecretKeySpec(this.sharedSecret.getBytes(UTF_8), "Hmac");
					KeyStore.SecretKeyEntry entry = new KeyStore.SecretKeyEntry(keySpec);
					URI uri = new URI(IOT_SCHEME_PREFIX+serverScheme, caseEncode(this.clientId), this.serverHost, this.serverPort, null, null, caseEncode(this.endpointId));
					String alias = uri.toString();
					taStore.setEntry(alias, entry, builder != null ? builder.getProtectionParameter(alias) : password);
				} 
				if (this.privateKey != null) {
					KeyStore.PrivateKeyEntry entry = new KeyStore.PrivateKeyEntry(this.privateKey, new Certificate[] { this.certificate });
					URI uri = new URI(IOT_SCHEME_PREFIX+serverScheme, caseEncode(this.clientId), this.serverHost, this.serverPort, null, null, caseEncode(this.endpointId));
					String alias = uri.toString();
					taStore.setEntry(alias, entry, builder != null ? builder.getProtectionParameter(alias) : password);
				}
			} else { // Case of User Authentication
				SecureRandom random = new SecureRandom();
				byte secret[] = new byte[32];
				random.nextBytes(secret);
				final SecretKeySpec keySpec = new SecretKeySpec(secret, "Hmac");
				KeyStore.SecretKeyEntry entry = new KeyStore.SecretKeyEntry(keySpec);
				URI uri = new URI(IOT_SCHEME_PREFIX+serverScheme, null, this.serverHost, this.serverPort, null, null, null);
				String alias = uri.toString();
				taStore.setEntry(alias, entry, builder != null ? builder.getProtectionParameter(alias) : password);
			}
			if (this.trustAnchors != null && !this.trustAnchors.isEmpty()) {
                            for (Map.Entry<String,Certificate> entry :
                                     this.trustAnchors.entrySet()) {
                                String alias = entry.getKey();
                                Certificate trustAnchor = entry.getValue();
				KeyStore.TrustedCertificateEntry newEntry =
                                    new KeyStore.TrustedCertificateEntry(
                                        trustAnchor);
                                taStore.setEntry(alias, newEntry, null);
                            }
			}
		}
	}

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
