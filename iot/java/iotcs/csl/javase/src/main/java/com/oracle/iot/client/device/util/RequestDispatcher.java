/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.device.util;

import com.oracle.iot.client.message.Message;
import com.oracle.iot.client.message.RequestMessage;
import com.oracle.iot.client.message.ResponseMessage;
import com.oracle.iot.client.message.StatusCode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * RequestDispatcher is a utility that allows the caller to register handlers
 * for requests coming from the IoT server. The RequestDispatcher handles all
 * of the logic of matching the resource to a handler and invoking the handler.
 */
public final class RequestDispatcher {

    //
    // This is the default behavior for a resource that hasn't registered a handler.
    //
    static final RequestHandler DEFAULT_HANDLER =
            new RequestHandler() {

                public ResponseMessage handleRequest(RequestMessage request) throws Exception {

                    try {
                        ResponseMessage.Builder builder =
                                new ResponseMessage.Builder(request)
                                        .statusCode(StatusCode.NOT_FOUND);

                        return builder.build();
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw e;
                    }
                }
            };

    /**
     * The RequestDispatcher is a singleton.
     * @return The single instance of RequestDispatcher
     */
    public static RequestDispatcher getInstance() {
        return INSTANCE;
    }

    private static final RequestDispatcher INSTANCE = new RequestDispatcher();

    /**
     * Registered RequestHandlers
     */
    private final List<RequestHandlerRegistration> requestHandlerRegistrations;

    /**
     * The current default handler
     */
    private RequestHandler defaultHandler;

    /**
     * Only singleton allowed
     */
    private RequestDispatcher() {
        this.requestHandlerRegistrations =
                new ArrayList<RequestHandlerRegistration>();
        defaultHandler = DEFAULT_HANDLER;
    }

    /**
     * Register a handler for the given endpoint and path. If endpoint is null,
     * empty, or a wildcard ("*"), then the handler is registered as being used
     * for all endpoints. If path is null, empty, or a wildcard ("*")
     * then the handler is registered as the handler for any requests that do
     * not have a more specific handler.
     * The {@code handler} parameter may not be null.
     * <p>
     * Registering a handler with
     * {@code registerRequestHandler("*", "*", handler)} will register a default,
     * catch-all handler. Note that this utility class already provides a
     * reasonable default handler that can be replaced with this technique.
     * The implementation ensures there is always a default handler.
     * </p>
     *
     * @param endpointId the endpoint id that has the given the resource path,
     *                   or a wildcard ("*") for any endpoint.
     * @param path       the resource path, or a wildcard ("*") for any resource
     * @param handler    the handler to invoke for requests to this endpoint and path
     * @throws IllegalArgumentException if handler is null
     */
    public void registerRequestHandler(String endpointId, String path, RequestHandler handler) {

        if (handler == null) {
            throw new IllegalArgumentException("handler may not be null");
        }

        synchronized (requestHandlerRegistrations) {

            final String normalizedEndpointId =
                    (endpointId == null) || (endpointId.length() == 0) ? "*" : endpointId;

            final String normalizedPath =
                    (path == null) || (path.length() == 0) ? "*" : path;

            // special case for default handler
            if ("*".equals(normalizedEndpointId) && "*".equals(normalizedPath)) {
                defaultHandler = handler;
                return;
            }

            // RequestHandlerRegistration is immutable, so make a new one
            final RequestHandlerRegistration registration =
                    new RequestHandlerRegistration(normalizedEndpointId, normalizedPath, handler);

            boolean found = false;
            for (int n = 0, nMax = requestHandlerRegistrations.size(); n < nMax; n++) {

                final RequestHandlerRegistration other = requestHandlerRegistrations.get(n);
                // assignment in the conditional is intentional
                if (found = registration.equals(other)) {
                    // replace the existing registration
                    requestHandlerRegistrations.set(n, registration);
                    break;
                }

            }

            if (!found) {
                requestHandlerRegistrations.add(registration);
                Collections.sort(requestHandlerRegistrations);
            }
        }
    }

