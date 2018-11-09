/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.message;

import com.oracle.iot.client.impl.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * RequestMessage extends Message class. It stores HTTP headers, parameters,
 * method, URL and body. The body is in byte array. Default encoding for string body is UTF-8.
 * This class is immutable.
 */
public final class RequestMessage extends Message {

    /**
     * Builder extends {@link Message.MessageBuilder} class. {@link RequestMessage} class is immutable.
     * A builder is required when creating {@link RequestMessage}. {@link RequestMessage} uses Builder
     * design pattern.
     */
    public final static class Builder extends MessageBuilder<Builder> {

        /** HTTP request message header. Header is pair of name and list of values. */
        private final Map<String, List<String>> headers = new HashMap<String, List<String>>();
        /** HTTP request message parameter */
        private final Map<String, String> params = new HashMap<String, String>();
        /** Method of the HTTP request message */
        private String method;
        /** URL of the HTTP request message */
        private String url;
        /** Body of the HTTP request message */
        private byte[] body;

        public Builder() {

        }

        /**
         * Add Http message header. The name cannot be {@code null}. The values can be {@code null}. The {@link List} of values
         * cannot contain {@code null}s. Name and values must contain ASCII-printable characters only. Note that
         * the name of the header is always converted to lower case.
         * 
         * @param name Name of the http header.
         * @param values Http header values.
         * @return Builder with updated header field.
         * @throws NullPointerException when the header name is {@code null} or if values contains {@code null} string.
         * @throws IllegalArgumentException when name or values contain non-ASCII-printable characters.
         */
        public final Builder header(String name, List<String> values) {
            Utils.checkNullValueThrowsNPE(name, "RequestMessage: Header name");
            Utils.checkNullValuesThrowsNPE(values, "RequestMessage: Header values");
            if (!Utils.isHttpHeaderAsciiPrintable(name, values)){
                throw new IllegalArgumentException("RequestMessage: Header contains non-ASCII printable characters!");
            }

            if (values == null) {
                values = new ArrayList<String>();
            }
            String lowerName = name.toLowerCase(Locale.ROOT);
            if (headers.containsKey(lowerName)) {
                List<String> newValues = new ArrayList<String>(this.headers.get(lowerName));
                newValues.addAll(values);
                this.headers.put(lowerName, Collections.unmodifiableList(new ArrayList<String>(newValues)));

            } else {
                this.headers.put(lowerName, Collections.unmodifiableList(new ArrayList<String>(values)));
            }
            return self();
        }

        /**
         * Add Http parameter. Name of the param cannot be {@code null}. Parameter value can be {@code null}.
         * 
         * @param name Http parameter name.
         * @param value Http parameter value.
         * @return Builder with updated param field.
         * @throws NullPointerException when the name is {@code null}.
         */
        public final Builder param(String name, String value) {
            Utils.checkNullValueThrowsNPE(name, "RequestMessage: Param name");
            this.params.put(name, value);
            return self();
        }

        /**
         * Set message URL. The URL is not checked for validity.
         *
         * @param url URL of the HTTP request.
         * @return Builder with updated url field.
         */
        public final Builder url(String url) {
            this.url = url;
            return self();
        }

        /**
         * Set message body.
         * 
         * @param body Body of the HTTP message, should not be {@code null}.
         * @return Builder with updated body field.
         */
        public final Builder body(byte[] body) {
            this.body = Arrays.copyOf(body, body.length);
            return self();
        }

