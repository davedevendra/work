/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.message;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ResourceMessage extends Message class. It is used for sending sync information between
 * Resource Directory on Gateway and Resource Directory on server. This class is immutable.
 * {
 *   reportType: 'typeOfTheReport',
 *   reconciliationMark: 'hash for resource available  on client at moment of message generation'
 *   resources: [{
 *       path: 'pathToResource',
 *       resourceStatus: 'STATUS_OF_RESOURCE',
 *       description: 'resourceDescription',
 *       (other resource properties. TBD)
 *   },
 *   {
 *       path: 'pathToResource',
 *       resourceStatus: 'STATUS_OF_RESOURCE',
 *       description: 'resourceDescription',
 *       (other resource properties. TBD)
 *   },
 *   ...
 *   ]
 *  }
 */
public final class ResourceMessage extends Message {

    /**
     * Enumeration for choosing type of the resource directory message. Allowed
     * statuses are UPDATE, DELETE, RECONCILIATION.
     */
    public enum Type {
        UPDATE,
        DELETE,
        RECONCILIATION 
    }

    /**
     * List of resources described by the message
     */
    private ArrayList<Resource> resources;

    /**
     * Type of the message
     */
    private Type type;
    
    /**
     * hash for resource available  on client at moment of message generation
     */
    private String reconciliationMark;

    /**
     * Endpoint ID, for that the resources shall be updated
     */
    private String endpointName;

    /**
     * Message constructor takes message builder and set values to each field.
     * If other values are {@code null}, sets a default value.
     *
     * @param builder {@link ResourceMessage.Builder} containing all information for Resource Directory message.
     * @throws IllegalArgumentException if the operation is not set.
     */
    private ResourceMessage(Builder builder) {
        super(builder);
        
        if (builder.endpointName == null || builder.endpointName.length() == 0 ||
           (builder.resources.isEmpty() && (builder.type == Type.UPDATE))) {
            throw new IllegalArgumentException("ResourceMessage resources cannot be empty.");
        } else {
            this.endpointName = builder.endpointName;
            this.resources = new ArrayList<Resource>(builder.resources);
            this.type = builder.type;
            this.reconciliationMark = builder.reconciliationMark == null ? "" 
                                      : builder.reconciliationMark;
        }
    }

