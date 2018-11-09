/**
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * StorageObject provides information about content in cloud storage.  For creation use
 * {@link iotcs.device.DirectlyConnectedDevice#createStorageObject}.
 *
 * @class
 * @memberof iotcs.device
 * @alias iotcs.device.StorageObject
 * @extends iotcs.ExternalObject
 *
 * @param {?string} uri - The full URI of the object in the Storage Cloud.
 * @param {?string} name - The name of the object used in the Storage Cloud.
 * @param {?string} type - The type of the object, if <code>null</code> then
 *        {@link iotcs.device.StorageObject.MIME_TYPE}.
 * @param {?string} encoding - The encoding of the object, or <code>null</code> if none.
 * @param {?Date} date - The last-modified date of the object.
 * @param {number} [length = -1] - The length of the object.
 */
lib.device.StorageObject = function (uri, name, type, encoding, date, length) {
    lib.StorageObject.call(this, uri, name, type, encoding, date, length);

    var self = this;
    Object.defineProperty(this._.internal, 'syncStatus',{
        enumerable: false,
        configurable: false,
        writable: true,
        value: lib.device.StorageObject.SyncStatus.NOT_IN_SYNC
    });

    Object.defineProperty(this._.internal, 'inputPath',{
        enumerable: false,
        configurable: false,
        writable: true,
        value: null
    });

    Object.defineProperty(this._.internal, 'outputPath',{
        enumerable: false,
        configurable: false,
        writable: true,
        value: null
    });

    Object.defineProperty(this, 'onSync', {
        enumerable: false,
        configurable: false,
        get: function () {
            return self._.onSync;
        },
        set: function (newValue) {
            if (!newValue || (typeof newValue !== 'function')) {
                lib.error('trying to set something to onDelivery that is not a function!');
                return;
            }
            self._.onSync = newValue;
        }
    });

    this._.onSync = function (arg) {};

    Object.defineProperty(this._.internal, 'syncEvents',{
        enumerable: false,
        configurable: false,
        writable: true,
        value: [null]
    });

    Object.defineProperty(this._, 'addSyncEvent', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (syncEvent) {
            switch (self.getSyncStatus()) {
                case lib.device.StorageObject.SyncStatus.NOT_IN_SYNC:
                case lib.device.StorageObject.SyncStatus.SYNC_PENDING:
                    self._.internal.syncEvents.push(syncEvent);
                    break;
                case lib.device.StorageObject.SyncStatus.IN_SYNC:
                case lib.device.StorageObject.SyncStatus.SYNC_FAILED:
                    self._.onSync(syncEvent);
                    break;
            }
        }
    });

    Object.defineProperty(this._, 'createSyncEvent', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function () {
            return new lib.device.StorageObject.SyncEvent(self, self._.nameForSyncEvent, self._.deviceForSync);
        }
    });

    Object.defineProperty(this._, 'deviceForSync', {
        enumerable: false,
        configurable: false,
        writable: true,
        value: null
    });

    Object.defineProperty(this._, 'nameForSyncEvent', {
        enumerable: false,
        configurable: false,
        writable: true,
        value: null
    });

    Object.defineProperty(this._, 'setSyncEventInfo', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (name, virtualDevice) {
            self._.nameForSyncEvent = name;
            self._.deviceForSync = virtualDevice;
        }
    });

    Object.defineProperty(this._, 'handleStateChange', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function () {
            if (self._.deviceForSync) {
                self._.deviceForSync._.handleStorageObjectStateChange(self);
            }
        }
    });

    Object.defineProperty(this._, 'setDevice',{
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (device) {
            if (device instanceof lib.device.util.DirectlyConnectedDevice) {
                self._.dcd = device;
            } else {
                lib.error("Invalid device type");
            }
        }
    });
};

lib.device.StorageObject.prototype = Object.create(lib.StorageObject.prototype);
lib.device.StorageObject.constructor = lib.device.StorageObject;

/**
 * Set an input file path for content to be uploaded.
 * The implementation allows for either the input path to be set,
 * or the output path to be set, but not both.
 * If the input path parameter is not null, the output path will be set to null.
 *
 * @param {string} path - input file path to which the content will be read.
 *
 * @memberof iotcs.device.StorageObject.prototype
 * @function setInputPath
 */
