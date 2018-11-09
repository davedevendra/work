/**
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

// Implement porting interface


/** @ignore */
var $port = lib.$port || {};

if (lib.debug) {
    lib.$port = $port;
}

var _b2h = (function () {
    var r = [];
    for (var i=0; i<256; i++) {
        r[i] = (i + 0x100).toString(16).substr(1);
    }
    return r;
})();

// pre-requisites (internal to lib)
var forge = require('node-forge');

// pre-requisites (internal to $port);
var os = require('os');
var https = require('https');
var http = require('http');
var querystring = require('querystring');
var fs = require('fs');
var url = require('url');
let sqlite3 = require('sqlite3');

//////////////////////////////////////////////
// Debug output options.
var debug = require ('debug')('debug');
//////////////////////////////////////////////

var spawn = require('child_process').spawnSync;
/**
 * This method is used for retrieving disk space information. It uses OS specific
 * utility commands, so it is very OS specific implementation. Also because handling
 * of external processes executed with spawn is not good, the timeout and try/catch
 * is used and if any error occurs -1 value is returned for each info.
 *
 * @ignore
 */
var _getDiskSpace = function() {
    var diskSpace = {
        freeDiskSpace: -1,
        totalDiskSpace: -1
    };
    try {
        if (os.platform() === 'win32') {
            var prc1 = spawn('wmic', ['LogicalDisk', 'Where', 'DriveType="3"', 'Get', 'DeviceID,Size,FreeSpace'], {timeout: 1000});
            var str1 = prc1.stdout.toString();
            var lines1 = str1.split(/(\r?\n)/g);
            lines1.forEach(function (line) {
                if (line.indexOf(__dirname.substring(0, 2)) > -1) {
                    var infos = line.match(/\d+/g);
                    if (Array.isArray(infos)) {
                        diskSpace.totalDiskSpace = infos[1];
                        diskSpace.freeDiskSpace = infos[0];
                    }
                }
            });
        } else if (os.platform() === 'linux' || os.platform() === "darwin") {
            var prc2 = spawn('df', [__dirname], {timeout: 1000});
            var str2 = prc2.stdout.toString();
            str2 = str2.replace(/\s/g,'  ');
            var infos = str2.match(/\s\d+\s/g);
            if (Array.isArray(infos)) {
                diskSpace.freeDiskSpace = parseInt(infos[2]);
                diskSpace.totalDiskSpace = (parseInt(infos[1]) + parseInt(infos[2]));
            }
        }
    } catch (e) {
        //just ignore
    }
    return diskSpace;
};

var tls = require('tls');
tls.checkServerIdentity = function (host, cert) {
    if (cert && cert.subject && cert.subject.CN) {
        var cn = cert.subject.CN;
        if ((typeof cn === 'string') && cn.startsWith('*.')) {
            var i = host.indexOf('.');
            if (i > 0) {
                host = host.substring(i);
            }
            cn = cn.substring(1);
        }
        if (cn === host) {
            return;
        }
    }
    lib.error('SSL host name verification failed');
};

// implement porting interface

$port.userAuthNeeded = function () {
    return false;
};

$port.os = {};

$port.os.type = function () {
    return os.type();
};

$port.os.release = function () {
    return os.release();
};

$port.https = {};

