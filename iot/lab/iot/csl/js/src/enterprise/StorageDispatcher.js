/**
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/** @ignore */
lib.enterprise.StorageDispatcher = function (ecl) {
    _mandatoryArg(ecl, lib.enterprise.EnterpriseClient);

    if (ecl.storageDispatcher) {
        return ecl.storageDispatcher;
    }
    lib.StorageDispatcher.call(this, ecl);

    var self = this;
    var client = ecl;
    var poolingInterval = lib.oracle.iot.client.monitor.pollingInterval;
    var startPooling = null;

    var processCallback = function (storage, state, bytes) {
        storage._.setProgressState(state);
        var progress = new lib.StorageDispatcher.Progress(storage);
        progress._.setBytesTransferred(bytes);
        self._.onProgress(progress);
    };

    var deliveryCallback = function (storage, error, bytes) {
        storage._.setProgressState(lib.StorageDispatcher.Progress.State.COMPLETED);
        var progress = new lib.StorageDispatcher.Progress(storage);
        progress._.setBytesTransferred(bytes);
        self._.onProgress(progress, error);
    };

    var errorCallback = function (storage, error, bytes) {
        storage._.setProgressState(lib.StorageDispatcher.Progress.State.FAILED);
        var progress = new lib.StorageDispatcher.Progress(storage);
        progress._.setBytesTransferred(bytes);
        self._.onProgress(progress, error);
    };

    var sendMonitor = new $impl.Monitor(function () {
        var currentTime = Date.now();
        if (currentTime >= (startPooling + poolingInterval)) {
            if (ecl._.internalClient._.refreshing || ecl._.internalClient._.storage_refreshing) {
                startPooling = currentTime;
                return;
            }
            var storage = self._.queue.pop();
            while (storage !== null) {
                storage._.setProgressState(lib.StorageDispatcher.Progress.State.IN_PROGRESS);
                self._.onProgress(new lib.StorageDispatcher.Progress(storage));
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

lib.enterprise.StorageDispatcher.prototype = Object.create(lib.StorageDispatcher);
lib.enterprise.StorageDispatcher.constructor = lib.enterprise.StorageDispatcher;

/** @ignore */
lib.enterprise.StorageDispatcher.prototype.queue = function (storage) {
    _mandatoryArg(storage, lib.StorageObject);
    lib.StorageDispatcher.prototype.queue.call(this, storage);
};

/** @ignore */
lib.enterprise.StorageDispatcher.prototype.cancel = function (storage) {
    _mandatoryArg(storage, lib.StorageObject);
    lib.StorageDispatcher.prototype.cancel.call(this, storage);
};
