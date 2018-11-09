/*
 * Copyright (c) 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

/**
 * DeviceModelAction
 */
class DeviceModelAction {
    /**
     *
     * @param {string} name
     * @param {string} description
     * @param {DeviceModelAttribute.Type} type
     * @param {number} lowerBound
     * @param {number} upperBound
     * @param {string} alias
     */
    constructor(name, description, type, lowerBound, upperBound, alias) {
        // @type {string}
        this.alias = alias;
        // @type {DeviceModelAttributeType}
        this.argType = type;
        // @type {string}
        this.description = description;
        // @type {string}
        this.name = name;

        // @type {number}
        this.lowerBound = null;
        // @type {number}
        this.upperBound = null;

        if (this.argType === DeviceModelAttribute.Type.INTEGER ||
            this.argType === DeviceModelAttribute.Type.NUMBER)
        {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
        }

        this.alias = alias;
    }


    /**
     *
     * @return {string}
     */
    getAlias() {
        return this.alias;
    }

    /**
     * @return {string} ({DeviceModelAttribute.Type})
     */
    getArgType() {
        return this.argType;
    }

    /**
     * @return {string}
     */
    getDescription() {
        return this.description;
    }

    /**
     * @return {number}
     */
    getLowerBound() {
        return this.lowerBound;
    }

    /**
     * @return {string} name
     */
    getName() {
        return this.name;
    }

    /**
     * @return {number}
     */
    getUpperBound() {
        return this.upperBound;
    }

    /**
     *
     * @return {string}
     */
    toString() {
        return 'name = ' + this.name +
            ', description = ' + this.description +
            ', type = ' + this.argType +
            ', lowerBound = ' + this.lowerBound +
            ', upperBound = ' + this.upperBound +
            ', alias = ' + this.alias;
    }
}
