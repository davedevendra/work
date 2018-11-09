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

/**
 * DeviceModelActionImpl
 */
public class DeviceModelActionImpl extends DeviceModelAction {

    private final String name;
    private final String description;
    private final DeviceModelAttribute.Type argType;
    private final Number lowerBound;
    private final Number upperBound;
    private final String alias;

    public DeviceModelActionImpl(String name, String description,
                                 DeviceModelAttribute.Type type,
                                 Number lowerBound, Number upperBound,
                                 String alias) {
        this.name = name;
        this.description = description;
        this.argType = type;
        if (this.argType == DeviceModelAttribute.Type.INTEGER ||
                this.argType == DeviceModelAttribute.Type.NUMBER) {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
        } else {
            this.lowerBound = this.upperBound = null;
        }
        this.alias = alias;
    }

    public DeviceModelActionImpl(String name, String description,
                                 String alias) {
        this(name, description, null, null, null, alias);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public DeviceModelAttribute.Type getArgType() {
        return argType;
    }
    
    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Number getLowerBound() {
        return lowerBound;
    }

    @Override
    public Number getUpperBound() {
        return upperBound;
    }
    
    @Override
    public String getAlias() {
        return alias;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("name = ");
        b.append(name);
        b.append(", description = ");
        b.append(description);
        b.append(", type = ");
        b.append(argType);
        b.append(", lowerBound = ");
        b.append(lowerBound);
        b.append(", upperBound = ");
        b.append(upperBound);
        b.append(", alias = ");
        b.append(alias);
        return b.toString();
    }
}
