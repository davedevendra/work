/**
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

lib.enterprise.StorageObject = function (url, name, type, encoding, date, length) {
    lib.StorageObject.call(this, url, name, type, encoding, date, length);

    var self = this;
    Object.defineProperty(this._.internal, 'syncStatus',{
        enumerable: false,
        configurable: false,
        writable: true,
        value: lib.enterprise.StorageObject.SyncStatus.NOT_IN_SYNC
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
                case lib.enterprise.StorageObject.SyncStatus.NOT_IN_SYNC:
                case lib.enterprise.StorageObject.SyncStatus.SYNC_PENDING:
                    self._.internal.syncEvents.push(syncEvent);
                    break;
                case lib.enterprise.StorageObject.SyncStatus.IN_SYNC:
                case lib.enterprise.StorageObject.SyncStatus.SYNC_FAILED:
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
            return new lib.enterprise.StorageObject.SyncEvent(self, self._.nameForSyncEvent, self._.deviceForSync);
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
            if (device instanceof lib.AbstractVirtualDevice || device instanceof lib.Client) {
                self._.dcd = device;
            } else {
                lib.error("Invalid client type");
            }
        }
    });
};

lib.enterprise.StorageObject.prototype = Object.create(lib.StorageObject.prototype);
lib.enterprise.StorageObject.constructor = lib.enterprise.StorageObject;

lib.enterprise.StorageObject.prototype.sync = function () {
    var syncEvent = this._.createSyncEvent();
    if (this._.internal.syncStatus === lib.enterprise.StorageObject.SyncStatus.NOT_IN_SYNC) {
        if (this._.internal.inputStream || this._.internal.outputStream) {
            this._.internal.syncStatus = lib.enterprise.StorageObject.SyncStatus.SYNC_PENDING;
        } else {
            lib.error("input path or output path must be set");
            return;
        }
        this._.addSyncEvent(syncEvent);
        if (this._.dcd instanceof lib.Client) {
            new lib.enterprise.StorageDispatcher(this._.dcd).queue(this);
        } else {
            new lib.enterprise.StorageDispatcher(this._.dcd.client).queue(this);
        }
    } else {
        this._.addSyncEvent(syncEvent);
    }
};

lib.enterprise.StorageObject.prototype.getSyncStatus = function () {
    return this._.internal.syncStatus;
};

lib.enterprise.StorageObject.prototype.setInputPath = function (path) {
    _mandatoryArg(path, "string");
    if (this._.internal.syncStatus === lib.enterprise.StorageObject.SyncStatus.SYNC_PENDING) {
        lib.error("Illegal state: sync pending");
        return;
    }
    if (this._.internal.inputPath === null || this._.internal.inputPath !== path) {
        this._.internal.inputPath = path;
        this._.internal.outputPath = null;
        this._.internal.syncStatus = lib.enterprise.StorageObject.SyncStatus.NOT_IN_SYNC;
        lib.StorageObject.prototype.setInputStream.call(this, require("fs").createReadStream(path));
    }
};

lib.enterprise.StorageObject.prototype.setOutputPath = function (path) {
    _mandatoryArg(path, "string");
    if (this._.internal.syncStatus === lib.enterprise.StorageObject.SyncStatus.SYNC_PENDING) {
        lib.error("Illegal state: sync pending");
        return;
    }
    if (this._.internal.outputPath === null || this._.internal.outputPath !== path) {
        this._.internal.outputPath = path;
        this._.internal.inputPath = null;
        this._.internal.syncStatus = lib.enterprise.StorageObject.SyncStatus.NOT_IN_SYNC;
        lib.StorageObject.prototype.setOutputStream.call(this, require("fs").createWriteStream(path));
    }
};

lib.enterprise.StorageObject.prototype.getInputPath = function () {
    return this._.internal.inputPath;
};

lib.enterprise.StorageObject.prototype.getOutputPath = function () {
    return this._.internal.outputPath;
};

lib.enterprise.StorageObject.SyncStatus = {
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

lib.enterprise.StorageObject.SyncEvent = function (storage, name, virtualDevice) {
    _mandatoryArg(storage, lib.enterprise.StorageObject);
    _optionalArg(name, "string");
    _optionalArg(virtualDevice, lib.enterprise.VirtualDevice);

    Object.defineProperty(this, '_', {
        enumerable: false,
        configurable: false,
        writable: true,
        value: {}
    });

    Object.defineProperty(this._, 'internal', {
        enumerable: false,
        configurable: false,
        writable: true,
        value: {
            storage: storage,
            name: name,
            virtualDevice: virtualDevice
        }
    });
};

lib.enterprise.StorageObject.SyncEvent.prototype.getVirtualDevice = function () {
    return this._.internal.virtualDevice;
};

lib.enterprise.StorageObject.SyncEvent.prototype.getName = function () {
    return this._.internal.name;
};

lib.enterprise.StorageObject.SyncEvent.prototype.getSource = function () {
    return this._.internal.storage;
};
