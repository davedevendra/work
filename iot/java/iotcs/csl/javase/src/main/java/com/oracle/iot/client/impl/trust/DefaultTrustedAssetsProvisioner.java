/*
 * Copyright (c) 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.trust;

/**
 * The {@code DefaultTrustedAssetsManagerProvisioner} tool allows for
 * provisioning trust
 * assets. This tool is to be run to create a trusted assets store file that is
 * used by the {@link DefaultTrustedAssetsManager}. A trusted assets store file
 * needs to be created for directly connected devices, gateway devices and
 * enterprise applications prior to connecting to the IoT CS. The tool creates a
 * key store file with the name {@code {@quote <}clientID{@quote >}.upf}.
 * The tool will not overwrite an existing file.
 */
public class DefaultTrustedAssetsProvisioner extends
        DefaultTrustedAssetsProvisionerBase {
    /**
     * Runs the {@code InteractiveProvisioner} tool with the provided
     * arguments.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        main(args, "", "upf", true);
    }

    /**
     * Runs the {@code InteractiveProvisioner} tool with the provided
     * arguments.
     *
     * @param args command line arguments
     * @param workDir the working directory
     */
    public static void main(String[] args, String workDir) {
        main(args, workDir, "upf", true);
    }

}
