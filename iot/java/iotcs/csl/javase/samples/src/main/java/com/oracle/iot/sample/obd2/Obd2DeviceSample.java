/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 */

package com.oracle.iot.sample.obd2;

import oracle.iot.client.DeviceModel;
import oracle.iot.client.device.Alert;
import oracle.iot.client.device.DirectlyConnectedDevice;
import oracle.iot.client.device.VirtualDevice;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;

/*
 * This sample is a gateway that presents OBD2 dongles as virtual
 * devices to the IoT server.
 *
 * Note that the code is Java SE 1.5 compatible.
 */
public class Obd2DeviceSample {

    private static final String OBD2_SENSOR_MODEL_URN = "urn:com:oracle:iot:device:obd2";
    private static final String OBD2_SENSOR_SIMULATOR_MODEL_URN = "urn:com:oracle:iot:device:obd2simulator";

    private static final String VEHICLE_SPEED_ATTRIBUTE = "ora_obd2_vehicle_speed";
    private static final String ENGINE_RPM_ATTRIBUTE = "ora_obd2_engine_rpm";
    private static final String THROTTLE_POSITION_ATTRIBUTE = "ora_obd2_throttle_position";
    private static final String ENGINE_COOLANT_TEMPERATURE = "ora_obd2_engine_coolant_temperature";
    private static final String RUNTIME_SINCE_ENGINE_START_ATTRIBUTE = "ora_obd2_runtime_since_engine_start";
    private static final String NUMBER_OF_DTCS = "ora_obd2_number_of_dtcs";
    private static final String CLEAR_DISTANCE = "ora_obd2_distance_since_dtcs_cleared";
    private static final String LATITUDE_ATTRIBUTE = "ora_latitude";
    private static final String LONGITUDE_ATTRIBUTE = "ora_longitude";
    private static final String ALTITUDE_ATTRIBUTE = "ora_altitude";

    private static final String VEHICLE_STARTED_ALERT_URN = OBD2_SENSOR_MODEL_URN + ":vehicle_started";
    private static final String VEHICLE_STOPPED_ALERT_URN = OBD2_SENSOR_MODEL_URN + ":vehicle_stopped";

    private static final String SPEED_ATTRIBUTE = "ora_obd2_vehicle_speed";

    private static final String VEHICLE_START_ACTION = "start";
    private static final String VEHICLE_STOP_ACTION = "stop";
    private static final String VEHICLE_BRAKE_ACTION = "brake";
    private static final String VEHICLE_ACCEL_ACTION = "accelerate";
    private static final String VEHICLE_SET_SPEED_ACTION = "setSpeed";

    private static final String VEHICLE_MIL_ON_ACTION = "setMalfunctionIndicatorOn";
    private static final String VEHICLE_MIL_OFF_ACTION = "setMalfunctionIndicatorOff";

    private final boolean isSimulated = true;

    private final DirectlyConnectedDevice obdDevice;
    final DeviceModel   obd2DeviceModel, obd2SimulatorDeviceModel;
    final VirtualDevice virtualObd2SimulatorDevice, virtualObd2Device;
    final Obd2EventHandler obd2EventHandler;
    final Obd2Sensor obd2Sensor;
    final Obd2SimulationConfig simulationInputs;

    //Obd2SimulationCommandListener simulationCommandListener;

    String assetFile;
    String assetFilePassword;
    String simulationConfigFile;

    Obd2DeviceSample(String[] args) throws GeneralSecurityException, IOException {
            //initializeDevices(args);
        processCommandLineArgs(args);

        // Initialize the device client
        obdDevice = new DirectlyConnectedDevice(assetFile, assetFilePassword);

        // Activate the device
        if (!obdDevice.isActivated()) {
            display("\nActivating with " + OBD2_SENSOR_MODEL_URN + " ...");
            obdDevice.activate(OBD2_SENSOR_MODEL_URN, OBD2_SENSOR_SIMULATOR_MODEL_URN);
        }

        // Create the virtual devices implementing the device models
        obd2DeviceModel = obdDevice.getDeviceModel(OBD2_SENSOR_MODEL_URN);
        obd2SimulatorDeviceModel = obdDevice.getDeviceModel(OBD2_SENSOR_SIMULATOR_MODEL_URN);

        virtualObd2SimulatorDevice = obdDevice.createVirtualDevice( obdDevice.getEndpointId(), obd2SimulatorDeviceModel);
        virtualObd2Device = obdDevice.createVirtualDevice( obdDevice.getEndpointId(), obd2DeviceModel);

        String endpointId = obdDevice.getEndpointId();
        System.out.println(endpointId);

        simulationInputs = Obd2SimulationConfig.getInstance();
        String sensorId = obdDevice.getEndpointId() + "_obd2";
        obd2Sensor = new Obd2Sensor(sensorId, simulationConfigFile, simulationInputs);
        obd2EventHandler = new Obd2EventHandler(virtualObd2Device, sensorId);

        if (isSimulated) {
            obd2EventHandler.setupSimulationEventHandlers(obd2Sensor);
        }
    }

