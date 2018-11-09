/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

/**
 * Creates a Filter builder object.  This class allows to easily formulate filter queries and
 * convert straight to Json Queries. 
 *
 * @example
 * var f = new iotcs.enterprise.Filter();
 *
 * f = f.and([
 *     f.eq("name","Andrew"),
 *     f.or([f.not(f.in("maritalStatus", ["MARRIED", "SINGLE"])),
 *           f.gte("children.count", 1)]),
 *     f.gt("salary.rank", 3),
 *     f.lte("salary.rank", 7),
 *     f.like("lastName", "G%")
 * ]);
 *
 * lib.log(f.stringify());
 * // '{"$and":[{"name":{"$eq":"Andrew"}},{"$or":[{"$not":{"maritalStatus":{"$in":["MARRIED","SINGLE"]}}},{"children.count":{"$gte":1}}]},{"salary.rank":{"$gt":3}},{"salary.rank":{"$lte":7}},{"lastName":{"$like":"G%"}}]}';
 *
 * @alias iotcs.enterprise.Filter
 * @class
 * @memberOf iotcs.enterprise
 */
lib.enterprise.Filter = function (query) {
    this.query = query || {};
};

/**
 * Converts this filter into a JSON object
 *
 * @function toJSON
 * @memberof iotcs.enterprise.Filter.prototype
 */
lib.enterprise.Filter.prototype.toJSON = function () {
    return this.query;
};

/**
 * Returns a string containing a string-ified version of the current filter.
 *
 * @function toString
 * @memberof iotcs.enterprise.Filter.prototype
 */
lib.enterprise.Filter.prototype.toString = function () {
    return JSON.stringify(this.query);
};

/**
 * Alias for toString.
 *
 * @function stringify
 * @memberof iotcs.enterprise.Filter.prototype
 */
lib.enterprise.Filter.prototype.stringify = lib.enterprise.Filter.prototype.toString;

/**
 * Equality operator
 * <p>
 * Note that if the value string does contain a <code>%</code>,
 * then this operation is replaced by the
 * {@link iotcs.enterprise.Filter#like like} operation.
 *
 * @param {string} field - the field name
 * @param {(string|number)} value - the value to compare the field
 * against. Values can only be simple types such as numbers or
 * string.
 * @returns {iotcs.enterprise.Filter} a new Filter expressing this operation
 * @memberof iotcs.enterprise.Filter.prototype
 * @function eq
 */
lib.enterprise.Filter.prototype.eq = function (field, value) {
    if (_is(field, ['string']) &&
        _is(value, ['string', 'number'])) {
        var query = {};
        if ((typeof value === 'string') && (value.indexOf('%')>=0)) {
            lib.log('$eq value field contains a "%". Operation replaced into a $like');
            query[field] = {"$like":value};
        } else {
            query[field] = {"$eq":value};
        }
        return new lib.enterprise.Filter(query);
    }
    return null;
};

/**
 * Greater-than operator
 *
 * @param {string} field - the field name
 * @param {number} value - the value to compare the field
 * against. Values can only be simple types such as numbers or
 * string.
 * @returns {iotcs.enterprise.Filter} a new Filter expressing this operation
 * @memberof iotcs.enterprise.Filter.prototype
 * @function gt
 */
lib.enterprise.Filter.prototype.gt = function (field, value) {
    if (_is(field, ['string']) &&
        _is(value, ['number'])) {
        var query = {};
        query[field] = {"$gt":value};
        return new lib.enterprise.Filter(query);
    }
    return null;
};

/**
 * Greater-than-or-equal operator
 *
 * @param {string} field - the field name
 * @param {number} value - the value to compare the field
 * against. Values can only be simple types such as numbers or
 * string.
 * @returns {iotcs.enterprise.Filter} a new Filter expressing this operation
 * @memberof iotcs.enterprise.Filter.prototype
 * @function gte
 */
lib.enterprise.Filter.prototype.gte = function (field, value) {
    if (_is(field, ['string']) &&
        _is(value, ['number'])) {
        var query = {};
        query[field] = {"$gte":value};
        return new lib.enterprise.Filter(query);
    }
    return null;
};

