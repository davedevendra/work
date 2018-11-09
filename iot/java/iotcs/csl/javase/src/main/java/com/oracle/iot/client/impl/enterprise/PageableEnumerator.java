/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 */

package com.oracle.iot.client.impl.enterprise;

import com.oracle.iot.client.HttpResponse;
import com.oracle.iot.client.impl.http.HttpSecureConnection;
import com.oracle.iot.client.message.StatusCode;

import oracle.iot.client.enterprise.Pageable;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 */
public abstract class PageableEnumerator<T> implements Pageable<T> {
    /**
     * The total number of elements in the remote collection.
     * Set size to a negative number (-1) if unknown.
     */
    protected int size;
    /**
     * The offset of the first element in the page
     */
    protected int offset;
    /**
     * The maximum number of elements to fetch in next REST call
     */
    protected int limit;
    /**
     * Whether or not the remote collection has more elements
     */
    protected boolean hasMore;
    /**
     * The elements of the page
     */
    private Collection<T> elements;

    public PageableEnumerator(int offset, int limit) {
        this.offset = offset;
        this.limit = limit;
        this.hasMore = false;
        this.elements = Collections.emptyList();
    }

    protected abstract HttpSecureConnection getSecureConnection();

    /**
     * Callback to refill current page.
     * @param offset the offset of the first element to fetch in the remote collection
     * @param limit the maximum number of elements to fetch in the remote collection
     * @return the page fetched from remote collection
     * @throws IOException if an I/O error occurred while accessing the remote collection
     */
    protected abstract PagedResponse<T> load(int offset, int limit) throws IOException;

    public void setSize(int size) {
        this.size = size;
        // if size is unknown, force reload
        // otherwise set hasMore using elements count compared to total size
        this.hasMore = (size < 0) || (this.offset + this.elements.size() < this.size);
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public boolean hasMore() {
        return this.hasMore;
    }

    @Override
    public int offset() {
        return this.offset;
    }

    private synchronized void offset(int offset) throws IOException {
        // always try to access the remote collection (size might have evolved since last call)
        PagedResponse<T> resp = load(offset, this.limit);
        this.elements = resp.elements();
        this.hasMore = resp.hasMore();
        this.offset = resp.offset();
        this.size = resp.total();
    }

    @Override
    public int limit() {
        return this.limit;
    }

    @Override
    public Pageable<T> limit(int limit) {
        this.limit = limit;
        return this;
    }

    @Override
    public Collection<T> elements() {
        return this.elements;
    }

    @Override
    public Pageable<T> next() throws IOException {
        // always try to access the remote collection (size might have evolved since last call)
        this.offset(this.offset + this.elements.size());
        if (this.elements.isEmpty()) {
            throw new NoSuchElementException();
        }
        return this;
    }

    @Override
    public Pageable<T> first() throws IOException {
        return this.at(0);
    }

    @Override
    public Pageable<T> last() throws IOException {
        return this.at(size - this.limit);
    }

    @Override
    public Pageable<T> at(int offset) throws IOException {
        this.offset(offset);
        if (this.elements.isEmpty()) {
            throw new IndexOutOfBoundsException();
        }
        return this;
    }

    protected JSONObject get(Map<String, String> headers, String request)
            throws IOException, GeneralSecurityException {

        HttpResponse res =
            getSecureConnection().get(request);

        int status = res.getStatus();
        if (status != StatusCode.OK.getCode()) {
            throw new IOException(res.getVerboseStatus("GET", request));
        }

        byte[] data = res.getData();
        if (data == null) {
            throw new IOException("GET " + request +
                                  " failed: no data received");
        }

        String json = new String(data, "UTF-8");
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            throw new IOException("GET " + request +
                " failed: cannot parse data received" + e.getMessage());
        }
    }

    /**
     * A response to a REST call supporting paging.
     * @param <T> the type of elements in the page.
     */
    public static class PagedResponse<T> {

        private final Collection<T> elements;
        private final boolean hasMore;
        private final int offset;
        private final int total;
        private final Map<String, String> links;

        public static <T> PagedResponse<T> empty() {
            return new PagedResponse<T>(Collections.<T>emptyList(), 0, 0, false, null);
        }

        protected PagedResponse(Collection<T> elements, int offset, int total, boolean hasMore, Map<String, String> links) {
            this.elements = elements;
            this.hasMore = hasMore;
            this.offset = offset;
            this.total = total;
            this.links = links;
        }

        /**
         * Get the elements in the page
         * @return the elements of the page
         */
        public Collection<T> elements() {
            return this.elements;
        }

        /**
         * Get the links of the page (first, last, next, ...)
         * @return a map of the links to navigate from this page
         */
        public Map<String, String> links() {
            return this.links;
        }

        /**
         * Whether the collection has more elements
         * @return {@code true} if the collection has more elements, {@code false} otherwise.
         */
        public boolean hasMore() {
            return this.hasMore;
        }

        /**
         * Return the offset of the first element
         * @return the offset of the first element of this page.
         */
        public int offset() {
            return this.offset;
        }
        /**
         * Get the number of elements in this response.
         * @return the element count in this response.
         */
        public int count() {
            return this.elements().size();
        }
        /**
         * Get the total number of items in the remote collection.
         * @return the total count.
         */
        public int total() {
            return this.total;
        }

        /**
         * Get from the response the item named "canonical" in the set of "links".
         *
         * @return the canonical link representing this response, or {@code null} if not found.
         */
        public String canonical() {
            return this.links.get("canonical");
        }

        /**
         * Get from the response the item named "self" in the set of "links".
         *
         * @return the link representing this response, or {@code null} if not found.
         */
        public String self() {
            return this.links.get("self");
        }

        /**
         * Get from the response the item named "next" in the set of "links".
         *
         * @return the link to access next response, or {@code null} if not found.
         */
        public String next() {
            return this.links.get("next");
        }

        /**
         * Get from the response the item named "next" in the set of "links".
         *
         * @return the link to access first response, or {@code null} if not found.
         */
        public String first() {
            return this.links.get("first");
        }

        /**
         * Get from the response the item named "next" in the set of "links".
         *
         * @return the link to access last response, or {@code null} if not found.
         */
        public String last() {
            return this.links.get("last");
        }

    }

}
