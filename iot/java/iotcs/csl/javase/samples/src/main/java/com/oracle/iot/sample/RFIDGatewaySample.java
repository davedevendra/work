package com.oracle.iot.sample;
/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 */

import oracle.iot.client.DeviceModel;
import oracle.iot.client.device.GatewayDevice;
import oracle.iot.client.device.VirtualDevice;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.Map;

import java.security.GeneralSecurityException;

/*
 * This sample is a reference RFID Gateway implementation that detects and sends the proximity of the RFID tags
 * as virtual devices to the IoT server.
 *
 *
 * Note that the code is Java SE 1.5 compatible.
 */
public class RFIDGatewaySample {

    private static final String PROXIMITY_SENSOR_MODEL_URN =
            "urn:com:oracle:iot:device:proximity_sensor";
    private static final String ZONE_ATTRIBUTE = "ora_zone";

    private static GatewayDevice gatewayDevice;

    private static final String RFID_COM_PORT_PROPERTY_NAME = "com.oracle.iot.sample.rfid_com_port";

    /**  RFID_COM_PORT Could be configured using {@code com.oracle.iot.sample.rfid_com_port} property */
    private static final String RFID_COM_PORT = System.getProperty(RFID_COM_PORT_PROPERTY_NAME, "/dev/ttyUSB0");

