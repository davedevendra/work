/*
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.sample.obd2;

import oracle.iot.client.DeviceModel;
import oracle.iot.client.enterprise.Device;
import oracle.iot.client.enterprise.EnterpriseClient;
import oracle.iot.client.enterprise.Filter;
import oracle.iot.client.enterprise.Pageable;
import oracle.iot.client.enterprise.VirtualDevice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/*
 * This sample of listing, monitoring, and setting the attributes of OBD
 * devices using an IoT server.
 *
 * Note that the code is Java SE 1.5 compatible.
 */
public class Obd2EnterpriseSample {
    private static final String OBD2_SENSOR_MODEL_URN = "urn:com:oracle:iot:device:obd2";
    private static final String OBD2_SENSOR_SIMULATOR_MODEL_URN = "urn:com:oracle:iot:device:obd2simulator";

    private static final String VEHICLE_START_ACTION = "start";
    private static final String VEHICLE_STOP_ACTION = "stop";

    private static final String VEHICLE_BRAKE_ACTION = "brake";
    private static final String VEHICLE_ACCEL_ACTION = "accelerate";
    private static final String VEHICLE_SET_SPEED_ACTION = "setSpeed";

    private static final String VEHICLE_MIL_ON_ACTION = "setMalfunctionIndicatorOn";
    private static final String VEHICLE_MIL_OFF_ACTION = "setMalfunctionIndicatorOff";

    public static boolean isUnderFramework = false;
    public static boolean exiting = false;

    // Map model format URN to its name
    private Map<String,String> formatNames;

    private EnterpriseClient ec;
    private List<VirtualDevice> devices = new ArrayList<VirtualDevice>();
    private VirtualDevice device;

    public static void main(String[] args) {
        Obd2EnterpriseSample ecwv = null;
        int exitCode = -1;

        try {
            if (args.length < 2) {
                display("\nIncorrect number of arguments.");
                showUsage();
                throw new IllegalArgumentException("provisioner file and password must be specified.");
            }

            if (args.length == 2) {
                ecwv = new Obd2EnterpriseSample(args[0], args[1]);
                ecwv.listDevices();
            } else {
                if (args.length == 3) {
                    ecwv = new Obd2EnterpriseSample(args[0], args[1], args[2], false);
                    ecwv.monitorDevices();
                } else if (args.length >= 4) {
                    String cmd = args[2];
                    if (cmd.trim().equalsIgnoreCase("help")) {
                        showUsage();
                    } else {
                        ecwv = new Obd2EnterpriseSample(args[0], args[1], args[2], true);
                        processSimulationCommnds(ecwv, args[3], args);
                    }
                }
            }

            exitCode = 0;

        } catch (Throwable e) {
            displayException(e);
            if (isUnderFramework) throw new RuntimeException(e);
        } finally {
            // Dispose of the enterprise client
            if (ecwv != null) {
                ecwv.close();
            }
        }
        if (isUnderFramework && !exiting) {
            // when not monitoring there is no infinite loop which needs 'stop <bundle>' command
            // throw exception to stop bundle
            throw new RuntimeException("exiting with " + exitCode);
        }
    }

    static int getSpeed(String[] args) {
        int speed = -1;
        if (args.length > 3) {
            try {
               speed = Integer.parseInt(args[4]);
            } catch (Exception ex) {

            }
        }
        return speed;
    }

    static int getPercentage(String[] args) {
        int speedPercentage = -1;
        if (args.length > 3) {
            try {
                speedPercentage = Integer.parseInt(args[4]);
            } catch (Exception ex) {

            }
        }
        return speedPercentage;
    }


    static void processSimulationCommnds(Obd2EnterpriseSample ecwv, String cmd, String[] args) throws Exception {
        Commands command = Commands.getCommand(cmd);
        int argument;
        if (command != null) {
            switch (command) {
                case START:
                    ecwv.startVehicle();
                    break;
                case STOP:
                    ecwv.stopVehicle();
                    break;
                case SET_SPEED:
                    argument = getSpeed(args);
                    if (argument > 0) {
                        ecwv.setSpeed(argument);
                    } else {
                        System.out.println("Please specify valid integer between 0-255 for speed.");
                        showUsage();
                    }
                    break;
                case BRAKE:
                    int brakePercentage = getPercentage(args);
                    if (brakePercentage > 0 && brakePercentage <= 100) {
                        ecwv.brakeVehicle(brakePercentage);
                    } else {
                        System.out.println("Please specify valid integer between 0-100 for brake percentage.");
                        showUsage();
                    }
                    break;
                case ACCELERATE:
                    int accelPercentage = getPercentage(args);
                    if (accelPercentage > 0 && accelPercentage <= 100) {
                        ecwv.accelerateVehicle(accelPercentage);
                    } else {
                        System.out.println("Please specify valid integer between 0-100 for acceleration percentage.");
                        showUsage();
                    }
                    break;
                case MIL_ON:
                    ecwv.setMilOn(args[4]);
                    break;
                case MIL_OFF:
                    ecwv.setMilOff();
                    break;
                default:
                    System.out.println("invalid command:" + cmd);
                    showUsage();
            }
        }
        else
        {
            showUsage();
        }
    }

