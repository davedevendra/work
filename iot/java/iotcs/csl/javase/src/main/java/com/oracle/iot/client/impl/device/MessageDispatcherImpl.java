/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import com.oracle.iot.client.TransportException;
import com.oracle.iot.client.device.DirectlyConnectedDevice;
import com.oracle.iot.client.device.util.MessageDispatcher;
import com.oracle.iot.client.device.persistence.MessagePersistence;
import com.oracle.iot.client.device.util.RequestDispatcher;
import com.oracle.iot.client.device.util.RequestHandler;
import com.oracle.iot.client.impl.DiagnosticsImpl;
import com.oracle.iot.client.impl.TestConnectivity;
import com.oracle.iot.client.message.Message;
import com.oracle.iot.client.message.Message.Reliability;
import com.oracle.iot.client.message.Message.Type;
import com.oracle.iot.client.message.RequestMessage;
import com.oracle.iot.client.message.Resource;
import com.oracle.iot.client.message.ResourceMessage;
import com.oracle.iot.client.message.ResponseMessage;
import com.oracle.iot.client.message.StatusCode;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLException;
import oracle.iot.client.StorageObject.SyncStatus;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MessageDispatcherImpl
 */
public final class MessageDispatcherImpl extends MessageDispatcher {

    private static final int DEFAULT_MAXIMUM_MESSAGES_TO_QUEUE = 10000;
    private static final int DEFAULT_MAXIMUM_MESSAGES_PER_CONNECTION = 1000;
    private static final long DEFAULT_POLLING_INTERVAL = 3000; // milliseconds
    private static final long DEFAULT_SETTLE_TIME = 10000; // milliseconds
    private static final int REQUEST_DISPATCHER_THREAD_POOL_SIZE =
                 Math.max( Integer.getInteger("oracle.iot.client.device.request_dispatcher_thread_pool_size", 1),1);
    static final String MAXIMUM_MESSAGES_TO_QUEUE_PROPERTY =
        "oracle.iot.client.device.dispatcher_max_queue_size";
    private static final String MAXIMUM_MESSAGES_PER_CONNECTION_PROPERTY = 
        "oracle.iot.client.device.dispatcher_max_messages_per_connection";

    // Amount of time in milliseconds to backoff in the face of a 503 from the server.
    // This is the starting value for the amount of time to backoff.
    // The backoff time increases exponentially if the server continues to return a 503.
    // Defaults to 1000 milliseconds. Not less than 100 milliseconds.
    private static final long BACKOFF_DELAY =
        Math.max( Long.getLong("oracle.iot.client.device.message_dispatcher_backoff", 1000L),100L);

    private static final String POLLING_INTERVAL_PROPERTY =
        "oracle.iot.client.device.dispatcher_polling_interval";
    private static final String DISABLE_LONG_POLLING_PROPERTY =
        "com.oracle.iot.client.disable_long_polling";
    private static final String SETTLE_TIME_PROPERTY =
            "oracle.iot.client.device.dispatcher_settle_time";

    // This flag is used by receiver thread to wait for reconnection
    // when SocketException exception happens due to network disruptions.
    private AtomicBoolean waitOnReconnect = new AtomicBoolean(false);

    static final String COUNTERS_URL;
    static final String RESET_URL;
    static final String POLLING_INTERVAL_URL;
    static final String DIAGNOSTICS_URL;
    static final String TEST_CONNECTIVITY_URL;

    static {
        COUNTERS_URL = "deviceModels/" + ActivationManager.MESSAGE_DISPATCHER_URN + "/counters";
        RESET_URL = "deviceModels/" + ActivationManager.MESSAGE_DISPATCHER_URN + "/reset";
        POLLING_INTERVAL_URL = "deviceModels/" + ActivationManager.MESSAGE_DISPATCHER_URN + "/pollingInterval";
        DIAGNOSTICS_URL = "deviceModels/" + ActivationManager.DIAGNOSTICS_URN + "/info";
        TEST_CONNECTIVITY_URL = "deviceModels/" + ActivationManager.DIAGNOSTICS_URN + "/testConnectivity";
    }

    private RequestHandler counterHandler;
    private final RequestHandler resetHandler;

    private final RequestHandler pollingIntervalHandler;
    private RequestHandler diagnosticsHandler;
    private RequestHandler testConnectivityHandler;

    final HashMap<StorageObjectImpl, HashSet<String>> contentMap;
    final HashSet<String> failedContentIds;

    /*
     * Queue of outgoing messages to be dispatched to server
     * (with thread for servicing the queue).
     * Note: it has package access for unit test access.
     */
    final PriorityQueue<Message> outgoingMessageQueue;

    // This keeps track of how many elements the outgoingMessageQueue can
    // accept without overflowing the queue size. queueCapacity is decremented
    // in the queue method when a message is added to outgoingMessageQueue and
    // is incremented in Transmitter#send method when messages are successfully
    // sent.
    private final AtomicInteger queueCapacity = new AtomicInteger(getQueueSize());

    /*
     * maximum number of messages allowed in the outgoing message queue
     */
    private final int maximumQueueSize;

    /*
     * maximum number of messages to send in one connection
     */
    private final int maximumMessagesPerConnection;


    // Counter indicating total number of messages that have been delivered
    private int totalMessagesSent;

    // Counter indicating total number of messages that have been delivered
    private int totalMessagesReceived;

    // Counter indicating total number of messages that were retried for delivery
    private int totalMessagesRetried;

    // Counter indicating total number of bytes that have been delivered
    private long totalBytesSent;

    // Counter indicating total number of bytes that have been delivered
    private long totalBytesReceived;

    // Counter indicating total number of protocol errors.
    private long totalProtocolErrors;

    /*
     * Maximum time in milliseconds to wait for a message to be queued
     * before the send thread will poll the server
     */
    private long pollingInterval;

    private final Lock sendLock = new ReentrantLock();
    private final Condition messageQueued = sendLock.newCondition();

    private final Lock contentLock = new ReentrantLock();

    private final Lock receiveLock = new ReentrantLock();
    private final Condition messageSent = receiveLock.newCondition();

    /*
     * Thread for sending messages.
     */
    private final Thread transmitThread;

