/*
 * Copyright (c) 2017, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import com.oracle.iot.client.DeviceModelAction;
import com.oracle.iot.client.DeviceModelAttribute;
import com.oracle.iot.client.device.DirectlyConnectedDevice;
import com.oracle.iot.client.device.util.RequestDispatcher;
import com.oracle.iot.client.impl.DeviceModelImpl;
import com.oracle.iot.client.message.Message;
import com.oracle.iot.client.message.RequestMessage;
import com.oracle.iot.client.message.ResponseMessage;
import com.oracle.iot.client.message.StatusCode;
import oracle.iot.client.DeviceModel;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Device
 */
public final class DeviceAnalogImpl implements DeviceAnalog {

    public DeviceAnalogImpl(DirectlyConnectedDevice directlyConnectedDevice, DeviceModelImpl deviceModel, String endpointId) {
        this.directlyConnectedDevice = directlyConnectedDevice;
        this.deviceModel = deviceModel;
        this.endpointId = endpointId;
        attributeValueMap = new HashMap<String,Object>();
    }

    @Override
    public String getEndpointId() {
        return this.endpointId;
    }

    @Override
    public DeviceModel getDeviceModel() {
        return deviceModel;
    }

    @Override
    public void setAttributeValue(String attribute, Object value) {

        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }

        final DeviceModelAttribute deviceModelAttribute = deviceModel.getDeviceModelAttributes().get(attribute);
        if (deviceModelAttribute == null) {
            throw new IllegalArgumentException(deviceModel.getURN() + " does not contain attribute " + attribute);
        }

        final DeviceModelAttribute.Type type = deviceModelAttribute.getType();
        final boolean badValue;
        switch (type) {
            case NUMBER:
                badValue = !(value instanceof Number);
                break;
            case STRING:
            case URI:
                badValue = !(value instanceof String);
                break;
            case BOOLEAN:
                badValue = !(value instanceof Boolean);
                break;
            case INTEGER:
                badValue = !(value instanceof Integer);
                break;
            case DATETIME:
                badValue = !(value instanceof Long);
                break;
            default:
                throw new InternalError("unknown type '" + type + "'");
        }

        if (badValue) {
            throw new IllegalArgumentException(
                    "cannot set '"+ deviceModel.getURN() + ":attribute/" + attribute + "' to " + value.toString()
            );
        }