    /**
     * Calculates the MD5 hash value for a list of Strings.
     * The String list is being alphabetical ordered before the calculation
     * @param resStrings List os Strings
     * @return hash value
     */
    public static String getMD5ofList(List<String> resStrings) {

        // NOTE: This implementation must match the implementation on the server!
        Collection<String> resCol
                = new TreeSet<String>(Collator.getInstance());
        resCol.addAll(resStrings);

        String[] ordered = new String[resCol.size()];
        resCol.toArray(ordered);
        MessageDigest dg;
        try {
            dg = MessageDigest.getInstance("MD5");
            for (String s : ordered) {
                dg.update(s.getBytes("UTF-8"));
            }
            byte[] hashedBytes = dg.digest();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hashedBytes.length; ++i) {
                sb.append(Integer.toHexString((hashedBytes[i] & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            getLogger().log(Level.SEVERE, e.getMessage());
        } catch (UnsupportedEncodingException e) {
            getLogger().log(Level.SEVERE, e.getMessage());
        }
        return null;
    }
//    
//    @Override
//    public MessageBuilder getMessageBuilder() {
//        return new ResourceMessage.Builder().copy(this);
//    }
//    
    /**
     * Builder extends {@link MessageBuilder} class. {@link ResourceMessage} class is immutable.
     * A builder is required when creating new {@link ResourceMessage}.
     */
    public static final class Builder extends MessageBuilder<Builder> {

        /**
         * {@link List} of resources. Each Resource object is represented by a path which is the path to the resource.
         */
        private ArrayList<Resource> resources = new ArrayList<Resource>();
        private Type type = Type.UPDATE;
        private String endpointName = null;
        private String reconciliationMark = "";

        public Builder() {

        }

        /**
         * Set the type of the {@link ResourceMessage} to register.
         *
         * @return Builder with already set operation field
         */
        public final Builder delete() {
            this.type = Type.DELETE;
            if (!resources.isEmpty()) {
                resources.clear();
            }
            return self();
        }

        /**
         * Sets the type of the {@link Resource} to register and
         * adds the resource in the list
         *
         * @param ri the resource to register.
         * @return Builder with already set operation field
         */
        public final Builder register(Resource ri) {
            this.type = Type.UPDATE;
//            ri.setStatus(oracle.iot.client.device.Resource.Status.ADDED);
            this.resources.add(ri);
            return self();
        }

        /**
         * Set the type of the {@link Resource} to remove and
         * adds the resource in the list
         *
         * @param ri the resource to remove.
         * @return Builder with already set operation field
         */
        public final Builder remove(Resource ri) {
            this.type = Type.DELETE;
//            ri.setStatus(oracle.iot.client.device.Resource.Status.REMOVED);
            this.resources.add(ri);
            return self();
        }

        /**
         * Add a resource in the list
         *
         * @param ri the resource to add.
         * @return Builder with already set operation field
         */
        public final Builder add(Resource ri) {
            this.type = Type.UPDATE;
            this.resources.add(ri);
            return self();
        }

        /**
         * Adds all resources.
         *
         * @param resources Resources to be added.
         * @return Builder with already set resources field.
         */
        public final Builder resources(List<Resource> resources) {
            this.type = Type.UPDATE;
            this.resources.addAll(resources);
            return self();
        }

        /**
         * Set the reconciliation mark
         * @param rM - reconciliation mark
         * @return Builder with already set reconciliationMark field.
         */
        public final Builder reconciliationMark(String rM) {
            this.reconciliationMark = rM;
            return self();
        }

        /**
         * Set the endpointName this message is for.
         *
         * @param endpointName the endpoint Name for this message.
         * @return Builder with endpointName set.
         */
        public final Builder endpointName(String endpointName) {
            this.endpointName = endpointName;
            return self();
        }

        /**
         * Sets the message type to RECONCILIATION.
         *
         * @return  Builder with message type set to RDMessageType.RECONCILIATION.
         */
        public final Builder reconcile() {
            this.type = Type.RECONCILIATION;
            return self();
        }

        /**
         * Returns current instance of {@link ResourceMessage.Builder}.
         *
         * @return Instance of {@link ResourceMessage.Builder}
         */
        @Override
        protected Builder self() {
            return this;
        }

        /**
         * Creates new instance of {@link ResourceMessage} using values from {@link ResourceMessage.Builder}.
         *
         * @return Instance of {@link ResourceMessage}
         */
        @Override
        public ResourceMessage build() {
            return new ResourceMessage(this);
        }

        @Override
        public final Builder fromJson(JSONObject jsonObject) {
            super.fromJson(jsonObject);

            try {
                JSONObject payload = (JSONObject)jsonObject.opt("payload");
                JSONObject payloadValue = (JSONObject)payload.opt("value");

                String rt = payloadValue.getString("reportType");
                this.type = Type.valueOf(Type.class, rt);
                this.reconciliationMark =
                    payloadValue.optString("reconciliationMark", null);
                this.endpointName =
                    payloadValue.optString("endpointName", null);
                JSONArray resArray = (JSONArray)payloadValue.opt("resources");
                if (resArray != null) {
                    for (int i = 0, size = resArray.length(); i < size; i++) {
                        JSONObject res = (JSONObject)resArray.opt(i);
                        resources.add(
                            new Resource.Builder().fromJson(res).build());
                    }
                }
            } catch (JSONException e) {
                throw new MessageParsingException(e);
            }

            return self();
        }
    }


    /**
     * Exports data from {@link ResourceMessage} to {@link String} using JSON interpretation of the message.
     *
     * @return JSON interpretation of the message as {@link String}.
     */
    @Override
    public String toString() {
        return toJson().toString();
    }

    /**
     * Method to export the Resource Directory message to {@link JSONObject}.
     *
     * @return JSON interpretation of {@link ResourceMessage}
     */
    @Override
    public JSONObject toJson() {
        JSONObject jsonObject = Utils.commonFieldsToJson(this);
        JSONObject payload = new JSONObject();
        JSONObject value = new JSONObject();
        JSONArray resource = new JSONArray();

        try {
            jsonObject.put("type", Message.Type.RESOURCE.alias());
            payload.put("type", "JSON");
            value.put("reportType", this.type.name());

            if ((this.reconciliationMark != null) &&
                (this.reconciliationMark.length() != 0)) {
                value.put("reconciliationMark", this.reconciliationMark);
            }

            value.put("endpointName", this.endpointName);

            for (Resource r: this.resources) {
                resource.put(r.toJson());
            }

            value.put("resources", resource);
            payload.put("value", value);
            jsonObject.put("payload", payload);
        } catch (JSONException e) {
            throw new MessageParsingException(e);
        }
        
        return jsonObject;
    }

    @Override
    public Message.Type getType() {
        return Message.Type.RESOURCE;
    }

    /**
     * Returns List of resources described in the message. 
     *
     * @return List of resources, never {@code null}
     */
    public List<Resource> getResources() {
        return resources;
    }

    /**
     * Returns type of the message (Could be UPDATE or DELETE)
     * 
     * @return RDMessageType 
     */
    public Type getMessageType() {
        return type;
    }

    public String getReconciliationMark() {
        return reconciliationMark;
    }

    /**
     * Returns endpoint id for the message
     * @return endpoint id
     */
    public String getEndpointName() {
        return endpointName;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ResourceMessage that = (ResourceMessage) o;

        if (!this.type.equals(that.type)) return false;
        if (!resources.equals(that.resources)) return false;
        return this.reconciliationMark.equals(that.reconciliationMark);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + resources.hashCode();
        return result;
    }

    public static class ReportResponse {
        public enum ResponseStatus {
            OK,
            BAD_REPORT,
            RECONCILIATION 
        }
        private String endpointName;
        private ResponseStatus status;

        private ReportResponse(Builder builder) {

            if ((builder.endpointName == null) || (builder.endpointName.length() == 0)) {
                throw new IllegalArgumentException("Resource name, path cannot be null or empty.");
            }

            this.endpointName = builder.endpointName;

            if (builder.status != null) {
                this.status = builder.status;
            } else {
                this.status = ResponseStatus.OK;
            }
        }
        
        public JSONObject toJson() {
            JSONObject res = new JSONObject();

            try {
                res.put("endpointName", this.endpointName);

                if (this.status != null) {
                    res.put("status", this.status.name());
                } else {
                    res.put("status", ResponseStatus.OK.name());
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }

            return res;
        }
        
        public String getEndpointName() {
            return endpointName;
        }
        
        public ResponseStatus getStatus() {
            return status;
        }
        
        public String toString() {
            return toJson().toString();
        }
        
        /**
         * {@link Resource} class is immutable. A builder is required when
         * creating new {@link Resource}.
         */
        public static final class Builder {
            private String endpointName;

            private ResponseStatus status;
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

            public Builder status(ResponseStatus status) {
                this.status = status;
                return this;
            }

            public final Builder fromString(String str) {
                if ((str == null) || (str.length() == 0)) {
                    return this;
                }

                try {
                    return fromJson(new JSONObject(str));
                } catch (JSONException e) {
                    throw new MessageParsingException(e);
                }
            }
            
            public final Builder fromJson(JSONObject resObject) {
                String st = resObject.optString("status", null);

                if (st != null) {
                    this.status = st.equals("OK") ? ResponseStatus.OK : 
                        st.equals("BAD_REPORT") ? ResponseStatus.BAD_REPORT : 
                        ResponseStatus.RECONCILIATION;
                }

                // ReportResponse(Builder builder) constructor requires
                // non-null endpointName
                String endpointName = resObject.optString("endpointName", null);
                if (endpointName == null) {
                    throw new MessageParsingException(
                    "ResourceMessage fromJson: endpointName must not be null");
                }

                this.endpointName = endpointName;
                
                return this;
            }

            public ReportResponse build() {
                return new ReportResponse(this);
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
