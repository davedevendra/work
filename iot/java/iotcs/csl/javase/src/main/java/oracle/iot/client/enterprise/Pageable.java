/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package oracle.iot.client.enterprise;

import java.io.IOException;
import java.util.Collection;
import java.util.NoSuchElementException;

/**
 * A {@code Pageable} instance is a cached view on a remote collection of elements of the specified type {@code T}.
 *
 * <h3>Notion of Pages</h3>
 * The remote collection is not entirely loaded in memory but only a subset of the elements is loaded in the cache.
 * This subset is called a <b>page</b> and has a configurable {@link #limit() limit} and {@link #offset() offset} to
 * respectively control the maximum number of elements to fetch from the remote collection and the absolute offset of
 * the first element to fetch. The elements loaded in the cache are accessed using {@link #elements()}.
 *
 * <h3>Navigation</h3>
 * The {@code Pageable} instance is also used to navigate in the remote collection and access its elements. The
 * navigation supports random access to any position in the remote collection and is performed by calling one of the
 * following methods: {@link #next()}, {@link #first()}, {@link #last()}, {@link #at(int offset)}.
 * Navigation implies accessing the remote collection and may fail with an I/O error.
 *
 * <h3>Concurrent modifications of the remote collection</h3>
 * The remote collection might be modified concurrently. Consequences:
 * <ul>
 * <li>Changes that occurred in the remote collection are not reflected in the elements in the cache
 * accessed with {@link #elements()}</li>
 * <li>The total size of the collection might have evolved since last access and may affect the result of
 * {@link #next()}, {@link #at(int)} and {@link #last()}</li>
 * </ul>
 *
 * <h3>Examples:</h3>
 * <pre><code>
 *     Pageable&lt;String&gt; p;
 *
 *     // iterate on all elements of the remote collection
 *     while (p.hasMore()) {
 *         // get next available page, using already initialized offset and limit
 *         p = p.next();
 *
 *         // get the elements of the first page
 *         Collection&lt;String&gt; names = p.elements();
 *         ...
 *
 *     }
 *
 *     // get 10 elements, starting at offset 300 in the remote collection.
 *     Collection&lt;String&gt; names = p.limit(10).at(300).elements();
 * </code></pre>
 */
public interface Pageable<T> {

    /**
     * Get the total number of elements in the collection
     * @return the size of the collection
     */
    int size();

    /**
     * Returns whether or not more elements are available in the remote collection, accessible using {@link #next()}.
     * @return {@code true} if more elements are available.
     */
    boolean hasMore();

    /**
     * Get the absolute offset in the remote collection of the first element.
     * @return the offset of the first element
     */
    int offset();

    /**
     * Get the maximum number of elements that will be fetched from the remote collection during next refill.
     * @return the maximum number of elements to fetch
     */
    int limit();

    /**
     * Set the number of elements that will be retrieved from the remote collection during next refill.
     * @param limit the maximum number of elements of a page
     * @return {@code Pageable} instance configured with the new limit (could be the same object).
     */
    Pageable<T> limit(int limit);

    /**
     * Returns the subset of elements that have been loaded from the remote collection.
     * @return the elements of current page.
     */
    Collection<T> elements();

    /**
     * Navigate in the remote collection to get the next page, starting at the specified offset increased by the number
     * of elements of the current page, and return a view on it.
     * @return the next page in the remote collection
     * @throws IOException if an I/O error occurred while trying to access remote collection
     * @throws NoSuchElementException if the collection has no more element
     * @see #hasMore()
     */
    Pageable<T> next() throws IOException, NoSuchElementException;

    /**
     * Navigate to the first page and return a view on it.
     * @return the first page in the remote collection
     * @throws IOException if an I/O error occurred while trying to access remote collection
     */
    Pageable<T> first() throws IOException;

    /**
     * Navigate to the last page and return a view on it.
     * @return the last page in the remote collection
     * @throws IOException if an I/O error occurred while trying to access remote collection
     */
    Pageable<T> last() throws IOException;

    /**
     * Navigate to the specified offset in the remote collection and return the corresponding page
     * @param offset the absolute offset of the first element to fetch
     * @return an Pageable to access elements of the page
     * @throws IOException if an I/O error occurred while trying to access remote collection
     * @throws IndexOutOfBoundsException if the offset is out of range {@code  (offset < 0 || offset >= size())}
     */
    Pageable<T> at(int offset) throws IOException, IndexOutOfBoundsException;
}