        /**
         * Set message body using a {@link String}. The encoding for the body is UTF-8.
         *
         * @param body Body of the HTTP message, should not be {@code null}.
         * @return Builder with updated body field.
         */
        public final Builder body(String body) {
            try {
                return body(body.getBytes("UTF-8"));
            } catch (java.io.UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Set Http method. Tha name of the HTTP method is always converted to lower case.
         * 
         * @param method Method of the HTTP.
         * @return Builder with updated method field.
         */
        public final Builder method(String method) {
            this.method = (method != null) ? method.toLowerCase(Locale.ROOT) : null;
            return self();
        }

        @Override
        public final Builder fromJson(JSONObject jsonObject) {
            super.fromJson(jsonObject);
            if (this.clientId == null || (this.clientId.length() == 0)) {
                throw new MessageParsingException(
                    "clientId should be set for RequestMessage");
            }

            JSONObject payload = jsonObject.optJSONObject("payload");
            Utils.checkNullValueAndThrowMPE(payload,
                "request.message.payload.null");

            String method = payload.optString("method", null);
            Utils.checkNullOrEmptyStringThrowMPE(method,
                "request.message.method.null");

            String url = payload.optString("url", null);
            Utils.checkNullOrEmptyStringThrowMPE(url,
                "request.message.url.null");

            byte[] body;
            try {
                body =
                    Base64.getDecoder().decode(payload.optString("body", null));
            } catch (IllegalArgumentException e) {
                throw new MessageParsingException("request.message.body.wrong");
            } catch (NullPointerException e) {
                throw new MessageParsingException("request.message.body.null");
            }

            this.method(method);
            this.url(url);
            this.body(body);

            JSONObject headers = payload.optJSONObject("headers");
            if (headers != null) {
                final List<String> headerList = new ArrayList<String>();

                Iterator<String> keys = headers.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object jsonValue = headers.opt(key);
                    if (!(jsonValue instanceof JSONArray)) {
                        continue;
                    }

                    JSONArray headerValues = (JSONArray)jsonValue;
                    int size = headerValues.length();
                    for (int i = 0; i < size; i++) {
                        String headerValue = headerValues.optString(i, null);
                        headerList.add(headerValue);
                    }

                    this.header(key, headerList);
                    headerList.clear();
                }
            }

            JSONObject params = payload.optJSONObject("params");
            if (params != null) {
                Iterator<String> keys = params.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String paramValue = params.optString(key, null);
                    this.param(key, paramValue);
                }
            }

            return self();
        }

        /**
         * Returns current instance of {@link RequestMessage.Builder}.
         * @return Instance of {@link RequestMessage.Builder}
         */
        @Override
        protected final Builder self() {
            return this;
        }

        /**
         * Creates new instance of {@link RequestMessage} using values from {@link RequestMessage.Builder}.
         * @return Instance of {@link RequestMessage}
         */
        @Override
        public RequestMessage build() {
            return new RequestMessage(this);
        }

    }

    /** HTTP request message header. Header is pair of name and list of values. */
    private final Map<String, List<String>> headers;

    /** HTTP request message parameter */
    private final Map<String, String> params;

    /** Method of the HTTP request message */
    private final String method;

    /** URL of the HTTP request message */
    private final String url;

    /** Body of the HTTP request message */
    private final byte[] body;

    /**
     * {@link RequestMessage} constructor takes {@link RequestMessage.Builder} and set values to
     * each field. If the value is {@code null}, set a default value. Url cannot be {@code null} or empty.
     * 
     * @param builder generic type of message builder
     * @throws IllegalArgumentException when the url is not specified
     */
    private RequestMessage(Builder builder) {
        super(builder);
        if (builder.headers == null) {
            // unreachable code, see initialization of headers in Builder
            this.headers = Collections.emptyMap();
        } else {
            this.headers = Collections.unmodifiableMap(new HashMap<String,List<String>>(builder.headers));
        }
        if (builder.params == null) {
            // unreachable code, see initialization of params in Builder
            this.params = Collections.emptyMap();
        } else {
            this.params = Collections.unmodifiableMap(new HashMap<String,String>(builder.params));
        }
        if (builder.url == null || builder.url.length() == 0) {
          throw new IllegalArgumentException("Requested url cannot be null or empty.");
        } else {
            this.url = builder.url;
        }
        if (builder.body == null) {
            this.body = new byte[0];
        } else {
            this.body = Arrays.copyOf(builder.body, builder.body.length);
        }
        if (builder.method == null) {
            this.method = "get";
        } else {
            this.method = builder.method;
        }
    }

    /**
     * Get Http request message headers.
     * @return {@link Map} of pair {@link String} and {@link List} of {@link String} representing headers, never {@code null}
     */
    public final Map<String, List<String>> getHeaders() {
        return Collections.unmodifiableMap(this.headers);
    }

