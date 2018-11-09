/*
 * Copyright (c) 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

/**
 * WritableValue is a wrapper around a value that can be read or written.
 */
class WritableValue {

    constructor() {
        this.value = null;
    }

    /**
     * Get the value.
     *
     * @return {object} the value.
     */
    getValue() {
        return this.value;
    }

    /**
     * Set the value
     *
     * @param {object} value the value.
     */
    setValue(value) {
        this.value = value;
    }
}
