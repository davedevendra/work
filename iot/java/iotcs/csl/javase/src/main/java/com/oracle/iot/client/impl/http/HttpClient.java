/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.http;

import com.oracle.iot.client.HttpResponse;

import com.oracle.iot.client.RestApi;
import com.oracle.iot.client.message.Message;
import oracle.iot.client.enterprise.UserAuthenticationException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 */
abstract public class HttpClient {

	private final URL url;

	private static final int DEFAULT_RESPONSE_TIMEOUT = 15000; // milliseconds
	private static final int responseTimeout;
	static {
		int value = DEFAULT_RESPONSE_TIMEOUT;
		try {
			value = Integer.getInteger(
					"oracle.iot.client.http_response_timeout",
					DEFAULT_RESPONSE_TIMEOUT);
		} catch (SecurityException e) {
			// use default value
			value = DEFAULT_RESPONSE_TIMEOUT;
		} finally {
			responseTimeout = value > 0 ? value : 0;
		}
	}

	// Static variable for injection transport implementation in unit tests
	// without real server. When not used in unit tests, variable is set from
	// HttpClientImpl class.
	static Transport transport;

	/**
	 * Get the HTTP response timeout.
	 * The timeout The default value is 15000 milliseconds and can be set
	 * with the {@code oracle.iot.client.http_response_timeout} property.
	 * @return the HTTP response timeout
     */
	public static int getResponseTimeout() {
		return responseTimeout;
	}

	/**
	 * Transport interface provides hook into HTTPS connection,
	 * or can be stubbed out to allow for unit tests without real server.
	 * The post/put/get/delete methods call this interface.
	 */
	static public class Transport {

		// TODO: this was made public for the mock server unit tests - make this protected again once every test uses mock server.
		public Transport() {
		}

		/*
		 * Timeout (in milliseconds) is passed to
		 * HttpsUrlConnection.setReadTimeout in which a
		 * timeout of zero is interpreted as an infinite timeout.
		 */
        public HttpResponse invokeMethod(String method,
                                         SSLSocketFactory sslSocketFactory, byte[] data,
                                         Map<String, String> headers, URL url, int timeout)
                throws IOException, GeneralSecurityException {

            HttpsURLConnection con = null;
            HttpResponse response = null;

            // If we get a SocketException, we'll retry once.
            // We have to get new HttpsURLConnection for the retry in order
            // to get a new input/output stream.
            int retries = 0;
            while (retries < 2) {
                try {
                    // Create the connection object
                    con = (HttpsURLConnection) url.openConnection();
                    con.setSSLSocketFactory(sslSocketFactory);

                    if (RestApi.V2.isWebApi())
                        con.setInstanceFollowRedirects(false);
                    else
                        con.setInstanceFollowRedirects(true);

                    con.setConnectTimeout(timeout);
                    con.setReadTimeout(timeout);

                    con.setRequestMethod(method);

                    // Add request headers (caller-supplied first, so we overwrite any
                    // collisions)
                    if (headers != null) {
                        for (Map.Entry<String, String> entry : headers.entrySet()) {
                            con.setRequestProperty(entry.getKey(), entry.getValue());
                        }
                    }
                    beforeConnect(con);

                    if (data != null) {
                        // Send post request
                        con.setDoOutput(true);
                        con.setFixedLengthStreamingMode(data.length);
                    }


                    con.connect();

                    if (data != null) {
                        pipe(new ByteArrayInputStream(data), con.getOutputStream());
                    }

                    // Check if redirection is requested
                    int responseCode = con.getResponseCode();

                    // Validate the connection
                    if (RestApi.V2.isWebApi() && (responseCode == 302 || responseCode == 301))
                        throw new UserAuthenticationException("User Authentication failed!", con.getHeaderField("Location"));

                    // Read the response
                    Map<String, List<String>> responseHeaders = con.getHeaderFields();
                    byte[] responseData = getResponseBody(con,
                            (responseCode >= 200 && responseCode < 300) ? con.getInputStream() : con.getErrorStream());

                    response = new HttpResponse(responseCode, responseData, responseHeaders);
                    break;

                } catch (SocketException se) {
                    // Connection may have been reset - try again
                    if (++retries == 1) {
                        getLogger().log(Level.FINEST, "Received '" + se.toString() +
                                "'. Retry " + method + " " + url.getPath());
                        continue;
                    }
                    throw se;
                }
                /* Do not disconnect, it defeats java's socket reuse
                finally {
                    con.disconnect();
                }
                */
            }


            if (getLogger().isLoggable(Level.FINEST)) {

                final StringBuilder builder = new StringBuilder();
                builder.append(method)
                        .append(' ')
                        .append(url.getPath())
                        .append(' ');
                if (data != null) {
                    builder.append(Message.prettyPrintJson(data));
                }
                builder.append(" => response: ").append(response);

                getLogger().log(Level.FINEST, builder.toString());
            }

            return response;
        }

