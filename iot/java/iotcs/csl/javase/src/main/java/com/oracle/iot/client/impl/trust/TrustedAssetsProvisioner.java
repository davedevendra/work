/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.trust;

import java.io.IOException;

import java.security.GeneralSecurityException;

public class TrustedAssetsProvisioner extends TrustedAssetsProvisionerBase {
    private final static String STORE_EXTENSION = "jks";

    /**
     * Runs the {@code TrustedAssetsProvisioner} tool with the provided
     * arguments.
     *
     * @param args
     *            the arguments (see the description above for the supported
     *            options).
     */
    public static void main(String[] args) {
        main(args, STORE_EXTENSION);
    }

    /**
     * This method for unit tesing provisions does what {@code main} does,
     * accept it does not call {@code System.exit}
     * 
     * @param args the arguments (see the description above for the supported
     *            options).
     * @return the file name of the created trusted assets store.
     * @throws IllegalArgumentException if an illegal or unsupported
     *                  parameter/option is passed.
     * @throws GeneralSecurityException if any security-related error occurred
     *          during provisioning.
     * @throws IOException if any I/O-related error occurred during
     *   provisioning.
     */
    public static String provision(String... args) throws IllegalArgumentException,
            GeneralSecurityException, IOException {
        return provision(args, STORE_EXTENSION);
    }
}
