/**
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * Detailed information on a device model. A device model is a specification
 * of the attributes, formats, and resources available on the device.
 */
class DeviceModel {
    // Instance "variables"/properties...see constructor.

    /**
     * @param {string} urn
     * @param {string} name
     * @param {string} description
     * @param {DeviceModelAttribute[]} deviceModelAttributes
     * @param {DeviceModelAction[]} deviceModelActions
     * @param {DeviceModelFormat[]} deviceModelFormats
     */
    constructor(urn, name, description, deviceModelAttributes, deviceModelActions,
                deviceModelFormats)
    {
        // Instance "variables"/properties.
        /**
         * The URN of the device model.
         *
         * @type {string}
         */
        this.urn = urn;
        /**
         * The device model's name.
         *
         * @type {string}
         */
        this.name = name;
        /**
         * The device model's description.
         *
         * @type {string}
         */
        this.description = description;

        /**
         * Map of attribute names to DeviceModelAttribute's.
         * {Map<String, DeviceModelAttribute[]>}
         */
        // {Map<string, DeviceModelAttribute>}
        this.deviceModelAttributes = new Map();
        // {Map<String, DeviceModelAction[]>}
        this.deviceModelActions = new Map();
        // {Map<String, DeviceModelFormat[]>}
        this.deviceModelFormats = new Map();

        if (deviceModelAttributes) {
            deviceModelAttributes.forEach(deviceModelAttribute => {
                let attributeName = deviceModelAttribute.name;

                if (!this.deviceModelAttributes.get(attributeName)) {
                    this.deviceModelAttributes.set(attributeName, deviceModelAttribute);
                }
            });
        }

        if (deviceModelActions) {
            for (let i = 0; i < deviceModelActions.length; i++) {
                let actName = deviceModelActions[i].name;

                if (this.deviceModelActions.get(actName) == null) {
                    let deviceModelAction = new DeviceModelAction(actName,
                        deviceModelActions[i].description, deviceModelActions[i].type,
                        deviceModelActions[i].lowerBound, deviceModelActions[i].upperBound,
                        deviceModelActions[i].alias);

                    this.deviceModelActions.set(actName, deviceModelAction);
                }
            }
        }

        if (deviceModelFormats) {
            for (let i = 0; i < deviceModelFormats.length; i++) {
                let formatUrn = deviceModelFormats[i].urn;

                if (!this.deviceModelFormats.get(formatUrn)) {
                    let fields = [];

                    if (deviceModelFormats[i].value &&
                        deviceModelFormats[i].value.fields &&
                        deviceModelFormats[i].value.fields.length > 0)
                    {
                        let fs = deviceModelFormats[i].value.fields;

                        fs.forEach(v => {
                            fields.push(new DeviceModelFormatField(v.name, v.description, v.type,
                                v.optional));
                        });
                    }

                    let deviceModelFormat = new DeviceModelFormat(deviceModelFormats[i].urn,
                        deviceModelFormats[i].name, deviceModelFormats[i].description,
                        deviceModelFormats[i].type, fields);

                    this.deviceModelFormats.set(formatUrn, deviceModelFormat);
                }
            }
        }
    }

    /**
     * Returns the actions for this device model.
     *
     * @return {Map<string, DeviceModelAction[]>} the actions for this device model.
     */
    getDeviceModelActions() {
        return this.deviceModelActions;
    }

    /**
     * Returns the attributes for this device model.
     *
     * @return {Map<string, DeviceModelAttribute[]>} the attributes for this device model.
     */
    getDeviceModelAttributes() {
        return this.deviceModelAttributes;
    }

    /**
     * @return {Map<string, DeviceModelFormat[]>}
     */
    getDeviceModelFormats() {
        return this.deviceModelFormats;
    }

    /**
     * Returns the device model's description.
     *
     * @return {string} the device model's description.
     */
    getDescription() {
        return this.description;
    }

    /**
     * Returns the device model's name.
     *
     * @return {string} the device model's name.
     */
    getName() {
        return this.name;
    }

    /**
     * Returns the device model's URN.
     *
     * @return {string} the device model's URN.
     */
    getUrn() {
        return this.urn;
    }

    /**
     * Returns a string representation of this device model.
     *
     * @return {string}
     */
    // toString() {
    //     // let StringBuilder = require('stringbuilder');
    //     // let firstItem = true;
    //     // let b = new StringBuilder("urn = ");
    //     // b.append("\t");
    //     // b.append(urn);
    //     // b.append(",\n\tname = ");
    //     // b.append(name);
    //     // b.append(",\n\tdescription = ");
    //     // b.append(description);
    //     // b.append(",\n\tattributes = [");
    //     //
    //     // for (let attribute of this.deviceModelAttributes.values()) {
    //     //     if (!firstItem) {
    //     //         b.append(",");
    //     //     } else {
    //     //         firstItem = false;
    //     //     }
    //     //
    //     //     b.append("\n\t{");
    //     //     b.append(attribute);
    //     //     b.append("}");
    //     // }
    //     //
    //     // if (!firstItem) {
    //     //     b.append("\n\t");
    //     // }
    //     //
    //     // b.append("],\n\tactions = [");
    //     // firstItem = true;
    //     //
    //     // for (let action of this.deviceModelActions.values()) {
    //     //     if (!firstItem) {
    //     //         b.append(",");
    //     //     } else {
    //     //         firstItem = false;
    //     //     }
    //     //
    //     //     b.append("\n\t{");
    //     //     b.append(action);
    //     //     b.append("}");
    //     // }
    //     //
    //     // if (!firstItem) {
    //     //     b.append("\n\t");
    //     // }
    //     //
    //     // b.append("],\n\tformats = [");
    //     // firstItem = true;
    //     //
    //     // for (let format of this.deviceModelFormats.values()) {
    //     //     if (!firstItem) {
    //     //         b.append(",");
    //     //     } else {
    //     //         firstItem = false;
    //     //     }
    //     //
    //     //     b.append("\n\t{");
    //     //     b.append(format);
    //     //     b.append("}");
    //     // }
    //     //
    //     // if (!firstItem) {
    //     //     b.append("\n\t");
    //     // }
    //     //
    //     // b.append("]");
    //     // return b.toString();
    //     return '';
    //  }
}