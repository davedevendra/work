/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.impl;

import com.oracle.iot.client.TransportException;
import com.oracle.iot.client.HttpResponse;
import com.oracle.iot.client.RestApi;
import com.oracle.iot.client.SecureConnection;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONException;
import org.json.JSONObject;
import com.oracle.iot.client.StorageObject;

/**
 * The StorageConnection transfers content to the Storage Cloud Service.
 * Service. There is one StorageConnection instance per client.
 */
public abstract class StorageConnectionBase implements StorageConnection {
    
    private static final String STORAGE_CLOUD_HOST;
    private static final String DEFAULT_STORAGE_CLOUD_HOST = "storage.oraclecloud.com";
    private static final boolean virtualStorageDirectories;
    /**
     * REST resource for storage authentication
     */
    private static final String REST_STORAGE_AUTHENTICATION =
        RestApi.V2.getReqRoot()+"/provisioner/storage";
    private static final String AUTH_TOKEN_HEADER = "X-Auth-Token";
    private static final int DEFAULT_RETRY_LIMIT = 2;
    private static final int BAD_CHECKSUM_STATUS = -1;
    /**
     * Number of times to retry a transfer if the token expires.
     */
    private static final int RETRY_LIMIT;
    private final SecureConnection secureConnection;
    private String storageContainerUrl = null;
    private String authToken = null;
    
    private boolean closed;

    static {
        Integer val = Integer.getInteger(
            "com.oracle.iot.client.storage_connection_retry_limit");
        RETRY_LIMIT =
            ((val != null) && (val > 0))? val : DEFAULT_RETRY_LIMIT;

        STORAGE_CLOUD_HOST = System.getProperty("com.oracle.iot.client.storage_connection_host_name", DEFAULT_STORAGE_CLOUD_HOST);

        final String value = (String)System.getProperty(
            "oracle.iot.client.disable_storage_object_prefix");
        virtualStorageDirectories = value == null || "".equals(value) ||
            !Boolean.parseBoolean(value);
    }

    
    protected StorageConnectionBase(SecureConnection secureConnection) {
        this.secureConnection = secureConnection;
        this.closed = false;
        authenticate();
    }

    final public void sync(StorageObject storageObject) throws IOException, GeneralSecurityException {
        if (storageObject.getInputStream() != null) {
            upload(storageObject);
        }
        else if (storageObject.getOutputStream() != null) {
            download(storageObject);
        }
        else throw new IllegalArgumentException("InputStream and OutputStream are not set.");
    }

    private void upload(StorageObject storageObject) throws IOException, GeneralSecurityException {
        int responseCode = transfer(storageObject);
        if (responseCode != 201) {
            throw new TransportException(responseCode, "Upload " + storageObject.getURI());
        }
    }

    private void download(StorageObject storageObject) throws IOException, GeneralSecurityException {
        int responseCode = transfer(storageObject);
        if (responseCode != 200 && responseCode != 206) {
            throw new TransportException(responseCode, "Download " + storageObject.getURI());
        }
    }
    
    final public StorageObject createStorageObject(String clientId,
            String name, String contentType)
            throws IOException, GeneralSecurityException {
        final String path;
        if (virtualStorageDirectories && clientId != null) {
            path = clientId + "/" + name;
        } else {
            path = name;
        }

        final String contentStorageLocation =
            getStorageContainerUrl() + "/" + path;
        final StorageObject storageObject =
            createStorageObject(contentStorageLocation, name,
                                contentType, null, null, -1);

        return storageObject;
    }

