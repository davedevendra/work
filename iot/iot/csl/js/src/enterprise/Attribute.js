/**
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

//@TODO: missing jsdoc

/**
 * @class
 */
/** @ignore */
$impl.Attribute = function (attributeSpec) {
    _mandatoryArg(attributeSpec, 'object');
    
    if ((!attributeSpec.name) || (!attributeSpec.type)) {
        lib.error('attribute specification in device model is incomplete');
        return;
    }

    var spec = {
        name: attributeSpec.name,
        description: (attributeSpec.description || ''),
        type: attributeSpec.type,
        writable: (attributeSpec.writable || false),
        alias: (attributeSpec.alias || null),
        range: (attributeSpec.range ? _parseRange(attributeSpec.type, attributeSpec.range) : null),
        defaultValue: ((typeof attributeSpec.defaultValue !== 'undefined') ? attributeSpec.defaultValue : null)
    };
    
    /** @private */
    Object.defineProperty(this, '_', {
        enumerable: false,
        configurable: false,
        writable: true,
        value: {}
    });
    this._.value = spec.defaultValue;
    this._.lastKnownValue = spec.defaultValue;
    this._.lastUpdate = null;
    this._.localUpdateRequest = false;

    var self = this;

    //@TODO: see comment in AbstractVirtualDevice; this is not clean especially it is supposed to be a private function and yet used in 4 other objects ...etc...; this looks like a required ((semi-)public) API ... or an $impl.XXX or a function ()...

    /** @private */
    Object.defineProperty(this._, 'isValidValue', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (newValue) {
            try {
                newValue = _checkAndGetNewValue(newValue, spec);
            } catch (e) {
                lib.createError('invalid value', e);
                return false;
            }

            if (typeof newValue === 'undefined') {
                lib.createError('trying to set an invalid value');
                return false;
            }

            if (spec.range && ((newValue < spec.range.low) || (newValue > spec.range.high))) {
                lib.createError('trying to set a value out of range [' + spec.range.low + ' - ' + spec.range.high + ']');
                return false;
            }
            return true;
        }
    });

    /** @private */
    Object.defineProperty(this._, 'getNewValue', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (newValue, virtualDevice, callback) {
            try {
                if (self._.isValidValue(newValue)) {
                    _checkAndGetNewValueCallback(newValue, spec, virtualDevice, function(attributeValue) {
                        if (callback) {
                            callback(attributeValue);
                        }
                    });
                }
            } catch (e) {
                lib.createError('invalid value ', e);
            }
        }
    });

    /** @private */
    Object.defineProperty(this._, 'remoteUpdate', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (newValue) {
            try {
                if (self._.isValidValue(newValue)) {
                    self._.lastUpdate = Date.now();

                    if (_equal(newValue, self._.lastKnownValue, spec)) {
                        return;
                    }

                    self._.lastKnownValue = newValue;

                    if (!(spec.writable && self._.localUpdateRequest)) {
                        var consoleValue = (self._.value instanceof lib.ExternalObject) ? self._.value.getURI() : self._.value;
                        var consoleNewValue = (newValue instanceof lib.ExternalObject) ? newValue.getURI() : newValue;
                        lib.log('updating attribute "' + spec.name + '" of type "' + spec.type + '" from ' + consoleValue + ' to ' + consoleNewValue);
                        self._.value = newValue;
                    }
                }
            } catch (e) {
                lib.createError('invalid value ', e);
            }
        }
    });

    /** @private */
    Object.defineProperty(this._, 'onUpdateResponse', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (error) {
            if (error) {
                var consoleValue = (self._.value instanceof lib.ExternalObject)? self._.value.getURI() : self._.value;
                var consoleLastValue = (self._.lastKnownValue instanceof lib.ExternalObject)?
                    self._.lastKnownValue.getURI() : self._.lastKnownValue;
                lib.log('updating attribute "' + spec.name + '" of type "' + spec.type + '" from ' + consoleValue + ' to ' + consoleLastValue);
                self._.value = self._.lastKnownValue;
            }
            self._.lastUpdate = new Date().getTime();
            self._.localUpdateRequest = false;
        }
    });

    /** @private */
    Object.defineProperty(this._, 'localUpdate', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (newValue, nosync) {
            if (self._.isValidValue(newValue)) {
                newValue = _checkAndGetNewValue(newValue, spec);

                if (!spec.writable) {
                    lib.error('illegal write access; attribute "' + spec.name + '" is read-only"');
                    return;
                }

                if (_equal(newValue, self._.value, spec)) {
                    return;
                }

                var consoleValue = (self._.value instanceof lib.ExternalObject) ? self._.value.getURI() : self._.value;
                var consoleNewValue = (newValue instanceof lib.ExternalObject) ? newValue.getURI() : newValue;
                lib.log('updating attribute "' + spec.name + '" of type "' + spec.type + '" from ' + consoleValue + ' to ' + consoleNewValue);
                self._.value = newValue;
                self._.localUpdateRequest = true;

                if (!nosync) {
                    if (!self.device || !(self.device instanceof lib.enterprise.VirtualDevice)) {
                        return;
                    }
                    var attributes = {};
                    attributes[spec.name] = newValue;
                    self.device.controller.updateAttributes(attributes, true);
                }
            }  else {
                lib.error('invalid value');
            }
        }
    });


    // public properties

    /**
     * @memberof iotcs.Attribute 
     * @member {string} id - the unique/reproducible
     * id for this attribute (usually its name)
     */
    Object.defineProperty(this, 'id', {
        enumerable: true,
        configurable: false,
        writable: false,
        value: spec.name
    });

    /**
     * @memberof iotcs.Attribute
     * @member {string} description - the description
     * of this attribute
     */
    Object.defineProperty(this, 'description', {
        enumerable: true,
        configurable: false,
        writable: false,
        value: spec.description
    });

    /**
     * @memberof iotcs.Attribute 
     * @member {string} type - one of <code>INTEGER</code>,
     * <code>NUMBER</code>, <code>STRING</code>, <code>BOOLEAN</code>, 
     * <code>DATETIME</code> 
     */
    Object.defineProperty(this, 'type', {
        enumerable: true,
        configurable: false,
        writable: false,
        value: spec.type
    });

    Object.defineProperty(this, 'defaultValue', {
        enumerable: true,
        configurable: false,
        writable: false,
        value: spec.defaultValue
    });

    /**
     * @ignore
     * @memberof iotcs.Attribute 
     * @member {boolean} writable - expressing whether
     * this attribute is writable or not 
     */
    Object.defineProperty(this, 'writable', {
        enumerable: true,
        configurable: false,
        writable: false,
        value: spec.writable
    });

    /**
     * @memberof iotcs.Attribute 
     * @member {function(Object)} onChange - function called
     * back when value as changed on the server side. Callback
     * signature is <code>function (e) {}</code>, where <code>e</code> 
     * is <code>{'attribute':this, 'newValue':, 'oldValue':}</code>
     */
    Object.defineProperty(this, 'onChange', {
        enumerable: false,
        configurable: false,
        get: function () {
            return this._.onChange;
        },
        set: function (newValue) {
            if (!newValue || (typeof newValue !== 'function')) {
                lib.error('trying to set to onChange something that is not a function!');
                return;
            }
            this._.onChange = newValue;
        }
    });

    /**
     * @memberof iotcs.Attribute 
     * @member {function(Object)} onError - function called
     * back when value could not be changed. Callback signature is
     * <code>function (e) {}</code>, where <code>e</code> is 
     * <code>{'attribute':this, 'newValue':, 'tryValue':}</code>
     */
    Object.defineProperty(this, 'onError', {
        enumerable: false,
        configurable: false,
        get: function () {
            return this._.onError;
        },
        set: function (newValue) {
            if (!newValue || (typeof newValue !== 'function')) {
                lib.error('trying to set to onError something that is not a function!');
                return;
            }
            this._.onError = newValue;
        }
    });

    /**
     * @memberof iotcs.Attribute 
     * @member {(number|string|boolean|Date)} value - used for setting or
     * getting the current value of this attribute (subject to whether it is writable
     * or not).
     */
    Object.defineProperty(this, 'value', {
        enumerable: false,
        configurable: false,
        get: function () {
            return this._.value;
        },
        set: function (newValue) {
            this._.localUpdate(newValue, false);
        }
    });

    /**
     * @memberof iotcs.Attribute 
     * @member {(number|string|boolean|Date)} lastKnownValue - 
     * used for getting the current value of this attribute 
     */
    Object.defineProperty(this, 'lastKnownValue', {
        enumerable: false,
        configurable: false,
        get: function () {
            return this._.lastKnownValue;
        },
        set: function (newValue) {
        }
    });

    /**
     * @memberof iotcs.Attribute
     * @member {Date} lastUpdate - the date of the last value update
     */
    Object.defineProperty(this, 'lastUpdate', {
        enumerable: false,
        configurable: false,
        get: function () {
            return this._.lastUpdate;
        },
        set: function (newValue) {
        }
    });
};

