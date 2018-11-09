/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.sample.ext;

import com.oracle.iot.client.device.DirectlyConnectedDevice;
import com.oracle.iot.client.device.util.RequestDispatcher;
import com.oracle.iot.client.device.util.RequestHandler;
import com.oracle.iot.client.message.DataMessage;
import com.oracle.iot.client.message.RequestMessage;
import com.oracle.iot.client.message.ResponseMessage;
import com.oracle.iot.client.message.StatusCode;
import com.oracle.iot.client.message.AlertMessage;

import com.oracle.iot.sample.HumiditySensor;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.UUID;

/**
 * This sample presents a simple sensor as a directly connected device
 * to the IoT server. The sample is single threaded and messages are
 * created by the sample and delivered to the server via the
 * {@code com.oracle.iot.client.device.DirectlyConnectedDevice} API.
 * The simple sensor is polled every half second.
 * <p>
 *     The sample runs in two modes: with policies or without policies. By
 *     default, the sample runs without policies. To enable the use of
 *     policies, set the property {@code com.oracle.iot.sample.use_policy}.
 *  <p>
 *      When running with policies, the device policies must be uploaded to
 *      the server. The sample reads the sensor data, creates a data message,
 *      and calls the {@code DirectlyConnectedDevice#offer} method,
 *      which applies the policies. Data and alert messages are sent
 *      to the server depending on the configuration of the device policies.
 *  <p>
 *      When running without policies, the sample reads the sensor data,
 *      creates data and alert messages, and calls the
 *      {@code DirectlyConnectedDevice#send} method, which results in a
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
     * humiditySensor polling interval before sending next readings.
     * Could be configured using {@code com.oracle.iot.sample.sensor_polling_interval} property
     */
    private static final long SENSOR_POLLING_INTERVAL =
        Long.getLong("com.oracle.iot.sample.sensor_polling_interval", 5000);

    private static final boolean useLongPolling =
        !Boolean.getBoolean("com.oracle.iot.client.disable_long_polling");

    public static boolean isUnderFramework = false;
    public static boolean exiting = false;

    public static void main(String[] args) {

        final HumiditySensor humiditySensor =
                new HumiditySensor(UUID.randomUUID().toString());

        DirectlyConnectedDevice directlyConnectedDevice = null;

        try {
            if (args.length < 2) {
                throw new IllegalArgumentException("\nIncorrect number of arguments.");
            }

            // Initialize device client
            directlyConnectedDevice = new DirectlyConnectedDevice(args[0], args[1]);

            // Activate the device
            if (!directlyConnectedDevice.isActivated()) {
                display("\nActivating...");
                directlyConnectedDevice.activate(
                        "urn:com:oracle:iot:device:humidity_sensor",
                        "urn:oracle:iot:dcd:capability:device_policy"
                );
            }

            // Create the directly-connected device instance
            display("\nCreating the directly-connected device instance " +
                    directlyConnectedDevice.getEndpointId());

            // Register handler for attributes of the device model. The humidity humiditySensor
            // device model only has maxThreshold for a writable attribute.
            final RequestDispatcher requestDispatcher = RequestDispatcher.getInstance();
            requestDispatcher.registerRequestHandler(directlyConnectedDevice.getEndpointId(),
                "deviceModels/urn:com:oracle:iot:device:humidity_sensor/attributes",
                new RequestHandler() {
                    public ResponseMessage handleRequest(RequestMessage requestMessage) {
                        return handleHumidityAttributeRequest(requestMessage, humiditySensor);
                    }
                }
            );

            display("\n\tPress enter to exit.\n");

            //
            // Which sensor data is processed depends on whether or not policies are used.
            // Compare the code in the processSensorData method of the WithPolicies class
            // to that of the WithoutPolicies class.
            //
            final MainLogic mainLogic = usePolicy
                    ? new WithDevicePolicies(directlyConnectedDevice, humiditySensor)
                    :  new WithoutDevicePolicies(directlyConnectedDevice, humiditySensor);

            mainLoop:
            for (; ; ) {

                mainLogic.processSensorData();


                // Receive and dispatch requests from the server
                //
                // This loop is constructed so that it will Wait SENSOR_POLLING_INTERVAL
                // seconds before sending the next reading. If there is a request message,
                // the message is dispatched to the handler and the response message is sent.
                //
                //
                long receiveTimeout = SENSOR_POLLING_INTERVAL;
                while (receiveTimeout > 0) {

                    if (!isUnderFramework) {
                        // User pressed the enter key while sleeping, exit.
                        if (System.in.available() > 0) {
                            exiting = true;
                            break;
                        }
                    }

                    long before = System.currentTimeMillis();
                    RequestMessage requestMessage = directlyConnectedDevice.receive(receiveTimeout);

                    if (requestMessage != null) {
                        ResponseMessage responseMessage =
                                requestDispatcher.dispatch(requestMessage);
                        directlyConnectedDevice.send(responseMessage);

                        // return to main loop so that the updated values can be
                        // sent to the server.
                        break;
                    }

                    /*
                     * The receive call and the processing of a server request
                     * may take less than the device polling interval so reduce
                     * the receive timeout by the time taken.
                     */
                    receiveTimeout -= System.currentTimeMillis() - before;

                    if (!useLongPolling && receiveTimeout > 0) {
                        Thread.sleep(receiveTimeout);
                        break;
                    }
                }

                if (exiting) {
                    break;
                }
            }
        } catch (Throwable e) {
            // could be java.lang.NoClassDefFoundError
            displayException(e);
            if (isUnderFramework) {
                throw new RuntimeException(e);
            }
        } finally {
            // Dispose of the device client
            if (directlyConnectedDevice != null) {
                try {
                    directlyConnectedDevice.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    // Handle deviceModels/urn:com:oracle:iot:device:humidity_sensor/attributes[/maxThreshold]
    private static ResponseMessage handleHumidityAttributeRequest(RequestMessage requestMessage,
                                                                  HumiditySensor humiditySensor) {

        try {

            // The request body of a PUT looks like {"value":70}, and the attribute is suffixed to the path
            // The request body of a POST looks like {"maxThreshold":70}
            final String jsonRequestBody = new String(requestMessage.getBody(), "UTF-8");
            final JSONObject jsonObject = new JSONObject(jsonRequestBody);

            int value = jsonObject.has("value") ? jsonObject.getInt("value") : jsonObject.getInt("maxThreshold");
            humiditySensor.setMaxThreshold(value);

            final StringBuilder consoleMessage = new StringBuilder(new Date().toString())
                    .append(" : ")
                    .append(requestMessage.getDestination())
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

        // The previous maximum humidity threshold.
        // If the new maximum threshold is not equal to the previous,
        // the new value will be sent in the data message.
        protected int prevMaxHumidityThreshold;

        protected final DirectlyConnectedDevice directlyConnectedDevice;
        protected final HumiditySensor humiditySensor;

        protected MainLogic(DirectlyConnectedDevice directlyConnectedDevice, HumiditySensor humiditySensor) {
            this.humiditySensor = humiditySensor;
            this.directlyConnectedDevice = directlyConnectedDevice;
            prevMaxHumidityThreshold = humiditySensor.getMaxThreshold();
        }

        // This method allows for differentiation between the processing
        // of sensor data when policies are in use from when policies are
        // not in use.
        public abstract void processSensorData() throws IOException, GeneralSecurityException;
    }


    private static class WithoutDevicePolicies extends MainLogic {

        // Flags to ensure an alert is only generated when the threshold is crossed.
        private boolean humidityAlerted = false;

        private WithoutDevicePolicies(DirectlyConnectedDevice directlyConnectedDevice,
                                      HumiditySensor humiditySensor) {
            super(directlyConnectedDevice, humiditySensor);
        }

        //
        // When running the sample without policies, the sample must do more work.
        // The sample has to generate alerts if the sensor value exceeds the
        // threshold. This logic can be handled by the client library with the
        // right set of policies in place. Compare this method to that of the
        // WithPolicies inner class.
        //
        @Override
        public void processSensorData() throws IOException, GeneralSecurityException {

            final int humidity = humiditySensor.getHumidity();

            // Send data from the indirectly-connected device
            final DataMessage.Builder dataMessageBuilder =
                    new DataMessage.Builder();

            StringBuilder consoleMessage =
                    new StringBuilder(new Date().toString());

            dataMessageBuilder.format("urn:com:oracle:iot:device:humidity_sensor:attributes")
                    .source(directlyConnectedDevice.getEndpointId())
                    .dataItem("humidity", humidity);
            consoleMessage.append(" : ")
                    .append(directlyConnectedDevice.getEndpointId())
                    .append(" : Data : \"humidity\"=")
                    .append(humidity);

            // Send humidityThreshold if it has changed
            // Note that this is handled automatically when using device virtualization.
            int humidityThreshold = humiditySensor.getMaxThreshold();
            if (prevMaxHumidityThreshold != humidityThreshold) {
                prevMaxHumidityThreshold = humidityThreshold;
                dataMessageBuilder.dataItem("maxThreshold", humidityThreshold);
                consoleMessage.append(",\"maxThreshold\"=")
                        .append(humidityThreshold);
            }

            DataMessage dataMessage = dataMessageBuilder.build();

            display(consoleMessage.toString());
            directlyConnectedDevice.send(dataMessage);

            if (humidity > humidityThreshold) {
                if (!humidityAlerted) {
                    try {
                        humidityAlerted = true;
                        final AlertMessage alertMessage =
                                new AlertMessage.Builder()
                                        .format("urn:com:oracle:iot:device:humidity_sensor:too_humid")
                                        .source(directlyConnectedDevice.getEndpointId())
                                        .description("max threshold crossed")
                                        .dataItem("humidity", humidity)
                                        .severity(AlertMessage.Severity.CRITICAL).build();

                        consoleMessage = new StringBuilder(new Date().toString())
                                .append(" : ")
                                .append(directlyConnectedDevice.getEndpointId())
                                .append(" : Alert : \"humidity\"=")
                                .append(humidity);

                        display(consoleMessage.toString());
                        directlyConnectedDevice.send(alertMessage);
                    } catch (Exception e) {
                        System.err.println("could not raise alert: " + e.getMessage());
                    }
                }
            } else {
                humidityAlerted = false;
            }
        }
    }

    private static class WithDevicePolicies extends MainLogic {

        private WithDevicePolicies(DirectlyConnectedDevice directlyConnectedDevice,
                                   HumiditySensor humiditySensor) {
            super(directlyConnectedDevice, humiditySensor);
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
        public void processSensorData() throws IOException, GeneralSecurityException {

            final int humidity = humiditySensor.getHumidity();

            // Send data from the indirectly-connected device
            final DataMessage.Builder dataMessageBuilder =
                    new DataMessage.Builder();

            StringBuilder consoleMessage =
                    new StringBuilder(new Date().toString());

            dataMessageBuilder.format("urn:com:oracle:iot:device:humidity_sensor:attributes")
                    .source(directlyConnectedDevice.getEndpointId())
                    .dataItem("humidity", humidity);

            consoleMessage.append(" : ")
                    .append(directlyConnectedDevice.getEndpointId())
                    .append(" : Data : \"humidity\"=")
                    .append(humidity);

            // Send humidityThreshold if it has changed.
            // Note that this is handled automatically when using device virtualization.
            int humidityThreshold = humiditySensor.getMaxThreshold();
            if (prevMaxHumidityThreshold != humidityThreshold) {
                prevMaxHumidityThreshold = humidityThreshold;
                dataMessageBuilder.dataItem("maxThreshold", humidityThreshold);
                consoleMessage.append(",\"maxThreshold\"=")
                        .append(humidityThreshold);
            }

            DataMessage dataMessage = dataMessageBuilder.build();

            display(consoleMessage.toString());
            directlyConnectedDevice.offer(dataMessage);

        }
    }

    private static void showUsage() {
        Class<?> thisClass = new Object() { }.getClass().getEnclosingClass();
        display("Usage:\n"
                + "java " + thisClass.getName()
                + " <trusted assets file> <trusted assets password>\n");
    }

    private static void display(String string) {
        System.out.println(string);
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
