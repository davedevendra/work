/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package oracle.iot.client.enterprise;

import oracle.iot.client.AbstractVirtualDevice;

import java.util.Date;

/**
 * VirtualDevice for an enterprise-client adds methods to execute a write-only
 * or executable attribute.
 */
public abstract class VirtualDevice extends AbstractVirtualDevice<VirtualDevice> {

    /**
     * Execute an action with the given data.
     * @param <T> the data type
     * @param actionName The name of the action
     * @param data The data to send to the action
     *
     */
    public abstract <T> void call(String actionName, T data);

    /**
     * Execute an action with no data.
     * @param actionName The name of the action
     */
    public abstract void call(String actionName);


    /**
     * An event passed to the {@code AlertCallback}.
     * @see VirtualDevice.AlertCallback
     */
    public static abstract class  AlertEvent extends Event<VirtualDevice> {

        /**
         * Get the alert's device model format URN.
         * @return the format URN
         */
        public abstract String getURN();

        /**
         * Get the time the event was raised by the device-client.
         * @return The time of the event
         */
        public abstract Date getEventTime();
    }


    /**
     * A callback interface for receiving notification of an Alert.
     */
    public interface AlertCallback {

        /**
         * Callback for receiving an alert from the server for a VirtualDevice.
         *
         * @param event The AlertEvent
         */
        void onAlert(AlertEvent event);
    }

    /**
     * Set a callback that is invoked when an alert for the given format URN is
     * received.
     * <p>
     * Note that it is possible to set a callback for a specific alert and
     * to set a callback for all alerts via {@link #setOnAlert(VirtualDevice.AlertCallback)}.
     * If there is a callback for the specific alert and for all alerts,
     * both callbacks will be invoked.
     * @param formatURN The alert format URN from the device model
     * @param callback a callback to invoke when an alert is received with the
     * format URN, if {@code null}, the existing callback will be removed
     * @see #setOnAlert(VirtualDevice.AlertCallback)
     */
    public abstract void setOnAlert(String formatURN, AlertCallback callback);


    /**
     * Set a callback that is invoked when any alert in the device model is
     * received.
     * <p>
     * Note that it is possible to set a callback for a specific alert
     * via {@link #setOnAlert(String, VirtualDevice.AlertCallback)}.
     * If there is a callback for the specific alert and for all alerts,
     * both callbacks will be invoked.
     * @param callback a callback to invoke when an alert is received,
     * if {@code null}, the existing callback will be removed
     * @see #setOnAlert(VirtualDevice.AlertCallback)
     */
    public abstract void setOnAlert(AlertCallback callback);

    /**
     * An event passed to the {@code DataCallback}.
     * @see VirtualDevice.DataCallback
     */
    public static abstract class  DataEvent extends Event<VirtualDevice> {
        /**
         * Get the data's device model format URN.
         * @return the format URN
         */
        public abstract String getURN();

        /**
         * Get the time the event was raised by the device-client.
         * @return The time of the event
         */
        public abstract Date getEventTime();
    }

    /**
     * A callback interface for receiving notification of custom data.
     */
    public interface DataCallback {
        /**
         * Callback for receiving custom data from the server for a
         * VirtualDevice.
         *
         * @param event The DataEvent
         */
        void onData(DataEvent event);
    }

    /**
     * Set a callback that is invoked when custom data for the given
     * format URN is received.
     * <p>
     * Note that it is possible to set a callback for a specific data format and
     * to set a callback for all formats via {@link #setOnData(VirtualDevice.DataCallback)}.
     * If there is a callback for the specific data and for all formats,
     * both callbacks will be invoked.
     * @param formatURN The data format URN from the device model
     * @param callback a callback to invoke when custom data is received with
     * the format URN, if {@code null}, the existing callback will be removed
     * @see #setOnData(VirtualDevice.DataCallback)
     */
    public abstract void setOnData(String formatURN, DataCallback callback);


    /**
     * Set a callback that is invoked when any custom data in the device model
     * is received.
     * <p>
     * Note that it is possible to set a callback for a specific data format
     * via {@link #setOnData(String, VirtualDevice.DataCallback)}.
     * If there is a callback for the specific data and for all formats,
     * both callbacks will be invoked.
     * @param callback a callback to invoke when custom data is received,
     * if {@code null}, the existing callback will be removed
     * @see #setOnData(VirtualDevice.DataCallback)
     */
    public abstract void setOnData(DataCallback callback);

    protected VirtualDevice() { super(); }
}
