/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.impl;

import oracle.iot.client.DeviceModel;

import com.oracle.iot.client.DeviceModelAction;
import com.oracle.iot.client.DeviceModelAttribute;
import com.oracle.iot.client.DeviceModelFormat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.Reader;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 */
public class DeviceModelParser {

    public static DeviceModel fromJson(String jsonModel)
            throws JSONException {
        return fromJson(new JSONObject(jsonModel));
    }

    public static DeviceModel fromJson(Reader reader)
            throws JSONException {
        StringBuilder sb = new StringBuilder();
        int c;
        try {
            while ((c = reader.read()) != -1) {
                sb.append((char) c);
            }
        } catch (IOException e) {
            throw new JSONException(e.getMessage());
        }

        return fromJson(sb.toString());
    }

    public static DeviceModel fromJson(JSONObject root)
            throws JSONException {
        String urn = root.optString("urn", null);
        if (urn == null || urn.length() == 0) {
            throw new JSONException("urn cannot be null or empty");
        }

        String name = root.optString("name", null);
        if (name == null || name.length() == 0) {
            throw new JSONException("name cannot be null or empty");
        }

        String description = root.optString("description", null);

        // Attributes
        List<DeviceModelAttribute> attributes =
            new ArrayList<DeviceModelAttribute>();
        List<String> existingAttrNames = new ArrayList<String>();

        JSONArray jsonAtts = root.optJSONArray("attributes");
        if (jsonAtts != null) {
            for (int i = 0, size = jsonAtts.length(); i < size ;i++) {
                DeviceModelAttribute attribute = createDeviceModelAttribute(
                    urn, existingAttrNames, jsonAtts.getJSONObject(i));
                attributes.add(attribute);
                existingAttrNames.add(attribute.getName());
            }
        }

        // Actions
        List<DeviceModelAction> actions = new ArrayList<DeviceModelAction>();
        List<String> existingActionsNames = new ArrayList<String>();

        JSONArray jsonActions = root.optJSONArray("actions");
        if (jsonActions != null) {
            for (int i = 0, size = jsonActions.length(); i < size ;i++) {
                DeviceModelAction action = createDeviceModelAction(
                    existingActionsNames, jsonActions.getJSONObject(i));
                actions.add(action);
                existingActionsNames.add(action.getName());
            }
        }

        // Formats
        List<DeviceModelFormat> formats =
            new ArrayList<DeviceModelFormat>();
        JSONArray jsonFormats = root.optJSONArray("formats");
        if (jsonFormats != null) {
            for (int i = 0, size = jsonFormats.length(); i < size ;i++) {
                DeviceModelFormat format = createDeviceModelFormat(
                    jsonFormats.getJSONObject(i));
                formats.add(format);
            }
        }

        return new DeviceModelImpl(urn, name, description,
            attributes.toArray(new DeviceModelAttribute[0]),
            actions.toArray(new DeviceModelAction[0]),
            formats.toArray(new DeviceModelFormat[0]));
    }

    private static DeviceModelAttributeImpl createDeviceModelAttribute(
            String urn, List<String> existingFieldNames,
            JSONObject root) throws JSONException {
        String name = root.getString("name");
        if (existingFieldNames.contains(name)) {
            throw new JSONException("duplicate attribute: " + name);
        }

        String description = root.optString("description", null);
        String alias = root.optString("alias", null);
        String range = root.optString("range", null);
        DeviceModelAttribute.Access access =
            (root.optBoolean("writable", false) ?
               DeviceModelAttribute.Access.READ_WRITE :
               DeviceModelAttribute.Access.READ_ONLY);

        DeviceModelAttribute.Type type = null;
        try {
            String t = root.optString("type", null);
            if (t != null) {
                type = DeviceModelAttribute.Type.valueOf(t);
            }
        } catch (IllegalArgumentException e) {
            throw new JSONException("Illegal type: " +
                                    root.optString("type", ""));
        }
        Number min = null;
        Number max = null;
        try {
            if (range != null) {
                NumberFormat nf = NumberFormat.getNumberInstance(Locale.ROOT);

                String[] strings = range.split(",");
                min = nf.parse(strings[0]);
                max = nf.parse(strings[1]);
            }
        } catch (Exception e) {
            throw new JSONException("Invalid range: " + range);
        }

        Object defaultValue = root.opt("defaultValue");
        if (defaultValue != null) {
            if (type == DeviceModelAttribute.Type.INTEGER && defaultValue instanceof Number) {
                defaultValue = ((Number) defaultValue).intValue();
            } else if (type == DeviceModelAttribute.Type.NUMBER && defaultValue instanceof Number) {
                // nothing to do
            } else if (type == DeviceModelAttribute.Type.STRING && defaultValue instanceof String) {
                // nothing to do
            } else if (type == DeviceModelAttribute.Type.BOOLEAN && defaultValue instanceof Boolean) {
                // nothing to do
            } else if (type == DeviceModelAttribute.Type.DATETIME && defaultValue instanceof Number) {
                defaultValue = new Date(((Number) defaultValue).longValue());
            } else if (type == DeviceModelAttribute.Type.URI && defaultValue instanceof String) {
                //TODO: Only HL API uses defaults, so only HL object is supported for now
                //TODO: StorageObject case?
                defaultValue = new oracle.iot.client.ExternalObject((String)defaultValue);
            }else {
                throw new JSONException("Invalid defaultValue: " + defaultValue);
            }
        }
        return new DeviceModelAttributeImpl<Object>(urn, name, description, type, min,
                                            max, access, alias, defaultValue);
    }

    private static DeviceModelAction createDeviceModelAction(
            List<String> existingActionNames, JSONObject root)
            throws JSONException {
        String name = root.getString("name");
        if (existingActionNames.contains(name)) {
            throw new JSONException("duplicate action:" + name);
        }


        String description = root.optString("description", null);
        String alias = root.optString("alias", null);

        DeviceModelAttribute.Type type = null;
        try {
            String argType = root.optString("argType", null);
            if (argType != null) {
                type = DeviceModelAttribute.Type.valueOf(argType);
            }
        } catch (Exception e) {
            throw new JSONException("Invalid action type: " +
                                    root.optString("argType", ""));
        }

        return new DeviceModelActionImpl(name, description, type, null, null,
                                         alias);
    }

    private static DeviceModelFormat createDeviceModelFormat(
            JSONObject root) throws JSONException {
        String urn = root.getString("urn");
        String name = root.getString("name");
        String description = root.optString("description", "");
        String type = root.getString("type");

        List<DeviceModelFormat.Field> fields =
            new ArrayList<DeviceModelFormat.Field>();
        JSONObject value = root.getJSONObject("value");
        JSONArray jsonFields = value.getJSONArray("fields");
        for (int i = 0, size = jsonFields.length(); i < size ;i++) {
            DeviceModelFormat.Field field =
                createMsgFormatField(jsonFields.getJSONObject(i));
            fields.add(field);
        }

        return new 
            DeviceModelFormatImpl(urn, name, description, type, fields);
    }

    private static DeviceModelFormat.Field createMsgFormatField(
            JSONObject root) throws JSONException {
        String name = root.getString("name");
        String description = root.optString("description", "");
        String type = root.getString("type");
        boolean optional = root.getBoolean("optional");
        return new 
            DeviceModelFormatImpl.FieldImpl(name, description, type,
                                            optional);
    }
}