lib.device.StorageObject.prototype.setInputPath = function (path) {
    _mandatoryArg(path, "string");

    if (this._.internal.syncStatus === lib.device.StorageObject.SyncStatus.SYNC_PENDING) {
        lib.error("Illegal state: sync pending");
        return;
    }

    if (this._.internal.inputPath === null || this._.internal.inputPath !== path) {
        this._.internal.inputPath = path;
        this._.internal.outputPath = null;
        this._.internal.syncStatus = lib.device.StorageObject.SyncStatus.NOT_IN_SYNC;
        lib.StorageObject.prototype.setInputStream.call(this, require("fs").createReadStream(path));
    }
};

/**
 * Set an output file path for content to be downloaded.
 * The implementation allows for either the output path to be set,
 * or the input path to be set, but not both.
 * If the output path parameter is not null, the input path will be set to null.
 *
 * @param {string} path - output file path to which the content will be written.
 *
 * @memberof iotcs.device.StorageObject.prototype
 * @function setOutputPath
 */
lib.device.StorageObject.prototype.setOutputPath = function (path) {
    _mandatoryArg(path, "string");
    if (this._.internal.syncStatus === lib.device.StorageObject.SyncStatus.SYNC_PENDING) {
        lib.error("Illegal state: sync pending");
        return;
    }
    if (this._.internal.outputPath === null || this._.internal.outputPath !== path) {
        this._.internal.outputPath = path;
        this._.internal.inputPath = null;
        this._.internal.syncStatus = lib.device.StorageObject.SyncStatus.NOT_IN_SYNC;
        lib.StorageObject.prototype.setOutputStream.call(this, require("fs").createWriteStream(path));
    }
};

/**
 * Get the the name of this object in the storage cloud.
 * This is name and path of the file that was uploaded to the storage cloud.
 *
 * @returns {string} name
 * @memberof iotcs.device.StorageObject.prototype
 * @function getName
 */
lib.device.StorageObject.prototype.getName = function () {
    return this._.internal.name;
};

/**
 * Get the mime-type of the content.
 *
 * @returns {string} type
 * @see {@link http://www.iana.org/assignments/media-types/media-types.xhtml|IANA Media Types}
 * @memberof iotcs.device.StorageObject.prototype
 * @function getType
 */
lib.device.StorageObject.prototype.getType = function () {
    return this._.internal.type;
};

/**
 * Get the date and time the content was created or last modified in cloud storage.
 *
 * @returns {?Date} date the content was last modified in cloud storage,
 * or <code>null</code> if the content has not been uploaded
 * @memberof iotcs.device.StorageObject.prototype
 * @function getDate
 */
lib.device.StorageObject.prototype.getDate = function () {
    return this._.internal.date;
};

/**
 * Get the length of the content in bytes.
 * This is the number of bytes required to upload or download the content.
 *
 * @returns {number} the length of the content in bytes, or <code>-1</code> if unknown
 * @memberof iotcs.device.StorageObject.prototype
 * @function getLength
 */
lib.device.StorageObject.prototype.getLength = function () {
    return this._.internal.length;
};

/**
 * Get the compression scheme of the content.
 *
 * @returns {?String} the compression scheme of the content,
 * or <code>null</code> if the content is not compressed
 * @memberof iotcs.device.StorageObject.prototype
 * @function getEncoding
 */
lib.StorageObject.prototype.getEncoding = function () {
    return this._.internal.encoding;
};

/**
 * Get the URI value.
 *
 * @returns {?string} A URI, or <code>null</code> if unknown.
 * @memberof iotcs.device.StorageObject.prototype.
 * @function getURI
 */
lib.device.StorageObject.prototype.getURI = function () {
    return this._.internal.uri;
};

/**
 * Get the input file path when uploading content.
 *
 * @returns {string} input file path
 * @memberof iotcs.device.StorageObject.prototype
 * @function getInputPath
 */
lib.device.StorageObject.prototype.getInputPath = function () {
    return this._.internal.inputPath;
};

/**
 * Get the output file path when downloading content.
 *
 * @returns {string} output file path
 * @memberof iotcs.device.StorageObject.prototype
 * @function getOutputPath
 */
