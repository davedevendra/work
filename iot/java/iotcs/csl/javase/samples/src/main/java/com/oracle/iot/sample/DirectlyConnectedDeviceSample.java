/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.sample;

import oracle.iot.client.AbstractVirtualDevice.ErrorCallback;
import oracle.iot.client.AbstractVirtualDevice.ErrorEvent;
import oracle.iot.client.DeviceModel;
import oracle.iot.client.device.Alert;
import oracle.iot.client.device.DirectlyConnectedDevice;
import oracle.iot.client.device.VirtualDevice;

import java.io.IOException;
import java.util.Date;

/**
 * This sample presents a simple sensor as virtual device to the IoT
 * server. The simple sensor is polled every half second and each
 * virtual device is updated.
 * <p>
 *     The sample runs in two modes: with policies or without policies. By
 *     default, the sample runs without policies. To enable the use of
 *     policies, set the property {@code com.oracle.iot.sample.use_policy}.
 *  <p>
 *      When running with policies, the device policies must be uploaded to
 *      the server. The sample reads the sensor data and calls the
 *      {@code VirtualDevice#offer} method, which applies the policies.
 *      Data and alert messages are sent
 *      to the server depending on the configuration of the device policies.
 *  <p>
 *      When running without policies, the sample reads the sensor data and
 *      calls the {@code VirtualDevice#set} method, which results in a
 *      data message being sent to the server. The sample itself must
 *      generate alerts if the value from the sensor exceeds
 *      the maximum threshold of the sensor's data model.
 *  <p>
 * Note that the code is Java SE 1.6 compatible.
 */
public class DirectlyConnectedDeviceSample {

    /**
     * This sample can be used with policies, or without policies. By default,
     * the sample does not use policies. Set the property
     * "com.oracle.iot.sample.use_policy" to true to use policies.
     */
    private static final boolean usePolicy;
    static {
        // Treat -Dcom.oracle.iot.sample.use_policy
        // the same as -Dcom.oracle.iot.sample.use_policy=true
        final String value = System.getProperty("com.oracle.iot.sample.use_policy");
        usePolicy = "".equals(value) || Boolean.parseBoolean(value);
    }

    /**
     * sensor polling interval before sending next readings.
     * Could be configured using {@code com.oracle.iot.sample.sensor_polling_interval} property
     */
    private static final long SENSOR_POLLING_INTERVAL =
        Long.getLong("com.oracle.iot.sample.sensor_polling_interval", 5000);

    // The following calculations of number_of_loops and sleepTime break the
    // SENSOR_POLLING_INTERVAL into a number of smaller intervals approximately
    // SLEEP_TIME milliseconds long. This just makes the sample a little more
    // responsive to keyboard input and has nothing to do with the client library.
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



        DirectlyConnectedDevice directlyConnectedDevice = null;

