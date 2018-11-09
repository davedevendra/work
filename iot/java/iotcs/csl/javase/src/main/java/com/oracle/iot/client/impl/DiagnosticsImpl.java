/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 */

package com.oracle.iot.client.impl;

import com.oracle.iot.client.impl.Diagnostics;
import java.io.File;

/**
 * Message handler for device diagnostics.
 */
public class DiagnosticsImpl extends Diagnostics {
    
    public DiagnosticsImpl() {
        super();
    } 
	
	 /**
    * @return amount of free disk space.
    */	
    protected Long getFreeDiskSpace() {
        File file = new File(".");
        return file.getFreeSpace();
    }
   
    /**
     *
     * @return the amount of total disk space.
     */
    protected Long getTotalDiskSpace() {
        File file = new File(".");
        return file.getTotalSpace();
    }
  
}