        // Give implementation an opportunity to modify the connection before
        // the URLConnection {@code connect()} method is called.
        protected void beforeConnect(HttpsURLConnection connection)
                throws IOException, GeneralSecurityException {
            // Override as needed
        }
    }


	private final SSLSocketFactory sslSocketFactory;

	protected HttpClient(SSLSocketFactory sslSocketFactory, String url) throws MalformedURLException {
		this(sslSocketFactory, new URL(url));
	}

	protected HttpClient(SSLSocketFactory sslSocketFactory, URL url) {
		if (url == null) {
			throw new IllegalArgumentException("url cannot be null");
		}
		this.url = url;
		this.sslSocketFactory = sslSocketFactory;
	}

	public final HttpResponse post(byte[] data, Map<String, String> headers)
		throws IOException, GeneralSecurityException {

		return post(data, headers, getResponseTimeout());
	}

	/*
	 * Timeout (in milliseconds) is need only for the HttpClient which
	 * passes the timeout to HttpsUrlConnection.setReadTimeout, in which 
	 * timeout of zero is interpreted as an infinite timeout.
	 *
	 * However, if a negative value is given, it will be converted to be the
	 * default response timeout.
	 */
	public final HttpResponse post(byte[] data, Map<String, String> headers,
		int timeout) throws IOException, GeneralSecurityException {

		if (timeout < 0) {
			timeout = getResponseTimeout();
		}
		headers.put("Content-Length", (data != null ? Integer.toString(data.length) : "0"));
		return transport.invokeMethod("POST", sslSocketFactory, data,
			headers, url, timeout);
	}

	public final HttpResponse put(byte[] data, Map<String, String> headers) throws IOException, GeneralSecurityException {
		headers.put("Content-Length", (data != null ? Integer.toString(data.length) : "0"));
		return transport.invokeMethod("PUT", sslSocketFactory, data, headers, url, getResponseTimeout());
	}

	public final HttpResponse get(Map<String, String> headers) throws IOException, GeneralSecurityException {
		return transport.invokeMethod("GET", sslSocketFactory, null, headers, url, getResponseTimeout());
	}

	public final HttpResponse delete(Map<String, String> headers) throws IOException, GeneralSecurityException {
		return transport.invokeMethod("DELETE", sslSocketFactory, null, headers, url, getResponseTimeout());
	}

	protected static void pipe(InputStream in, OutputStream out) throws IOException {
		// Setup so that if in or out is null but not both, then the one that
		// isn't null will end up being closed.
		// Otherwise any failure ends up as an IOException but the streams are
		// still closed
		try {
			if (in != null && out != null) {
				final byte[] buffer = new byte[8096];
				int length;
				while ((length = in.read(buffer)) != -1) {
					out.write(buffer, 0, length);
				}
			}
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException ioe) {
				}
			}
			if (out != null) {
				try {
					out.close();
				} catch (IOException ioe) {
				}
			}
		}
	}

	protected static byte[] getResponseBody(HttpURLConnection http, InputStream responseStream) throws IOException {
		// If this is GZIP encoded, then wrap the input stream
		final String contentEncoding = http.getContentEncoding();
		if ("gzip".equals(contentEncoding)) {
			responseStream = new GZIPInputStream(responseStream);
		} else if ("deflate".equals(contentEncoding)) {
			responseStream = new InflaterInputStream(responseStream);
		}
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		pipe(responseStream, body);
		return body.toByteArray();
	}

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }   
}
