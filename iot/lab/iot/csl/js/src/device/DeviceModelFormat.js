/*
 * Copyright (c) 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

/**
 * DeviceModelFormat
 */
class DeviceModelFormat {
    // Instance "variables"/properties...see constructor.

    /**
     * @param {string} urn
     * @param {string} name
     * @param {string} description
     * @param {lib.message.Message.Type} type
     * @param {DeviceModelFormatField[]} fields
     */
    constructor(urn, name, description, type, fields) {
        // Instance "variables"/properties.
        this.urn = urn;
        this.name = name;
        this.description = description;
        this.fields = fields;

        if (lib.message.Message.Type.hasOwnProperty(type)) {
            this.type = type;
        } else {
            this.type = null;
        }
    }

    /**
     * @return {string}
     */
    getDescription() {
        return this.description;
    }

    /**
     *
     * @return {DeviceModelFormatField[]}
     */
    getFields() {
        return this.fields;
    }

    /**
     * @return {string}
     */
    getName() {
        return this.name;
    }

    /**
     * @return {string}
     */
    getType() {
        return this.type;
    }

    /**
     * @return {string}
     */
    getUrn() {
        return this.urn;
    }


    /**
     * @return {string}
     */
    toString() {
        let str =
            'name = ' + this.name +
            ', description = ' + this.description +
            ', type = ' + this.type +
            ',\n fields = [';


        let firstItem = true;

        this.fields.forEach(field => {
            if (!firstItem) {
                str += ',';
            } else {
                firstItem = false;
            }

            str += '\n {' + field + '}"';
        });

        if (!firstItem) {
            str += '\n';
        }

        str += ' ]';
        return str;
    }
}
