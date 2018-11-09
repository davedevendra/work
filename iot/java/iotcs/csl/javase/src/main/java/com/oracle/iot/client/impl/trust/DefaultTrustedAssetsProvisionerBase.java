/*
 * Copyright (c) 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.trust;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Vector;
import java.util.Locale;

/**
 * The {@code DefaultTrustedAssetsProvisionerBase} tool allows for provisioning
 * trust
 * assets. This tool is to be run to create a trusted assets store file that is
 * used by the {@link DefaultTrustedAssetsManager}. A trusted assets store file
 * needs to be created for directly connected devices, gateway devices and
 * enterprise applications prior to connecting to the IoT CS. The tool creates a
 * key store file with the name
 * {@code {@quote <}clientID{@quote >}.storeExtension}.
 * The tool will not overwrite an existing file.
 */
public class DefaultTrustedAssetsProvisionerBase {
    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final String MQTTS_SCHEME = "mqtts";
    private static final String HTTPS_SCHEME = "https";
    private static final int DEFAULT_MQTTS_PORT = 8883;

    protected static String INTEGRATION_ID_PROMPT = "Enter the ID";
    protected static String INTEGRATION_SECRET_PROMPT =
        "Enter the Shared Secret";
    protected static String ACTIVATION_ID_PROMPT = "Enter the Activation ID";
    protected static String ACTIVATION_SECRET_PROMPT =
        "Enter the Activation Secret";
    /**
     * Runs the {@code InteractiveAssetsProvisioner} tool with the provided
     * arguments.
     *
     * @param args command line arguments
     * @param taPath path to context files directory
     * @param storeExtension file extension of the store without a "."
     * @param hideUserAuthSupport set to {@code false} to enable the {@code -u} option,
     *                            which sets an enterprise client to use User Authentication
     */
    protected static void main(String[] args, String taPath, String storeExtension, boolean hideUserAuthSupport) {

        boolean isEnterpriseClient = false;
        boolean listTa = false;
        boolean verbose = false;
		boolean userAuth= false;
        String arg;

        String idPrompt = ACTIVATION_ID_PROMPT;
        String secretPrompt = ACTIVATION_SECRET_PROMPT;

        int index = 0;			
        while ((arg = nextArg(args, index++)) != null) {
            if (arg.equals("-e")) {
                isEnterpriseClient = true;
                idPrompt = INTEGRATION_ID_PROMPT;
                secretPrompt = INTEGRATION_SECRET_PROMPT;
                continue;
            }
			
            if (!hideUserAuthSupport && arg.equals("-u")) {
                isEnterpriseClient = true;
                userAuth = true;
                idPrompt = INTEGRATION_ID_PROMPT;
                continue;
            }

            if (arg.equals("-h")) {
                showUsage(hideUserAuthSupport);
                System.exit(0);
            }

            if (arg.equals("-l")) {
                listTa = true;
                continue;
            }

            if (arg.equals("-v")) {
                listTa = true;
                verbose = true;
                continue;
            }

            break;
        }

        try {
            if (arg != null) {
                File file = new File(arg);
                if (file.exists()) {
                    if (listTa) {
                        String password = nextArg(args, index++);
                        if (password == null) {
                            password = promptForInput("Enter passphrase");
                        }

                        listStore(file, password, verbose);
                        System.exit(0);
                    }

                    argError("Incorrect argument: " + arg);
                }
            } else {
                arg = promptForInput("Enter Cloud Service URI");
            }

            HttpUrl url = new HttpUrl(arg);

            String serverHost = url.host;

            if (serverHost == null) {
                url = new HttpUrl(HTTPS_SCHEME, "//" + arg);
                serverHost = url.host;
                if (serverHost == null) {
                    argError("Argument not a URI: " + arg);
                }
            }
            String serverScheme = url.getScheme();
            if (!MQTTS_SCHEME.equals(serverScheme) &&
                    !HTTPS_SCHEME.equals(serverScheme)) {
                argError("Unsupported scheme: " + serverScheme);
            }

            int serverPort = url.port;
            if (serverPort == -1) {
                if (MQTTS_SCHEME.equals(serverScheme)) {
                    serverPort = DEFAULT_MQTTS_PORT;
                } else {
                    serverPort = DEFAULT_HTTPS_PORT;
                }
            }

            X509Certificate serverRootCert = null;

            // If the SERVER_ROOT_CERTIFICATE is set in the
            // environment use it and do not get the cert chain
            // from the server
            Map<String, String> env = System.getenv();
            String envCert = env.get("SERVER_ROOT_CERTIFICATE");
            if (envCert != null && !envCert.isEmpty()) {
                File file = new File(envCert);
                if (!file.exists()) {
                    argError("Cannot find server root certificate: " +
                        envCert);
                }
                try {
                    serverRootCert = certFromPem(file);
                } catch (Exception e) {
                    argError("Cannot create certificate from file " + envCert);
                }
                if (!checkConnectivity(serverRootCert, serverHost,
                        serverPort)) {
                    display("\nWarning could not connect to " +
                        serverHost + ":" + serverPort + 
                        " with SERVER_ROOT_CERTIFICATE " + envCert + 
                        ". Creating trusted assets store anyway.");
                }
            } else {
                try {
                    // Get the certificate to verify the URI
                    serverRootCert = caCertFromServer(serverHost, serverPort);
                } catch (UnknownHostException uhe) {
                    argError("Unknown host " + serverHost);
                } catch (ConnectException ce) {
                    argError(serverHost + 
                        " refused HTTPS connection at port " + serverPort);
                }
            }

            String clientId = nextArg(args, index++);
            if (clientId == null) {
                clientId = promptForInput(idPrompt);
            }

            String taStore = taPath + clientId + "." + storeExtension;
            File taFile = new File(taStore);
            if (taFile.exists()) {
                argError(taStore + " already exists");
            }
            
            String sharedSecret = nextArg(args, index++);
            if (sharedSecret == null && !userAuth) {
                sharedSecret = promptForInput(secretPrompt);
            }

            String taPassword = nextArg(args, index++);
            if (taPassword == null) {
                taPassword = promptForInput("Enter passphrase");
            }

            String icd = nextArg(args, index++);
            final Map<String,SecretKey> icdMap = icd != null ? new HashMap<String,SecretKey>() : null;
            while(icd != null) {
                String icdSecret = nextArg(args, index++);
                if (icdSecret == null) {
                    argError("Incorrect connected device format. Missing shared secret for: " + icd);
                }
                byte[] secretBytes = icdSecret.getBytes("UTF-8");
                SecretKey secretKey = new SecretKeySpec(secretBytes, "Hmac");
                icdMap.put(icd, secretKey);

                icd = nextArg(args, index++);
            }

            byte[] tas = UnifiedTrustedAssetsManager.createTas(
                    taPassword,
                    serverScheme,
                    serverHost,
                    serverPort,
                    clientId,
                    sharedSecret,
                    isEnterpriseClient ? clientId : null,
                    serverRootCert,
                    null,
                    null,
                    icdMap
            );

            FileOutputStream fos = new FileOutputStream(taFile);
            try {
                fos.write(tas);
                fos.flush();
            } finally {
                try {
                    fos.close();
                } catch (IOException ioe) {
                }
            }

            if (listTa) {
                listStore(taFile, taPassword, verbose);
            } else {
                display("\nCreated trusted assets store: " + taStore + "\n");
            }
        } catch (IllegalArgumentException iae) {
            displayError(iae.getMessage());
            System.exit(1);
        } catch (Exception e) {
            displayError(e.getMessage());
            System.exit(2);
        }
    }