    /*
     * Thread for receiving requests when long polling.
     */
    private final Thread receiveThread;

    private final DirectlyConnectedDevice deviceClient;

    /*
     * Set to true if close method has been called.
     */
    private boolean closed;
    private volatile boolean requestClose;

    private MessageDispatcher.DeliveryCallback deliveryCallback;
    private MessageDispatcher.ErrorCallback errorCallback;
    private final boolean useLongPolling;

    @Override
    public void setOnDelivery(MessageDispatcher.DeliveryCallback deliveryCallback) {
        this.deliveryCallback = deliveryCallback;
    }

    @Override
    public void setOnError(MessageDispatcher.ErrorCallback errorCallback) {
        this.errorCallback = errorCallback;
    }

    @Override
    public RequestDispatcher getRequestDispatcher() {
        return RequestDispatcher.getInstance();
    }

    /**
     * Implmentation of MessageDispatcher.
     * @param deviceClient The DirectlyConnectedDevice that uses this message dispatcher.
     *
     */
    public MessageDispatcherImpl(DirectlyConnectedDevice deviceClient) {

        this.requestClose = false;
        this.closed = false;

        this.deviceClient = deviceClient;
        this.pollingInterval = getPollingInterval();
        this.maximumQueueSize = getQueueSize();
        this.maximumMessagesPerConnection = getMaximumMessagesPerConnection();

        final String endpointId = MessageDispatcherImpl.this.deviceClient.getEndpointId();

        this.useLongPolling = !Boolean.getBoolean(DISABLE_LONG_POLLING_PROPERTY);
        
        this.contentMap = new HashMap<StorageObjectImpl, HashSet<String>>();
        this.failedContentIds = new HashSet<String>();
        
        this.outgoingMessageQueue = new PriorityQueue<Message>(
                getQueueSize(),
                new Comparator<Message>() {
                    @Override
                    public int compare(Message o1, Message o2) {

                        // Note this implementation is not consistent with equals. It is possible
                        // that a.compareTo(b) == 0 is not that same boolean value as a.equals(b)

                        // The natural order of enum is the enum's ordinal, i.e.,
                        // x.getPriority().compareTo(y.getPriority() will give {x,y}
                        // if x is a lower priority. What we want is to sort by the
                        // higher priority.
                        int c = o2.getPriority().compareTo(o1.getPriority());

                        // If they are the same priority, take the one that was created first
                        if (c == 0) {
                            c = o1.getEventTime().compareTo(o2.getEventTime());
                        }

                        // If they are still the same, take the one with higher reliability.
                        if (c == 0) {
                            c = o1.getReliability().compareTo(o2.getReliability());
                        }

                        // If they are still the same, take the one that was created first.
                        if (c == 0) {
                            long lc = o1.getOrdinal() - o2.getOrdinal();
                            if (lc > 0) c = 1;
                            else if (lc < 0) c = -1;
                            else c = 0; // this would mean o1 == o2! This shouldn't happen.
                        }

                        return c;
                    }
                }
        );

        this.totalMessagesSent = this.totalMessagesReceived = 0;
        this.totalMessagesRetried = 0;
        this.totalBytesSent = this.totalBytesReceived = this.totalProtocolErrors = 0L;

        // Start the receive thread first in order to receive any pending
        // requests messages when messages are first transmitted.
        receiveThread = new Thread(new Receiver(), "MessageDispatcher-receive");
        // Allow the VM to exit if this thread is still running
        receiveThread.setDaemon(true);
        receiveThread.start();

        transmitThread = new Thread(new Transmitter(), "MessageDispatcher-transmit");
        // Allow the VM to exit if this thread is still running
        transmitThread.setDaemon(true);
        transmitThread.start();

        counterHandler = new RequestHandler() {

            public ResponseMessage handleRequest(RequestMessage request) throws Exception {
                if (request == null) {
                    throw new NullPointerException("Request is null");
                }

                StatusCode status;
                if (request.getMethod() != null) {
                    if ("GET".equals(request.getMethod().toUpperCase(Locale.ROOT))) {
                        try {
                            JSONObject job = new JSONObject();
                            float size = maximumQueueSize - queueCapacity.get();
                            float max = maximumQueueSize;
                            // we only want 1 decimal point percision
                            int load = (int)(size / max * 1000f);
                            job.put("load", (float)load / 10f);
                            job.put("totalBytesSent", totalBytesSent);
                            job.put("totalBytesReceived", totalBytesReceived);
                            job.put("totalMessagesReceived", totalMessagesReceived);
                            job.put("totalMessagesRetried", totalMessagesRetried);
                            job.put("totalMessagesSent", totalMessagesSent);
                            job.put("totalProtocolErrors", totalProtocolErrors);
                            String jsonBody = job.toString();
                            return new ResponseMessage.Builder(request)
                                .statusCode(StatusCode.OK)
                                .body(jsonBody)
                                .build();
                        } catch (Exception exception) {
                            status = StatusCode.BAD_REQUEST;
                        }
                    } // GET
                    else  {
                        //Unsupported request method
                        status = StatusCode.METHOD_NOT_ALLOWED;
                    }
                }
                else {
                    //Unsupported request method
                    status = StatusCode.METHOD_NOT_ALLOWED;
                }
                return new ResponseMessage.Builder(request)
                    .statusCode(status)
                    .build();
            }
        };        

        resetHandler = new RequestHandler() {

            public ResponseMessage handleRequest(RequestMessage request) throws Exception {
                if (request == null) {
                    throw new NullPointerException("Request is null");
                }

                StatusCode status;     
                if (request.getMethod() != null) {
                    if ("PUT".equals(request.getMethod().toUpperCase(Locale.ROOT))) {
                        try {
                            totalBytesSent = totalBytesReceived = totalProtocolErrors = 0L;
                            totalMessagesSent = totalMessagesReceived = totalMessagesRetried = 0;
                            status = StatusCode.OK;
                        } catch (Exception exception) {
                            status = StatusCode.BAD_REQUEST;
                        }
                    }
                    else  {
                        //Unsupported request method
                        status = StatusCode.METHOD_NOT_ALLOWED;
                    } 
                }
                else {
                    status = StatusCode.METHOD_NOT_ALLOWED;
                }
                return new ResponseMessage.Builder(request)
                    .statusCode(status)
                    .build();
            }
        };        

        /*
         * resource definition for pollingInterval: {
         *   "get": {
         *     "schema": {
         *       "properties": {
         *         "value": {
         *           "type": "number",
         *           "description": "The incoming message polling interval
         *              in seconds on the directly connected device."
         *         }
         *       }
         *     },
         *   "put" : {
         *     "parameters": [
         *       {"name": "value",
         *        "type": "integer",
         *        "in": "body",
         *        "description": "The incoming message polling interval in
         *          seconds.",
         *        "required": "true"},
         *       ]
         *   }
         */
        this.pollingIntervalHandler = new RequestHandler() {
            public ResponseMessage handleRequest(RequestMessage request) throws Exception {
                if (request == null) {
                    throw new NullPointerException("Request is null");
                }

                StatusCode status;
                if (request.getMethod() != null) {
                    if ("GET".equals(request.getMethod().toUpperCase(Locale.ROOT))) {
                        try {
                            JSONObject job = new JSONObject();
                            job.put("value", pollingInterval);
                            return new ResponseMessage.Builder(request)
                                .statusCode(StatusCode.OK)
                                .body(job.toString())
                                .build();
                        } catch (Exception exception) {
                            status = StatusCode.BAD_REQUEST;
                        }
                    }
                    else if ("PUT".equals(request.getMethod().toUpperCase(Locale.ROOT))) {
                        try {
                            String jsonRequestBody =
                                new String(request.getBody(), "UTF-8");
                            JSONObject jsonObject =
                                new JSONObject(jsonRequestBody);
                            StringBuilder errors = new StringBuilder();
                            // Use Long objects here in case there was a
                            // problem getting the parameters.
                            long newPollingInterval = getParam(jsonObject, "value",
                                                       errors);
                            if (errors.toString().length() != 0) {
                                return getBadRequestResponse(request,
                                    endpointId, errors.toString());
                            }
                            if (newPollingInterval < 0) {
                                return getBadRequestResponse(request, endpointId, 
                                                             "Polling interval must be a numeric value greater than or equal to 0.");
							}else {
								pollingInterval = newPollingInterval;
							}
                            status = StatusCode.OK;
                        } catch (JSONException exception) {
                            status = StatusCode.BAD_REQUEST;
                        }                            
                    }
                    else  {
                        //Unsupported request method
                        status = StatusCode.METHOD_NOT_ALLOWED;
                    } 
                }
                else {
                    status = StatusCode.METHOD_NOT_ALLOWED;
                }
                return new ResponseMessage.Builder(request)
                    .statusCode(status)
                    .build();
            }
        };        

        try {
            diagnosticsHandler = new DiagnosticsImpl();
            TestConnectivity testConnectivity = new TestConnectivity(endpointId, this);
            testConnectivityHandler = testConnectivity.getTestConnectivityHandler();
            ResourceMessage resourceMessage = new ResourceMessage.Builder()
                .endpointName(endpointId)
                .source(endpointId)
                .register(getResource(endpointId, COUNTERS_URL, counterHandler, Resource.Method.GET))
                .register(getResource(endpointId, RESET_URL, resetHandler,Resource.Method.PUT))     
                .register(getResource(endpointId, POLLING_INTERVAL_URL, pollingIntervalHandler, Resource.Method.GET, Resource.Method.PUT))
                .register(getResource(endpointId, DIAGNOSTICS_URL, diagnosticsHandler, Resource.Method.GET))
                .register(getResource(endpointId, TEST_CONNECTIVITY_URL, testConnectivityHandler, Resource.Method.GET, Resource.Method.PUT))
                .build();
            // TODO: handle error
            this.queue(resourceMessage);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, e.getMessage());
            e.printStackTrace();
        } catch(Throwable t) {
            t.printStackTrace();
        }