    private void  mainLoop() throws InterruptedException {

        obd2Sensor.start();
        // Keep getting messages from OBD dongle and post to IoT CS.
        for (int simulationTime = 0; simulationTime <= simulationInputs.SIMULATION_PERIOD;
            simulationTime += simulationInputs.SIMULATION_EVENT_INTERVAL) {
            processNextObdIteration();
            // Sleep for simulation interval.
            Thread.sleep(simulationInputs.SIMULATION_EVENT_INTERVAL *1000);
        }
    }

    void processNextObdIteration() {
        Obd2Data nextObd2Data = obd2Sensor.getNextObd2Data();
        obd2EventHandler.processOb2Event(nextObd2Data);
    }

    private void processCommandLineArgs(String[] args) {
        if (args.length < 2) {
            showUsage();
            System.exit(1);
        } else{
            assetFile = args[0];
            assetFilePassword = args[1];
        }
    }

    private void closeDevices() {
        if (obdDevice != null) {
            try {
                obdDevice.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public static void main(String[] args) {
        Obd2DeviceSample deviceSample = null;
        try {
            if (args.length < 2) {
                display("\nIncorrect number of arguments.");
                throw new IllegalArgumentException("");
            }

            deviceSample = new Obd2DeviceSample(args);
            // Process the OBD2 device messages to the cloud services.
            deviceSample.mainLoop();

        } catch (Exception e) {
            displayException(e);
        } finally {
            // Dispose of the device client
            if (deviceSample != null) {
                deviceSample.closeDevices();
            }
        }
    }

    private static void showUsage() {
        Class<?> thisClass = new Object() { }.getClass().getEnclosingClass();
        display("Usage: \n"
                + "java " + thisClass.getName()
                + " <trusted assets file> <trusted assets password>\n");
    }

    private static void display(String s) {
        System.out.println(s);
    }
    private static void displayException(Throwable e) {
        StringBuffer sb = new StringBuffer(e.getMessage() == null ?
                e.getClass().getName() : e.getMessage());
        if (e.getCause() != null) {
            sb.append(".\n\tCaused by: ");
            sb.append(e.getCause());
        }
        System.out.println('\n' + sb.toString() + '\n');
        showUsage();
    }

    private  class Obd2EventHandler {
        int previousSpeed = 0;
        boolean previousMalfunctionStatus = false;

        VirtualDevice obd2VirtualDevice;
        String vehicleId;


        Obd2EventHandler(VirtualDevice obd2VirtualDevice, String vehicleId) {
            this.obd2VirtualDevice = obd2VirtualDevice;
            this.vehicleId = vehicleId;
        }

        /** Process OBD2 Events */
        void processOb2Event(Obd2Data obd2Data) {
              handleAlertConditions(obd2Data);
              sendObd2DataEvent(obd2Data);
              previousSpeed = obd2Data.vehicleSpeed;
              previousMalfunctionStatus = obd2Data.malfunctionIndicatorStatus;
        }

        private void handleAlertConditions(Obd2Data obd2Data) {
            int currentSpeed = obd2Data.vehicleSpeed;
            if (previousSpeed == 0 && currentSpeed > 0) {
                raiseVehicleStartedAlertEvent(currentSpeed);
            } else if (previousSpeed > 0 && currentSpeed == 0) {
                raiseVehicleStoppedAlertEvent();
            }
        }
        void raiseVehicleStartedAlertEvent(int speed) {
            Alert vehicleStartedAlertEvent = obd2VirtualDevice.createAlert(VEHICLE_STARTED_ALERT_URN);
            vehicleStartedAlertEvent.set(SPEED_ATTRIBUTE, speed);
            vehicleStartedAlertEvent.raise();
            System.out.println("! ALERT: Vehicle Started Event. Speed: " + speed);
        }

        void raiseVehicleStoppedAlertEvent() {
            Alert vehicleStoppedAlertEvent = obd2VirtualDevice.createAlert(VEHICLE_STOPPED_ALERT_URN);
            vehicleStoppedAlertEvent.raise();
            System.out.println("! ALERT: Vehicle Stopped Event.");
        }

        /* Fill the OBD2 sensor data and send to CS */
        private void sendObd2DataEvent(Obd2Data obd2Data) {
            // Update speed related events only if speed has changed.
            obd2VirtualDevice.update().set(VEHICLE_SPEED_ATTRIBUTE, obd2Data.vehicleSpeed)
            .set(ENGINE_RPM_ATTRIBUTE, obd2Data.engineRpm)
            .set(THROTTLE_POSITION_ATTRIBUTE, obd2Data.throttlePosition)
            .set(ENGINE_COOLANT_TEMPERATURE, obd2Data.engineCoolantTemperature)
            .set(RUNTIME_SINCE_ENGINE_START_ATTRIBUTE, obd2Data.runtimeSinceEngineStart)
            .set(NUMBER_OF_DTCS, obd2Data.numberOfDTC);
            obd2VirtualDevice.set(CLEAR_DISTANCE, obd2Data.distanceSinceDTCsCleared)
            .set(LATITUDE_ATTRIBUTE, obd2Data.ora_latitude)
            .set(LONGITUDE_ATTRIBUTE, obd2Data.ora_longitude)
            .set(ALTITUDE_ATTRIBUTE, obd2Data.ora_altitude);
            obd2VirtualDevice.finish();

            obd2Data.displayOb2DataEvent(virtualObd2Device.getEndpointId());
        }

        private void setupSimulationEventHandlers(final Obd2Sensor sensor) {

            virtualObd2SimulatorDevice.setCallable( VEHICLE_START_ACTION,
                    new VirtualDevice.Callable<Void>() {
                        @Override
                        public void call(VirtualDevice virtualDevice, Void val) {
                            display(new Date().toString() + " : " +
                                    virtualDevice.getEndpointId() +
                                    " : On Call : " + VEHICLE_START_ACTION);

                            sensor.start();
                        }
                    });

            virtualObd2SimulatorDevice.setCallable( VEHICLE_STOP_ACTION,
                    new VirtualDevice.Callable<Void>() {
                        @Override
                        public void call(VirtualDevice virtualDevice, Void val) {
                            display(new Date().toString() + " : " +
                                    virtualDevice.getEndpointId() +
                                    " : On Call : " + VEHICLE_STOP_ACTION);

                            sensor.stop();
                        }
                    });

            virtualObd2SimulatorDevice.setCallable( VEHICLE_MIL_ON_ACTION,
                    new VirtualDevice.Callable<String>() {
                        @Override
                        public void call(VirtualDevice virtualDevice, String dtcCodes) {
                            display(new Date().toString() + " : " +
                                    virtualDevice.getEndpointId() +
                                    " : On Call : " + VEHICLE_MIL_ON_ACTION + " DTC Codes: " + dtcCodes );
                            sensor.setMilIndicatorOn(dtcCodes);
                        }
                    });

            virtualObd2SimulatorDevice.setCallable( VEHICLE_MIL_OFF_ACTION,
                    new VirtualDevice.Callable<Boolean>() {
                        @Override
                        public void call(VirtualDevice virtualDevice, Boolean powerOn) {
                            display(new Date().toString() + " : " +
                                    virtualDevice.getEndpointId() +
                                    " : On Call : " + VEHICLE_MIL_OFF_ACTION);
                            sensor.setMilIndicatorOff();
                        }
                    });

            virtualObd2SimulatorDevice.setCallable( VEHICLE_SET_SPEED_ACTION,
                    new VirtualDevice.Callable<Integer>() {
                        @Override
                        public void call(VirtualDevice virtualDevice, Integer speed) {
                            display(new Date().toString() + " : " +
                                    virtualDevice.getEndpointId() +
                                    " : On Call : " +  VEHICLE_SET_SPEED_ACTION +  " Speed: " + speed );
                            sensor.setSpeed(speed);
                        }
                    });

            virtualObd2SimulatorDevice.setCallable( VEHICLE_BRAKE_ACTION,
                    new VirtualDevice.Callable<Integer>() {
                        @Override
                        public void call(VirtualDevice virtualDevice, Integer percentage) {
                            display(new Date().toString() + " : " +
                                    virtualDevice.getEndpointId() +
                                    " : On Call : " + VEHICLE_BRAKE_ACTION  + " percentage:" + percentage);
                            sensor.brake(percentage);
                        }
                    });

            virtualObd2SimulatorDevice.setCallable( VEHICLE_ACCEL_ACTION,
                    new VirtualDevice.Callable<Integer>() {
                        @Override
                        public void call(VirtualDevice virtualDevice, Integer percentage) {
                            display(new Date().toString() + " : " +
                                    virtualDevice.getEndpointId() +
                                    " : On Call : " + VEHICLE_ACCEL_ACTION + " accelerate:" + percentage);
                            sensor.accelerate(percentage);
                        }
                    });

        }

    }

}
