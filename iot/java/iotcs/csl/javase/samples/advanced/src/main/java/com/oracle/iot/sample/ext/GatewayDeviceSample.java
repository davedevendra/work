/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.sample.ext;

import com.oracle.iot.client.device.GatewayDevice;
import com.oracle.iot.client.device.util.MessageDispatcher;
import com.oracle.iot.client.device.util.RequestDispatcher;
import com.oracle.iot.client.device.util.RequestHandler;
import com.oracle.iot.client.message.AlertMessage;
import com.oracle.iot.client.message.DataMessage;
import com.oracle.iot.client.message.Message;
import com.oracle.iot.client.message.RequestMessage;
import com.oracle.iot.client.message.ResponseMessage;
import com.oracle.iot.client.message.StatusCode;
import com.oracle.iot.sample.HumiditySensor;
import com.oracle.iot.sample.TemperatureSensor;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This sample is a gateway that presents multiple simple sensors as indirectly
 * connected devices to the IoT server. The sample is multi-threaded and uses
 * the MessageDispatcher utility to send messages to the server, and to handle
 * resource requests from the server. The simple sensors are polled every half
 * second.
 * <p>
 *     The sample runs in two modes: with policies or without policies. By
 *     default, the sample runs without policies. To enable the use of
 *     policies, set the property {@code com.oracle.iot.sample.use_policy}.
 *  <p>
 *      When running with policies, the device policies must be uploaded to
 *      the server. The sample reads the humidity and temperature values,
 *      creates data messages, and calls the {@code MessageDispatcher#offer}
 *      method, which applies the policies. Data and alert messages are sent
 *      to the server depending on the configuration of the device policies.
 *  <p>
 *      When running without policies, the sample reads the sensor data,
 *      creates data messages and alerts, and calls the
 *      {@code VirtualDevice#queue} method, which results in the
 *      data messages being sent to the server. The sample itself must
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

        GatewayDevice gatewayDevice = null;
        MessageDispatcher messageDispatcher = null;

        try {
            if (args.length < 2) {
                display("\nIncorrect number of arguments.");
                throw new IllegalArgumentException("");
            }

            // Initialize device client
            gatewayDevice = new GatewayDevice(args[0], args[1]);

            // Activate the device
            if (!gatewayDevice.isActivated()) {
                // If the device has not been activated, connect to the server
                // using client-credentials and activate the client to
                // obtain the private key. The private key is then persisted.
                display("\nActivating...");
                gatewayDevice.activate("urn:oracle:iot:dcd:capability:device_policy");
            }

            final String gatewayEndpointId = gatewayDevice.getEndpointId();

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

            final HumiditySensor humiditySensor =
                new HumiditySensor(
                        args.length > 3
                                ? args[3]
                                : gatewayDevice.getEndpointId() + "_Sample_HS");


            String temperatureSensorEndpointId = null;
            final String humiditySensorEndpointId;

            // Register indirectly-connected devices
            Map<String,String> metaData = new HashMap<String, String>();
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
            // then restrict the sensor to this gateway. This means that
            // the sensor cannot be connected through other gateways.
            //
            final boolean humiditySensorRestricted = args.length > 3;
            humiditySensorEndpointId =
                        gatewayDevice.registerDevice(
                                humiditySensor.getHardwareId(),
                                metaData,
                                "urn:com:oracle:iot:device:humidity_sensor");


            //
            // This sample uses the MessageDispatcher utility for queuing and
            // dispatching messages, and for invoking the resource request
            // handlers.
            //
            messageDispatcher = MessageDispatcher.getMessageDispatcher(gatewayDevice);

            messageDispatcher.setOnError(new MessageDispatcher.ErrorCallback() {
                public void failed(List<Message> messages,
                        Exception exception) {
                    display(new Date().toString() + " : " +
                        gatewayEndpointId +
                        " : OnFailed : \"size\"=" + messages.size() +
                        "\"exception\"=" + exception.getMessage());
                }
            });

            // For each managed device, register a handler for incoming requests.
            final RequestDispatcher requestDispatcher = messageDispatcher.getRequestDispatcher();

            // Register a handler for urn:com:oracle:iot:device:temperature_sensor attributes
            // The only writable attributes are minThreshold and maxThreshold
            requestDispatcher.registerRequestHandler(
                    temperatureSensorEndpointId,
                    "deviceModels/urn:com:oracle:iot:device:temperature_sensor/attributes",
                    new RequestHandler() {
                        public ResponseMessage handleRequest(RequestMessage requestMessage) {
                            return handleTemperatureAttributeRequest(requestMessage, temperatureSensor);
                        }
                    });

            // Register a handler for urn:com:oracle:iot:device:temperature_sensor reset action
            requestDispatcher.registerRequestHandler(
                    temperatureSensorEndpointId,
                    "deviceModels/urn:com:oracle:iot:device:temperature_sensor/actions/reset",
                    new RequestHandler() {
                        public ResponseMessage handleRequest(RequestMessage requestMessage) {
                            return handleTemperatureSensorReset(requestMessage, temperatureSensor);
                        }
                    });

            // Register a handler for urn:com:oracle:iot:device:temperature_sensor power action
            requestDispatcher.registerRequestHandler(
                    temperatureSensorEndpointId,
                    "deviceModels/urn:com:oracle:iot:device:temperature_sensor/actions/power",
                    new RequestHandler() {
                        public ResponseMessage handleRequest(RequestMessage requestMessage) {
                            return handleTemperatureSensorPower(requestMessage, temperatureSensor);
                        }
                    });

            // Register a handler for urn:com:oracle:iot:device:humidity_sensor attributes
            // The only writable attribute is maxThreshold
            requestDispatcher.registerRequestHandler(
                    humiditySensorEndpointId,
                    "deviceModels/urn:com:oracle:iot:device:humidity_sensor/attributes",
                    new RequestHandler() {
                        public ResponseMessage handleRequest(RequestMessage requestMessage) {
                            return handleHumidityAttributeRequest(requestMessage, humiditySensor);
                        }
                    });

            display("\nCreated temperature sensor " + temperatureSensorEndpointId);
            display("Created humidity sensor " + humiditySensorEndpointId + "\n");
            display("\tPress enter to exit.\n");

            //
            // Which sensor data is processed depends on whether or not policies are used.
            // Compare the code in the processSensorData method of the WithPolicies class
            // to that of the WithoutPolicies class.
            //
            final MainLogic mainLogic = usePolicy
                    ? new WithDevicePolicies(messageDispatcher,
                                       humiditySensor, humiditySensorEndpointId,
                                       temperatureSensor, temperatureSensorEndpointId)
                    :  new WithoutDevicePolicies(messageDispatcher,
                                           humiditySensor, humiditySensorEndpointId,
                                           temperatureSensor, temperatureSensorEndpointId);

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
            // could be java.lang.NoClassDefFoundError
            // which is not Exception

            displayException(e);

            if (isUnderFramework) throw new RuntimeException(e);            
        } finally {
            try {
                // under framework, if messageDispatcher is not closed, 
                // transmitThread continues to run
                if (messageDispatcher != null) {
                    messageDispatcher.close();
                }
                // Dispose of the device client
                if (gatewayDevice != null) {
                    gatewayDevice.close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    // Handle deviceModels/urn:com:oracle:iot:device:temperature_sensor/attributes[/minThreshold | /maxThreshold]
    private static ResponseMessage handleTemperatureAttributeRequest(
            RequestMessage requestMessage,
            TemperatureSensor temperatureSensor) {

        final String endpointId = requestMessage.getDestination();
        final String path = requestMessage.getURL();
        final boolean poweredOn = temperatureSensor.isPoweredOn();
        StatusCode statusCode = poweredOn ? StatusCode.OK : StatusCode.CONFLICT;
        try {

            // The request body of a PUT looks like {"value":70}, and the attribute is suffixed to the path
            // The request body of a POST looks like {"minThreshold":-5, "maxThreshold":70}
            final String jsonRequestBody = new String(requestMessage.getBody(), "UTF-8");
            final JSONObject jsonObject = new JSONObject(jsonRequestBody);

            if (jsonObject.has("value")) {
                int value = jsonObject.getInt("value");
                if (path.endsWith("minThreshold")) {
                    display(new Date().toString() + " : " +
                            endpointId + " : Request : \"minThreshold\"=" + value +
                            (poweredOn ? "" : " ignored. Device is powered off"));
                    if (poweredOn) temperatureSensor.setMinThreshold(value);

                } else {
                    display(new Date().toString() + " : " +
                            endpointId + " : Request : \"maxThreshold\"=" + value +
                            (poweredOn ? "" : " ignored. Device is powered off"));
                    if (poweredOn) temperatureSensor.setMaxThreshold(value);
                }

            } else {
                if (jsonObject.has("minThreshold")) {
                    int value = jsonObject.getInt("minThreshold");
                    display(new Date().toString() + " : " +
                            endpointId + " : Request : \"minThreshold\"=" + value +
                            (poweredOn ? "" : " ignored. Device is powered off"));
                    if (poweredOn) temperatureSensor.setMinThreshold(value);
                }
                if (jsonObject.has("maxThreshold")) {
                    int value = jsonObject.getInt("maxThreshold");
                    display(new Date().toString() + " : " +
                            endpointId + " : Request : \"maxThreshold\"=" + value +
                            (poweredOn ? "" : " ignored. Device is powered off"));
                    if (poweredOn) temperatureSensor.setMaxThreshold(value);
                }
            }


            return new ResponseMessage.Builder(requestMessage)
                    .statusCode(statusCode)
                    .build();

        } catch (UnsupportedEncodingException ue) {
            // UTF-8 is required, so this won't happen,
            // but throw an exception to make the compiler happy.
            throw new RuntimeException(ue);
        } catch (JSONException je) {
            je.printStackTrace();
            return new ResponseMessage.Builder(requestMessage)
                    .statusCode(StatusCode.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    // Handle deviceModels/urn:com:oracle:iot:device:temperature_sensor/action/reset
    private static ResponseMessage handleTemperatureSensorReset(
            RequestMessage requestMessage,
            TemperatureSensor temperatureSensor) {

        final String endpointId = requestMessage.getDestination();

        if (temperatureSensor.isPoweredOn()) {
            display(new Date().toString() + " : " +
                    endpointId + " : Request : \"reset\"");

            // the reset action doesn't take any arguments, so there is no need to
            // get the request message body as is done in handleTemperatureSensorPower
            temperatureSensor.reset();

            return new ResponseMessage.Builder(requestMessage)
                    .statusCode(StatusCode.OK)
                    .build();
        } else {
            display(new Date().toString() + " : " +
                    endpointId + " : Request : \"reset\" ignored. Device powered off.");
            return new ResponseMessage.Builder(requestMessage)
                    .statusCode(StatusCode.CONFLICT)
                    .build();
        }
    }

    // Handle deviceModels/urn:com:oracle:iot:device:temperature_sensor/action/power {"value":true} | {"value":false}
    private static ResponseMessage handleTemperatureSensorPower(
            RequestMessage requestMessage,
            TemperatureSensor temperatureSensor) {

        final String endpointId = requestMessage.getDestination();
        try {

            // The request body looks like {"value":true} or {"value" : false}
            final String jsonRequestBody = new String(requestMessage.getBody(), "UTF-8");
            final JSONObject jsonObject = new JSONObject(jsonRequestBody);

            Boolean bvalue = jsonObject.getBoolean("value");
            boolean on = bvalue.booleanValue();
            boolean isOn = temperatureSensor.isPoweredOn();
            display(new Date().toString() + " : " +
                    endpointId + " : Request : \"power\"=" + on);
            if (on != isOn) {
                temperatureSensor.power(on);
            }

            return new ResponseMessage.Builder(requestMessage)
                    .statusCode(StatusCode.OK)
                    .build();

        } catch (UnsupportedEncodingException ue) {
            // UTF-8 is required, so this won't happen,
            // but throw an exception to make the compiler happy.
            throw new RuntimeException(ue);
        } catch (JSONException je) {
            je.printStackTrace();
            return new ResponseMessage.Builder(requestMessage)
                    .statusCode(StatusCode.INTERNAL_SERVER_ERROR)
                    .build();
        }

    }

    // Handle deviceModels/urn:com:oracle:iot:device:humidity_sensor/attributes[/maxThreshold]
    private static ResponseMessage handleHumidityAttributeRequest(
            RequestMessage requestMessage,
            HumiditySensor humiditySensor) {

        final String endpointId = requestMessage.getDestination();
        try {

            // The request body of a PUT looks like {"value":70}, and the attribute is suffixed to the path
            // The request body of a POST looks like {"maxThreshold":70}
            final String jsonRequestBody = new String(requestMessage.getBody(), "UTF-8");
            final JSONObject jsonObject = new JSONObject(jsonRequestBody);

            int value = jsonObject.has("value") ? jsonObject.getInt("value") : jsonObject.getInt("maxThreshold");
            humiditySensor.setMaxThreshold(value);

            final StringBuilder consoleMessage = new StringBuilder(new Date().toString())
                    .append(" : ")
                    .append(endpointId)
                    .append(" : Request : ")
                    .append("\"maxThreshold\"=")
                    .append(value);

            display(consoleMessage.toString());

            return new ResponseMessage.Builder(requestMessage)
                    .statusCode(StatusCode.OK)
                    .build();

        } catch (UnsupportedEncodingException ue) {
            // UTF-8 is required, so this won't happen,
            // but throw an exception to make the compiler happy.
            throw new RuntimeException(ue);
        } catch (JSONException je) {
            je.printStackTrace();
            return new ResponseMessage.Builder(requestMessage)
                    .statusCode(StatusCode.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    private static abstract class MainLogic {

        // Flag to know whether the temperature sensor was off and is now on.
        protected boolean wasOff = false;

        // The previous maximum humidity threshold.
        // If the new maximum threshold is not equal to the previous,
        // the new value will be sent in the data message.
        protected int prevMaxHumidityThreshold;

        // The previous minimum threshold for the temperature sensor.
        // If the new minimum temperature threshold is not equal to the previous,
        // the new value will be sent in the data message.
        protected int prevMinTempThreshold;

        // The previous maximum threshold for the temperature sensor.
        // If the new maximum temperature threshold is not equal to the previous,
        // the new value will be sent in the data message.
        protected int prevMaxTempThreshold;

        protected final MessageDispatcher messageDispatcher;
        protected final HumiditySensor humiditySensor;
        protected final String humiditySensorEndpointId;
        protected final TemperatureSensor temperatureSensor;
        protected final String temperatureSensorEndpointId;

        protected MainLogic(MessageDispatcher messageDispatcher,
                            HumiditySensor humiditySensor,
                            String humiditySensorEndpointId,
                            TemperatureSensor temperatureSensor,
                            String temperatureSensorEndpointId) {

            this.messageDispatcher = messageDispatcher;
            this.humiditySensor = humiditySensor;
            this.humiditySensorEndpointId = humiditySensorEndpointId;
            this.temperatureSensor = temperatureSensor;
            this.temperatureSensorEndpointId = temperatureSensorEndpointId;

            prevMaxHumidityThreshold = humiditySensor.getMaxThreshold();
            prevMinTempThreshold = temperatureSensor.getMinThreshold();
            prevMaxTempThreshold = temperatureSensor.getMaxThreshold();
        }

        // This method allows for differentiation between the processing
        // of sensor data when policies are in use from when policies are
        // not in use.
        public abstract void processSensorData();
    }


    private static class WithoutDevicePolicies extends MainLogic {

        // Flags to ensure an alert is only generated when the threshold is crossed.
        private boolean humidityAlerted = false;
        private boolean tempAlerted = false;

        // The previous minimum temperature.
        // If the new minimum temperature is less than the previous,
        // update "minTemp" in the virtual TemperatureSensor
        private double prevMinTemp = Double.MAX_VALUE;

        // The previous maximum temperature.
        // If the new maximum temperature is greater than the previous,
        // update "maxTemp" in the virtual TemperatureSensor
        private double prevMaxTemp = Double.MIN_VALUE;


        private WithoutDevicePolicies(MessageDispatcher messageDispatcher,
                                HumiditySensor humiditySensor,
                                String humiditySensorEndpointId,
                                TemperatureSensor temperatureSensor,
                                String temperatureSensorEndpointId) {
            super(messageDispatcher, humiditySensor, humiditySensorEndpointId,
                    temperatureSensor, temperatureSensorEndpointId);
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

            // Send data from the indirectly-connected device
            final int humidity = humiditySensor.getHumidity();
            DataMessage.Builder humidityMessageBuilder =
                    new DataMessage.Builder()
                            .format("urn:com:oracle:iot:device:humidity_sensor:attributes")
                            .source(humiditySensorEndpointId)
                            .dataItem("humidity", humidity);

            StringBuilder consoleMessage = new StringBuilder(new Date().toString())
                    .append(" : ")
                    .append(humiditySensorEndpointId)
                    .append(" : Data : \"humidity\"=")
                    .append(humidity);

            // Only send maxThreshold if it has changed, or is not the default
            // Note that this is handled automatically when using device virtualization
            final int humidityThreshold = humiditySensor.getMaxThreshold();
            if (prevMaxHumidityThreshold != humidityThreshold) {
                humidityMessageBuilder.dataItem("maxThreshold", humidityThreshold);
                prevMaxHumidityThreshold = humidityThreshold;
                consoleMessage
                        .append(",\"maxThreshold\"=")
                        .append(humidityThreshold);
            }

            display(consoleMessage.toString());
            messageDispatcher.queue(humidityMessageBuilder.build());

            if (humidity > humidityThreshold) {
                if (!humidityAlerted) {
                    humidityAlerted = true;
                    final AlertMessage alertMessage =
                            new AlertMessage.Builder()
                                    .format("urn:com:oracle:iot:device:humidity_sensor:too_humid")
                                    .source(humiditySensorEndpointId)
                                    .description("max threshold crossed")
                                    .dataItem("humidity", humidity)
                                    .severity(AlertMessage.Severity.CRITICAL)
                                    .build();

                    consoleMessage = new StringBuilder(new Date().toString())
                            .append(" : ")
                            .append(humiditySensorEndpointId)
                            .append(" : Alert : \"humidity\"=")
                            .append(humidity);

                    display(consoleMessage.toString());
                    messageDispatcher.queue(alertMessage);
                }
            } else {
                humidityAlerted = false;
            }

            // if temperature sensor is powered off, do not queue a message for temp
            if (temperatureSensor.isPoweredOn()) {

                final double temperature = temperatureSensor.getTemp();
                final double minTemp = temperatureSensor.getMinTemp();
                final double maxTemp = temperatureSensor.getMaxTemp();
                final String unit = temperatureSensor.getUnit();

                com.oracle.iot.client.message.DataMessage.Builder temperatureMessageBuilder =
                        new com.oracle.iot.client.message.DataMessage.Builder()
                                .format("urn:com:oracle:iot:device:temperature_sensor:attributes")
                                .source(temperatureSensorEndpointId)
                                .dataItem("temp", temperature);

                consoleMessage = new StringBuilder(new Date().toString())
                        .append(" : ")
                        .append(temperatureSensorEndpointId)
                        .append(" : Data : \"temp\"=")
                        .append(temperature);

                if (wasOff) {
                    wasOff = false;
                    Date startTime = temperatureSensor.getStartTime();
                    consoleMessage
                            .append(",\"startTime\"=").append(startTime);
                    temperatureMessageBuilder
                            .dataItem("startTime", startTime.getTime());
                }

                if (minTemp != prevMinTemp) {
                    prevMinTemp = minTemp;
                    temperatureMessageBuilder
                            .dataItem("minTemp", minTemp);
                    consoleMessage
                            .append(",\"minTemp\"=")
                            .append(minTemp);
                }

                if (maxTemp != prevMaxTemp) {
                    prevMaxTemp = maxTemp;
                    temperatureMessageBuilder
                            .dataItem("maxTemp", maxTemp);
                    consoleMessage
                            .append(",\"maxTemp\"=")
                            .append(maxTemp);
                }

                // There is no need to set minThreshold if the value has not changed.
                // Note that this is handled automatically when using device virtualization
                final int minTempThreshold = temperatureSensor.getMinThreshold();
                if (minTempThreshold != prevMinTempThreshold) {
                    prevMinTempThreshold = minTempThreshold;
                    temperatureMessageBuilder
                            .dataItem("minThreshold", minTempThreshold);
                    consoleMessage
                            .append(",\"minThreshold\"=")
                            .append(minTempThreshold);
                }

                // There is no need to set maxThreshold if the value has not changed.
                // Note that this is handled automatically when using device virtualization
                final int maxTempThreshold = temperatureSensor.getMaxThreshold();
                if (maxTempThreshold != prevMaxTempThreshold) {
                    prevMaxTempThreshold = maxTempThreshold;
                    temperatureMessageBuilder
                            .dataItem("maxThreshold", maxTempThreshold);
                    consoleMessage
                            .append(",\"maxThreshold\"=")
                            .append(maxTempThreshold);
                }

                display(consoleMessage.toString());
                messageDispatcher.queue(temperatureMessageBuilder.build());

                if (temperature < minTempThreshold) {
                    if (!tempAlerted) {
                        tempAlerted = true;
                        final AlertMessage alertMessage = new AlertMessage.Builder()
                                .format("urn:com:oracle:iot:device:temperature_sensor:too_cold")
                                .source(temperatureSensorEndpointId)
                                .description("min threshold crossed")
                                .dataItem("temp", temperature)
                                .dataItem("unit", temperatureSensor.getUnit())
                                .severity(AlertMessage.Severity.CRITICAL)
                                .build();

                        consoleMessage = new StringBuilder(new Date().toString())
                                .append(" : ")
                                .append(temperatureSensorEndpointId)
                                .append(" : Alert : \"temp\"=")
                                .append(temperature)
                                .append(",\"minThreshold\"=")
                                .append(minTempThreshold);

                        display(consoleMessage.toString());
                        messageDispatcher.queue(alertMessage);
                    }

                } else if (temperature > maxTempThreshold) {
                    if (!tempAlerted) {
                        tempAlerted = true;
                        final AlertMessage alertMessage = new AlertMessage.Builder()
                                .format("urn:com:oracle:iot:device:temperature_sensor:too_hot")
                                .source(temperatureSensorEndpointId)
                                .description("max threshold crossed")
                                .dataItem("temp", temperature)
                                // unit is required by the too_hot format in the device model
                                .dataItem("unit", temperatureSensor.getUnit())
                                .severity(AlertMessage.Severity.CRITICAL)
                                .build();

                        consoleMessage = new StringBuilder(new Date().toString())
                                .append(" : ")
                                .append(temperatureSensorEndpointId)
                                .append(" : Alert : \"temp\"=")
                                .append(temperature)
                                .append(",\"maxThreshold\"=")
                                .append(maxTempThreshold);

                        display(consoleMessage.toString());
                        messageDispatcher.queue(alertMessage);
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

        private WithDevicePolicies(MessageDispatcher messageDispatcher,
                                   HumiditySensor humiditySensor,
                                   String humiditySensorEndpointId,
                                   TemperatureSensor temperatureSensor,
                                   String temperatureSensorEndpointId) {
            super(messageDispatcher, humiditySensor, humiditySensorEndpointId,
                    temperatureSensor, temperatureSensorEndpointId);
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

            // Send data from the indirectly-connected device
            final int humidity = humiditySensor.getHumidity();
            DataMessage.Builder humidityMessageBuilder =
                    new DataMessage.Builder()
                            .format("urn:com:oracle:iot:device:humidity_sensor:attributes")
                            .source(humiditySensorEndpointId)
                            .dataItem("humidity", humidity);

            StringBuilder consoleMessage = new StringBuilder(new Date().toString())
                    .append(" : ")
                    .append(humiditySensorEndpointId)
                    .append(" : Data : \"humidity\"=")
                    .append(humidity);

            // Send humidityThreshold if it has changed.
            // Note that this is handled automatically when using device virtualization.
            int humidityThreshold = humiditySensor.getMaxThreshold();
            if (prevMaxHumidityThreshold != humidityThreshold) {
                prevMaxHumidityThreshold = humidityThreshold;
                humidityMessageBuilder.dataItem("maxThreshold", humidityThreshold);
                consoleMessage.append(",\"maxThreshold\"=")
                        .append(humidityThreshold);
            }

            display(consoleMessage.toString());
            messageDispatcher.offer(humidityMessageBuilder.build());

            // if temperature sensor is powered off, do not queue a message for temp
            if (temperatureSensor.isPoweredOn()) {

                final double temperature = temperatureSensor.getTemp();

                DataMessage.Builder temperatureMessageBuilder =
                        new DataMessage.Builder()
                                .format("urn:com:oracle:iot:device:temperature_sensor:attributes")
                                .source(temperatureSensorEndpointId)
                                .dataItem("temp", temperature);

                consoleMessage = new StringBuilder(new Date().toString())
                        .append(" : ")
                        .append(temperatureSensorEndpointId)
                        .append(" : Data : \"temp\"=")
                        .append(temperature);

                if (wasOff) {
                    wasOff = false;
                    Date startTime = temperatureSensor.getStartTime();
                    consoleMessage
                            .append(",\"startTime\"=").append(startTime);
                    temperatureMessageBuilder
                            .dataItem("startTime", startTime.getTime());
                }

                // There is no need to set minThreshold if the value has not changed.
                // Note that this is handled automatically when using device virtualization.
                final int minTempThreshold = temperatureSensor.getMinThreshold();
                if (minTempThreshold != prevMinTempThreshold) {
                    prevMinTempThreshold = minTempThreshold;
                    temperatureMessageBuilder
                            .dataItem("minThreshold", minTempThreshold);
                    consoleMessage
                            .append(",\"minThreshold\"=")
                            .append(minTempThreshold);
                }

                // There is no need to set maxThreshold if the value has not changed.
                // Note that this is handled automatically when using device virtualization.
                final int maxTempThreshold = temperatureSensor.getMaxThreshold();
                if (maxTempThreshold != prevMaxTempThreshold) {
                    prevMaxTempThreshold = maxTempThreshold;
                    temperatureMessageBuilder
                            .dataItem("maxThreshold", maxTempThreshold);
                    consoleMessage
                            .append(",\"maxThreshold\"=")
                            .append(maxTempThreshold);
                }

                display(consoleMessage.toString());
                messageDispatcher.offer(temperatureMessageBuilder.build());

            } else {
                wasOff = true;
            }
        }
    }

    private static void showUsage() {
        Class<?> thisClass = new Object() { }.getClass().getEnclosingClass();
        display("Usage:\n"
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
        e.printStackTrace(System.out);
        showUsage();
    }
}