/**
 * Less-than operator
 *
 * @param {string} field - the field name
 * @param {number} value - the value to compare the field
 * against. Values can only be simple types such as numbers or
 * string.
 * @returns {iotcs.enterprise.Filter} a new Filter expressing this operation
 * @memberof iotcs.enterprise.Filter.prototype
 * @function lt
 */
lib.enterprise.Filter.prototype.lt = function (field, value) {
    if (_is(field, ['string']) &&
        _is(value, ['number'])) {
        var query = {};
        query[field] = {"$lt":value};
        return new lib.enterprise.Filter(query);
    }
    return null;
};

/**
 * Less-than-or-equal operator
 *
 * @param {string} field - the field name
 * @param {number} value - the value to compare the field
 * against. Values can only be simple types such as numbers or
 * string.
 * @returns {iotcs.enterprise.Filter} a new Filter expressing this operation
 * @memberof iotcs.enterprise.Filter.prototype
 * @function lte
 */
lib.enterprise.Filter.prototype.lte = function (field, value) {
    if (_is(field, ['string']) &&
        _is(value, ['number'])) {
        var query = {};
        query[field] = {"$lte":value};
        return new lib.enterprise.Filter(query);
    }
    return null;
};

/**
 * Not-equal operator
 *
 * @param {string} field - the field name
 * @param {(string|number)} value - the value to compare the field
 * against. Values can only be simple types such as numbers or
 * string.
 * @returns {iotcs.enterprise.Filter} a new Filter expressing this operation
 * @memberof iotcs.enterprise.Filter.prototype
 * @function ne
 */
lib.enterprise.Filter.prototype.ne = function (field, value) {
    if (_is(field, ['string']) &&
        _is(value, ['string', 'number'])) {
        var query = {};
        query[field] = {"$ne":value};
        return new lib.enterprise.Filter(query);
    }
    return null;
};

/**
 * Is-in operator.
 * <p>
 * Checks whether the field's value is one of the proposed values.
 *
 * @param {string} field - the field name
 * @param {(string[]|number[])} valuearray - an array of same typed
 * values to test the field against. Values can only be simple
 * types such as numbers or string.
 * @returns {iotcs.enterprise.Filter} a new Filter expressing this operation
 * @memberof iotcs.enterprise.Filter.prototype
 * @function in
 */
lib.enterprise.Filter.prototype.in = function (field, valuearray) {
    if (_is(field, ['string']) &&
        Array.isArray(valuearray)) {
        var type = null;
        for (var index in valuearray) {
            var value = valuearray[index];
            if (!type && _is(value, ['string', 'number'])) {
                type = typeof value;
            } else if (typeof value !== type) {
                lib.error('inconsistent value types in $in valuearray');
                return null;
            }
        }
        var query = {};
        query[field] = {"$in":valuearray};
        return new lib.enterprise.Filter(query);
    }
    return null;
};

/**
 * Exists operator.
 * <p>
 * Checks whether the field's value matches the given boolean state.
 *
 * @param {string} field - the field name
 * @param {boolean} state - the boolean to test field against
 * @returns {iotcs.enterprise.Filter} a new Filter expressing this operation
 * @memberof iotcs.enterprise.Filter.prototype
 * @function exists
 */
lib.enterprise.Filter.prototype.exists = function (field, state) {
    if (_is(field, ['string']) &&
        _is(state, ['boolean'])) {
        var query = {};
        query[field] = {"$exists":state};
        return new lib.enterprise.Filter(query);
    }
    return null;
};

/**
 * Like operator.
 * <p>
 * Checks whether the field's value matches the search query. Use
 * <code>%</code> in the match string as search jocker, e.g.
 * <code>"jo%"</code>.
 * <p>
 * Note that if the match string does not contain any <code>%</code>,
 * then this operation is replaced by the
 * {@link iotcs.enterprise.Filter#eq eq} operation.
 *
 * @param {string} field - the field name
 * @param {string} match - the pattern matching string to test field against
 * @returns {iotcs.enterprise.Filter} a new Filter expressing this operation
 * @memberof iotcs.enterprise.Filter.prototype
 * @function like
 */
