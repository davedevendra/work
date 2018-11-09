/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.message;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link MessageParsingException} when an incorrect JSON interpretation of any {@link Message} is being parsed.
 */
public class MessageParsingException extends RuntimeException {

    private static final long serialVersionUID = -5061935851535283451L;

    /**
     * This code is assigned if there is unknown error.
     */
    private final static int UNKNOWN_ERROR_CODE = 1000;

    /**
     * This code is assigned if the message type is not supported.
     */
    public final static int INVALID_MSG_TYPE = 1019;

    /**
     * This variable is used for holding errorCode.
     */
    private final int errorCode;

    /**
     * Constructs a new runtime exception with the specified detail message. The cause is not initialized,
     * and may subsequently be initialized by a call to {@link Throwable#initCause(java.lang.Throwable)}.
     *
     * @param message The detail message. The detail message is saved for later retrieval by
     *                the {@link Throwable#getMessage()} method.
     */
    public MessageParsingException(String message) {
        super(exceptionText.get(message) != null ? exceptionText.get(message).getDescription() : message);
        DescriptionAndErrCodeMapping map = exceptionText.get(message);
        if (map != null) {
            this.errorCode = map.getErrCode();
        }
        else {
            this.errorCode = UNKNOWN_ERROR_CODE;
        }
    }

    /**
     * Constructs a new runtime exception with the specified cause.
     * Note that the detail message associated with cause is not automatically
     * incorporated in this runtime exception's detail message.
     *
     * @param cause  the cause (which is saved for later retrieval by
     *               the {@link Throwable#getCause()} method).
     *               (A {@code null} value is permitted, and indicates that
     *               the cause is nonexistent or unknown.)
     */
    public MessageParsingException(Throwable cause) {
        this("message.parsing.unknown", cause);
    }

    /**
     * Constructs a new runtime exception with the specified detail message and cause.
     * Note that the detail message associated with cause is not automatically
     * incorporated in this runtime exception's detail message.
     *
     * @param message The detail message. The detail message is saved for later retrieval by
     *                the {@link Throwable#getMessage()} method.
     * @param cause   the cause (which is saved for later retrieval by the {@link Throwable#getCause()} method).
     *                (A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public MessageParsingException(String message, Throwable cause) {
        super(exceptionText.get(message) != null ? exceptionText.get(message).getDescription() : message, cause);
        DescriptionAndErrCodeMapping map = exceptionText.get(message);
        if (map != null) {
            this.errorCode = map.getErrCode();
        }
        else {
            this.errorCode = UNKNOWN_ERROR_CODE;
        }
    }

    /**
     * Method returns code for the current error.
     * @return Error code.
     */
    public int getErrorCode() {
        return errorCode;
    }

    // Associative array that maps short exception test to full text of the exception
    private static final Map<String, DescriptionAndErrCodeMapping> exceptionText;

