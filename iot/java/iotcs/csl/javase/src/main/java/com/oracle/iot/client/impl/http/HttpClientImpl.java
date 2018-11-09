/*
 * Copyright (c) 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.http;

import javax.net.ssl.SSLSocketFactory;
import java.net.MalformedURLException;
import java.net.URL;

/**
 */
public final class HttpClientImpl extends HttpClient {

	static {
		setTransport(new Transport());
	}

	public static void setTransport(Transport tr) {
		transport = tr;
	}

	public HttpClientImpl(SSLSocketFactory sslSocketFactory, URL url) throws MalformedURLException {
		super(sslSocketFactory, new URL(null, url.toExternalForm(), new sun.net.www.protocol.https.Handler()));
	}

}
