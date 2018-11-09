/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

/**
 API for directly&ndash;connected devices and gateway devices as clients
 of the Oracle IoT Cloud Service. A directly&ndash;connected device client
 is able to send messages to, and receive requests from, the server. A gateway
 device client is also able to register indirectly&ndash;connected devices
 with the server and to proxy messages to and from the server on behalf of the
 indirectly&ndash;connected devices.
 <p>
 A directly&ndash;connected device or a gateway device is first registered
 on the server with an <em>activation identifier</em>. This identifier is
 used by the client to activate the device (establish a secure connection between
 the device and the Oracle IoT Cloud Service). The device must be activated by
 the application before messages can be sent or received.
 The {@link com.oracle.iot.client.device.DirectlyConnectedDevice#activate(String...) activate}
 method will activate the registered device at which point the server will
 create an <em>endpoint identifier</em> for the device.  The
 {@code DirectlyConnectedDevice} or {@code GatewayDevice} must be activated
 before it can be used to create a {@link oracle.iot.client.device.VirtualDevice} or
 register indirectly&ndash;connected devices.
 <p>
 The activate method need only be called once. The method
 {@link com.oracle.iot.client.device.DirectlyConnectedDevice#isActivated()} should
 be called before calling {@code activate}. If activate is called on a
 {@code DirectlyConnectedDevice} or {@code GatewayDevice} that is already
 activated, an IllegalStateException will be thrown.

 */
package oracle.iot.client.device;
