/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates.  All rights reserved.
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
import com.oracle.iot.client.message.StatusCode;
import oracle.iot.client.DeviceModel;
import oracle.iot.client.enterprise.Filter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DeviceModel Factory.
 */
public class DeviceModelFactory {

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }

    final private static String DM_PREFIX = "dm-";
    final private static String LOCAL_STORE;
    final private static String FILE_TYPE = ".json";
    final private static boolean ALLOW_DRAFT_MODELS;

    static {

        final String localStorePathname = System.getProperty("oracle.iot.client.device_model_store");

        // If device_model_store is set, then make sure it is readable/writable,
        // or try to create the directory if it does not exist.
        LOCAL_STORE = checkLocalStorePath(localStorePathname);

        ALLOW_DRAFT_MODELS = Boolean.getBoolean(
                "com.oracle.iot.client.device.allow_draft_device_models");
    }

    private static String checkLocalStorePath(String localStorePathname) {

        if (localStorePathname == null || "".equals(localStorePathname)) {
            return null;
        }

        final File file = new File(localStorePathname);
        if (file.exists()) {
            if (!file.isDirectory()) {
                getLogger().log(Level.WARNING, "Cannot local device model store is not a directory: " + localStorePathname);
                return null;
            }
            if (file.canRead() && file.canWrite()) {
                return file.getAbsolutePath();
            }
            if (!file.canRead()) {
                getLogger().log(Level.WARNING, "Cannot read from local device model store: " + localStorePathname);
            }
            if (!file.canWrite()) {
                getLogger().log(Level.WARNING, "Cannot write to local device model store: " + localStorePathname);
            }

        } else if (file.mkdir()) {
            return file.getAbsolutePath();

        } else {
            getLogger().log(Level.WARNING, "Cannot create local device model store: " + localStorePathname);
        }
        return null;
    }

    private DeviceModelFactory() {}

    /**
     * Get a device model from the IoT server.
     *
     * @param secureConnection a connection to the server.
     * @param urn the URN of desired device model
     *
     * @return device model or null if not found
     *
     * @throws NullPointerException if an argument is null
     * @throws IOException if the request failed
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid
     */
    public static DeviceModel getDeviceModel(SecureConnection secureConnection,
                                             String urn) throws IOException, GeneralSecurityException {
        return getDeviceModel(null, secureConnection, urn);
    }

    /**
     * Get a device model from the IoT server.
     *
     * @param aid the IoT application identifier
     * @param secureConnection a connection to the server.
     * @param urn the URN of desired device model
     *
     * @return device model or null if not found
     *
     * @throws NullPointerException if an argument is null
     * @throws IOException if the request failed
     * @throws GeneralSecurityException when key or signature algorithm class
     *                                  cannot be loaded, or the key is not in
     *                                  the trusted assets store, or the
     *                                  private key is invalid
     */
    public static DeviceModel getDeviceModel(String aid,
            SecureConnection secureConnection, String urn)
            throws IOException, GeneralSecurityException {
        String path = null;

        if (LOCAL_STORE != null) {
            Reader reader = null;
            try {
                String encoded = URLEncoder.encode(urn, "UTF-8");
                path = LOCAL_STORE + File.separator + DM_PREFIX + encoded +
                        FILE_TYPE;
                reader = new InputStreamReader(new FileInputStream(path), "UTF-8");
                return DeviceModelParser.fromJson(reader);
            } catch (JSONException e) {
                getLogger().log(Level.SEVERE,e.getMessage());
                throw new IOException(e);
            } catch (FileNotFoundException e) {
                // The model is not local, so get it from the server
            } catch (UnsupportedEncodingException e) {
                // UTF-8 is a required encoding so we should never get here.
                getLogger().log(Level.SEVERE,e.getMessage());
                throw e;
            } finally {
                if (reader != null) {
                    try { reader.close(); }
                    catch (IOException ignored) {}
                }
            }
        }

        String uri;
        try {
            if (aid != null) {
                Filter f = Filter.eq("urn", urn);
                uri = new URI(null, null, RestApi.V2.getReqRoot() +
                        "/apps/" + aid + "/deviceModels",
                        "q=" + f.toJson().toString(),
                        null).toString();
            } else {
                /*
                 * TODO: The server does not allow devices to list device
                 *       models and use a filter like enterprise clients can,
                 *       so we filter in the method below, when the server
                 *       allows it, use the the code below instead.
                 * 
                 * Filter f = Filter.and(Filter.eq("urn", urn),
                 *            Filter.eq("draft", false));
                 * uri = new URI(null, null,
                 *       RestApi.V2.getReqRoot() +  "/deviceModels",
                 *       "q=" + f.toJson().toString(),
                 *       null).toString(); 
                 */
                uri = new URI(null, null,
                        RestApi.V2.getReqRoot() + "/deviceModels/" + urn,
                        "expand=formats", null).toString();
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        byte[] data = getObject(secureConnection, uri);
        if (data == null) {
            // Model not found
            return null;
        }

        DeviceModel dm = null;
        JSONObject devicePolicy = null;
        try {
            JSONObject object = new JSONObject(new String(data, "UTF-8"));
            if (aid != null) {
                // we have received a collection of Device Models
                JSONArray items = object.getJSONArray("items");
                if (items.length() > 0) {
                    JSONObject jsonObject = items.getJSONObject(0);
                    dm = DeviceModelParser.fromJson(jsonObject);
                    if (LOCAL_STORE != null) {
                        data = jsonObject.toString().getBytes("UTF8");
                    }
                }
            } else {
                // we have received a single Device Model
                if ((!ALLOW_DRAFT_MODELS) && object.optBoolean("draft", false)) {
                    return null;
                }

                dm = DeviceModelParser.fromJson(object);
            }

        } catch (Exception e) {
            // could be JSONException or ClassCastException if server response
            // is incorrect
            throw new IOException("GET " + uri.toString() + ": " +
                                  e.getMessage());
        }

        if (LOCAL_STORE != null) {
            /*
             * persist model so that next time we do not have to
             * go to server for the same model
             */
            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new java.io.FileOutputStream(path);
                fileOutputStream.write(data);

            } catch (IOException e) {
                getLogger().log(Level.SEVERE, e.toString());

            } finally {
                if (fileOutputStream != null) {
                    try { fileOutputStream.close(); }
                    catch (IOException ignored) {}
                }
            }
        }

        return dm;
    }

    private static byte[] getObject(SecureConnection secureConnection, String uri)
            throws IOException, GeneralSecurityException {

        HttpResponse res = secureConnection.get(uri);

        int status = res.getStatus();
        if (status == StatusCode.NOT_FOUND.getCode()) {
            return null;
        }

        if (status != StatusCode.OK.getCode()) {
            throw new TransportException(status, res.getVerboseStatus("GET", uri));
        }

        byte[] data = res.getData();
        if (data == null) {
            throw new IOException("GET " + uri + " failed: no data received");
        }

        return data;
    }
}