lib.enterprise.Filter.prototype.like = function (field, match) {
    if (_is(field, ['string']) &&
        _is(match, ['string'])) {
        var query = {};
        if (match.indexOf('%')<0) {
            lib.log('$eq match field does not contains any "%". Operation replaced into a $eq');
            query[field] = {"$eq":match};
        } else {
            query[field] = {"$like":match};
        }
        return new lib.enterprise.Filter(query);
    }
    return null;
};

/**
 * And operator.
 * <p>
 * Checks if all conditions are true.
 * <p>
 * This function takes either an array of iotcs.enterprise.Filter
 * or an indefinit number of iotcs.enterprise.Filter.
 *
 * @param {(iotcs.enterprise.Filter[]|...iotcs.enterprise.Filter)} args - an array
 * or variable length argument list of filters to AND
 * @returns {iotcs.enterprise.Filter} a new Filter expressing this operation
 * @memberof iotcs.enterprise.Filter.prototype
 * @function and
 */
lib.enterprise.Filter.prototype.and = function (args) {
    var filters = null;
    if (Array.isArray(args)) {
        if (!_argsAreFilters(args)) {
            lib.error('invalid operation type(s)');
            return;
        }
        filters = args;
    } else {
        if (!_argsAreFilters(arguments)) {
            lib.error('invalid operation type(s)');
            return;
        }
        filters = [];
        for (var i=0; i<arguments.length; i++) {
            filters.push(arguments[i]);
        }
    }
    var query = {"$and":filters};
    return new lib.enterprise.Filter(query);
};

/**
 * Or operator.
 * <p>
 * Checks if at least one of the conditions is true.
 * <p>
 * This function takes either an array of iotcs.enterprise.Filter
 * or an indefinit number of iotcs.enterprise.Filter.
 *
 * @param {(iotcs.enterprise.Filter[]|...iotcs.enterprise.Filter)} args - an array
 * or variable length argument list of filters to OR
 * @returns {iotcs.enterprise.Filter} a new Filter expressing this operation
 * @memberof iotcs.enterprise.Filter.prototype
 * @function or
 */
lib.enterprise.Filter.prototype.or = function (args) {
    var filters = null;
    if (Array.isArray(args)) {
        if (!_argsAreFilters(args)) {
            lib.error('invalid operation type(s)');
            return;
        }
        filters = args;
    } else {
        if (!_argsAreFilters(arguments)) {
            lib.error('invalid operation type(s)');
            return;
        }
        filters = [];
        for (var i=0; i<arguments.length; i++) {
            filters.push(arguments[i]);
        }
    }
    var query = {"$or":filters};
    return new lib.enterprise.Filter(query);
};

/**
 * Not operator
 * <p>
 * Checks if the negative condition is true.
 *
 * @param {iotcs.enterprise.Filter} filter - a filter to negate
 * @memberof iotcs.enterprise.Filter.prototype
 * @function not
 */
lib.enterprise.Filter.prototype.not = function (filter) {
    if (!_argIsFilter(filter)) {
        lib.error('invalid type');
        return;
    }
    var query = {"$not":filter};
    return new lib.enterprise.Filter(query);
};

/** @ignore */
function _argIsFilter(arg) {
    return (arg instanceof lib.enterprise.Filter);
}

/** @ignore */
function _argsAreFilters(args) {
    if (Array.isArray(args)) {
        // args is []
        return args.every(function (arg) {
            return (arg instanceof lib.enterprise.Filter);
        });
    } else {
        // args are varargs
        for (var i = 0; i < args.length; i++) {
            if (! (args[i] instanceof lib.enterprise.Filter)) {
                return false;
            }
        }
        return true;
    }
}

/** @ignore */
function _is(parameter, types) {
    var ptype = typeof parameter;
    for(var index = 0; index < types.length; index++) {
        if (types[index] === ptype) {
            return true;
        }
    }
    lib.log('type is "'+ptype+'" but should be ['+types.toString()+']');
    lib.error('invalid parameter type for "'+parameter+'"');
    return false;
}
