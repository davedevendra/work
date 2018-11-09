/**
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
     * @property {string} iotcs.name - the short name of this library
     */
    try {
        lib.name = lib.name || "%%LIBNAME%%";
    } catch(e) {}
    
    /**
     * @property {string} iotcs.description - the longer description
     */
    lib.description = "%%DESCRIPTION%%";

    /**
     * @property {string} iotcs.version - the version of this library
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
     * Log and return an error message
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
        console.log(msgstr);
    }
    
//////////////////////////////////////////////////////////////////////////////

%%FILES%%

//END/////////////////////////////////////////////////////////////////////////
    lib.log(lib.description+' v'+ lib.version+' loaded!');
    return lib;
}
//////////////////////////////////////////////////////////////////////////////
// module initialization
if ((typeof module === 'object') && (module.exports)) {
    //((typeof exports !== 'undefined') && (this.exports !== exports))
    // node.js
    module.exports = function %%LIBNAME%% (lib) {
        return init(lib);
    };
    module.exports(module.exports);
}
})();
