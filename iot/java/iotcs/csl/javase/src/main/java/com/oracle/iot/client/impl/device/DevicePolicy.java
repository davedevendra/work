/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.client.impl.device;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detailed information on a device policy.
 */
public final class DevicePolicy {

    public static String ALL_ATTRIBUTES() {
        return "*";
    }

    public static class Function {

        public Function(String id, Map<String,?> parameters) {
            this.id = id;
            if (parameters != null && !parameters.isEmpty()) {
                this.parameters = Collections.unmodifiableMap(new HashMap<String, Object>(parameters));
            } else {
                this.parameters = Collections.emptyMap();
            }
        }

        public String getId() {
            return id;
        }
        
        public Map<String,?> getParameters() {
            return parameters;
        }

        @Override public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || obj.getClass() != DevicePolicy.Function.class) {
                return false;
            }
            return this.id.equals(((DevicePolicy.Function)obj).id);
        }

        @Override
        public int hashCode() {
            return this.id.hashCode();
        }

        private final String id;
        private final Map<String,?> parameters;
    }

    /**
     * Get the policy id.
     * @return the policy id
     */
    public String getId() {
        return id;
    }

    /**
     * Get the target device model URN. 
     *
     * @return the URN of the target device model
     */
    public String getDeviceModelURN() {
        return deviceModelURN;
    }

    /**
     * Get the function pipeline of this policy for an attribute.
     * @return a read-only list of {@link DevicePolicy.Function}.
     */
    public List<Function> getPipeline(String attributeName) {
        return attributeName != null ? pipelines.get(attributeName) : pipelines.get(ALL_ATTRIBUTES());
    }

    /**
     * Get all the pipelines of this policy. The map key is
     * an attribute name, the value is the pipeline for that attribute.
     * @return the pipelines of this policy
     */
    public Map<String, List<Function>> getPipelines() {
        return pipelines;
    }

    /**
     * Get the free form description of the device policy
     *
     * @return the description of the model
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get the {@code enabled} state of the device policy
     * @return {@code true} if the policy is enabled, {@code false} otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get the date of last modification.
     * @return the date of last modification
     */
    public long getLastModified() {
        return lastModified;
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || obj.getClass() != DevicePolicy.class) {
            return false;
        }
        return this.id.equals(((DevicePolicy)obj).id);
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public String toString() {

        final StringBuilder stringBuilder = new StringBuilder()
                .append(getDeviceModelURN())
                .append("\n\"pipelines\":\n[");

        final Map<String, List<DevicePolicy.Function>> pipelines =
                getPipelines();

        if (pipelines != null && !pipelines.isEmpty()) {
            boolean first = true;
            for (Map.Entry<String, List<DevicePolicy.Function>> pipeline : pipelines.entrySet()) {
                dumpPipeline(stringBuilder, pipeline.getKey(), pipeline.getValue(), first);
                first = false;
            }
        }
        stringBuilder.append("\n]");
        return stringBuilder.toString();
    }

    public DevicePolicy(String id,
                        String deviceModelURN,
                        Map<String, List<Function>> pipelines,
                        String description,
                        long lastModified,
                        boolean enabled)
    {
        this.id = id;
        this.deviceModelURN = deviceModelURN;

        this.pipelines = new HashMap<String,List<Function>>();
        if (pipelines != null) {
            this.pipelines.putAll(pipelines);
        }
        
        this.description = description;
        this.lastModified = lastModified;
        this.enabled = enabled;
    }
    
    private final String id;
    private final String deviceModelURN;
    private final Map<String, List<Function>> pipelines;
    private final String description;
    private final long lastModified;
    private final boolean enabled;

    private static String dumpPipeline(final StringBuilder stringBuilder,
                                       final String attributeName,
                                       final List<DevicePolicy.Function> pipeline,
                                       boolean first) {

        if (!first) {
            stringBuilder.append(',');
        }

        stringBuilder
                .append("\n  {\n    \"attributeName\":\"")
                .append(attributeName)
                .append("\",")
                .append("\n    \"pipeline\":\n    [");

        if (pipeline != null && !pipeline.isEmpty()) {

            for (DevicePolicy.Function function : pipeline) {
                String id = function.getId();
                Map<String, ?> parameters = function.getParameters();
                DeviceFunction deviceFunction = DeviceFunction.getDeviceFunction(id);
                if (deviceFunction != null) {
                    stringBuilder.append("\n      ").append(deviceFunction.getDetails(parameters));
                } else {
                    stringBuilder.append("\n      `").append(id).append("` not found!");
                }
            }
            stringBuilder.append("\n");
        }

        stringBuilder.append("    ]\n  }");

        return stringBuilder.toString();
    }
}
