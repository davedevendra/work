/*
 * Copyright (c) 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

/**
 * Describes a field of a message.
 */
class DeviceModelFormatField {
    // Instance "variables"/properties...see constructor.

    /**
     *
     * @param {string} name
     * @param {string} description
     * @param {string} type
     * @param {boolean} optional
     */
    constructor(name, description, type, optional) {
        this.name = name;
        this.description = description;
        this.optional = optional;

        if (DeviceModelAttribute.Type.hasOwnProperty(type)) {
            this.type = type;
        } else {
            this.type = null;
        }
    }


    /**
     * @return {string}
     */
    getName() {
        return this.name;
    }


    /**
     * @return {string} - DeviceModelAttribute.Type
     */
    getType() {
        return this.type;
    }


    /**
     * @return {boolean}
     */
    isOptional() {
        return this.optional;
    }

    /**
     * @return {string}
     */
    toString() {
        let str = 'name = ' + this.name +
        ', description = ' + this.description +
        ', type = ' + this.type +
        ', optional = ' + this.optional + 'optional';

        return str;
    }
}
