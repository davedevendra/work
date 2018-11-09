/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

/**
 * API for handling trust material used for activation and authentication
 * to the Oracle IoT Cloud Service.
 * The {@code TrustedAssetsManager} API is not a general purpose crypto or trust managementAPI;
 * it is fit-for-purpose and only contains the classes and methods strictly
 * necessary for handling the trust material and operations of registration,
 * activation and authentication with the IoT CS.
 * <p>
 * An implementation of the {@code TrustedAssetsManager} interface handles the trust
 * material and operations of registration, activation and authentication with
 * the IoT CS and should additionally handle the provisioning of the trust material.
 * </p>
 */
package com.oracle.iot.client.trust;
