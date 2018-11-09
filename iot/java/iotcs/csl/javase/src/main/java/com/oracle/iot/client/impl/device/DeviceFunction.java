/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import com.oracle.iot.client.DeviceModelAction;
import com.oracle.iot.client.DeviceModelAttribute;
import com.oracle.iot.client.DeviceModelFormat;
import com.oracle.iot.client.StorageObject;
import com.oracle.iot.client.device.persistence.BatchByPersistence;
import com.oracle.iot.client.impl.DeviceModelImpl;
import com.oracle.iot.client.impl.util.Base64;
import com.oracle.iot.client.impl.util.Pair;
import com.oracle.iot.client.message.AlertMessage;
import com.oracle.iot.client.message.Message;
import com.oracle.iot.shared.Formula;
import com.oracle.iot.shared.FormulaParser;
import com.oracle.iot.shared.ValueProvider;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DeviceFunction is an abstraction of a policy device function.
 */
public abstract class DeviceFunction {

    private static final Double ZERO = 0.0d;

    /**
     * The {@code apply} method is where the logic for the function is coded.
     * This method returns {@code true} if the conditions for the function have
     * been met. Only when this function's apply method returns true
     * will the next function in the pipeline be applied.
     * <p>
     * After this method returns {@code true}, use
     * {@link #get(DeviceAnalog, String, Map, Map)} to retrieve
     * the value from the function.
     *
     * @param deviceAnalog the {@link oracle.iot.client.device.VirtualDevice}, never {@code null}
     * @param attribute the {@link DeviceModelAttribute}, which may be {@code null} if the
     *                             function is being applied at the device model level
     * @param configuration the parameters for this function from the device policy
     * @param data a place for storing data between invocations of the function
     * @param value the value to which the function is being applied
     * @return {@code true} if the conditions for the function have been satisfied.
     */
    public abstract boolean apply(DeviceAnalog deviceAnalog,
                                  String attribute,
                                  Map<String, ?> configuration,
                                  Map<String, Object> data,
                                  Object value);


    /**
     * Return the value from the function. This method should only be called after
     * {@link #apply(DeviceAnalog, String, Map, Map, Object)}
     * returns {@code true}, or when a window expires.
     * @param deviceAnalog the {@link oracle.iot.client.device.VirtualDevice}, never {@code null}
     * @param attribute the {@link DeviceModelAttribute}, which may be {@code null} if the
     *                             function is being applied at the device model level
     * @param configuration the parameters for this function from the device policy
     * @param data a place for storing data between invocations of the function
     * @return the value from having applied the function
     */
    public abstract Object get(DeviceAnalog deviceAnalog,
                               String attribute,
                               Map<String, ?> configuration,
                               Map<String, Object> data);

    /**
     * Utility for getting a "window" value from a configuration.
     * @param configuration the parameters for this function from the device policy
     * @return a window value, or -1 if the configuration is not time based
     */
    public static long getWindow(Map<String, ?> configuration) {
        for (String key : new String[]{"window", "delayLimit"}) {
            Number criterion = (Number) configuration.get(key);
            if (criterion != null) {
                return criterion.longValue();
            }
        }
        return -1l;

    }

    /**
     * Utility for getting a "slide" value from a configuration.
     * @param configuration the parameters for this function from the device policy
     * @param window the corresponding window for the slide
     * @return the configured slide value, or window if there is no slide or slide is zero
     */
    public static long getSlide(Map<String, ?> configuration, long window) {
        final Number slideParameter = (Number) configuration.get("slide");
        if (slideParameter != null) {
            final long slide = slideParameter.longValue();
            return slide > 0 ? slide : window;
        }
        return window;
    }

    /**
     * Get the id of the function. This is the unique id from the function definition.
     *
     * @return the policy id
     */
    public String getId() {
        return id;
    }

    @Override
    public String toString() { return id; }

    /**
     * Return a string representation of this function. Useful for logging.
     * @param configuration the parameters for this function from the device policy
     * @return a string representation of this function
     */
    public String getDetails(Map<String, ?> configuration) {
        return getId();
    }

    public static DeviceFunction getDeviceFunction(String functionId) {
        return POLICY_MAP.get(functionId);
    }

    // A map of attribute-name -> in-process attribute value.
    // The in-process value of an attribute is the value that is being
    // passed along the pipeline; i.e., $(attribute) in a formula or condition
    private static final Map<String,Object> inProcessValues = new HashMap<String,Object>();

    private static String createInProcessMapKey(String endpointId, String deviceModelURN, String attribute) {
        return endpointId.concat("/deviceModels/".concat(deviceModelURN.concat(":attributes/".concat(attribute))));
    }

    public static void putInProcessValue(String endpointId, String deviceModelURN, String attribute, Object value) {
        inProcessValues.put(createInProcessMapKey(endpointId, deviceModelURN, attribute), value);
    }

    public static Object removeInProcessValue(String endpointId, String deviceModelURN, String attribute) {
        return inProcessValues.remove(createInProcessMapKey(endpointId, deviceModelURN, attribute));
    }

    public static Object getInProcessValue(String endpointId, String deviceModelURN, String attribute) {
        return inProcessValues.get(createInProcessMapKey(endpointId, deviceModelURN, attribute));
    }

    //
    // Policy definitions
    //
    private final static DeviceFunction FILTER_CONDITION =
            new DeviceFunction("filterCondition") {

                @Override
                public boolean apply(DeviceAnalog deviceAnalog,
                                     String attribute,
                                     Map<String, ?> configuration,
                                     Map<String, Object> data,
                                     Object value) {

                    data.put("filterCondition.value", value);

                    FormulaParser.Node condition = (FormulaParser.Node) data.get("filterCondition.condition");
                    if (condition == null) {
                        final String str = (String)configuration.get("condition");
                        List<FormulaParser.Token> tokens = FormulaParser.tokenize(str);
                        Stack<FormulaParser.Node> stack = new Stack<FormulaParser.Node>();
                        FormulaParser.parseConditionalOrExpression(stack, tokens, str, 0);
                        condition = stack.pop();
                        data.put("filterCondition.condition", condition);
                    }

                    final Double computedValue = (Double)compute(condition, deviceAnalog);
                    // For a filter condition, if the computation returns 0.0, meaning
                    // the condition evaluated to false, then we want to return 'true'
                    // because "filter" means out, not in.
                    return -1.0 < computedValue && computedValue < 1.0;
                }

                @Override
                public Object get(DeviceAnalog deviceAnalog,
                                  String attribute,
                                  Map<String, ?> configuration,
                                  Map<String, Object> data) {
                    final Object value = data.remove("filterCondition.value");
                    return value;
                }

                @Override
                public String getDetails(Map<String, ?> config) {
                    return super.getDetails(config) + "[condition=\"" + config.get("condition") + "\"]";
                }
            };

    private final static DeviceFunction SAMPLE =
            new DeviceFunction("sampleQuality") {

                Random rand = new Random();

                @Override
                public boolean apply(DeviceAnalog deviceAnalog,
                                     String attribute,
                                     Map<String, ?> configuration,
                                     Map<String, Object> data,
                                     Object value) {

                    // Always put the value in the data map.
                    data.put("sample.value", value);

                    Integer terms = (Integer) data.get("sample.terms");
                    if (terms == null || terms == Integer.MAX_VALUE) {
                        terms = 0;
                    }

                    data.put("sample.terms", ++terms);

                    final Integer criterion = (Integer)configuration.get("rate");
                    // -1 is random, 0 is all
                    if (criterion == 0) {
                        return true;
                    } else if (criterion == -1) {
                        // TODO: make configurable
                        return (rand.nextInt(30) == 0);
                    }

                    return (criterion == terms);
                }

                @Override
                public Object get(DeviceAnalog deviceAnalog,
                                  String attribute,
                                  Map<String, ?> configuration,
                                  Map<String, Object> data) {
                    final Object sample = data.remove("sample.value");
                    data.remove("sample.terms");
                    return sample;
                }

                @Override
                public String getDetails(Map<String, ?> config) {
                    final Object rate = config.get("rate");
                    final boolean isString = "all".equals(rate) || "none".equals(rate) || "random".equals(rate);

                    return super.getDetails(config) + "[rate=" + (isString ? "\"" + rate + "\"" : rate) + "]";
                }

            };

