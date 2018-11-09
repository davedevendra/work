/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package oracle.iot.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/**
 * StorageObject provides information about content in the cloud storage.
 *
 * <pre>
 * {@code
 *     // Upload example
 *     StorageObject lenna =
 *         directlyConnectedDevice.createStorageObject("lenna.jpg", "image/jpeg");
 *     lenna.setInputPath("../images/lenna.jpg");
 *
 *     // onSync is called when the content referenced by the storageObject
 *     // is in sync with the storage cloud, or the sync has failed.
 *     lenna.setOnSync(event -> {
 *         StorageObject storageObject = event.getSource();
 *         if (storageObject.getSyncStatus() == SyncStatus.IN_SYNC) {
 *             // image was uploaded and can be deleted
 *         } else if (storageObject.getSyncStatus() == SyncStatus.SYNC_FAILED) {
 *             // image was not uploaded, take action!
 *         }
 *     });
 *
 *     virtualDevice.set("image", lenna);
 *
 *     // Download example
 *     // onChange is called when the attribute value changes.
 *     virtualDevice.setOnChange("image", event -> {
 *         VirtualDevice.NamedValue namedValue = event.getNamedValue();
 *         StorageObject storageObject = (StorageObject)namedValue.getValue();
 *         if (storageObject.getContentLength() < availableDiskSpace) {
 *             // syncTo will kick off the async download of the content
 *             storageObject.setOnSync(event -> {
 *                 StorageObject storageObject = event.getSource();
 *                 if (storageObject.getSyncStatus() == SyncStatus.IN_SYNC) {
 *                     // image was downloaded and can now be used
 *                 } else if (storageObject.getSyncStatus() == SyncStatus.SYNC_FAILED) {
 *                     // image was not downloaded, take action!
 *                 }
 *             }
 *             storageObject.setOutputPath("../downloads"+storageObject.getName());
 *             storageObject.sync();
 *         }
 *     });
 * }
 * </pre>
 *
 */
public abstract class StorageObject extends ExternalObject {

    /**
     * The status of whether or not the content is in sync with the storage cloud.
     */
    public enum SyncStatus {
        /**
         * The content is in sync with the storage cloud. This means that
         * the upload or download is complete.
         */
        IN_SYNC,

        /**
         * The content is not in sync with the storage cloud because
         * the upload or download failed.
         */
        SYNC_FAILED,
        
        /**
         * The content is not in sync with the storage cloud because
         * it has not yet been uploaded or download.
         */
        NOT_IN_SYNC,

        /**
         * The content is not in sync with the storage cloud, but a
         * sync is pending.
         */
        SYNC_PENDING
    }

    /**
     * Get the the name of this object in the storage cloud. 
     * @return the name of this object in the storage cloud
     */
    public String getName() {
        return delegate.getName();
    }

    /**
     * Get the mime-type of the content. See
     * <a href="http://www.iana.org/assignments/media-types/media-types.xhtml">IANA Media Types</a>.
     * @return The mime-type of the content
     */
    public String getType() {
        return delegate.getType();
    }

    /**
     * Get the compression scheme of the content.
     * @return the compression scheme of the content, or {@code null} if the content is not compressed
     */
    public String getEncoding() {
        return delegate.getEncoding();
    }

    /**
     * Get the length of the content in bytes. This is the number of bytes required to upload or download
     * the content.
     * @return The length of the content, or {@code -1} if unknown
     */
    public long getLength() {
        return delegate.getLength();
    }

    /**
     * Get the date and time the content was created or last modified in cloud 
     * storage. This may be {@code null} if the content has not been uploaded.
     * The date and time stamp format is ISO 8601.
     * @return The date the content was last modified in cloud storage, or {@code null} if the
     * content has not been uploaded.
     */
    public String getDate() {
        return delegate.getDate();
    }

    /**
     * Get the status of whether or not the content is in sync with the storage cloud.
     * @return the status of whether or not the content is in sync with the storage cloud.
     */
    public SyncStatus getSyncStatus() {
        return syncStatus;
    }

    /**
     * Get the input path.
     * @return the input path, or {@code null} if not set
     */
    public String getInputPath() {
        return inputPath;
    }

    /**
     * Set the input path for uploading content to the storage cloud. The implementation
     * allows for either the input path to be set, or the output path to be set, but not both.
     * If the {@code inputPath} parameter is not null, the output path will be set to null.
     * If the {@code inputPath} parameter is not null and does not equal the current input path,
     * the sync status will be reset to {@link StorageObject.SyncStatus#NOT_IN_SYNC}.
     * This method will throw an {@code IllegalStateException} if
     * {@link #getSyncStatus() sync status} is {@link StorageObject.SyncStatus#SYNC_PENDING},
     * @param inputPath the path from which to read the content for upload
     * @throws java.io.FileNotFoundException if the input path cannot be read
     * @throws IllegalStateException if called when sync status is {@code SYNC_PENDING}
     */
    public void setInputPath(String inputPath) throws FileNotFoundException {

        if (syncStatus == SyncStatus.SYNC_PENDING) {
            throw new IllegalStateException("sync pending");
        }

        if (this.inputPath == null || !this.inputPath.equals(inputPath)) {
            this.inputPath = inputPath;
            if (this.inputPath != null) {
                this.outputPath = null;
                this.syncStatus = SyncStatus.NOT_IN_SYNC;
                delegate.setInputStream(new FileInputStream(inputPath));
            }
        }

    }

    /**
     * Get the output path.
     * @return the output path, or {@code null} if not set
     */
    public String getOutputPath() {
        return outputPath;
    }