    private static X509Certificate caCertFromServer(String host, int port)
            throws Exception {
        X509Certificate[] chain;
        X509Certificate caCert;

        chain = getCertChain(host, port);
        if (chain.length == 0) {
            // If the certs are not retrieved, we consider it an error
            // even if the URL connection is successful.
            throw new Exception("No certificates from the SSL server");
        }

        caCert = chain[chain.length - 1];

        // CA root certs are self signed
        if (caCert.getSubjectX500Principal().equals(
                caCert.getIssuerX500Principal())) {
            return caCert;
        }

        // If normal TLS connection can be made, a trust anchor is not needed
        // Use a plain SSLSocket to check connectivity to support protocols
        // other than HTTPS.
        SSLSocket socket = null;
        try {
            socket = (SSLSocket)SSLContext.getDefault().getSocketFactory().createSocket(host, port);
            socket.startHandshake();
        } catch (IOException ioe) {
            throw new Exception("The cloud service at " + host + ":" + port +
                " does not use a system\nknown CA and does not provide a " +
                "CA certificate. Set the environment\nvariable " +
                "SERVER_ROOT_CERTIFICATE to a file containing the CA " +
                "certficate\n(PEM format) and try again.");
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
        // Server uses system known CA
        return null;
    }

    private static X509Certificate[] getCertChain(String host, int port)
            throws Exception {

        // Use a plain SSLSocket to connect and get the cert chain
        // to support protocols other than HTTPS.
        GetChainTrustManager trustManager = new GetChainTrustManager();
        SSLSocketFactory socketFactory;
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] { trustManager }, null);
            socketFactory = context.getSocketFactory();
        } catch (GeneralSecurityException gse) {
            throw new IOException("No initialized SSLSocketFactory");
        }

        SSLSocket socket = null;
        synchronized (trustManager) {
            try {
                socket = (SSLSocket)socketFactory.createSocket(host, port);
                socket.startHandshake();
                return trustManager.serverChain;
            } catch (IOException ioe) {
                // If the server certificate has already been
                // retrieved, don't mind the connection state.
                if (trustManager.exchangedServerCerts &&
                        trustManager.serverChain != null) {
                    return trustManager.serverChain;
                }
                // otherwise, rethrow the exception
                throw ioe;
            } finally {
                if (socket != null) {
                    socket.close();
                }
                trustManager.cleanup();
            }
        }
    }

    private static X509Certificate certFromPem(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate)cf.generateCertificate(fis);
        } finally {
            fis.close();
        }
    }

    private static void provision(File file, String taPassword,
            String serverScheme, String serverHost, int serverPort,
            String clientId, String sharedSecret, boolean isEnterpriseClient,
            X509Certificate trustAnchor,boolean userAuth) throws Exception {
        DefaultTrustedAssetsManager.ProvisioningSupport provisioningSupport =
            DefaultTrustedAssetsManager.ProvisioningSupport.create(file,
            taPassword);

        provisioningSupport.setServer(serverScheme, serverHost, serverPort);

        if (isEnterpriseClient) {
			if(!userAuth){
            provisioningSupport.setEnterpriseClientCredentials(clientId,
                sharedSecret);
			}
        } else {
            provisioningSupport.setClientCredentials(clientId, sharedSecret);
        }

        if (trustAnchor != null) {
            provisioningSupport.addTrustAnchor("trustAnchor", trustAnchor);
        }

        provisioningSupport.provision();
    }

    // Would use System.lineSeparator(), but it is JDK 1.7 API
    private static final String lineSep = System.getProperty("line.separator", "\n");

    private static void listStore(File file, String taPassword,
            boolean verbose) throws Exception {
        UnifiedTrustedAssetsManager tam =
            new UnifiedTrustedAssetsManager(file.getPath(), taPassword, null);
           
        display("\nTrusted assets store: " + file);

        try {
            String host = tam.getServerHost() ;
            int port = tam.getServerPort();
            String scheme = tam.getServerScheme();
            display(" Server URI: " + scheme + "://" + host + ":" + port);
        } catch (IllegalStateException ise) {
            display(" Server URI is not set.");
        }

        PublicKey key = null;
        try {
            key = tam.getPublicKey();
        } catch (IllegalStateException ise) {
        }

        try {
            String clientId = tam.getClientId();
            String endpointId = null;

            try {
                endpointId = tam.getEndpointId();
            } catch (IllegalStateException ise) {
            }

            if (clientId.equals(endpointId) && key == null) {
                display(" Enterprise integration ID: " + clientId);
            } else {
                display(" Activation ID: " + clientId);
                if (endpointId != null) {
                    display(" Device ID: " + endpointId);
                }
            }
        } catch (IllegalStateException ise) {
            display(" Device ID is not set.");
        }

        if (tam.icdMap != null) {
            StringBuilder builder = new StringBuilder(" Connected Devices: [");
            for (String icd : tam.icdMap.keySet()) {
                builder.append(icd).append(", ");
            }
            int len = builder.length();
            builder.replace(len-2, len, "]");
            display(builder.toString());
        }
        if (!verbose) {
            return;
        }

        if (key == null) {
            display(" Public key pair is not set.");
        } else {
            display(" Public key pair is set.");
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

    private static String nextArg(String args[], int index) {
        if (index >= args.length) {
            return null;
        }

        return args[index].trim();
    }

    private static void argError(String msg) {
        throw new IllegalArgumentException(msg);
    }
                
    private static String promptForInput(String prompt) {
        // create a scanner so we can read the command-line input
        Scanner scanner = new Scanner(System.in);
        System.out.print(prompt + ": ");
        // get their input as a String
        return scanner.next().trim();
    }

    private static void showUsage(boolean hideUserAuthSupport) {
        // The shell script will display the first and half of the second line
        display(
        /*    provisioner.sh*/" [option...] <URI> <ID> <Secret> <password> [<ID> <sharedSecret>]\n" +
         "                             or\n" +
         "                   -l <provisioned file> <password>\n" +
         "options:\n" +
         "    -e : to indicate <ID> is an Enterprise Integration ID");
        if(!hideUserAuthSupport)
			display("    -u : to indicate <ID> is an Enterprise Integration ID used for User Authentication");
        display(        
         "    -h : help\n" +
         "    -l : to list the URI and IDs in the provisioned file\n" +
         "    -v : to list all of the contents of the provisioned file\n" +
         "<URI> is entered as [<scheme>://]<host>[:<port>]\n" +
         "\twhere <scheme> is one of \"mqtts\" or \"https\"\n" +
         "<ID> is either an Activation ID or Enterprise Integration ID\n" +
         "<Secret> is either the Activation Secret from registering the\n" +
         "\tdevice on the server or the Shared Secret from creating\n" +
         "\tthe enterprise integration\n" +
         "<password> is a passphrase used to protect the integrity of the\n" +
         "\ttrusted assets store\n"+
         "<ID> <sharedSecret> provisions an indirectly connected device.\n" +
         "\t<ID> is the Activation ID of an indirectly connected device, and <sharedSecret>\n" +
         "\tis the shared secret entered when registering the indirectly connected device.\n" +
         "\tThe <ID> is separated from the <sharedSecret> by a space and there may be\n"+
         "\tzero or more <ID> <sharedSecret> pairs.\n\n"+
         "In addition, the automatic trust anchor processing may be overridden\n" +
         "by setting the environment variable SERVER_ROOT_CERTIFICATE to a\n" +
         "file containing a PEM/PKCS7 encoded certificate."); 
            
    }

    private static void display(String s) {
        System.out.println(s);
    }

    private static void displayError(String s) {
        System.err.println("\n" + s + "\n");
    }

    /*
     * A X509TrustManager that ignores the server certificate validation.
     */
    private static class GetChainTrustManager
            implements X509TrustManager {

        private X509Certificate[] serverChain = new X509Certificate[0];
        private boolean exchangedServerCerts = false;

        @Override
        public void checkClientTrusted(X509Certificate[] chain,
                String authType) throws CertificateException {

            throw new UnsupportedOperationException();
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain,
                String authType) throws CertificateException {

            exchangedServerCerts = true;
            if (chain != null) {
                serverChain = chain;
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        void cleanup() {
            exchangedServerCerts = false;
            serverChain = new X509Certificate[0];
        }
    }

    /**
     * Given a certifate created from pem file spefified by the
     * SERVER_ROOT_CERTIFICATE environment variable,
     * verify that it can be used to connect to the desired server.
     * @param cert certificate provided by the user
     * @param host the certificate host
     * @param port the certifiacte host port
     * @return true if a connection can be established else false
     */
    private static boolean checkConnectivity(X509Certificate cert, String host,
            int port) {

        SSLSocket socket = null;
        FileInputStream fis = null;
        try {
            String alias = "alias";

            KeyStore trustStore = 
                KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null);
            trustStore.setCertificateEntry(alias, cert);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(trustStore, null);
            KeyManager[] keyManagers = kmf.getKeyManagers();

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            TrustManager[] trustManagers = tmf.getTrustManagers();

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);
            socket = (SSLSocket)
                sslContext.getSocketFactory().createSocket(host, port);
            socket.startHandshake();
        } catch (IOException e) {
            return false;
        } catch (Exception e) {
            displayError(e.getMessage());
            System.exit(2);
        } finally {
            try {
                if (socket != null) {
                    socket.close();
                }
                if (fis != null) {
                    fis.close();
                }
            } catch (Exception e) {
            }
        }
        return true;
    }

    /**
     * A parsed HTTP (or subclass of) URL. Based on RFC 2396.
     * <p>
     * Handles IPv6 hosts, check host[0] for a "[".
     * Can be used for relative URL's that do not have authorities.
     * Can be used for FTP URL's that do not have the username and passwords.
     * <p>
     * Any elements not specified are represented by null, except a
     * non-specified port, which is represented by a -1.
     */
    private static class HttpUrl {
        /** Scheme of the URL or null. */
        public String scheme;
        /** Authority (host [port]) of the URL. */
        public String authority;
        /** Path of the URL or null. */
        public String path;
        /** Query of the URL or null. */
        public String query;
        /** Fragment of the URL or null. */
        public String fragment;
        /** hHst of the authority or null. */
        public String host;
        /** Port of the authority or -1 for not specified. */
        public int port = -1;

        /**
         * Construct a HttpUrl.
         *
         * @param url HTTP URL to parse
         *
         * @exception IllegalArgumentException if there is a space in the URL or
         *             the port is not numeric
         */
        public HttpUrl(String url) {
            int afterScheme = 0;
            int length;
            int endOfScheme;

            if (url == null) {
                return;
            }

            length = url.length();
            if (length == 0) {
                return;
            }

            // ":" can mark a the scheme in a absolute URL which has a "//".
            endOfScheme = url.indexOf(':');
            if (endOfScheme != -1) {
                if (endOfScheme == length - 1) {
                    // just a scheme
                    scheme = url.substring(0, endOfScheme);
                    return;
                }

                if (endOfScheme < length - 2 &&
                    url.charAt(endOfScheme + 1) == '/' &&
                    url.charAt(endOfScheme + 2) == '/') {
                    // found "://", get the scheme
                    scheme = url.substring(0, endOfScheme);
                    afterScheme = endOfScheme + 1;
                }
            }

            parseAfterScheme(url, afterScheme, length);
        }

        /**
         * Construct a HttpUrl from a scheme and partial HTTP URL.
         *
         * @param theScheme  the protocol component of an HTTP URL
         * @param partialUrl HTTP URL to parse
         *
         * @exception IllegalArgumentException if there is a space in the URL or
         *             the port is not numeric
         */
        public HttpUrl(String theScheme, String partialUrl) {
            int length;

            scheme = theScheme;

            if (partialUrl == null) {
                return;
            }

            length = partialUrl.length();
            if (length == 0) {
                return;
            }

            parseAfterScheme(partialUrl, 0, length);
        }

        /**
         * Return the URL scheme.
         * @return the URL scheme
         */
        public String getScheme() {
            return scheme;
        }

        /**
         * Parse the part of the HTTP URL after the scheme.
         *
         * @param url the part of the HTTP URL after the ":" of the scheme
         * @param afterScheme index of the first char after the scheme
         * @param length length of the url
         *
         * @exception IllegalArgumentException if there is a space in the URL or
         *             the port is not numeric
         */
        private void parseAfterScheme(String url, int afterScheme, int length) {
            int start;
            int startOfAuthority;
            int endOfUrl;
            int endOfAuthority;
            int endOfPath;
            int endOfQuery;
            int endOfHost;
            int startOfPort;
            int endOfPort;
            int lastDot;
            int startOfDomain;

            if (url.indexOf(' ') != -1 || url.indexOf('\r') != -1 || 
                url.indexOf('\n') != -1 || url.indexOf('\u0007') != -1) {
                throw new IllegalArgumentException("Space character in URL");
            }

            endOfUrl = length;
            endOfAuthority = endOfUrl;
            endOfPath = endOfUrl;
            endOfQuery = endOfUrl;

            if (url.startsWith("//", afterScheme)) {
                // do not include the "//"
                startOfAuthority = afterScheme + 2;
            } else {
                /*
                 * no authority, the path starts at 0 and
                 * may not begin with a "/"
                 */
                startOfAuthority = afterScheme;
            }

            /*
             * Since all of the elements after the authority are optional
             * and they can contain the delimiter of the element before it.
             * Work backwards since we know the end of the last item and will
             * know the end of the next item when find the start of the current
             * item.
             */
            start = url.indexOf('#', startOfAuthority);
            if (start != -1) {
                endOfAuthority = start;
                endOfPath = start;
                endOfQuery = start;

                // do not include the "#"
                start++;

                // do not parse an empty fragment
                if (start < endOfUrl) {
                    fragment = url.substring(start, endOfUrl);
                }
            }

            start = url.indexOf('?', startOfAuthority);
            if (start != -1 && start < endOfQuery) {
                endOfAuthority = start;
                endOfPath = start;

                // do not include the "?"
                start++;

                // do not parse an empty query
                if (start < endOfQuery) {
                    query = url.substring(start, endOfQuery);
                }
            }

            if (startOfAuthority == afterScheme) {
                // no authority, the path starts after scheme
                start = afterScheme;
            } else {
                // this is not relative URL so the path must begin with "/"
                // can be [IPv6/60].../
                int posSqBr = url.indexOf(']', startOfAuthority);
                if (posSqBr > -1) {
                    start = url.indexOf('/', posSqBr);
                } else {
                    start = url.indexOf('/', startOfAuthority);
                }
            }

            // do not parse an empty path
            if (start != -1 && start < endOfPath) {
                endOfAuthority = start;

                path = url.substring(start, endOfPath);
            }

            if (startOfAuthority >= endOfAuthority) {
                return;
            }

            authority = url.substring(startOfAuthority, endOfAuthority);
            endOfPort = authority.length();

            // get the port first, to find the end of the host

            // IPv6 address have brackets around them and can have ":"'s
            start = authority.indexOf(']');
            if (start == -1) {
                startOfPort = authority.indexOf(':');
            } else {
                startOfPort = authority.indexOf(':', start);
            }

            if (startOfPort != -1) {
                endOfHost = startOfPort;

                // do not include the ":"
                startOfPort++;

                // do not try parse an empty port
                if (startOfPort < endOfPort) {
                    try {
                        port = Integer.parseInt(authority.substring(
                               startOfPort, endOfPort));

                        if (port < 0) {
                            throw new
                                IllegalArgumentException("invalid port format");
                        }

                        if (port == 0 || port > 0xFFFF) {
                            throw new IllegalArgumentException(
                                "port out of legal range");
                        }
                    } catch (NumberFormatException nfe) {
                        throw new IllegalArgumentException(
                            "invalid port format");
                    }
                }
            } else {
                endOfHost = endOfPort;
            }

            // there could be a port but no host
            if (endOfHost < 1) {
                return;
            }

            // get the host
            host = authority.substring(0, endOfHost);
            // the last char of the host must not be a minus sign or period
            int hostLength = host.length();
            if ((host.lastIndexOf('.') == hostLength - 1) 
                || (host.lastIndexOf('-') == hostLength - 1)) {
                throw new IllegalArgumentException("invalid host format");
            } 
        
            /*
             * find the machine name and domain, if not host is not an IP
             * address
             */
            if (host.charAt(0) == '[') {
                if (!isValidIPv6Address(host)) {
                    throw new IllegalArgumentException("invalid IPv6 format");
                }
                return;
            }

            if (Character.isDigit(host.charAt(0))) {
                if (!isValidIPv4Address(host)) {
                    throw new IllegalArgumentException("invalid IPv4 format");
                }
                return;
            }

            if (!isValidHostName(host)) {
                throw new IllegalArgumentException("invalid host format");
            }
        }

        /**
         * Adds a base URL to this URL if this URL is a relative one.
         * Afterwards this URL will be an absolute URL.
         *
         * @param baseUrl an absolute URL
         *
         * @exception IllegalArgumentException if there is a space in the URL or
         *             the port is not numeric
         */
        public void addBaseUrl(String baseUrl) {
            addBaseUrl(new HttpUrl(baseUrl));
        }

        /**
         * Adds a base URL to this URL if this URL is a relative one.
         * Afterwards this URL will be an absolute URL.
         *
         * @param baseUrl a parsed absolute URL
         */
        public void addBaseUrl(HttpUrl baseUrl) {
            String basePath;

            if (authority != null) {
                return;
            }

            scheme = baseUrl.scheme;
            authority = baseUrl.authority;

            if (path == null) {
                path = baseUrl.path;
                return;
            }

            if (path.charAt(0) == '/' || baseUrl.path == null ||
                baseUrl.path.charAt(0) != '/') {
                return;
            }

            // find the base path
            basePath = baseUrl.path.substring(0, baseUrl.path.lastIndexOf('/'));

            path = basePath + '/' + path;
        }

        /**
         * Converts this URL into a string.
         *
         * @return string representation of this URL
         */
        public String toString() {
            StringBuffer url = new StringBuffer();

            if (scheme != null) {
                url.append(scheme);
                url.append(':');
            }

            if (authority != null || scheme != null) {
                url.append('/');
                url.append('/');
            }

            if (authority != null) {
                url.append(authority);
            }

            if (path != null) {
                url.append(path);
            }

            if (query != null) {
                url.append('?');
                url.append(query);
            }

            if (fragment != null) {
                url.append('#');
                url.append(fragment);
            }

            return url.toString();
        }

        /**
         * Checks is IPv6 address has a valid format.
         *
         * @param address the string representation of IPv6 address
         * @return true when IPv6 address has valid format else false
         */
        private boolean isValidIPv6Address(String address) {
            int addressLength = address.length();
            if (addressLength < 4) { // empty IPv6
                return false;
            }

            if (address.charAt(0) != '[' ||
                    address.charAt(addressLength - 1) != ']') {
                return false;
            }

            String IPv6 = address.substring(1, addressLength - 1);
            // Format according to RFC 3513
            int IPv6Length = addressLength - 2;
            int ptrChar = 0;
            int numHexPieces = 0; // number of 16-bit pieces in the address
            char currChar = 0;
            String hexString = null;
            String separator = null;
            int length;
            boolean isDoubleColon = false;
            boolean lastSeparator = true;
            while (ptrChar < IPv6Length) {
                currChar = IPv6.charAt(ptrChar);
                if (isHex(currChar)) {
                    hexString = getNextHexValue(IPv6, ptrChar, false);
                    length = hexString.length();
                    if (length > 4) {
                        // 16-bit value couldn't contain more than 4 digits
                        return false;
                    }

                    ptrChar += length;
                    lastSeparator = false;
                } else if (currChar == ':') { // colon
                    if (++numHexPieces > 7) { // more than 8 hex pieces
                        return false;
                    }

                    separator = getNextHexValue(IPv6, ptrChar, true);
                    length = separator.length();
                    if (separator.equals("::")) { // double colon
                        // double colon
                        if (isDoubleColon) { // double colon twice
                            return false;
                        }
                        isDoubleColon = true;
                    } else if (length > 1) { // wrong separator
                        return false;
                    } else  { // separator is equal ":"
                        if (ptrChar == 0) { // first symbol is ":"
                            return false;
                        }
                        if (isDoubleColon && numHexPieces > 6) {
                            // no more 7 pieces when "::"
                            return false;
                        }
                    }
                    ptrChar += length;
                    lastSeparator = true;
                } else if (currChar == '.') { // IPv4 suffix
                    if (hexString == null || !isDecimal(hexString)) {
                        // previous hex piece must be start of IPv4
                        return false;
                    }

                    if (!((!isDoubleColon && numHexPieces == 6) ||
                          (isDoubleColon && numHexPieces < 6))) {
                        return false;
                    }
                    ptrChar -= hexString.length();
                    return isValidIPv4Address(IPv6.substring(ptrChar));
                } else if (currChar == '/') { // bit prefix
                    break;
                } else { // wrong symbol
                    return false;
                }
            }

            if (lastSeparator && separator.equals(":")) {
                return false;
            }

            if (!((!isDoubleColon && numHexPieces == 7) ||
                  (isDoubleColon && numHexPieces < 7))) {
                return false;
            }

            if (currChar == '/') { // bit prefix
                ptrChar++;
                String decString = getNextDecValue(IPv6, ptrChar, false);
                length = decString.length();
                if (length == 0 || ptrChar + length < IPv6Length) {
                    return false;
                }

                int i = Integer.parseInt(decString);
                if (i < 1 || i > 128) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Checks is IPv4 address has a valid format.
         *
         * @param address the string representation of IPv4 address
         * @return true when IPv4 address has valid format else false
         */
        private boolean isValidIPv4Address(String address) {
            if (address.length() < 7) { // less than 0.0.0.0
                return false;
            }
            int IPv4Length = address.length();
            int ptrChar = 0;
            int numDecPieces = 0; // number of 8-bit pieces in the address
            char currChar;
            String decString;
            String separator;
            int length, value;
            boolean lastSeparator = true;
            while (ptrChar < IPv4Length) {
                currChar = address.charAt(ptrChar);
                if (Character.isDigit(currChar)) {
                    decString = getNextDecValue(address, ptrChar, false);
                    value = Integer.parseInt(decString);
                    if (value < 0 || value > 255) {
                        return false;
                    }

                    ptrChar += decString.length();
                    lastSeparator = false;
                } else if (currChar == '.') {
                    if (++numDecPieces > 3) { // more than 4 hex pieces
                        return false;
                    }

                    separator = getNextDecValue(address, ptrChar, true);
                    length = separator.length();
                    if (length > 1) {
                        return false;
                    }

                    ptrChar += length;
                    lastSeparator = true;
                } else { // wrong symbol
                    return false;
                }
            }

            if (lastSeparator || numDecPieces < 3) {
                return false;
            }
            return true;
        }

        /**
         * Checks is host name has a valid format (RFC 2396).
         *
         * @param host the host name for checking
         * @return true when the host name has a valid format
         */
        private boolean isValidHostName(String host) {
            char currChar;
            int ptrChar = 0;
            int lenDomain = 0;
            while (ptrChar < host.length()) {
                currChar = host.charAt(ptrChar++);
                if (currChar == '.') {
                    if (lenDomain == 0) {
                        return false;
                    }

                    lenDomain = 0;
                } else if (currChar == '-' || Character.isDigit(currChar)) {
                    if (lenDomain == 0) {
                        return false;
                    }

                    lenDomain++;
                } else if (Character.isLowerCase(currChar) ||
                           Character.isUpperCase(currChar)) {
                    lenDomain++;
                } else {
                    return false;
                }
            }
            return true;
        }

        /**
         * Checks is the next symbol is hex.
         *
         * @param sym the given symbol
         * @return true when symbol is hex
         */
        private boolean isHex(char sym) {
            return (Character.isDigit(sym) || "ABCDEFabcdef".indexOf(sym) > -1);
        }

        /**
         * Gets the next hex substring.
         *
         * @param str the source string
         * @param offset the start index of substring
         * @param isSeparator false when we need hex value else separator
         * @return the hex substring or separator
         */
        private String getNextHexValue(String str, int offset,
                boolean isSeparator) {
            StringBuffer strOut = new StringBuffer();
            int length = str.length();
            int i = offset;
            char sym;
            while (i < length) {
                sym = str.charAt(i++);
                if ((!isHex(sym) && !isSeparator) ||
                        ((isHex(sym) || sym == '/') && isSeparator)) {
                    break;
                }
                strOut.append(sym);
            }

            return strOut.toString();
        }

        /**
         * Gets the next decimal substring.
         *
         * @param str the source string
         * @param offset the start index of substring
         * @param isSeparator false when we need dec value else separator
         * @return the dec substring or separator
         */
        private String getNextDecValue(String str, int offset,
                boolean isSeparator) {
            StringBuffer strOut = new StringBuffer();
            int length = str.length();
            int i = offset;
            char sym;
            while (i < length) {
                sym = str.charAt(i++);
                if ((!Character.isDigit(sym) && !isSeparator) ||
                        (Character.isDigit(sym) && isSeparator)) {
                    break;
                }
                strOut.append(sym);
            }

            return strOut.toString();
        }

        /**
         * Checks is the given string contains decimal symbols only.
         *
         * @param str the source string
         * @return true when all symbols are dacimal else false
         */
        private boolean isDecimal(String str) {
            for (int i = 0; i < str.length(); i++) {
                if (!Character.isDigit(str.charAt(i))) {
                    return false;
                }

            }

            return true;
        }
    }
}