    private final static DeviceFunction MEAN =
            new DeviceFunction("mean") {

                @Override
                @SuppressWarnings({"unchecked"})
                public boolean apply(DeviceAnalog deviceAnalog,
                                     String attribute,
                                     Map<String, ?> configuration,
                                     Map<String, Object> data,
                                     Object value) {

                    // Handling slide:
                    //
                    // Slide is how much the window moves after the
                    // window expires. If there is a window of 5 seconds
                    // with a slide of 2 seconds, then at the end of
                    // 5 seconds, the window slides over by two seconds.
                    // That means that the next window's worth of data
                    // would include 3 seconds of data from the previous
                    // window, and 2 seconds of new data.
                    //
                    // To handle this, we divide up the window into buckets.
                    // Each bucket represents a period of time, such
                    // that the time period is the greatest common factor
                    // between the window and the slide. For example, if
                    // the window is 60 seconds and the slide is 90
                    // seconds, a bucket would span 30 seconds, and
                    // there would be three buckets.
                    //
                    // When the window expires and the get method is called,
                    // the return value of the mean policy function will
                    // include the value and number of terms of bucket[0]
                    // through bucket[n]. Then the buckets that don't
                    // contribute to the next window are emptied (so to speak)
                    // and the cycle continues.
                    //
                    // Best case is that the slide equal to the window.
                    // In this case, there is only ever one bucket.
                    // The worst case is when greatest common factor between
                    // slide and window is small. In this case, you end up
                    // with a lot of buckets, potentially one bucket per
                    // slide time unit (e.g., 90 seconds, 90 buckets).
                    // But this is no worse (memory wise) than keeping
                    // an array of values and timestamps.
                    //

                    final long now = System.currentTimeMillis();

                    // windowStartTime is the time at which the first
                    // call to apply was made for the current window
                    // of time. We need to know when this window
                    // started so that we can figure out what bucket
                    // the data goes into.
                    Long windowStartTime = (Long)data.get("mean.windowStartTime");
                    if (windowStartTime == null) {
                        windowStartTime = now;
                        data.put("mean.windowStartTime", windowStartTime);
                    }

                    // The greatest common factor between the
                    // window and the slide represents the greatest
                    // amount of time that goes evenly into
                    // both window and slide.
                    final long window = getWindow(configuration);
                    final long slide = getSlide(configuration, window);
                    // Each bucket spans this amount of time.
                    final long span = gcd(window, slide);

                    Bucket<Double>[] buckets = (Bucket<Double>[])data.get("mean.buckets");
                    if (buckets == null) {

                        // The number of buckets is the window or span
                        // (which ever is greater) divided
                        // by the amount of time it spans. Thus, if
                        // there is a 5 second window with a 2 second slide,
                        // the greatest common factor is 1 second and we end
                        // up with 5 buckets. But if the slide was 7 seconds,
                        // you'd end up with 7 buckets. This lets us fill
                        // up buckets without worrying about whether the
                        // window is greater than, equal to, or less than
                        // the slide.
                        // Note: we add 1 so there is a bucket for when
                        // a value comes in for the next window, but before
                        // the window has been moved.
                        final int numberOfBuckets = (int)(Math.max(slide,window) / span) + 1;
                        buckets = new Bucket[numberOfBuckets];
                        for (int i = 0; i<numberOfBuckets; i++) {
                            buckets[i] = new Bucket(0d);
                        }
                        data.put("mean.buckets", buckets);
                    }

                    // bucketZero is the index of the zeroth bucket
                    // in the buckets array. This allows the buckets array
                    // to be treated as a circular buffer so we don't have
                    // to move array elements when the window slides.
                    Integer bucketZero = (Integer)data.get("mean.bucketZero");
                    if (bucketZero == null) {
                        bucketZero = 0;
                        data.put("mean.bucketZero", bucketZero);
                    }

                    // Which bucket are we working on is calculated
                    // by the dividing the amount of time we are into
                    // the window by the span of time represented by
                    // one bucket. For example, say we have a 2 second
                    // slide and a 10 second window giving us 5 buckets.
                    // Say our window started at 20 seconds and the
                    // value arrives at 25 seconds (5 seconds into the
                    // window). The value, then should be added to the
                    // third bucket (buckets[2]) since that bucket
                    // represents the time from 4 seconds to 6 seconds
                    // into the current window.
                    final int bucketIndex = (int)((now - windowStartTime) / span);
                    final int bucket = (bucketZero + bucketIndex) % buckets.length;

                    // May throw ClassCastException
                    Number number = (Number)value;

                    buckets[bucket].value += number.doubleValue();
                    buckets[bucket].terms += 1;

                    return false;
                }

                @Override
                @SuppressWarnings({"unchecked"})
                public Object get(DeviceAnalog deviceAnalog,
                                  String attribute,
                                  Map<String, ?> configuration,
                                  Map<String, Object> data) {

                    final Bucket<Double>[] buckets = (Bucket[])data.get("mean.buckets");
                    if (buckets == null) {
                        // must have called get before apply
                        return null;
                    }

                    final Integer bucketZero = (Integer)data.get("mean.bucketZero");
                    assert bucketZero != null;
                    if (bucketZero == null) {
                        // if buckets is not null, but bucketZero is,
                        // something is wrong with our implementation
                        return null;
                    }

                    // The greatest common factor between the
                    // window and the slide represents the greatest
                    // amount of time that goes evenly into
                    // both window and slide.
                    final long window = getWindow(configuration);
                    final long slide = getSlide(configuration, window);

                    // Each bucket spans this amount of time.
                    final long span = gcd(window, slide);

                    // The number of buckets that make up a window
                    final int bucketsPerWindow = (int)(window / span);

                    // The number of buckets that make up the slide
                    final int bucketsPerSlide = (int)(slide / span);

                    // update windowStartTime for the next window.
                    // The new windowStartTime is just the current window
                    // start time plus the slide.
                    Long windowStartTime = (Long)data.get("mean.windowStartTime");
                    assert windowStartTime != null;
                    if (windowStartTime == null) {
                        windowStartTime = System.currentTimeMillis();
                    }
                    data.put("mean.windowStartTime", windowStartTime + span * bucketsPerSlide);

                    // update bucketZero index. bucketZero is the index
                    // of the zeroth bucket in the circular buckets array.
                    data.put("mean.bucketZero", (bucketZero + bucketsPerSlide) % buckets.length);

                    double sum = 0;
                    int terms = 0;

                    // Loop through the number of buckets in the
                    // window and sum them up.
                    for (int i=0; i<bucketsPerWindow; i++) {
                        final int index = (bucketZero + i) % buckets.length;
                        Bucket<Double> bucket = buckets[index];
                        sum += bucket.value;
                        terms += bucket.terms;
                    }

                    // Now slide the window
                    for (int i=0; i<bucketsPerSlide; i++) {
                        Bucket bucket = buckets[(bucketZero + i) % buckets.length];
                        bucket.value = 0d;
                        bucket.terms = 0;
                    }

                    if ((Double.compare(sum, ZERO) == 0)
                            || (terms == 0)) {
                        return null;
                    }
                    return sum / terms;
                }

                @Override
                public String getDetails(Map<String, ?> config) {
                    StringBuilder details = new StringBuilder(super.getDetails(config));

                    final Object window = config.get("window");
                    if (window != null) {
                        details
                                .append("[window=")
                                .append(window);
                    }

                    final Object slide = config.get("slide");
                    if (slide != null) {
                        details
                                .append((window != null ? ',' : '['))
                                .append("slide=")
                                .append(slide);
                    }
                    details.append(']');
                    return details.toString();
                }

            };

