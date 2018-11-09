/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

(function () {
var name = '%%LIBNAME%%';
function init(lib) {
'use strict';
//START///////////////////////////////////////////////////////////////////////

    /**
     * @global
     * @alias %%LIBNAME%%
     * @namespace
     */
    lib = lib || {};

    /**
     * @property {string} iotcs.name - The short name of this library.
     */
    try {
        lib.name = lib.name || "%%LIBNAME%%";
    } catch(e) {}

    /**
     * @property {string} iotcs.description - The description of this library.
     */
    lib.description = "%%DESCRIPTION%%";

    /**
     * @property {string} iotcs.version - The version of this library.
     */
    lib.version = "%%VERSION%%";

    /**
     * Log an info message
     * @function
     */
    lib.log = function (msg) {
        if (lib.debug) {
            _log('info', msg);
        }
    };

    /**
     * Throw and log an error message
     * @function
     */
    lib.error = function (msg) {
        if (lib.debug && console.trace) {
            console.trace(msg);
        }
        _log('error', msg);
        throw '[%%LIBNAME%%:error] ' + msg;
    };

    /**
     * Log and return an error message.
     *
     * @function
     */
    lib.createError = function (msg, error) {
        if (lib.debug && console.trace) {
            console.trace(msg);
        }
        _log('error', msg);
        if (!error) {
            return new Error('[%%LIBNAME%%:error] ' + msg);
        }
        return error;
    };

    /** @ignore */
    function _log(level, msg) {
        var msgstr = '[%%LIBNAME%%:'+level+'] ' + msg;
        var logDOM = document.getElementById('iotcs-log');
        if (logDOM) {
            logDOM.innerHTML += '<span class="log-'+level+'">' + msgstr + '</span></br>';
        } else {
            console.log(msgstr);
        }
    }

//////////////////////////////////////////////////////////////////////////////

%%FILES%%

//END/////////////////////////////////////////////////////////////////////////
    lib.log(lib.description+' v'+ lib.version+' loaded!');
    return lib;
}
//////////////////////////////////////////////////////////////////////////////
// module initialization
if (typeof window !== 'undefined') {
    %%LIBNAME%% = function %%LIBNAME%%(lib) {
        return init(lib);
    };
    %%LIBNAME%%(%%LIBNAME%%);
}
})();
