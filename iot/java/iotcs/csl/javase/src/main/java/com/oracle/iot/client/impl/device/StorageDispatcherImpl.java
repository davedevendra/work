/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.iot.client.StorageObject;
import com.oracle.iot.client.device.DirectlyConnectedDevice;
import com.oracle.iot.client.device.util.StorageDispatcher;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;


/**
 * The StorageDispatcher queues content for automatic upload to or download from
 * the Oracle Storage Cloud Service.
 */
public class StorageDispatcherImpl extends StorageDispatcher {

    private final static Map<DirectlyConnectedDevice,StorageDispatcherImpl> dispatcherMap =
            new WeakHashMap<DirectlyConnectedDevice,StorageDispatcherImpl>();

    /**
     * Get the singleton {@code StorageDispatcher}.
     * @return a StorageDispatcher
     * @param directlyConnectedDevice
     */
    public static StorageDispatcher getStorageDispatcher(DirectlyConnectedDevice directlyConnectedDevice) {
        StorageDispatcherImpl storageDispatcher = dispatcherMap.get(directlyConnectedDevice);
        if (storageDispatcher == null || storageDispatcher.isClosed()) {
            storageDispatcher =  new StorageDispatcherImpl();
            dispatcherMap.put(directlyConnectedDevice, storageDispatcher);
        }
        return storageDispatcher;
    }

    private StorageDispatcher.ProgressCallback progressCallback;
    private final Thread contentThread;
    private final Lock contentLock = new ReentrantLock();
    private final Condition contentQueued = contentLock.newCondition();
    private boolean closed;
    private boolean requestClose;

    //The following is protected with contentLock
    final Queue<com.oracle.iot.client.impl.device.StorageObjectDelegate> queue;
    
    public StorageDispatcherImpl() {
        this.closed = false;
        this.requestClose = false;
        this.queue = new LinkedList<com.oracle.iot.client.impl.device.StorageObjectDelegate>();
        this.contentThread = new Thread(new ContentTransmitter(), "ContentTransmitter-thread");
        this.contentThread.setDaemon(true);
        this.contentThread.start();
    }
    
    @Override
    public void queue(StorageObject storageObject) {

        if (storageObject == null) {
            throw new IllegalArgumentException("StorageObject cannot be null.");
        }

        final com.oracle.iot.client.impl.device.StorageObjectDelegate storageObjectDelegate =
                (com.oracle.iot.client.impl.device.StorageObjectDelegate)storageObject;

        final Progress.State state = storageObjectDelegate.getState();
        if (state != null) {
            switch (state) {
                case COMPLETED:
                    return;
                case QUEUED:
                case IN_PROGRESS:
                    throw new IllegalStateException("Transfer is pending.");
                default:
                    // fall through
            }
        }

        contentLock.lock();
        try {
            queue.offer(storageObjectDelegate);
            storageObjectDelegate.setState(Progress.State.QUEUED);
            if (progressCallback != null) {
                ProgressImpl p = new ProgressImpl(storageObject);
                dispatchProgressCallback(p);
            }
            contentQueued.signal();
        } finally {
            contentLock.unlock();
        }    
    }

    @Override
    public void cancel(StorageObject storageObject) {
        boolean cancelled = false;
            contentLock.lock();
            try {
                Progress.State state = ((com.oracle.iot.client.impl.device.StorageObjectDelegate) storageObject).getState();
                if (state == Progress.State.QUEUED) {
                    cancelled = queue.remove((com.oracle.iot.client.impl.device.StorageObjectDelegate) storageObject);
                }
                if (cancelled || state == Progress.State.IN_PROGRESS) {
                    ((com.oracle.iot.client.impl.device.StorageObjectDelegate) storageObject).setState(Progress.State.CANCELLED);
                }
            } finally {
                contentLock.unlock();
            }
            if (cancelled && progressCallback != null) {
                ProgressImpl p = new ProgressImpl(storageObject);
                dispatchProgressCallback(p);
            }
    }

    @Override
    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            requestClose = true;
            try {
                contentThread.interrupt();
                contentThread.join();
            } catch (InterruptedException e) {
                // Restore the interrupted status
                Thread.currentThread().interrupt();
            }

