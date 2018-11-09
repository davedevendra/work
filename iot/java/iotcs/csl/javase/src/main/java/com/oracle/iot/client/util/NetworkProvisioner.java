/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.util;

import java.io.File;
import java.io.FileInputStream;
import java.net.*;
import java.util.HashSet;
import java.util.Set;

/**
 * The Network Provisioner application is used to discover the available clients 
 * that are waiting to be provisioned and send the provisioning information to 
 * the target client to enable it to complete provisioning. These requirements 
 * drive the need for two modes of operation: discovery and provisioning. When 
 * the Network Provisioner is started with no arguments, it runs in the 
 * discovery mode of operation. To discover the available clients, it send out a 
 * message to the predefined {@link Bootstrapper#MULTICAST_ADDRESS} and {@link Bootstrapper#UDP_PORT}
 * where prospective clients join the multicast group and wait for incoming 
 * messages. The Network Provisioner then waits for any responses from clients, 
 * and displays the identification information received from each unique client. 
 * Until the user exits, the Network Provisioner continues running and 
 * discovering new clients. 
 * <p>
 * When the Network Provisioner application runs in provisioning mode, it takes 
 * in the target client and provisioning information as arguments. It sends this 
 * information to the target client and waits for a response acknowledging 
 * successful or unsuccessful provisioning. If no response is received, the 
 * Network Provisioner retries several times before exiting. 
 * <p>
 * The provisioning information is in the unified provisioner format. This 
 * ensures that the provisioning data is sent to the Bootstrapper in encrypted 
 * form. 
 */
public class NetworkProvisioner {
    /*
     * 65,507 bytes is the practical limit for the data length which is
     * imposed the IPv4 protocol (65,535 = 8 byte UDP header = 20 byte
     * IP header).
     */
    static final int MAX_DATAGRAM_DATA = 65507;
    static final int DATAGRAM_OVERHEAD = 28;
    
