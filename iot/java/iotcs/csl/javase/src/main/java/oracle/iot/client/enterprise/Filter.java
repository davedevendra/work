/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 */

package oracle.iot.client.enterprise;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A Filter that can be used for queries.
 * <p>
 * <b>Example:</b>
 * <p>
 * Devices activated in the last hour that implement a "com:acme:device"
 * device model.
 * <p>
 * The following code:
 * <pre>
 *  <code>
 *  f = Filter.and(
 *        Filter.gte(Device.Field.CREATED.alias(),
 *          (System.currentTimeMillis() - 3600000L)),
 *        Filter.eq(Device.Field.STATE.alias(), "ACTIVATED"),
 *        Filter.like(Device.Field.DEVICE_MODELS.alias() + ".urn",
 *          "urn:com:acme:device:%"));
 *  </code>
 * </pre>
 * <p>
 * will create the following JSON object:
 * <pre>
 *  <code>
 *  {"$and":[
 *    {"created":{"$gte":1457137772894}},
 *    {"state":{"$eq":"ACTIVATED"}},
 *    {"deviceModels.urn":{"$like":"urn:com:acme:device:%"}}]}
 *  </code>
 * </pre>
 */
public abstract class Filter {

    /**
     * Creates a query filter that performs the logical AND between sub filters provided as argument: (filter1 and filter2 and ...).
     *
     * @param filters sub filters to combine
     * @return a new filter { "$and": [ filter1, filter2, .... ] }
     */
    public static Filter and(Filter... filters) {
        return new CompositeFilter("$and", filters);
    }

    /**
     * Creates a query filter that performs the logical OR between sub filters provided as argument: (filter1 or filter2 or ...).
     *
     * @param filters sub filters to combine
     * @return a new filter  { "$or": [ filter1, filter2, .... ] }
     */
    public static Filter or(Filter... filters) {
        return new CompositeFilter("$or", filters);
    }

    /**
     * Creates a query filter that performs the logical NOT on the filter provided as argument: (not filter).
     *
     * @param filter on which to apply the NOT
     * @return a new filter { "$not": filter1 }
     */
    public static Filter not(Filter filter) {
        return new CompositeFilter("$not", new Filter[]{filter});
    }

    /**
     * Creates a query filter that performs (op1 == op2).
     *
     * @param op1 first operand
     * @param op2 second operand
     * @return a new filter { op1: { "$eq": op2 } }
     */
    public static Filter eq(String op1, String op2) {
        return new SimpleFilter("$eq", op1, op2);
    }

    /**
     * Creates a query filter that performs (op1 == op2).
     *
     * @param op1 first operand
     * @param op2 second operand
     * @return a new filter { op1: { "$eq": op2 } }
     */
    public static Filter eq(String op1, long op2) {
        return new SimpleFilter("$eq", op1, op2);
    }

    /**
     * Creates a query filter that performs (op1 == op2).
     *
     * @param op1 first operand
     * @param op2 second operand
     * @return a new filter { op1: { "$eq": op2 } }
     */
    public static Filter eq(String op1, double op2) {
        return new SimpleFilter("$eq", op1, op2);
    }

    /**
     * Creates a query filter that performs (op1 == op2).
     *
     * @param op1 first operand
     * @param op2 second operand
     * @return a new filter { op1: { "$eq": op2 } }
     */
    public static Filter eq(String op1, boolean op2) {
        return new SimpleFilter("$eq", op1, op2);
    }

    /**
     * Creates a query filter that performs(op1 &gt; op2).
     *
     * @param op1 first operand
     * @param op2 second operand
     * @return a new filter { op1: { "$gt": op2 } }
     */
    public static Filter gt(String op1, long op2) {
        return new SimpleFilter("$gt", op1, op2);
    }

    /**
     * Creates a query filter that performs(op1 &gt; op2).
     *
     * @param op1 first operand
     * @param op2 second operand
     * @return a new filter { op1: { "$gt": op2 } }
     */
    public static Filter gt(String op1, double op2) {
        return new SimpleFilter("$gt", op1, op2);
    }

