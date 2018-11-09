/*
 * Copyright (c) 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.sample;

public class BeaconSensor {
    public enum Type {
        IBEACON("iBeacon"),
        EDDYSTONE("Eddystone"),
        NONE("None");

        final String alias;
        Type(String alias) {
            this.alias = alias;
        }

        public String alias() {
            return this.alias;
        }
    }
    // for generating rssi fluctuation as a sine wave
    private int x = 0;
    private final double minRssiRange = -119.0;
    private final double maxRssiRange = -0.1;
    private double lastPoint = minRssiRange * .30f;
    private final double amplitude = minRssiRange * .10f + 1f;
    private final int numSteps = 10;
    private int step = 0;
    private double nextPoint;
    
    private int xVolt = 0;
    private final double minVoltRange = 0.1;
    private final double maxVoltRange = 3000.0;
    private double lastVoltPoint = maxVoltRange;
    private final double amplitudeVolt = .10f;

    private int xTemp = 0;
    private final double minTempRange = 0.0;
    private final double maxTempRange = 2560.0;
    private double lastTempPoint = minTempRange * .30f;
    private final double amplitudeTemp = maxTempRange * .10f + 1f;
    
    private final int txPower = -67;
    private final String hardwareId;
    private final String major;
    private final String minor;
    private final String UUID;
    private String endpointId = "";
    private final Type type;
    private double incrementalRssi = 0;

    public BeaconSensor(String UUID, Type type) {
        final String[] parts = UUID.split(":");
        this.UUID = parts[0];
        if (parts.length > 2) {
            this.major = parts[1];
            this.minor = parts[2];
            this.hardwareId = this.UUID + ":" + this.major + ":" + this.minor;
        } else if (parts.length > 1) {
            this.major = parts[1];
            this.minor = "";
            this.hardwareId = this.UUID + ":" + this.major;
        } else {
            this.major = "";
            this.minor = "";
            this.hardwareId = this.UUID;
        }
        this.type = type;
    }

    public BeaconSensor(String UUID, String major, String minor, Type type) {
        this.UUID = UUID;
        this.major = major;
        this.minor = minor;
        this.hardwareId = this.UUID + ":" + major + ":" + minor;
        this.type = type;
    }

    /**
     * Get the current RSSI value.
     *
     * @return the RSSI value
     */
    public int getRSSI() {
        if (step == 0) {
            final double delta = amplitude * Math.sin(Math.toRadians(x));
            x += 14;
            double rssi = lastPoint + delta;

            if (rssi > maxRssiRange || rssi < minRssiRange)
                rssi = lastPoint - delta;

            nextPoint = rssi;
        }

        incrementalRssi = lastPoint + (nextPoint - lastPoint)/numSteps*(step + 1);

        if (step == numSteps - 1) {
            step = 0;
            lastPoint = nextPoint;
        } else {
            step++;
        }

        return (int)Math.round(incrementalRssi);
    }
    
    public int getTxPower() {
        return txPower;
    }

    public String getManufacturer() {
        return "Sample";
    }

    public String getModelNumber() {
        return "MN-" + hardwareId;
    }

    public String getSerialNumber() {
        return "SN-" + hardwareId;
    }
    
    public String getProtocol() {
        return "Bluetooth";
    }
    
    public String getDeviceClass() {
        return "Beacon";
    }
    
    public String getProtocolDeviceClass() {
        return "Bluetooth Beacon";
    }
    
    public String getProtocolDeviceId() {
        return "Bluetooth Device";
    }
    
    public String getMajor() {
        return major;
    }
    
    public String getMinor() {
        return minor;
    }
    
    public String getUUID() {
        return UUID;
    }

    public String getHardwareId() {
        return hardwareId;
    }
    
    public void setEndpointId(String eid) {
        endpointId = eid;
        x = Math.abs(endpointId.hashCode())%360;
        lastPoint = minRssiRange * (x + 1) / 360;
        
        if (type == Type.EDDYSTONE) {
            xTemp = Math.abs(endpointId.hashCode())%360;
            lastTempPoint = maxTempRange * (xTemp + 1) / 360;
            xVolt = Math.abs(endpointId.hashCode())%360;
        }
    }
    
    public Type getType() {
        return type;
    }
    
    public int getTemperature() {
        if (type != Type.EDDYSTONE) {
            return 0;
        }
        
        final double delta = amplitudeTemp * Math.sin(Math.toRadians(xTemp));
        xTemp += 14;
        double temp = lastTempPoint + delta;

        if (temp > maxTempRange || temp < minTempRange)
            temp = lastTempPoint - delta;

        lastTempPoint = temp;

        return (int)Math.round(temp);
    }

    public int getVoltage() {
        if (type != Type.EDDYSTONE) {
            return 0;
        }
        
        final double delta = Math.abs(amplitudeVolt * Math.sin(Math.toRadians(xVolt)));
        xVolt += 14;
        double volt = lastVoltPoint - delta;

        if (volt < minVoltRange)
            volt = minVoltRange;

        lastVoltPoint = volt;

        return (int)Math.round(volt);
    }
    
    public double getDistance() {
        if (incrementalRssi == 0) {
            return -1.0; // can't determine
        }
        double ratio = incrementalRssi * 1.0 / txPower;
        if (ratio < 1.0) {
            return Math.pow(ratio, 10);
        } else {
            double accuracy = (0.89976) * Math.pow(ratio, 7.7095) + 0.111;
            // 0.89976, 7.7095 and 0.111 are the three constants calculated when solving for a best fit curve to our measured data points.
            return accuracy;
        }
    }
}
