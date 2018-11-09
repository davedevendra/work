/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * Slide is how much the window moves after the window expires. If there is a window of 5 seconds
 * with a slide of 2 seconds, then at the end of 5 seconds, the window slides over by two seconds.
 * That means that the next window's worth of data would include 3 seconds of data from the previous
 * window, and 2 seconds of new data.
 *
 * To handle this, we divide up the window into buckets. Each bucket represents a period of time,
 * such that the time period is the greatest common factor between the window and the slide. For
 * example, if the window is 60 seconds and the slide is 90 seconds, a bucket would span 30 seconds,
 * and there would be three buckets.
 *
 * When the window expires and the get method is called, the return value of the mean policy
 * function will include the value and number of terms of bucket[0] through bucket[n]. Then the
 * buckets that don't contribute to the next window are emptied (so to speak) and the cycle
 * continues.
 *
 * Best case is that the slide equal to the window. In this case, there is only ever one bucket. The
 * worst case is when greatest common factor between slide and window is small. In this case, you
 * end up with a lot of buckets, potentially one bucket per slide time unit (e.g., 90 seconds, 90
 * buckets). But this is no worse (memory wise) than keeping an array of values and timestamps.
 */
class Bucket {
    // Instance "variables"/properties...see constructor.

    constructor(initialValue) {
        // Instance "variables"/properties.
        this.value = initialValue;
        this.terms = 0;
        // Instance "variables"/properties.
    }

    /**
     * @return {string} this Bucket represented as a string.
     */
    toString() {
        return '{"value" : ' + value + ', "terms" : ' + terms + '}';
    }
}
