/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.impl.enterprise;

import org.json.JSONException;
import org.json.JSONObject;

import oracle.iot.client.enterprise.Application;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * An {@code Application} lists the characteristics of an IoT CS application.
 *
 */
public class ApplicationImpl extends Application {

    private final String id;
    private final String name;
    private final String description;
    private final Map<String,String> metadata;

    private ApplicationImpl(String id, String name, String description, Map<String, String> metadata) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.metadata = metadata;
    }

    public static Application fromJson(JSONObject object) throws JSONException {
        try {
            HashMap<String, String> mt = new HashMap<String, String>();
            JSONObject metadata = object.optJSONObject("metadata");
            if (metadata != null) {
                Iterator<String> keys = metadata.keys();
                while (keys.hasNext()) {
                    String key = keys.next(); 
                    mt.put(key, metadata.opt(key).toString());
                }
            }

            return new ApplicationImpl(
                    object.getString("id"),
                    object.getString("name"),
                    object.optString("description", ""),
                    mt);
        } catch (NullPointerException e) {
            throw new JSONException("field is missing");
        }
    }


    /**
     * Application identifier
     *
     * @return the identifier of the application
     */
    public String getId() {
        return this.id;
    }

    /**
     * Name of the application
     *
     * @return the name of the application
     */
    public String getName() {
        return this.name;
    }

    /**
     * Free form description of the application
     *
     * @return the description of the application
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * Get the application Metadata for the specified {@code key}
     *
     * @param key the metadata to look for
     * @return the value corresponding to the specified {@code key}
     */
    public String getMetadata(String key) {
        return this.metadata.get(key);
    }

    /**
     * Get the application Metadata
     *
     * @return a Map associating keys and values.
     */
    public Map<String, String> getMetadata() {
        return this.metadata;
    }

}
