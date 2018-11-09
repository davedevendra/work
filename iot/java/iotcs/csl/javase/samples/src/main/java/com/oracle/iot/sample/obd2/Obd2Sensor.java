/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.sample.obd2;


import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;


/**
 * A simulated OBD2 sensor for use in the samples. The sensor has
 * temperature, maximum temperature threshold, and minimum temperature
 * threshold attributes.
 */
class Obd2Sensor {

    Obd2SimulatedDataGenerator simulatedDataGenerator;

    private boolean isSimulated = true;
    private boolean isVehicleRunning = false;

    private boolean isMilIndicatorOn = false;

    int previousSpeed;
    int explicitSpeed = -1;

    int runTimeSinceStart = 0;
    int totalEngineRunTime = 0;


    double totalMilesTravelled = 0;
    double distanceTravelledSinceMalfunctionIndicator = 0;
    double distanceSinceDTCsCleared = 0;
    double fuelTankInput = 0;
    int minutesRunWithMalfunctionIndicator = 0;

    double locationX;
    double locationY;
    double altitude;

    double lattitudeDeltaMiles = 0;

    final Obd2SimulationConfig simulationInputs = Obd2SimulationConfig.getInstance();

    String milDtcCodes = "";

    public Obd2Sensor(String id , String cfgFile, final Obd2SimulationConfig simulationInputs) throws IOException {
        if (isSimulated) {
            previousSpeed = 0;
            simulatedDataGenerator = new Obd2SimulatedDataGenerator(cfgFile);
            locationX = simulationInputs.ORIGIN_LATITUDE;
            locationY = simulationInputs.ORIGIN_LONGITUDE;
            altitude = simulationInputs.ORIGIN_ALTITUDE;
            fuelTankInput = simulationInputs.FUEL_TANK_LEVEL_INPUT;
        }
    }

    synchronized Obd2Data getNextObd2Data() {
            Obd2Data obd2Data = new Obd2Data();
            // update malfunction related Data
            updateMalfunctionData(obd2Data);
            // update time related fields.
            updateTimingData(obd2Data);
            if ( isVehicleRunning()) {
                // update Distance related fields.
                updateDistanceRelatedData(obd2Data);
                // update speed related fields.
                updateSpeedData(obd2Data);
            }
            return obd2Data;
    }

    private void updateDistanceRelatedData(Obd2Data data) {
        double milesPerMin = (previousSpeed / 60.0);
        double deltaMiles= simulationInputs.SIMULATION_EVENT_INTERVAL * milesPerMin;
        lattitudeDeltaMiles += deltaMiles;
        data.isLocationChanged = true;
        lattitudeDeltaMiles=0;

        totalMilesTravelled += deltaMiles;
        if (isMilIndicatorOn) {
            distanceSinceDTCsCleared = 0;
            distanceTravelledSinceMalfunctionIndicator += deltaMiles;
            data.distanceTravelledSinceMalfunctionIndicator = (int)distanceTravelledSinceMalfunctionIndicator;
        } else {
            distanceTravelledSinceMalfunctionIndicator = 0;
            distanceSinceDTCsCleared += deltaMiles;
            data.distanceSinceDTCsCleared = (int)distanceSinceDTCsCleared;
        }
        fuelTankInput -= 0.01;
        data.fuelTankLevelInput = fuelTankInput;
        simulatedDataGenerator.changeLocation(data);
    }

    /**
     * Update the speed relatd data.
     */
    private void updateSpeedData(Obd2Data obd2Data) {
        int nextSpeed = getCurrentSpeed(previousSpeed);
        updateEventSpeed(obd2Data, nextSpeed, previousSpeed);
        previousSpeed = nextSpeed;
    }

    private void updateTimingData(Obd2Data data) {
        int interval = simulationInputs.SIMULATION_EVENT_INTERVAL;
        if (isVehicleRunning) {
            runTimeSinceStart += interval;
            data.runtimeSinceEngineStart = runTimeSinceStart;
            totalEngineRunTime += interval;
            data.isRunTimingChanged = true;
        }
    }

    private void updateMalfunctionData(Obd2Data data) {
        if (isMilIndicatorOn) {
            data.malfunctionIndicatorStatus = true;
            data.numberOfDTC = 1;
            minutesRunWithMalfunctionIndicator += simulationInputs.SIMULATION_EVENT_INTERVAL;
            data.minutesRunWithMalfunctionIndicator = minutesRunWithMalfunctionIndicator;
            data.diagnosticCodes = milDtcCodes;
        } else {
            data.malfunctionIndicatorStatus = false;
            data.numberOfDTC = 0;
            data.diagnosticCodes = "";
        }
        //update location
    }

    private int getCurrentSpeed(int previousSpeed) {
        int nextSpeed;

        if (explicitSpeed >= 0) {
            nextSpeed = explicitSpeed;
            explicitSpeed = -1;
        } else  if (previousSpeed < simulationInputs.minSpeed) {
            nextSpeed = previousSpeed + 10;
        } else if (simulationInputs.speedVariation > 0) {
            if (previousSpeed >= simulationInputs.maxSpeed) {
                nextSpeed = simulationInputs.minSpeed;
            } else {
                nextSpeed = previousSpeed + simulationInputs.speedVariation;
            }
        } else {
            nextSpeed = simulationInputs.minSpeed;
        }
        return nextSpeed;
    }

