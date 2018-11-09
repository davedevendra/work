/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl;

import com.oracle.iot.client.DeviceModelAction;
import com.oracle.iot.client.DeviceModelAttribute;
import com.oracle.iot.client.DeviceModelFormat;

import oracle.iot.client.DeviceModel;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DeviceModelImpl
 */
public class DeviceModelImpl extends DeviceModel {

    private final String urn;
    private final String name;
    private final String description;
    private final Map<String, DeviceModelAttribute> deviceModelAttributes = new HashMap<String, DeviceModelAttribute>();
    private final Map<String, DeviceModelAction> deviceModelActions = new HashMap<String, DeviceModelAction>();
    private final Map<String, DeviceModelFormat> deviceModelFormats = new HashMap<String, DeviceModelFormat>();

    public DeviceModelImpl(String urn, String name, String description, 
                           DeviceModelAttribute[] deviceModelAttributes,
                           DeviceModelAction[] deviceModelActions) {
        this(urn, name, description, deviceModelAttributes,
             deviceModelActions, null);
    }

    public DeviceModelImpl(String urn, String name, String description, 
                           DeviceModelAttribute[] deviceModelAttributes,
                           DeviceModelAction[] deviceModelActions,
                           DeviceModelFormat[] deviceModelFormats) {
        this.urn = urn;
        this.name = name;
        this.description = description;

        if (deviceModelAttributes != null)
            for(int i = 0; i < deviceModelAttributes.length; i++) {
                String attName = deviceModelAttributes[i].getName();
                if (this.deviceModelAttributes.get(attName) == null)
                    this.deviceModelAttributes.put(attName, deviceModelAttributes[i]);
            }
        if (deviceModelActions != null)
            for(int i = 0; i < deviceModelActions.length; i++) {
                String actName = deviceModelActions[i].getName();
                if (this.deviceModelActions.get(actName) == null) 
                    this.deviceModelActions.put(actName, deviceModelActions[i]);
            }

        if (deviceModelFormats != null) 
            for(int i = 0; i < deviceModelFormats.length; i++) {
                String formatUrn = deviceModelFormats[i].getURN();
                if (this.deviceModelFormats.get(formatUrn) == null)
                    this.deviceModelFormats.put(formatUrn, deviceModelFormats[i]);
            }

    }

    @Override
    public String getURN() {
        return urn;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public Map<String,DeviceModelAttribute> getDeviceModelAttributes() {
        return deviceModelAttributes;
    }

    public Map<String, DeviceModelAction> getDeviceModelActions() {
        return deviceModelActions;
    }

    public Map<String, DeviceModelFormat> getDeviceModelFormats() {
        return deviceModelFormats;
    }

    @Override
    public String toString() {
        boolean firstItem = true;
        StringBuilder b = new StringBuilder("urn = ");
        b.append("\t");
        b.append(urn);
        b.append(",\n\tname = ");
        b.append(name);
        b.append(",\n\tdescription = ");
        b.append(description);
        b.append(",\n\tattributes = [");

        for (DeviceModelAttribute attribute: deviceModelAttributes.values()) {
            if (!firstItem) {
                b.append(",");
            } else {
                firstItem = false;
            }

            b.append("\n\t{");
            b.append(attribute);
            b.append("}");
        }

        if (!firstItem) {
            b.append("\n\t");
        }

        b.append("],\n\tactions = [");

        firstItem = true;
        for (DeviceModelAction action: deviceModelActions.values()) {
            if (!firstItem) {
                b.append(",");
            } else {
                firstItem = false;
            }

            b.append("\n\t{");
            b.append(action);
            b.append("}");
        }

        if (!firstItem) {
            b.append("\n\t");
        }

        b.append("],\n\tformats = [");

        firstItem = true;
        for (DeviceModelFormat format: deviceModelFormats.values()) {
            if (!firstItem) {
                b.append(",");
            } else {
                firstItem = false;
            }

            b.append("\n\t{");
            b.append(format);
            b.append("}");
        }

        if (!firstItem) {
            b.append("\n\t");
        }

        b.append("]");

        return b.toString();
    }
}
