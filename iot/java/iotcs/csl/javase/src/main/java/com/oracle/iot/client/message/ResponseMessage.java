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
import org.json.JSONException;
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
 * ResponseMessage extends Message class. It stores HTTP headers, status code
 * and URL and body. The body is in byte array. Default encoding for string body is UTF-8.
 * This class is immutable.
 */
public final class ResponseMessage extends Message {

    /**
     * Builder extends {@link Message.MessageBuilder} class. {@link ResponseMessage} class is immutable.
     * A builder is required when creating {@link ResponseMessage}. {@link ResponseMessage}
     * uses Builder design pattern.
     */
    public final static class Builder extends MessageBuilder<Builder> {

        /** HTTP response message header. Header is a name and list of values pair. */
        private final Map<String, List<String>> headers = new HashMap<String, List<String>>();
        /** Status code of the HTTP response message */
        private StatusCode statusCode;
        /** URL of the HTTP response message */
        private String url;
        /** ID of the request. Can be used for pairing request-response */
        private String requestId;
        /** Body of the HTTP response message */
        private byte[] body;

        public Builder() {
        }

        public Builder(RequestMessage requestMessage) {
            this.source = requestMessage.destination;
            this.destination = requestMessage.source;
            this.requestId = requestMessage.id;
        }

        /**
         * Add message header. The name cannot be {@code null}. The values can be {@code null}. The List of values
         * cannot contain {@code null}. Name and values must contain ASCII-printable characters only. Note that
         * the name of the header is always converted to lower case.
         *
         * @param name header name
         * @param values header values
         * @return Builder with updated header field.
         * @throws NullPointerException when the header name is {@code null} or if values contains {@code null} string.
         * @throws IllegalArgumentException when name or values contain non-ASCII-printable characters.
         */
        public final Builder header(String name, List<String> values) {
            Utils.checkNullValueThrowsNPE(name, "ResponseMessage: Header name");
            Utils.checkNullValuesThrowsNPE(values, "ResponseMessage: Header values");
            if (!Utils.isHttpHeaderAsciiPrintable(name, values)){
                throw new IllegalArgumentException("ResponseMessage: Header contains non-ASCII printable characters");
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
         * A convenience method for setting the "Content-Type" header to the specified value.
         *
         * @param contentType The content type to use, cannot be {@code null}. This is used literally.
         * @return Builder with updated contentType field.
         * @throws NullPointerException when contentType is null.
         */
        public final Builder contentType(String contentType) {
            Utils.checkNullValueThrowsNPE(contentType, "ResponseMessage: Content type");
            header("Content-Type", Arrays.asList(contentType));
            return self();
        }

        /**
         * Set message URL. The URL is not checked for validity.
         * 
         * @param url URL of the HTTP request.
         * @return Builder with updated URL field.
         */
        public final Builder url(String url) {
            this.url = url;
            return self();
        }

        /**
         * Set message body.
         * 
         * @param body Body of the HTTP response message, should not be {@code null}.
         * @return Builder with updated body field.
         */
        public final Builder body(byte[] body) {
            this.body = Arrays.copyOf(body, body.length);
            return self();
        }

        /**
         * Set message body using a {@link String}. The encoding for the body is UTF-8.
         *
         * @param body body of the HTTP message, should not be {@code null}.
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
         * Set http response status code.
         * 
         * @param statusCode Status code for HTTP response.
         * @return Builder with updated statusCode field.
         */
        public final Builder statusCode(StatusCode statusCode) {
            this.statusCode = statusCode;
            return self();
        }

        /**
         * Sets the Id of {@link RequestMessage} for that this response was created.
         *
         * @param requestId Http request message id.
         * @return Builder with updated requestId field.
         */
        public final Builder requestId(String requestId) {
            this.requestId = requestId;
            return self();
        }

        @Override
        public final Builder fromJson(JSONObject jsonObject) {
            super.fromJson(jsonObject);

            JSONObject payload = jsonObject.optJSONObject("payload");
            Utils.checkNullValueAndThrowMPE(payload,
                "response.message.payload.null");

            StatusCode statusCode;
            try {
                statusCode = StatusCode.valueOf(payload.getInt("statusCode"));
            } catch (JSONException e) {
                throw new MessageParsingException(e);
            } catch (NullPointerException e) {
                throw new MessageParsingException(
                    "response.message.status.null", e);
            } catch (ClassCastException e) {
                throw new MessageParsingException(
                    "response.message.status.notNumber", e);
            } catch (IllegalArgumentException e) {
                throw new MessageParsingException(
                    "response.message.status.wrong", e);
            }

            String url = payload.optString("url", null);

            String requestId = payload.optString("requestId", null);
            Utils.checkNullOrEmptyStringThrowMPE(requestId,
                "response.message.requestId.null");

            byte[] body;
            try {
                body =
                    Base64.getDecoder().decode(payload.optString("body", null));
            } catch (IllegalArgumentException e) {
                throw new MessageParsingException(
                    "response.message.body.wrong");
            } catch (NullPointerException e) {
                throw new MessageParsingException("response.message.body.null");
            }

            this.statusCode(statusCode);
            this.url(url);
            this.body(body);
            this.requestId(requestId);

            JSONObject headers = payload.optJSONObject("headers");
            if (headers != null) {
                List<String> headerList = new ArrayList<String>();
               
                Iterator<String> keys = headers.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object jsonValue = headers.opt(key);
                    if (!(jsonValue instanceof JSONArray)) {
                        continue;
                    }

                    JSONArray headerValues = (JSONArray)jsonValue;
                    for (int i = 0, size = headerValues.length();
                            i < size; i++) {
                        String headerValue = headerValues.optString(i, null);
                        headerList.add(headerValue);
                    }

                    this.header(key, headerList);
                    headerList.clear();
                }
            }

            return self();
        }

        /**
         * Returns current instance of {@link ResponseMessage.Builder}.
         * @return Instance of {@link ResponseMessage.Builder}
         */
        @Override
        protected final Builder self() {
            return this;
        }

        /**
         * Creates new instance of {@link ResponseMessage} using values from {@link ResponseMessage.Builder}.
         * @return Instance of {@link ResponseMessage}
         */
        @Override
        public ResponseMessage build() {
            return new ResponseMessage(this);
        }

    }