    final public StorageObject createStorageObject(String storageUrl)
            throws IOException, GeneralSecurityException {

        final URL url;
        final String name;
        try {
            url = new URL(storageUrl);
            String fullContainerUrl = getStorageContainerUrl() + "/";
            if (!storageUrl.startsWith(fullContainerUrl)) {
                throw new GeneralSecurityException("Storage container URL does not match.");
            }

            if (virtualStorageDirectories) {
                name = storageUrl.substring(storageUrl.lastIndexOf('/') + 1);
            } else {
                name = storageUrl.substring(fullContainerUrl.length());
            }
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("Storage Cloud URL is invalid", ex);
        }

        int i=0;
        while (i < RETRY_LIMIT) {
            final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setRequestProperty(AUTH_TOKEN_HEADER, authToken);
            connection.connect();

            final int length = connection.getContentLength();
            final String type = connection.getContentType();
            final String encoding = connection.getContentEncoding();
            final String date = connection.getHeaderField("Last-Modified");
            final int responseCode = connection.getResponseCode();

            if (responseCode == 200) {
                return createStorageObject(storageUrl, name, type, encoding, date, length);
            } else if (responseCode != 401) {
                throw new TransportException(responseCode, "HEAD " + storageUrl);
            } else {
                authenticate();
            }
            i++;
        }
        throw new TransportException(401, "HEAD " + storageUrl);
    }

    protected abstract StorageObject createStorageObject(
                                           String storageContainerUrl,
                                           String name,
                                           String type,
                                           String encoding,
                                           String date,
                                           int length);

    public static boolean isStorageCloudURI(String uri) {
        try {
            if (new URI(uri).getHost().contains(STORAGE_CLOUD_HOST)) {
                return true;
            }
        } catch (Exception ex) {
        }
        return false;
    }

    @Override
    final public synchronized void close() throws IOException {
        if (!closed) {
            closed = true;
        }
    }

    private String getStorageContainerUrl() throws IOException, GeneralSecurityException {
        if (storageContainerUrl == null) {
            StorageAuthenticationResponse storageAuthenticationResponse
                    = getStorageAuthentication();
            storageContainerUrl = storageAuthenticationResponse.getStorageContainerUrl();
            authToken = storageAuthenticationResponse.getAuthToken();
        }
        return storageContainerUrl;
    }

    private int transfer(StorageObject storageObject)
            throws IOException, GeneralSecurityException {
        int responseCode = 0;
        if(authToken == null) {
            if (!authenticate()) {
                responseCode = 401;
            }
        }

        final InputStream inputStream = storageObject.getInputStream();
        final boolean upload = inputStream != null;
        if (upload && inputStream.markSupported()) {
            inputStream.mark(Integer.MAX_VALUE);
        }

        final String scPath = storageObject.getURI() == null
            ? storageContainerUrl + "/" + storageObject.getName()
            : storageObject.getURI();

        int retries = 0;
        while (retries < RETRY_LIMIT) {
            if (authToken != null) {
                responseCode = transferContent(scPath, storageObject);
            }
            if ((upload && responseCode == 201)
                    || (!upload && (responseCode == 200 || responseCode == 206))) {
                break;
            }
            if (responseCode == 401) {
                //token expired
                authenticate();
            } else if (responseCode == BAD_CHECKSUM_STATUS) {
                //checksum mismatch- just try transfer again
            } else {
                //don't retry other errors
                break;
            }
            if (upload) {
                if (inputStream.markSupported()) {
                    inputStream.reset();
                } else if (!(inputStream instanceof FileInputStream)) {
                    // cannot reset stream for retry
                    return responseCode;
                } else {
                    FileInputStream fs = (FileInputStream)inputStream;
                    FileChannel fc = fs.getChannel();
                    fc.position(0);
                }
            }
            retries++;
        }
        return responseCode;
    }

