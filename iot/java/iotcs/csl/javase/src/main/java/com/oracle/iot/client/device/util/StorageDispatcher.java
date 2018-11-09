/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.device.util;

import java.io.Closeable;

import com.oracle.iot.client.StorageObject;
import com.oracle.iot.client.device.DirectlyConnectedDevice;
import com.oracle.iot.client.impl.device.StorageDispatcherImpl;

/**
 * The StorageDispatcher queues content for automatic upload to, or download from,
 * the Oracle Storage Cloud Service.
 * <pre>
 *     {@code
 *     // Upload example
 *     com.oracle.iot.client.device.DirectlyConnectedDevice dcd =
 *         new com.oracle.iot.client.device.DirectlyConnectedDevice(assetsFilePath, assetsFilePassword);
 *     StorageDispatcher storageDispatcher = StorageDispatcher.getStorageDispatcher(dcd);
 *     storageDispatcher.setProgressCallback(new ProgressCallback() {
 *          public void progress(Progress progress) {
 *              StorageObject storageObject = progress.getStorageObject();
 *              if (storageObject.getName().equals("lenna")) {
 *                  if (progress.getState() == State.COMPLETED) {
 *                      // Can now safely remove images/Lenna.jpg, if desired.
 *                  } else if (progress.getState() == State.IN_PROGRESS && takingTooLong) {
 *                      storageDispatcher.cancel(storageObject);
 *                  }
 *              }
 *          }
 *      });
 *     StorageObject lenna =  dcd.createStorageObject("lenna","image/jpeg");
 *     lenna.setInputStream(new FileInputStream("../images/Lenna.jpg"));
 *     storageDispatcher.queue(lenna);
 *
 *     // Download example
 *     com.oracle.iot.client.device.DirectlyConnectedDevice dcd =
 *         new com.oracle.iot.client.device.DirectlyConnectedDevice(assetsFilePath, assetsFilePassword);
 *     StorageDispatcher storageDispatcher = StorageDispatcher.getStorageDispatcher(dcd);
 *     storageDispatcher.setProgressCallback(new ProgressCallback() {
 *         public void progress(Progress progress) {
 *             StorageObject storageObject = progress.getStorageObject();
 *             if (storageObject.getName().equals("lenna")) {
 *                 if (progress.getState == State.COMPLETED) {
 *                     // downloads/Lenna.jpg is ready
 *                 } else if (progress.getState() == State.IN_PROGRESS && takingTooLong) {
 *                     storageDispatcher.cancel(storageObject);
 *                 }
 *             }
 *         }
 *     });
 *     
 *     StorageObject lenna =  dcd.createStorageObject(uri);
 *     lenna.setOutputStream(new FileOutputStream("../images/Lenna.jpg"));
 *     storageDispatcher.queue(lenna);
 *     }
 *     
 * </pre>
 */
public abstract class StorageDispatcher implements Closeable {

    /**
     * Get the {@code StorageDispatcher} for the given {@link DirectlyConnectedDevice}.
     * @return a StorageDispatcher
     * @param directlyConnectedDevice the {@link DirectlyConnectedDevice}
     */
    public static StorageDispatcher getStorageDispatcher(DirectlyConnectedDevice directlyConnectedDevice) {
        return StorageDispatcherImpl.getStorageDispatcher(directlyConnectedDevice);
    }

    /**
     * Add a {@code StorageObject} to the queue to upload/download content
     * to/from the Storage Cloud. 
     *
     * @param storageObject The content storageObject to be queued
     * @throws IllegalArgumentException if the storage object is null
     * @throws IllegalStateException if the storage object is already queued or
     * in progress
     */
    public abstract void queue(StorageObject storageObject);

    /**
     * Cancel the transfer of content to or from storage. This call has no effect
     * if the transfer is completed, already cancelled, has failed, or the
     * {@code storageObject} is not queued.
     * @param storageObject the content storageObject to be cancelled.
     */
    public abstract void cancel(StorageObject storageObject);

    /**
     * An object for receiving progress via the {@code ProgressCallback}.
     */
    public interface Progress {
        enum State {
            /**
             * Initial state after upload() or download() is called
             */
            INITIATED,

            /**
             * Up/download is queued and not yet started
             */
            QUEUED,

            /**
             * Up/download is currently in progress
             */
            IN_PROGRESS,

            /**
             * Up/download completed successfully
             */
            COMPLETED,

            /**
             * Up/download was cancelled before it completed
             */
            CANCELLED,

            /**
             * Up/download failed without completing
             */
            FAILED;
        }

        /**
         * Get the StorageObject that was queued for which this progress event pertains.
         * @return a StorageObject
         */
        StorageObject getStorageObject();

        /**
         * Get the state of the transfer
         * @return the transfer state
         */
        State getState();

        /**
         * Get the exception that resulted in the transfer failure. The
         * exception is either an IOException or GeneralSecurityException. This
         * is useful if the progress state is {@code FAILED}.
         * @return the exception causing the failure
         */
        Exception getFailureCause();

        /**
         * Get the number of bytes transferred. This can be compared to the
         * length of content obtained by calling {@link StorageObject#getLength()}.
         * @return the number of bytes transferred
         */
        long getBytesTransferred();
    }

    /**
     * A callback interface for monitoring progress of queued content.
     */
    public interface ProgressCallback {
        /**
         * Notify of progress for content transfer.
         * @param progress progress data
         */
        void progress(Progress progress);
    }


    /**
     * Set a callback to be notified as the transfer progresses.
     * @param callback callback to invoke, if {@code null},
     *                 the existing callback will be removed
     */
    public abstract void setProgressCallback(ProgressCallback callback);

}
