/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.sample.obd2;

class Obd2SimulationConfig {
    int minSpeed = 0;
    int maxSpeed = 255;
    int speedVariation = 1;

    final double MIN_ENGINE_RPM = 0;
    final double MAX_ENGINE_RPM = 16378.75;

    final double MIN_THROTTLE_POSITION = 0;
    double MAX_THROTTLE_POSITION = 100;

    final int MIN_ENGINE_COOLANT_TEMPERATURE = -40;
    final int MAX_ENGINE_COOLANT_TEMPERATURE = 215;

    final double FUEL_TANK_LEVEL_INPUT = 100;

    final double MIN_ENGINE_FUEL_RATE = 0;
    final double MAX_ENGINE_FUEL_RATE = 3276.75;

    final int SIMULATION_EVENT_INTERVAL = 1;
    final int SIMULATION_PERIOD = 500;

    final double ORIGIN_LATITUDE = 12.897532;
    final double ORIGIN_LONGITUDE = 77.658337;
    final double ORIGIN_ALTITUDE = 800;

    final double LATITUDE_DELTA = 0.000001;
    final double LONGITUDE_DELTA = 0.0000001;
    final double ALTITUDE_DELTA = 0;

    private static Obd2SimulationConfig instance = null;
    private Obd2SimulationConfig() {
    }

    public static Obd2SimulationConfig getInstance() {
        if(instance == null) {
            instance = new Obd2SimulationConfig();
        }
        return instance;
    }

}
