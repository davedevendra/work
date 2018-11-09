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
$impl.Data = function (dataSpec) {
    _mandatoryArg(dataSpec, 'object');

    if (!dataSpec.urn) {
        lib.error('data specification in device model is incomplete');
        return;
    }

    var spec = {
        urn: dataSpec.name,
        description: (dataSpec.description || ''),
        name: (dataSpec.name || null),
        fields: (dataSpec.value && dataSpec.value.fields)? dataSpec.value.fields : null
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

    Object.defineProperty(this, 'onData', {
        enumerable: false,
        configurable: false,
        get: function () {
            return this._.onData;
        },
        set: function (newValue) {
            if (!newValue || (typeof newValue !== 'function')) {
                lib.error('trying to set something to onData that is not a function!');
                return;
            }
            this._.onData = newValue;
        }
    });
    this._.onData = function (arg) {};

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