    private final static DeviceFunction MIN =
            new DeviceFunction("min") {

                @Override
                @SuppressWarnings({"unchecked"})
                public boolean apply(DeviceAnalog deviceAnalog,
                                     String attribute,
                                     Map<String, ?> configuration,
                                     Map<String, Object> data,
                                     Object value) {

                    //
                    // See DeviceFunction("mean") for details on handling slide
                    // and what all this bucket stuff is about
                    //
                    final long now = System.currentTimeMillis();

                    Long windowStartTime = (Long)data.get("min.windowStartTime");
                    if (windowStartTime == null) {
                        windowStartTime = now;
                        data.put("min.windowStartTime", windowStartTime);
                    }

                    final long window = getWindow(configuration);
                    final long slide = getSlide(configuration, window);
                    final long span = gcd(window, slide);

                    Bucket<Number>[] buckets = (Bucket<Number>[])data.get("min.buckets");
                    if (buckets == null) {
                        final int numberOfBuckets = (int)(Math.min(slide,window) / span) + 1;
                        buckets = new Bucket[numberOfBuckets];
                        for (int i = 0; i<numberOfBuckets; i++) {
                            buckets[i] = new Bucket<Number>(Double.MAX_VALUE);
                        }
                        data.put("min.buckets", buckets);
                    }

                    Integer bucketZero = (Integer)data.get("min.bucketZero");
                    if (bucketZero == null) {
                        bucketZero = 0;
                        data.put("min.bucketZero", bucketZero);
                    }

                    final int bucketIndex = (int)((now - windowStartTime) / span);
                    final int bucket = (bucketZero + bucketIndex) % buckets.length;

                    // may throw ClassCastException
                    Number num = (Number) value;
                    Number min = buckets[bucket].value;

                    buckets[bucket].value = Double.compare(num.doubleValue(), min.doubleValue()) <= 0 ? num : min;
                    return false;

                }

                @Override
                @SuppressWarnings({"unchecked"})
                public Object get(DeviceAnalog deviceAnalog,
                                  String attribute,
                                  Map<String, ?> configuration,
                                  Map<String, Object> data) {

                    // See DeviceFunction("mean")#get for explanation of slide and buckets
                    final Bucket<Number>[] buckets = (Bucket[])data.get("min.buckets");
                    if (buckets == null) {
                        // must have called get before apply
                        return null;
                    }

                    final Integer bucketZero = (Integer)data.get("min.bucketZero");
                    assert bucketZero != null;
                    if (bucketZero == null) {
                        // if buckets is not null, but bucketZero is,
                        // something is wrong with our implementation
                        return null;
                    }

                    final long window = getWindow(configuration);
                    final long slide = getSlide(configuration, window);
                    final long span = gcd(window, slide);
                    final int bucketsPerWindow = (int)(window / span);
                    final int bucketsPerSlide = (int)(slide / span);

                    Long windowStartTime = (Long)data.get("min.windowStartTime");
                    assert windowStartTime != null;
                    if (windowStartTime == null) {
                        windowStartTime = System.currentTimeMillis();
                    }
                    data.put("min.windowStartTime", windowStartTime + span * bucketsPerSlide);
                    data.put("min.bucketZero", (bucketZero + bucketsPerSlide) % buckets.length);

                    Number min = Double.MAX_VALUE;

                    for (int i=0; i<bucketsPerWindow; i++) {
                        final int index = (bucketZero + i) % buckets.length;
                        Bucket<Number> bucket = buckets[index];
                        Number num = bucket.value;
                        min = Double.compare(num.doubleValue(), min.doubleValue()) <= 0 ? num : min;
                    }

                    for (int i=0; i<bucketsPerSlide; i++) {
                        Bucket<Number> bucket = buckets[(bucketZero + i) % buckets.length];
                        bucket.value = Double.MAX_VALUE;
                    }
                    return min;
                }

                @Override
                public String getDetails(Map<String, ?> config) {
                    StringBuilder details = new StringBuilder(super.getDetails(config));

                    final Object window = config.get("window");
                    if (window != null) {
                        details
                                .append("[window=")
                                .append(window);
                    }

                    final Object slide = config.get("slide");
                    if (slide != null) {
                        details
                                .append((window != null ? ',' : '['))
                                .append("slide=")
                                .append(slide);
                    }
                    details.append(']');
                    return details.toString();
                }
            };

    private final static DeviceFunction MAX =
            new DeviceFunction("max") {

                @Override
                @SuppressWarnings({"unchecked"})
                public boolean apply(DeviceAnalog deviceAnalog,
                                     String attribute,
                                     Map<String, ?> configuration,
                                     Map<String, Object> data,
                                     Object value) {

                    //
                    // See DeviceFunction("mean") for details on handling slide
                    // and what all this bucket stuff is about
                    //
                    final long now = System.currentTimeMillis();

                    Long windowStartTime = (Long)data.get("max.windowStartTime");
                    if (windowStartTime == null) {
                        windowStartTime = now;
                        data.put("max.windowStartTime", windowStartTime);
                    }

                    final long window = getWindow(configuration);
                    final long slide = getSlide(configuration, window);
                    final long span = gcd(window, slide);

                    Bucket<Number>[] buckets = (Bucket<Number>[])data.get("max.buckets");
                    if (buckets == null) {
                        final int numberOfBuckets = (int)(Math.max(slide,window) / span) + 1;
                        buckets = new Bucket[numberOfBuckets];
                        for (int i = 0; i<numberOfBuckets; i++) {
                            buckets[i] = new Bucket<Number>(Double.MIN_VALUE);
                        }
                        data.put("max.buckets", buckets);
                    }

                    Integer bucketZero = (Integer)data.get("max.bucketZero");
                    if (bucketZero == null) {
                        bucketZero = 0;
                        data.put("max.bucketZero", bucketZero);
                    }

                    final int bucketIndex = (int)((now - windowStartTime) / span);
                    final int bucket = (bucketZero + bucketIndex) % buckets.length;

                    // may throw ClassCastException
                    Number num = (Number) value;
                    Number max = buckets[bucket].value;

                    buckets[bucket].value = Double.compare(num.doubleValue(), max.doubleValue()) <= 0 ? max : num;
                    return false;

                }

                @Override
                @SuppressWarnings({"unchecked"})
                public Object get(DeviceAnalog deviceAnalog,
                                  String attribute,
                                  Map<String, ?> configuration,
                                  Map<String, Object> data) {

                    // See DeviceFunction("mean")#get for explanation of slide and buckets
                    final Bucket<Number>[] buckets = (Bucket[])data.get("max.buckets");
                    if (buckets == null) {
                        // must have called get before apply
                        return null;
                    }

                    final Integer bucketZero = (Integer)data.get("max.bucketZero");
                    assert bucketZero != null;
                    if (bucketZero == null) {
                        // if buckets is not null, but bucketZero is,
                        // something is wrong with our implementation
                        return null;
                    }

                    final long window = getWindow(configuration);
                    final long slide = getSlide(configuration, window);
                    final long span = gcd(window, slide);
                    final int bucketsPerWindow = (int)(window / span);
                    final int bucketsPerSlide = (int)(slide / span);

                    Long windowStartTime = (Long)data.get("max.windowStartTime");
                    assert windowStartTime != null;
                    if (windowStartTime == null) {
                        windowStartTime = System.currentTimeMillis();
                    }
                    data.put("max.windowStartTime", windowStartTime + span * bucketsPerSlide);
                    data.put("max.bucketZero", (bucketZero + bucketsPerSlide) % buckets.length);

                    Number max = Double.MIN_VALUE;

                    for (int i=0; i<bucketsPerWindow; i++) {
                        final int index = (bucketZero + i) % buckets.length;
                        Bucket<Number> bucket = buckets[index];
                        Number num = bucket.value;
                        max = Double.compare(num.doubleValue(), max.doubleValue()) <= 0 ? max : num;
                    }

                    for (int i=0; i<bucketsPerSlide; i++) {
                        Bucket<Number> bucket = buckets[(bucketZero + i) % buckets.length];
                        bucket.value = Double.MIN_VALUE;
                    }

                    return max;
                }

                @Override
                public String getDetails(Map<String, ?> config) {
                    StringBuilder details = new StringBuilder(super.getDetails(config));

                    final Object window = config.get("window");
                    if (window != null) {
                        details
                                .append("[window=")
                                .append(window);
                    }

                    final Object slide = config.get("slide");
                    if (slide != null) {
                        details
                                .append((window != null ? ',' : '['))
                                .append("slide=")
                                .append(slide);
                    }
                    details.append(']');
                    return details.toString();
                }
            };

