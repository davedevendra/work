/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * This object is used for request messages dispatch.  You can register handlers to an instance of
 * this object that will handle request messages that come from the cloud and will return a response
 * message associated for that request message.
 * <p>
 * There can be only one instance of this object (singleton) generated at first use.
 *
 * @alias iotcs.device.util.RequestDispatcher
 * @class
 * @memberof iotcs.device.util
 */
lib.device.util.RequestDispatcher = function () {
    if (lib.device.util.RequestDispatcher.prototype._singletonInstance) {
        return lib.device.util.RequestDispatcher.prototype._singletonInstance;
    }
    lib.device.util.RequestDispatcher.prototype._singletonInstance = this;

    Object.defineProperty(this, '_', {
        enumerable: false,
        configurable: false,
        writable: true,
        value: {}
    });

    Object.defineProperty(this._, 'requestHandlers', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: {}
    });

    Object.defineProperty(this._, 'defaultHandler', {
        enumerable: false,
        configurable: false,
        writable: false,
        value: function (requestMessage) {
            return lib.message.Message.buildResponseMessage(requestMessage, 404, {}, 'Not Found', '');
        }
    });
};

/**
 * This is main function of the RequestDispatcher that dispatches a request message to the
 * appropriate handler, if one is found and the handler is called so the appropriate response
 * message is returned. If no handler is found, the RequestDispatcher implements a default request
 * message dispatcher that would just return a 404 (Not Found) response message. This method will
 * never return null.
 *
 * @function dispatch
 * @memberof iotcs.device.util.RequestDispatcher.prototype
 *
 * @param {object} requestMessage - The request message to dispatch.
 * @returns {iotcs.message.Message} The response message associated with the request.
 */
lib.device.util.RequestDispatcher.prototype.dispatch = function (requestMessage) {
    if (!requestMessage || !requestMessage.type
        || (requestMessage.type !== lib.message.Message.Type.REQUEST)
        || !requestMessage.destination
        || !requestMessage.payload
        || !requestMessage.payload.url
        || !this._.requestHandlers[requestMessage.destination]
        || !this._.requestHandlers[requestMessage.destination][requestMessage.payload.url]) {
        return this._.defaultHandler(requestMessage);
    }
    var message = this._.requestHandlers[requestMessage.destination][requestMessage.payload.url](requestMessage);
    if (message && (message instanceof lib.message.Message)
        && (message.getJSONObject().type === "RESPONSE_WAIT")) {
        return null;
    }
    if (!message || !(message instanceof lib.message.Message)
        || (message.getJSONObject().type !== lib.message.Message.Type.RESPONSE)) {
        return this._.defaultHandler(requestMessage);
    }
    return message;
};

/**
 * This method registers a handler to the RequestDispatcher.  The handler is a function that must
 * have the form:<br> <code>handler = function (requestMessage) { ... return responseMessage};
 * </code><br>.  Where requestMessage if a JSON representing the exact message received from the
 * cloud that has the type REQUEST and responseMessage is an instance of iotcs.message.Message that
 * has type RESPONSE.  If neither of the conditions are satisfied the RequestDispatcher will use the
 * default handler.
 * <p>
 * It is advisable to use the iotcs.message.Message.buildResponseMessage method for generating
 * response messages.
 *
 * @function registerRequestHandler
 * @memberof iotcs.device.util.RequestDispatcher.prototype
 * @see {@link iotcs.message.Message.Type}
 * @see {@link iotcs.message.Message.buildResponseMessage}
 *
 * @param {string} endpointId - The endpoint IDthat is the destination of the request message.
 * @param {string} path - The path that is the "address" (resource definition) of the request
 *        message.
 * @param {function} handler - The actual handler to be registered.
 */
lib.device.util.RequestDispatcher.prototype.registerRequestHandler = function (endpointId, path, handler) {
    _mandatoryArg(endpointId, 'string');
    _mandatoryArg(path, 'string');
    _mandatoryArg(handler, 'function');

    if (!this._.requestHandlers[endpointId]) {
        this._.requestHandlers[endpointId] = {};
    }
    this._.requestHandlers[endpointId][path] = handler;
};

/**
 * Returns a registered request handler, if it is registered, otherwise <code>null</code>.
 *
 * @function getRequestHandler
 * @memberof iotcs.device.util.RequestDispatcher.prototype
 *
 * @param {string} endpointId - The endpoint ID that the handler was registered with.
 * @param {string} path - The path that the handler was registered with.
 * @returns {function} The actual handler or <code>null</code>.
 */
lib.device.util.RequestDispatcher.prototype.getRequestHandler = function (endpointId, path) {
    _mandatoryArg(endpointId, 'string');
    _mandatoryArg(path, 'string');

    if (!this._.requestHandlers[endpointId] || !this._.requestHandlers[endpointId][path]) {
        return null;
    }
    return this._.requestHandlers[endpointId][path];
};

/**
 * This method removed a handler from the registered handlers list of the RequestDispatcher.  If
 * handler is present as parameter, then endpointId and path parameters are ignored.
 *
 * @function unregisterRequestHandler
 * @memberof iotcs.device.util.RequestDispatcher.prototype
 *
 * @param {function} handler - The reference to the handler to be removed.
 * @param {string} endpointId - The endpoint id that the handler was registered with.
 * @param {string} path - The path that the handler was registered with.
 */
lib.device.util.RequestDispatcher.prototype.unregisterRequestHandler = function (handler, endpointId, path) {
    if (handler && (typeof handler === 'string')) {
        endpointId = handler;
        path = endpointId;
        handler = null;
    }

    if (handler && (typeof handler === 'function')) {
        Object.keys(this._.requestHandlers).forEach(function (endpointId){
            Object.keys(this._.requestHandlers[endpointId]).forEach(function (path) {
                delete this._.requestHandlers[endpointId][path];
                if (Object.keys(this._.requestHandlers[endpointId]).length === 0) {
                    delete this._.requestHandlers[endpointId];
                }
            });
        });
        return;
    } else {
        _mandatoryArg(endpointId, 'string');
        _mandatoryArg(path, 'string');
    }

    if (!this._.requestHandlers[endpointId] || !this._.requestHandlers[endpointId][path]) {
        return;
    }
    delete this._.requestHandlers[endpointId][path];
    if (Object.keys(this._.requestHandlers[endpointId]).length === 0) {
        delete this._.requestHandlers[endpointId];
    }
};
