/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */
package oracle.iot.client.enterprise;

import java.util.Map;

/**
 * An {@code Application} lists the characteristics of an IoT CS application.
 *
 * @see EnterpriseClient#getApplication()
 */
public abstract class Application {

    /**
     * Get the application identifier
     *
     * @return the identifier of the application
     */
    public abstract String getId();

    /**
     * Get the name of the application
     *
     * @return the name of the application
     */
    public abstract String getName();

    /**
     * Get the free form description of the application
     *
     * @return the description of the application
     */
    public abstract String getDescription();

    /**
     * Get the application Metadata for the specified {@code key}.
     * This is shorthand for calling {@code getMetadata().get(key);}.
     *
     * @param key the metadata to look for
     * @return the value corresponding to the specified {@code key}
     */
    public abstract String getMetadata(String key);

    /**
     * Get the application Metadata
     *
     * @return a Map associating keys and values
     */
    public abstract Map<String, String> getMetadata();

}
