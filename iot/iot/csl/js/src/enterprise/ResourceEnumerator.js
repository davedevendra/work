/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * A class that implements a way of getting the custom resources registered by a device and the
 * possibility of invoking them (V1 implementation).
 *
 * @alias iotcs.enterprise.ResourceEnumerator
 * @class
 * @memberof iotcs.enterprise
 *
 * @param {iotcs.enterprise.EnterpriseClient} client - The enterprise client associated with the
 *        application context of the device for which the resources need to be enumerated.
 * @param {string} deviceId - The ID for which the resources are to be enumerated/invoked.
 */
lib.enterprise.ResourceEnumerator = function (client, deviceId) {
    _mandatoryArg(client, lib.enterprise.EnterpriseClient);
    _mandatoryArg(deviceId, 'string');
    this.client = client;
    this.deviceId = deviceId;
    this.requestMonitors = {};
};

/**
 * Return the list of resources that the device associated with the enumerator has registered in the
 * cloud.
 *
 * @memberof iotcs.enterprise.ResourceEnumerator.prototype
 * @function getResources
 *
 * @returns {iotcs.enterprise.Pageable} A pageable instance with which pages can be requested that
 *          contain resources as items.
 */
lib.enterprise.ResourceEnumerator.prototype.getResources = function () {
    return new lib.enterprise.Pageable({
        method: 'GET',
        path: $impl.reqroot
        + '/apps/' + this.client.appid
        + '/devices/' + this.deviceId
        + '/resources'
    }, '', null, this.client);
};

/**
 * Invokes the specified resource with defined options, query and payload.
 * <p>
 * Resources can be retrieved by using the getResources method and from the items property of the
 * response the resource objects can be extracted.
 *
 * A resource object must have the following properties:<br>
 * - methods: An array of methods the resource accepts.<br>
 * - endpointId: The device ID.<br>
 * - the self link: This the link that the resource can be accessed with present in the links array
 *   property.
 *
 * @function invokeResource
 * @memberof iotcs.enterprise.ResourceEnumerator.prototype
 * @see {@link iotcs.enterprise.ResourceEnumerator#getResources}
 *
 * @param {object} resource -The resource to be invoked as described.
 * @param {{method:string, headers:object}} options - The request options.  The headers are optional
 *        and method is mandatory.
 * @param {object} [query] - The query for the request as JSON object.
 * @param {string} [body] - The payload for the request.
 * @param {function} callback - The callback function that is called when a response arrives. The
 *        whole HTTP response as JSON object is given as parameter to the callback function.  If an
 *        error occurs or the response is invalid the error object is passed as the second parameter
 *        in the callback with the reason in error.message: callback(response, error).
 */
lib.enterprise.ResourceEnumerator.prototype.invokeResource = function (resource, options, query, body, callback) {

    if (query && (typeof query === 'function')) {
        callback = query;
        query = null;
    }

    if (body && (typeof body === 'function')) {
        callback = body;
        body = null;
    }

    _mandatoryArg(resource, 'object');
    _mandatoryArg(resource.methods, 'array');
    _mandatoryArg(resource.endpointId, 'string');
    _mandatoryArg(resource.links, 'array');
    _mandatoryArg(options, 'object');
    _mandatoryArg(options.method, 'string');
    _optionalArg(options.headers, 'object');
    _optionalArg(query, 'object');
    _optionalArg(body, 'string');
    _mandatoryArg(callback, 'function');

    if (resource.endpointId !== this.deviceId){
        lib.error('invalid resource');
        return;
    }

    var path = null;

    resource.links.forEach(function(link){
        if(link.rel && link.href && (link.rel === 'self')){
            path = link.href;
        }
    });

    if (!path) {
        lib.error('invalid resource');
        return;
    }

    var method = null;

    resource.methods.forEach(function (m) {
        if (m === options.method){
            method = options.method;
        }
    });

    if (!method) {
        lib.error('invalid options');
        return;
    }

    path = decodeURI(path + (query ? ('?=' + $port.util.query.stringify(query)) : ''));

    var opt = {};
    opt.method = method;
    opt.path = path;

    if (options.headers) {
        opt.headers = options.headers;
    }

    var self = this;
    $impl.https.bearerReq(opt, (body ? body : null), function (response, error) {
        if (!response || error || !(response.id)) {
            callback(null, lib.createError('invalid response on async request for resource invocation', error));
            return;
        }
        var reqId = response.id;

        try {
            self.requestMonitors[reqId] = new $impl.AsyncRequestMonitor(reqId, function (response, error) {
                if (!response || error) {
                    callback(null, lib.createError('invalid response on resource invocation', error));
                    return;
                }
                Object.freeze(response);
                callback(response);
            }, self.client._.internalClient);
            self.requestMonitors[reqId].start();
        } catch (e) {
            callback(null, lib.createError('invalid response on async request for resource invocation', e));
        }
    }, function () {
        self.invokeResource(resource, options, query, body, callback);
    }, self.client._.internalClient);

};

/**
 * Closes the enumerator and will stop any pending resource invocations.
 *
 * @function close
 * @memberof iotcs.enterprise.ResourceEnumerator.prototype
 */
lib.enterprise.ResourceEnumerator.prototype.close = function () {
    for(var key in this.requestMonitors) {
        this.requestMonitors[key].stop();
    }
    this.requestMonitors = {};
    this.deviceId = null;
};


