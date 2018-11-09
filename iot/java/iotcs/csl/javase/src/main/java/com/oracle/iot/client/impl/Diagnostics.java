/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 */

package com.oracle.iot.client.impl;

import com.oracle.iot.client.message.RequestMessage;
import com.oracle.iot.client.message.ResponseMessage;
import com.oracle.iot.client.message.StatusCode;
import com.oracle.iot.client.device.util.RequestHandler;

import org.json.JSONObject;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;

/**
 * Message handler for device diagnostics.
 */
public abstract class Diagnostics implements RequestHandler {
    private final Date startTime;
    private final String version;
    private final String fixedIpAddress;
    private final String fixedMacAddress;

    public Diagnostics() {
        this.startTime = new Date(TimeManager.currentTimeMillis());
        this.version =
            System.getProperty("oracle.iot.client.version", "Unknown");
        this.fixedIpAddress =
            System.getProperty("com.oracle.iot.client.device.ip_address");
        this.fixedMacAddress =
            System.getProperty("com.oracle.iot.client.device.mac_address",
                               "Unknown");
    }

    @Override
    public ResponseMessage handleRequest(RequestMessage request)
            throws Exception {
        if (request == null) {
            throw new NullPointerException("Request is null");
        }

        if (request.getMethod() != null) {
            if ("GET".equals(request.getMethod().toUpperCase(Locale.ROOT))) {
                try {
                    NetworkInterface[] nif = new NetworkInterface[1];
                    JSONObject job = new JSONObject();
                        job.put("freeDiskSpace", getFreeDiskSpace());
                        
                        // diagnostics rest API definition currently only
                        // supports a single address, should consider extending
                        // this to include an array of addresses
                        // (for multiple interfaces)
                        if (fixedIpAddress == null) {
                            job.put("ipAddress", getIpAddress(nif));
                            job.put("macAddress", getMacAddress(nif[0]));
                        } else {
                            job.put("ipAddress", fixedIpAddress);
                            job.put("macAddress", fixedMacAddress);
                        }
                        job.put("startTime", getStartTimeMs());
                        job.put("totalDiskSpace", getTotalDiskSpace());
                        job.put("version", version);
                        String jsonValue = job.toString();
                        return getResponseMessage(request, jsonValue,
                                                  StatusCode.OK);
                } catch (Exception e) {
                    e.printStackTrace();
                    // Will pass through to return bad request.
                }

                return getBadRequestResponse(request);
            }
            return getMethodNotAllowedResponse(request);
        } else {
            return getNullRequestResponse(request);
        }
	}



    /**
     * @return the IP address of this device.
     */
    private String getIpAddress(NetworkInterface[] networkInterface) {
        InetAddress ipAddress = null;
        Enumeration<NetworkInterface> nis;

        try {
            nis = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException ignored) {
            return "Unknown";
        }

        interfaceLoop:
        while (nis.hasMoreElements()) {
            NetworkInterface itf = nis.nextElement();
            try {
                if (itf.isLoopback() || itf.isVirtual()) {
                    continue;
                }
            } catch (SocketException ignored) {
                continue;
            } 

            Enumeration<InetAddress> addresses = itf.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();

                // Even though the interface passed in will not be a
                // loopback interface, outgoing network interfaces can have
                // a loopback address
                if (!address.isLoopbackAddress()) {
                    if (!address.isLinkLocalAddress()) {
                        // !loopBack && !linkLocal = global
                        ipAddress = address;
                        networkInterface[0] = itf;
                        if (address instanceof Inet4Address) {
                            // prefer first global IPv4
                            break interfaceLoop;
                        }
                    } else if (ipAddress == null) {
                        ipAddress = address;
                        networkInterface[0] = itf;
                    } else if (ipAddress.isLinkLocalAddress() &&
                               address instanceof Inet4Address) {
                        // prefer IPv4
                        ipAddress = address;
                        networkInterface[0] = itf;
                    }
                }
            }
        }

        if (ipAddress == null) {
            return "Unknown";
        }

        return ipAddress.getHostAddress();
    }

    /**
     * Return the MAC address of the first non-virtual network interface.
     * <p>
     *     Note: on a device that has multiple network interfaces, it is not defined which MAC address is returned.
     * </p>
     * @return the MAC address of this device.
     */
    private String getMacAddress(NetworkInterface networkInterface) {
        if (networkInterface != null) {

            byte[] macAddress = null;
            try {
                macAddress = networkInterface.getHardwareAddress();
            } catch (SocketException ignored) {
            }

            if (macAddress != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < macAddress.length; i++) {
                    sb.append(String.format(Locale.ROOT, "%02X%s", macAddress[i], (i < macAddress.length - 1) ? "-" : ""));
                }
                return sb.toString();
            }
        }
        return "Unknown";   // "cannot retrieve."
    }
    
    /**
    * @return amount of free disk space.
    */
    protected abstract Long getFreeDiskSpace();
   
    /**
     *
     * @return the amount of total disk space.
     */
    protected abstract Long getTotalDiskSpace();

    /**
     * @return the start time in ms.
     */
    private Long getStartTimeMs() {
        return startTime.getTime();
    }

    private ResponseMessage getBadRequestResponse(RequestMessage requestMessage) {
        return getResponseMessage(requestMessage, "Unable to retrieve resource: state.", StatusCode.BAD_REQUEST);
    }

    private ResponseMessage getMethodNotAllowedResponse(RequestMessage requestMessage) {
        return getResponseMessage(requestMessage, "Unsupported request: " + requestMessage.toString(),
            StatusCode.METHOD_NOT_ALLOWED);
    }

    private ResponseMessage getNullRequestResponse(RequestMessage requestMessage) {
        return getResponseMessage(requestMessage, "Unsupported request.", StatusCode.METHOD_NOT_ALLOWED);
    }

    private ResponseMessage getResponseMessage(RequestMessage requestMessage, String body, StatusCode statusCode) {
        return new ResponseMessage.Builder(requestMessage)
            .body(body)
            .statusCode(statusCode)
            .build();
    }
}
