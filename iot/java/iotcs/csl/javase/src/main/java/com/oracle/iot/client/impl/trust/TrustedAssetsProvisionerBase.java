/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.trust;

import com.oracle.iot.client.trust.TrustException;
import com.oracle.iot.client.trust.TrustedAssetsManager;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Enumeration;
import java.util.Vector;
import java.util.Locale;

/**
 * The {@code TrustedAssetsProvisioner} tool allows for provisioning trust
 * assets. This tool is to be run to create a trusted assets store file that is
 * used by the {@link DefaultTrustedAssetsManager}. A trusted assets store file
 * needs to be created for directly connected devices, gateway devices and
 * enterprise applications prior to connecting to the IoT CS. The tool creates a
 * key store file with the name {@code {@quote <}activationId|endpointId
 * {@quote >}.jks} in the current working directory, unless a target
 * file name is provided. The tool will not overwrite an existing file.
 * 
 * <pre>
 * Usage:
 * java com.oracle.iot.client.trust.TrustedAssetsProvisioner
 * [-user]
 * [-taStore {@code <}taStoreFile{@code >}] # required if -user option specified; if not specified the the TA store will be name {@code <}activationId|endpointId{@code >}.jks
 * -taStorePassword {@code <}taStorePassword{@code >}
 * [ -serverScheme {@code <}iotServerScheme{@code >} ]
 * -serverHost {@code <}iotServerHostName{@code >}
 * [ -serverPort {@code <}iotServerPort{@code >} ]
 * [ -activationId {@code <}activationId{@code >} | -endpointId {@code <}endpointId{@code >} ]
 * [ -sharedSecret {@code <}sharedSecret{@code >} ]
 * -truststore {@code <}truststoreFile{@code >}
 * -truststorePassword {@code <}truststorePassword{@code >}
 * [ -trustAnchorAlias {@code <}trustAnchorAlias{@code >} ]
 * </pre>
 * <dl>
 * <dt>{@code taStoreFile}</dt>
 * <dd>
 * {@code taStoreFile} is the file name of the Trusted Assets Store to create.</dd>
 * <dt>{@code taStorePassword}</dt>
 * <dd>
 * {@code truststorePassword} is the password protecting the Trusted Assets
 * Store.</dd>
 * <dt>{@code iotServerScheme}</dt>
 * <dd>
 * {@code iotServerScheme} is the scheme of the IoT CS on the specified server.
 * {@code iotServerScheme} is optional; default is {@code "https"}.</dd>
 * <dt>{@code iotServerHostName}</dt>
 * <dd>
 * {@code iotServerHostName} is the IoT CS server instance the client (device or
 * enterprise application) has been registered with.</dd>
 * <dt>{@code iotServerPort}</dt>
 * <dd>
 * {@code iotServerPort} is the port of the IoT CS on the specified server.
 * {@code iotServerPort} is optional; default is {@code 443}.</dd>
 * <dt>{@code activationId}</dt>
 * <dd>
 * {@code activationId} is the Id under which the device being provisioned has been
 * registered with the IoT CS. The {@code -activationId} option is used for device
 * provisioning and is therefore incompatible with the {@code -endpointId}
 * option. If nor {@code -activationId} nor the {@code -endpointId} option is
 * provided then the client is provisioned for user authentication.</dd>
 * <dt>{@code endpointId}</dt>
 * <dd>
 * {@code endpointId} is the endpoint Id assigned by the IoT CS to the
 * enterprise application being provisioned. The {@code -endpointId} option is
 * used for enterprise application client provisioning and is therefore
 * incompatible with the {@code -activationId} option. If nor {@code -activationId} nor
 * the {@code -endpointId} option is provided then the client is provisioned for
 * user authentication.</dd>
 * <dt>{@code sharedSecret}</dt>
 * <dd>
 * {@code sharedSecret} is the secret shared with the IoT CS upon registration
 * in order to authenticate the client. The {@code -sharedSecret} is not
 * required in case of user authentication.</dd>
 * <dt>{@code truststoreFile}</dt>
 * <dd>
 * {@code truststoreFile} is the file name of the truststore containing the
 * trusted IoT CS CA certificate(s): the trust anchors.</dd>
 * <dt>{@code truststorePassword}</dt>
 * <dd>
 * {@code truststorePassword} is the password protecting the truststore file.</dd>
 * <dt>{@code trustAnchorAlias}</dt>
 * <dd>
 * {@code trustAnchorAlias} is the alias of the single most trusted IoT CS CS
 * certificate; if not provided all the trusted certificates from the specified
 * truststore will be imported as trust anchors.</dd>
 * </dl>
 */
