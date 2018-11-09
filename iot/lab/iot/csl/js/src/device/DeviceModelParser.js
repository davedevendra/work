/**
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

//* @return {Promise<DeviceModel>}
class DeviceModelParser {
    /**
     * Returns a DeviceModel from a JSON string or object representation of a Device model.
     * @type {string|object}
     * @return {DeviceModel|null}
     */
    static fromJson(deviceModelJson) {
        if (!deviceModelJson) {
            return null;
        }

        let deviceModelJsonObj;

        // Is this a device model JSON string or object?  We need an object.
        if (deviceModelJson.hasOwnProperty('urn')) {
            deviceModelJsonObj = deviceModelJson;
        } else {
            deviceModelJsonObj = JSON.parse(deviceModelJson);
        }

        DeviceModelParser.printDeviceActions(deviceModelJsonObj.actions);
        DeviceModelParser.printDeviceAttributes(deviceModelJsonObj.attributes);
        DeviceModelParser.printDeviceFormats(deviceModelJsonObj.formats);
        let deviceModelActions = [];
        let deviceModelAttributes = [];
        let deviceModelFormats = [];

        if (deviceModelJsonObj.actions) {
            deviceModelJsonObj.actions.forEach(action => {
                deviceModelActions.push(new DeviceModelAction());
            });
        }

        if (deviceModelJsonObj.attributes) {
            deviceModelJsonObj.attributes.forEach(attribute => {
                deviceModelAttributes.push(new DeviceModelAttribute(deviceModelJson.urn,
                    attribute.name, attribute.description, attribute.type, attribute.lowerBound,
                    attribute.upperBound, attribute.access, attribute.alias,
                    attribute.defaultValue));
            });
        }

        if (deviceModelJsonObj.formats) {
            deviceModelJsonObj.formats.forEach(format => {
                let fields = [];

                if (format.fields) {
                    //format.value.fields?
                    format.fields.forEach(field => {
                        fields.push(new DeviceModelFormatField(field.name, field.description,
                            field.type, field.optional));
                    });
                }

                deviceModelFormats.push(new DeviceModelFormat(format.urn, format.name,
                    format.description, format.type, fields));
            });
        }

        return new DeviceModel(deviceModelJsonObj.urn, deviceModelJsonObj.name,
            deviceModelJsonObj.description, deviceModelAttributes,
            deviceModelActions, deviceModelFormats);
    }

    static printDeviceActions(actionsJson) {
        if (actionsJson) {
            for (let i = 0; i < actionsJson.length; i++) {
                let action = actionsJson[i];
            }
        }
    }

    static printDeviceAttributes(attributesJson) {
        if (attributesJson) {
            for (let i = 0; i < attributesJson.length; i++) {
                let attribute = attributesJson[i];
            }
        }
    }

    static printDeviceFormats(formatsJson) {
        if (formatsJson) {
            for (let i = 0; i < formatsJson.length; i++) {
                let format = formatsJson[i];
            }
        }
    }
}