    /**
     * Creates a query filter that performs(op1 &gt;= op2).
     *
     * @param op1 first operand
     * @param op2 second operand
     * @return a new filter { op1: { "$gte": op2 } }
     */
    public static Filter gte(String op1, long op2) {
        return new SimpleFilter("$gte", op1, op2);
    }

    /**
     * Creates a query filter that performs(op1 &gt;= op2).
     *
     * @param op1 first operand
     * @param op2 second operand
     * @return a new filter { op1: { "$gte": op2 } }
     */
    public static Filter gte(String op1, double op2) {
        return new SimpleFilter("$gte", op1, op2);
    }

    /**
     * Creates a query filter that performs (op1 &lt; op2).
     *
     * @param op1 first operand
     * @param op2 second operand
     * @return a new filter { op1: { "$lt": op2 } }
     */
    public static Filter lt(String op1, long op2) {
        return new SimpleFilter("$lt", op1, op2);
    }

    /**
     * Creates a query filter that performs (op1 &lt; op2).
     *
     * @param op1 first operand
     * @param op2 second operand
     * @return a new filter { op1: { "$lt": op2 } }
     */
    public static Filter lt(String op1, double op2) {
        return new SimpleFilter("$lt", op1, op2);
    }

    /**
     * Creates a query filter that performs(op1 &lt;= op2).
     *
     * @param op1 first operand
     * @param op2 second operand
     * @return a new filter { op1: { "$lte": op2 } }
     */
    public static Filter lte(String op1, long op2) {
        return new SimpleFilter("$lte", op1, op2);
    }

    /**
     * Creates a query filter that performs(op1 &lt;= op2).
     *
     * @param op1 first operand
     * @param op2 second operand
     * @return a new filter { op1: { "$lte": op2 } }
     */
    public static Filter lte(String op1, double op2) {
        return new SimpleFilter("$lte", op1, op2);
    }

    /**
     * Creates a query filter that performs (op1 != op2).
     *
     * @param op1 first operand
     * @param op2 second operand
     * @return a new filter { op1: { "$ne": op2 } }
     */
    public static Filter ne(String op1, String op2) {
        return new SimpleFilter("$ne", op1, op2);
    }

    /**
     * Creates a query filter that performs (op1 != op2).
     *
     * @param op1 first operand
     * @param op2 second operand
     * @return a new filter { op1: { "$ne": op2 } }
     */
    public static Filter ne(String op1, long op2) {
        return new SimpleFilter("$ne", op1, op2);
    }

    /**
     * Creates a query filter that performs (op1 != op2).
     *
     * @param op1 first operand
     * @param op2 second operand
     * @return a new filter { op1: { "$ne": op2 } }
     */
    public static Filter ne(String op1, double op2) {
        return new SimpleFilter("$ne", op1, op2);
    }

    /**
     * Creates a query filter that performs (op1 != op2).
     *
     * @param op1 first operand
     * @param op2 second operand
     * @return a new filter { op1: { "$ne": op2 } }
     */
    public static Filter ne(String op1, boolean op2) {
        return new SimpleFilter("$ne", op1, op2);
    }

    /**
     * Creates a query filter that performs (key IN [value1, value2, ...])
     *
     * @param key    the key to check
     * @param values the list of values to compare with
     * @return a new filter { key: { "$in" : [value1, value2, ...] }}
     */
    public static Filter in(String key, String[] values) {
        return new SimpleFilter("$in", key, values);
    }

    /**
     * Creates a query filter that performs (key IN [value1, value2, ...])
     *
     * @param key    the key to check
     * @param values the list of values to compare with
     * @return a new filter { key: { "$in" : [value1, value2, ...] }}
     */
    public static Filter in(String key, long[] values) {
        return new SimpleFilter("$in", key, values);
    }

