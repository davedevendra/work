/**
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/** @ignore */
var $port = lib.$port || {};

if (lib.debug) {
    lib.$port = $port;
}

if (typeof window === 'undefined') {
    lib.error('invalid target platform');
}

var _b2h = (function () {
    var r = [];
    for (var i=0; i<256; i++) {
        r[i] = (i + 0x100).toString(16).substr(1);
    }
    return r;
})();

$port.userAuthNeeded = function () {
    return true;
};

$port.os = {};

$port.os.type = function () {
    return window.navigator.platform;
};

$port.os.release = function () {
    return '0';
};

$port.https = {};

$port.https.csrf = {};
$port.https.csrf.token = null;
$port.https.csrf.inProgress = false;
$port.https.csrf.tokenName = 'X-CSRF-TOKEN';

$port.https.authRequest = {};
$port.https.authRequest.path = '/iot/webapi/v2/private/server';

/*@TODO: Validate with server implementation the user auth*/
$port.https.req = function (options, payload, callback, oracleIoT) {
    ['family', 'localAddress', 'socketPath', 'agent',
     'pfx', 'key', 'passphrase', 'cert', 'ca', 'ciphers', 'rejectUnauthorized',
     'secureProtocol' ]
        .forEach(function (key) {
            if (key in options) {
                //lib.log('this $port.https.req ignores "' + key + '" option');
            }
        });
    if ((options.method === 'GET') && (payload)) {
        lib.log('there should be no payload when using GET method; use "path" for passing query');
    }

    // if this is the first attempt to access IoT-CS
    if ((oracleIoT) && (!$port.https.csrf.token) && (!$port.https.csrf.inProgress)) {
        $port.https.csrf.inProgress = true;
        $port.https.getTokenAndRequest(options, payload, 1, callback);
    } else {
        $port.https.request(options, payload, callback, oracleIoT);
    }
};

/*@TODO: Trial should be used for testing only, not for real POD*/
$port.https.getTokenAndRequest = function (options, payload, trial, callback) {

    var csrfOptions = {
        protocol: options.protocol,
        hostname: options.hostname,
        port: options.port,
        method: 'GET',
        path: (lib.oracle.iot.client.test.auth.activated ? lib.oracle.iot.client.test.reqroot : $impl.reqroot) + '/private/server'
    };

    if (options.headers && options.headers.Authorization) {
        csrfOptions.headers = {};
        csrfOptions.headers.Authorization = options.headers.Authorization;
    }

    $port.https.request(csrfOptions, payload, function (response, error) {
        $port.https.csrf.inProgress = false;
        if (!response || error) {
            callback(response, error);
            return;
        }
        if ((!$port.https.csrf.token) && (trial > 0)) {
            $port.https.getTokenAndRequest(options, payload, --trial, callback);
        } else {
            $port.https.request(options, payload, callback, true);
        }
    }, true);

};

var _authWindow = null;

