/**
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

//@TODO: missing JSDOC

/**
 * @class
 */
/** @ignore */
$impl.Action = function (actionSpec) {
    _mandatoryArg(actionSpec, 'object');

    if (!actionSpec.name) {
        lib.error('attribute specification in device model is incomplete');
        return;
    }

    var spec = {
        name: actionSpec.name,
        description: (actionSpec.description || ''),
        argType: (actionSpec.argType || null),
        alias: (actionSpec.alias || null),
        range: (actionSpec.range ? _parseRange(actionSpec.argType, actionSpec.range) : null)
    };

    /** @private */
    Object.defineProperty(this, '_', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: {}
    });

    // public members

    /**
     * @memberof iotcs.Action
     * @member {string} name - the name of this action
     */
    Object.defineProperty(this, 'name', {
        enumerable: true,
        configurable: false,
        writable: false,
        value: spec.name
    });

    /**
     * @memberof iotcs.Action
     * @member {string} description - the description of this action
     */
    Object.defineProperty(this, 'description', {
        enumerable: true,
        configurable: false,
        writable: false,
        value: spec.description
    });

    /**
     * @memberof iotcs.Action
     * @member {function(Object)} onExecute - the action to perform when the an execute() is
     * received from the other party
     */
    Object.defineProperty(this, 'onExecute', {
        enumerable: false,
        configurable: false,
        get: function () {
            return this._.onExecute;
        },
        set: function (newValue) {
            if (!newValue || (typeof newValue !== 'function')) {
                lib.error('trying to set something to onExecute that is not a function!');
                return;
            }
            this._.onExecute = newValue;
        }
    });
    this._.onExecute = null;

    /** @private */
    this.checkAndGetVarArg = function (arg, virtualDevice, callback) {
        var isURICallback = false;
        if (!spec.argType) {
            if (typeof arg !== 'undefined') {
                lib.error('invalid number of arguments');
                return;
            }
        } else {
            if (typeof arg === 'undefined') {
                lib.error('invalid number of arguments');
                return;
            }

            if (spec.argType === 'URI') {
                if (arg instanceof lib.ExternalObject) {
                    arg = arg.getURI();
                } else if (typeof arg === 'string') {
                    // get uri from server
                    if (_isStorageCloudURI(arg)) {
                        isURICallback = true;
                        virtualDevice.client._.internalDev.createStorageObject(arg, function (storage, error) {
                            if (error) {
                                lib.error('Error during creation storage object: ' + error);
                                return;
                            }

                            var storageObject = new lib.device.StorageObject(storage.getURI(), storage.getName(),
                                storage.getType(), storage.getEncoding(), storage.getDate(), storage.getLength());
                            storageObject._.setDevice(virtualDevice.client._.internalDev);
                            storageObject._.setSyncEventInfo(spec.name, virtualDevice);

                            if (!_matchType(spec.argType, storageObject)) {
                                lib.error('type mismatch; action "'+spec.name+'" requires arg type [' + spec.argType + ']');
                                return;
                            }
                            callback(storageObject);
                        });
                        return;
                    } else {
                        arg = new lib.ExternalObject(arg);
                    }
                } else {
                    lib.error('invalid URI parameter');
                    return;
                }
            }

            if (!_matchType(spec.argType, arg)) {
                lib.error('type mismatch; action "'+spec.name+'" requires arg type [' + spec.argType + ']');
                return;
            }
            if (spec.range && ((arg<spec.range.low) || (arg>spec.range.high))) {
                lib.error('trying to use an argument which is out of range ['+spec.range.low+' - '+spec.range.high+']');
                return;
            }
        }
        if (!isURICallback) {
            callback(arg, true);
        }
    };
};
