/*
 * Copyright (c) 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package com.oracle.iot.shared;

/**
 * ValueProvider
 */
public interface ValueProvider {
    /**
     * Provides an attribute or other property for $(propRef) in a formula.
     * @parameter key the value of propRef in a formula
     * @return value of the given propRef or null if not found
     */
    Object getCurrentValue(String key);

    /**
     * Provides an attribute or other property for $$(propRef) in a formula.
     * @parameter key the value of propRef in a formula
     * @return value of the given propRef or null if not found
     */
    Object getInProcessValue(String key);
}