    /**
     * Un-register the handler for the given endpoint and path. Either or both
     * of the parameters may be a wildcard ("*"). Null or empty parameters are
     * interpreted as a wildcard.
     * Note that the wildcard is not used to match any endpoint or path, but
     * is used to match a handler that was registered with the wildcard endpoint
     * and/or path. Thus, a handler registered by calling
     * {@code registerRequestHandler("0-AB", "resource", handler)}
     * will not be un-registered by calling
     * {@code unregisterRequestHandler("*", "resource")}.
     * Nor will calling {@code unregisterRequestHandler("0-AB", "*")} result
     * in un-registering all handlers for endpoint "0-AB". In short, the
     * arguments to {@code registerRequestHandler} should match the
     * arguments to {@code unregisterRequestHandler}.
     * <p>
     * A default handler can be registered by calling
     * {@code registerRequestHandler("*", "*", handler)}. Calling
     * {@code unregisterRequestHandler("*", "*")} will restore the
     * default handler. The implementation ensures there is always a
     * default handler.
     * </p>
     *
     * @param endpointId the endpoint id that has the given the resource path,
     *                   or a wildcard ("*").
     * @param path       the resource path, or a wildcard ("*")
     */
    public void unregisterRequestHandler(String endpointId, String path) {

        synchronized (requestHandlerRegistrations) {


            final String normalizedEndpointId =
                    (endpointId == null) || (endpointId.length() == 0) ? "*" : endpointId;

            final String normalizedPath =
                    (path == null) || (path.length() == 0) ? "*" : path;

            // special case for default handler
            if ("*".equals(normalizedEndpointId) && "*".equals(normalizedPath)) {
                defaultHandler = DEFAULT_HANDLER;
                return;
            }

            for (int n = 0, nMax = requestHandlerRegistrations.size(); n < nMax; n++) {

                final RequestHandlerRegistration other = requestHandlerRegistrations.get(n);

                final String otherEndpointId = other.endpointId;
                final String otherPath = other.path;
                if (normalizedEndpointId.equals(otherEndpointId) &&
                        normalizedPath.equals(other.path)) {
                    requestHandlerRegistrations.remove(n);
                    return;
                }
            }
        }
    }

    /**
     * Un-register the given handler for all endpoints and paths for which the
     * handler may be registered. The {@code handler} parameter may not be null.
     *
     * @param handler the handler to un-register
     */
    public void unregisterRequestHandler(RequestHandler handler) {

        synchronized (requestHandlerRegistrations) {

            // special case for default handler
            if (handler == defaultHandler && defaultHandler != DEFAULT_HANDLER) {
                defaultHandler = DEFAULT_HANDLER;
                return;
            }

            for (int n = requestHandlerRegistrations.size() - 1; 0 <= n; --n) {
                final RequestHandlerRegistration other = requestHandlerRegistrations.get(n);
                if (other.handler == handler) {
                    requestHandlerRegistrations.remove(n);
                }
            }
        }
    }

    /**
     * Match the request to a registered handler and invoke the handler.
     *
     * @param requestMessage The request message to be dispatched
     * @return The return value of the invoked handler. If the invoked
     * handler throws an exception or returns null, a reasonable response
     * will be returned by this utility. This method will not return null.
     * @throws IllegalArgumentException if requestMessage is null
     */
    public ResponseMessage dispatch(RequestMessage requestMessage) {

        if (requestMessage == null) {
            throw new IllegalArgumentException("requestMessage may not be null");
        }

        final String endpointId = requestMessage.getDestination();
        final String path = requestMessage.getURL();
        final RequestHandler requestHandler = getRequestHandler(endpointId, path);
        try {
            ResponseMessage responseMessage = requestHandler.handleRequest(requestMessage);
            if (responseMessage == null) {
                if (getLogger().isLoggable(Level.SEVERE)) {
                    getLogger().log(Level.SEVERE, requestHandler.toString() + " returned null");
                } 
                ResponseMessage.Builder builder =
                    copyResponseMessage((new ResponseMessage.Builder(requestMessage)).build())
                    .statusCode(StatusCode.INTERNAL_SERVER_ERROR);
                return builder.build();
            }
            return responseMessage;

        } catch (Exception e) {
            if (getLogger().isLoggable(Level.SEVERE)) {
                getLogger().log(Level.SEVERE, requestHandler.toString() + " threw " + e.getMessage());
                e.printStackTrace();
            }
            ResponseMessage.Builder builder =
                    new ResponseMessage.Builder(requestMessage)
                            .source(requestMessage.getDestination())
                            .requestId(requestMessage.getId())
                            .statusCode(StatusCode.INTERNAL_SERVER_ERROR);
            if (e.getMessage() != null) {
                builder.body(e.getMessage());
            }
            return builder.build();
        }

    }
    
    private ResponseMessage.Builder copyResponseMessage(Message message) {
        ResponseMessage.Builder builder = new ResponseMessage.Builder();
        if (message instanceof ResponseMessage) {
            ResponseMessage responseMsg = (ResponseMessage) message;
            builder = copyMessage(builder, responseMsg);
            Map<String, List<String>> oldHeader = responseMsg.getHeaders();
            Set<Map.Entry<String,List<String>>> headerEntries = oldHeader.entrySet();
            for(Map.Entry<String,List<String>> entry : headerEntries) {
                builder.header(entry.getKey(), entry.getValue());
            }
            builder = builder.body(Arrays.copyOf(responseMsg.getBody(), responseMsg.getBody().length))
                .statusCode(responseMsg.getStatusCode())
                .url(responseMsg.getURL())
                .requestId(responseMsg.getRequestId());
        } else {
            throw new IllegalArgumentException("Can not copy a different type of message");
        }
        return builder;
    }