    private final static DeviceFunction STANDARD_DEVIATION =
            new DeviceFunction("standardDeviation") {

                @Override
                @SuppressWarnings({"unchecked"})
                public boolean apply(DeviceAnalog deviceAnalog,
                                     String attribute,
                                     Map<String, ?> configuration,
                                     Map<String, Object> data,
                                     Object value) {

                    //
                    // See DeviceFunction("mean") for details on handling slide
                    // and what all this bucket stuff is about
                    //
                    final long now = System.currentTimeMillis();

                    Long windowStartTime = (Long)data.get("standardDeviation.windowStartTime");
                    if (windowStartTime == null) {
                        windowStartTime = now;
                        data.put("standardDeviation.windowStartTime", windowStartTime);
                    }

                    final long window = getWindow(configuration);
                    final long slide = getSlide(configuration, window);
                    final long span = gcd(window, slide);

                    Bucket<List<Number>>[] buckets = (Bucket<List<Number>>[])data.get("standardDeviation.buckets");
                    if (buckets == null) {
                        final int numberOfBuckets = (int)(Math.min(slide,window) / span) + 1;
                        buckets = new Bucket[numberOfBuckets];
                        for (int i = 0; i<numberOfBuckets; i++) {
                            buckets[i] = new Bucket<List<Number>>(new ArrayList<Number>());
                        }
                        data.put("standardDeviation.buckets", buckets);
                    }

                    Integer bucketZero = (Integer)data.get("standardDeviation.bucketZero");
                    if (bucketZero == null) {
                        bucketZero = 0;
                        data.put("standardDeviation.bucketZero", bucketZero);
                    }

                    final int bucketIndex = (int)((now - windowStartTime) / span);
                    final int bucket = (bucketZero + bucketIndex) % buckets.length;


                    // may throw ClassCastException
                    Number number = (Number) value;
                    buckets[bucket].value.add(number);

                    return false;
                }

                @Override
                @SuppressWarnings({"unchecked"})
                public Object get(DeviceAnalog deviceAnalog,
                                  String attribute,
                                  Map<String, ?> configuration,
                                  Map<String, Object> data) {

                    // See DeviceFunction("mean")#get for explanation of slide and buckets
                    final Bucket<List<Number>>[] buckets = (Bucket[])data.get("standardDeviation.buckets");
                    if (buckets == null) {
                        // must have called get before apply
                        return null;
                    }

                    final Integer bucketZero = (Integer)data.get("standardDeviation.bucketZero");
                    assert bucketZero != null;
                    if (bucketZero == null) {
                        // if buckets is not null, but bucketZero is,
                        // something is wrong with our implementation
                        return null;
                    }

                    final long window = getWindow(configuration);
                    final long slide = getSlide(configuration, window);
                    final long span = gcd(window, slide);
                    final int bucketsPerWindow = (int)(window / span);
                    final int bucketsPerSlide = (int)(slide / span);

                    Long windowStartTime = (Long)data.get("standardDeviation.windowStartTime");
                    assert windowStartTime != null;
                    if (windowStartTime == null) {
                        windowStartTime = System.currentTimeMillis();
                    }
                    data.put("standardDeviation.windowStartTime", windowStartTime + span * bucketsPerSlide);
                    data.put("standardDeviation.bucketZero", (bucketZero + bucketsPerSlide) % buckets.length);

                    List<Number> terms = new ArrayList<Number>();

                    for (int i=0; i<bucketsPerWindow; i++) {
                        final int index = (bucketZero + i) % buckets.length;
                        Bucket<List<Number>> bucket = buckets[index];
                        List<Number> values = bucket.value;
                        terms.addAll(values);
                    }

                    double sum = 0;
                    for (int n = 0, nMax = terms.size(); n < nMax; n++) {
                        double d = terms.get(n).doubleValue();
                        sum += d;
                    }

                    double mean = sum / terms.size();

                    for (int n = 0, nMax = terms.size(); n < nMax; n++) {
                        double d = terms.get(n).doubleValue() - mean;
                        terms.set(n, Math.pow(d, 2));
                    }

                    sum = 0d;
                    for (int n = 0, nMax = terms.size(); n < nMax; n++) {
                        double d = terms.get(n).doubleValue();
                        sum += d;
                    }
                    mean = sum / terms.size();

                    double stdDeviation = Math.sqrt(mean);

                    for (int i=0; i<bucketsPerSlide; i++) {
                        Bucket<List<Number>> bucket = buckets[(bucketZero + i) % buckets.length];
                        bucket.value.clear();
                    }
                    return stdDeviation;
                }

                @Override
                public String getDetails(Map<String, ?> config) {
                    StringBuilder details = new StringBuilder(super.getDetails(config));

                    final Object window = config.get("window");
                    if (window != null) {
                        details
                                .append("[window=")
                                .append(window);
                    }

                    final Object slide = config.get("slide");
                    if (slide != null) {
                        details
                                .append((window != null ? ',' : '['))
                                .append("slide=")
                                .append(slide);
                    }
                    details.append(']');
                    return details.toString();
                }

            };

    private final static DeviceFunction BATCH_BY_SIZE =
            new DeviceFunction("batchBySize") {

                @Override
                @SuppressWarnings({"unchecked"})
                public boolean apply(DeviceAnalog deviceAnalog,
                                     String attribute,
                                     Map<String, ?> configuration,
                                     Map<String, Object> data,
                                     Object value) {

                    final BatchByPersistence batchByPersistence = BatchByPersistence.getInstance();
                    if (batchByPersistence != null) {
                        final Message message = ((Pair<Message,StorageObject>)value).getKey();
                        batchByPersistence.save(message, deviceAnalog.getEndpointId());

                    } else {
                        List<Object> list = (List<Object>) data.get("batchBySize.value");
                        if (list == null) {
                            list = new ArrayList<Object>();
                            data.put("batchBySize.value", list);
                        }

                        list.add(value);
                    }

                    Integer batchCount = (Integer)data.get("batchBySize.batchCount");
                    if (batchCount == null) {
                        batchCount = 0;
                    }

                    batchCount += 1;
                    data.put("batchBySize.batchCount", batchCount);

                    Integer batchSize = (Integer) configuration.get("batchSize");
                    if (batchSize == null || batchSize == batchCount) {
                        return true;
                    }

                    return false;

                }

                @Override
                @SuppressWarnings({"unchecked"})
                public Object get(DeviceAnalog deviceAnalog,
                                  String attribute,
                                  Map<String, ?> configuration,
                                  Map<String, Object> data) {
                    data.put("batchBySize.batchCount", 0);
                    final BatchByPersistence batchByPersistence = BatchByPersistence.getInstance();
                    if (batchByPersistence != null) {
                        final List<Pair<Message,StorageObject>> value = getPersistedBatchedData(batchByPersistence, deviceAnalog);
                        return value;

                    } else {
                        final Object value = data.remove("batchBySize.value");
                        return value;
                    }
                }

                @Override
                public String getDetails(Map<String, ?> config) {
                    return super.getDetails(config) + "[batchSize=" + config.get("batchSize") + "]";
                }

            };

