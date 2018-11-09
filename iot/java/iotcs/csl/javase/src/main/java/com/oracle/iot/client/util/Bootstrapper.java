/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.util;

import com.oracle.iot.client.trust.TrustedAssetsManager;
import com.oracle.iot.client.trust.TrustException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.PublicKey;
import java.util.Enumeration;
import java.util.Vector;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;

/**
 * The Bootstrapper application is used to complete the provisioning for a 
 * an unprovisioned client. With the trusted assets store name and the 
 * trusted assets store password, the Bootstrapper 
 * checks to see if the asset store is already provisioned or not. In the 
 * case it is already provisioned, the Bootstrapper goes ahead and starts 
 * the sample application identified in the first argument. It passes all 
 * subsequent arguments on to the sample application during invocation. 
 * <p>
 * In the case the client is not already provisioned, the Bootstrapper 
 * joins the UDP multicast group at the predefined address 
 * {@link #MULTICAST_ADDRESS} and port {@link #UDP_PORT} to wait for a message 
 * from the Network Provisioner for discovery or provisioning. If the message 
 * received is for discovery {@link #DISCOVER_REQUEST}, the Bootstrapper sends 
 * back the client information. By default, the information is the client's MAC 
 * address. This information will be displayed by the Network Provisioner to the 
 * operator to select the target client. The information is sent as a key value 
 * pair, where key="MAC" and value is the MAC address. If different information 
 * is desired from the client, the {@link #getDeviceInfo()} method should be 
 * modified to return the desired data. 
 * <p>
 * If the message received is for provisioning {@link #PROVISION_REQUEST}, the 
 * Bootstrapper saves and verifies the provisioning data. 
 * The result of the provisioning is sent back to the Network 
 * Provisioner. If the provisioning was successful, the Bootstrapper continues 
 * to start the sample application as described previously. If the provisioning 
 * was unsuccessful, the Bootstrapper waits until another provisioning attempt 
 * is made. 
 * <p>
 * The provisioning file should be in the unified provisioner format so that 
 * the provisioning data is sent in encrypted form. 
 */
public class Bootstrapper {
    /**
     * The port to listen on for messages from the Network Provisioner.
     */
    static final int UDP_PORT = 4456;
    
    /**
     * The address to which the Network Provisioner sends multicast messages.
     */
    static final String MULTICAST_ADDRESS = "238.163.7.96";
    
    /**
     * Message type: Network Provisioner to Bootstrapper- request for client identification information.
     */
    static final byte DISCOVER_REQUEST = 0x01;
    
    /**
     * Message type: Bootstrapper to Network Provisioner- client identification information.
     */
    static final byte DISCOVER_RESPONSE = 0x02;
    
    /**
     * Message type: Network Provisioner to Bootstrapper- provisioning information
     */
    static final byte PROVISION_REQUEST = 0x03;
    
    /**
     * Message type: Bootstrapper to Network Provisioner- response provisioning status.
     */
    static final byte PROVISION_RESPONSE = 0x04;

    /**
     * Status: Successful result
     */
    static final byte SUCCESS = 0x00;
    
    /**
     * Status: Failed result
     */
    static final byte FAILURE = 0x01;
    
    /**
     * Status: Unknown result
     */
    static final byte UNKNOWN = 0x02;
    
    /**
     * Charset for the provisioning protocol.
     */
    static final Charset UTF_8 = Charset.forName("UTF-8");

    /*
     * 65,507 bytes is the practical limit for the data length which is
     * imposed the IPv4 protocol (65,535 = 8 byte UDP header = 20 byte
     * IP header).
     */
    static final int MAX_DATAGRAM_PACKET = 65535;
    static final int MAX_DATAGRAM_DATA = 65507;
    
    private static String taStore; 
    private static String taStorePassword;

