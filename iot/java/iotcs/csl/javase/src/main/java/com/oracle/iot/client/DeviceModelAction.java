/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client;

import oracle.iot.client.DeviceModel;

/**
 * DeviceModelAction is the model of an action in a {@link DeviceModel}.
 */
public abstract class DeviceModelAction {

    /**
     * Get the action name.
     * @return the action name from the device model
     */
    public abstract String getName();

    /**
     * A human friendly description of the action. If the model does not
     * contain a description, this method will return an empty String.
     * @return the action description, or an empty string
     */
    public abstract String getDescription();

    /**
     * The data type of the argument to the action. If the action does not
     * take an argument, then this method will return null.
     * @return the action argument's data type, or null
     */
    public abstract DeviceModelAttribute.Type getArgType();


    /**
     * For {@link DeviceModelAttribute.Type#NUMBER} and
     * {@link DeviceModelAttribute.Type#INTEGER} only, give the lower bound of the
     * acceptable range of values for the action's argument.
     * Null is always returned for actions other than {@code NUMBER}
     * or {@code INTEGER} type.
     *
     * @return a Number, or null if no lower bound has been set
     */
    public abstract Number getLowerBound();

    /**
     * For {@link DeviceModelAttribute.Type#NUMBER} and
     * {@link DeviceModelAttribute.Type#INTEGER} only, give the upper bound of the
     * acceptable range of values for the action's argument.
     * Null is always returned for actions other than {@code NUMBER}
     * or {@code INTEGER} type.
     *
     * @return a Number, or null if no upper bound has been set
     */
    public abstract Number getUpperBound();

    /**
     * @return an alternative name for the attribute
     * @deprecated Use {@link #getName()}
     */
    @Deprecated
    public abstract String getAlias();

    protected DeviceModelAction() {}

}
