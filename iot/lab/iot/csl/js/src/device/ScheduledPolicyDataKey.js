/**
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

class ScheduledPolicyDataKey {
    // Instance "variables"/properties...see constructor.

    /**
     *
     * @param {number} window
     * @param {number} slide
     */
    constructor(window, slide) {
        // Instance "variables"/properties.
        this.window = window;
        this.slide = slide;
        // Instance "variables"/properties.
    }

    toString() {
        return 'ScheduledPolicyDataKey[{"window": ' + this.window + ', "slide": ' + this.slide + '}]';
    }
}