    public static void main(String[] args) {
        if (args.length < 3) {
            display("\nIncorrect number of arguments.\n");
            showUsage();
            System.exit(-1);
        }
        taStore = args[0];
        taStorePassword = args[1];
        
        try {        
            //Is device already provisioned?
            if (!isProvisioned(taStore, taStorePassword)) {
                listStore(taStore, taStorePassword);
                
                //Multicast to see devices waiting, select the desired one.
                MulticastSocket multicastSocket = new MulticastSocket(UDP_PORT);
                InetAddress inetAddress =
                    InetAddress.getByName(MULTICAST_ADDRESS);

                //join the UDP multicast group
                multicastSocket.joinGroup(inetAddress);
                display("Joined multicast group.");
                
                if (multicastSocket.getReceiveBufferSize() <
                        MAX_DATAGRAM_PACKET) {
                    /*
                     * Try to get the maximum packet size, but don't stop
                     * it cannot be set, the provisioning info may fit in
                     * the current receive buffer.
                     */
                    multicastSocket.setReceiveBufferSize(MAX_DATAGRAM_PACKET);
                }

                DatagramPacket datagramPacket;
                byte[] receivedData = new byte[MAX_DATAGRAM_DATA];
                byte[] outBuffer = 
                    new byte[multicastSocket.getSendBufferSize()];

                //wait for notification from provisioner
                while (true) {
                    datagramPacket =
                        new DatagramPacket(receivedData, receivedData.length);

                    multicastSocket.receive(datagramPacket);

                    int length = datagramPacket.getLength();
                    
                    if (length < 1)
                        continue;

                    //Discover or Provision?
                    if (receivedData[0] == DISCOVER_REQUEST) {
                        handleDiscoverRequest(multicastSocket,
                            outBuffer, datagramPacket.getAddress(),
                            datagramPacket.getPort());
                    } else if (receivedData[0] == PROVISION_REQUEST) {
                        if (handleProvisionRequest(multicastSocket,
                                receivedData, 1, length,
                                datagramPacket.getAddress(),
                                datagramPacket.getPort())) {
                            // successful provision
                            break;
                        }

                        display("Waiting for provisioning info.");
                    }
                }
                
                multicastSocket.leaveGroup(inetAddress);
                multicastSocket.close();
            }
            
            listStore(taStore, taStorePassword);
            
            //Start the device app
            int appArgsLen = args.length - 1;
            String[] appArgs = new String[appArgsLen];
            System.arraycopy(args, 0, appArgs, 0, 2);
            System.arraycopy(args, 3, appArgs, 2, appArgsLen - 2);
            startApp(args[2], appArgs);             
            
        } catch (Exception e) {
            display(e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }
    }
    
    /**
     * Check trusted asset store to determine if asset is already provisioned
     * @param taStore trusted asset store file name
     * @param taStorePassword trusted asset store password
     * @return true if it is already provisioned
     *  false otherwise
     * @throws TrustException  if any error occurs opening and loading the file
     */
    private static boolean isProvisioned(String taStore, 
            String taStorePassword) throws GeneralSecurityException {
        
        if (! new File(taStore).exists())
            return false;
        
        TrustedAssetsManager trustedAssetsManager =
            getTam(taStore, taStorePassword);

        String deviceID = null;
        String serverHost = null;
        try {
            serverHost = trustedAssetsManager.getServerHost();
            deviceID = trustedAssetsManager.getClientId();
        } catch (IllegalStateException e) {
        }

        if (serverHost == null || deviceID == null)
            return false;
        return true;
    }

    /**
     * Sends the discovery response message.
     * @param socket The socket on which to send the message
     * @param outBuffer output buffer
     * @param address The address to send to
     * @param port The port to send to
     * @throws IOException if an I/O error occurs
     */
    private static void handleDiscoverRequest(DatagramSocket socket,
            byte[] outBuffer, InetAddress address, int port) throws IOException {
        //send back client identification info
        DeviceInfo clientInfo = getDeviceInfo();

        outBuffer[0] = DISCOVER_RESPONSE;

        int offset = 1;
        
        offset += encodeLengthValue(clientInfo.key, outBuffer, offset);
        
        int length = offset + encodeLengthValue(clientInfo.value,
            outBuffer, offset);

        socket.send(new DatagramPacket(outBuffer, length, address, port));
    }

    /**
     * Completes the provisioning of the device.
     * @param socket The socket on which to send the message
     * @param request provision request
     * @param offset offset into the request buffer where the request starts
     * @param length length of the provision request
     * @param address The address to send to
     * @param port The port to send to
     * @return true if successful
     * @throws IOException if an I/O error occurs
     */
    private static boolean handleProvisionRequest(DatagramSocket socket,
            byte[] request, int offset, int length, InetAddress address,
            int port) throws IOException {
        //Check for minimum possible valid length
        if (length < 5){
            display("\tProvisioning was unsuccessful, \n" +
                    "\tformat was not correct.\n");
            sendProvisionResponse(socket, FAILURE, address, port);
            return false;
        }

        try {
            //store provisioned info into trusted assets
            display("Provisioning...");

            length -= offset;
            int requestLen = (request[offset++] & 0x000000FF) << 8;
            length--;
            requestLen += request[offset++] & 0x000000FF;
            length--;
            if (requestLen != length) {
                display("Provisioning information too short");
                sendProvisionResponse(socket, FAILURE, address, port);
                return false;
            }

            updateStore(taStore, request, offset);

            if (isProvisioned(taStore, taStorePassword)) {
                display("\tSuccessfully provisioned.\n");
                sendProvisionResponse(socket, SUCCESS, address, port);
                return true;
            }
        } catch (IndexOutOfBoundsException e) {
            display("\tProvisioning was unsuccessful, \n" +
                "\tformat was not correct.\n");
            sendProvisionResponse(socket, FAILURE, address, port);
            return false;
        } catch (Throwable t) {
            // fall through
        }

        display("\tProvisioning was unsuccessful.\n");
        sendProvisionResponse(socket, FAILURE, address, port);
        return false;
    }

    /**
     * Sends the result status response message.
     * @param socket The socket on which to send the message
     * @param status The response status to send
     * @param address The address to send to
     * @param port The port to send to
     * @throws IOException if an I/O error occurs
     */
    private static void sendProvisionResponse(DatagramSocket socket,
            byte status, InetAddress address, int port)
            throws IOException {
        byte[] response;

        response = new byte[] {PROVISION_RESPONSE, status};

        DatagramPacket datagram = new DatagramPacket(response, response.length,
            address, port);
        socket.send(datagram);
    }

    /**
     * Gets the TAM.
     * @param taStore trusted asset store file name
     * @param taStorePassword trusted asset store password
     * @return the TAM
     * @throws TrustException  if any error occurs opening and loading the file
     */
    private static TrustedAssetsManager getTam(String taStore,
            String taStorePassword) throws GeneralSecurityException {
        return TrustedAssetsManager.Factory.getTrustedAssetsManager(taStore, 
                taStorePassword, null);
    }
    
    /**
     * Completes provisioning.
     * @param taStore trusted asset store file name
     * @param buff data to store
     * @param offset offset into buff to start
     * @throws TrustException  if any error occurs loading or writing the file
     */
    private static void updateStore(String taStore,
            byte[] buff, int offset) throws TrustException {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(taStore);
            fos.write(buff, offset, buff.length - offset);
            fos.flush();
        } catch (FileNotFoundException fnfe) {
            display("Error writing trusted assets store file. ");
        } catch (IOException ioe ) {
            display("Error writing trusted assets store file. ");
        }finally {
            try {
                if (fos != null)
                    fos.close();
            } catch (IOException ioe) {
            }
        }
    }
    
