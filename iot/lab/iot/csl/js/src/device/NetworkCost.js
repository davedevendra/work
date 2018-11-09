/**
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * DeviceFunction is an abstraction of a policy device function.
 */
class NetworkCost {
    /**
     * Get the cost of the NetworkCost given by the string value. The "property" parameter is just
     * used for logging. The defaultValue is the value returned if the value is null or not a valid
     * NetworkCost.
     *
     * @param {string} value
     * @param {string} property
     * @param {string} defaultValue @type {NetworkCost.Type}
     * @return {number}
     */
    static getCost(value, property, defaultValue) {
        // @type {NetworkCost}
        let networkCost = null;

        if (value) {
            try {
                // @type {string}
                let upperValue = value.toUpperCase();
                upperValue = upperValue.replace('\\(.*', '');
                networkCost = upperValue.valueOf();
            } catch (error) {
                console.log('Invalid "' + property + '", value: "' + value + '"');
            }
        }

        if (!networkCost) {
            // Not given or illegal value.
            networkCost = defaultValue;
            console.log('Defaulting "' + property + '" to: "' + networkCost + '"');
        }

        return NetworkCost.ordinal(networkCost);
    }

    /**
     * Returns the ordinal value of the given type in the list of NetworkCost.Type's.
     *
     * @param {string} type the NetworkCost.Type.
     * @return {number} the ordinal value of the type in the NetworkCost.Type list.
     */
    static ordinal(type) {
        switch(type) {
            case NetworkCost.Type.ETHERNET:
                return 1;
            case NetworkCost.Type.CELLULAR:
                return 2;
            case NetworkCost.Type.SATELLITE:
                return 3;
            default:
                throw new Error(type + ' is not one of NetworkCost.Type.');
        }
    }
}

/**
 * The order of these is in increasing network cost.  For example, the cost of data over ethernet is
 * much lower then the cost of data over satellite.
 *
 * Note: The order of these is important - DO NOT CHANGE THE ORDER.  If you do changed the order,
 * also updte the getTypeOrdinal function.
 *
 * @type {{ETHERNET: string, CELLULAR: string, SATELLITE: string}}
 */
NetworkCost.Type = {
    ETHERNET: 'ETHERNET',
    CELLULAR: 'CELLULAR',
    SATELLITE: 'SATELLITE'
};