    /** HTTP response message header. Header is a name and list of values pair. */
    private final Map<String, List<String>> headers;
    /** Status code of the HTTP response message */
    private final StatusCode statusCode;
    /** URL of the HTTP response message */
    private final String url;
    /** Body of the HTTP response message */
    private final byte[] body;
    /** Id of the request message. Can be used for pairing request-response */
    private final String requestId;

    /**
     * {@link ResponseMessage} constructor takes {@link ResponseMessage.Builder} and set values
     * to each field. If the value is {@code null}, set a default value.
     * 
     * @param builder generic type of message builder
     */
    private ResponseMessage(Builder builder) {
        super(builder);
        if (builder.requestId != null) {
            this.requestId = builder.requestId;
        } else {
            this.requestId = null;
        }
        if (builder.headers == null) {
            // unreachable code, see initialization of params in Builder
            this.headers = Collections.emptyMap();
        } else {
            this.headers = Collections.unmodifiableMap(new HashMap<String,List<String>>(builder.headers));
        }
        if (builder.url == null) {
            this.url = "";
        } else {
            this.url = builder.url;
        }
        if (builder.body == null) {
            this.body = new byte[0];
        } else {
            this.body = Arrays.copyOf(builder.body, builder.body.length);
        }
        if (builder.statusCode == null) {
            this.statusCode = StatusCode.OK;
        } else {
            this.statusCode = builder.statusCode;
        }
    }

    /**
     * Get Http response message headers.
     * 
     * @return {@link Map} of pair {@link String} and {@link List} of {@link String} representing headers, never {@code null}
     */
    public final Map<String, List<String>> getHeaders() {
        return Collections.unmodifiableMap(this.headers);
    }

    /**
     * Get Http response message header values for given header name.
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
     * Get response message header value at given index.
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
     * Get first value from response message header.
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
     * Get request message URL.
     * 
     * @return URL, never {@code null}
     */
    public final String getURL() {
        return this.url;
    }

    /**
     * Get HTTP response message body. If message body contains string value, default encoding is UTF-8.
     * 
     * @return body, never {@code null}
     */
    public final byte[] getBody() {
        return Arrays.copyOf(body, body.length);
    }

    /**
     * Get HTTP response message body in {@link String}. It fromString and return the inner representation of message
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
     * Get Http response message status code.
     * 
     * @return statusCode, never {@code null}
     */
    public final StatusCode getStatusCode() {
        return this.statusCode;
    }

    /**
     * Get ID of {@link RequestMessage}. {@link RequestMessage} Id can be used for pairing request-response.
     * @return Request id, never {@code null}
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * Get message type.
     *
     * @return type, never {@code null}.
     */
    @Override
    public Type getType() {
        return Type.RESPONSE;
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
     * Exports response message to {@link JSONObject}.
     * 
     * @return response message in JSON format
     */
    @Override
    public final JSONObject toJson() {
        return Utils.bodyToJson(this, null, Type.RESPONSE, this.statusCode,
            null, this.url, this.requestId, this.headers, this.body);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ResponseMessage that = (ResponseMessage) o;

        if (!Arrays.equals(body, that.body)) return false;
        if (!headers.equals(that.headers)) return false;
        if (statusCode != that.statusCode) return false;
        if (!url.equals(that.url)) return false;
        return requestId.equals(that.requestId);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + headers.hashCode();
        result = 31 * result + statusCode.hashCode();
        result = 31 * result + url.hashCode();
        result = 31 * result + requestId.hashCode();
        result = 31 * result + Utils.hashCodeByteArray(body);
        return result;
    }
}
