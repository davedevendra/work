/*
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.sample.obd2;


import java.text.SimpleDateFormat;
import java.util.Date;

class Obd2Data {

    final Obd2SimulationConfig simulationInputs = Obd2SimulationConfig.getInstance();
    static double totalFuelEconomy = 0;
    static  int count = 0;

    // Speed related data
    int vehicleSpeed; // The current speed of the vehicle measured in miles/hour
    double engineRpm; // Engine RPM (rpm)
    double throttlePosition; //The absolute pedal position (not the relative or learned) pedal position. Usually above 0% at idle and less than 100% at full throttle

    int engineCoolantTemperature; // Engine Coolant Temperature (°F)
    double engineFuelRate; // Amount of fuel consumed by the engine per unit of time in gallons per hour.
    double averageFuelEconomy; // The average Fuel Economy of the Vehicle (MPG)
    boolean isSpeedChanged = false;

    // Timing data
    int runtimeSinceEngineStart; // Total engine run time for the life of vehicle (seconds)
    boolean isRunTimingChanged = false;

    // Malfunction Data
    boolean malfunctionIndicatorStatus = false; // Malfunction Indicator Lamp, also called Check Engine Lamp
    String diagnosticCodes; // Diagnostic Trouble Codes
    int minutesRunWithMalfunctionIndicator; // Accumulated minutes of engine run time while the malfunction indicator light is on
    int distanceTravelledSinceMalfunctionIndicator; //Distance travelled since MIL On (Miles)
    int distanceSinceDTCsCleared; // Distance accumulated since DTCs where cleared with a scan tool (Miles)
    int numberOfDTC;
    boolean isMalfunctionDataChanged = false;

    // Fuel Data
    double fuelTankLevelInput; // Fuel Tank Level Input. indicates the nominal fuel tank liquid fill capacity as a percent of maximum

    // Location Data
    double ora_latitude; // The decimal notation of latitude, e.g. -43.5723 [World Geodetic System 1984]
    double ora_longitude; // The decimal notation of longitude, e.g. -43.5723 [World Geodetic System 1984]
    double ora_altitude; // Altitude
    boolean isLocationChanged = false;

    boolean isInitialEvent = false;

    void changeSpeed(int speed) {
        this.vehicleSpeed = speed;
        double percentage =  speed / 255.0;

        double delta = simulationInputs.MAX_ENGINE_RPM - simulationInputs.MIN_ENGINE_RPM;
        engineRpm = delta * percentage + simulationInputs.MIN_ENGINE_RPM;

        delta = simulationInputs.MAX_THROTTLE_POSITION - simulationInputs.MIN_THROTTLE_POSITION;
        throttlePosition = delta * percentage + simulationInputs.MIN_THROTTLE_POSITION;

        delta = simulationInputs.MAX_ENGINE_COOLANT_TEMPERATURE - simulationInputs.MIN_ENGINE_COOLANT_TEMPERATURE;
        engineCoolantTemperature = (int) (delta * percentage + simulationInputs.MIN_ENGINE_COOLANT_TEMPERATURE);

        delta = simulationInputs.MAX_ENGINE_FUEL_RATE - simulationInputs.MIN_ENGINE_FUEL_RATE;
        engineFuelRate = delta * percentage + simulationInputs.MIN_ENGINE_FUEL_RATE;

        double instantFuelEconomy;
        if (engineFuelRate > 0) {
            instantFuelEconomy = (speed / engineFuelRate);
        } else {
            instantFuelEconomy = 0;
        }
        totalFuelEconomy += instantFuelEconomy;
        count++;
        averageFuelEconomy = (totalFuelEconomy / count);

        isSpeedChanged = true;
    }

    String formatDouble(double value, int width , int decimal, boolean percentage) {
        String formatStr = "%" + width + "." + decimal + "f" ;
        String formatVal = String.format(formatStr, value) + (percentage ? "%" : "");
        return  formatVal;
    }

    String formatInt(int width, int val) {
        String formatVal = String.format("%" + width + "d", val);
        return  formatVal;
    }

    /** Display OBD2 Event */
    void displayOb2DataEvent(String endpointId) {
        SimpleDateFormat printFormat = new SimpleDateFormat("HH:mm:ss");
        String time = printFormat.format(new Date());
        StringBuilder msg = new StringBuilder(time);
        msg.append(": ");

        if (isInitialEvent || isSpeedChanged) {
            msg.append(" Speed:");
            msg.append(String.format("%3d", vehicleSpeed));
            msg.append(" Throttle: ");
            msg.append(formatDouble(throttlePosition, 5, 2,true));
            msg.append(" RPM: ");
            msg.append(formatDouble(engineRpm, 7,2, false));
            msg.append(" CoolantTemp:");
            msg.append(formatInt(3, engineCoolantTemperature));
            msg.append(" FuelTank: ");
            msg.append(formatDouble(fuelTankLevelInput,3,2,true));
        }

        if (isInitialEvent || isRunTimingChanged) {
            msg.append(" Runtime: " + formatInt(3, runtimeSinceEngineStart));
        }

        if (malfunctionIndicatorStatus) {
            msg.append(" MIL?: yes ");
            if (malfunctionIndicatorStatus) {
                msg.append(" DTC:" + diagnosticCodes);
            }
        } else {
            msg.append(" Distance: " + formatDouble(distanceSinceDTCsCleared,7,2,false));
        }

        if (isInitialEvent || isLocationChanged) {
            String location = String.format("%f, %f %f", ora_latitude, ora_longitude, ora_altitude);
            msg.append(" Location : " + location);
        }
        System.out.println(msg.toString());
    }

}


