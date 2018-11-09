/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl;

import com.oracle.iot.client.DeviceModelAttribute;

/**
 * DeviceModelAttributeImpl
 */
public class DeviceModelAttributeImpl<T> extends DeviceModelAttribute<T> {

    private final String name;
    private final String urn;
    private final String description;
    private final Type type;
    private final Number lowerBound;
    private final Number upperBound;
    private final Access access;
    private final String alias;
    private final T defaultValue;

    public DeviceModelAttributeImpl(String urn, String name, String description, Type type,
                             Number lowerBound, Number upperBound, Access access, String alias) {
        this(urn, name, description, type, lowerBound, upperBound, access, alias, null);
    }

    public DeviceModelAttributeImpl(String urn, String name, String description, Type type,
                                    Number lowerBound, Number upperBound, Access access, String alias,
                                    T defaultValue) {
        this.urn = urn;
        this.name = name;
        this.description = description;
        this.type = type;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.access = access;
        this.alias = alias;
        this.defaultValue = defaultValue;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getModel() {
        return urn;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Type getType() {
        return type;
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
    public Access getAccess() {
        return access;
    }

    @Override
    public String getAlias() {
        return alias;
    }

    @Override
    public T getDefaultValue() { return defaultValue; }

    @Override
    public String toString() {
        return "urn = " + urn +
                ", name = " + name +
                ", description = " + description +
                ", type = " + type +
                ", lowerBound = " + lowerBound +
                ", upperBound = " + upperBound +
                ", access = " + access +
                ", alias = " + alias +
                ", defaultValue = " + defaultValue ;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof DeviceModelAttribute)) return false;
        DeviceModelAttribute other = (DeviceModelAttribute) obj;

        if (getModel() != null ? !getModel().equals(other.getModel()) : other.getModel() != null) return false;
        return getName() != null ? getName().equals(other.getName()) : other.getName() == null;

    }

    @Override
    public int hashCode() {
        int hash = 37;
        hash = 37 * hash + (getModel() != null ? getModel().hashCode() : 0);
        hash = 37 * hash + (getName()  != null ? getName().hashCode()  : 0);
        return hash;
    }
}
