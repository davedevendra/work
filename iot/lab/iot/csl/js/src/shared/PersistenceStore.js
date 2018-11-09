/**
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */
lib.PersistenceStore = function () {
    var self = this;

    Object.defineProperty(this, '_', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: {}
    });
};


/**
 * Returns the value for the specified key, or defaultValue if no key is found.
 *
 * @param key {string} the key for the value to retrieve.
 * @param defaultValue {string} the value to return if no key is found.
 */
lib.PersistenceStore.prototype.getOpaque = function(key, defaultValue) {
    return defaultValue;
}

/**
 * Returns the persistence store for the given name.
 *
 * @ignore
 * @param name {string} the name of the persistence store to retrieve.
 * @returns {lib.PersistenceStore}
 */
lib.PersistenceStore.prototype.getPersistenceStore = function (name) {
    _mandatoryArg(name, string);

    // final Object dpmObj = persistenceStore.getOpaque(DevicePolicyManager.class.getName(), null);
    //
    // if (!dpmObj) {
    //     debugSevere("Cannot access DevicePolicyManager for " + 
    //         directlyConnectedDevice.getEndpointId());
    //     return null;
    // }
    //
    // return DevicePolicyManager.class.cast(dpmObj);

    return null;
};