    /**
     * Starts the sample application. 
     * @param name full class name of the sample application to start
     * @param args arguments to pass on to the sample application
     * @throws ClassNotFoundException if the class cannot be located
     * @throws NoSuchMethodException  if the "main" method is not found
     * @throws IllegalAccessException if the Method object enforces Java
     *       language access control and the "main" is inaccessible
     * @throws InvocationTargetException if the "main" method throws an
     *                                   exception
     */
    public static void startApp(String name, String[] args)
            throws ClassNotFoundException, NoSuchMethodException,
            IllegalAccessException, InvocationTargetException {
        display("\nStarting application "+name+"...");
        Class c = Class.forName(name);
        Method m = c.getMethod("main", String[].class);
        m.invoke(null, new Object[] {args});
    } 
    
    /**
     * Lists the information in the trusted assets store. 
     * @param taStore trusted asset store file name
     * @param taStorePassword trusted asset store password
     * @throws TrustException  if any error occurs loading or writing the file
     */
    private static void listStore(String taStore, String taStorePassword)
            throws GeneralSecurityException {
        
        if (! new File(taStore).exists())
            return;
        
        TrustedAssetsManager tam = getTam(taStore, taStorePassword);

        display("Trusted assets store: " + taStore);
        try {
            display(" Server scheme: " + tam.getServerScheme());
        } catch (IllegalStateException ise) {
            display(" Server scheme not set");
        }
        
        try {
            display(" Server host: " + tam.getServerHost());
        } catch (IllegalStateException ise) {
            display(" Server host not set");
        }

        final String serverPort;
        if (-1 == tam.getServerPort()) {
            if ("https".equals(tam.getServerScheme())) serverPort = "443";
            else if ("mqtts".equals(tam.getServerScheme())) serverPort = "8883";
            else serverPort = "Server port not set";
        } else {
             serverPort = Integer.toString(tam.getServerPort());
        }
        display(" Server port: " + serverPort);

        try {
            display(" Client ID: " + tam.getClientId());
        } catch (IllegalStateException ise) {
            display(" Client ID not set");
        }

        try {
            display(" Endpoint ID: " + tam.getEndpointId());
        } catch (IllegalStateException ise) {
            display(" Endpoint ID not set");
        }

        try {
            PublicKey key = tam.getPublicKey();
            display(" Public key is set");
        } catch (IllegalStateException ise) {
            display(" Public key not set");
        }

        try {
	    Vector<byte[]> certs = tam.getTrustAnchorCertificates();
            display(" Trust anchor certificates are set");
        } catch (IllegalStateException ise) {
            display(" Trust anchor certificates not are set");
        }
    }