$port.https.request = function (options, payload, callback, oracleIoT) {
    var baseUrl = (options.protocol || 'https')
        + '://'
        + (options.hostname || options.host || 'localhost')
        + (((options.port) && ((options.protocol === 'https' && options.port !== 443)) ||
                                (options.protocol === 'http' && options.port !== 80)) ? (':' + options.port) : '');

    var url = baseUrl + (options.path || '/');

    var authUrl = baseUrl + $port.https.authRequest.path;

    var _onNotAuth = function (authWindowOpen) {

        if ((!_authWindow || _authWindow.closed) && authWindowOpen) {
            _authWindow = window.open(authUrl, 'auth');
        }

        var authMonitor = null;

        authMonitor = new $impl.Monitor(function () {
            if (authMonitor) {
                authMonitor.stop();
            }
            authMonitor = null;
            $port.https.req(options, payload, callback, oracleIoT);
        });

        authMonitor.start();

    };

    var xhr = new XMLHttpRequest();
    var _onready = function (req) {

        if (req.readyState === 4) {

            if ((req.status === 302) || (req.status === 0)
                || (req.responseUrl && req.responseUrl.length && (decodeURI(req.responseURL) !== url))) {
                _onNotAuth(true);
                return;
            } else {
                if (_authWindow && (!_authWindow.closed)) {
                    _authWindow.close();
                }
            }

            if (req.status === 401) {
                if (!$port.https.csrf.inProgress) {
                    $port.https.csrf.token = null;
                }
                _onNotAuth(false);
                return;
            }

            if ((req.status === 200) || (req.status === 202)) {
                if (xhr.getResponseHeader($port.https.csrf.tokenName) && xhr.getResponseHeader($port.https.csrf.tokenName).length) {
                    $port.https.csrf.token = xhr.getResponseHeader($port.https.csrf.tokenName);
                }
                callback(req.responseText);
            } else {
                callback(null, lib.createError(req.responseText));
            }
        }

    };
    xhr.open(options.method, url, true);
    if (oracleIoT) {
        xhr.withCredentials = true;
        if ($port.https.csrf.token) {
            xhr.setRequestHeader($port.https.csrf.tokenName, $port.https.csrf.token);
        }
    }
    xhr.onreadystatechange = function () {
        _onready(xhr);
    };
    if (options.headers) {
        Object.keys(options.headers).forEach(function (key, index) {
            if ((!oracleIoT) && (key === 'Authorization') && (options.auth)) {
                xhr.setRequestHeader(key, options.auth);
            } else {
                xhr.setRequestHeader(key, options.headers[key]);
            }
        });
    }
    xhr.send(payload || null);

};

$port.file = {};

$port.file.store = function (path, data) {
    localStorage.setItem(path, data);
};

$port.file.exists = function (path) {
    return (localStorage.getItem(path) !== null);
};

$port.file.load = function (path) {
    var data = localStorage.getItem(path);
    if (!data) {
        lib.error('could not load file "'+path+'"');
        return;
    }
    return data;
};

$port.file.append = function (path, data) {
    var original_data = localStorage.getItem(path);
    if (!original_data) {
        lib.error('could not load file "'+path+'"');
        return;
    }                   
    localStorage.setItem(path, original_data + data);
};

$port.file.remove = function (path) {
    localStorage.removeItem(path);
};

$port.util = {};

$port.util.rng = function (count) {
    var a = new Array(count);
    for (var i=0; i<count; i++) {
        a[i] = Math.floor(Math.random()*256);
    }
    return a;
};

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
    return btoa(str);
};

$port.util.atob = function (str) {
    return atob(str);
};

$port.util.query = {};

$port.util.query.escape = function (str) {
    return escape(str);
};

$port.util.query.unescape = function (str) {
    return unescape(str);
};

$port.util.query.parse = function (str, sep, eq, options) {
    var _sep = sep || '&';
    var _eq  = eq  || '=';
    var decodeURIComponent = $port.util.query.unescape;
    var obj = {};
    var args = str.split(_sep);
    for (var i=0; i < args.length; i++) {
        var pair = args[i].split(_eq);
        var field = decodeURIComponent(pair[0]);
        var value = decodeURIComponent(pair[1]);
        if (obj[field]) {
            if (!Array.isArray(obj[field])) {
                var current = obj[field];
                obj[field] = new Array(current);
            }
            obj[field].push(value);
        } else {
            obj[field] = value;
        }
    }
    return obj;
};

$port.util.query.stringify = function (obj, sep, eq, options) {
    var _sep = sep || '&';
    var _eq  = eq  || '=';
    var encodeURIComponent = $port.util.query.escape;
    var str = '';
    Object.keys(obj).forEach(function (key) {
        if (typeof obj[key] === 'object') {
            obj[key].forEach(function (e) {
                str += _sep + key + _eq + encodeURIComponent(e);
            });
        } else {
            str += _sep + key + _eq + encodeURIComponent(obj[key]);
        }
    });
    return str.substring(1);
};

/*@TODO: check that Promise are actually supported! either try/catch or if (!Promise) else lib.error ...
*/
$port.util.promise = function(executor){
    return new Promise(executor);
};

$port.HttpStatusCode = {
    OK: 200,
    NOT_FOUND: 404
};