        attributeValueMap.put(attribute, value);

    }

    @Override
    public Object getAttributeValue(String attribute) {

        final DeviceModelAttribute deviceModelAttribute = deviceModel.getDeviceModelAttributes().get(attribute);
        if (deviceModelAttribute == null) {
            throw new IllegalArgumentException(deviceModel.getURN() + " does not contain attribute " + attribute);
        }

        Object value = attributeValueMap.get(attribute);
        if (value == null) {
            value = deviceModelAttribute.getDefaultValue();
        }
        return value;
    }

    @Override
    public void call(String actionName, Object... args) {

        final Map<String,DeviceModelAction> deviceModelActionMap = deviceModel.getDeviceModelActions();
        if (deviceModelActionMap == null) {
            getLogger().log(Level.WARNING, deviceModel.getURN() + " does not contain action '" + actionName + "'");
            return;
        }

        final DeviceModelAction deviceModelAction = deviceModelActionMap.get(actionName);
        if (deviceModelAction == null) {
            getLogger().log(Level.WARNING, deviceModel.getURN() + " does not contain action '" + actionName + "'");
            return;
        }

        final DeviceModelAttribute.Type argType = deviceModelAction.getArgType();

        // TODO: currently, call only supports one arg
        final Object arg = args != null && args.length > 0 ? args[0] : null;

        // What this is doing is pushing a request message into the message
        // queue for the requested action. To the LL, it is handled just like
        // any other RequestMessage. But we don't want this RM going to the
        // server, so the source and destination are set to be the same
        // endpoint id. In SendReceiveImpl, if there is a RequestMessage
        // with a source == destination, it is treated it specially.
        final RequestMessage.Builder requestMessageBuilder =
                new RequestMessage.Builder()
                .source(getEndpointId())
                .destination(getEndpointId())
                .url("deviceModels/" + getDeviceModel().getURN() + "/actions/" + actionName)
                .method("POST");

        // check arg for correct type
        if (argType != null) {

            if (arg == null) {
                getLogger().log(Level.WARNING, getDeviceModel().getURN() +
                        " action '" + actionName + "' requires an argument");
                return;
            }

            final boolean goodArg;
            switch (argType) {
                case INTEGER:
                case NUMBER:
                    if (goodArg = (arg instanceof Number)) {

                        final Number number = Number.class.cast(arg);
                        final String value;
                        if (argType == DeviceModelAttribute.Type.INTEGER) {
                            final Long roundedValue = Math.round(number.doubleValue());
                            value = Integer.toString(roundedValue.intValue());
                        } else {
                            value = number.toString();
                        }
                        requestMessageBuilder.body("{\"value\":" + value + "}");

                        // Assumption here is that lowerBound <= upperBound
                        final double val = ((Number) arg).doubleValue();
                        if (deviceModelAction.getUpperBound() != null) {
                            final double upper = deviceModelAction.getUpperBound().doubleValue();
                            if (Double.compare(val, upper) > 0) {
                                getLogger().log(Level.WARNING, getDeviceModel().getURN() +
                                        " action '" + actionName + "' arg out of range: " +
                                        val + " > " + upper);
                                // even though the arg is out of range, pass it to the action
                                // TODO is this the right thing to do?
                            }
                        }
                        if (deviceModelAction.getLowerBound() != null) {
                            final double lower = deviceModelAction.getLowerBound().doubleValue();
                            if(Double.compare(val, lower) < 0) {
                                getLogger().log(Level.WARNING, getDeviceModel().getURN() +
                                        " action '" + actionName + "' arg out of range: " +
                                        val + " < " + lower);
                                // even though the arg is out of range, pass it to the action
                                // TODO is this the right thing to do?
                            }
                        }
                    }
                    break;
                case DATETIME:
                    goodArg = arg instanceof Date || arg instanceof Number;
                    if (goodArg) {
                        final String value;
                        if (arg instanceof Date) {
                            final Date date = Date.class.cast(arg);
                            value = Long.toString(date.getTime());
                        } else {
                            final Long lval = Long.class.cast(arg);
                            value = Long.toString(lval);
                        }
                        requestMessageBuilder.body("{\"value\":" + value + "}");
                    }
                    break;
                case BOOLEAN:
                    if (goodArg = (arg instanceof Boolean)) {
                        final Boolean value = Boolean.class.cast(arg);
                        requestMessageBuilder.body("{\"value\":" + value.toString() + "}");

                    }
                    break;
                case STRING:
                case URI:
                    if (goodArg = (arg instanceof String)) {
                        final String value = String.class.cast(arg);
                        requestMessageBuilder.body("{\"value\":" + value + "}");

                    }
                    break;
                default:
                    getLogger().log(Level.SEVERE, "unexpected type " + argType);
                    goodArg = false;
            }

            if (!goodArg) {
                getLogger().log(Level.WARNING, getDeviceModel().getURN() +
                        " action '" + actionName + "': wrong argument type. " +
                        "Expected " + argType + " found " + arg.getClass());
                return;
            }

        }
        final RequestMessage requestMessage = requestMessageBuilder.build();

        final boolean useLongPolling = !Boolean.getBoolean("com.oracle.iot.client.disable_long_polling");

        //
        // Assumption here is that, if you are using long polling, you are using message dispatcher.
        // This could be a bad assumption. But if long polling is disabled, putting the message on
        // the request buffer will work regardless of whether message dispatcher is used.
        //
        if (useLongPolling) {
            try {
                final ResponseMessage responseMessage = RequestDispatcher.getInstance().dispatch(requestMessage);
                if (responseMessage.getStatusCode() == StatusCode.NOT_FOUND) {
                    getLogger().log(Level.INFO,
                            "Endpoint " + getEndpointId() + " has no handler for " + requestMessage.getURL());
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, e.getMessage());
            }

        } else {
            // Not long polling, push request message back on request buffer
            try {
                directlyConnectedDevice.send(requestMessage);
            } catch (IOException e) {
                getLogger().log(Level.WARNING, e.getMessage());
            } catch (GeneralSecurityException e) {
                getLogger().log(Level.WARNING, e.getMessage());
            }
        }


    }

    // DeviceAnalog API
    @Override
    public void queueMessage(Message message) {
        try {
            directlyConnectedDevice.offer(message);
        } catch (IOException e) {
            getLogger().log(Level.INFO, e.getMessage());
        } catch (GeneralSecurityException e) {
            getLogger().log(Level.INFO, e.getMessage());
        }
    }

    private final DirectlyConnectedDevice directlyConnectedDevice;
    private final DeviceModelImpl deviceModel;
    private final String endpointId;
    private final Map<String,Object> attributeValueMap;

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }
}
