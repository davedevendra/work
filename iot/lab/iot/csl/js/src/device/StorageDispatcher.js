/**
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * The StorageDispatcher queues content for automatic upload to, or download from, the Oracle
 * Storage Cloud Service.
 * <p>
 * There can be only one StorageDispatcher instance per DirectlyConnectedDevice at a time and it is
 * created at first use.  To close an instance of a StorageDispatcher the
 * <code>DirectlyConnectedDevice.close</code> method must be used.
 * <p>
 * The onProgress function can be used to set handlers that are used for notifying as the transfer
 * progresses:
 * <p>
 * <code>storageDispatcher.onProgress = function (progress, error);</code><br>
 * where {@link Progress|iotcs.device.util.StorageDispatcher.Progress} progress is an object
 * represents the transfer progress of storage object.
 *
 * @alias iotcs.device.util.StorageDispatcher
 * @class
 * @extends iotcs.StorageDispatcher
 * @memberof iotcs.device.util
 *
 * @param {iotcs.device.util.DirectlyConnectedDevice} device - The directly connected device (Messaging API)
 *        associated with this storage dispatcher.
 */
lib.device.util.StorageDispatcher = function (device) {
    _mandatoryArg(device, lib.device.util.DirectlyConnectedDevice);

    if (device.storageDispatcher) {
        return device.storageDispatcher;
    }
    lib.StorageDispatcher.call(this, device);

    var self = this;
    var client = device;
    var poolingInterval = lib.oracle.iot.client.device.defaultMessagePoolingInterval;
    var startPooling = null;

    var processCallback = function (storage, state, bytes) {
        storage._.setProgressState(state);
        var progress = new lib.device.util.StorageDispatcher.Progress(storage);
        progress._.setBytesTransferred(bytes);
        self._.onProgress(progress);
    };

    var deliveryCallback = function (storage, error, bytes) {
        storage._.setProgressState(lib.StorageDispatcher.Progress.State.COMPLETED);
        var progress = new lib.device.util.StorageDispatcher.Progress(storage);
        progress._.setBytesTransferred(bytes);
        self._.onProgress(progress, error);
    };

    var errorCallback = function (storage, error, bytes) {
        storage._.setProgressState(lib.StorageDispatcher.Progress.State.FAILED);
        var progress = new lib.device.util.StorageDispatcher.Progress(storage);
        progress._.setBytesTransferred(bytes);
        self._.onProgress(progress, error);
    };

    var sendMonitor = new $impl.Monitor(function () {
        var currentTime = Date.now();
        if (currentTime >= (startPooling + poolingInterval)) {
            if (!device.isActivated() || device._.internalDev._.activating
                || device._.internalDev._.refreshing || device._.internalDev._.storage_refreshing) {
                startPooling = currentTime;
                return;
            }
            var storage = self._.queue.pop();
            while (storage !== null) {
                storage._.setProgressState(lib.StorageDispatcher.Progress.State.IN_PROGRESS);
                self._.onProgress(new lib.device.util.StorageDispatcher.Progress(storage));
                client._.sync_storage(storage, deliveryCallback, errorCallback, processCallback);
                storage = self._.queue.pop();
            }
            startPooling = currentTime;
        }
    });

    Object.defineProperty(this._, 'stop', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function () {
            sendMonitor.stop();
        }
    });

    startPooling = Date.now();
    sendMonitor.start();
};

lib.device.util.StorageDispatcher.prototype = Object.create(lib.StorageDispatcher);
lib.device.util.StorageDispatcher.constructor = lib.device.util.StorageDispatcher;

/**
 * Add a StorageObject to the queue to upload/download content to/from the Storage Cloud.
 *
 * @function queue
 * @memberof iotcs.device.util.StorageDispatcher.prototype
 *
 * @param {iotcs.StorageObject} storageObject - The content storageObject to be queued.
 */
lib.device.util.StorageDispatcher.prototype.queue = function (storageObject) {
    _mandatoryArg(storageObject, lib.StorageObject);
    lib.StorageDispatcher.prototype.queue.call(this, storageObject);
};

/**
 * Cancel the transfer of content to or from storage.  This call has no effect if the transfer is
 * completed, already cancelled, has failed, or the storageObject is not queued.
 *
 * @function cancel
 * @memberof iotcs.device.util.StorageDispatcher.prototype
 *
 * @param {iotcs.StorageObject} storageObject - The content storageObject to be cancelled.
 */
lib.device.util.StorageDispatcher.prototype.cancel = function (storageObject) {
    _mandatoryArg(storageObject, lib.StorageObject);
    lib.StorageDispatcher.prototype.cancel.call(this, storageObject);
};

/**
 * An object for receiving progress via the Progress callback.
 *
 * @alias iotcs.device.util.StorageDispatcher.Progress
 * @class
 * @memberof iotcs.device.util.StorageDispatcher
 *
 * @param {StorageObject} storageObject - The storage object which progress will be tracked.
 */
lib.device.util.StorageDispatcher.Progress = function (storageObject) {
    _mandatoryArg(storageObject, lib.StorageObject);
    lib.StorageDispatcher.Progress.call(this, storageObject);
};

lib.device.util.StorageDispatcher.Progress.prototype = Object.create(lib.StorageDispatcher.Progress);
lib.device.util.StorageDispatcher.Progress.constructor = lib.device.util.StorageDispatcher.Progress;

/**
 * Get the number of bytes transferred.  This can be compared to the length of content obtained by
 * calling {@link iotcs.StorageObject#getLength}.
 *
 * @function getBytesTransferred
 * @memberof iotcs.device.util.StorageDispatcher.Progress.prototype
 *
 * @returns {number} The number of bytes transferred.
 */
lib.device.util.StorageDispatcher.Progress.prototype.getBytesTransferred = function () {
    return lib.StorageDispatcher.Progress.prototype.getBytesTransferred.call(this);
};

/**
 * Get the state of the transfer.
 *
 * @function getState
 * @memberof iotcs.device.util.StorageDispatcher.Progress.prototype
 *
 * @returns {iotcs.device.util.StorageDispatcher.Progress.State} The state of the transfer.
 */
lib.device.util.StorageDispatcher.Progress.prototype.getState = function () {
    return lib.StorageDispatcher.Progress.prototype.getState.call(this);
};

/**
* Get the StorageObject that was queued for which this progress event pertains.
*
* @function getStorageObject
* @memberof iotcs.device.util.StorageDispatcher.Progress.prototype
*
* @returns {iotcs.StorageObject} A StorageObject.
*/
lib.device.util.StorageDispatcher.Progress.prototype.getStorageObject = function () {
    return lib.StorageDispatcher.Progress.prototype.getStorageObject.call(this);
};

/**
 * Enumeration of progress states.
 *
 * @alias State
 * @enum {string}
 * @memberof iotcs.device.util.StorageDispatcher.Progress
 * @readonly
 * @static
 */
lib.device.util.StorageDispatcher.Progress.State = {
    /**The upload or download was cancelled before it completed. */
    CANCELLED: "CANCELLED",
    /** The upload or download completed successfully. */
    COMPLETED: "COMPLETED",
    /** The upload or download failed without completing. */
    FAILED: "FAILED",
    /** The upload or download is currently in progress. */
    IN_PROGRESS: "IN_PROGRESS",
    /** Initial state of the upload or download. */
    INITIATED: "INITIATED",
    /** The upload or download is queued and not yet started. */
    QUEUED: "QUEUED"
};