    /* For testing. Returns a new, unmodifiable collection each time it is called */
    static Map<String, DescriptionAndErrCodeMapping> getExceptionText() {
        return Collections.unmodifiableMap(exceptionText);
    }
    // Initialization af the mapping of exception texts.
    static {
        exceptionText = new HashMap<String, DescriptionAndErrCodeMapping>();
        exceptionText.put("message.structure.null",             new DescriptionAndErrCodeMapping("Message: received json has wrong format", 1001));
        exceptionText.put("message.parsing.unknown",            new DescriptionAndErrCodeMapping("Message: exception during parsing", 1002));
        exceptionText.put("message.byteArray.null",             new DescriptionAndErrCodeMapping("Message: trying to parse null byte array", 1003));
        exceptionText.put("message.jsonString.null",            new DescriptionAndErrCodeMapping("Message: trying to parse null Json string", 1004));
        exceptionText.put("message.id.null",                    new DescriptionAndErrCodeMapping("Message Common Fields: id is null", 1005));
        exceptionText.put("message.id.wrong",                   new DescriptionAndErrCodeMapping("Message Common Fields: id is not valid UUID", 1006));
        exceptionText.put("message.source.destination.null",    new DescriptionAndErrCodeMapping("Message Common Fields: source is null or empty", 1007));
        exceptionText.put("message.source.NaN",                 new DescriptionAndErrCodeMapping("Message Common Fields: source is not a number (valid endpoint ID should be a number)", 1008));
        exceptionText.put("message.destination.NaN",            new DescriptionAndErrCodeMapping("Message Common Fields: destination is not a number (valid endpoint ID should be a number)", 1009));
        exceptionText.put("message.priority.null",              new DescriptionAndErrCodeMapping("Message Common Fields: priority is null", 1010));
        exceptionText.put("message.priority.wrong",             new DescriptionAndErrCodeMapping("Message Common Fields: priority string is not valid PRIORITY value", 1011));
        exceptionText.put("message.reliability.null",           new DescriptionAndErrCodeMapping("Message Common Fields: reliability is null", 1012));
        exceptionText.put("message.reliability.wrong",          new DescriptionAndErrCodeMapping("Message Common Fields: reliability string is not valid RELIABILITY value", 1013));
        exceptionText.put("message.created.null",               new DescriptionAndErrCodeMapping("Message Common Fields: created is null", 1014));
        exceptionText.put("message.created.wrong",              new DescriptionAndErrCodeMapping("Message Common Fields: created has wrong Date format (correct: yyyy-MM-dd'T'HH:mm:ss.SSSXXX)", 1015));
        exceptionText.put("message.property.key.long",          new DescriptionAndErrCodeMapping("Message Common Fields: property key is longer than " + Message.Utils.MAX_KEY_LENGTH + " characters", 1016));
        exceptionText.put("message.property.value.long",        new DescriptionAndErrCodeMapping("Message Common Fields: property value is longer than " + Message.Utils.MAX_STRING_VALUE_LENGTH + " characters", 1017));
        exceptionText.put("message.type.null",                  new DescriptionAndErrCodeMapping("Message Common Fields: type of the message is null", 1018));
        exceptionText.put("message.type.wrong",                 new DescriptionAndErrCodeMapping("Message Common Fields: type of the message is not valid", INVALID_MSG_TYPE));
        exceptionText.put("data.message.data.null",             new DescriptionAndErrCodeMapping("DataMessage: data field is missing", 1101));
        exceptionText.put("data.message.data.empty",            new DescriptionAndErrCodeMapping("DataMessage: data field is empty", 1102));
        exceptionText.put("data.message.format.null",           new DescriptionAndErrCodeMapping("DataMessage: format is null or empty", 1103));
        exceptionText.put("data.message.origin.null",           new DescriptionAndErrCodeMapping("DataMessage: origin is null or empty", 1104));
        exceptionText.put("data.message.eventTime.null",        new DescriptionAndErrCodeMapping("DataMessage: eventTime is null", 1105));
        exceptionText.put("data.message.eventTime.wrong",       new DescriptionAndErrCodeMapping("DataMessage: eventTime has wrong Long value", 1106));
        exceptionText.put("data.message.item.key.long",         new DescriptionAndErrCodeMapping("DataMessage: item key is longer than " + Message.Utils.MAX_KEY_LENGTH + " characters", 1107));
        exceptionText.put("data.message.item.value.long",       new DescriptionAndErrCodeMapping("DataMessage: item date string value is longer than " + Message.Utils.MAX_STRING_VALUE_LENGTH + " characters", 1108));
        exceptionText.put("request.message.payload.null",       new DescriptionAndErrCodeMapping("RequestMessage: Json payload is null", 1201));
        exceptionText.put("request.message.payload.value.null", new DescriptionAndErrCodeMapping("RequestMessage: Json payload value is null", 1202));
        exceptionText.put("request.message.method.null",        new DescriptionAndErrCodeMapping("RequestMessage: method is null or empty", 1203));
        exceptionText.put("request.message.url.null",           new DescriptionAndErrCodeMapping("RequestMessage: url is null or empty", 1204));
        exceptionText.put("request.message.body.wrong",         new DescriptionAndErrCodeMapping("RequestMessage: body is not encoded with Base64", 1205));
        exceptionText.put("request.message.body.null",          new DescriptionAndErrCodeMapping("RequestMessage: body is null", 1206));
        exceptionText.put("response.message.payload.null",      new DescriptionAndErrCodeMapping("ResponseMessage: Json payload is null", 1301));
        exceptionText.put("response.message.payload.value.null",new DescriptionAndErrCodeMapping("ResponseMessage: Json payload value is null", 1302));
        exceptionText.put("response.message.status.null",       new DescriptionAndErrCodeMapping("ResponseMessage: status code is null", 1303));
        exceptionText.put("response.message.status.notNumber",  new DescriptionAndErrCodeMapping("ResponseMessage: status code is not a number", 1304));
        exceptionText.put("response.message.status.wrong",      new DescriptionAndErrCodeMapping("ResponseMessage: status code number is not supported", 1305));
        exceptionText.put("response.message.requestId.null",    new DescriptionAndErrCodeMapping("ResponseMessage: requestId is null or empty", 1306));
        exceptionText.put("response.message.requestId.wrong",   new DescriptionAndErrCodeMapping("ResponseMessage: requestId is not valid UUID", 1307));
        exceptionText.put("response.message.body.wrong",        new DescriptionAndErrCodeMapping("ResponseMessage: body is not encoded with Base64", 1308));
        exceptionText.put("response.message.body.null",         new DescriptionAndErrCodeMapping("ResponseMessage: body is null", 1309));
        exceptionText.put("alert.message.format.null",          new DescriptionAndErrCodeMapping("AlertMessage: format is null", 1401));
        exceptionText.put("alert.message.item.key.long",        new DescriptionAndErrCodeMapping("AlertMessage: item key is longer than " + Message.Utils.MAX_KEY_LENGTH + " characters", 1403));
        exceptionText.put("alert.message.item.value.long",      new DescriptionAndErrCodeMapping("AlertMessage: item data string value is longer than " + Message.Utils.MAX_STRING_VALUE_LENGTH + " characters", 1404));
        exceptionText.put("alert.message.data.null",            new DescriptionAndErrCodeMapping("AlertMessage: data field is missing", 1404));
        exceptionText.put("alert.message.payload.null",         new DescriptionAndErrCodeMapping("AlertMessage: payload block is missing", 1405));
        exceptionText.put("alert.message.severity.null",        new DescriptionAndErrCodeMapping("AlertMessage: severity field is missing", 1406));
        exceptionText.put("alert.message.severity.invalid",     new DescriptionAndErrCodeMapping("AlertMessage: severity field is missing", 1407));
    }

    /**
     * This class is used for pairing exception text <-> err code of the exception
     */
    /*package for testing*/ final static class DescriptionAndErrCodeMapping {
        /** Text of the error*/
        private final String description;
        /** Error code*/
        private final int errCode;

        public DescriptionAndErrCodeMapping(String description, int errCode) {
            this.description = description;
            this.errCode = errCode;
        }

        /**
         * Method returns text of the exception.
         * @return Exception text.
         */
        public String getDescription() {
            return description;
        }

        /**
         * Method returns error code for the exception.
         * @return Error code.
         */
        public int getErrCode() {
            return errCode;
        }
    }
}

