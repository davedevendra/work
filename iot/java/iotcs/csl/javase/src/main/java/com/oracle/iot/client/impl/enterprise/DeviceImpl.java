/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.enterprise;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import oracle.iot.client.enterprise.Device;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Device implementation.
 */
public class DeviceImpl extends Device {

    private static final String DCD = "DIRECTLY_CONNECTED_DEVICE";
    private static final String ICD = "INDIRECTLY_CONNECTED_DEVICE";
    
    private static final String LOCATION_LATITUDE = "latitude";
    private static final String LOCATION_LONGITUDE = "longitude";
    private static final String LOCATION_ALTITUDE = "altitude";
    private static final String LOCATION_UNCERTAINTY = "uncertainty";

    private final Map<Field, String> fields;
    private final boolean isDirectlyConnected;
    private final String id;
    private final List<String> deviceModels;
    private final Map<String, String> metadata;
    private final Device.Location location;

    private DeviceImpl(String id, Map<Field, String> fields, List<String> deviceModels, Map<String, String> metadata, Device.Location location, boolean isDCD) {
        this.id = id;
        this.fields = fields;
        this.deviceModels = deviceModels;
        this.metadata = metadata;
        this.location = location;
        this.isDirectlyConnected = isDCD;
    }

    public static Device from(JSONObject object) throws JSONException {
        try {
            Map<Field, String> fields = new HashMap<Field, String>();

            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Field f = Field.fromAlias(key);
                if (f != null) {
                    fields.put(f, object.opt(key).toString());
                }
            }

            String t = fields.get(Field.TYPE);
            boolean isDCD = (t != null) && t.equals(DCD);
            String id = fields.get(Field.ID);
            JSONArray jsonModels =
                object.optJSONArray(Field.DEVICE_MODELS.alias());
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
                object.optJSONObject(Field.METADATA.alias());
            Map<String, String> metadata = new HashMap<String, String>();
            if (jsonMetadata != null) {
                keys = jsonMetadata.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object value = jsonMetadata.opt(key);
                    metadata.put(key, (value != null) ? value.toString() : "");
                }
            }
            JSONObject jsonLocation = object.optJSONObject(Field.LOCATION.alias());
            float latitude = Float.NaN;
            float longitude = Float.NaN;
            float altitude = Float.NaN;
            float uncertainty = Float.NaN;
            if (jsonLocation != null) {
                keys = jsonLocation.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (key.equals(LOCATION_LATITUDE)) {
                        latitude = (float)jsonLocation.optDouble(key);
                    } else if (key.equals(LOCATION_LONGITUDE)) {
                        longitude = (float)jsonLocation.optDouble(key);
                    } else if (key.equals(LOCATION_ALTITUDE)) {
                        altitude = (float)jsonLocation.optDouble(key);
                    } else if (key.equals(LOCATION_UNCERTAINTY)) {
                        uncertainty = (float)jsonLocation.optDouble(key);
                    }
                }
            }
            Device.Location location = new Device.Location(latitude, longitude, altitude, uncertainty);
            return new DeviceImpl(id, fields, deviceModels, metadata, location, isDCD);
        } catch (NullPointerException e) {
            throw new JSONException("Attribute 'type' or 'id' not found: " +
                                    object.toString());
        }
    }

    @Override
    public List<String> getDeviceModels() {
        return deviceModels;
    }

    @Override
    public String getValue(Field field) {
        return fields.get(field);
    }

    @Override
    public boolean isDirectlyConnected() {
        return this.isDirectlyConnected;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public Map<String, String> getMetadata() {
        return this.metadata;
    }

    @Override
    public String getMetadata(String key) {
        return this.metadata.get(key);
    }
    
    @Override
    public Device.Location getLocation() {
        return this.location;
    }

    private String toJson() {
        JSONObject builder = new JSONObject();

        try {
             builder.put("type", isDirectlyConnected? DCD : ICD).put("id", id);

             for (Map.Entry e: fields.entrySet()) {
                 String alias = Field.valueOf(e.getKey().toString()).alias();
                 builder.put( alias, e.getValue().toString());
             }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return builder.toString();
    }

    public String toString() {
        return "Endpoint: " + this.toJson();
    }
}