        try {
            if (args.length != 2) {
                display("\nIncorrect number of arguments.\n");
                throw new IllegalArgumentException("");
            }

            // Initialize the device client
            directlyConnectedDevice = new DirectlyConnectedDevice(args[0], args[1]);

            // Activate the device
            if (!directlyConnectedDevice.isActivated()) {
                display("\nActivating...");
                directlyConnectedDevice.activate("urn:com:oracle:iot:device:humidity_sensor");
            }

            // The "real" device
            final HumiditySensor humiditySensor =
                    new HumiditySensor(directlyConnectedDevice.getEndpointId() + "_Sample_HS");

            // Create a virtual device implementing the device model
            final DeviceModel deviceModel =
                directlyConnectedDevice.getDeviceModel("urn:com:oracle:iot:device:humidity_sensor");

            final VirtualDevice virtualHumiditySensor =
                directlyConnectedDevice.createVirtualDevice(directlyConnectedDevice.getEndpointId(), deviceModel);

            // Initialize the sensor to the device model's maxThreshold default value
            int defaultThreshold = virtualHumiditySensor.get("maxThreshold");
            humiditySensor.setMaxThreshold(defaultThreshold);

            /*
             * Monitor the virtual device for requested attribute changes and
             * errors.
             *
             * Since there is only one attribute, maxThreshold, this could have
             * done with using an attribute specific on change handler.
             */
            virtualHumiditySensor.setOnChange(
                new VirtualDevice.ChangeCallback<VirtualDevice>() {
                    public void onChange(
                            VirtualDevice.ChangeEvent<VirtualDevice> event) {

                        VirtualDevice virtualDevice =
                            event.getVirtualDevice();
                        VirtualDevice.NamedValue<?> namedValues =
                            event.getNamedValue();
                        StringBuilder msg =
                            new StringBuilder(new Date().toString());
                        msg.append(" : ");
                        msg.append(virtualDevice.getEndpointId());
                        msg.append(" : onChange : ");

                        boolean first = true;
                        for (VirtualDevice.NamedValue<?> namedValue =
                             namedValues;
                             namedValue != null;
                             namedValue = namedValue.next()) {
                            final String attribute = namedValue.getName();
                            final Object value = namedValue.getValue();
                            if (!first) {
                                msg.append(',');
                            } else {
                                first = false;
                            }

                            if ("maxThreshold".equals(attribute)) {
                                int max = ((Integer)value).intValue();
                                msg.append("\"maxThreshold\"=");
                                msg.append(max);

                                // Update the actual device
                                humiditySensor.setMaxThreshold(max);
                                /*
                                 * After this callback returns without an
                                 * exception, the virtual device attribute
                                 * will be updated locally and on the server.
                                 */
                            } else {
                                msg.append("\"")
                                        .append(attribute)
                                        .append("\" not implemented");
                            }
                        }

                        display(msg.toString());
                    }
                });

            virtualHumiditySensor.setOnError(
                new VirtualDevice.ErrorCallback<VirtualDevice>() {
                    public void onError(VirtualDevice.ErrorEvent<VirtualDevice
                            > event) {
                        VirtualDevice device =  event.getVirtualDevice();
                        display(new Date().toString() + " : onError : " +
                                device.getEndpointId() +
                                " : \"" + event.getMessage() + "\"");
                    }
                });



            display("\nCreated virtual humidity sensor " + virtualHumiditySensor.getEndpointId());
            display("\n\tPress enter to exit.\n");

            //
            // Which sensor data is processed depends on whether or not policies are used.
            // Compare the code in the processSensorData method of the WithPolicies class
            // to that of the WithoutPolicies class.
            //
            final MainLogic mainLogic = usePolicy
                    ? new WithDevicePolicies(humiditySensor, virtualHumiditySensor)
                    :  new WithoutDevicePolicies(humiditySensor, virtualHumiditySensor);

            mainLoop:
            for (; ; ) {

                mainLogic.processSensorData();

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
            }
        } catch (Throwable e) { 
            // catching Throwable, not Exception:
            // could be java.lang.NoClassDefFoundError
            // which is not Exception

            displayException(e);
            if (isUnderFramework) throw new RuntimeException(e);
        } finally {
            // Dispose of the device client
            try {
                if (directlyConnectedDevice != null) directlyConnectedDevice.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static abstract class MainLogic {

        protected final HumiditySensor humiditySensor;
        protected final VirtualDevice virtualHumiditySensor;

        protected MainLogic(HumiditySensor humiditySensor,
                            VirtualDevice virtualHumiditySensor) {
            this.humiditySensor = humiditySensor;
            this.virtualHumiditySensor = virtualHumiditySensor;
        }

        // This method allows for differentiation between the processing
        // of sensor data when policies are in use from when policies are
        // not in use.
        public abstract void processSensorData();
    }


    private static class WithoutDevicePolicies extends MainLogic {

        //
        // Create an Alert for maximum humidity threshold.
        // Alerts do not have to be created new each time an
        // alerting event occurs.
        //
        private final Alert tooHumidAlert;

        // Flags to ensure an alert is only generated when the threshold is crossed.
        private boolean humidityAlerted = false;

        // A callback for receiving an error if the tooHumid alert fails to be sent.
        private final ErrorCallback<VirtualDevice> alertFailed = new ErrorCallback<VirtualDevice>() {
            @Override
            public void onError(ErrorEvent<VirtualDevice> event) {
                VirtualDevice virtualDevice = event.getVirtualDevice();
                StringBuilder msg =
                    new StringBuilder(new Date().toString())
                        .append(" : ")
                        .append(virtualDevice.getEndpointId())
                        .append(" : onError : ");

                VirtualDevice.NamedValue<?> namedValue = event.getNamedValue();
                while (namedValue != null) {
                    final String attribute = namedValue.getName();
                    final Object value = namedValue.getValue();
                    msg.append(",\"").append(attribute).append("\"=").append(value);
                    namedValue = namedValue.next();
                }
                display(msg.toString());

            }
        };


        private WithoutDevicePolicies(HumiditySensor humiditySensor,
                                      VirtualDevice virtualHumiditySensor) {
            super(humiditySensor, virtualHumiditySensor);

            tooHumidAlert = virtualHumiditySensor
                    .createAlert("urn:com:oracle:iot:device:humidity_sensor:too_humid");

            tooHumidAlert.setOnError(alertFailed);

        }

        //
        // When running the sample without policies, the sample must do more work.
        // The sample has to generate alerts if the sensor value exceeds the
        // threshold. This logic can be handled by the client library with the
        // right set of policies in place. Compare this method to that of the
        // WithPolicies inner class.
        //
        @Override
        public void processSensorData() {

            int humidity = humiditySensor.getHumidity();

            StringBuilder consoleMessage = new StringBuilder(new Date().toString())
                    .append(" : ")
                    .append(virtualHumiditySensor.getEndpointId())
                    .append(" : Set : \"humidity\"=")
                    .append(humidity);

            // Set the virtualilzed humidity sensor value in an 'update'. If
            // the humidity maxThreshold has changed, that maxThreshold attribute
            // will be added to the update (see the block below). A call to
            // 'finish' will commit the update.
            virtualHumiditySensor.update()
                    .set("humidity", humidity);

            display(consoleMessage.toString());
            virtualHumiditySensor.finish();

            int humidityThreshold = humiditySensor.getMaxThreshold();
            if (humidity > humidityThreshold) {
                if (!humidityAlerted) {
                    humidityAlerted = true;
                    consoleMessage = new StringBuilder(new Date().toString())
                            .append(" : ")
                            .append(virtualHumiditySensor.getEndpointId())
                            .append(" : Alert : \"humidity\"=")
                            .append(humidity);
                    display(consoleMessage.toString());
                    tooHumidAlert.set("humidity", humidity).raise();
                }
            } else {
                humidityAlerted = false;
            }

        }
    }

    private static class WithDevicePolicies extends MainLogic {

        private WithDevicePolicies(HumiditySensor humiditySensor,
                                   VirtualDevice virtualHumiditySensor) {
            super(humiditySensor, virtualHumiditySensor);
        }

        //
        // When running with policies, we only need to "offer" the
        // humidity value. Policies can be set on the server to
        // generate alerts, and filter bad data - which had to be
        // handled by the sample code when running without policies.
        // Compare this implementation of processSensorData to that
        // of the WithoutPolicies inner class.
        //
        @Override
        public void processSensorData() {

            int humidity = humiditySensor.getHumidity();

            StringBuilder consoleMessage = new StringBuilder(new Date().toString())
                    .append(" : ")
                    .append(virtualHumiditySensor.getEndpointId())
                    .append(" : Offer : \"humidity\"=")
                    .append(humidity);

            display(consoleMessage.toString());

            virtualHumiditySensor.update().offer("humidity", humidity).finish();

        }
    }

    private static void showUsage() {
        Class<?> thisClass = new Object() { }.getClass().getEnclosingClass();
        display("Usage: \n"
                + "java " + thisClass.getName()
                + " <trusted assets file> <trusted assets password>\n"
        );
    }

    private static void display(String string) {
        System.out.println(string);
    }

    private static void displayException(Throwable e) {
        StringBuilder sb = new StringBuilder(e.getMessage() == null ?
                  e.getClass().getName() : e.getMessage());
        if (e.getCause() != null) {
            sb.append(".\n\tCaused by: ");
            sb.append(e.getCause());
        }
        System.out.println('\n' + sb.toString() + '\n');
        showUsage();
    }
}