    /**
     * Get Http request message header values for given header name.
     * 
     * @param name header name, should not be {@code null}.
     * @return {@link List} of header values, never {@code null}
     */
    public final List<String> getHeaderValues(String name) {
        if (name != null) {
            name = name.toLowerCase(Locale.ROOT);
            return Collections.unmodifiableList(this.headers.get(name));
        }
        return Collections.emptyList();
    }

    /**
     * Get request message header value at given index.
     * 
     * @param name header name, should not be {@code null}.
     * @param index index in {@link List} of header values.
     * @return header value, may return {@code null} if header name does not exist or index is out of range.
     */
    public final String getHeaderValue(String name, int index) {
        if (name != null) {
            name = name.toLowerCase(Locale.ROOT);
        }
        
        List<String> values = this.headers.get(name);
        
        if (values != null && values.size() > index) {
            return this.headers.get(name).get(index);
        } else {
            return null;
        }
    }
    
    /**
     * Get first value from request message header.
     *
     * @param name header name, should not be {@code null}.
     * @return first header value, may return {@code null} if the header name does not exist or no values were set for given header name.
     */
    public final String getHeaderValue(String name) {
        if (name != null) {
            name = name.toLowerCase(Locale.ROOT);
        }
        
        List<String> values = this.headers.get(name);
        
        if (values != null && values.size() > 0)
            return values.get(0);
        
        return null;
    }

    /**
     * Get Http request message parameters.
     * 
     * @return {@link Map} of pair {@link String} and {@link String} representing parameters, never {@code null}
     */
    public final Map<String, String> getParams() {
        return this.params;
    }

    /**
     * Get parameter value for given parameter name.
     * 
     * @param name parameter name, should not be {@code null}.
     * @return parameter value, may return {@code null} if the parameter name does not exist.
     */
    public final String getParam(String name) {
        return this.params.get(name);
    }

    /**
     * Get request message URL.
     * 
     * @return URL, never {@code null}
     */
    public final String getURL() {
        return this.url;
    }

    /**
     * Get HTTP request message body. If message body contains string value, default encoding is UTF-8.
     * 
     * @return body, never {@code null}
     */
    public final byte[] getBody() {
        return Arrays.copyOf(body, body.length);
    }

    /**
     * Get HTTP request message body in {@link String}. It fromString and return the inner representation of message
     * body to {@link String}.
     *
     * @return Http message body as {@link String}, never {@code null}
     */
    public final String getBodyString(){
        try {
            return new String(getBody(), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get request message method. Note that the returned {@link java.lang.String} is always in lower case.
     * 
     * @return Http method, never {@code null}
     */
    public final String getMethod() {
        if (this.method != null) {
            return this.method.toLowerCase(Locale.ROOT);
        }
        // unreachable code, see constructor
        return null;
    }

    /**
     * Get message type.
     *
     * @return type, never {@code null}.
     */
    @Override
    public Type getType() {
        return Type.REQUEST;
    }

    /**
     * Exports data from {@link RequestMessage} to {@link String} using JSON interpretation of the message.
     *
     * @return JSON interpretation of the message as {@link String}.
     */
    @Override
    public final String toString() {
        return toJson().toString();
    }

    /**
     * Exports Http request message to {@link JSONObject} format.
     * 
     * @return request message in JSON format
     */
    @Override
    public final JSONObject toJson() {
        return Utils.bodyToJson(this, params, (Type)null, null, this.method,
             this.url, null, this.headers, this.body);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        RequestMessage that = (RequestMessage) o;

        if (!Arrays.equals(body, that.body)) return false;
        if (!headers.equals(that.headers)) return false;
        if (!method.equals(that.method)) return false;
        if (!params.equals(that.params)) return false;
        return url.equals(that.url);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + headers.hashCode();
        result = 31 * result + params.hashCode();
        result = 31 * result + method.hashCode();
        result = 31 * result + url.hashCode();
        result = 31 * result + Utils.hashCodeByteArray(body);
        return result;
    }
}
