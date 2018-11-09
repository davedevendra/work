/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package oracle.iot.client.enterprise;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@code Device} lists the characteristics of a device.
 *
 * @see EnterpriseClient#getActiveDevices(String)
 */
public abstract class Device {

    /**
     * Get the list of device models supported by this device.
     *
     * @return a list of device model URN.
     */
    public abstract List<String> getDeviceModels();

    /**
     * Get the value of the specified {@code field} or an empty String if the value is unknown.
     *
     * @param field the field to look for
     * @return the field value or an empty String.
     */
    public abstract String getValue(Field field);

    /**
     * Return whether the device is directly connected to the server or is connected through a gateway.
     *
     * @return {@code true} if the device is directly connected, {@code false} otherwise.
     */
    public abstract boolean isDirectlyConnected();

    /**
     * Get the device identifier.
     *
     * @return the device identifier.
     */
    public abstract String getId();

    /**
     * Return the device metadata. Might be empty.
     * @return a map of device metadata
     */
    public abstract Map<String,String> getMetadata();

    /**
     * Return the value of the metadata field {@code key}.
     * @param key the metadata key to return
     * @return value for metadata {@code key} or {@code null} if not found.
     */
    public abstract String getMetadata(String key);
    
    /**
     * Return the device location. 
     * @return the device location
     */
    public abstract Device.Location getLocation();

    /**
     * The Field enum defines the characteristics of a device.
     */
    public enum Field {

        /**
         * A unique value identifying the device
         */
        ID("id"),
        /**
         * A string representing the device unique identifier.
         */
        DEVICE_UID("deviceUID"),
        /**
         * The device type
         */
        TYPE("type"),
        /**
         * Optional free form text describing the device.
         */
        DESCRIPTION("description"),
        /**
         * Date and time the endpoint was created as a String in ISO-8601
         * time format.
         */
        CREATED("created"),
        /**
         * The device state.
         */
        STATE("state"),
        /**
         * The device name.
         */
        NAME("name"),
        /**
         * A string (generally an OUI) describing the manufacturer of the
         * device.
         */
        MANUFACTURER("manufacturer"),
        /**
         * A string representing the specific model of the device.
         */
        MODEL_NUMBER("modelNumber"),
        /**
         * A string (which should be unique across all devices of this
         * modelNumber / manufacturer) uniquely identifying the specific device.
         */
        SERIAL_NUMBER("serialNumber"),
        /**
         * A string representing the hardware revision.
         */
        HARDWARE_REVISION("hardwareRevision"),
        /**
         * A string representing the software revision.
         */
        SOFTWARE_REVISION("softwareRevision"),
        /**
         * A string representing the software version. This field is
         * available only for gateway devices.
         */
        SOFTWARE_VERSION("softwareVersion"),
        /**
         * {@code true} if the device is currently enabled.
         */
        ENABLED("enabled"),
        /**
         * Connection status of the device.
         */
        CONNECTIVITY_STATUS("connectivityStatus"),
        /**
         * Last time the device sent a message.
         */
        LAST_HEARD_TIME("lastHeardTime"),
        /**
         * A list of device models
         */
        DEVICE_MODELS("deviceModels"),
        /**
         * Additional user-defined key / value pairs describing the endpoint.
         */
        METADATA("metadata"),
        /**
         * The device location.
         */
        LOCATION("location"),

        /**
         * For endpoints which are not themselves directly connected to the IoT
         * network, this points to the endpoint which is handling
         * communications on their behalf.
         */
        DIRECTLY_CONNECTED_OWNER("directlyConnectedOwner");

        /**
         * Create a Field instance
         * @param alias the alias name used by server
         */
        Field(String alias) {
            this.alias = alias;
        }

        /**
         * The alias used by server REST API
         */
        final String alias;

        /**
         * Get the {@code Field} instance associated with the specified alias.
         * The String must match exactly, including case.
         * @param alias the alias to look for
         * @return the Field instance matching the specified alias
         */
        public static Field fromAlias(String alias) {
            for(Field f: Field.values()) {
                if (f.alias.equals(alias)) {
                    return f;
                }
            }
            return null;
        }

        /**
         * Get a new {@code Set} initialized with all possible {@code Field} values.
         * @return a new {@code Set} of all possible {@code Field} values.
         */
        static public Set<Field> all() {
            return EnumSet.allOf(Field.class);
        }

        /**
         * Get the alias of this {@code Field}. The alias is the name the
         * server uses for the field.
         * @return the alias of this {@code Field}.
         */
        public String alias() {
            return this.alias;
        }
    }
    
/**
 * The {@code Device.Location} describes the location of a device.
 */
    public static class Location {
        /**
         * The device's latitude in decimal degrees.
         */
        private final float latitude;
        
        /**
         * The device's longitude in decimal degrees.
         */
        private final float longitude;
        
        /**
         * The device's altitude in meters.
         */
        private final float altitude;
        
        /**
         * The device's uncertainty in meters.
         */
        private final float uncertainty;
        
        /**
         * Builds a new {@code Device.Location}. Any of the parameters should be 
         * {@code Float.NaN} if they are unknown.
         * @param latitude the device's latitude in degrees
         * @param longitude the device's longitude in degrees
         * @param altitude the device's altitude in meters
         * @param uncertainty the device's uncertainty in meters
         */
        public Location(float latitude, float longitude, float altitude, float uncertainty) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.uncertainty = uncertainty;
        }
        
        /**
         * Gets the latitude in decimal degrees. If latitude is unknown, 
         * {@code Float.NaN} is returned.
         * @return the latitude
         */
        public float getLatitude() {
            return this.latitude;
        }
        
        /**
         * Gets the longitude in decimal degrees. If longitude is unknown, 
         * {@code Float.NaN} is returned.
         * @return the longitude
         */
        public float getLongitude() {
            return this.longitude;
        }
        
        /**
         * Gets the altitude in meters. If altitude is unknown, 
         * {@code Float.NaN} is returned.
         * @return the altitude
         */
        public float getAltitude() {
            return this.altitude;
        }
        
        /**
         * Gets the uncertainty in meters. If uncertainty is unknown, 
         * {@code Float.NaN} is returned.
         * @return the uncertainty
         */
        public float getUncertainty() {
            return this.uncertainty;
        }
    }

    /**
     * Build a new {@code Device} instance.
     */
    protected Device() {}
}