lib.device.StorageObject.prototype.getOutputPath = function () {
    return this._.internal.outputPath;
};

/**
 * Notify the library to sync content with the storage cloud.
 *
 * @memberof iotcs.device.StorageObject.prototype
 * @function sync
 */
lib.device.StorageObject.prototype.sync = function () {
    var syncEvent = this._.createSyncEvent();
    if (this._.internal.syncStatus === lib.device.StorageObject.SyncStatus.NOT_IN_SYNC) {
        if (this._.internal.inputStream || this._.internal.outputStream) {
            this._.internal.syncStatus = lib.device.StorageObject.SyncStatus.SYNC_PENDING;
        } else {
            lib.error("input path or output path must be set");
            return;
        }
        this._.addSyncEvent(syncEvent);
        new lib.device.util.StorageDispatcher(this._.dcd).queue(this);
    } else {
        this._.addSyncEvent(syncEvent);
    }
};

/**
 * Get the status of whether or not the content is in sync with the storage cloud.
 *
 * @see {@link iotcs.device.StorageObject.SyncStatus}
 * @memberof iotcs.device.StorageObject.prototype
 * @function getSyncStatus
 */
lib.device.StorageObject.prototype.getSyncStatus = function () {
    return this._.internal.syncStatus;
};

/**
 * Enumeration of the status of whether or not the content is in sync with the storage cloud.
 *
 * @memberof iotcs.device.StorageObject
 * @alias SyncStatus
 * @readonly
 * @enum {string}
 */
lib.device.StorageObject.SyncStatus = {
    /**
     * The content is not in sync with the storage cloud
     */
    NOT_IN_SYNC: "NOT_IN_SYNC",
    /**
     * The content is not in sync with the storage cloud, but a
     * sync is pending.
     */
    SYNC_PENDING: "SYNC_PENDING",
    /**
     * The content is in sync with the storage cloud
     */
    IN_SYNC: "IN_SYNC",
    /**
     * The content is not in sync with the storage cloud because the upload or download failed.
     */
    SYNC_FAILED: "SYNC_FAILED"
};

/**
 * An event passed to the onSync callback when content referred to by an attribute value has been
 * successfully synchronized, or has failed to be synchronized.
 *
 * @alias iotcs.device.StorageObject.SyncEvent
 * @class
 * @memberof iotcs.device.StorageObject
 *
 * @param {iotcs.device.StorageObject} storageObject - The storage object to synchronize.
 * @param {string} [name] - The name of the storage object.
 * @param {iotcs.device.VirtualDevice} [virtualDevice]
 */
lib.device.StorageObject.SyncEvent = function (storageObject, name, virtualDevice) {
    _mandatoryArg(storageObject, lib.device.StorageObject);
    _optionalArg(name, "string");
    _optionalArg(virtualDevice, lib.device.VirtualDevice);

    Object.defineProperty(this, '_', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: {}
    });

    Object.defineProperty(this._, 'internal', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: {
            storage: storageObject,
            name: name,
            virtualDevice: virtualDevice
        }
    });
};

/**
 * Get the virtual device that is the source of the event.
 *
 * @function getVirtualDevice
 * @memberof iotcs.device.StorageObject.SyncEvent.prototype
 *
 * @returns {iotcs.device.VirtualDevice} the virtual device, or <code>null</code> if sync was called
 *          independently.
 */
lib.device.StorageObject.SyncEvent.prototype.getVirtualDevice = function () {
    return this._.internal.virtualDevice;
};

/**
 * Get the name of the attribute, action, or format that this event is associated with.
 *
 * @function getName
 * @memberof iotcs.device.StorageObject.SyncEvent.prototype
 *
 * @returns {string} The name, or <code>null</code> if sync was called independently.
 */
lib.device.StorageObject.SyncEvent.prototype.getName = function () {
    return this._.internal.name;
};

/**
 * Get the StorageObject that is the source of this event.
 *
 * @function getSource
 * @memberof iotcs.device.StorageObject.SyncEvent.prototype
 *
 * @returns {iotcs.device.StorageObject} The storage object.
 */
lib.device.StorageObject.SyncEvent.prototype.getSource = function () {
    return this._.internal.storage;
};