//////////////////////////////////////////////////////////////////////////////

/** @ignore */
function _parseRange(type, rangeStr) {
    _mandatoryArg(type, 'string');
    _mandatoryArg(rangeStr, 'string');
    if ((type !== 'NUMBER') && (type !== 'INTEGER')) {
        lib.error('device model specification is invalid');
        return;
    }
    var rangeLimits = rangeStr.split(',');
    if (rangeLimits.length != 2) {
        lib.error('device model specification is invalid');
        return;
    }
    var first = parseFloat(rangeLimits[0]);
    var second = parseFloat(rangeLimits[1]);
    return { low:Math.min(first,second), high:Math.max(first,second) };
}

/** @ignore */
function _matchType(reqType, value) {
    _mandatoryArg(reqType, 'string');
    switch(reqType) {
        case 'INTEGER':
            return ((typeof value === 'number') && (value % 1 === 0));
        case 'NUMBER':
            return (typeof value === 'number');
        case 'STRING':
            return (typeof value === 'string');
        case 'BOOLEAN':
            return (typeof value === 'boolean');
        case 'DATETIME':
            return (value instanceof Date);
        case 'URI':
            return (value instanceof lib.ExternalObject) || (typeof value === 'string');
        default:
            lib.error('illegal state');
            return;
    }
}