    private void setMilOn(String dtcCodes) throws Exception {

        // Control the device through the virtual device
        display(new Date().toString() + " : " + device.getEndpointId() + " : Call : " +
                VEHICLE_MIL_ON_ACTION  + " DTC Codes: " + dtcCodes);

        // Set the MIL flag.
        device.call(VEHICLE_MIL_ON_ACTION, dtcCodes);

        // Dispose of the enterprise client
        close();
    }

    private void setMilOff() throws Exception {

        // Control the device through the virtual device
        display(new Date().toString() + " : " + device.getEndpointId() + " : Call : " +
                VEHICLE_MIL_OFF_ACTION );

        // ReSet the MIL flag.
        device.call(VEHICLE_MIL_OFF_ACTION);

        // Dispose of the enterprise client
        close();
    }

    private void setSpeed(int speed) throws Exception {

        // Control the device through the virtual device
        display(new Date().toString() + " : " + device.getEndpointId() + " : Call : " +
                VEHICLE_SET_SPEED_ACTION  + " speed: " + speed);

        System.out.println("speed is:" + speed);

        // Set the power flag.
        device.call(VEHICLE_SET_SPEED_ACTION, speed);

        // Dispose of the enterprise client
        close();
    }

    private void startVehicle() throws Exception {

        // Control the device through the virtual device
        display(new Date().toString() + " : " + device.getEndpointId() + " : Call : " +
                VEHICLE_START_ACTION);
        // Set the power flag.
        device.call(VEHICLE_START_ACTION);

        // Dispose of the enterprise client
        close();
    }

    private void stopVehicle() throws Exception {

        // Control the device through the virtual device
        display(new Date().toString() + " : " + device.getEndpointId() + " : Call : " +
                VEHICLE_STOP_ACTION);
        // Set the power flag.
        device.call(VEHICLE_STOP_ACTION);

        // Dispose of the enterprise client
        close();
    }

    private void brakeVehicle(int brakePercentage) throws Exception {

        // Control the device through the virtual device
        display(new Date().toString() + " : " + device.getEndpointId() + " : Call : " +
                VEHICLE_BRAKE_ACTION + " brakePercentage: " + brakePercentage);

        //System.out.println("Before the brake call");

        // Set the power flag.
        device.call(VEHICLE_BRAKE_ACTION, brakePercentage);

        //System.out.println("After the brake call");

        // Dispose of the enterprise client
        close();
    }

    private void accelerateVehicle(int accelPercentage) throws Exception {

        // Control the device through the virtual device
        display(new Date().toString() + " : " + device.getEndpointId() + " : Call : " +
                VEHICLE_ACCEL_ACTION + " AccelerationPercentage: " + accelPercentage);
        // Set the power flag.
        device.call(VEHICLE_ACCEL_ACTION, accelPercentage);

        // Dispose of the enterprise client
        close();
    }

    private static VirtualDevice getVirtualDeviceByModelURN( List<VirtualDevice> devices, String modelURN) {
        for (VirtualDevice d: devices) {
            if (modelURN.equals(d.getDeviceModel().getURN())) {
                return d;
            }
        }
        return null;
    }


    private static void display(String string) {
        System.out.println(string);
    }

    private static void showUsage() {
        Class<?> thisClass = new Object() { }.getClass().getEnclosingClass();
        display("Usage:\n"
                + "java " + thisClass.getName()
                + " <trusted assets file> <trusted assets password>\n"
                + "    List all OBD2 sensors.\n\n"

                + "java " + thisClass.getName()
                + " <trusted assets file> <trusted assets password>"
                + " <deviceId>[,<deviceId>]\n"
                + "    Monitor OBD device(s) & print its measurements every"
                + " time it changes, until return key is pressed.\n\n"

                + "java " + thisClass.getName()
                + " <trusted assets file> <trusted assets password>"
                + " <deviceId> (accel <acceleration percentage> | start | stop |"
                + " brake <brake percentage> | setSpeed <speed> | milOn <DTC Codes> | milOff  \n\n");
    }

