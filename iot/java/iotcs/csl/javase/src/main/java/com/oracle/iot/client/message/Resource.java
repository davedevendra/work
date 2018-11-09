/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.message;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * This class represents a resource for an endpoint. 
 */
public class Resource {
    /**
     * Method supported by a Resource
     */
    public enum Method {
        GET(0x1),
        POST(0x2),
        PUT(0x4),
        DELETE(0x8),
        PATCH(0x10);

        private final int value;

        Method(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }

        /**
         * Returns the Method value for the String
         *
         * @param name String shall be "GET" or "POST"
         *
         * @return Method
         */
        public static Method createValue(String name) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        /**
         * Returns a list of the Method values for the bit string
         *
         * @param bitMask bit string
         *
         * @return list of the Method values
         */
        public static List<Method> createValue(int bitMask) {
            List<Method> methods = new ArrayList<Method>();

            for (Method m : Method.values()) {
                if ((bitMask & m.value()) != 0) {
                    methods.add(m);
                }
            }

            return methods;
        }

        /**
         * Returns a bit mask for a list of the Method values
         *
         * @param methods list of the Method values
         *
         * @return int bit mask
         */
        public static int getBitMask(List<Method> methods) {
            int result = 0;

            for (Method m : methods) {
                result = result + m.value();
            }

            return result;
        }

        /**
         * Returns String for a list of the Method values
         *
         * @param methods list of the Method values.
         * @return String a comma-delimited string of method names or an empty string if methods is empty or
         *                {@code null}.
         */
        public static String listToString(List<Method> methods) {
            StringBuilder result = new StringBuilder();

            if ((methods != null) && !methods.isEmpty()) {
                for (Method method : methods) {
                    result.append(result.length() == 0 ? "" : ",").append(method.name());
                }
            }

            return result.toString();
        }

        /**
         * Converts string of the method names into a list of the Method values.  Returns an empty list methods is
         * {@code null} or empty.
         *
         * @param methods string of methods
         *
         * @return list of the Method values
         */
        public static List<Method> stringToList(String methods) {
            List<Method> result = new ArrayList<Method>();

            if ((methods != null) && (methods.length() != 0)) {
                final StringTokenizer methodsParser = new StringTokenizer(methods, ",");

                while (methodsParser.hasMoreTokens()) {
                    Method method = Method.createValue(methodsParser.nextToken());

                    if (method != null) {
                        result.add(method);
                    }
                }
            }

            return result;
        }
    }

    /**
     * Enumeration for choosing status of the resource directory item.  Allowed statuses are ADDED, REMOVED.
     */
    public enum Status {
        ADDED,
        REMOVED
    }

    /**
     * The endpoint name or ID this resource is for.
     */
    private String endpointName;

    /**
     * Resource name. This name should describe the resource in free form.
     */
    private String name;

    /**
     * Resource status.
     */
    private Status status;

    /**
     * Path of the resource
     */
    private String path;

    /**
     * Methods supported by the resource
     */
    private List<Method> methods;

    private Resource(Builder builder) {

        if ((builder.name == null) || (builder.name.length() == 0) ||
                (builder.path == null) || (builder.path.length() == 0)) {
            throw new IllegalArgumentException("Resource name, path cannot be null or empty.");
        }

        this.endpointName = builder.endpointName;
        this.name = builder.name;
        this.path = builder.path;

        if (builder.status != null) {
            this.status = builder.status;
        } else {
            this.status = Status.ADDED;
        }

        if (this.status != Status.REMOVED) {
            if ((builder.methods == null) || builder.methods.isEmpty()) {
                throw new IllegalArgumentException("Resource methods cannot be null or empty.");
            } else {
                this.methods = builder.methods;
            }
        }
    }

    /**
     * {@link Resource} class is immutable. A builder is required when
     * creating new {@link Resource}.
     */
    public static final class Builder {
        /**
         * Resource's endpoint name or ID.
         * RD TODO: Is endpointName needed?
         */
        private String endpointName;

        /**
         * URI at which the resource can be found.  This must be unique for the endpoint.
         */
        private String path;

        /**
         * Resource name or description in free form.
         * It could be used for resource lookup.
         */
        private String name;

