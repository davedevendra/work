/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package oracle.iot.client;

/**
 * Detailed information on a device model. A device model is a specification
 * of the attributes, formats, and resources available on the device.
 */
public abstract class DeviceModel {

    /**
     * Get the URN of the device model
     *
     * @return the URN of the model
     */
    public abstract String getURN();

    /**
     * Get the name of the device model
     * @return the device model name
     */
    public abstract String  getName();

    /**
     * Get the free form description of the device model
     *
     * @return the description of the model
     */
    public abstract String  getDescription();


    protected DeviceModel() {}
}