    private Obd2EnterpriseSample(String tas, String pwd) throws Exception {
        this(tas, pwd, null,false);
    }

    private Obd2EnterpriseSample(String tas, String pwd, String deviceIds, boolean isControlingDevie)
            throws Exception {
        /*
         * Initialize enterprise client.
         */
        ec = EnterpriseClient.newClient(tas, pwd);

        if (deviceIds == null) {
            devices = null;
            return;
        }

        List<String> ids = Arrays.asList(deviceIds.split(","));
        for (String id: ids) {
            boolean added = false;
            List<String> modelUrns = getDeviceModelUrnsForDevice(id);
            for (String modelUrn : modelUrns) {
                if (isControlingDevie) {
                    if (OBD2_SENSOR_SIMULATOR_MODEL_URN.equals(modelUrn)) {
                        // Create a virtual device for this model
                        DeviceModel deviceModel = ec.getDeviceModel(modelUrn);
                        //System.out.println("deviceModel" + deviceModel);
                        device = ec.createVirtualDevice(id, deviceModel);
                        added = devices.add(device);
                    }
                }
                if (OBD2_SENSOR_MODEL_URN.equals(modelUrn)) {
                    // Create a virtual device for this model
                    DeviceModel deviceModel = ec.getDeviceModel(modelUrn);
                    //System.out.println("deviceModel" + deviceModel);
                    added = devices.add(ec.createVirtualDevice(id, deviceModel));
                }
            }

            if (!added) {
                throw new IllegalArgumentException(id + " does not have device model \"" +
                    OBD2_SENSOR_SIMULATOR_MODEL_URN + "\"");
            }
        }

        if (devices.isEmpty()) {
            throw new IllegalArgumentException("Cannot find " + deviceIds);
        }

    }

    private void close() {
        try {
            // Dispose of the enterprise client
            ec.close();
        } catch (Exception ignored) {
        }
    }

