/**
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * DeviceModelAttribute is the model of an attribute in a {@link DeviceModel}.
 */
class DeviceModelAttribute {
    // Instance "variables"/properties...see constructor.
    /**
     *
     * @param {string} urn
     * @param {string} name
     * @param {string} description
     * @param {Type} type
     * @param {number} lowerBound
     * @param {number} upperBound
     * @param {Access} access
     * @param {string} alias
     * @param {object} defaultValue
     * @constructor
     */
    constructor(urn, name, description, type, lowerBound, upperBound, access, alias, defaultValue) {
        // Instance "variables"/properties.
        /**
         *
         *
         * @type {Access}
         */
        this.access = access;
        /**
         * The attribute's name.
         *
         * @type {string}
         * @deprecated
         */
        this.alias = alias;
        /**
         * The attribute's default value.
         *
         * @type {object}
         */
        this.defaultValue = defaultValue;
        /**
         * The attribute's description.
         *
         * @type {string}
         */
        this.description = description;
        /**
         * The name of the attribute
         *
         * @type {string}
         */
        this.name = name;
        /**
         * The attribute's lower bound.
         *
         * @type {number}
         */
        this.lowerBound = lowerBound;
        /**
         * The attribute type.
         *
         * @type {Type}
         */
        this.type = type;
        /**
         * The URN of the attribute.
         *
         * @type {string}
         */
        this.urn = urn;
        /**
         * The attribute's upper bound.
         *
         * @type {number}
         */
        this.upperBound = upperBound;
    }

    /**
     * Return the access rules for the attribute. The default is READ-ONLY
     *
     * @return {Access} the access rule for the attribute
     */
    getAccess() {
        return this.access;
    }

    /**
     * Get the attribute name.
     *
     * @return {string} an alternative name for the attribute.
     * @deprecated Use {@link #getName()}
     */
    getAlias() {
        return this.alias;
    }

    /**
     * Get the default value of the attribute as defined by the device model. If there is no
     * {@code defaultValue} for the attribute in the device model, then this method will return
     * {@code null}. The value {@code null} is <em>not</em> a default value.
     *
     * @return {object} the default value of the attribute, or {@code null} if no default is
     *         defined.
     */
    getDefaultValue() {
        return this.defaultValue;
    }

    /**
     * A human friendly description of the attribute. If the model does not
     * contain a description, this method will return an empty String.
     *
     * @return {string} the attribute description, or an empty string.
     */
    getDescription() {
        return this.description;
    }

    /**
     * Get the URN of the device type model this attribute comes from.
     *
     * @return {string} the URN of the device type model.
     */
    getModel() {
        return this.urn;
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
     * The data type of the attribute. If the access type of the attribute is
     * executable, this method will return null.
     *
     * @return {Type} the attribute's data type, or null.
     */
    getType() {
        return this.type;
    }

    /**
     * For {@link Type#NUMBER} only, give the lower bound of the
     * acceptable range of values. Null is always returned for attributes
     * other than {@code NUMBER} type.
     *
     * @return {number} a Number, or null if no lower bound has been set.
     */
    getLowerBound() {
        return this.lowerBound;
    }

    /**
     * For {@link Type#NUMBER} only, give the upper bound of the
     * acceptable range of values. Null is always returned for attributes
     * other than {@code NUMBER} type.
     *
     * @return {number} a Number, or null if no upper bound has been set
     */
    getUpperBound() {
        return this.upperBound;
    }
}

DeviceModelAttribute.Access = {
    EXECUTABLE: 'EXECUTABLE',
    READ_ONLY: 'READ_ONLY',
    READ_WRITE: 'READ_WRITE',
    WRITE_ONLY: 'WRITE_ONLY'
};

DeviceModelAttribute.Type = {
    BOOLEAN: 'BOOLEAN',
    DATETIME: 'DATETIME',
    INTEGER: 'INTEGER',
    NUMBER: 'NUMBER',
    STRING: 'STRING',
    URI: 'URI'
};