/** @ignore */
function _checkAndGetNewValue(newValue, spec) {
    if (spec.type === 'DATETIME') {
        if (typeof newValue === 'number') {
            var str = '' + newValue;
            if (str.match(/^[-+]?[1-9]\.[0-9]+e[-]?[1-9][0-9]*$/)) {
                newValue = newValue.toFixed();
            }
        }
        newValue = new Date(newValue);
        if (isNaN(newValue.getTime())) {
            lib.error('invalid date in date time parameter');
            return;
        }
    }

    if (!_matchType(spec.type, newValue)) {
        lib.error('type mismatch; attribute "' + spec.name + '" has type [' + spec.type + ']');
        return;
    }
    return newValue;
}

/** @ignore */
function _checkAndGetNewValueCallback(newValue, spec, virtualDevice, callback) {
    var isURICallback = false;
    if (spec.type === 'DATETIME') {
        if (typeof newValue === 'number') {
            var str = '' + newValue;
            if (str.match(/^[-+]?[1-9]\.[0-9]+e[-]?[1-9][0-9]*$/)) {
                newValue = newValue.toFixed();
            }
        }
        newValue = new Date(newValue);
        if (isNaN(newValue.getTime())) {
            lib.error('invalid date in date time parameter');
            return;
        }
    }
    if (spec.type === 'URI') {
        if (newValue instanceof lib.ExternalObject) {
            // nothing to do
        } else if (typeof newValue === 'string') {
            // get uri from server
            if (_isStorageCloudURI(newValue)) {
                isURICallback = true;
                virtualDevice.client._.createStorageObject(newValue, function (storage, error) {
                    if (error) {
                        lib.error('Error during creation storage object: ' + error);
                        return;
                    }
                    var storageObject = new lib.enterprise.StorageObject(storage.getURI(), storage.getName(),
                        storage.getType(), storage.getEncoding(), storage.getDate(), storage.getLength());
                    storageObject._.setDevice(virtualDevice);
                    storageObject._.setSyncEventInfo(spec.name, virtualDevice);

                    if (!_matchType(spec.type, storageObject)) {
                        lib.error('type mismatch; attribute "' + spec.name + '" has type [' + spec.type + ']');
                        return;
                    }
                    callback(storageObject);
                });
                return;
            } else {
                newValue = new lib.ExternalObject(newValue);
            }
        } else {
            lib.error('invalid URI parameter');
            return;
        }
    }

    if (!_matchType(spec.type, newValue)) {
        lib.error('type mismatch; attribute "' + spec.name + '" has type [' + spec.type + ']');
        return;
    }

    if (!isURICallback) {
        callback(newValue);
    }
}

/** @ignore */
function _equal(newValue, oldValue, spec) {
    if (spec.type === 'DATETIME'
        && (newValue instanceof Date)
        && (oldValue instanceof Date)) {
        return (newValue.getTime() === oldValue.getTime());
    } else {
        return (newValue === oldValue);
    }
}
