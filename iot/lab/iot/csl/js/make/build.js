/**
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

//////////////////////////////////////////////////////////////////////////////

var fs = require('fs');
var path = require('path');
var os = require('os');

var config = require('./build-config.js').config;
config.bundles.forEach(function(bundle) {
    //XXX delete bundle.output
    console.log('building bundle: '+ bundle.output);
    try { fs.unlinkSync(bundle.output); } catch(e) {}
    try {
        var dir = path.dirname(bundle.output);
        fs.mkdirSync(dir);
    } catch(e) {}
    var fswrapper = fs.readFileSync(bundle.wrapper).toString();
    Object.keys(bundle.fields).forEach(function(key) {
        var regexp = new RegExp('%%'+key+'%%', 'g');
        var field = bundle.fields[key];
        switch (typeof field) {
        case 'number':
        case 'string':
            fswrapper = fswrapper.replace(regexp, field);
            break;
        case 'object':
            if (Array.isArray(field)) {
                //those are files to stitch ...
                var fsfile = '';
                field.forEach(function(file) {
                    console.log('+ file: '+file);
                    fsfile += os.EOL
                        + '//////////////////////////////////////////////////////////////////////////////' + os.EOL
                        + '// file: ' + file + os.EOL
                        + os.EOL
                        + fs.readFileSync(file).toString() + os.EOL;
                });
                fswrapper = fswrapper.replace(regexp, fsfile);
            } else {
                throw '[error] unsupported field type';
            }
            break;
        default:
            throw '[error] unsupported field type';
            break;
        }
    });
    fs.writeFileSync(bundle.output, fswrapper);
});
