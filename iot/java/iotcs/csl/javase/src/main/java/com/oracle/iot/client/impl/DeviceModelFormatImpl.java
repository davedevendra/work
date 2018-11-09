/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl;

import java.util.List;

import com.oracle.iot.client.DeviceModelAttribute;
import com.oracle.iot.client.DeviceModelFormat;

/**
 * DeviceModelMessageFormatImpl
 */
public class DeviceModelFormatImpl extends DeviceModelFormat {

    private final String urn;
    private final String name;
    private final String description;
    private Type type;
    private final List<Field> fields;

    public DeviceModelFormatImpl(String urn, String name, String description,
            String type, List<Field> fields) {
        this.urn = urn;
        this.name = name;
        this.description = description;
        try {
            this.type = Type.valueOf(type);
        } catch (IllegalArgumentException e) {
            this.type = null;
        }
        this.fields = fields;
    }

    @Override
    public String getURN() { return urn; }

    @Override
    public String getName() {
        return name;
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
    public List<Field> getFields() {
        return fields;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("name = ");
        b.append(name);
        b.append(", description = ");
        b.append(description);
        b.append(", type = ");
        b.append(type);

        b.append(",\n fields = [");
        boolean firstItem = true;
        for (Field field: fields) {
            if (!firstItem) {
                b.append(",");
            } else {
                firstItem = false;
            }

            b.append("\n {");
            b.append(field);
            b.append("}");
        }

        if (!firstItem) {
            b.append("\n");
        }

        b.append(" ]");
        return b.toString();
    }

    /**
     * Describes a field of a message.
     */
    static class FieldImpl implements Field {
        private final String name;
        private final String description;
        private final DeviceModelAttribute.Type type;
        private final boolean optional;

        FieldImpl(String name, String description, String type,
                boolean optional) {
            this.name = name;
            this.description = description;
            this.type = DeviceModelAttribute.Type.valueOf(type);
            this.optional = optional;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public DeviceModelAttribute.Type getType() {
            return type;
        }

        @Override
        public boolean isOptional() {
            return optional;
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder("name = ");
            b.append(name);
            b.append(", description = ");
            b.append(description);
            b.append(", type = ");
            b.append(type);
            b.append(", optional = ");
            b.append(optional);
            return b.toString();
        }
    }
}