    /**
     * Holds a LV byte length and string value. 
     */
    static class LV {
        final int length;
        final String value;

        LV (int byteLength, String value) {
            this.length = byteLength;
            this.value = value;
        }
    }

    /**
     * Decode a length value pair (LV) into a string.
     *
     * @param buffer input buffer
     * @param offset where the LV starts in the buffer
     *
     * @return decoded byte length and string value
     *
     * @throws IndexOutOfBoundsException if offset plus length of the value is
     *                                   greater than the buffer length
     */
    static LV decodeLengthValue(byte[] buffer, int offset)
            throws IndexOutOfBoundsException {
        int length = buffer[offset++] & 0x000000FF;

        return new LV(length + 1,
            UTF_8.decode(ByteBuffer.wrap(buffer, offset, length)).toString());
    }

    /**
     * Encode a string into a length value pair (LV).
     *
     * @param value input string
     * @param buffer output buffer
     * @param offset where the LV should start in the buffer
     *
     * @return nend of value in the buffer
     *
     * @throws IndexOutOfBoundsException if offset plus UTF-8 length of the
     *                                   value is greater than the buffer length
     */
    static int encodeLengthValue(String value, byte[] buffer, int offset)
            throws IndexOutOfBoundsException {
        byte[] bytes = UTF_8.encode(value).array();

        if (bytes.length > 255) {
            throw new
                IndexOutOfBoundsException("value encodes to over 255 bytes");
        }

        buffer[offset++] = (byte)bytes.length;

        System.arraycopy(bytes, 0, buffer, offset, bytes.length);

        return bytes.length + 1;
    }

    /**
     * Change this method if MAC address is not desired as the device
     * information.
     * Returns the device information with which to identify it.
     * @return device information
     */
    private static DeviceInfo getDeviceInfo() {
        try {
            String key = System.getProperty(
                "oracle.iot.client.discovery_key", "MAC");
            String value = System.getProperty(
                "oracle.iot.client.discovery_value", getMacAddress());

            return new DeviceInfo(key, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the MAC address.
     * @return the MAC address
     * @throws SocketException if an IO error occurs
     */
    private static String getMacAddress() throws SocketException {
        NetworkInterface networkInterface = null;
        Enumeration<NetworkInterface> nis;

        nis = NetworkInterface.getNetworkInterfaces();

        while (nis.hasMoreElements()) {
            NetworkInterface itf = nis.nextElement();

            if (itf.isLoopback() || itf.isVirtual() || !itf.isUp()) {
                continue;
            }

            networkInterface = itf;
        }

        if (networkInterface != null) {
            byte[] macAddress = null;

            macAddress = networkInterface.getHardwareAddress();

            if (macAddress != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < macAddress.length; i++) {
                    sb.append(String.format("%02X%s", macAddress[i],
                        (i < macAddress.length - 1) ? "-" : ""));
                }
                return sb.toString();
            }
        }

        return "Unknown";   // "cannot retrieve."
    }

    /**
     * Displays the input string
     * @param string string to display
     */
    private static void display(String string) {
        System.out.println(string);
    }
    
    /**
     * Displays the correct usage.
     */
    private static void showUsage() {
        display("Usage: \n\n"
                + "java " + Bootstrapper.class.getName()
                + "\n\t<trust assets file> <trust assets password>"
                + " <app class name>"
                + " [<app arg 0>...<app arg 8>]"
                + "\n\n"
                + "Where the app class name is the name of the application "
                + "to start. "
                + "Where the trust assets store file and trust assets password "
                + "are also passed on to the application as the first two "
                + "parameters. "
                + "Where <app arg 0>...<app arg 8> are any optional arguments "
                + "to be passed onto the application when started.\n");
    }

    /**
     * Device information to hold the identifying key and value. 
     */
    private static class DeviceInfo {
        String key;
        String value;

        DeviceInfo(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
