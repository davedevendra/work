/**
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

//General TODOs:

//@TODO: all $impl.https.req(...,...,(function(response){ /*HERE*/})); do not handle error cases consistently: some are lib.error(), while others are callback(null) @DONE
//@TODO: all conditions should be defensively parenthesized e.g. "if (a==b && !c && d)" => if ((a==b) && (!c) && (d))"
//@TODO: there should be more lib.oracle.iot.XXX.defaultLimit and every Pageable instanciation should use its own explicitly for maximum configurability: e.g. "new lib.enterprise.Pageable({},,,lib.oracle.iot.XXX.defaultLimit);"

//@TODO: code as flat as possible: e.g. instead of if(ok) { } => use if(!ok) {error | return ...} ... } @DONE
//@TODO: error message case should be consistent: all lowercase or w first letter Uppercase ...etc... @DONE
//@TODO: if/while/catch/... are not functions e.g. conventionally "if(XX)" should be "if (X)"
//@TODO: "function(" => "function ("
//@TODO: "){" => ") {"
//@TODO: "}\nelse {\n" => "} else {\n"

//@TODO: we probably need a few global (lib-private) functions to do advanced parameter value checking (e.g. check that appid has no "/" (or %XX equivalent ...etc...) ... this depends on needs from other classes/functions...
//@TODO: lib.error() is currently not satisfactory; related: callbacks (especially not in timeout/intervals) should not throw any exceptions ...etc...


//@TODO (last) align DCL to ECL for all sibling definitions (use winmerge ...)

//////////////////////////////////////////////////////////////////////////////

//@TODO: should probably be /iot/webapi/v2
//@TODO: should probably be moved to "lib.oracle.iot.server.pathroot" => @globals.js
/** @ignore */
$impl.reqroot = '/iot/webapi/v2';

$impl.https.bearerReq = function (options, payload, callback, retryCallback, client) {

    if (client && client._.tam && client._.tam.getClientId()) {
        options.path = options.path.replace('webapi','api');
        if (!options.headers) {
            options.headers = {};
        }
        options.headers.Authorization = client._.bearer;
        options.headers['X-EndpointId'] = client._.tam.getClientId();
        options.tam = client._.tam;
        $impl.https.req(options, payload, function (response_body, error) {
            if (error) {
                var exception = null;
                try {
                    exception = JSON.parse(error.message);
                    if (exception.statusCode && (exception.statusCode === 401)) {
                        client._.refresh_bearer(function (error) {
                            if (error) {
                                callback(response_body, error);
                                return;
                            }
                            retryCallback();
                        });
                        return;
                    }
                } catch (e) {

                }
            }
            callback(response_body, error);
        });
    } else {
        $impl.https.req(options, payload, callback);
    }

};