            synchronized (dispatcherMap) {
                for (Map.Entry<DirectlyConnectedDevice, StorageDispatcherImpl> entry : dispatcherMap.entrySet()) {
                    if (this.equals(entry.getValue())) {
                        dispatcherMap.remove(entry.getKey());
                        break;
                    }
                }
            }
            closed = true;
        }
    }

    public boolean isClosed() {
        return requestClose;
    }
    
    class ProgressImpl implements Progress {
        private final StorageObject storageObject;
        private Exception exception = null;
        private long bytesTransferred = 0;
        private State state;
        
        ProgressImpl(StorageObject storageObject) {
            this.storageObject = storageObject;
            this.state = ((com.oracle.iot.client.impl.device.StorageObjectDelegate)storageObject).getState();
        }
        
        void setFailureCause(Exception e) {
            this.exception = e;
        }
        
        void setBytesTransferred(long bytesTransferred) {
            this.bytesTransferred = bytesTransferred;
        }
        
        void setState(State state) {
            this.state = state;
        }
        
        @Override
        public StorageObject getStorageObject() {
            return storageObject;
        }

        @Override
        public State getState() {
            return state;
        }

        @Override
        public Exception getFailureCause() {
            return exception;
        }

        @Override
        public long getBytesTransferred() {
            return bytesTransferred;
        }
    }
    
    /**
     * Handles the content queue for pending uploads and downloads to the SCS.
     */
    private class ContentTransmitter implements Runnable {

        @Override
        public void run() {
            while (true) {
                if (requestClose && queue.isEmpty()) {
                    break;
                }
                com.oracle.iot.client.impl.device.StorageObjectDelegate storageObjectDelegate = null;
                contentLock.lock();
                try {
                    while (!requestClose && queue.isEmpty()) {
                        contentQueued.await();
                    }
                    storageObjectDelegate = queue.poll();
                } catch (InterruptedException e) {
                    // restore interrupt state
                    Thread.currentThread().interrupt();
                } finally {
                    contentLock.unlock();
                }
                if (storageObjectDelegate != null) {
                    if (StorageDispatcherImpl.this.progressCallback != null) {
                        ProgressImpl p = new ProgressImpl(storageObjectDelegate);
                        StorageDispatcherImpl.this.dispatchProgressCallback(p);
                    }
                    transferAndCallback(storageObjectDelegate);
                }
            }
        }
    }
    
    private boolean transferAndCallback(StorageObjectDelegate storageObjectDelegate) {
        final ProgressImpl p = new ProgressImpl(storageObjectDelegate);
        Progress.State state;
        try {
            // Blocking!
            storageObjectDelegate.sync();
            state = Progress.State.COMPLETED;
            p.setBytesTransferred(storageObjectDelegate.getLength());
        } catch (Exception e) {
            if (storageObjectDelegate.isCancelled()) {
                state = Progress.State.CANCELLED;
            } else {
                state = Progress.State.FAILED;
                p.setFailureCause(e);
            }
        }

        storageObjectDelegate.setState(state);
        p.setState(state);
        dispatchProgressCallback(p);

        return state == Progress.State.COMPLETED;
    }

    void dispatchProgressCallback(final ProgressImpl p) {
        if (progressCallback != null) {
            CALLBACK_DISPATCHER.execute(new Runnable() {
                @Override
                public void run() {
                    progressCallback.progress(p);
                }
            });
        }
    }

    private static final Executor CALLBACK_DISPATCHER
            = Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    final SecurityManager s = System.getSecurityManager();
                    final ThreadGroup group = (s != null) ? s.getThreadGroup()
                            : Thread.currentThread().getThreadGroup();

                    final Thread t = new Thread(group, r, "dispatcher-thread", 0);

                    // this is opposite of what the Executors.DefaultThreadFactory does
                    if (!t.isDaemon()) {
                        t.setDaemon(true);
                    }
                    if (t.getPriority() != Thread.NORM_PRIORITY) {
                        t.setPriority(Thread.NORM_PRIORITY);
                    }
                    return t;
                }
            });
}