    public static void main(String[] args) {
        boolean provision = true;
        
        if (args.length == 0) {
            provision = false;
        }
        else if (args.length != 2) {
            System.err.println("Incorrect arguments");
            showUsage();
            System.exit(-1);
        }
        try {
            if (!provision) {
                discover();
            } else {
                provision(args);
            }
        } catch (Exception e) {
            display(e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }
    }
    
    private static void discover() throws Exception {
        Set clientSet = new HashSet();
        DatagramSocket datagramSocket = new DatagramSocket();
        byte[] outBuffer = {Bootstrapper.DISCOVER_REQUEST};
        byte[] inBuffer =
            new byte[datagramSocket.getReceiveBufferSize()];
        InetAddress inetAddress =
            InetAddress.getByName(Bootstrapper.MULTICAST_ADDRESS);
        DatagramPacket outgoingPacket = new DatagramPacket(outBuffer, 
                outBuffer.length, inetAddress, Bootstrapper.UDP_PORT);
        DatagramPacket incomingPacket;

        display("\nWaiting for connections...");
        display("\n\tPress Enter to exit.\n");

        while (true) {
            //Send multicast to indicate discovery mode
            datagramSocket.send(outgoingPacket);

            //Receive client information from any available clients
            incomingPacket =
                new DatagramPacket(inBuffer, inBuffer.length);
            datagramSocket.setSoTimeout(500);
            try {
                datagramSocket.receive(incomingPacket);

                if (incomingPacket.getLength() < 3 ||
                    inBuffer[0] != Bootstrapper.DISCOVER_RESPONSE) {
                    continue;
                }

                DiscoverResponse dr =
                    parseDiscoverResponse(incomingPacket);

                String clientInfo = dr.key + " = " +
                    dr.value + ", IpAddress = " +
                    dr.address;

                //List unique clients
                if (clientSet.add(clientInfo)) {
                    display("Client: "+ clientInfo);
                }
            } catch (java.net.SocketTimeoutException ste) {}

            //check if user wants to exit.
            if (System.in.available() > 0) {
                datagramSocket.close();
                System.exit(0);
            }
        }
    }
    
    private static void provision(String[] args) throws Exception {
        InetAddress inetAddress = InetAddress.getByName(args[0]);

        DatagramSocket datagramSocket = new DatagramSocket();

        //write provisioning info
        display("Sending provisioning info." );
        int tries = 6;
        while(tries-- > 0) {
            sendProvisionRequest(datagramSocket, args[1],
                 inetAddress, Bootstrapper.UDP_PORT);

            byte[] inBuffer =
                new byte[datagramSocket.getReceiveBufferSize()];
            DatagramPacket incomingPacket =
                new DatagramPacket(inBuffer, inBuffer.length);

            //wait for ACK
            datagramSocket.setSoTimeout(5000);
            try {
                datagramSocket.receive(incomingPacket);
                int offset = 0;

                if (incomingPacket.getLength() < 2 ||
                    !(inBuffer[offset] ==
                     Bootstrapper.PROVISION_RESPONSE )) {
                    display("Client did not respond correctly. ");
                } else {
                    byte status = inBuffer[1];

                    if (status == Bootstrapper.SUCCESS) {
                        display("Client was successfully provisioned. ");
                    } else if (status == Bootstrapper.FAILURE) {
                        display("Client was not successfully provisioned. ");
                    } else {
                        display("Client did not respond correctly. ");
                    }
                }
                break;
            } catch (java.net.SocketTimeoutException ste) {
                if (tries == 0)
                    display("Provisioning timed out. ");
            }
        }
        datagramSocket.close();
    }

    /** Hold the values of parsed discover response. */
    private static class DiscoverResponse {
        String key;
        String value;
        String address;

        /**
         * Creates discover response.
         * @param key key/label for the value
         * @param value unique value/ID for the client
         * @param address IP address of client
         */
        DiscoverResponse(String key, String value,
                         String address) {
            this.key = key;
            this.value = value;
            this.address = address;
        }
    }

    /**
     * Creates a discover response from datagram.
     * @param datagram datagram from a client
     * @return discovery response
     * @throws Exception 
     */
    private static DiscoverResponse parseDiscoverResponse(
            DatagramPacket datagram) throws Exception {
        byte[] inBuffer = datagram.getData();

        int offset = 1;

        Bootstrapper.LV lv =
            Bootstrapper.decodeLengthValue(inBuffer, offset);
        String valueKey = lv.value;
        offset += lv.length;

        lv = Bootstrapper.decodeLengthValue(inBuffer, offset);
        String value = lv.value;

        return new DiscoverResponse(valueKey, value,
            datagram.getAddress().getHostAddress());
    }

    /**
     * Sends the provisioning information message.
     * @param socket The socket on which to send the message
     * @param fileName The UPF path
     * @param address The address to send to
     * @param port The port to send to
     * @throws Exception 
     */
    private static void sendProvisionRequest(DatagramSocket socket,
            String fileName,
            InetAddress address, int port) throws Exception {
        File file = new File(fileName);
        long fileLength = file.length();
        if (fileLength > (MAX_DATAGRAM_DATA + 3)) {
            throw new Exception("Provisioning file too large for a datagram");
        }
        
        long requestLength = fileLength + 3; // tag + 2 byte length
        long sendBufferSize = socket.getSendBufferSize();
        if ((requestLength + DATAGRAM_OVERHEAD) > sendBufferSize) {
            int nextBufferSize = (int)requestLength + DATAGRAM_OVERHEAD;
            socket.setSendBufferSize(nextBufferSize);
            if (socket.getSendBufferSize() < nextBufferSize) {
                throw new
                    Exception("Provisioning file too big for send buffer");
            }
        }

        byte[] request = new byte[(int)requestLength];
        request[0] = Bootstrapper.PROVISION_REQUEST;
        request[1] = (byte)(fileLength >> 8);
        request[2] = (byte)fileLength;

        FileInputStream fis = new FileInputStream(file);

        fis.read(request, 3, (int)fileLength); 

        DatagramPacket datagram = new DatagramPacket(request, request.length,
            address, port);
           
        socket.send(datagram);
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
        display("Usage: java " + NetworkProvisioner.class.getName() 
                + "\n\t[<Client host> <Provisioning file>] \n"
                + "\n\tWhere the Network Provisioner discovers available clients "
                + "\n\twhen no arguments are provided, and provisions the "
                + "\n\tselected client when all arguments are present. "
                + "\n\tWhere the Client host is the IP address of the "
                + "\n\tBootstrapper, displayed in discovery. "
                + "\n\tWhere the Provisioning file is the name of the file "
                + "\n\tcontaining the provisioning information in the unified "
                + "\n\tprovisioner format. "
                );
    }
}