    private static List<Pair<Message,StorageObject>> getPersistedBatchedData(BatchByPersistence batchByPersistence, DeviceAnalog deviceAnalog) {
        final List<Message> messages = batchByPersistence.load(deviceAnalog.getEndpointId());
        batchByPersistence.delete(messages);

//        final DeviceModelImpl deviceModel = (DeviceModelImpl)deviceAnalog.getDeviceModel();
//        final Map<String,DeviceModelAttribute> deviceModelAttributes = deviceModel.getDeviceModelAttributes();

        final List<Pair<Message,StorageObject>> pairs = new ArrayList<Pair<Message,StorageObject>>();
        for (Message message : messages) {
            pairs.add(new Pair(message, null));

//            TODO: need to handle storage object somehow
//            if (message instanceof DataMessage) {
//                final DataMessage dataMessage = (DataMessage) message;
//                final List<DataItem<?>> dataItems = dataMessage.getDataItems();
//                // go through data items to see if any are related to a storage object
//                for (DataItem<?> dataItem : dataItems) {
//                    final String key = dataItem.getKey();
//                    final DeviceModelAttribute<?> deviceModelAttribute = deviceModelAttributes.get(key);
//                    assert deviceModelAttribute != null;
//                    if (deviceModelAttribute != null && deviceModelAttribute.getType() == DeviceModelAttribute.Type.URI) {
//                        final String uri = (String)dataItem.getValue();
//                        deviceAnalog.
//                    }
//                }
//            }

        }
        return pairs;
    }

    private final static DeviceFunction BATCH_BY_TIME =
            new DeviceFunction("batchByTime") {

                @Override
                @SuppressWarnings({"unchecked"})
                public boolean apply(DeviceAnalog deviceAnalog,
                                     String attribute,
                                     Map<String, ?> configuration,
                                     Map<String, Object> data,
                                     Object value) {

                    final BatchByPersistence batchByPersistence = BatchByPersistence.getInstance();
                    if (batchByPersistence != null) {
                        final Message message = ((Pair<Message,StorageObject>)value).getKey();
                        batchByPersistence.save(message, deviceAnalog.getEndpointId());

                    } else {
                        List<Object> list = (List<Object>) data.get("batchByTime.value");
                        if (list == null) {
                            list = new ArrayList<>();
                            data.put("batchByTime.value", list);
                        }

                        list.add(value);
                    }

                    return false;

                }

                @Override
                @SuppressWarnings({"unchecked"})
                public Object get(DeviceAnalog deviceAnalog,
                                  String attribute,
                                  Map<String, ?> configuration,
                                  Map<String, Object> data) {

                    final BatchByPersistence batchByPersistence = BatchByPersistence.getInstance();
                    if (batchByPersistence != null) {
                        final List<Pair<Message,StorageObject>> value = getPersistedBatchedData(batchByPersistence, deviceAnalog);
                        return value;

                    } else {
                        final Object value = data.remove("batchByTime.value");
                        return value;
                    }
                }

                @Override
                public String getDetails(Map<String, ?> config) {
                    return super.getDetails(config) + "[delayLimit=" + config.get("delayLimit") + "]";
                }

            };

    // NetworkCost in order of increasing cost.
    private enum NetworkCost {
        ETHERNET,
        CELLULAR,
        SATELLITE;

        // Get the cost of the NetworkCost given by the string value.
        // The "property" parameter is just used for logging.
        // The defaultValue is the value returned if the value is null or not a valid NetworkCost
        static int getCost(String value, String property, NetworkCost defaultValue) {
            NetworkCost networkCost = null;
            if (value != null) {
                try {
                    String upperValue = value.toUpperCase(Locale.ROOT);
                    upperValue = upperValue.replaceAll("\\(.*","");
                    networkCost = NetworkCost.valueOf(upperValue);
                } catch (IllegalArgumentException e) {
                    DeviceFunction.getLogger().log(Level.WARNING, "invalid '" + property + "' value: '" + value + "'");
                }
            }
            if (networkCost == null) {
                // not given or illegal value
                networkCost = defaultValue;
                DeviceFunction.getLogger().log(Level.WARNING, "defaulting '" + property + "' to: '" + networkCost + "'");
            }
            return networkCost.ordinal();
        }
    }

    // Will batch data until networkCost (Satellite > Cellular > Ethernet) lowers to the configured value
    private final static DeviceFunction BATCH_BY_COST =
            new DeviceFunction("batchByCost") {

                @Override
                @SuppressWarnings({"unchecked"})
                public boolean apply(DeviceAnalog deviceAnalog,
                                     String attribute,
                                     Map<String, ?> configuration,
                                     Map<String, Object> data,
                                     Object value) {

                    final BatchByPersistence batchByPersistence = BatchByPersistence.getInstance();
                    if (batchByPersistence != null) {
                        final Message message = ((Pair<Message,StorageObject>)value).getKey();
                        batchByPersistence.save(message, deviceAnalog.getEndpointId());

                    } else {
                        List<Object> list = (List<Object>) data.get("batchByCost.value");
                        if (list == null) {
                            list = new ArrayList<>();
                            data.put("batchByCost.value", list);
                        }

                        list.add(value);
                    }

                    final int configuredCost =
                            NetworkCost.getCost(
                                    (String) configuration.get("networkCost"),
                                    "networkCost",
                                    NetworkCost.SATELLITE // Assume the configured cost is the most expensive
                            );


                    final int networkCost =
                            NetworkCost.getCost(
                                    System.getProperty("oracle.iot.client.network_cost"),
                                    "oracle.iot.client.network_cost",
                                    NetworkCost.ETHERNET // Assume the client cost is the least expensive
                            );

                    // If the cost of the network the client is on (networkCost) is greater than
                    // the cost of the network the policy is willing to bear (configuredCost),
                    // then return false (the value is filtered).
                    if (networkCost > configuredCost) {
                        return false;
                    }

                    return true;

                }

                @Override
                @SuppressWarnings({"unchecked"})
                public Object get(DeviceAnalog deviceAnalog,
                                  String attribute,
                                  Map<String, ?> configuration,
                                  Map<String, Object> data) {
                    final BatchByPersistence batchByPersistence = BatchByPersistence.getInstance();
                    if (batchByPersistence != null) {
                        final List<Pair<Message,StorageObject>> value = getPersistedBatchedData(batchByPersistence, deviceAnalog);
                        return value;

                    } else {
                        final Object value = data.remove("batchByCost.value");
                        return value;
                    }
                }

                @Override
                public String getDetails(Map<String, ?> config) {
                    return super.getDetails(config) + "[networkCost=" + config.get("networkCost") + "]";
                }

            };

    private final static DeviceFunction ELIMINATE_DUPLICATES =
            new DeviceFunction("eliminateDuplicates") {

                @Override
                public boolean apply(DeviceAnalog deviceAnalog,
                                     String attribute,
                                     Map<String, ?> configuration,
                                     Map<String, Object> data,
                                     Object value) {

                    boolean isDuplicate = false;

                    final long now = System.currentTimeMillis();
                    final Object lastValue = data.put("eliminateDuplicates.lastValue", value);

                    // If value equals lastValue, then this is a duplicate value.
                    // If value is the first duplicate value, then lastValue has already
                    // been passed along and we want to filter out the current value and
                    // all other sequential duplicates within the window.
                    if (value.equals(lastValue)) {

                        // windowEnd is the end time of the current window.
                        final Long windowEnd = (Long) data.get("eliminateDuplicates.windowEnd");
                        assert windowEnd != null;

                        // If the current window has not expired (i.e., now <= windowEnd),
                        // then the value is filtered out.
                        isDuplicate = (now <= windowEnd);

                        // If the current window has expired (i.e., windowEnd <= now),
                        // then update windowEnd.
                        if (windowEnd <= now) {
                            // windowEnd is the current time plus the window.
                            // window is normalized so that window is greater than or equal to zero.
                            final long window = getWindow(configuration);
                            data.put("eliminateDuplicates.windowEnd", now + (window > 0 ? window : 0));
                        }

                    } else {
                        // Values are not duplicates. Move window.
                        // windowEnd is the current time plus the window.
                        // window is normalized so that window is greater than or equal to zero.
                        final long window = getWindow(configuration);
                        data.put("eliminateDuplicates.windowEnd", now + (window > 0 ? window : 0));
                    }
                    return !isDuplicate;
                }

                @Override
                public Object get(DeviceAnalog deviceAnalog,
                                  String attribute,
                                  Map<String, ?> configuration,
                                  Map<String, Object> data) {
                    return data.get("eliminateDuplicates.lastValue");
                }

                @Override
                public String getDetails(Map<String, ?> config) {
                    return super.getDetails(config) + "[window=" + config.get("window") + "]";
                }

            };

