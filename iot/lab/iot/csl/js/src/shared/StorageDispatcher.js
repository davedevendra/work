/**
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/** @ignore */
lib.StorageDispatcher = function (device) {
    _mandatoryArg(device, "object");
    var self = this;
    Object.defineProperty(this, '_', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: {}
    });

    Object.defineProperty(this._, 'device', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: device
    });

    Object.defineProperty(this, 'onProgress', {
        enumerable: false,
        configurable: false,
        get: function () {
            return self._.onProgress;
        },
        set: function (newValue) {
            if (!newValue || (typeof newValue !== 'function')) {
                lib.error('trying to set something to onDelivery that is not a function!');
                return;
            }
            self._.onProgress = newValue;
        }
    });
    this._.onProgress = function (arg, error) {};

    Object.defineProperty(this._, 'queue', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: new $impl.PriorityQueue(lib.oracle.iot.client.maximumStorageObjectsToQueue)
    });

    Object.defineProperty(this._, 'push', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (storage) {
            self._.queue.push(storage);
        }
    });

    Object.defineProperty(this._, 'remove', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (storage) {
            return self._.queue.remove(storage);
        }
    });

    Object.defineProperty(device, 'storageDispatcher', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: this
    });
};

/** @ignore */
lib.StorageDispatcher.prototype.queue = function (storageObject) {
    _mandatoryArg(storageObject, lib.StorageObject);
    if (storageObject._.internal.progress_state === lib.StorageDispatcher.Progress.State.COMPLETED) {
        return;
    }
    if (storageObject._.internal.progress_state === lib.StorageDispatcher.Progress.State.QUEUED ||
        storageObject._.internal.progress_state === lib.StorageDispatcher.Progress.State.IN_PROGRESS) {
        lib.error("Can't queue storage during transfer process.");
        return;
    }
    storageObject._.setProgressState(lib.StorageDispatcher.Progress.State.QUEUED);
    this._.push(storageObject);
    this._.onProgress(new lib.StorageDispatcher.Progress(storageObject));
};

/** @ignore */
lib.StorageDispatcher.prototype.cancel = function (storageObject) {
    _mandatoryArg(storageObject, lib.StorageObject);
    var cancelled = false;
    if (storageObject._.internal.progress_state === lib.StorageDispatcher.Progress.State.QUEUED) {
        cancelled = (this._.remove(storageObject) !== null);
    }
    if (cancelled ||
        storageObject._.internal.progress_state === lib.StorageDispatcher.Progress.State.IN_PROGRESS) {
        storageObject._.setProgressState(lib.StorageDispatcher.Progress.State.CANCELLED);
    }

    if (cancelled) {
        this._.onProgress(new lib.StorageDispatcher.Progress(storageObject));
    }
};

/** @ignore */
lib.StorageDispatcher.Progress = function (storageObject) {
    _mandatoryArg(storageObject, lib.StorageObject);

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
            storage: storageObject,
            state: storageObject._.internal.progress_state,
            bytesTransferred: 0
        }
    });

    var self = this;
    Object.defineProperty(this._, 'setBytesTransferred', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (bytes) {
            self._.internal.bytesTransferred = bytes;
        }
    });
};

/** @ignore */
lib.StorageDispatcher.Progress.prototype.getBytesTransferred = function () {
    return this._.internal.bytesTransferred;
};

/** @ignore */
lib.StorageDispatcher.Progress.prototype.getState = function () {
    return this._.internal.state;
};

/** @ignore */
lib.StorageDispatcher.Progress.prototype.getStorageObject = function () {
    return this._.internal.storage;
};

lib.StorageDispatcher.Progress.State = {
    /** Up/download was cancelled before it completed */
    CANCELLED: "CANCELLED",
    /** Up/download completed successfully */
    COMPLETED: "COMPLETED",
    /** Up/download failed without completing */
    FAILED: "FAILED",
    /** Up/download is currently in progress */
    IN_PROGRESS: "IN_PROGRESS",
    /** Initial state */
    INITIATED: "INITIATED",
    /** Up/download is queued and not yet started */
    QUEUED: "QUEUED"
};