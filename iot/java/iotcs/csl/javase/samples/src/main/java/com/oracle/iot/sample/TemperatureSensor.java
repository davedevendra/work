/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.sample;

import java.util.Date;
import java.util.Random;

/**
 * A simulated temperature sensor for use in the samples. The sensor has
 * temperature, maximum temperature threshold, and minimum temperature
 * threshold attributes.
 */
public class TemperatureSensor {

    // Whether the sensor is powered on (true), or off (false)
    private boolean power;

    // The time (measured in EPOCH) at which the system was powered ON or reset
    private Date startTime;

    // The minimum value measured by the sensor since power ON or reset
    private double minTemp = Double.MAX_VALUE;

    // The minimum value measured by the sensor since power ON or reset
    private double maxTemp = Double.MIN_VALUE;

    // The minimum threshold is in the range [-20,0]
    private int minThreshold = 0;

    // The maximum threshold is in the range [65,80]
    private int maxThreshold = 70;

    // A unique identifier for this sensor
    private final String hardwareId;

    /**
     * Create a TemperatureSensor
     * @param id a unique identifier for this sensor
     */
    public TemperatureSensor(String id) {
        hardwareId = id;
        power = true;
        startTime = new Date();
    }

    /**
     * Set the power on or off.
     * @param on {@code true} to set power on, {@code false} to set power off
     */
    public void power(boolean on) {
        if (on) {
            if (power) {
                return;
            }

            power = true;
            startTime = new Date();
            reset();
            return;
        }

        power = false;
    }

    /**
     * Get whether or not the sensor is powered on.
     * @return {@code true}, if the sensor is powered on.
     */
    public boolean isPoweredOn() {
        return power;
    }

    /**
     * An action to reset the miniumum and maximum measured temperature to
     * the current value.
     */
    public void reset() {
        maxTemp = Double.MIN_VALUE;
        minTemp = Double.MAX_VALUE;
    }

    /**
     * Get the current temperature value.
     * @return the temperature value
     */
    public double getTemp() {

        final double amplitude = (maxThreshold + minThreshold)/2;
        final double delta = amplitude * Math.sin(Math.toRadians(angle));
        angle += 15;
        double temp = randomlyViolateTemperatureThreshold(amplitude + delta);
        if (temp < minTemp) {
            minTemp = temp;
        }
        if (maxTemp < temp) {
            maxTemp = temp;
        }
        return temp;
    }

    /**
     * Get the temperature units (scale)
     * @return the temperature units
     */
    public String getUnit() {
        return "\u00B0C";
    }

    /**
     * Get the minimum temperature since the last power on or reset.
     * @return the minimum temperature since the last power on or reset
     */
    public double getMinTemp() {
        return minTemp;
    }

    /**
     * Get the maximum temperature since the last power on or reset.
     * @return the maximum temperature since the last power on or reset
     */
    public double getMaxTemp() {
        return maxTemp;
    }

    /**
     * Get the minumum threshold value.
     * @return the minimum threshold
     */
    public int getMinThreshold() {
        return minThreshold;
    }

    /**
     * Set the minimum degrees Celsius threshold for alerts. The value is
     * clamped to the range [-20..0].
     * @param threshold a value between -20 and 0
     */
    public void setMinThreshold(int threshold) {
        if (threshold < -20) minThreshold = -20;
        else if (threshold > 0) minThreshold = 0;
        else minThreshold = threshold;
    }

    /**
     * Get the maximum threshold value.
     * @return the maximum threshold
     */
    public int getMaxThreshold() {
        return maxThreshold;
    }

    /**
     * Set the maximum degrees Celsius threshold for alerts. The value is
     * clamped to the range [65..80].
     * @param threshold a value between 65 and 80
     */
    public void setMaxThreshold(int threshold) {
        if (threshold < 65) maxThreshold = 65;
        else if (threshold > 80) maxThreshold = 100;
        else maxThreshold = threshold;
    }

    /**
     * Get the time at which the device was powered on or reset.
     * @return the device start time
     */
    public Date getStartTime() {
        return startTime;
    }

    /**
     * Get the manufacturer name, which can be used as part of the
     * device meta-data.
     * @return the manufacturer name
     */
    public String getManufacturer() {
        return "Sample";
    }

    /**
     * Get the model number, which can be used as part of the
     * device meta-data.
     * @return the model number
     */
    public String getModelNumber() {
        return "MN-" + hardwareId;
    }

    /**
     * Get the serial number, which can be used as part of the
     * device meta-data.
     * @return the serial number
     */
    public String getSerialNumber() {
        return "SN-" + hardwareId;
    }

    /**
     * Get the hardware id, which can be used as part of the
     * device meta-data.
     * @return the hardware id
     */
    public String getHardwareId() {
        return hardwareId;
    }

    //
    // Used in generating data values
    //
    private int angle = 0;

    // Used in determining whether or not to violate the threshold.
    final Random temperatureThresholdViolation = new Random();
    final double randomlyViolateTemperatureThreshold(double temperature) {
        // probability that 1 time out of 25, we exceed a threshold
        if (temperatureThresholdViolation.nextInt(25)==0) {

            // we know minThreshold < temperature <= maxThreshold,
            // so if 'temperature' is closer to min than max, violoate minThreshold.
            if ((temperature - minThreshold) < (maxThreshold - temperature)) {
                return Math.max(-20, minThreshold - 2d);
            } else {
                return Math.min(80, maxThreshold + 2d);
            }
        }
        return temperature;
    }

}