$port.https.req = function (options, payload, callback) {

    if (options.tam
        && (typeof options.tam.getTrustAnchorCertificates === 'function')
        && Array.isArray(options.tam.getTrustAnchorCertificates())
        && (options.tam.getTrustAnchorCertificates().length > 0)) {
        options.ca = options.tam.getTrustAnchorCertificates();
    }

    options.rejectUnauthorized = true;
    options.protocol = options.protocol + ':';
    options.agent = false;

    if ((options.method !== 'GET') && ((options.path.indexOf('attributes') > -1) || (options.path.indexOf('actions') > -1))) {
        if (options.headers['Transfer-Encoding'] !== "chunked") {
            options.headers['Content-Length'] = payload.length;
        }
    }

    var urlObj = url.parse(options.path, true);
    if (urlObj.query) {
        if (typeof urlObj.query === 'object') {
            urlObj.query = querystring.stringify(urlObj.query);
        }
        urlObj.query = querystring.escape(urlObj.query);
    }
    options.path = url.format(urlObj);

    debug();
    debug("Request: " + new Date().getTime());
    debug(options.path);
    // var clone = Object.assign({}, options);
    // delete clone.tam;
    // delete clone.ca;
    // debug(clone);
    debug(payload);

    var req = https.request(options, function (response) {

        debug();
        debug("Response: " + response.statusCode + ' ' + response.statusMessage);
        debug(response.headers);

        // Continuously update stream with data
        var body = '';
        response.on('data', function (d) {
            body += d;
        });
        response.on('end', function () {
            // Data reception is done, do whatever with it!
            debug(body);
            if ((response.statusCode === 200) || (response.statusCode === 201) || (response.statusCode === 202)) {
                if (response.headers && (typeof response.headers['x-min-acceptbytes'] !== 'undefined')
                    && (response.headers['x-min-acceptbytes'] !== '') && (response.headers['x-min-acceptbytes'] !== 0)){
                    callback(JSON.stringify({'x-min-acceptbytes': response.headers['x-min-acceptbytes']}));
                } else {
                    callback(body);
                }
            } else {
                var error = new Error(JSON.stringify({statusCode: response.statusCode, statusMessage: (response.statusMessage ? response.statusMessage : null), body: body}));
                callback(body, error);
            }
        });
    });
    if (options.path.indexOf('iot.sync') < 0) {
        req.setTimeout(lib.oracle.iot.client.httpConnectionTimeout);
    } else if (options.path.indexOf('iot.timeout=') > -1) {
        var timeout = parseInt(options.path.substring(options.path.indexOf('iot.timeout=') + 12));
        req.setTimeout(timeout * 1000 + lib.oracle.iot.client.device.longPollingTimeoutOffset);
    }
    req.on('timeout', function () {
        callback(null, new Error('connection timeout'));
    });
    req.on('error', function(error) {
        callback(null, error);
    });
    req.write(payload);
    req.end();
};

$port.https.storageReq = function (options, storage, deliveryCallback, errorCallback, processCallback) {
    options.protocol = options.protocol + ':';
    options.rejectUnauthorized = true;
    options.agent = false;

    var isUpload = false;
    if (options.method !== 'GET') {
        isUpload = true;
        if (options.headers['Transfer-Encoding'] !== "chunked") {
            // FIXME: if Transfer-Encoding isn't chunked
            options.headers['Content-Length'] = storage.getLength();
        } else {
            delete options.headers['Content-Length'];
        }
    }

    var urlObj = url.parse(options.path, true);
    if (urlObj.query) {
        if (typeof urlObj.query === 'object') {
            urlObj.query = querystring.stringify(urlObj.query);
        }
        urlObj.query = querystring.escape(urlObj.query);
    }
    options.path = url.format(urlObj);

    debug();
    debug("Request: " + new Date().getTime());
    debug(options.path);
    debug(options);

    if (isUpload) {
        _uploadStorageReq(options, storage, deliveryCallback, errorCallback, processCallback);
    } else {
        _downloadStorageReq(options, storage, deliveryCallback, errorCallback, processCallback);
    }
};

