/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.enterprise;

import com.oracle.iot.client.enterprise.DeviceApp;
import oracle.iot.client.enterprise.Device;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DeviceApp implementation.
 */
public class DeviceAppImpl extends DeviceApp {

    private static final String DEVICE_APPLICATION = "DEVICE_APPLICATION";

    private final Map<DeviceApp.Field, String> fields;
    private final String id;
    private final List<String> deviceModels;
    private final Map<String, String> metadata;

    private DeviceAppImpl(String id, Map<DeviceApp.Field, String> fields, List<String> deviceModels, Map<String, String> metadata) {
        this.id = id;
        this.fields = fields;
        this.deviceModels = deviceModels;
        this.metadata = metadata;
    }

    /**
     * Creates a new {@code DeviceApp} instance from the given JSON object.
     * @param object the json object to use to build the DeviceApp instance
     * @return a new DeviceApp instance created from the given JSON object.
     * @throws JSONException if the provided object cannot be interpreted as
     * a DeviceApp.
     */
    public static DeviceApp from(JSONObject object) throws JSONException {
        Map<DeviceApp.Field, String> fields =
            new EnumMap<DeviceApp.Field, String>(DeviceApp.Field.class);

        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next(); 
            DeviceApp.Field f = DeviceApp.Field.fromAlias(key);
            if (f != null) {
                fields.put(f, object.opt(key).toString());
            }
        }

        String t = fields.get(Field.TYPE);
        if (!DEVICE_APPLICATION.equals(t)) {
            throw new JSONException("Not a device Application: " + t);
        }

        String id = fields.get(Field.ID);
        if (id == null) {
            throw new JSONException("Id is missing");
        }

        JSONArray jsonModels =
            object.optJSONArray(Device.Field.DEVICE_MODELS.alias());
        List<String> deviceModels = new LinkedList<String>();
        if (jsonModels != null) {
            for (int i = 0, size = jsonModels.length(); i < size; i++) {
                Object model = jsonModels.opt(i);
                String urn;
                if (model instanceof JSONObject) {
                    JSONObject jsonObject = (JSONObject)model;
                    urn = jsonObject.getString("urn");
                } else {
                    urn = model.toString();
                }

                deviceModels.add(urn);
            }
        }

        JSONObject jsonMetadata =
            object.optJSONObject(Device.Field.METADATA.alias());
        Map<String, String> metadata = new HashMap<String, String>();
        if (jsonMetadata != null) {
            keys = jsonMetadata.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = jsonMetadata.opt(key);
                metadata.put(key, (value != null) ? value.toString() : "");
            }
        }

        return new DeviceAppImpl(id, fields, deviceModels, metadata);
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public String getMetadata(String key) {
        return this.metadata.get(key);
    }

    @Override
    public List<String> getDeviceModels() {
        return this.deviceModels;
    }

    @Override
    public Map<String, String> getMetadata() {
        return this.metadata;
    }

    @Override
    public String getValue(Field field) {
        return this.fields.get(field);
    }
}