    /**
     * Set the output path for downloading content from the storage cloud. The implementation
     * allows for either the output path to be set, or the input path to be set, but not both.
     * If the {@code outputPath} parameter is not null, the input path will be set to null.
     * If the {@code outputPath} parameter is not null and does not equal the current output path,
     * the sync status will be reset to {@link StorageObject.SyncStatus#NOT_IN_SYNC}.
     * This method will throw an {@code IllegalStateException} if the
     * {@link #getSyncStatus() sync status} is {@link StorageObject.SyncStatus#SYNC_PENDING},
     * @param outputPath the path to which the content will be downloaded. If the path does not
     * already exist, it will be created.
     * @throws java.io.FileNotFoundException if the output path cannot be written
     * @throws IllegalStateException if called when sync status is {@code SYNC_PENDING}
     */
    public void setOutputPath(String outputPath) throws FileNotFoundException {

        if (syncStatus == SyncStatus.SYNC_PENDING) {
            throw new IllegalStateException("sync pending");
        }

        if (this.outputPath == null || !this.outputPath.equals(outputPath)) {
            this.outputPath = outputPath;
            if (this.outputPath != null) {
                this.inputPath = null;
                this.syncStatus = SyncStatus.NOT_IN_SYNC;
                File file = new File(outputPath);
                if(file.getParent() != null) {
                    File parent = file.getParentFile();
                    if (!parent.exists()) {
                        parent.mkdirs();
                    }
                }
                delegate.setOutputStream(new FileOutputStream(outputPath));
            }
        }
    }

    /**
     * Notify the library to sync content with the storage cloud.
     * The direction of the sync, upload or download, depends on whether
     * the {@link #setInputPath(String) input path} or the
     * {@link #setOutputPath(String) output path} has been set.
     * This method does not start any uploads or downloads if {@link #getSyncStatus() sync status}
     * is other than {@link StorageObject.SyncStatus#NOT_IN_SYNC}.
     * <p>
     * This is a non-blocking call. The sync is performed on a separate thread.
     * The status of the sync can be monitored by setting a {@link StorageObject.SyncCallback}
     * on this {@code StorageObject}.
     * <p>
     * If the input path cannot be read, or the output path cannot be
     * written, an {@code IOException} is thrown. Any I/O exceptions
     * during the background sync are reported through the
     * {@link AbstractVirtualDevice.ErrorCallback error syncCallback}
     * of the virtual device.
     *
     * @throws IllegalStateException if both input path and output path are {@code null}
     */
    public abstract void sync();

    /**
     * An event passed to the {@link #setOnSync(SyncCallback) SyncCallback}
     * when content referred to by an attribute value has been successfully
     * synchronized, or has failed to be synchronized.
     * @param <V> the type of AbstractVirtualDevice
     */
    public interface SyncEvent<V extends AbstractVirtualDevice> {

        /** 
         * Get the virtual device that is the source of the event.
         * @return the virtual device, or {@code null} if {@code sync} was 
         * called independently
         */
        V getVirtualDevice();

        /**
         * Get the name of the attribute, action, or format that this event
         * is associated with.
         * @return the name, or {@code null} if {@code sync} was called 
         * independently 
         */
        String getName();

        /**
         * Get the {@code StorageObject} that is the source of this event.
         * @return the storageObject, never {@code null}
         */
        StorageObject getSource();
    }

    /**
     * A syncCallback interface for receiving an event when content referred to by
     * an attribute value has been successfully synchronized, or has failed
     * to be synchronized.
     * @param <V> the type of AbstractVirtualDevice
     * @see #setOnSync(SyncCallback)
     */
    public interface SyncCallback<V extends AbstractVirtualDevice> {
        /**
         * Callback for receiving an event when content referred to by
         * an attribute value has been successfully synchronized, or has failed
         * to be synchronized.
         * This syncCallback will never be invoked by the
         * client-library with a {@code null} argument.
         * @param event The error event
         */
        void onSync(SyncEvent<V> event);
    }

    /**
     * Set a {@link StorageObject.SyncCallback} that is invoked when the content
     * referred to by this {@code StorageObject} is synchronized.
     * @param callback a callback to invoke when there is an error setting a
     * @param <V> type of AbstractVirtualDevice
     * value, if {@code null}, the existing syncCallback will be removed
     */
    public <V extends AbstractVirtualDevice> void setOnSync(SyncCallback<V> callback) {
        this.syncCallback = callback;
    }
    
    @Override
    public String toString() {
        return super.getURI();
    }

    /**
     * Create a {@code StorageObject} instance.
     * @param delegate A messaging API {@code StorageObject} to which
     *             this API will delegate.
     */
    protected StorageObject(com.oracle.iot.client.StorageObject delegate) {
        super(delegate.getURI());
        this.delegate = delegate;
        this.syncStatus = SyncStatus.NOT_IN_SYNC;
    }

    /**
     * Used internally by the client library. Not intended for general use.
     * @param syncStatus the new SyncStatus
     */
    final protected void setSyncStatus(SyncStatus syncStatus) {
        this.syncStatus = syncStatus;
    }

    /**
     * Used internally by the client library. Not intended for general use.
     * @return the SyncCallback
     */
    final protected SyncCallback getSyncCallback() {
        return syncCallback;
    }

    private final com.oracle.iot.client.StorageObject delegate;
    private volatile SyncStatus syncStatus;
    private volatile SyncCallback syncCallback;
    private String inputPath;
    private String outputPath;
//
}
