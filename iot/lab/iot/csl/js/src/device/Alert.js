/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * The Alert is an object that represents an alert type message format defined in the formats
 * section of the device model. Alerts can be used to send alert messages to the server.
 * <p>
 * The Alert API is specific to the device client library and the alerts can be created by the
 * VirtualDevice objects or using them.  For setting the fields of the alert as defined in the
 * model, the fields property of the alert will be used e.g.:<br>
 * <code>alert.fields.temp = 50;</code>
 * <p>
 * The constructor of the Alert should not be used directly but the
 * {@link iotcs.device.VirtualDevice#createAlert} method should be used for creating alert objects.
 *
 * @alias iotcs.device.Alert
 * @class
 * @memberof iotcs.device
 * @see {@link iotcs.device.VirtualDevice#createAlert}
 *
 * @param {iotcs.device.VirtualDevice} virtualDevice - The virtual device that has in it's device
 *        model the alert specification.
 * @param {string} formatUrn - The URN format of the alert spec.
 */
lib.device.Alert = function (virtualDevice, formatUrn) {
    _mandatoryArg(virtualDevice, lib.device.VirtualDevice);
    _mandatoryArg(formatUrn, 'string');

    var alertSpec = virtualDevice[formatUrn];

    if (!alertSpec.urn || (alertSpec.type !== 'ALERT')) {
        lib.error('alert specification in device model is invalid');
        return;
    }

    this.device = virtualDevice;

    var spec = {
        urn: alertSpec.urn,
        description: (alertSpec.description || ''),
        name: (alertSpec.name || null)
    };

    if (alertSpec.value && alertSpec.value.fields && Array.isArray(alertSpec.value.fields)) {
        /**
         * The fields object for this Alert.  Specific fields can be referenced by referencing the
         * field name from the fields object.  For example, to reference a field named 'myName', use
         * 'alertName.fields.myName'.
         *
         * @name iotcs.device.Alert#fields
         * @public
         * @readonly
         * @type {object}
         */
        Object.defineProperty(this, 'fields', {
            enumerable: true,
            configurable: false,
            writable: false,
            value: {}
        });

        /** @private */
        Object.defineProperty(this, '_', {
            enumerable: false,
            configurable: false,
            writable: false,
            value: {}
        });

        var self = this;

        alertSpec.value.fields.forEach(function (field) {
            self._[field.name] = {};
            self._[field.name].type = field.type.toUpperCase();
            self._[field.name].optional = field.optional;
            self._[field.name].name = field.name;
            self._[field.name].value = null;
            Object.defineProperty(self.fields, field.name, {
                enumerable: false,
                configurable: false,
                get: function () {
                    return self._[field.name].value;
                },
                set: function (newValue) {

                    if (!self._[field.name].optional && ((typeof newValue === 'undefined') || (newValue === null))) {
                        lib.error('trying to unset a mandatory field in the alert');
                        return;
                    }

                    newValue = _checkAndGetNewValue(newValue, self._[field.name]);

                    if (typeof newValue === 'undefined') {
                        lib.error('trying to set an invalid type of field in the alert');
                        return;
                    }

                    self._[field.name].value = newValue;
                }
            });
        });
    }

    /**
     * The URN of this Alert.  This is the Alert's device model URN.
     *
     * @name iotcs.device.Alert#urn
     * @public
     * @readonly
     * @type {string}
     */
    Object.defineProperty(this, 'urn', {
        enumerable: true,
        configurable: false,
        writable: false,
        value: spec.urn
    });

    /**
     * The name of this Alert.
     *
     * @name iotcs.device.Alert#name
     * @public
     * @readonly
     * @type {string}
     */
    Object.defineProperty(this, 'name', {
        enumerable: true,
        configurable: false,
        writable: false,
        value: spec.name
    });

    /**
     * The description of this Alert.
     *
     * @name iotcs.device.Alert#description
     * @public
     * @readonly
     * @type {string}
     */
    Object.defineProperty(this, 'description', {
        enumerable: true,
        configurable: false,
        writable: false,
        value: spec.description
    });

    /**
     * (Optional)
     * Callback function called when there is an error sending the Alert.  May be set to null to
     * un-set the callback.
     *
     * @name iotcs.device.Alert#onError
     * @public
     * @type {?iotcs.device.Alert~onErrorCallback}
     *       response.
     */
    Object.defineProperty(this, 'onError', {
        enumerable: true,
        configurable: false,
        get: function () {
            return this._.onError;
        },
        set: function (newOnError) {
            if (newOnError && (typeof newOnError !== 'function')) {
                lib.error('Trying to set onError to something that is not a function.');
                return;
            }

            this._.onError = newOnError;
        }
    });

    this._.onError = null;
};

/**
 * This method is used to actually send the alert message to the server.  The default severity for
 * the alert sent is SIGNIFICANT. All mandatory fields (according to the device model definition)
 * must be set before sending, otherwise an error will be thrown.  Any error that can arise while
 * sending will be handled by the Alert.onError handler, if set.
 * <p>
 * After a successful raise all the values are reset so to raise again the values must be first set.
 *
 * @function raise
 * @memberof iotcs.device.Alert.prototype
 * @public
 * @see {@link iotcs.device.VirtualDevice}
 */
lib.device.Alert.prototype.raise = function () {
    var message = lib.message.Message.AlertMessage.buildAlertMessage(this.urn, this.description,
        lib.message.Message.AlertMessage.Severity.SIGNIFICANT);

    message.source(this.device.getEndpointId());
    message.onError = this.onError;
    var messageDispatcher = new lib.device.util.MessageDispatcher(this.device.client._.internalDev);
    var storageObjects = [];

    for (var key in this._) {
        if (key !== 'onError') {
            var field = this._[key];

            if (!field.optional && (!field.value || (typeof field.value === 'undefined'))) {
                lib.error('All mandatory fields not set.');
                return;
            }

            if (field.value && (typeof field.value !== 'undefined')) {
                if ((field.type === "URI") && (field.value instanceof lib.StorageObject)) {
                    var syncStatus = field.value.getSyncStatus();

                    if (syncStatus === lib.device.StorageObject.SyncStatus.NOT_IN_SYNC ||
                        syncStatus === lib.device.StorageObject.SyncStatus.SYNC_PENDING) {
                        storageObjects.push(field.value);
                    }

                    field.value._.setSyncEventInfo(key, this.device);
                    field.value.sync();
                }

                message.dataItem(key, field.value);
            }
        }
    }

    storageObjects.forEach(function (storageObject) {
        messageDispatcher._.addStorageDependency(storageObject, message._.internalObject.clientId);
    });

    messageDispatcher.queue(message);

    for (var key1 in this._) {
        this._[key1].value = null;
    }
};

// Callback JSDocs.
/**
 * Callback function called when there is an error sending the Alert.
 *
 * @callback iotcs.device.Alert~onErrorCallback
 *
 * @param {string} error - The error which occurred when sending this Alert.
 */
