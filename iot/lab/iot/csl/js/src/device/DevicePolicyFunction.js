/**
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

class DevicePolicyFunction {
    // Instance "variables"/properties...see constructor.

    /**
     * Create a point.
     *
     * @param {string} id - The ID of the function.
     * @param {Map<string, object>} parameters - The parameters of the function.
     */
    constructor(id, parameters) {
        // Instance "variables"/properties.
        /** @type {string} */
        this.id = id;
        /** @type {Map<string, Set<Function>>} */
        this.parameters = '';

        if (parameters && parameters.size !== 0) {
            this.parameters = parameters;
        } else {
            this.parameters = new Map();
        }
    }

    /**
     * Returns the function's ID.
     *
     * @return {string} the function's ID.
     */
    getId() {
        return this.id;
    }

    /**
     * Returns the function's parameters.
     *
     * @return {Map<String, object>} the function's parameters.
     */
    getParameters() {
        return this.parameters;
    }
//
// @Override public boolean equals(Object obj) {
//     if (this == obj) return true;
//     if (obj == null || obj.getClass() != DevicePolicy.Function.class) {
//         return false;
//     }
//     return this.id.equals(((DevicePolicy.Function)obj).id);
// }
//
// @Override
// public int hashCode() {
//     return this.id.hashCode();
// }
}
