/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.sample;

import oracle.iot.client.AbstractVirtualDevice.ErrorCallback;
import oracle.iot.client.AbstractVirtualDevice.ErrorEvent;
import oracle.iot.client.DeviceModel;
import oracle.iot.client.StorageObject;
import oracle.iot.client.device.Data;
import oracle.iot.client.device.DirectlyConnectedDevice;
import oracle.iot.client.device.VirtualDevice;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

/*
 * This sample demonstrates the oracle.iot.client.StorageObject and related API.
 *
 * The sample uses the virtualization API to simulate a motion activated camera.
 * An image is uploaded to the storage cloud every 5 seconds.
 * The sample also handles the "recording" action.
 *
 * Note that the code is Java SE 1.5 compatible.
 */
public class MotionActivatedCameraSample {

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

        if (args.length != 2) {
            display("\nIncorrect number of arguments.\n");
            showUsage();
            throw new IllegalArgumentException("Incorrect number of arguments.");
        }

        final File[] images = getFiles("./images", "jpeg");
        final File[] videos = getFiles("./videos", "mp4");

        // The directlyConnectedDevice variable needs to be final so that it can be used in the
        // setCallable("record", new Callable() {...}); implementation below.
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

        // A callback for receiving an error if the Data fails to be sent.
        final ErrorCallback<VirtualDevice> dataFailed = new ErrorCallback<VirtualDevice>() {
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

        try {

            // Activate the device
            if (!directlyConnectedDevice.isActivated()) {
                display("\nActivating...");
                directlyConnectedDevice.activate(MOTION_ACTIVATED_CAMERA_MODEL_URN);
            }

            // Create a virtual device implementing the device model
            final DeviceModel deviceModel =
                directlyConnectedDevice.getDeviceModel(MOTION_ACTIVATED_CAMERA_MODEL_URN);

            final VirtualDevice virtualMotionActivatedCamera =
                directlyConnectedDevice.createVirtualDevice(directlyConnectedDevice.getEndpointId(), deviceModel);

            virtualMotionActivatedCamera.setCallable("record", new VirtualDevice.Callable<Number>() {
                @Override
                public void call(final VirtualDevice virtualDevice, Number data) {

                    int d = data.intValue();
                    if (d <= 0) return;

                    // round to nearest increment of 15
                    final int duration = (d + 14) / 15 * 15;
                    // assumes videos.length > 0 and videos are 15 second increments.
                    final File video = videos[Math.min(duration/15-1,videos.length-1)];

                    final StringBuilder msg =
                            new StringBuilder(new Date().toString())
                                    .append(" : ")
                                    .append(virtualDevice.getEndpointId())
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
                                /*
                                 * The name of video will be automatically
                                 * prefixed with the device's client and "/"
                                 */
                                StorageObject storageObject =
                                    directlyConnectedDevice.createStorageObject(
                                        video.getName(), "video/mp4");
                                storageObject.setOnSync(syncCallback);
                                storageObject.setInputPath(video.getPath());

                                StringBuilder consoleMessage = new StringBuilder(new Date().toString())
                                        .append(" : ")
                                        .append(virtualMotionActivatedCamera.getEndpointId())
                                        .append(" : Set : \"")
                                        .append("recording")
                                        .append("\"=")
                                        .append(storageObject.getURI());

                                Data recording = virtualDevice.createData(MOTION_ACTIVATED_CAMERA_MODEL_URN + ":recording");
                                recording.setOnError(dataFailed);
                                recording.set("video", storageObject)
                                        .set("startTime", startTime)
                                        .set("duration", duration)
                                        .submit();

                                display(consoleMessage.toString());

                            } catch(IOException ioe) {
                                displayException(ioe);
                            } catch (GeneralSecurityException gse) {
                                displayException(gse);

                            }
                        }
                    }).start();
                }
            });

            /*
             * Monitor the virtual device for errors when setting attributes
             */
            virtualMotionActivatedCamera.setOnError(
                new VirtualDevice.ErrorCallback<VirtualDevice>() {
                    public void onError(VirtualDevice.ErrorEvent<VirtualDevice
                            > event) {
                        VirtualDevice device =  event.getVirtualDevice();
                        display(new Date().toString() + " : onError : " +
                                device.getEndpointId() +
                                " : \"" + event.getMessage() + "\"");
                    }
                });

            display("\nCreated virtual motion activated camera " + virtualMotionActivatedCamera.getEndpointId());
            display("\n\tPress enter to exit.\n");

            int n = 0;
            mainLoop:
            for (;;) {

                final File image = images[n++ % images.length];

                StringBuilder consoleMessage = new StringBuilder(new Date().toString())
                        .append(" : ")
                        .append(virtualMotionActivatedCamera.getEndpointId())
                        .append(" : Set : \"")
                        .append(IMAGE_ATTRIBUTE)
                        .append("\"=")
                        .append(image.getPath());

                try {
                    /*
                     * The name of video will be automatically
                     * prefixed with the device's client and "/"
                     *
                     * If this device implements many models or
                     * this was a gateway device, adding the another
                     * level of uniqueness would be warrented.
                     */
                    final StorageObject storageObject =
                        directlyConnectedDevice.createStorageObject(
                            image.getName(), "image/jpeg");
                    storageObject.setOnSync(syncCallback);

                    storageObject.setInputPath(image.getPath());

                    virtualMotionActivatedCamera.update()
                            .set(IMAGE_ATTRIBUTE, storageObject)
                            .set(IMAGE_TIME_ATTRIBUTE, new Date())
                            .finish();

                } catch (FileNotFoundException fnfe) {
                    displayException(fnfe);
                    break mainLoop;
                }

                display(consoleMessage.toString());
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

    // This callback will be set on a StorageObject when the client library synchronizes a
    // StorageObject. The callback simply prints out the
    // status of synchronizing the content with the Oracle Storage Cloud Service.
    // The same callback is used for all of the StorageObject instances.
    private static final StorageObject.SyncCallback<VirtualDevice> syncCallback =
            new StorageObject.SyncCallback<VirtualDevice>() {
                @Override
                public void onSync(StorageObject.SyncEvent<VirtualDevice> event) {
                    VirtualDevice virtualDevice = event.getVirtualDevice();
                    StorageObject storageObject = event.getSource();
                    StringBuilder msg =
                            new StringBuilder(new Date().toString())
                                    .append(" : ")
                                    .append(virtualDevice.getEndpointId())
                                    .append(" : onSync : ")
                                    .append(storageObject.getName())
                                    .append("=\"")
                                    .append(storageObject.getSyncStatus())
                                    .append("\"");

                    if (storageObject.getSyncStatus() == StorageObject.SyncStatus.IN_SYNC) {
                        msg.append(" (")
                                .append(storageObject.getLength())
                                .append(" bytes)");
                    }
                    display(msg.toString());
                }
            };

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
