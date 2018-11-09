/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 */

package com.oracle.iot.sample;

import oracle.iot.client.DeviceModel;
import oracle.iot.client.device.GatewayDevice;
import oracle.iot.client.device.VirtualDevice;

import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

/**
 * This sample is a gateway that presents multiple beacon sensors as virtual
 * devices to the IoT server. The beacons are simulated iBeacons and Eddystone 
 * beacons.
 *
 * The sensors are polled every half second and each virtual device
 * is updated.
 *
 * Note that the code is Java SE 1.5 compatible.
 */
public class BeaconGatewaySample {
    private static final String IBEACON_SENSOR_MODEL_URN = 
            "urn:com:oracle:iot:device:location:ibeacon";
    private static final String EDDYSTONE_SENSOR_MODEL_URN = 
            "urn:com:oracle:iot:device:location:eddystone-tlm-uid";
    private static final String BEACON_RSSI_ATTRIBUTE = "ora_rssi";
    private static final String BEACON_TXPOWER_ATTRIBUTE = "ora_txPower";
    private static final String EDDYSTONE_TEMP_ATTRIBUTE = "temperature";
    private static final String EDDYSTONE_VOLT_ATTRIBUTE = "batteryVoltage";
    private static GatewayDevice gatewayDevice;
    
    /**
     * Number of simulated beacons to create
     */
    private static final int DEFAULT_NUM_BEACONS = 2;
    
    /**
     * Only information from beacons currently within this maximum distance in 
     * meters will be reported.
     */
    private static final double MAX_DISTANCE = 100.0;
    private static final String MAJOR_NUM = "BEAC";

    /**
     * sensor polling interval before sending next readings.
     * Could be configured using {@code com.oracle.iot.sample.sensor_polling_interval} property
     */
    private static final long SENSOR_POLLING_INTERVAL =
            Long.getLong("com.oracle.iot.sample.sensor_polling_interval", 5000);

    private static final int SLEEP_TIME = 100;
    private static long number_of_loops = (SLEEP_TIME > SENSOR_POLLING_INTERVAL ? 1 :
            SENSOR_POLLING_INTERVAL / SLEEP_TIME);
    private static long sleepTime =
            (SLEEP_TIME > SENSOR_POLLING_INTERVAL ?
                    SENSOR_POLLING_INTERVAL :
                    SLEEP_TIME + (SENSOR_POLLING_INTERVAL - number_of_loops * SLEEP_TIME) / number_of_loops);

    public static boolean isUnderFramework = false;
    public static boolean exiting = false;

