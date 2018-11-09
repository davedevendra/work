/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 */

package com.oracle.iot.sample;

import oracle.iot.client.AbstractVirtualDevice.ErrorCallback;
import oracle.iot.client.AbstractVirtualDevice.ErrorEvent;
import oracle.iot.client.DeviceModel;
import oracle.iot.client.device.Alert;
import oracle.iot.client.device.GatewayDevice;
import oracle.iot.client.device.VirtualDevice;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * This sample is a gateway that presents multiple simple sensors as virtual
 * devices to the IoT server. The simple sensors are polled every half second
 * and each virtual device is updated.
 * <p>
 *     The sample runs in two modes: with policies or without policies. By
 *     default, the sample runs without policies. To enable the use of
 *     policies, set the property {@code com.oracle.iot.sample.use_policy}.
 *  <p>
 *      When running with policies, the device policies must be uploaded to
 *      the server. The sample reads the humidity and temperature values
 *      from the sensors and calls the {@code VirtualDevice#offer} method,
 *      which applies the policies. Data and alert messages are sent
 *      to the server depending on the configuration of the device policies.
 *  <p>
 *      When running without policies, the sample reads the sensor data and
 *      calls the {@code VirtualDevice#set} method, which results in a
 *      data message being sent to the server. The sample itself must
 *      generate alerts if the value from either sensor exceeds
 *      the maximum threshold of that sensor's data model.
 *  <p>
 * Note that the code is Java SE 1.6 compatible.
 */
public class GatewayDeviceSample {

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
     * Sensor polling interval before sending next readings, in milliseconds.
     * Default value is 5000 milliseconds. Use the
     * "com.oracle.iot.sample.sensor_polling_interval" property
     * to override the default value.
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

        GatewayDevice gatewayDevice = null;

        try {
            if (args.length < 2) {
                display("\nIncorrect number of arguments.");
                throw new IllegalArgumentException("");
            }

            display("\nCreating the gateway instance...");
            // Initialize the device client
            gatewayDevice = new GatewayDevice(args[0], args[1]);

            // Activate the device
            if (!gatewayDevice.isActivated()) {
                display("\nActivating...");
                gatewayDevice.activate();
            }

            //
            // The hardware id for the sensor instances are fabricated
            // from the gateway's endpoint id and a suffix, the combination of
            // which is likely to be unique. On subsequent runs of the
            // GatewayDeviceSample (with the same gateway device), the
            // endpoint id from the previously registered indirectly connected
            // device will be returned by the registerDevice call. In other
            // words, the same indirectly connected device will be used.
            //
            final TemperatureSensor temperatureSensor =
                    new TemperatureSensor(
                            args.length > 2
                                    ? args[2]
                                    : gatewayDevice.getEndpointId() + "_Sample_TS");
            final String temperatureSensorEndpointId;

            final HumiditySensor humiditySensor =
                new HumiditySensor(
                        args.length > 3
                                ? args[3]
                                : gatewayDevice.getEndpointId() + "_Sample_HS");

            Map<String, String> metaData = new HashMap<String, String>();
            metaData.put(
                         GatewayDevice.MANUFACTURER,
                         temperatureSensor.getManufacturer());
            metaData.put(
                         GatewayDevice.MODEL_NUMBER,
                         temperatureSensor.getModelNumber());
            metaData.put(
                         GatewayDevice.SERIAL_NUMBER,
                         temperatureSensor.getSerialNumber());

            //
            // If the user gave a hardware id for the temperature sensor,
            // then restrict the sensor to this gateway. This means that
            // the sensor cannot be connected through other gateways.
            //
            final boolean temperatureSensorRestricted = args.length > 2;
            temperatureSensorEndpointId =
                gatewayDevice.registerDevice(
                        temperatureSensorRestricted,
                        temperatureSensor.getHardwareId(),
                        metaData,
                        "urn:com:oracle:iot:device:temperature_sensor");

            metaData = new HashMap<String, String>();
            metaData.put(
                         GatewayDevice.MANUFACTURER,
                         humiditySensor.getManufacturer());
            metaData.put(
                         GatewayDevice.MODEL_NUMBER,
                         humiditySensor.getModelNumber());
            metaData.put(
                         GatewayDevice.SERIAL_NUMBER,
                         humiditySensor.getSerialNumber());

            //
            // If the user gave a hardware id for the humidity sensor,
            // then restrict the sensor to this gateways. This means that
            // the sensor cannot be connected through other gateways.
            //
            final boolean humiditySensorRestricted = args.length > 3;
            final String humiditySensorEndpointId =
                        gatewayDevice.registerDevice(
                                humiditySensorRestricted,
                                humiditySensor.getHardwareId(),
                                metaData,
                                "urn:com:oracle:iot:device:humidity_sensor");

            // Create the virtual devices implementing the device models
            final DeviceModel temperatureDeviceModel =
                    gatewayDevice.getDeviceModel("urn:com:oracle:iot:device:temperature_sensor");

            final VirtualDevice virtualTemperatureSensor =
                    gatewayDevice.createVirtualDevice(
                            temperatureSensorEndpointId,
                            temperatureDeviceModel);

            // Initialize the temperature sensor to the device model's min and maxThreshold default values
            int defaultThreshold = virtualTemperatureSensor.get("minThreshold");
            temperatureSensor.setMinThreshold(defaultThreshold);
            defaultThreshold = virtualTemperatureSensor.get("maxThreshold");
            temperatureSensor.setMaxThreshold(defaultThreshold);

            final DeviceModel humidityDeviceModel =
                    gatewayDevice.getDeviceModel("urn:com:oracle:iot:device:humidity_sensor");

            final VirtualDevice virtualHumiditySensor =
                    gatewayDevice.createVirtualDevice(
                            humiditySensorEndpointId,
                            humidityDeviceModel);

            // Initialize the humidity sensor to the device model's maxThreshold default value
            defaultThreshold = virtualHumiditySensor.get("maxThreshold");
            humiditySensor.setMaxThreshold(defaultThreshold);

            /*
             * Set callbacks on the virtual devices to set attributes and
             * control the actual devices.
             */

            /*
             * For the temperatureSensor model, the min and max threshold values
             * can be written. Create a callback that will handle setting
             * the value on the temperature sensor device.
             */
            virtualTemperatureSensor.setOnChange(
                new VirtualDevice.ChangeCallback<VirtualDevice>() {
                    public void onChange(
                        VirtualDevice.ChangeEvent<VirtualDevice> event) {

                        VirtualDevice virtualDevice =
                            event.getVirtualDevice();
                        VirtualDevice.NamedValue<?> namedValues =
                            event.getNamedValue();
                        StringBuilder msg =
                            new StringBuilder(new Date().toString())
                                    .append(" : ")
                                    .append(virtualDevice.getEndpointId())
                                    .append(" : onChange : ");

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

                            if ("minThreshold".equals(attribute)) {
                                int min = ((Integer)value).intValue();
                                msg.append("\"minThreshold\"=");
                                msg.append(min);
                                if (temperatureSensor.isPoweredOn()) {
                                    // Update the actual device
                                    temperatureSensor.setMinThreshold(min);
                                }
                            } else if ("maxThreshold".equals(
                                    attribute)) {
                                int max = ((Integer)value).intValue();
                                msg.append("\"maxThreshold\"=");
                                msg.append(max);
                                if (temperatureSensor.isPoweredOn()) {
                                    // Update the actual device
                                    temperatureSensor.setMaxThreshold(max);
                                }
                            }

                            /*
                             * After this callback returns without an
                             * exception, the virtual device attributes
                             * will be updated locally and on the server.
                             */
                        }
                        if (!temperatureSensor.isPoweredOn()) {
                            msg.append(" ignored. Device is powered off");
                        }
                        display(msg.toString());
                    }
                });

            /*
             * The temperatureSensor model has a 'reset' action, which
             * resets the temperature sensor to factory defaults. Create
             * a callback that will handle calling reset on the temperature
             * sensor device.
             */
            virtualTemperatureSensor.setCallable("reset",
                new VirtualDevice.Callable<Void>() {
                    public void call(VirtualDevice virtualDevice,
                        Void not_used) {
                        StringBuilder msg =
                                new StringBuilder(new Date().toString())
                                        .append(" : ")
                                        .append(virtualDevice.getEndpointId())
                                        .append(" : Call : reset");
                        if (temperatureSensor.isPoweredOn()) {
                            temperatureSensor.reset();
                        } else {
                            msg.append(" ignored. Device is powered off");
                        }
                        display(msg.toString());
                        /*
                         * After the sensor is reset, next poll of the
                         * sensor will yield new min and max temp values
                         * and update the values in the VirtualDevice.
                         */
                    }
                });

            /*
             * The temperatureSensor model has a 'power' action, which
             * takes a boolean argument: true to power on the simulated device,
             * false to power off the simulated device. Create a callback that
             * will handle calling power on and power off on the temperature
             * sensor device.
             */
            virtualTemperatureSensor.setCallable("power",
                new VirtualDevice.Callable<Boolean>() {
                public void call(VirtualDevice virtualDevice, Boolean on) {
                    display(new Date().toString() + " : " +
                        virtualDevice.getEndpointId() +
                        " : Call : \"power\"=" + on);
                    boolean isOn = temperatureSensor.isPoweredOn();
                    if (on != isOn) {
                        temperatureSensor.power(on);
                    }

                    /*
                     * Avoid re-entering the library, let the polling
                     * loop handle the attribute update.
                     */
                }
            });

            /*
             * For the humiditySensor model, the maxThreshold attribute can be
             * written. Create a callback for setting the maxThreshold on the
             * humidity sensor device.
             */
            virtualHumiditySensor.setOnChange("maxThreshold",
                new VirtualDevice.ChangeCallback<VirtualDevice>() {
                    public void onChange(
                            VirtualDevice.ChangeEvent<VirtualDevice> event) {
                        VirtualDevice virtualDevice = event.getVirtualDevice();
                        VirtualDevice.NamedValue<?> namedValue =
                            event.getNamedValue();
                        Integer value = (Integer)namedValue.getValue();
                        display(new Date().toString() + " : " +
                            virtualDevice.getEndpointId() +
                            " : onChange : \"maxThreshold\"=" + value);

                        humiditySensor.setMaxThreshold(value);
                    }
                });


            /*
             * Create a handler for errors that may be generated when trying
             * to set values on the virtual device. The same callback is used
             * for both virtual devices.
             */
            final VirtualDevice.ErrorCallback<VirtualDevice> errorCallback =
                new VirtualDevice.ErrorCallback<VirtualDevice>() {
                    public void onError(VirtualDevice.ErrorEvent<VirtualDevice> event) {
                        VirtualDevice device = event.getVirtualDevice();
                        display(new Date().toString() + " : onError : " +
                            device.getEndpointId() +
                            " : \"" + event.getMessage() + "\"");
                    }
                };

            virtualTemperatureSensor.setOnError(errorCallback);
            virtualHumiditySensor.setOnError(errorCallback);

            display("\nCreated virtual humidity sensor " + humiditySensorEndpointId);
            display("Created virtual temperature sensor " + temperatureSensorEndpointId);
            display("\n\tPress enter to exit.\n");

            //
            // Which sensor data is processed depends on whether or not policies are used.
            // Compare the code in the processSensorData method of the WithPolicies class
            // to that of the WithoutPolicies class.
            //
            final MainLogic mainLogic = usePolicy
                    ? new WithDevicePolicies(humiditySensor, virtualHumiditySensor,
                                       temperatureSensor, virtualTemperatureSensor)
                    :  new WithoutDevicePolicies(humiditySensor, virtualHumiditySensor,
                                        temperatureSensor, virtualTemperatureSensor);

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
        } catch (ClassCastException e) {
            e.printStackTrace();
        } catch (Throwable e) {
            // catching Throwable, not Exception:
            // could be java.lang.NoClassDefFoundError
            // which is not Exception

            displayException(e);
            if (isUnderFramework) throw new RuntimeException(e);
        } finally {
            // Dispose of the device client
            try {
                if (gatewayDevice != null) gatewayDevice.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static abstract class MainLogic {

        protected final HumiditySensor humiditySensor;
        protected final VirtualDevice virtualHumiditySensor;
        protected final TemperatureSensor temperatureSensor;
        protected final VirtualDevice virtualTemperatureSensor;

        protected MainLogic(HumiditySensor humiditySensor,
                            VirtualDevice virtualHumiditySensor,
                            TemperatureSensor temperatureSensor,
                            VirtualDevice virtualTemperatureSensor) {
            this.humiditySensor = humiditySensor;
            this.virtualHumiditySensor = virtualHumiditySensor;
            this.temperatureSensor = temperatureSensor;
            this.virtualTemperatureSensor = virtualTemperatureSensor;
        }

        // This method allows for differentiation between the processing
        // of sensor data when policies are in use from when policies are
        // not in use.
        public abstract void processSensorData();
    }


    private static class WithoutDevicePolicies extends MainLogic {

        //
        //Create an Alert for max/min temperature threshold and another
        //for maximum humidity threshold. Alerts do not have to be
        //created new each time an alerting event occurs.
        //
        private final Alert tooHotAlert;
        private final Alert tooColdAlert;
        private final Alert tooHumidAlert;


        // A callback for receiving an error if an alert fails to be sent.
        // If necessary, there could be a different ErrorCallback for each alert.
        // For the purposes of this sample, one callback will do.
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

        // Flags to ensure an alert is only generated when the threshold is crossed.
        private boolean humidityAlerted = false;
        private boolean tempAlerted = false;

        // Flag to know whether the temperature sensor was off and is now on.
        private boolean wasOff = true;

        // The previous minimum temperature.
        // If the new minimum temperature is less than the previous,
        // update "minTemp" in the virtual TemperatureSensor
        private double prevMinTemp = Double.MAX_VALUE;

        // The previous maximum temperature.
        // If the new maximum temperature is greater than the previous,
        // update "maxTemp" in the virtual TemperatureSensor
        private double prevMaxTemp = Double.MIN_VALUE;

        private WithoutDevicePolicies(HumiditySensor humiditySensor,
                                      VirtualDevice virtualHumiditySensor,
                                      TemperatureSensor temperatureSensor,
                                      VirtualDevice virtualTemperatureSensor) {
            super(humiditySensor, virtualHumiditySensor, temperatureSensor, virtualTemperatureSensor);

            tooHotAlert = virtualTemperatureSensor
                    .createAlert("urn:com:oracle:iot:device:temperature_sensor:too_hot");
            tooHotAlert.setOnError(alertFailed);

            tooColdAlert = virtualTemperatureSensor
                    .createAlert("urn:com:oracle:iot:device:temperature_sensor:too_cold");
            tooColdAlert.setOnError(alertFailed);

            tooHumidAlert = virtualHumiditySensor
                    .createAlert("urn:com:oracle:iot:device:humidity_sensor:too_humid");
            tooHumidAlert.setOnError(alertFailed);

        }

        //
        // When running the sample without policies, the sample must do more work.
        // The sample has to update minTemp and maxTemp, and generate alerts if
        // the sensor values exceed, or fall below, thresholds. This logic can
        // be handled by the client library with the right set of policies in
        // place. Compare this method to that of the WithPolicies inner class.
        //
        @Override
        public void processSensorData() {

            int humidity = humiditySensor.getHumidity();

            StringBuilder consoleMessage = new
                    StringBuilder(new Date().toString())
                    .append(" : ")
                    .append(virtualHumiditySensor.getEndpointId())
                    .append(" : Set   : ")
                    .append("\"humidity\"=")
                    .append(humidity);

            // Set the virtualilzed humidity sensor value in an 'update'. If
            // the humidity maxThreshold has changed, that maxThreshold attribute
            // will be added to the update (see the block below). A call to
            // 'finish' will commit the update.
            virtualHumiditySensor.update()
                    .set("humidity", humidity);

            display(consoleMessage.toString());

            // Commit the update.
            virtualHumiditySensor.finish();

            int humidityThreshold = humiditySensor.getMaxThreshold();
            if (humidity > humidityThreshold) {
                if (!humidityAlerted) {
                    humidityAlerted = true;
                    consoleMessage = new StringBuilder(new Date().toString())
                            .append(" : ")
                            .append(virtualHumiditySensor.getEndpointId())
                            .append(" : Alert : \"")
                            .append("humidity")
                            .append("\"=")
                            .append(humidity);
                    display(consoleMessage.toString());
                    tooHumidAlert
                            .set("humidity", humidity)
                            .raise();
                }
            } else {
                humidityAlerted = false;
            }

            if (temperatureSensor.isPoweredOn()) {

                //
                // Update the virtual device to reflect the new values.
                //
                final double temperature =
                        temperatureSensor.getTemp();
                final String unit =
                        temperatureSensor.getUnit();
                final double minTemp =
                        temperatureSensor.getMinTemp();
                final double maxTemp =
                        temperatureSensor.getMaxTemp();

                consoleMessage = new StringBuilder(new Date().toString())
                        .append(" : ")
                        .append(virtualTemperatureSensor.getEndpointId())
                        .append(" : Set   : ");

                virtualTemperatureSensor.update();

                // Data model has temp range as [-20,80]. If data is out
                // of range, do not set the attribute in the virtual device.
                // Note: without this check, the set would throw an
                // IllegalArgumentException.
                if (-20 < temperature && temperature < 80) {
                    consoleMessage
                            .append("\"temp\"=")
                            .append(temperature);
                    virtualTemperatureSensor.set("temp", temperature);
                }

                // There is no need to set startTime or units if the value has not changed.
                if (wasOff) {
                    tempAlerted = false;
                    wasOff = false;

                    Date restartTime =
                            temperatureSensor.getStartTime();
                    consoleMessage
                            .append(",\"unit\"=").append(unit)
                            .append(",\"startTime\"=").append(restartTime);

                    virtualTemperatureSensor
                            .set("startTime", restartTime)
                            .set("unit", unit);
                }

                int introLength = consoleMessage.length();

                // There is no need to set minTemp if the value has not changed.
                if (minTemp != prevMinTemp) {
                    prevMinTemp = minTemp;
                    consoleMessage.append(",\"minTemp\"=").append(minTemp);
                    virtualTemperatureSensor.set("minTemp", minTemp);
                }

                // There is no need to set maxTemp if the value has not changed.
                if (maxTemp != prevMaxTemp) {
                    prevMaxTemp = maxTemp;
                    consoleMessage.append(",\"maxTemp\"=").append(maxTemp);
                    virtualTemperatureSensor.set("maxTemp", maxTemp);
                }

                display(consoleMessage.toString());

                // finish() commits the update.
                virtualTemperatureSensor.finish();

                final int minThreshold = temperatureSensor.getMinThreshold();
                final int maxThreshold = temperatureSensor.getMaxThreshold();
                if (maxThreshold < temperature) {
                    if (!tempAlerted) {
                        tempAlerted = true;

                        consoleMessage = new StringBuilder(new Date().toString())
                                .append(" : ")
                                .append(virtualTemperatureSensor.getEndpointId())
                                .append(" : Alert : ")
                                .append("\"temp\"=")
                                .append(temperature)
                                .append(",\"maxThreshold\"=")
                                .append(maxThreshold)
                                .append(",\"unit\"=")
                                .append(unit);

                        display(consoleMessage.toString());

                        tooHotAlert
                                .set("temp", temperature)
                                .set("maxThreshold", maxThreshold)
                                .set("unit", unit)
                                .raise();
                    }
                } else if (temperature < minThreshold) {
                    if (!tempAlerted) {
                        tempAlerted = true;
                        consoleMessage = new StringBuilder(new Date().toString())
                                .append(" : ")
                                .append(virtualTemperatureSensor.getEndpointId())
                                .append(" : Alert : ")
                                .append("\"temp\"=")
                                .append(temperature)
                                .append(",\"minThreshold\"=")
                                .append(minThreshold)
                                .append(",\"unit\"=")
                                .append(unit);

                        display(consoleMessage.toString());

                        tooColdAlert
                                .set("temp", temperature)
                                .set("minThreshold", minThreshold)
                                .set("unit", unit)
                                .raise();
                    }
                } else {
                    tempAlerted = false;
                }

            } else {
                wasOff = true;
            }
        }
    }

    private static class WithDevicePolicies extends MainLogic {

        private boolean wasOff = true;

        private WithDevicePolicies(HumiditySensor humiditySensor,
                                   VirtualDevice virtualHumiditySensor,
                                   TemperatureSensor temperatureSensor,
                                   VirtualDevice virtualTemperatureSensor) {
            super(humiditySensor, virtualHumiditySensor, temperatureSensor, virtualTemperatureSensor);
        }

        //
        // When running with policies, we only need to "offer" the
        // temperature and humidity values. Policies can be set on the
        // server to compute max and min temp, generate alerts, and
        // filter bad data - all of which have to be handled by the
        // sample code when running without policies.
        // Compare this implementation of processSensorData to that
        // of the WithoutPolicies inner class.
        //
        @Override
        public void processSensorData() {

            int humidity = humiditySensor.getHumidity();

            StringBuilder consoleMessage = new
                    StringBuilder(new Date().toString())
                    .append(" : ")
                    .append(virtualHumiditySensor.getEndpointId())
                    .append(" : Offer : \"humidity\"=")
                    .append(humidity);

            display(consoleMessage.toString());

            virtualHumiditySensor.offer("humidity", humidity);

            if (temperatureSensor.isPoweredOn()) {

                //
                // Update the virtual device to reflect the new values.
                //
                final double temperature =
                        temperatureSensor.getTemp();

                consoleMessage = new StringBuilder(new Date().toString())
                        .append(" : ")
                        .append(virtualTemperatureSensor.getEndpointId())
                        .append(" : Offer : ")
                        .append("\"temp\"=")
                        .append(temperature);


                virtualTemperatureSensor
                        .update()
                        .offer("temp", temperature);

                if (wasOff) {
                    wasOff = false;
                    final Date startTime = new Date();
                    consoleMessage
                            .append("\",startTime\"=")
                            .append(startTime);

                    // If there is no policy for the startTime attribute,
                    // the call to "offer" works just as a call to "set".
                    virtualTemperatureSensor
                            .offer("startTime", startTime);
                }

                display(consoleMessage.toString());

                virtualTemperatureSensor
                        .finish();

            } else {
                wasOff = true;
            }

        }
    }

    private static void showUsage() {
        Class<?> thisClass = new Object() { }.getClass().getEnclosingClass();
        display("Usage: \n"
                + "java " + thisClass.getName()
                + " <trusted assets file> <trusted assets password> " +
                " [<temperature sensor activation id> [<humidity sensor activation id>]]\n");
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