    private ResponseMessage.Builder copyMessage(ResponseMessage.Builder builder, Message message) {
        builder.id(message.getId());
        builder.clientId(message.getClientId());
        builder.source(message.getSource());
        builder.destination(message.getDestination());
        builder.priority(message.getPriority());
        builder.properties(message.getProperties());
        builder.eventTime(message.getEventTime().longValue());
        builder.reliability(message.getReliability());
        builder.sender(message.getSender());
        builder = copyMap(builder, message.getDiagnostics());
        builder.direction(message.getDirection());
        builder.receivedTime(message.getReceivedTime());
        builder.sentTime(message.getSentTime());
        return builder;
    }

    private ResponseMessage.Builder copyMap(ResponseMessage.Builder builder, Map<String,Object> map){
            if ( map == null )
                return builder;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                builder = builder.diagnostic(entry.getKey(), entry.getValue());
            }
            return builder;
    }

    /**
     * Lookup a RequestHandler for the given endpointId and path. The lookup
     * will return the most specific handler for the endpointId and path. If
     * there is not a specific match for endpointId, the lookup will match
     * try to match {@code ("*", path)}. Failing that, the lookup will return
     * a handler for {@code ("*", "*")}, or the default handler if no handler
     * for {@code ("*","*")} has been registered.
     * Will not return null.
     *
     * @param endpointId the endpoint id that has the given the resource path,
     *                   or a wildcard ("*").
     * @param path       the resource path, or a wildcard ("*")
     * @return The RequestHandler for the given endpointId and path.
     */
    public RequestHandler getRequestHandler(String endpointId, String path) {

        synchronized (requestHandlerRegistrations) {
            final String normalizedEndpointId =
                    endpointId == null || (endpointId.length() == 0) ? "*" : endpointId;

            final String normalizedPath =
                    (path == null) || (path.length() == 0) ? "*" : path;

            // special case for default handler
            if ("*".equals(normalizedEndpointId) && "*".equals(normalizedPath)) {
                return defaultHandler;
            }

            //
            // Note that this loop depends on requestHandlerRegistrations being
            // sorted such that longer paths come first in the list.
            //
            for (int n = 0, nMax = requestHandlerRegistrations.size(); n < nMax; n++) {

                final RequestHandlerRegistration registration
                        = requestHandlerRegistrations.get(n);

                final String otherEndpointId = registration.endpointId;

                if (normalizedEndpointId.equals(otherEndpointId)
                        || "*".equals(otherEndpointId)) {

                    final String otherPath = registration.path;

                    if ("*".equals(otherPath)) {
                        return registration.handler;
                    }

                    int diff = otherPath.length() - normalizedPath.length();
                    if (diff == 0) {
                        if (normalizedPath.equals(otherPath)) {
                            return registration.handler;
                        }

                    } else if (diff < 0) {
                        if (normalizedPath.startsWith(otherPath)) {
                            return registration.handler;
                        }
                    }
                    // else {
                    //     diff > 0, which means that registration.resource
                    //     is more specific than resource
                    // }
                }

            }

            return defaultHandler;
        }
    }

    static final class RequestHandlerRegistration implements Comparable<RequestHandlerRegistration> {
        final String endpointId;
        final String path;
        final RequestHandler handler;

        RequestHandlerRegistration(String endpointId, String path, RequestHandler handler) {
            this.endpointId = (endpointId == null) || (endpointId.length() == 0) ? "*" : endpointId;
            this.path = (path == null) || (path.length() == 0) ? "*" : path;
            this.handler = handler;
        }

        // Note that other code depends on RequestHandlerRegistrations being
        // sorted such that longer contexts come first.
        public int compareTo(RequestHandlerRegistration other) {

            int c = other.endpointId.compareTo(this.endpointId);
            if (c != 0) return c;

            // same endpoint
            int diff = other.path.length() - this.path.length();
            if (diff != 0) return diff;

            // this.path and other.path are the same length
            return other.path.compareTo(this.path);

        }

        @Override
        public boolean equals(Object obj) {

            // equals and compareTo have to agree.
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final RequestHandlerRegistration other = (RequestHandlerRegistration) obj;

            if (!this.endpointId.equals(other.endpointId)) return false;
            return this.path.equals(other.path);

        }

        @Override
        public int hashCode() {
            return endpointId.hashCode() + path.hashCode();
        }

        @Override
        public String toString() {
            return "RequestHandlerRegistration{" + endpointId + ", " + path + "}";
        }

    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