    public static void main(String[] args) {

        try {
            if (args.length < 2) {
                display("\nIncorrect number of arguments.");
                throw new IllegalArgumentException("");
            }

            display("\nCreating the gateway instance...");
            try {
                // Initialize the device client
                gatewayDevice = new GatewayDevice(args[0], args[1]);
            } catch (GeneralSecurityException dse) {
                throw new RuntimeException(dse);
            }

            // Activate the device 
            if (!gatewayDevice.isActivated()) {
                display("\nActivating...");
                gatewayDevice.activate();
            }

            final DeviceModel iBeaconDeviceModel =
                    gatewayDevice.getDeviceModel(IBEACON_SENSOR_MODEL_URN);
            final DeviceModel eddystoneDeviceModel =
                    gatewayDevice.getDeviceModel(EDDYSTONE_SENSOR_MODEL_URN);

            BeaconSensor[] beaconSensors = null;

            if (args.length == 2) {
                beaconSensors = new BeaconSensor[DEFAULT_NUM_BEACONS];

                final String beaconUUID = gatewayDevice.getEndpointId() + "_Sample";
                for (int i = 0; i < DEFAULT_NUM_BEACONS; i++) {
                    String minor = String.format(Locale.ROOT, "%04x", i);
                    BeaconSensor.Type type;
                    DeviceModel model;
                    if (i % 2 == 0) {
                        type = BeaconSensor.Type.IBEACON;
                    } else {
                        type = BeaconSensor.Type.EDDYSTONE;
                    }
                    beaconSensors[i] = new BeaconSensor(beaconUUID, MAJOR_NUM, minor, type);
                }

            } else {

                //
                // The user may passed in the hardware id values for the beacons.
                //
                int count = 0;

                for(int argc=2; argc < args.length; argc++) {
                    final BeaconSensor.Type beaconType;
                    if (args[argc].startsWith("ibeaconIds=")) {
                        beaconType = BeaconSensor.Type.IBEACON;
                    } else if (args[argc].startsWith("eddystoneIds=")) {
                        beaconType = BeaconSensor.Type.EDDYSTONE;
                    } else {
                        throw new IllegalArgumentException(args[argc]);
                    }

                    final String[] beaconIds = args[argc].substring(args[argc].indexOf('=')+1).split(",");
                    if (beaconSensors == null) {
                        beaconSensors = new BeaconSensor[beaconIds.length];
                    } else {
                        BeaconSensor[] temp = new BeaconSensor[beaconSensors.length + beaconIds.length];
                        System.arraycopy(beaconSensors, 0, temp, 0, beaconSensors.length);
                        beaconSensors = temp;
                    }

                    for (int n=0; n<beaconIds.length; n++) {
                        beaconSensors[count++] = new BeaconSensor(beaconIds[n], beaconType);
                    }
                }

            }

            final int numberOfBeacons = beaconSensors.length;
            final String[] beaconSensorEndpointIds = new String[numberOfBeacons];
            final VirtualDevice[] virtualizedBeaconSensors =  new VirtualDevice[numberOfBeacons];

            for (int i=0; i<numberOfBeacons; i++) {
                // Register indirectly-connected devices 
                Map<String, String> metaData = new HashMap<String, String>();
                metaData.put(GatewayDevice.MANUFACTURER,
                        beaconSensors[i].getManufacturer());
                metaData.put(GatewayDevice.MODEL_NUMBER,
                        beaconSensors[i].getModelNumber());
                metaData.put(GatewayDevice.SERIAL_NUMBER,
                        beaconSensors[i].getSerialNumber());
                metaData.put(GatewayDevice.DEVICE_CLASS,
                        beaconSensors[i].getDeviceClass());
                metaData.put(GatewayDevice.PROTOCOL,
                        beaconSensors[i].getProtocol());
                metaData.put(GatewayDevice.PROTOCOL_DEVICE_CLASS,
                        beaconSensors[i].getProtocolDeviceClass());
                metaData.put(GatewayDevice.PROTOCOL_DEVICE_ID,
                        beaconSensors[i].getProtocolDeviceId());
                metaData.put("UUID", beaconSensors[i].getUUID());
                metaData.put("major", beaconSensors[i].getMajor());
                metaData.put("minor", beaconSensors[i].getMinor());

                final DeviceModel deviceModel =
                        beaconSensors[i].getType() == BeaconSensor.Type.IBEACON
                                ? iBeaconDeviceModel
                                : eddystoneDeviceModel;

                // Passing 'false' for the restricted flag tells the client library
                // that, if the server allows, the endpoint may freely roam.
                beaconSensorEndpointIds[i] =
                        gatewayDevice.registerDevice(
                                false,
                                beaconSensors[i].getHardwareId(),
                                metaData,
                                deviceModel.getURN()
                        );

                virtualizedBeaconSensors[i] =
                    gatewayDevice.createVirtualDevice(beaconSensorEndpointIds[i],
                            deviceModel);

                beaconSensors[i].setEndpointId(beaconSensorEndpointIds[i]);

                display("\nCreated virtual beacon sensor " + 
                        beaconSensorEndpointIds[i] + " with model " + deviceModel.getName() +
                "\n\tfor hardwareId " + beaconSensors[i].getHardwareId());
            }

            display("\n\tPress enter to exit.\n");
            
            //Set txPower value for beacon
            for (int i=0; i< numberOfBeacons; i++) {
                int txPower = beaconSensors[i].getTxPower();
                virtualizedBeaconSensors[i].update()
                        .set(BEACON_TXPOWER_ATTRIBUTE, txPower)
                        .finish();

                display(new Date().toString() + " : " + 
                        beaconSensorEndpointIds[i] + 
                        " : Set : \"ora_txPower\"=" + txPower);
            }

            mainLoop:
            for (; ; ) {
                //
                //
                // Wait SENSOR_POLLING_INTERVAL seconds before sending the next reading.
                // The SENSOR_POLLING_INTERVAL is broken into a number of smaller increments
                // to make the application more responsive to a keypress, which will cause
                // the application to exit.
                //
                for (int i = 0; i < number_of_loops; i++) {
                    Thread.sleep(sleepTime);

                    // when running under framework, use platform specific exit
                    if (!isUnderFramework) {
                        // User pressed the enter key while sleeping, exit.
                        if (System.in.available() > 0) {
                            break mainLoop;
                        }
                    } else if (exiting) {
                        break mainLoop;
                    }
                }

                for (int i=0; i<numberOfBeacons; i++) {
                    int rssi = beaconSensors[i].getRSSI();
                    double dist = beaconSensors[i].getDistance();

                    if (dist < MAX_DISTANCE) {
                        if (beaconSensors[i].getType() == BeaconSensor.Type.EDDYSTONE) {
                            int temp = beaconSensors[i].getTemperature();
                            int volt = beaconSensors[i].getVoltage();
                            virtualizedBeaconSensors[i].update()
                                    .set(BEACON_RSSI_ATTRIBUTE, rssi)
                                    .set(EDDYSTONE_TEMP_ATTRIBUTE, temp)
                                    .set(EDDYSTONE_VOLT_ATTRIBUTE, volt)
                                    .finish();

                            display(new Date().toString() + " : " + 
                                    beaconSensorEndpointIds[i] + 
                                    " : Set : \"ora_rssi\"=" + rssi + 
                                    " \"temperature\"=" + temp + 
                                    " \"batteryVoltage\"=" + volt);
                            
                        } else {
                            virtualizedBeaconSensors[i].update()
                                    .set(BEACON_RSSI_ATTRIBUTE, rssi)
                                    .finish();

                            display(new Date().toString() + " : " + 
                                    beaconSensorEndpointIds[i] + 
                                    " : Set : \"ora_rssi\"=" + rssi);
                        }
                    }
                }


            }
        } catch (Throwable e) {
            displayException(e);
            if (isUnderFramework) throw new RuntimeException(e);
        } finally {
            try {
                // Dispose of the device client
                gatewayDevice.close();
            } catch (java.io.IOException ignored) {
            }
         }
    }

    private static void showUsage() {
        Class<?> thisClass = new Object() { }.getClass().getEnclosingClass();
        display("Usage:\n"
                + "java " + thisClass.getName()
                + " <trusted assets file> <trusted assets password>" +
                " [ibeaconIds=<ibeacon activation id>[,<ibeacon activation id>...]]" +
                " [eddystoneIds=<eddystone activation id>[,<eddystone activation id>...]]\n");
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
}