    /** Check if the given device file path exists and create a BufferedReader object */
    private static BufferedReader getDeviceFileReader(String deviceFilePath) {
        File deviceFile = new File(deviceFilePath);
        BufferedReader reader = null;
        if (deviceFile.exists() && deviceFile.canRead()) {
            try {
                reader = new BufferedReader(new FileReader(deviceFile));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        return reader;
    }

    public static void main(String[] args) {
        try {
            String gatewayProvisionerFile = "";
            String gatewayProvisionerFilePassword = "";
            String tagList = "";

            // Parse the command line arguments.
            boolean isSimulated = false;
            if (args.length < 2) {
                display("\nIncorrect number of arguments.");
                throw new IllegalArgumentException("");
            } else {
                gatewayProvisionerFile = args[0];
                gatewayProvisionerFilePassword = args[1];
                if (args.length > 2) {
                    tagList = args[2];
                    isSimulated = true;
                }
            }

            // Create the RFID Gateway device.
            display("\nCreating the RFID gateway instance...");
            try {
                gatewayDevice = new GatewayDevice(gatewayProvisionerFile, gatewayProvisionerFilePassword);
                display("Done.");
            } catch (GeneralSecurityException dse) {
                throw dse;
            }

            // Activate the RFID Gateway device.
            if (!gatewayDevice.isActivated()) {
                display("\nActivating the gateway device..." + args[0]);
                gatewayDevice.activate();
                display("Done.");
            }
            DeviceModel proximityDeviceModel = gatewayDevice.getDeviceModel(PROXIMITY_SENSOR_MODEL_URN);

            // Create the RFID Sensor object.
            final RFIDSensor sensor;
            if (isSimulated) {
                sensor = new RFIDSensor(gatewayDevice.getEndpointId() + "_Rfid", tagList);
                display("Running in Simulated mode: RFID Tag list: " + tagList);
            } else {
                BufferedReader comPortReader = getDeviceFileReader(RFID_COM_PORT);
                if (comPortReader == null) {
                    String error = "Property: " + RFID_COM_PORT_PROPERTY_NAME + " must be set to valid COM port device path";
                    throw new InvalidPropertiesFormatException(error);
                }
                display("Running in device mode: Using the COM port device :" + RFID_COM_PORT + " for reading RFID tags");
                sensor = new RFIDSensor(gatewayDevice.getEndpointId() + "_Rfid", comPortReader);
                display("Please swipe the RFID tag(s) in RFID reader. Press Ctrl+C to Exit.");
            }

            Map<String, String> metaData = new HashMap<String, String>();
            metaData.put(GatewayDevice.MANUFACTURER, sensor.getManufacturer());
            metaData.put(GatewayDevice.MODEL_NUMBER, sensor.getModelNumber());
            HashMap<String, VirtualDevice> mapVirtualDevices = new HashMap<String, VirtualDevice>();

            VirtualDevice proximitySensor;
            String nextId = "";
            while (true){
                // Get the next RFID tag from the RFID reader
                nextId = sensor.readNextId();
                // If nextId is null, either there is problem in I/O with device,
                // or in simulated mode, all given tags have been processed already.
                if (nextId == null) {
                    break;
                } else if (nextId.isEmpty()) {
                    // Empty tag was read, so retry reading the RFID tag again.
                    continue;
                }

                // If this tag has not been read before, register a new ICD device through gateway.
                if ((proximitySensor = mapVirtualDevices.get(nextId)) == null) {
                    metaData.put(GatewayDevice.SERIAL_NUMBER, nextId);
                    String proximitySensorEndpointId =
                            gatewayDevice.registerDevice(nextId, metaData, PROXIMITY_SENSOR_MODEL_URN);
                    proximitySensor = gatewayDevice.createVirtualDevice(proximitySensorEndpointId, proximityDeviceModel);
                    mapVirtualDevices.put(nextId, proximitySensor);
                }
                proximitySensor.set(ZONE_ATTRIBUTE,"IMMEDIATE");

                StringBuilder sb = new StringBuilder(new Date().toString());
                sb.append(" : ");
                sb.append(nextId);
                display(sb.toString());
            }
        } catch (Throwable e) {
            // catching Throwable, not Exception:
            // could be java.lang.NoClassDefFoundError
            // which is not Exception
            displayException(e);
        } finally {
            // Dispose of the device client
            try {
                if (gatewayDevice != null) gatewayDevice.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void showUsage() {
        Class<?> thisClass = new Object() { }.getClass().getEnclosingClass();
        display("Usage: \n"
                + "java " + thisClass.getName()
                + " <trusted assets file> <trusted assets password> " +
                " [<RFID asset tag id1>[,<RFID asset tag id2>,...]]\n");
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

    /**
     * RFID sensor for use in the samples. The sensor reads the RFID tag value
     */
    public static class RFIDSensor {


        // A unique identifier for this sensor
        private final String hardwareId;

        //BufferedReader to read next tags based on newline separator.
        private final BufferedReader reader;

        // Flag to indicate simulated mode.
        private final boolean isSimulated;

        private String[] tags;

        private int index = 0;

        /**
         * Create a RFIDSensor
         * @param id a unique identifier for this sensor
         * @param comPortReader device file for the USB RFID Reader
         *
         */
        RFIDSensor(String id, BufferedReader comPortReader) {
            hardwareId = id;
            reader = comPortReader;
            isSimulated = false;
        }

        /**
         * Create a RFIDSensor for Simulated mode
         * @param  id a unique identifier for this sensor
         * @param tagList comma separated list of simulated RFID tags.
         *
         */
        RFIDSensor(String id, String tagList) {
            hardwareId = id;
            tags = tagList.split(",");
            reader = null;
            isSimulated = true;
        }

        /**
         * Get the next RFID tag.
         *
         * @return the RFID tag scanned by RFID sensor.
         */
        String readNextId() throws IOException {
            String id;
            if (isSimulated) {
                if (index < tags.length) {
                    id = tags[index];
                    index++;
                } else {
                    id = null;
                }
            } else {
                id = reader.readLine();
                if (id != null) {
                    id = id.trim();
                }
            }
            return id;
        }


        /**
         * Get the manufacturer name, which can be used as part of the device meta-data.
         *
         * @return the manufacturer name
         */
        String getManufacturer() {
            return "Sparkfun";
        }

        /**
         * Get the model number, which can be used as part of the device meta-data.
         *
         * @return the model number
         */
        String getModelNumber() {
            return "USB-RF-" + hardwareId;
        }

    }
}
