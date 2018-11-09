/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.sample.ext;

import com.oracle.iot.client.StorageObject;
import com.oracle.iot.client.device.DirectlyConnectedDevice;
import com.oracle.iot.client.device.util.RequestDispatcher;
import com.oracle.iot.client.device.util.RequestHandler;
import com.oracle.iot.client.message.DataMessage;
import com.oracle.iot.client.message.RequestMessage;
import com.oracle.iot.client.message.ResponseMessage;
import com.oracle.iot.client.message.StatusCode;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

/*
 * This sample demonstrates the com.oracle.iot.client.StorageObject and related API.
 *
 * The sample uses the messaging API to simulate a motion activated camera.
 * An image is uploaded to the storage cloud every 5 seconds.
 * The sample also handles the "record" action.
 *
 * Because this sample runs in a single thread, the sample explicitly disables
 * long polling.
 *
 * Note that the code is Java SE 1.5 compatible.
 */
public class MotionActivatedCameraSample {

    // This sample runs in a single thread, and long polling should be disabled.
    static {
        System.setProperty("com.oracle.iot.client.disable_long_polling", "true");
    }

    private static final String MOTION_ACTIVATED_CAMERA_MODEL_URN =
            "urn:com:oracle:iot:device:motion_activated_camera";
    private static final String IMAGE_ATTRIBUTE = "image";
    private static final String IMAGE_TIME_ATTRIBUTE = "imageTime";

    /**
     * sensor polling interval before sending next readings.
     * Could be configured using {@code com.oracle.iot.sample.sensor_polling_interval} property
     */
    private static final long SENSOR_POLLING_INTERVAL = 
        Long.getLong("com.oracle.iot.sample.sensor_polling_interval", 5000);

    public static boolean isUnderFramework = false;
    public static boolean exiting = false;