public class TrustedAssetsProvisionerBase {
	
	private enum Options {
		USER("-user"),
		TA_STORE_FILE("-taStore"),
		TA_STORE_PASSWORD("-taStorePassword"),
		DEVICE_ID("-deviceId"),
		ACTIVATION_ID("-activationId"),
		ENDPOINT_ID("-endpointId"),
		SHARED_SECRET("-sharedSecret"),
                SERVER_SCHEME("-serverScheme"),
		SERVER_HOST("-serverHost"),
		SERVER_PORT("-serverPort"),
		TRUST_STORE("-truststore"),
		TRUST_STORE_PASSWORD("-truststorePassword"),
		TRUST_STORE_TYPE("-truststoreType"),
		TRUST_ANCHOR_ALIAS("-trustAnchorAlias"),
		LIST_STORE("-list"),
		VERBOSE("-v");

		private final String option;

		Options(String option) {
			this.option = option;
		}

		public boolean equals(String value) {
			return option.equalsIgnoreCase(value);
		}

                @Override
		public String toString() {
			return option;
		}
	}

	/**
	 * Runs the {@code TrustedAssetsProvisioner} tool with the provided
	 * arguments.
	 *
	 * @param args the arguments (see the description above for the
	 *             supported options).
	 * @param storeExtension file extension of the store without a "."
	 */
        protected static void main(String[] args, String storeExtension) {
		try {
			String taStore = provision(args, storeExtension);
			display("Created trusted assets store: " + taStore);
			System.exit(0);
		} catch (IllegalArgumentException iae) {
			System.err.println(iae.getMessage());
			showUsage();
		} catch (Exception e) {
			System.err.println("Caught '" + e + ": " + e.getMessage() + "' while creating the trusted assets store");
		}
		System.exit(1);
	}