var _uploadStorageReq = function(options, storage, deliveryCallback, errorCallback, processCallback) {
    var encoding = storage.getEncoding();
    var uploadBytes = 0;
    var protocol = options.protocol.indexOf("https") !== -1 ? https : http;
    var req = protocol.request(options, function (response) {
        debug();
        debug("Response: " + response.statusCode + ' ' + response.statusMessage);
        debug(response.headers);

        // Continuously update stream with data
        var body = '';
        response.on('data', function (d) {
            body += d;
        });
        response.on('end', function () {
            if (!req.aborted) {
                if (response.statusCode === 201) {
                    var lastModified = new Date(Date.parse(response.headers["last-modified"]));
                    storage._.setMetadata(lastModified, uploadBytes);
                    deliveryCallback(storage, null, uploadBytes);
                } else {
                    var error = new Error(JSON.stringify({
                        statusCode: response.statusCode,
                        statusMessage: (response.statusMessage ? response.statusMessage : null),
                        body: body
                    }));
                    errorCallback(error);
                }
            }
        });
    });
    req.on('timeout', function () {
        errorCallback(new Error('connection timeout'));
    });
    req.on('error', function(error) {
        errorCallback(error);
    });
    req.on('abort', function() {
        if (processCallback) {
            processCallback(storage, lib.StorageDispatcher.Progress.State.CANCELLED, uploadBytes);
        }
    });

    var readableStream = storage.getInputStream();
    if (readableStream) {
        readableStream.on('data', function(chunk) {
            if (storage._.isCancelled()) {
                req.abort();
                return;
            }
            req.write(chunk, encoding);
            uploadBytes += chunk.length;
            if (processCallback) {
                processCallback(storage, lib.StorageDispatcher.Progress.State.IN_PROGRESS, uploadBytes);
            }
        }).on('end', function() {
            if (storage._.isCancelled()) {
                req.abort();
                return;
            }
            req.end();
        }).on('error', function (error) {
            errorCallback(error);
        });
    } else {
        errorCallback(new Error("Readable stream is not set for storage object. Use setInputStream."));
    }
};

var _downloadStorageReq = function(options, storage, deliveryCallback, errorCallback, processCallback) {
    var writableStream = storage.getOutputStream();
    if (writableStream) {
        var encoding = storage.getEncoding();
        var downloadBytes = 0;
        var protocol = options.protocol.indexOf("https") !== -1 ? https : http;
        var req = protocol.request(options, function (response) {
            debug();
            debug("Response: " + response.statusCode + ' ' + response.statusMessage);
            debug(response.headers);

            // Continuously update stream with data
            var body = '';
            if (encoding) {
                writableStream.setDefaultEncoding(encoding);
            }
            writableStream.on('error', function (err) {
                errorCallback(err);
            });

            response.on('data', function (d) {
                if (storage._.isCancelled()) {
                    req.abort();
                    return;
                }
                body += d;
                downloadBytes += d.length;
                writableStream.write(d);
                if (processCallback) {
                    processCallback(storage, lib.StorageDispatcher.Progress.State.IN_PROGRESS, downloadBytes);
                }
            });
            response.on('end', function () {
                if (!req.aborted) {
                    if ((response.statusCode === 200) || (response.statusCode === 206)) {
                        writableStream.end();
                        var lastModified = new Date(Date.parse(response.headers["last-modified"]));
                        storage._.setMetadata(lastModified, downloadBytes);
                        deliveryCallback(storage, null, downloadBytes);
                    } else {
                        var error = new Error(JSON.stringify({
                            statusCode: response.statusCode,
                            statusMessage: (response.statusMessage ? response.statusMessage : null),
                            body: body
                        }));
                        errorCallback(error);
                    }
                }
            });
        });
        req.on('timeout', function () {
            errorCallback(new Error('connection timeout'));
        });
        req.on('error', function (error) {
            errorCallback(error);
        });
        req.on('abort', function() {
            if (processCallback) {
                processCallback(storage, lib.StorageDispatcher.Progress.State.CANCELLED, downloadBytes);
            }
        });
        if (storage._.isCancelled()) {
            req.abort();
            return;
        }
        req.end();
    } else {
        errorCallback(new Error("Writable stream is not set for storage object. Use setOutputStream."));
    }
};

$port.file = {};

$port.file.store = function (path, data) {
    try {
        fs.writeFileSync(path, data, {encoding:'binary'});
    } catch (e) {
        lib.error('could not store file "'+path+'"');
    }
};

