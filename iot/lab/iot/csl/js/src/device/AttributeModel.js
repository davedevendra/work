/**
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * The model of an attribute in a device model.
 *
 * @class
 */
class AttributeModel {
    // Instance "variables"/properties...see constructor.

    constructor() {
        // Instance "variables"/properties.
        this.access = '';
        this.alias = '';
        /** @type {string} */
        this.description = '';
        this.deviceModelUrn = '';
        this.lowerBound = '';
        /** @type {string} */
        this.name = '';
        this.type = '';
        this.upperBound = '';
    }

    /**
     * Get the attribute name.
     *
     * @return {string} the attribute name from the device model.
     */
    getName() {
        return this.name;
    }

    /**
     * Get the URN of the device type model this attribute comes from.
     *
     * @return {string} the URN of the device type model
     */
    getDeviceModelUrn() {
        return this.deviceModelUrn;
    }

    /**
     * A human friendly description of the attribute. If the model does not contain a description, this method will
     * return an empty String.
     *
     * @return {string} the attribute description, or an empty string.
     */
    getDescription() {
        return this.description;
    }
}