    private void listDevices() throws Exception {
        try {
            Pageable<Device> devices = ec.getActiveDevices(OBD2_SENSOR_MODEL_URN);

            if (devices.hasMore()) {
                while (devices.hasMore()) {
                    try {
                        devices.next();
                    } catch (java.util.NoSuchElementException e) {
                        // TODO: remove try-catch when server properly sends
                        //       hasMore in the response
                        break;
                    }

                    for (Device d : devices.elements()) {
                        display(d.getId() + " [OBD2 Sensor]");
                    }
                }
            } else {
                display("No active devices for device model \"" + OBD2_SENSOR_MODEL_URN + "\"");
            }

        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }

    private void monitorDevices() throws Exception {
        for (VirtualDevice d: devices) {
            // Monitor devices through virtual devices
            setupHandlers(d);
        }

        VirtualDevice device = getVirtualDeviceByModelURN(devices, OBD2_SENSOR_SIMULATOR_MODEL_URN);

        if (isUnderFramework) {
            for(;;) {
                Thread.sleep(100);
                if (exiting) {
                    System.err.println("ECS monitorDevices: isUnderFramework="+isUnderFramework+" exiting="+exiting);
                    break;
                }
            }
        } else {
            display("\n\tMonitoring changes in OBD2 Device..");
            display("\tPress enter to exit.\n");
        
            System.in.read();
            exiting = true;
       }
    }

    private void setupHandlers(VirtualDevice device) {
        setupHandlers(device, false);
    }

    private final AtomicBoolean breakWaitLoop = new AtomicBoolean(false);

    private void setupHandlers(VirtualDevice device,
            final boolean displayOnlyThreshold) {
        device.setOnChange(new VirtualDevice.ChangeCallback<VirtualDevice>() {
            public void onChange(VirtualDevice.ChangeEvent<VirtualDevice>
                                 event) {
                VirtualDevice virtualDevice = event.getVirtualDevice();

                boolean maxThresholdChanged = false;
                StringBuilder msg =
                    new StringBuilder(new Date().toString());
                msg.append(" : ");
                msg.append(virtualDevice.getEndpointId());
                msg.append(" : onChange : ");
                boolean first = true;
                VirtualDevice.NamedValue<?> namedValue =
                    event.getNamedValue();
                while(namedValue != null) {
                    String attributeName = namedValue.getName();
                    Object attributeValue = namedValue.getValue();

                    if (!first) {
                        msg.append(',');
                    } else {
                        first = false;
                    }

                    //maxThresholdChanged |=
                    //    MAX_THRESHOLD_ATTRIBUTE.equals(attributeName);
                    msg.append('\"');
                    msg.append(attributeName);
                    msg.append("\"=");
                    msg.append(String.valueOf(attributeValue));

                    namedValue = namedValue.next();
                }

                if (displayOnlyThreshold) {
                    msg.insert(0, System.getProperty("line.separator"));
                }
                display(msg.toString() + "\n");

                breakWaitLoop.set(displayOnlyThreshold && maxThresholdChanged);
            }
        });

        /*
         * Set a callback for any alert that may be raised for this virtual
         * device.
         */
        device.setOnAlert(new VirtualDevice.AlertCallback() {
            public void onAlert(VirtualDevice.AlertEvent event) {
                VirtualDevice virtualDevice = event.getVirtualDevice();

                StringBuilder msg =
                    new StringBuilder(new Date().toString());
                msg.append(" : ");
                msg.append(virtualDevice.getEndpointId());
                msg.append(" : onAlert : ");

                boolean first = true;
                VirtualDevice.NamedValue<?> namedValue = event.getNamedValue();
                while(namedValue != null) {
                    String name = namedValue.getName();
                    Object value = namedValue.getValue();

                    if (!first) {
                        msg.append(',');
                    } else {
                        first = false;
                    }

                    msg.append('\"');
                    msg.append(name);
                    msg.append("\"=");
                    msg.append(String.valueOf(value));

                    namedValue = namedValue.next();
                }

                /*final String alertName = formatNames.get(event.getURN());
                if (alertName != null) {
                    msg.append(" (").append(alertName).append(")");
                }

                if (displayOnlyThreshold) {
                    msg.insert(0, System.getProperty("line.separator"));
                }*/

                display(msg.toString());
            }
        });

        device.setOnError(new VirtualDevice.ErrorCallback<VirtualDevice>() {
            public void onError(VirtualDevice.ErrorEvent<VirtualDevice> event) {
                VirtualDevice device = event.getVirtualDevice();
                display(new Date().toString() + " : onError : " +
                    device.getEndpointId() + " : \"" + event.getMessage() +
                    "\"");
                /*    
                 * Normally some error processing would go here,
                 * but since this a sample, notify the main thread
                 * to end the sample.
                 */
                breakWaitLoop.set(true);
            }
        });
    }

    private List<String> getDeviceModelUrnsForDevice(String endpointId)
            throws Exception {
        Device device = null;

        Filter f = Filter.eq(Device.Field.ID.alias(), endpointId);
        Pageable<Device> devices = ec.getDevices(null, f);
        while (device == null && devices.hasMore()) {
            devices.next();
            for (Device d: devices.elements()) {
                if (d.getId().equals(endpointId)) {
                    device = d;
                    break;
                }
            }
        }

        if (device == null) {
            throw new IllegalArgumentException("Cannot find device " +
                                               endpointId);
        }

        // Get the device model instance.
        List<String> urns = device.getDeviceModels();
        if (urns == null || urns.isEmpty()) {
            throw new RuntimeException("No device models for " + endpointId);
        }

        // The device model formats.
        return urns;
    }

    private static void displayException(Throwable e) {
        StringBuffer sb = new StringBuffer(e.getMessage() == null ?
                  e.getClass().getName() : e.getClass().getName() +": " + e.getMessage());
        if (e.getCause() != null) {
            sb.append(".\n\tCaused by: ");
            sb.append(e.getCause());
        }
        System.out.println('\n' + sb.toString() + '\n');
        showUsage();
    }

    enum Commands {
        POWER_ON("powerOn"),
        START("start"),
        STOP("stop"),
        SET_SPEED("setSpeed"),
        BRAKE("brake"),
        ACCELERATE("accel"),
        MIL_ON("milOn"),
        MIL_OFF("milOff"),
        POWER_OFF("powerOff");

        private final String commmand;

        /**
         * @param text
         */
        private Commands(final String text) {
            this.commmand = text;
        }

        /* (non-Javadoc)
         * @see java.lang.Enum#toString()
         */
        @Override
        public String toString() {
            return commmand;
        }

        public static Commands getCommand(String cmd) {
            if (cmd.equalsIgnoreCase("start")) {
                return START;
            }else if (cmd.equalsIgnoreCase("stop")) {
                return STOP;
            } else if (cmd.equalsIgnoreCase("setSpeed")) {
                return SET_SPEED;
            } else if (cmd.equalsIgnoreCase("brake")) {
                return BRAKE;
            } else if (cmd.equalsIgnoreCase("milOn")) {
                return MIL_ON;
            } else if (cmd.equalsIgnoreCase("milOff")) {
                return MIL_OFF;
            } else if (cmd.equalsIgnoreCase("accel")) {
                return ACCELERATE;
            }else {
                return null;
            }
        }

    }
}