$port.file.exists = function (path) {
    try {
        return fs.statSync(path).isFile();
    } catch (e) {
        return false;
    }
};

$port.file.size = function (path) {
    try {
        return fs.statSync(path).size;
    } catch (e) {
        return -1;
    }
};

$port.file.load = function (path) {
    var data = null;
    try {
        var tmp = fs.readFileSync(path, {encoding:'binary'});
        var len = tmp.length;
        data = '';
        for (var i=0; i<len; i++) {
            data += tmp[i];
        }
    } catch (e) {
        lib.error('could not load file "'+path+'"');
        return;
    }
    return data;
};

$port.file.append = function (path, data) {
    try {
        fs.appendFileSync(path, data);
    } catch (e) {
        lib.error('could not append to file "'+path+'"');
    }
};

$port.file.remove = function (path) {
    try {
        fs.unlinkSync(path);
    } catch (e) {
        lib.error('could not remove file "'+path+'"');
    }
};

$port.util = {};

$port.util.rng = function (count) {
    var b = forge.random.getBytesSync(count);
    var a = new Array(count);
    for (var i=0; i<count; i++) {
        a[i] = b[i].charCodeAt(0);
    }
    return a;
};

/*@TODO: this implementation is erroneous: leading '0's are sometime missing. => please use exact same implementation as $port-browser.js (it is anyway based on $port.util.rng()) + import _b2h @DONE
*/
$port.util.uuidv4 = function () {
    var r16 = $port.util.rng(16);
    r16[6]  &= 0x0f;  // clear version
    r16[6]  |= 0x40;  // set to version 4
    r16[8]  &= 0x3f;  // clear variant
    r16[8]  |= 0x80;  // set to IETF variant
    var i = 0;
    return _b2h[r16[i++]] + _b2h[r16[i++]] + _b2h[r16[i++]] + _b2h[r16[i++]] + '-' +
        _b2h[r16[i++]] + _b2h[r16[i++]] + '-' +
        _b2h[r16[i++]] + _b2h[r16[i++]] + '-' +
        _b2h[r16[i++]] + _b2h[r16[i++]] + '-' +
        _b2h[r16[i++]] + _b2h[r16[i++]] + _b2h[r16[i++]] +
        _b2h[r16[i++]] + _b2h[r16[i++]] + _b2h[r16[i]];
};

$port.util.btoa = function (str) {
    return new Buffer(str).toString('base64');
};

$port.util.atob = function (str) {
    return new Buffer(str, 'base64').toString();
};

$port.util.diagnostics = function () {
    var obj = {};
    obj.version = (process.env['oracle.iot.client.version'] || 'Unknown');
    var net = os.networkInterfaces();
    var space = _getDiskSpace();
    obj.freeDiskSpace = space.freeDiskSpace;
    obj.totalDiskSpace = space.totalDiskSpace;
    obj.ipAddress = 'Unknown';
    obj.macAddress = 'Unknown';
    var netInt = null;
    for (var key in net) {
        if (!key.match(/^lo\d?$/) && (key.indexOf('Loopback') < 0) && (net[key].length > 0)) {
            netInt = net[key][0];
            break;
        }
    }
    if (netInt && netInt.address) {
        obj.ipAddress = netInt.address;
    }
    if (netInt && netInt.mac) {
        obj.macAddress = netInt.mac;
    }
    return obj;
};

$port.util.query = {};

$port.util.query.escape = function (str) {
    return querystring.escape(str);
};

$port.util.query.unescape = function (str) {
    return querystring.unescape(str);
};

$port.util.query.parse = function (str, sep, eq, options) {
    return querystring.parse(str, sep, eq, options);
};

$port.util.query.stringify = function (obj, sep, eq, options) {
    return querystring.stringify(obj, sep, eq, options);
};

/*@TODO: check that Promise are actually supported! either try/catch or if (!Promise) else lib.error ...
*/
$port.util.promise = function(executor){
    return new Promise(executor);
};