    private final static DeviceFunction DETECT_DUPLICATES =
            new DeviceFunction("detectDuplicates") {

                @Override
                public boolean apply(DeviceAnalog deviceAnalog,
                                     String attribute,
                                     Map<String, ?> configuration,
                                     Map<String, Object> data,
                                     Object value) {

                    final long now = System.currentTimeMillis();
                    final Object lastValue = data.put("detectDuplicates.lastValue", value);

                    // If value equals lastValue, then this is a duplicate value.
                    // If value is the first duplicate value, then lastValue has already
                    // been passed along and we want to filter out the current value and
                    // all other sequential duplicates within the window.
                    if (value.equals(lastValue)) {

                        // windowEnd is the end time of the current window.
                        final Long windowEnd = (Long) data.get("detectDuplicates.windowEnd");

                        // If the current window has expired (i.e., windowEnd <= now),
                        // then update windowEnd.
                        if (windowEnd <= now) {
                            // windowEnd is the current time plus the window.
                            // window is normalized so that window is greater than or equal to zero.
                            final long window = getWindow(configuration);
                            data.put("detectDuplicates.windowEnd", now + (window > 0 ? window : 0));
                            // when the window moves, we need to send an alert.
                            data.put("detectDuplicates.alertSent", false);
                        }

                        // The first time we get here, alertSent will be false (because
                        // of the "else" part below) and an alert will be sent.
                        // alertSent will then be true until the window expires or
                        // a non-duplicate value is received.
                        final Boolean alertSent = (Boolean)data.get("detectDuplicates.alertSent");

                        if (!alertSent) {
                            data.put("detectDuplicates.alertSent", true);
                            final AlertMessage alertMessage = DeviceFunction.createAlert(
                                deviceAnalog,
                                configuration
                            );
                            deviceAnalog.queueMessage(alertMessage);
                        }


                    } else {
                        // Values are not duplicates. Move window.
                        // windowEnd is the current time plus the window.
                        // window is normalized so that window is greater than or equal to zero.
                        final long window = getWindow(configuration);
                        data.put("detectDuplicates.windowEnd", now + (window > 0 ? window : 0));
                        data.put("detectDuplicates.alertSent", false);
                    }

                    // detectDuplicates does not filter data. Return true.
                    return true;
                }

                @Override
                public Object get(DeviceAnalog deviceAnalog,
                                  String attribute,
                                  Map<String, ?> configuration,
                                  Map<String, Object> data) {

                    final Object value = data.get("detectDuplicates.lastValue");
                    return value;
                }

                @Override
                public String getDetails(Map<String, ?> config) {
                    return super.getDetails(config) +
                            "[window=" + config.get("window") +
                            ", alertFormatURN=\""+ config.get("alertFormatURN")+"\"]";
                }

            };