        /**
         * Resource status. Defines what action shall be done with it.
         * (ADDED, REMOVED)
         */
        private Resource.Status status;

        private List<Method> methods;

        /**
         * Constructor - does nothing.
         */
        public Builder() {
        }

        /**
         * Sets the name or ID of the resource's endpoint.
         *
         * @param endpointName name or ID of the resource's endpoint.
         * @return Builder with the endpointName added.
         */
        public Builder endpointName(String endpointName) {
            this.endpointName = endpointName;
            return this;
        }

        /**
         * Sets the path of the resource.  path must be unique for all resources within an endpoint.
         *
         * @param path Uri of the resource.  Must be unique and must not be {@code null}.
         * @return Builder with already set path field.
         */
        public Builder path(String path) {
            this.path = path;
            return this;
        }

        /**
         * Sets the name of the resource.
         *
         * @param name name of the resource.
         * @return Builder with added name
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the status value for the resource.  The default is Resource.Status.ADDED if no status or a
         * {@code null} status is specified.
         *
         * @param status the status of the resource.
         * @return a Builder with the specified status added.
         */
        public Builder status(Resource.Status status) {
            this.status = status;
            return this;
        }

        /**
         * Sets the method value for the resource.  The default is an empty list if no method or a {@code null}
         * method is specified.
         *
         * @param method status of the resource
         * @return a Builder with the specified method added.
         */
        public Builder method(Resource.Method method) {
            if (this.methods == null) {
                this.methods = new ArrayList<Method>();
            }

            this.methods.add(method);
            return this;
        }

        /**
         * Sets the method values for the resource.  The default is an empty list if no method or a {@code null}
         * methods is specified.
         *
         * @param methods a {@link List} of methods supported by the resource.
         * @return a Builder with the specified methods added.
         */
        public Builder methods(List<Method> methods) {
            if (this.methods == null) {
                this.methods = new ArrayList<Method>();
            }

            if (methods != null) {
                for (Resource.Method method : methods) {
                    this.methods.add(method);
                }
            }

            return this;
        }

        public final Builder fromJson(JSONObject resObject) {
            String st = resObject.optString("status", null);
            if (st != null) {
                this.status = st.equals("ADDED") ?
                    Resource.Status.ADDED : Resource.Status.REMOVED;
            }

            this.methods = Resource.Method.stringToList(
                resObject.optString("methods", null));
            this.endpointName = resObject.optString("endpointName", null);
            this.name = resObject.optString("name", null);
            this.path = resObject.optString("path", null);

            return this;
        }

        /**
         * Builds (creates) new instance of {@link Resource}.
         *
         * @return new instance of {@link Resource}
         */
        public Resource build() {
            return new Resource(this);
        }

    }

    public JSONObject toJson() {
        try {
            JSONObject res = new JSONObject();

            if (this.endpointName != null && this.endpointName.length() != 0) {
                res.put("endpointName", this.endpointName);
            }

            res.put("name", this.name);
            res.put("path", this.path);

            if (this.status != null) {
                res.put("status", this.status.name());
            } else {
                res.put("status", Status.ADDED.name());
            }

            if (this.methods != null) {
                res.put("methods", Method.listToString(this.methods));
            }

            return res;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @Override public String toString() { return toJson().toString(); }

    public final void setStatus(Resource.Status st) {
        this.status = st;
    }

    public final String getEndpointName() {
        return this.endpointName;
    }

    public final String getName() {
        return this.name;
    }

    public final String getPath() {
        return this.path;
    }

    public final Resource.Status getStatus() {
        return this.status;
    }

    public final List<Method> getMethods() {
        return this.methods;
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Resource that = (Resource) o;

        if (!this.name.equals(that.name)) {
            return false;
        }
        if (!this.path.equals(that.path)) {
            return false;
        }
        if (!this.status.equals(that.status)) {
            return false;
        }

        return !((this.methods != null) && !this.methods.equals(that.methods));

    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + path.hashCode();

        if (this.status != null) {
            result = 31 * result + status.hashCode();
        }

        if (this.methods != null) {
            result = 31 * result + methods.hashCode();
        }

        return result;
    }
}
