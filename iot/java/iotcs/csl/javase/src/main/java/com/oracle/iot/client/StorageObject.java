/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

/**
 * StorageObject provides information about content in cloud storage.
 */
public abstract class StorageObject extends ExternalObject {

    /**
     * Get the the name of this object in the storage cloud. This is name and path of the file
     * that was uploaded to the storage cloud.
     * @return the name of this object in the storage cloud
     */
    public String getName() {
        return name;
    }

    /**
     * Get the mime-type of the content. See
     * <a href="http://www.iana.org/assignments/media-types/media-types.xhtml">IANA Media Types</a>.
     *
     * @return The mime-type of the content
     */
    public String getType() {
        return type;
    }

    /**
     * Get the compression scheme of the content.
     *
     * @return the compression scheme of the content, or {@code null} if the content is not compressed
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * Get the length of the content in bytes. This is the number of bytes required to upload or download
     * the content.
     *
     * @return The length of the content, or {@code -1} if unknown
     */
    public long getLength() {
        return length;
    }

    /**
     * Get the date and time the content was created or last modified in cloud 
     * storage. This may be {@code null} if the content has not been uploaded.
     * The date and time stamp format is ISO 8601.
     * @return The date the content was last modified in cloud storage, or {@code null} if the
     * content has not been uploaded.
     */
    public String getDate() {
        return date;
    }

    /**
     * Called by the library to obtain the input stream when uploading content
     * @return The input stream for the content being uploaded
     */
    public InputStream getInputStream() {
        return inputStream;
    }

    /**
     * Set an input stream for content to be uploaded. The implementation
     * allows for either the input stream to be set, or the output stream to be set, but not both.
     * If the {@code inputStream} parameter is not null, the output stream will be set to null.
     * @param inputStream A stream from which the content will be read
     * @throws IllegalStateException if a transfer is currently in progress
     */
    public void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
        if (inputStream != null) {
            this.outputStream = null;
        }
    }

    /**
     * Called by the library to obtain the output stream when downloading content
     * @return The output stream for the content being downloaded
     */
    public OutputStream getOutputStream(){
        return outputStream;
    }

    /**
     * Set an output stream for content to be downloaded. The implementation
     * allows for either the input stream to be set, or the output stream to be set, but not both.
     * If the {@code outputStream} parameter is not {@code null}, the input stream will be set to
     * {@code null}.
     * @param outputStream A stream to which the content will be written
     * @throws IllegalStateException if a transfer is currently in progress
     */
    public void setOutputStream(OutputStream outputStream) {
        this.outputStream = outputStream;
        if (outputStream != null) {
            this.inputStream = null;
        }
    }

    /**
     * Synchronize content with the Storage Cloud Service.
     *
     * @throws IOException if there is an {@code IOException} raised by the runtime,
     * or a failure reported by the storage cloud
     * @throws GeneralSecurityException if there is an exception establishing a secure connection to the storage cloud
     * @throws IllegalArgumentException if the {@code StorageObject} does not return an {@code InputStream} or {@code OutputStream}
     */
    public abstract void sync() throws IOException, GeneralSecurityException;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if(obj == null) return false;
        if (this.getClass() != obj.getClass()) return false;
        StorageObject other = (StorageObject)obj;
        if (!this.getURI().equals(other.getURI())) return false;
        if (!this.getName().equals(other.getName())) return false;
        if (!this.getType().equals(other.getType())) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 61 * hash + this.name.hashCode();
        hash = 61 * hash + this.type.hashCode();
        hash = 61 * hash + this.getURI().hashCode();
        return hash;
    }
    
    /**
     * Used internally by the client library to update fields after upload. 
     * Not intended for general use.
     * @param date the last-modified date for the object in the Storage Cloud
     * @param length the length for the object in the Storage Cloud
     */
    protected void setMetadata(String date, long length) {
        this.date = date;
        this.length = length;
    }
    
    /**
     * Create a {@code StorageObject}.
     * @param uri the full URL of the object in the Storage Cloud
     * @param name name of the object used in the Storage Cloud
     * @param type type of the object, , or {@code null} if none
     * @param encoding encoding of the object, or {@code null} if none
     * @param date last-modified date of the object
     * @param length length of the object
     */
    protected StorageObject(String uri,
                            String name,
                            String type,
                            String encoding,
                            String date,
                            long length) {
        super(uri);
        this.name = name;
        this.type = type != null ? type : "application/octet-stream";
        this.encoding = encoding;
        this.date = date;
        this.length = length;
    }

    private final String name;
    private final String type;
    private final String encoding;
    private String date;
    private long length;
    private InputStream inputStream;
    private OutputStream outputStream;
}