    /**
     * Creates a query filter that performs (key IN [value1, value2, ...])
     *
     * @param key    the key to check
     * @param values the list of values to compare with
     * @return a new filter { key: { "$in" : [value1, value2, ...] }}
     */
    public static Filter in(String key, double[] values) {
        return new SimpleFilter("$in", key, values);
    }

    /**
     * Creates a query filter that performs (key IN [value1, value2, ...])
     *
     * @param key    the key to check
     * @param values the list of values to compare with
     * @return a new filter { key: { "$in" : [value1, value2, ...] }}
     */
    public static Filter in(String key, boolean[] values) {
        return new SimpleFilter("$in", key, values);
    }

    /**
     * Creates a query filter that checks if op1 exists
     *
     * @param op1 first operand
     * @param e   either {true} to check that exists, or {@code false} to check that not exists
     * @return a new filter performing { op1: { "$exists" : true/false }}
     */
    public static Filter exists(String op1, boolean e) {
        return new SimpleFilter("$exists", op1, e);
    }

    /**
     * Creates a query filter that checks if the value matches the pattern
     *
     * @param value   the value to check
     * @param pattern the pattern to match
     * @return a new filter performing { op1: { "$like" : pattern }}
     */
    public static Filter like(String value, String pattern) {
        return new SimpleFilter("$like", value, pattern);
    }

    /**
     * Return the JSONObject for this query filter.
     *
     * @return a JSON representation of the filter.
     */
    public abstract JSONObject toJson();

    private static class CompositeFilter extends Filter {

        private final Filter[] operands;
        private final String operator;

        CompositeFilter(String operator, Filter[] operands) {
            this.operator = operator;
            this.operands = operands;
        }

        @Override
        public String toString() {
            return this.toJson().toString();
        }

        @Override
        public JSONObject toJson() {
            try {
                if (operands.length > 1) {
                    JSONArray array = new JSONArray();
                    for (Filter f : operands) {
                        array.put(f.toJson());
                    }

                    return new JSONObject().put(operator, array);
                }

                return new JSONObject().put(operator, operands[0].toJson());
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class SimpleFilter extends Filter {

        private final JSONObject value;

        SimpleFilter(String operator, String name, String value) {
            try {
                this.value = new JSONObject()
                    .put(name, new JSONObject().put(operator, value));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        SimpleFilter(String operator, String name, long value) {
            try {
                this.value = new JSONObject()
                    .put(name, new JSONObject().put(operator, value));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        SimpleFilter(String operator, String name, double value) {
            try {
                this.value = new JSONObject()
                    .put(name, new JSONObject().put(operator, value));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        SimpleFilter(String operator, String name, boolean value) {
            try {
                this.value = new JSONObject()
                    .put(name, new JSONObject().put(operator, value));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        SimpleFilter(String operator, String name, String[] value) {
            try {
                JSONArray array = new JSONArray();
                for (String f : value) {
                    array.put(f);
                }
                this.value = new JSONObject()
                    .put(name, new JSONObject().put(operator, array));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        SimpleFilter(String operator, String name, long[] value) {
            try {
                JSONArray array = new JSONArray();
                for (long f : value) {
                    array.put(f);
                }
                this.value = new JSONObject()
                    .put(name, new JSONObject().put(operator, array));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        SimpleFilter(String operator, String name, double[] value) {
            try {
                JSONArray array = new JSONArray();
                for (double f : value) {
                    array.put(f);
                }
                this.value = new JSONObject()
                    .put(name, new JSONObject().put(operator, array));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        SimpleFilter(String operator, String name, boolean[] value) {
            try {
                JSONArray array = new JSONArray();
                for (boolean f : value) {
                    array.put(f);
                }
                this.value = new JSONObject()
                    .put(name, new JSONObject().put(operator, array));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toString() {
            return this.toJson().toString();
        }

        @Override
        public JSONObject toJson() {
            return value;
        }
    }
}