    public static void main(String[] args) {

        if (args.length != 2) {
            display("\nIncorrect number of arguments.\n");
            showUsage();
            throw new IllegalArgumentException("Incorrect number of arguments.");
        }

        final File[] images = getFiles("./images", "jpeg");
        final File[] videos = getFiles("./videos", "mp4");

        // The directlyConnectedDevice variable needs to be final so that it can be used in the
        // handleRecordAction(requestMessage, directlyConnectedDevice, videos); call below.
        final DirectlyConnectedDevice directlyConnectedDevice;

        try {
            // Initialize the device client
            directlyConnectedDevice = new DirectlyConnectedDevice(args[0], args[1]);
        } catch (GeneralSecurityException gse) {
            displayException(gse);
            showUsage();
            // Note: Throwing RuntimeException from this try-catch block stops the
            // compiler complaining that 'directlyConnectedDevice' might not have been initialized.
            throw new RuntimeException(gse);
        }

        try {

            // Activate the device
            if (!directlyConnectedDevice.isActivated()) {
                display("\nActivating...");
                directlyConnectedDevice.activate(MOTION_ACTIVATED_CAMERA_MODEL_URN);
            }

            // Register handler for the "record" action of the motion_activated_camera device model. 
            final RequestDispatcher requestDispatcher = RequestDispatcher.getInstance();
            requestDispatcher.registerRequestHandler(directlyConnectedDevice.getEndpointId(),
                    "deviceModels/" + MOTION_ACTIVATED_CAMERA_MODEL_URN + "/actions",
                    new RequestHandler() {
                        public ResponseMessage handleRequest(RequestMessage requestMessage) {
                            return handleRecordAction(requestMessage, directlyConnectedDevice, videos);
                        }
                    }
            );


            display("\nCreated motion activated camera " + directlyConnectedDevice.getEndpointId());
            display("\n\tPress enter to exit.\n");

            int n = 0;

            for (;;) {

                final File image = images[n++ % images.length];

                final StorageObject storageObject =
                        directlyConnectedDevice.createStorageObject(
                                image.getName(),
                                "image/jpeg");

                storageObject.setInputStream(new FileInputStream(image));

                // Blocking!
                storageObject.sync();

                final DataMessage dataMessage = new DataMessage.Builder()
                        .source(directlyConnectedDevice.getEndpointId())
                        .format(MOTION_ACTIVATED_CAMERA_MODEL_URN + ":attributes")
                        .dataItem(IMAGE_ATTRIBUTE, storageObject.getURI())
                        .dataItem(IMAGE_TIME_ATTRIBUTE, new Date().getTime())
                        .build();

                StringBuilder consoleMessage = new StringBuilder(new Date().toString())
                        .append(" : ")
                        .append(directlyConnectedDevice.getEndpointId())
                        .append(" : Data : \"")
                        .append(IMAGE_ATTRIBUTE)
                        .append("\"=")
                        .append(storageObject.getURI());


                display(consoleMessage.toString());

                // Blocking!
                directlyConnectedDevice.send(dataMessage);

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

                    if (receiveTimeout > 0) {
                        Thread.sleep(receiveTimeout);
                        break;
                    }
                }

                if (exiting) {
                    break;
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

    // Handle deviceModels/urn:com:oracle:iot:device:motion_activated_camera/action/record
    private static ResponseMessage handleRecordAction(RequestMessage requestMessage,
                                                      final DirectlyConnectedDevice directlyConnectedDevice,
                                                      File[] videos) {

        JSONObject jsonObject = new JSONObject(requestMessage.getBodyString());
        int d = jsonObject.getInt("value");
        if (d <= 0) {
            return new ResponseMessage.Builder(requestMessage)
                    .statusCode(StatusCode.BAD_REQUEST)
                    .build();
        }

        // round to nearest increment of 15
        final int duration = (d + 14) / 15 * 15;
        // assumes videos.length > 0 and videos are 15 second increments.
        final File video = videos[Math.min(duration/15-1,videos.length-1)];

        final StringBuilder msg =
                new StringBuilder(new Date().toString())
                        .append(" : ")
                        .append(directlyConnectedDevice.getEndpointId())
                        .append(" : Call : record ")
                        .append(d);
        display(msg.toString());

        new Thread(new Runnable() {
            @Override
            public void run() {

                final Date startTime = new Date();

                // Simulate the time it takes to record the video by sleeping
                try {
                    Thread.sleep(duration);
                } catch (InterruptedException e) {
                    // Restore the interrupted status
                    Thread.currentThread().interrupt();
                }

                try {

                    final StorageObject storageObject =
                            directlyConnectedDevice.createStorageObject(
                                    video.getName(),
                                    "video/mp4");
                    storageObject.setInputStream(new FileInputStream(video));

                    // Blocking!
                    storageObject.sync();

                    final StringBuilder consoleMessage = new StringBuilder(new Date().toString())
                            .append(" : ")
                            .append(directlyConnectedDevice.getEndpointId())
                            .append(" : Data : \"")
                            .append("recording")
                            .append("\"=")
                            .append(storageObject.getURI());

                    final DataMessage dataMessage = new DataMessage.Builder()
                            .source(directlyConnectedDevice.getEndpointId())
                            .format(MOTION_ACTIVATED_CAMERA_MODEL_URN+":recording")
                            .dataItem("video", storageObject.getURI())
                            .dataItem("startTime", startTime.getTime())
                            .dataItem("duration", duration)
                            .build();

                    display(consoleMessage.toString());

                    // Blocking!
                    directlyConnectedDevice.send(dataMessage);

                } catch(IOException ioe) {
                    displayException(ioe);
                } catch (GeneralSecurityException gse) {
                    displayException(gse);
                }

            }
        }).start();

        return new ResponseMessage.Builder(requestMessage)
                .statusCode(StatusCode.OK)
                .build();
    }

    private static File[] getFiles(final String dir, final String ext) {

        final File directory = new File(dir);
        final File[] files = directory.isDirectory()
                ? directory.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.endsWith(ext);
                    }
                })
                : null;

        if (files == null || files.length == 0) {
            String message = !directory.isDirectory()
                ? "Could not find: " + directory.getPath()
                : "Could not find images in path: " + directory.getPath();
            FileNotFoundException fileNotFoundException = new FileNotFoundException(message);
            displayException(fileNotFoundException);
            showUsage();
            throw new RuntimeException(fileNotFoundException);
        }

        // Make sure numbered files are ordered correctly
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                final String name1 = o1.getName();
                final String name2 = o2.getName();
                int c = name1.length() - name2.length();
                if (c == 0) {
                    c = name1.compareTo(name2);
                }
                return c;
            }
        });

        return files;

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
    }
}