    private int transferContent(final String scPath,
            StorageObject storageObject) throws IOException {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException ex) {
            getLogger().log(Level.WARNING, "Storage Cloud Service: checksum could not be verified.", ex);
        }
        final String contentType = storageObject.getType();
        final String encoding = storageObject.getEncoding();
        final InputStream inputStream = storageObject.getInputStream();
        final OutputStream outputStream = storageObject.getOutputStream();
        long transferredBytes = 0;
        String checksum;
        final boolean upload = inputStream != null;
        int responseCode;
        OutputStream os = null;
        InputStream is = null;
        URL url = new URL(scPath);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (upload) {
            is = inputStream;
            connection.setRequestMethod("PUT");
            connection.setChunkedStreamingMode(1024);
            connection.setRequestProperty("Content-Type", contentType != null ? contentType : "application/octet-stream");
            if (encoding != null) {
                connection.setRequestProperty("Content-Encoding", encoding);
            }
            connection.setDoOutput(true);
        } else {
            os = outputStream;
            connection.setRequestMethod("GET");
            connection.setDoOutput(false);
        }

        connection.setRequestProperty(AUTH_TOKEN_HEADER, authToken);
        
        connection.connect();
        if (upload) {
            os = connection.getOutputStream();
        } else {
            is = connection.getInputStream();
        }
        byte[] b = new byte[1024];
        int len;
        while ((len = is.read(b)) != -1) {
            if (((StorageObjectDelegate)storageObject).isCancelled()) {
                if (upload) {
                    os.close();
                } else {
                    is.close();
                }
                return -2;
            }
            os.write(b, 0, len);
            if (digest != null) {
                digest.update(b, 0, len);
            }
            ((StorageObjectDelegate)storageObject).setTransferredBytes(transferredBytes+= len);
        }
        if (upload) {
            os.close();
        } else {
            is.close();
        }

        responseCode = connection.getResponseCode();
        checksum = connection.getHeaderField("ETag");
        
        final String date = connection.getHeaderField("Last-Modified");
        
        if (checksum != null && digest != null) {
            //Verify MD5 checksum (objects < 5GB)
            String digestOut = bytesToHexString(digest.digest());
            if (!checksum.equals(digestOut)) {
                getLogger().log(Level.INFO, "Storage Cloud Service: checksum mismatch");
                return BAD_CHECKSUM_STATUS;
            }
        }
        
        if (upload && responseCode == 201) {
            ((StorageObjectDelegate)storageObject).setMetadata(date, transferredBytes);
            is.close();
        } else if (!upload && (responseCode == 200 || responseCode == 206)) {
            os.close();
        }

        return responseCode;
    }

    /**
     * GET the storage authentication.
     * @return the StorageAuthenticationResponse to the request
     * @throws IOException
     * @throws GeneralSecurityException 
     */
    private StorageAuthenticationResponse getStorageAuthentication()
            throws IOException, GeneralSecurityException {

        HttpResponse response =
            secureConnection.get(REST_STORAGE_AUTHENTICATION);

        int status = response.getStatus();

        if (status != 200) {
            throw new TransportException(status, response.getVerboseStatus("GET",
                REST_STORAGE_AUTHENTICATION));
        }

        String jsonResponse = new String(response.getData(), "UTF-8");
        JSONObject json;
        try {
            json = new JSONObject(jsonResponse);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        StorageAuthenticationResponse storageAuthenticationResponse =
            StorageAuthenticationResponse.fromJson(json);
        if (getLogger().isLoggable(Level.FINEST)) {
            getLogger().log(Level.FINEST,
                storageAuthenticationResponse.toString());
        }

        return storageAuthenticationResponse;
    }
    
    private boolean authenticate() {
        try {
            StorageAuthenticationResponse storageAuthenticationResponse
                    = getStorageAuthentication();
            storageContainerUrl = storageAuthenticationResponse.getStorageContainerUrl();
            authToken = storageAuthenticationResponse.getAuthToken();
            return true;
        } catch (Exception e) {
            getLogger().log(Level.INFO, "IoT storage API cannot be accessed: " + e.getMessage());
        }
        return false;
    }

    private static String bytesToHexString(byte[] in) {
        final StringBuilder sb = new StringBuilder();
        for(byte b : in) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