    private void updateEventSpeed(Obd2Data obd2Data, int currentSpeed, int previousSpeed) {
        if (currentSpeed != previousSpeed) {
            simulatedDataGenerator.updateSpeedRelatedData(obd2Data, currentSpeed);
        }
    }

    synchronized boolean isVehicleRunning() {
        return isVehicleRunning;
    }

    synchronized void start() {
        isVehicleRunning = true;
    }

    synchronized void stop() {
        isVehicleRunning = false;
        explicitSpeed = 0;
    }

    synchronized void brake(int percentage) {
        int newSpeed;
        if (percentage == 100) {
            explicitSpeed = 0;
        } else {
            newSpeed = (int) (previousSpeed * ( (100 - percentage) /100.0));
            setSpeed(newSpeed);
        }
    }

    synchronized void accelerate(int percentage) {
        int delta = (int) (previousSpeed * (percentage / 100.0));
        int newSpeed = previousSpeed + delta;
        setSpeed(newSpeed);
    }


    synchronized void setMilIndicatorOn(String milDTCCodes) {
        isMilIndicatorOn = true;
        if (milDTCCodes != null) {
            this.milDtcCodes = milDTCCodes;
        } else {
            this.milDtcCodes = "P0001";
        }
        distanceSinceDTCsCleared = 0;
    }

    synchronized void setMilIndicatorOff() {
        isMilIndicatorOn = false;
        this.milDtcCodes = "";
    }

    synchronized void setSpeed(int speed) {
        if (speed > 255) {
            speed = 255;
        } else if (speed < 0) {
            speed = 0;
        }
        explicitSpeed = speed;
        simulationInputs.minSpeed = speed - simulationInputs.speedVariation;
        simulationInputs.maxSpeed = speed + simulationInputs.speedVariation;
    }

    private class Obd2SimulatedDataGenerator {

        Properties properties;

        Obd2SimulatedDataGenerator(String cfgFileName) throws IOException {
            properties = new Properties();
            //parseSimulationConfigFile(cfgFileName);
        }

        int getInteger(String val) {
            return Integer.parseInt(val);
        }
        double getDouble(String val) {
            return Double.parseDouble(val);
        }

        private boolean isInteger(String s) {
            boolean isInteger = false;
            try {
                int val = Integer.parseInt(s);
                isInteger = true;
            } catch (Exception ex) {

            }
            return true;
        }

        private boolean isDouble(String s) {
            boolean isDouble = false;
            try {
                double val = Double.parseDouble(s);
                isDouble = true;
            } catch (Exception ex) {

            }
            return isDouble;
        }

        private class RangeVal {
            int min;
            int max;
            int step;
            RangeVal(int min, int max, int step) {
                this.min = min;
                this.max = max;
                this.step = step;
            }
        }

        private RangeVal getRangeVal(String val) {
            RangeVal  rangeVal = null;
            if (val != null && val.contains(",")) {
                String comps[] = val.split(",");
                if (comps != null && comps.length ==3) {
                    if (isInteger(comps[0]) && isInteger(comps[1]) && isInteger(comps[2])) {
                        int min = getInteger(comps[0]);
                        int max = getInteger(comps[1]);
                        int step = getInteger(comps[2]);
                        rangeVal = new RangeVal(min,max,step);
                    }
                }
            }
            return  rangeVal;
        }

        private int processProperty(String property) throws IOException {
            String propertyVal = properties.getProperty(property);
            if (propertyVal == null || !isInteger(propertyVal)) {
                throw new IOException("property" + property + " is not specified");
            }
            int val = getInteger(propertyVal);
            return val;
        }

        private RangeVal processRangeVal(String property) throws IOException {
            String propertyVal = properties.getProperty(property);
            RangeVal rangeVal = null;
            if (propertyVal == null ) {
                throw new IOException("property " + property + " is not specified");
            } else {
                rangeVal = getRangeVal(propertyVal);
                if (rangeVal == null) {
                    throw new IOException("property " + property + " range should be specified as min,max,step");
                }
            }
            return rangeVal;
        }

        private double processPropertyDouble(String property) throws IOException {
            String propertyVal = properties.getProperty(property);
            if (propertyVal == null || !isDouble(propertyVal)) {
                throw new IOException("property: " + property + " is not specified");
            }
            double val = getDouble(propertyVal);
            return val;
        }

        private void updateSpeedRelatedData(Obd2Data obd2Data, int vehicleSpeed) {
            obd2Data.changeSpeed(vehicleSpeed);
        }

        private void changeLocation(Obd2Data data) {
            locationX += simulationInputs.LATITUDE_DELTA;;
            locationY += simulationInputs.LONGITUDE_DELTA;
            altitude += simulationInputs.ALTITUDE_DELTA;
            data.ora_latitude = locationX;
            data.ora_longitude = locationY;
            data.ora_altitude = altitude;
            data.isLocationChanged = true;
        }

    }

}