    private final static DeviceFunction PRIVACY_POLICY =
            new DeviceFunction("privacyPolicy") {

                @Override
                public boolean apply(DeviceAnalog deviceAnalog,
                                     String attribute,
                                     Map<String, ?> configuration,
                                     Map<String, Object> data,
                                     Object value) {

                    data.put("privacyPolicy.value", value);
                    return true;
                }

                @Override
                public Object get(DeviceAnalog deviceAnalog,
                                  String attribute,
                                  Map<String, ?> configuration,
                                  Map<String, Object> data) {

                    final Object value = data.get("privacyPolicy.value");

                    if (value != null) {

                        final String string = value.toString();
                        final byte[] content;
                        try {
                            content = string.getBytes("UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            // Cannot happen, UTF-8 is requred encoding
                            // make compiler happy
                            throw new RuntimeException(e);
                        }

                        String level = (String) configuration.get("level");
                        if ("one-way".equals(level)) {

                            try {
                                //https://docs.oracle.com/javase/6/docs/technotes/guides/security/StandardNames.html#MessageDigest
                                final MessageDigest md = MessageDigest.getInstance("SHA-256");
                                final byte[] digest = md.digest(content);
                                final String urlEncodedDigest = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
                                return urlEncodedDigest;
                            } catch (NoSuchAlgorithmException e) {
                                // Cannot happen, SHA-256 is requred algorithm
                                return value;
                            }

                        } else if ("two-way".equals(level)) {

                            final String hashingKey = (String) configuration.get("hashingKey");
                            if (hashingKey == null) {
                                DeviceFunction.getLogger().log(Level.WARNING, "no hashingKey given");
                                return value;
                            }
                            final byte[] key;
                            try {
                                key = hashingKey.getBytes("UTF-8");
                            } catch (UnsupportedEncodingException e) {
                                // Cannot happen, UTF-8 is requred encoding
                                // make compiler happy
                                throw new RuntimeException(e);
                            }
                            try {
                                final SecretKeySpec keySpec = new SecretKeySpec(key, "HmacSHA256");
                                final Mac mac = Mac.getInstance("HmacSHA256");
                                mac.init(keySpec);
                                mac.update(content);
                                final byte[] digest = mac.doFinal();
                                final String urlEncodedDigest = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
                                return urlEncodedDigest;
                            } catch (NoSuchAlgorithmException e) {
                                DeviceFunction.getLogger().log(Level.WARNING, e.getMessage());
                                return value;
                            } catch (InvalidKeyException e) {
                                DeviceFunction.getLogger().log(Level.WARNING, e.getMessage());
                                return value;
                            }
                        } else if ("random".equals(level)) {
                            DeviceFunction.getLogger().log(Level.WARNING, "random privacy not supported");
                        } else {
                            // level is "none" or not given (default)
                            return value;
                        }
                    }
                    return null;
                }

                @Override
                public String getDetails(Map<String, ?> config) {
                    return super.getDetails(config) + "[level=\"" + config.get("level") + "\"]";
                }

            };

    private final static DeviceFunction ALERT_CONDITION =
            new DeviceFunction("alertCondition") {

                @Override
                public boolean apply(DeviceAnalog deviceAnalog,
                                     String attribute,
                                     Map<String, ?> configuration,
                                     Map<String, Object> data,
                                     Object value) {

                    FormulaParser.Node condition = (FormulaParser.Node) data.get("alertCondition.condition");
                    if (condition == null) {
                        final String str = (String)configuration.get("condition");
                        List<FormulaParser.Token> tokens = FormulaParser.tokenize(str);
                        Stack<FormulaParser.Node> stack = new Stack<FormulaParser.Node>();
                        FormulaParser.parseConditionalOrExpression(stack, tokens, str, 0);
                        condition = stack.pop();
                        data.put("alertCondition.condition", condition);
                    }

                    final Double computedValue = (Double)compute(condition, deviceAnalog);
                    if (computedValue.isNaN() || computedValue.isInfinite()
                            || computedValue.compareTo(0.0) == 0) { // zero is false
                        data.put("alertCondition.value", value);
                        return true;
                    }

                    try {
                        final AlertMessage alertMessage = DeviceFunction.createAlert(
                                deviceAnalog,
                                configuration
                        );

                        if (getLogger().isLoggable(Level.FINE)) {
                            getLogger().log(Level.FINE,
                                    deviceAnalog.getEndpointId() +
                                            " : Alert : \"" + attribute + "\"=" + value +
                                            " (" + alertMessage.getDescription() + ")");
                        }


                        deviceAnalog.queueMessage(alertMessage);
                    } catch (Exception e) {
                        getLogger().log(Level.SEVERE, e.toString(), e);
                    }

                    Boolean filter = (Boolean) configuration.get("filter");
                    if (filter == null || filter) {
                        // if this is a filter, returning false stops the pipeline
                        return false;
                    }

                    data.put("alertCondition.value", value);
                    return true;
                }

                @Override
                public Object get(DeviceAnalog deviceAnalog,
                                  String attribute,
                                  Map<String, ?> configuration,
                                  Map<String, Object> data) {

                    final Object value = data.remove("alertCondition.value");
                    return value;
                }

                @Override
                public String getDetails(Map<String, ?> config) {
                    final Object filter =
                            config.containsKey("filter")
                                    ? config.get("filter")
                                    : Boolean.TRUE;

                    return super.getDetails(config) +
                            "[condition=\"" + config.get("condition") +
                            "\", urn=\""+ config.get("urn")+"\", fields=" +
                            (Map<String,String>)config.get("fields") +
                            "\", filter="+ filter +"]";

                }
            };

    private final static DeviceFunction COMPUTED_METRIC =
            new DeviceFunction("computedMetric") {

                @Override
                public boolean apply(DeviceAnalog deviceAnalog,
                                     String attribute,
                                     Map<String, ?> configuration,
                                     Map<String, Object> data,
                                     Object value) {

                    FormulaParser.Node formula = (FormulaParser.Node) data.get("computedMetric.formula");
                    if (formula == null) {
                        final String str = (String)configuration.get("formula");
                        List<FormulaParser.Token> tokens = FormulaParser.tokenize(str);
                        formula = FormulaParser.parseFormula(tokens, str);
                        data.put("computedMetric.formula", formula);
                    }

                    final Double computedValue = (Double)compute(formula, deviceAnalog);
                    if (computedValue.isNaN() || computedValue.isInfinite()) {
                        return false;
                    }

                    data.put("computedMetric.value", computedValue);
                    return true;
                }

                @Override
                public Object get(DeviceAnalog deviceAnalog,
                                  String attribute,
                                  Map<String, ?> configuration,
                                  Map<String, Object> data) {
                    return data.remove("computedMetric.value");
                }


                @Override
                public String getDetails(Map<String, ?> config) {
                    return super.getDetails(config) + "[formula=\"" + config.get("formula") + "\"]";
                }

            };

    private final static DeviceFunction ACTION_CONDITION =
            new DeviceFunction("actionCondition") {

                @Override
                public boolean apply(DeviceAnalog deviceAnalog,
                                     String attribute,
                                     Map<String, ?> configuration,
                                     Map<String, Object> data,
                                     Object value) {

                    FormulaParser.Node condition = (FormulaParser.Node) data.get("actionCondition.condition");
                    if (condition == null) {
                        final String str = (String)configuration.get("condition");
                        List<FormulaParser.Token> tokens = FormulaParser.tokenize(str);
                        Stack<FormulaParser.Node> stack = new Stack<FormulaParser.Node>();
                        FormulaParser.parseConditionalOrExpression(stack, tokens, str, 0);
                        condition = stack.pop();
                        data.put("actionCondition.condition", condition);
                    }

                    final Double computedValue = (Double)compute(condition, deviceAnalog);
                    if (computedValue.isNaN() || computedValue.isInfinite()
                            || computedValue.compareTo(0.0) == 0) { // zero is false
                        data.put("actionCondition.value", value);
                        return true;
                    }

                    // getActionArgs may return null
                    final Object[] actionArgs = getActionArgs(deviceAnalog, configuration);
                    final String actionName = (String)configuration.get("name");

                    try {
                        deviceAnalog.call(actionName, actionArgs);
                    } catch (Exception e) {
                        getLogger().log(Level.SEVERE, e.toString(), e);
                    }

                    Boolean filter = (Boolean) configuration.get("filter");
                    if (filter == null || filter) {
                        // if this is a filter, returning false stops the pipeline
                        return false;
                    }

                    data.put("actionCondition.value", value);
                    return true;
                }

                @Override
                public Object get(DeviceAnalog deviceAnalog,
                                  String attribute,
                                  Map<String, ?> configuration,
                                  Map<String, Object> data) {

                    final Object value = data.remove("actionCondition.value");
                    return value;
                }

                @Override
                public String getDetails(Map<String, ?> config) {
                    final Object filter =
                            config.containsKey("filter")
                            ? config.get("filter")
                            : Boolean.TRUE;

                    return super.getDetails(config) +
                            "[condition=\"" + config.get("condition") +
                            "\", action=\""+ config.get("name")+
                            "\", arguments=\""+ config.get("arguments") +
                            "\", filter="+ filter +"]";
                }
            };
    //
    //  routines called from device functions
    //

    private static Object[] getActionArgs(DeviceAnalog deviceAnalog,
                                          Map<String, ?> configuration) {

        // This list comes from handling the "action" parameter in
        // com.oracle.iot.client.impl.device.DevicePolicyManager.devicePolicyFromJSON()
        List<Object> arguments = (List<Object>) configuration.get("arguments");
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }

        Object[] actionArgs = new Object[arguments.size()];
        for (int n = 0, nMax = arguments.size(); n < nMax; n++) {

            final DeviceModelImpl deviceModel = (DeviceModelImpl)deviceAnalog.getDeviceModel();
            final Map<String, DeviceModelAction> actionMap = deviceModel.getDeviceModelActions();

            if (actionMap == null || actionMap.isEmpty()) {
                // TODO: this could get annoying
                getLogger().log(Level.WARNING, "no actions in device model '" + deviceModel.getURN() + "'");
                actionArgs[n] = null;
                continue;
            }

            final String actionName = (String)configuration.get("name");
            final DeviceModelAction deviceModelAction = actionMap.get(actionName);

            if (deviceModelAction == null) {
                // TODO: this could also get annoying
                getLogger().log(Level.WARNING, "no action named '" + actionName
                        + "' in device model '" + deviceModel.getURN() + "'");
                actionArgs[n] = null;
                continue;
            }

            final DeviceModelAttribute.Type type = deviceModelAction.getArgType();

            try {
                actionArgs[n] = convertArg(deviceAnalog, type, arguments.get(n));
            } catch (IllegalArgumentException e) {
                getLogger().log(Level.WARNING, "bad argument to '" + actionName
                        + "' in '" + deviceModel.getURN() + "' :" + e.getMessage());
                // maybe this was purposeful - let application handle
                actionArgs[n] = arguments.get(n);
            }
        }

        return actionArgs;

    }

    private static Object convertArg(DeviceAnalog deviceAnalog,
                                     DeviceModelAttribute.Type type,
                                     Object arg) {

        if (arg == null) {
            return null;
        }

        // arg in an ACTION is a formula
        // ACTION type is represented as JSON of form
        // {
        //     "name": "name-of-the-action",
        //     "arguments": [
        //         "formula:argument-1",
        //         "formula:argument-2"
        //     ]
        // }
        // field in an ALERT is also a formula
        // ALERT type is represented as JSON of form
        // {
        //     "urn": "URN-of-the-alert",
        //     "fields": {
        //         "name-of-the-field-1": "<formula>",
        //         "name-of-the-field-2": "<formula>"
        //         ...
        //      },
        //     "severity": "severity level"
        // }
        final Object conversion = convertFormula(deviceAnalog, arg.toString());
        switch (type) {
            case STRING:
            case URI:
                return String.valueOf(conversion);
            case BOOLEAN:
                if (conversion instanceof Boolean) {
                    return conversion;
                } else if (conversion instanceof String) {
                    return Boolean.valueOf((String) conversion);
                } else if (conversion instanceof Number) {
                    return ((Number)conversion).doubleValue() != 0;
                } else {
                    throw new IllegalArgumentException("cannot convert " + arg + " to " + type.name());
                }
            case DATETIME:
                if (conversion instanceof Number) {
                    return new Date(((Number)conversion).longValue());
                } else {
                    throw new IllegalArgumentException("cannot convert " + arg + " to " + type.name());
                }
            case INTEGER:
                if (conversion instanceof Number) {
                    return ((Number)conversion).intValue();
                } else {
                    throw new IllegalArgumentException("cannot convert " + arg + " to " + type.name());
                }
            case NUMBER:
                if (conversion instanceof Number) {
                    return conversion;
                } else {
                    throw new IllegalArgumentException("cannot convert " + arg + " to " + type.name());
                }
            default:
                // No conversion
                getLogger().log(Level.WARNING, "unknown type : " + type.name());
                return arg;
        }
    }