	/**
	 * Provisions the trusted assets store (backed-up by a file
	 * {@code KeyStore}) using the provided credentials and options.
	 * 
	 * @param args the arguments (see the description above for the
         *             supported options).
         * @param storeExtension file extension of the store without a "."
	 * @return the file name of the created trusted assets store.
	 * @throws IllegalArgumentException if an illegal or unsupported parameter/option is passed.
	 * @throws GeneralSecurityException if any security-related error occurred during provisioning.
	 * @throws IOException if any I/O-related error occurred during provisioning.
	 */
    protected static String provision(String[] args, String storeExtension) throws IllegalArgumentException, GeneralSecurityException, IOException {
		String taStore = null;
		String taStorePassword = null;
                String serverScheme = "https";
		String serverHost = null;
		int serverPort = 443;
		String activationId = null;
		String endpointId = null;
		String sharedSecret = null;
		String truststore = null;
		String truststorePassword = null;
		String truststoreType = null;
		String trustAnchorAlias = null;
		boolean userAuth = false;
		boolean listStore = false;
		boolean verbose = false;

		for (int n = 0; n < args.length; n++) {
			String key = args[n];
			if (Options.USER.equals(key)) {
				userAuth = true;
			} else if (Options.TA_STORE_FILE.equals(key)) {
				taStore = getOptionValue(args, ++n);
			} else if (Options.TA_STORE_PASSWORD.equals(key)) {
				taStorePassword = getOptionValue(args, ++n);
			} else if (Options.DEVICE_ID.equals(key)) {
				activationId = getOptionValue(args, ++n);
			} else if (Options.ACTIVATION_ID.equals(key)) {
				activationId = getOptionValue(args, ++n);
			} else if (Options.ENDPOINT_ID.equals(key)) {
				endpointId = getOptionValue(args, ++n);
			} else if (Options.SHARED_SECRET.equals(key)) {
				sharedSecret = getOptionValue(args, ++n);
                        } else if (Options.SERVER_SCHEME.equals(key)) {
				serverScheme = getOptionValue(args, ++n);
			} else if (Options.SERVER_HOST.equals(key)) {
				serverHost = getOptionValue(args, ++n);
			} else if (Options.SERVER_PORT.equals(key)) {
				String s = getOptionValue(args, ++n);
				try {
				    serverPort = Integer.parseInt(s);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("invalid port value '" + s);
				}
			} else if (Options.TRUST_STORE.equals(key)) {
				truststore = getOptionValue(args, ++n);
			} else if (Options.TRUST_STORE_PASSWORD.equals(key)) {
				truststorePassword = getOptionValue(args, ++n);
			} else if (Options.TRUST_STORE_TYPE.equals(key)) {
				truststoreType = getOptionValue(args, ++n);
			} else if (Options.TRUST_ANCHOR_ALIAS.equals(key)) {
				trustAnchorAlias = getOptionValue(args, ++n);
			} else if (Options.LIST_STORE.equals(key)) {
				listStore = true;
			} else if (Options.VERBOSE.equals(key)) {
				verbose = true;
			} else {
				throw new IllegalArgumentException("unknown option '" + key + "'");
			}
		}
			
		File file = null;
		if (taStore != null) {
			file = new File(taStore);
		} 
		if (file != null && listStore && taStorePassword != null) {
			if (args.length != 5 && !(args.length == 6 && verbose))
				throw new IllegalArgumentException("wrong number of arguments for listing taStore.");
			if (!file.exists())
				throw new IllegalArgumentException("taStore file does not exist.");
			listStore(file, taStorePassword, verbose);
			System.exit(0);
		}

		if (activationId != null && endpointId != null) {
			throw new IllegalArgumentException("incompatible options: '-activationId' and '-endpointId'");
		}
		if (userAuth && (activationId != null || endpointId != null)) {
			throw new IllegalArgumentException("incompatible options: '-activationId', '-endpointId' and '-user'");
		}
		if (!userAuth && (activationId == null || activationId.length() == 0) && (endpointId == null || endpointId.length() == 0)) {
			throw new IllegalArgumentException("missing '-activationId', '-endpointId' or '-user' option");
		}
		if (serverHost == null || serverHost.length() == 0) {
			throw new IllegalArgumentException("missing '-serverHost' option");
		}
		if (serverPort > 65535 || serverPort < 0) {
			throw new IllegalArgumentException("illegal server port value");
		}
		if (!userAuth && (sharedSecret == null || sharedSecret.length() == 0)) {
			throw new IllegalArgumentException("missing '-sharedSecret' option");
		}
		if (userAuth && (sharedSecret != null)) {
			throw new IllegalArgumentException("missing '-sharedSecret' option not supported for user authentication mode ");
		}
		if (userAuth && taStore == null) {
			throw new IllegalArgumentException("missing '-taStore' option");
		}
		if (taStorePassword == null) {
			throw new IllegalArgumentException("missing '-taStorePassword' option");
		}
		if(taStore == null) {
			String clientId = activationId != null ? activationId : endpointId;
			file = new File(System.getProperty("user.dir"), "/" + clientId + "." + storeExtension);
		}
		if (file.exists()) {
			throw new IllegalArgumentException("Trusted assets store file already exists : " + file);
		}

		DefaultTrustedAssetsManager.ProvisioningSupport provisioningSupport = DefaultTrustedAssetsManager.ProvisioningSupport
				.create(file, taStorePassword).setServer(serverScheme, serverHost, serverPort);
		if (!userAuth) {
			if (activationId != null) {
				provisioningSupport.setClientCredentials(activationId, sharedSecret);
			} else {
				provisioningSupport.setEnterpriseClientCredentials(endpointId, sharedSecret);
			}
		}

                
		if (truststore == null || truststore.length() == 0) {
//                    display("WARNING: No SSL truststore provided. " +
//                        "Delegating SSL certificate checks to platform.");
                } else {
                    if (truststorePassword == null) {
			throw new IllegalArgumentException(
                            "missing '-truststorePassword' option");
                    }

                    FileInputStream fis = null;
                    try {
			String keyStoreType = truststoreType != null ?
                            truststoreType : KeyStore.getDefaultType();
			KeyStore ks = KeyStore.getInstance(keyStoreType);

			fis = new FileInputStream(truststore);
			ks.load(fis, truststorePassword.toCharArray());
			X509Certificate trustAnchor;
			if (trustAnchorAlias != null) {
                            trustAnchor = (X509Certificate)
                                ks.getCertificate(trustAnchorAlias);
                            provisioningSupport.addTrustAnchor(
                                trustAnchorAlias, trustAnchor);
			} else {
                            for (Enumeration<String> e = ks.aliases();
                                     e.hasMoreElements();) {
                                String alias = e.nextElement();
                                if (ks.isCertificateEntry(alias)) {
                                    trustAnchor = (X509Certificate)
                                        ks.getCertificate(alias);
                                    provisioningSupport.addTrustAnchor(alias,
                                        trustAnchor);
                                }
                            }
			}
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

		provisioningSupport.provision();
		
		if (listStore) {
			listStore(file, taStorePassword, verbose);
		}
		
		return file.getCanonicalPath();
	}

	private static String getOptionValue(String[] args, int n) throws IllegalArgumentException {
		if (n >= args.length) {
			throw new IllegalArgumentException("Missing parameter for command option: " + args[n - 1]);
		}
		String value = args[n];
		for (Options option : Options.values()) {
			if (option.equals(value)) {
				throw new IllegalArgumentException("Ambiguous or missing parameter for command option: " + args[n - 1]);
			}
		}
		return value;
	}

	// Would use System.lineSeparator(), but it is JDK 1.7 API
	private static final String lineSep = System.getProperty("line.separator", "\n");

	private static void listStore(File file, String taStorePassword, boolean verbose) throws TrustException {
		TrustedAssetsManager tam =
                    new DefaultTrustedAssetsManager(file.getPath(),
                    taStorePassword, null);
	   
		display("\nTrusted assets store: " + file);
		try {
			display(" Server host: " + tam.getServerHost());
		} catch (IllegalStateException ise) {
			display(" Server host is not set.");
		}

		display(" Server port: " + tam.getServerPort());

		try {
			display(" Client ID: " + tam.getClientId());
		} catch (IllegalStateException ise) {
			display(" Client ID is not set.");
		}

		try {
			display(" Endpoint ID: " + tam.getEndpointId());
		} catch (IllegalStateException ise) {
			display(" Endpoint ID is not set.");
		}

		try {
			PublicKey key = tam.getPublicKey();
			display(" Public key is set.");
			if (verbose)
				display("  " + key);
		} catch (IllegalStateException ise) {
			display(" Public key is not set.");
		}

                Vector<byte[]> certs = tam.getTrustAnchorCertificates();
                if (certs.size() == 0) {
                    display(" Trust anchor certificates are not set.");
                } else {
                    display(" Trust anchor certificates are set.");
                    if (verbose) {
						StringBuilder sb = new StringBuilder(lineSep);
						try {
							CertificateFactory factory = CertificateFactory.getInstance("X.509");
							for (byte[] cert : certs) {
								InputStream inputStream = new ByteArrayInputStream(cert);
								Certificate certificate = factory.generateCertificate(inputStream);
								sb.append(certificate.toString()).append(lineSep);
                            }
                        } catch (CertificateException e) {
							sb.delete(0,sb.length());
							for (byte[] cert : certs) {
								sb.append(lineSep);
								for(byte b : cert) {
									sb.append(String.format(Locale.ROOT, "%02x", b & 0xFF));
								}
							}
						}
						display(sb.toString());
					}
                }
	}

	private static void showUsage() {
		display("Usage: \n");
		display("java com.oracle.iot.client.impl.trust.TrustedAssetsProvisioner "
				+ "\n\t[-user]"
				+ "\n\t[-taStore <file>] # required if -user option specified; if not specified the TA store will be name <activationId|endpointId>.<jks|bks>"
				+ "\n\t-taStorePassword <password> " 
				+ "\n\t[ -serverScheme mqtts|https] "
				+ "\n\t-serverHost <hostname> "
				+ "\n\t[ -serverPort <port> ]"
				+ "\n\t-activationId <id> | -endpointId <id>"
				+ "\n\t-sharedSecret <secret> "
				+ "\n\t-truststore <file> "
				+ "\n\t-truststorePassword <password> "
				+ "\n\t-truststoreType <type> "
				+ "\n\t[ -trustAnchorAlias <alias> ]"
				+ "\n\t[ -list [-v] ]"
				+ "\n");
                display("-serverScheme is optional, if omitted https will be used. mqtts means mqtt over ssl; -serverPort is optional, if omitted 443 wi ll be used; -trustAnchorAlias is optional, if omitted all the certificates will be imported from the provided truststore.");
		display("-activationId and -endpointId options are mutually exclusive.");
		display("-user is used to provision an enterprise client for user authentication.");
		display("-list is used to list the content of the TA Store. -v is additionally used for verbose listing.");
		display("\nOr:");
		display("java com.oracle.iot.client.impl.trust.TrustedAssetsProvisioner "
				+ "\n\t-taStore <file>"
				+ "\n\t-taStorePassword <password> " 
				+ "\n\t-list [-v] "
				+ "\n"
				+ "To list an already created store.\n");
	}

    private static void display(String s) {
        System.out.println(s);
    }
}
