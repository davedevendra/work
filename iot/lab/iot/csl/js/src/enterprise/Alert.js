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
$impl.Alert = function (alertSpec) {
    _mandatoryArg(alertSpec, 'object');

    if (!alertSpec.urn) {
        lib.error('alert specification in device model is incomplete');
        return;
    }

    var spec = {
        urn: alertSpec.name,
        description: (alertSpec.description || ''),
        name: (alertSpec.name || null),
        fields: (alertSpec.value && alertSpec.value.fields)? alertSpec.value.fields : null
    };

    /** @private */
    Object.defineProperty(this, '_', {
        enumerable: false,
        configurable: false,
        writable: true,
        value: {}
    });

    // public members

    Object.defineProperty(this, 'urn', {
        enumerable: true,
        configurable: false,
        writable: false,
        value: spec.urn
    });

    Object.defineProperty(this, 'name', {
        enumerable: true,
        configurable: false,
        writable: false,
        value: spec.name
    });

    Object.defineProperty(this, 'description', {
        enumerable: true,
        configurable: false,
        writable: false,
        value: spec.description
    });

    Object.defineProperty(this, 'onAlerts', {
        enumerable: false,
        configurable: false,
        get: function () {
            return this._.onAlerts;
        },
        set: function (newValue) {
            if (!newValue || (typeof newValue !== 'function')) {
                lib.error('trying to set something to onAlert that is not a function!');
                return;
            }
            this._.onAlerts = newValue;
        }
    });
    this._.onAlerts = function (arg) {};

    /** @private */
    Object.defineProperty(this._, 'formatsLocalUpdate', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (formats, virtualDevice, callback) {
            if (spec.fields) {
                var index = 0;
                spec.fields.forEach(function (field) {
                    if (field.type === "URI") {
                        var url = formats[0].fields[field.name];
                        if (_isStorageCloudURI(url)) {
                            virtualDevice.client._.createStorageObject(url, function (storage, error) {
                                if (error) {
                                    lib.error('Error during creation storage object: ' + error);
                                    return;
                                }
                                var storageObject = new lib.enterprise.StorageObject(storage.getURI(), storage.getName(),
                                    storage.getType(), storage.getEncoding(), storage.getDate(), storage.getLength());
                                storageObject._.setDevice(virtualDevice);
                                storageObject._.setSyncEventInfo(field.name, virtualDevice);

                                formats[0].fields[field.name] = storageObject;
                                ++index;
                                if (callback && index === spec.fields.length) {
                                    callback();
                                }
                            });
                        } else {
                            formats[0].fields[field.name] = new lib.ExternalObject(url);
                            ++index;
                        }
                    } else {
                        ++index;
                    }
                });
                if (callback && index === spec.fields.length) {
                    callback();
                }
            }
        }
    });
};