    private static Object convertFormula(DeviceAnalog deviceAnalog,
                                         String formula) {

        // If arg is a String, it should be a FORMULA
        try {
            final List<FormulaParser.Token> tokens = FormulaParser.tokenize(formula);
            final FormulaParser.Node node = FormulaParser.parseFormula(tokens, formula);
            return compute(node, deviceAnalog);
        } catch (IllegalArgumentException e) {
            getLogger().log(Level.WARNING, "field in formula not in device model: " + formula);
        }

        return Double.NaN;
    }

    // Called from alertCondition
    private static AlertMessage createAlert(DeviceAnalog deviceAnalog,
                                            Map<String, ?> configuration) {

        final DeviceModelImpl deviceModel = (DeviceModelImpl)deviceAnalog.getDeviceModel();

        final Map<String, DeviceModelFormat> deviceModelFormatMap =
                deviceModel.getDeviceModelFormats();

        if (deviceModelFormatMap == null) {
            throw new IllegalArgumentException(
                    deviceModel.getURN() + " does not contain alert formats"
            );
        }

        final String format = (String) configuration.get("urn");
        final DeviceModelFormat deviceModelFormat = deviceModelFormatMap.get(format);

        if (deviceModelFormat == null) {
            throw new IllegalArgumentException(
                    deviceModel.getURN() + " does not contain alert format '" + format + "'"
            );
        }

        final List<DeviceModelFormat.Field> fields = deviceModelFormat.getFields();

        AlertMessage.Severity alertSeverity;
        try {
            final String severityConfig = (String)configuration.get("severity");
            alertSeverity = severityConfig != null
                    ? AlertMessage.Severity.valueOf(severityConfig.toUpperCase(Locale.ROOT))
                    : AlertMessage.Severity.NORMAL;
        } catch (IllegalArgumentException e) {
            alertSeverity = AlertMessage.Severity.NORMAL;
        }

        final AlertMessage.Builder builder = new AlertMessage.Builder()
                .source(deviceAnalog.getEndpointId())
                .format(format)
                .description(deviceModelFormat.getName())
                .severity(alertSeverity);

        final Map<String,Object> fieldsFromPolicy =
                (Map<String,Object>) configuration.get("fields");

        for (DeviceModelFormat.Field field : fields) {

            final Object policyValue = fieldsFromPolicy.get(field.getName());
            if (policyValue == null) {
                continue;
            }

            try {
                Object value = convertArg(deviceAnalog, field.getType(), policyValue);
                addDataItem(builder, field, value);
            } catch (IllegalArgumentException e) {
                getLogger().log(Level.WARNING, "bad value for '" + field.getName()
                        + "' in '" + deviceModel.getURN() + "' :" + e.getMessage());
            }

        }

        return builder.build();
    }

    private static void addDataItem(AlertMessage.Builder builder,
                                    DeviceModelFormat.Field field,
                                    Object value) {

        switch (field.getType()) {
            case INTEGER:
            case NUMBER:
                if (value instanceof Number) {
                    if (field.getType() == DeviceModelAttribute.Type.INTEGER) {
                        builder.dataItem(field.getName(), ((Number) value).intValue());
                    } else {
                        builder.dataItem(field.getName(), ((Number) value).doubleValue());
                    }
                } else {
                    throw new IllegalArgumentException("value of attribute '"
                            + field.getName() + "' is not a " + field.getType());
                }
                break;
            case URI:
            case STRING:
                if (value instanceof String) {
                    builder.dataItem(field.getName(), (String) value);
                } else {
                    throw new IllegalArgumentException("value of attribute '"
                            + field.getName() + "' is not a " + field.getType());
                }
                break;
            default:
                throw new IllegalArgumentException("the type of attribute '"
                            + field.getName() + "' is not a known type (" +
                            field.getType() + ")");
            case BOOLEAN:
                if (value instanceof Boolean) {
                    builder.dataItem(field.getName(), (Boolean) value);
                } else {
                    throw new IllegalArgumentException("value of attribute '"
                            + field.getName() + "' is not a " + field.getType());
                }
                break;
            case DATETIME:
                if (value instanceof Number) {
                    builder.dataItem(field.getName(), ((Number) value).longValue());
                } else if (value instanceof Date) {
                    builder.dataItem(field.getName(), ((Date) value).getTime());
                } else {
                    throw new IllegalArgumentException("value of attribute '"
                            + field.getName() + "' is not a " + field.getType());
                }
                break;
        }
    }


    static Object compute(FormulaParser.Node node, DeviceAnalog deviceAnalog) {
        return Formula.compute(node, new ValueProviderImpl(deviceAnalog));
    }

    // greatest common factor, e.g., gcd(90,60) = 30
    static long gcd(long x, long y){
        return (y == 0) ? x : gcd(y, x % y);
    }

    private final static List<DeviceFunction> DEVICE_FUNCTIONS;
    private final static Map<String, DeviceFunction> POLICY_MAP;

    static {

        final List<DeviceFunction> functions =
                new ArrayList<DeviceFunction>();

        Collections.addAll(functions,
                FILTER_CONDITION,
                ELIMINATE_DUPLICATES,
                DETECT_DUPLICATES,
                SAMPLE,
                MAX,
                MIN,
                MEAN,
                STANDARD_DEVIATION,
                BATCH_BY_SIZE,
                BATCH_BY_TIME,
                BATCH_BY_COST,
                ALERT_CONDITION,
                COMPUTED_METRIC,
                PRIVACY_POLICY,
                ACTION_CONDITION
        );
        DEVICE_FUNCTIONS = Collections.unmodifiableList(functions);

        final Map<String, DeviceFunction> policyMap =
                new HashMap<>(DEVICE_FUNCTIONS.size());

        for (DeviceFunction deviceFunction : DEVICE_FUNCTIONS) {
            policyMap.put(deviceFunction.getId(), deviceFunction);
        }
        POLICY_MAP = Collections.unmodifiableMap(policyMap);
    }

    // A bucket used for slide (see comments in the 'mean' function)
    // <T> Is the type of value
    private static class Bucket<T> {
        T value;
        int terms;
        Bucket(T initialValue) {
            this.value = initialValue;
            this.terms = 0;
        }

        @Override
        public String toString() {
            return "{\"value\" : " + value + ", \"terms\" : " + terms + "}";
        }
    }

    private DeviceFunction(String id) {
        this.id = id;
    }

    private final String id;

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() {
        return LOGGER;
    }
}

class ValueProviderImpl implements ValueProvider {
    DeviceAnalog deviceAnalog;
    
    ValueProviderImpl(DeviceAnalog da) {
        deviceAnalog = da;
    }

    @Override
    public Object getCurrentValue(String key) {
        if (deviceAnalog == null) {
            return null;
        }

        return deviceAnalog.getAttributeValue(key);
    }

    @Override
    public Object getInProcessValue(String key) {
        if (deviceAnalog == null) {
            return null;
        }

        return DeviceFunction.getInProcessValue(deviceAnalog.getEndpointId(),
            deviceAnalog.getDeviceModel().getURN(), key);
    }
}