        // Do this last after everything else is established.
        // Populate outgoing message queue from persisted messages, but leave the
        // messages in persistence. The messages are removed from persistence when
        // they are delivered successfully.
        // TODO: IOT-49043
        MessagePersistence messagePersistence = MessagePersistence.getInstance();
        if (messagePersistence != null) {
            final List<Message> messages = messagePersistence.load(deviceClient.getEndpointId());
            if (messages != null && !messages.isEmpty()) {
                messagePersistence.delete(messages);
                sendLock.lock();
                try {
                    this.outgoingMessageQueue.addAll(messages);
                    queueCapacity.addAndGet(-(messages.size()));
                } finally {
                    sendLock.unlock();
                }
            }
        }

    }

    private static int getQueueSize() {
        int size = Integer.getInteger(MAXIMUM_MESSAGES_TO_QUEUE_PROPERTY, DEFAULT_MAXIMUM_MESSAGES_TO_QUEUE);
        // size must be at least 1 or PriorityQueue constructor will throw exception
        return (size > 0 ? size : DEFAULT_MAXIMUM_MESSAGES_TO_QUEUE);
    }

    private static int getMaximumMessagesPerConnection() {
        int max = Integer.getInteger(MAXIMUM_MESSAGES_PER_CONNECTION_PROPERTY, DEFAULT_MAXIMUM_MESSAGES_PER_CONNECTION);
        // size must be at least 1
        return (max > 0 ? max : DEFAULT_MAXIMUM_MESSAGES_PER_CONNECTION);
    }

    private static long getPollingInterval() {
        long interval = Long.getLong(POLLING_INTERVAL_PROPERTY, DEFAULT_POLLING_INTERVAL);
        // polling interval may be zero, which means wait forever
        return (interval >= 0 ? interval : DEFAULT_POLLING_INTERVAL);
    }

    /**
     * Returns the Long parameter specified by 'paramName' in the request if
     * it's available.
     *
     * @param jsonObject a {@link JSONObject} containing the JSON request.
     * @param paramName   the name of the parameter to get.
     * @param errors  a {@link StringBuilder} of errors.  Any errors produced
     * from retrieving the parameter will be
     *                appended to this.
     * @return the parameter if it can be retrieved, otherwise {@code null}.
     */
    private Long getParam(JSONObject jsonObject, String paramName,
            StringBuilder errors) {
        Long value = null;

        try {
            Number jsonNumber = (Number)jsonObject.opt(paramName);

            if (jsonNumber != null) {
                try {
                    value = jsonNumber.longValue();

                } catch (NumberFormatException nfe) {
                    errors.append(paramName).append(
                        " must be a numeric value.");
                }
            } else {
                appendSpacesToErrors(errors);
                errors.append("The ").append(paramName).append(
                    " value must be supplied.");
            }
        } catch(ClassCastException cce) {
            appendSpacesToErrors(errors);
            errors.append("The ").append(paramName).append(
                " value must be a number.");
        }

        return value;
    }
    /**
     * Appends spaces (error separators) to the errors list if it's not empty.
     *
     * @param errors a list of errors in a StringBuilder.
     */
    private void appendSpacesToErrors(StringBuilder errors) {
        if (errors.toString().length() != 0) {
            errors.append("  ");
        }
    }

    /**
     * Returns an appropriate response if the request is bad.
     *
     * @param requestMessage the request for this capability.
     * @return an appropriate response if the request is bad.
     */
    private ResponseMessage getBadRequestResponse(RequestMessage requestMessage, String message, String src) {
        return getResponseMessage(requestMessage, message, StatusCode.BAD_REQUEST);
    }

    /**
     * Returns an {@link RequestMessage} with the supplied parameters.
     *
     * @param requestMessage the request for this capability.
     * @param body           the body for the response.
     * @param statusCode     the status code of the response.
     * @return an appropriate response if the request is {@code null}.
     */
    private ResponseMessage getResponseMessage(RequestMessage requestMessage,
            String body, StatusCode statusCode) {
        return new ResponseMessage.Builder(requestMessage)
            .body(body)
            .statusCode(statusCode)
            .build();
    }

    // Get current software version
    private String getSoftwareVersion() {
        // TODO: Need better mechanism for obtaining the version
        return System.getProperty("oracle.iot.client.version", "Unknown");
    }
    
    public void addStorageObjectDependency(StorageObjectImpl storageObject, String clientMsgId) {
        contentLock.lock();
        try {
            if(!contentMap.containsKey(storageObject)) {
                contentMap.put(storageObject, new HashSet<String>());
            }
            contentMap.get(storageObject).add(clientMsgId);
        } finally {
            contentLock.unlock();
        }
    }
    
    public void removeStorageObjectDependency(StorageObjectImpl storageObject) {
        boolean completed = storageObject.getSyncStatus() == SyncStatus.IN_SYNC;
        HashSet<String> ids;
        contentLock.lock();
        try {
            ids = contentMap.remove(storageObject);
            if (!completed && ids != null) {
                failedContentIds.addAll(ids);
            }
        } finally {
            contentLock.unlock();
        }
    }
    
    boolean isContentDependent(String clientId) {
        contentLock.lock();
        try {
            Collection<HashSet<String>> sets = contentMap.values();
            for(HashSet<String> set:sets) {
                if (set.contains(clientId)) {
                    return true;
                }
            }
            return false;
        } finally {
            contentLock.unlock();
        }
    }

    @Override
    public void queue(Message... messages) {
        if (messages == null || messages.length == 0) {
            throw new IllegalArgumentException("message is null");
        }

        sendLock.lock();
        try {
            if (queueCapacity.get() < messages.length) {
                throw new ArrayStoreException("queue is full");
            }
            for (Message message : messages) {
                outgoingMessageQueue.offer(message);
            }
            // decrement the queue capacity by the number of messages queued.
            queueCapacity.addAndGet(-(messages.length));
            messageQueued.signal();
        } finally {
            sendLock.unlock();
        }
    }

    @Override
    public void offer(Message... messages) {
        if (messages == null || messages.length == 0) {
            throw new IllegalArgumentException("message is null");
        }

        final MessagingPolicyImpl messagingPolicy;

        final PersistenceStore persistenceStore =
                PersistenceStoreManager.getPersistenceStore(deviceClient.getEndpointId());
        final Object mpiObj = persistenceStore.getOpaque(MessagingPolicyImpl.class.getName(), null);
        if (mpiObj == null) {
            messagingPolicy = new MessagingPolicyImpl(deviceClient);
            persistenceStore
                    .openTransaction()
                    .putOpaque(MessagingPolicyImpl.class.getName(), messagingPolicy)
                    .commit();

            final DevicePolicyManager devicePolicyManager
                    = DevicePolicyManager.getDevicePolicyManager(deviceClient);
            devicePolicyManager.addChangeListener(messagingPolicy);

        } else {
            messagingPolicy = MessagingPolicyImpl.class.cast(mpiObj);
        }

        final List<Message> messagesToQueue = new ArrayList<Message>();
        try {
            for (Message message : messages) {
                Message[] messagesFromPolicy = messagingPolicy.applyPolicies(message);
                if (messagesToQueue != null) {
                    Collections.addAll(messagesToQueue, messagesFromPolicy);
                }
            }
        } catch (IOException e) {
            // TODO: retry?
            getLogger().log(Level.WARNING, e.getMessage());
            if (errorCallback != null) {
                final List<Message> messageList = new ArrayList<Message>(messages.length);
                Collections.addAll(messageList, messages);
                errorCallback.failed(messageList, e);
            }
        } catch (GeneralSecurityException e) {
            // TODO: retry?
            getLogger().log(Level.WARNING, e.getMessage());
            if (errorCallback != null) {
                final List<Message> messageList = new ArrayList<>(messages.length);
                Collections.addAll(messageList, messages);
                errorCallback.failed(messageList, e);
            }
        }

        if (messagesToQueue.size() > 0) {
            queue(messagesToQueue.toArray(new Message[messagesToQueue.size()]));
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void close() throws IOException {
        if (!closed) {

            requestClose = true;

            if (receiveThread != null) {
                receiveThread.interrupt();
            }

            try {
                transmitThread.interrupt();
                transmitThread.join();
            } catch (InterruptedException e) {
                // Restore the interrupted status
                Thread.currentThread().interrupt();
            }

            closed = true;
        }
    }

    public boolean isClosed() {
        return requestClose;
    }

    /*************************************************************************
     *
     * Handling of the blocking queue, servicing the queue,
     * and dispatching dequeued entries.
     *
     *************************************************************************/
    private class Transmitter implements Runnable {

        // For exponential backoff, take the Fibbonaci value times the delay in milliseconds.
        private final int[] fib = new int[]{0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144};
        private final long delay_in_millis = BACKOFF_DELAY;

        // Attempt is incremented if there is a temporary error returned from the server
        private int attempt = 0;

        // 'backoff' is how long to delay sending messages because
        // of some temporary condition on the server. Messages can still
        // be queued, but they won't be sent until the backoff time
        // expires. If an AlertMessage is queued, then an attempt is made
        // to send the messages, regardless of the remaining backoff time.
        private long backoff = 0L;

        // Calculate the new value of 'backoff'.
        // If backoff > 0, then we are already backing off and
        // no adjustment is made. Only adjust backoff if backoff
        // less than or equal to zero. A backoff value of less
        // than or equal to zero means we are either not
        // currently backing off, or the backoff period has
        // expired. This method should only be called
        // from the 'send' method when handling an IOException.
        private long calculateBackoff() {
            if (backoff <= 0L) {
                attempt = Math.min(attempt + 1, fib.length - 1);
                backoff = fib[attempt] * delay_in_millis;
            }
            return backoff;
        }

        @Override
        public void run() {

            // Messages pending delivery. Messages from the outgoingMessageQueue
            // are placed into this list.
            final List<Message> pendingMessages = new ArrayList<Message>();

            while (true) {

                if (requestClose && outgoingMessageQueue.isEmpty()) {
                    break;
                }

                // 'newAlert' is set to true if the outgoingMessageQueue
                // contains an alert message. This knowledge is used by
                // the send method to know whether or not to force a send
                // when backoff > 0L. Basically, if we are backing off and
                // a new alert is queued, retry sending immediately.
                boolean newAlert = false;

                sendLock.lock();
                try {

                    // measure how long we wait.
                    final long t0 = System.currentTimeMillis();

                    // Transmit thread blocks waiting for an outgoing message
                    while (!requestClose && outgoingMessageQueue.isEmpty()) {

                        if (backoff > 0L) {
                            // If backing off, wait only as long as the backoff time,
                            // then break this loop even if no messages were queued.
                            // This lets the pending messages be retried when there
                            // are no messages being queued.
                            messageQueued.await(backoff, TimeUnit.MILLISECONDS);
                            break;
                        } else {
                        messageQueued.await();
                    }
                    }

                    // Adjust backoff by how long we waited for a message to be queued.
                    // If we've waited longer than we need, set backoff to zero.
                    if (backoff > 0L) {
                        final long waitTime = System.currentTimeMillis() - t0;
                        backoff = Math.max(backoff - waitTime, 0L);
                    }

                    // Add outgoing messages to pendingMessages. Call send
                    // outside of the sendLock so that the queue method
                    // is not blocked while sending.
                    while(!outgoingMessageQueue.isEmpty()) {
                        final Message message = outgoingMessageQueue.poll();
                        newAlert |= message.getType() == Type.ALERT;
                        pendingMessages.add(message);
                    }

                } catch (InterruptedException e) {
                    // restore interrupt state
                    Thread.currentThread().interrupt();

                } finally {
                    sendLock.unlock();
                }

                send(pendingMessages, newAlert);
            }
        }

        //
        // Get messages to send from the pendingMessages list. After
        // this method returns, there may still be messages in the
        // pendingMessages list. Left over messages are messages that
        // could not be sent because of an in-process storage object
        // upload, or messages that could not be sent because of we're
        // backing off (due to 'HTTP 503: server busy').
        //
        // 'receivedNewAlert' is a flag to indicate that at a new alert message
        // was queued. If we are backing off, and there aren't any new alert messages,
        // then this method returns an empty list.
        private List<Message>  getMessagesToSend(
            List<Message> pendingMessages,
            boolean receivedNewAlert) {

            if (pendingMessages.isEmpty()) {
                return Collections.<Message>emptyList();
            }

            // Flag to indicate that we're in backoff mode. If we're in backoff
            // mode, we'll only try to send messages if one of the messages is
            // an alert. In other words, if isBackoff is true and contains
            // alert is false, then this method should return an empty list.
            final boolean isBackoff = backoff > 0L;

            // If we're backing off and we haven't received a new alert,
            // return an empty list.
            if (isBackoff && !receivedNewAlert) {
                return Collections.<Message>emptyList();
            }

            // errorList are messages with failed content.
            // These are passed to the error callback.
            final List<Message> errorList = new ArrayList<Message>();

            // messageList are messages that are to be sent.
            // This is the list that is returned from this method.
            final List<Message> messageList = new ArrayList<Message>();

            // Process pendingMessages.
            // Logically, this loop says:
            //     "While the message is not dependent on content,
            //      move the message from pendingMessages to messageList."
            // As soon as the loop hits a message that is dependent
            // on content (i.e., has a storage object that is still
            // uploading), the loop breaks, and there may still be
            // messages in the pendingMessages list.
            final Iterator<Message> messageIterator = pendingMessages.iterator();
            while(messageIterator.hasNext()) {
                final Message message = messageIterator.next();
                final String clientId = message.getClientId();
                if (message.getType() == Message.Type.RESPONSE) {
                    // Response messages are always added to the message list.
                    messageIterator.remove();
                    messageList.add(message);
                } else if (failedContentIds.contains(clientId)) {
                    // If the content failed to upload, move the message
                    // to the errorList. A message will never be sent if
                    // the upload failed.
                    messageIterator.remove();
                    // removed a message from pending, so queue capacity increases by one.
                    queueCapacity.addAndGet(1);
                    errorList.add(message);
                } else if (!isContentDependent(clientId)) {
                    // If the message is not dependent on content, move it to
                    // the message list.
                    // Do not modify queueCapacity here.
                    // We have not increased capacity, we've just split the
                    // used capacity between messageList and pendingMessages.
                    messageIterator.remove();
                    messageList.add(message);

                } else {
                    // The message is dependent on content and that
                    // content upload is still in progress.
                    // All other messages in the wait list will
                    // remain in the wait list for now.
                    break;
                }
            }

            if (!errorList.isEmpty() && MessageDispatcherImpl.this.errorCallback != null) {
                MessageDispatcherImpl.this.errorCallback.
                    failed(errorList, new IOException("Content sync failed"));
            }

            return messageList;
        }

        private void send(List<Message> pendingMessages, boolean newAlert) {

            // Note that getMessagesToSend modifies pendingMessages.
            // After this call, pendingMessages will typically be empty.
            // But it might not be empty if there are messages waiting for
            // storage object upload.
            final List<Message> messages = getMessagesToSend(pendingMessages, newAlert);

            if (messages.isEmpty()) {
                return;
            }

            // Unfortunately, DirectlyConnectedDevice#send takes an array, not a List.
            // If MessageDispatcherImpl was in the same package as DirectlyConnectedDevice,
            // we could avoid this. TODO
            final Message[] messageArray = messages.toArray(new Message[messages.size()]);

            // 'fromIndex' is the first index into the messages array of messages passed
            // to DirectlyConnectedDevice#send. Typically, this will be zero. But in the
            // case where we are trying to send while backing off, this will be the index
            // of the first message passed to the DCD#send method.
            int fromIndex = 0;

           try {

               // Flag to know if we're backing off.
               // Use 'attempt' here, not backoff, because
               // backoff will be zero when the backoff
               // period expires.
               final boolean backingOff = attempt > 0;

               // If we are backing off, then we were trying to resend messages.
               // There may be persisted messages. Clear them out now, before trying to
               // send again. If the send fails, then they'll get persisted again.
                if (backingOff) {
                    final MessagePersistence messagePersistence = MessagePersistence.getInstance();
                    if (messagePersistence != null) {
                        final List<Message> guaranteedMessages = new ArrayList<Message>();
                        for (int index = 0; index < messageArray.length; index++) {
                            final Message message = messageArray[index];
                            if (message.getReliability() == Reliability.GUARANTEED_DELIVERY) {
                                guaranteedMessages.add(message);
                            }
                        }
                        if (!guaranteedMessages.isEmpty()) {
                            messagePersistence.delete(guaranteedMessages);
                        }
                    }
                }

               // 'iter' counts how many iterations of this do loop we've done.
               // It is used as an index into 'fib'. The idea is that, if we are
               // backing off, each iteration of this loop we'll send more messages
               // than the previous loop. In theory, this will help prevent
               // the recovering server from being swamped with messages.
               int iter = 0;

               // 'offset' is how far into messageArray we are.
               // On any iteration, there are messageArray.length - offset
               // messages left to send.
               int offset = 0;

               do {
                   // The number of messages in messageArray that we have left to send
                   final int numMessagesLeftToSend = messageArray.length - offset;

                   // The number of messages we want to send on this iteration
                   // depends on whether or not we are backing off.
                   // If we are backing off, send increasingly more each time.
                   // Otherwise, we want to send all the messages.
                   final int numMessageWeWantToSend = backingOff
                       ? fib[Math.min(++iter,fib.length-1)]*10
                       : numMessagesLeftToSend;

                   // 'numMessagesToSend' is how many messages to send and is
                   // the lesser of numMessagesLeftToSend, numMessagesWeWantToSend,
                   // and maximumMessagesPerConnection.
                   final int numMessagesToSend = Math.min(
                       Math.min(numMessagesLeftToSend, numMessageWeWantToSend),
                       maximumMessagesPerConnection
                   );

                   // remember where this sublist starts. If the send call
                   // throws an exception, the code will re-queue messages into
                   // the pendingQueue starting from 'fromIndex'
                   fromIndex = offset;

                   // sublist is the list of messages to send
                   final Message[] sublist = Arrays.copyOfRange(messageArray, offset, offset+=numMessagesToSend);

                   deviceClient.send(sublist);
                   messagesSent(sublist);

               } while (offset<messageArray.length);

            } catch (IOException e) {

               if (e instanceof TransportException) {
                   final int status = ((TransportException)e).getStatusCode();
                   // 503 means "The server is currently unable to handle the
                   // request due to a temporary overloading or maintenance of the server."
                   if (status == 503) {
                       calculateBackoff();
                       getLogger().log(Level.WARNING, "Server busy. Messages will be retried in " + backoff + " milliseconds.");
                   }
               } else if (e instanceof SocketException
                   || e instanceof SocketTimeoutException
                   || e instanceof UnknownHostException
                   || e instanceof SSLException) {
                   // For network issues, do not do exponential backoff;
                   // rather, use constant delay. Side effect here is that
                   // attempt is set to 1. Attempt needs to be one here
                   // because the code in the try branch checks if (attempt==0).
                   // If attempt is zero, then the persisted messages won't
                   // get un-persisted in the try block.
                   // Note also that this resets backoff to the full delay,
                   // whereas calculateBackoff returns the remaining delay.
                   backoff = fib[(attempt=1)] * delay_in_millis;
                   getLogger().log(Level.WARNING, e.toString() + ". Messages will be retried in " + backoff + " milliseconds.");
               }

               totalProtocolErrors++;

               final List<Message> failedMessages = new ArrayList<Message>();

               // If attempt > 0, then we're either backing off because of a
               // temporary condition with the server or with the network.
               // In this case, we want to persist guaranteed delivery messages.
               // Note that MessagePersistence.getInstance() might return null.
               final MessagePersistence messagePersistence
                   = attempt > 0 ? MessagePersistence.getInstance() : null;
               // The messages we want to persist will be added to this list.
               final List<Message> messagesToPersist
                   = messagePersistence != null ? new ArrayList<Message>() : null;

               // 'fromIndex' is set in the code above to the index of the first element
                // of the sublist that is being passed to DirectlyConnectedDevice#send.
                // Messages in the 'messages' list with index < fromIndex have been
                // successfully sent. Messages in the 'messages' list with fromIndex <= index
                // have not been sent and need to be put back into pendingMessages for
                // reprocessing.
               while (fromIndex < messages.size()) {
                    final Message message = messages.get(fromIndex++);
                    final int retries = message.getRemainingRetries();
                    if (retries > 0) {
                        assert pendingMessages.indexOf(message) == -1;
                        pendingMessages.add(message);
                        message.setRemainingRetries(retries-1);

                        // if message is guaranteed, add it to the list of messages to persist.
                        if (messagePersistence != null) {
                            if (message.getReliability() == Reliability.GUARANTEED_DELIVERY) {
                                messagesToPersist.add(message);
                            }
                        }

                    } else {
                        // The message was not added back to pendingMessages, so the
                        // capacity of our queue increases by one.
                        failedMessages.add(message);
                        queueCapacity.addAndGet(1);
                        getLogger().log(Level.INFO,
                                "Cannot queue message for retry. Message discarded: "
                                        + message.getClientId());
                    }

                }

               // Persist messages for retry.
               if (messagePersistence != null && !messagesToPersist.isEmpty()) {
                   messagePersistence.save(messagesToPersist, deviceClient.getEndpointId());
               }

              // Tell the client that messages failed.
                if (MessageDispatcherImpl.this.errorCallback != null && !failedMessages.isEmpty()) {
                    MessageDispatcherImpl.this.errorCallback.
                        failed(failedMessages, e);
                }

            } catch (GeneralSecurityException e) {

                // Do not retry messages that failed because of GeneralSecurityException
                if (MessageDispatcherImpl.this.errorCallback != null) {
                    MessageDispatcherImpl.this.errorCallback.failed(messages, e);
                }

            }
        }

        // These messages were successfully delivered to the server.
        // Update state and notify callbacks.
        private void messagesSent(Message[] messages) {

            // Send is successful, so make sure backoff is reset to zero.
            backoff = 0L;
            attempt = 0;

            // Send is successful, increase the queue capacity
            // by the number of messages sent
            queueCapacity.addAndGet(messages.length);

            // Send is successful, so if the receiver thread was waiting
            // for reconnection, send a wakeup signal.
            if (waitOnReconnect.get()) {
                synchronized (waitOnReconnect) {
                    waitOnReconnect.set(false);
                    waitOnReconnect.notify();
                }
            }

            if (MessageDispatcherImpl.this.deliveryCallback !=
                null) {
                MessageDispatcherImpl.this.deliveryCallback.
                    delivered(Arrays.<Message>asList(messages));
            }
            totalMessagesSent += messages.length;

            for (int index=0; index<messages.length; index++) {
                final Message message = messages[index];

                // TODO: There must be a better way to handle totalBytesSent
                totalBytesSent += message.toJson().toString()
                    .getBytes(Charset.forName("UTF-8")).length;
            }

            // wake up receive thread
            receiveLock.lock();
            try {
                messageSent.signal();
            } finally {
                receiveLock.unlock();
            }
        }
    }

    private final Lock pendingQueueLock = new ReentrantLock();
    private final Condition pendingTrigger = pendingQueueLock.newCondition();

    // RequestMessages that were not handled are stored here by the Dispatcher thread.
    // See Dispatcher#run() method.
    private final Queue<RequestMessage> pendingRequestMessages = new LinkedList<RequestMessage>();

    private class PendingRequestProcessor implements Runnable {

        private final int MAX_WAIT_TIME = 1000;
        private final long settleTime;
        private final long timeZero;
        private final long averageWaitTime; // in nano-seconds!
        private boolean settled;

        private PendingRequestProcessor(long settleTime) {

            this.settleTime = settleTime;
            this.timeZero = System.currentTimeMillis();
            if (settleTime <= MAX_WAIT_TIME) {
                this.averageWaitTime = TimeUnit.MILLISECONDS.toNanos(this.settleTime);
            } else {
                final long quotient = this.settleTime / MAX_WAIT_TIME;
                final long waitTimeMillis = MAX_WAIT_TIME + (this.settleTime - quotient * MAX_WAIT_TIME) / quotient;
                averageWaitTime = TimeUnit.MILLISECONDS.toNanos(waitTimeMillis);
            }

            settled = false;
        }

        @Override
        public void run() {

            while(!requestClose && !settled) {

                List<RequestMessage> messageList = null;

                pendingQueueLock.lock();
                try {

                    long waitTime = TimeUnit.MILLISECONDS.toNanos(
                            // wait at most the amount of time left before settleTime expires
                            settleTime - (System.currentTimeMillis() - timeZero)
                    );

                    if (waitTime > 0 && pendingRequestMessages.isEmpty()) {
                        waitTime = pendingTrigger.awaitNanos(waitTime);
                    }

                    //
                    // waitTime represents how much time is left before settleTime expires.
                    // If we waited in the pendingTrigger.awaitNanos call, then waitTime
                    // has been adjusted by how long we actually waited. If waitTime is
                    // greater than zero and we blocked in awaitNanos, then a request
                    // was added to the queue. If waitTime is less than or equal to zero,
                    // then we waited the entire time, or waitTime was less than or equal
                    // to zero to begin with because settleTime has expired.
                    //

                    // If we waited the entire time and the queue is still empty, bail.
                    // Otherwise, a request was queued, or the queue wasn't empty to begin with.
                    if (waitTime <= 0 && pendingRequestMessages.isEmpty()) {
                        break;
                    }

                    // In the case where waitTime is greater than zero, we want to make
                    // sure we wait at least averageWaitTime before dispatching the request.
                    // In other words, if we were blocked in the awaitNanos and a request
                    // was added to the queue, we don't want to process it immediately;
                    // rather, wait at least one average wait time so we don't thrash.
                    if (waitTime > 0) {
                        waitTime = averageWaitTime;
                        while (waitTime > 0) {
                            waitTime = pendingTrigger.awaitNanos(waitTime);
                        }
                    }

                    // Operate on a copy of pendingRequestMessages so that we don't hold
                    // pendingQueueLock while dispatching. At the end of this loop,
                    // pendingRequestMessages will be empty.
                    if (!pendingRequestMessages.isEmpty()) {
                        messageList = new ArrayList<RequestMessage>(pendingRequestMessages.size());
                        RequestMessage requestMessage = null;
                        while ((requestMessage = pendingRequestMessages.poll()) != null) {
                            messageList.add(requestMessage);
                        }
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    continue;
                } finally {
                    pendingQueueLock.unlock();
                }

                settled |= settleTime <= (System.currentTimeMillis() - timeZero);

                if (messageList != null) {
                    Iterator<RequestMessage> iterator = messageList.iterator();
                    while (iterator.hasNext()) {
                        RequestMessage requestMessage = iterator.next();
                        dispatcher.execute(new Dispatcher(requestMessage, settled));
                    }
                }
            }
        }
    }

    private class Dispatcher implements Runnable {

        final RequestMessage requestMessage;
        private boolean settled;

        private Dispatcher(RequestMessage requestMessage, boolean settled) {

            this.requestMessage = requestMessage;
            this.settled = settled;
        }


        @Override
        public void run() {

                ResponseMessage responseMessage =
                    RequestDispatcher.getInstance().dispatch(requestMessage);


                if (settled || responseMessage.getStatusCode() != StatusCode.NOT_FOUND) {
                    try {
                        MessageDispatcherImpl.this.queue(responseMessage);
                    } catch (Throwable t) {
                        getLogger().log(Level.SEVERE, t.toString());
                    }

                } else { // not settled && status code == not found

                    // try this again later.
                    pendingQueueLock.lock();
                    try {
                        if (pendingRequestMessages.offer(requestMessage)) {
                            pendingTrigger.signal();
                        } else {
                            getLogger().log(Level.SEVERE, "Cannot queue request for dispatch");
                            responseMessage =
                                    new ResponseMessage.Builder(requestMessage)
                                            .statusCode(StatusCode.INTERNAL_SERVER_ERROR)
                                            .build();
                            MessageDispatcherImpl.this.queue(responseMessage);
                        }
                    } finally {
                        pendingQueueLock.unlock();
                    }
                    return;
                }
            }
    }

    private  static ThreadFactory threadFactory = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            final SecurityManager s = System.getSecurityManager();
            final ThreadGroup group = (s != null) ? s.getThreadGroup()
                    : Thread.currentThread().getThreadGroup();

            final Thread t = new Thread(group, r, "dispatcher-thread", 0);

            // this is opposite of what the Executors.DefaultThreadFactory does
            if (!t.isDaemon())
                t.setDaemon(true);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    };

    private static final Executor dispatcher;
    static {
        if (REQUEST_DISPATCHER_THREAD_POOL_SIZE > 1) {
            dispatcher = Executors.newFixedThreadPool(REQUEST_DISPATCHER_THREAD_POOL_SIZE, threadFactory);
        } else {
            dispatcher =  Executors.newSingleThreadExecutor(threadFactory);
        }
    }

    private class Receiver implements Runnable {

        private final long settleTime;
        private final long timeZero;
        private boolean settled;

        private Receiver() {

            long value = Long.getLong(SETTLE_TIME_PROPERTY, DEFAULT_SETTLE_TIME);
            settleTime = (value >= 0 ? value : DEFAULT_SETTLE_TIME);
            timeZero = System.currentTimeMillis();
            settled = settleTime == 0;

            if (settleTime > 0) {
                final PendingRequestProcessor pendingRequestProcessor =
                        new PendingRequestProcessor(settleTime);
                final Thread thread = new Thread(pendingRequestProcessor);
                thread.setDaemon(true);
                thread.start();
            }

        }
        @Override
        public void run() {

            mainLoop:
            while (!requestClose) {

                // If there was a SocketException observed before,
                // wait for this flag "waitOnReconnect" to be set to false
                // by the transmitter thread after the next message was sent successfully
                // or close() was called.
                while(waitOnReconnect.get() && !requestClose) {
                    synchronized (waitOnReconnect) {
                        try {
                            waitOnReconnect.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        // If close() was called, exit the main loop.
                        if (requestClose) {
                            break mainLoop;
                        }
                    }
                }

                // If not long polling, block until the pollingInterval has
                // timed out or a message has been sent.
                if (!MessageDispatcherImpl.this.useLongPolling){
                    receiveLock.lock();
                    try {
                        // Wait here until the messageSent is signaled, or
                        // until pollingInterval is expired.
                        if (pollingInterval > 0) {
                            messageSent.await(pollingInterval, TimeUnit.MILLISECONDS);
                        } else {
                            messageSent.await();
                        }
                    } catch (InterruptedException e) {
                        // Restore the interrupted status
                        Thread.currentThread().interrupt();
                        continue;
                    } finally {
                        receiveLock.unlock();
                    }
                }

                try {
                    RequestMessage requestMessage = null;
                    // Receive will ignore -1 timeout when not long polling
                    while ((requestMessage = deviceClient.receive(-1)) != null) {
                        totalMessagesReceived++;
                        totalBytesReceived += requestMessage.toJson().toString()
                                .getBytes(Charset.forName("UTF-8")).length;
                        if (!requestClose) {
                            settled |= settleTime <= (System.currentTimeMillis() - timeZero);
                            dispatcher.execute(new Dispatcher(requestMessage, settled));
                        }
                    }
                } catch (IOException ie) {
                    getLogger().log(Level.FINEST,
                            "MessageDispatcher.receiver.run: " + ie.toString());
                    // Network connection issues detected.
                    // So wait for reconnection signal from transmitter thread.
                    waitOnReconnect.set(true);
                } catch (GeneralSecurityException ge) {
                    getLogger().log(Level.FINEST,
                            "MessageDispatcher.receiver.run: " + ge.toString());
                    // Network connection issues detected.
                    // So wait for reconnection signal from transmitter thread.
                    waitOnReconnect.set(true);
                }
            }
        }
    }

    /* package */
    private Resource getResource(String endpointId, String name, RequestHandler requestHandler, Resource.Method... methods) {

        final MessageDispatcher messageDispatcher = this;
                final RequestDispatcher requestDispatcher =
                    messageDispatcher.getRequestDispatcher();
                requestDispatcher.registerRequestHandler(endpointId, name, requestHandler);

        Resource.Builder builder = new Resource.Builder().endpointName(endpointId);
        for (Resource.Method m: methods) {
            builder = builder.method(m);
        }
        return builder.path(name).name(name).build();
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
