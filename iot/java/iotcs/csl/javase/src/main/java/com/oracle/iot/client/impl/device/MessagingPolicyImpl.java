/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import com.oracle.iot.client.DeviceModelAttribute;
import com.oracle.iot.client.device.DirectlyConnectedDevice;
import com.oracle.iot.client.impl.DeviceModelImpl;
import com.oracle.iot.client.impl.util.Pair;
import com.oracle.iot.client.message.AlertMessage;
import com.oracle.iot.client.message.DataItem;
import com.oracle.iot.client.message.DataMessage;
import com.oracle.iot.client.message.Message;
import oracle.iot.client.DeviceModel;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MessagingPolicyImpl is the code that supports the com.oracle.iot.client.device.DirectlyConnectedDevice
 * and the com.oracle.iot.client.device.util.MessageDispatcher offer(com.oracle.iot.client.message.Message...)
 * methods. This class uses the devicePolicy REST API
 * <p>
 * The {@link com.oracle.iot.client.device.DirectlyConnectedDevice#offer(com.oracle.iot.client.message.Message...)}
 * API invokes the {@link #applyPolicies(Message)} method and then calls
 * {@link com.oracle.iot.client.device.DirectlyConnectedDevice#send(com.oracle.iot.client.message.Message...)}
 * with the resulting messages.
 * </p>
 * <p>
 * The {@link com.oracle.iot.client.device.util.MessageDispatcher#offer(Message...)} API invokes the
 * {@link #applyPolicies(Message)} method and then calls
 * {@link com.oracle.iot.client.device.util.MessageDispatcher#queue(Message...)} with the resulting messages.
 * </p>
 */
public class MessagingPolicyImpl implements DevicePolicyManager.ChangeListener {

    /**
     * Constructed in com.oracle.iot.client.device.DirectlyConnectedDevice
     * @param directlyConnectedDevice the DirectlyConnectedDevice
     */
    public MessagingPolicyImpl(DirectlyConnectedDevice directlyConnectedDevice) {
        this.directlyConnectedDevice = directlyConnectedDevice;
    }

    /**
     * This is the method that applies whatever policies there may be to the message. The
     * method returns zero or more messages, depending on the policies that have been
     * applied to the message. The caller is responsible for sending or queuing the
     * returned messages. The data items in the returned are messages are possibly modified
     * by some policy; for example, a message with a temperature value goes in, a copy of
     * the same message is returned with the temperature value replaced by the
     * avearage temperature. A returned message may also be one that is created by a
     * policy function (such as a computedMetric). Or the returned messages may be messages
     * that have been batched. If no policy applies to the message, the message itself
     * is returned.
     * @param message A message of any kind
     * @return messages to be delivered
     * @throws IOException possibly thrown during network communications
     * @throws GeneralSecurityException possibly thrown accessing or using the secure connection
     */
    public Message[] applyPolicies(Message message)
            throws IOException, GeneralSecurityException {

        if (message == null) {
            return new Message[0];
        }

        // Pass the current millisecond time down so that functions that have the same
        // or intersecting timeout get expired at the same time. This ensures the attributes
        // are grouped.
        final long currentTimeMillis = System.currentTimeMillis();
        final List<Message> resultingMessages = new ArrayList<Message>();

        if (message.getType() == Message.Type.DATA) {
            DataMessage dataMessage =
                    applyAttributePolicies((DataMessage) message, currentTimeMillis);
            if (dataMessage != null) {
                resultingMessages.add(dataMessage);
            }
        } else {
            resultingMessages.add(message);
        }

        final List<Message> messageList = new ArrayList<Message>();
        if (messagesFromExpiredPolicies.size() > 0) {
            messageList.addAll(messagesFromExpiredPolicies);
            messagesFromExpiredPolicies.clear();
        }
        for (int index = 0, maxIndex = resultingMessages.size(); index < maxIndex; index++) {
            final Message[] messagesFromDevicePolicy =
                    applyDevicePolicies(resultingMessages.get(index), currentTimeMillis);
            Collections.addAll(messageList, messagesFromDevicePolicy);
        }

        return messageList.toArray(new Message[messageList.size()]);

    }

    @Override
    public void policyAssigned(DevicePolicy devicePolicy, Set<String> assignedDevices) {
        // do nothing
    }

    @Override
    public void policyUnassigned(DevicePolicy devicePolicy, Set<String> assignedDevices) {

        final long currentTimeMillis = System.currentTimeMillis();
        final List<Message> messages = expirePolicy(devicePolicy, currentTimeMillis);
        if (messages != null && !messages.isEmpty()) {
            messagesFromExpiredPolicies.addAll(messages);
        }

        // TODO:  Need to figure out how to handle accumulated values.
        //        For now, just clear out the various maps, which
        //        effectively means "start from scratch"
        deviceAnalogMap.clear();
        pipelineDataCache.clear();
        computedMetricTriggers.clear();
        windowMap.clear();
    }

    // Apply policies that are targeted to an attribute
    private DataMessage applyAttributePolicies(DataMessage dataMessage, long currentTimeMillis)
            throws IOException, GeneralSecurityException {

        // A data message format cannot be null or empty
        // (enforced in DataMessage(Builder) constructor
        final String format = dataMessage.getFormat();
        final String deviceModelUrn = format.substring(0, format.length() - ":attributes".length());

        final DeviceModelImpl deviceModel;
        DeviceModel dm = directlyConnectedDevice.getDeviceModel(deviceModelUrn);
        if (dm instanceof DeviceModelImpl) {
            deviceModel = (DeviceModelImpl) dm;
        } else {
            return dataMessage;
        }

        final String endpointId = dataMessage.getSource();
        final DeviceAnalog deviceAnalog;
        if (!deviceAnalogMap.containsKey(endpointId)) {
            deviceAnalog = new DeviceAnalogImpl(directlyConnectedDevice, (DeviceModelImpl) deviceModel, endpointId);
            deviceAnalogMap.put(endpointId, deviceAnalog);
        } else {
            deviceAnalog = deviceAnalogMap.get(endpointId);
        }

        final Map<String, Set<String>> triggerMap;
        if (!computedMetricTriggers.containsKey(deviceModelUrn)) {
            triggerMap = new HashMap<String, Set<String>>();
            computedMetricTriggers.put(deviceModelUrn, triggerMap);

            final Map<String, DeviceModelAttribute> deviceModelAttributeMap = deviceModel.getDeviceModelAttributes();
            for (Map.Entry<String, DeviceModelAttribute> entry : deviceModelAttributeMap.entrySet()) {
                final DeviceModelAttribute deviceModelAttribute = entry.getValue();
                final String attribute = deviceModelAttribute.getName();

                final DevicePolicyManager devicePolicyManager =
                        DevicePolicyManager.getDevicePolicyManager(directlyConnectedDevice);
                final DevicePolicy devicePolicy =
                        devicePolicyManager.getPolicy(deviceModelUrn, endpointId);
                if (devicePolicy == null) {
                    continue;
                }

                final List<DevicePolicy.Function> pipeline =
                        devicePolicy.getPipeline(attribute);
               if (pipeline == null || pipeline.isEmpty()) {
                    continue;
                }

                // If a computedMetric is the first function in the pipeline,
                // then see if the formula refers to other attributes. If so,
                // then try this pipeline after all others.
                final DevicePolicy.Function function = pipeline.get(0);
                final String deviceFunctionId = function.getId();
                final Map<String, ?> parameters = function.getParameters();
                if ("computedMetric".equals(deviceFunctionId)) {

                    final String formula = (String) parameters.get("formula");
                    final Set<String> triggerAttributes = new HashSet<String>();
                    int pos = formula.indexOf("$(");
                    while (pos != -1) {
                        final int end = formula.indexOf(')', pos + 1);
                        if (pos == 0 || formula.charAt(pos - 1) != '$') {
                            final String attr = formula.substring(pos + "$(".length(), end);
                            if (!attr.equals(attribute)) {
                                triggerAttributes.add(attr);
                            }
                        }
                        pos = formula.indexOf("$(", end + 1);
                    }

                    if (!triggerAttributes.isEmpty()) {
                        triggerMap.put(attribute, triggerAttributes);
                    }
                }
            }
        } else {
            triggerMap = computedMetricTriggers.get(deviceModelUrn);
        }

        // getDataItems is returns an unmodifiable list
        final List<DataItem<?>> dataMessageDataItems = dataMessage.getDataItems();

        // DataItems resulting from policies
        final List<DataItem<?>> policyDataItems = new ArrayList<DataItem<?>>(dataMessageDataItems.size());

        // DataItems that have no policies
        final List<DataItem<?>> skippedDataItems = new ArrayList<DataItem<?>>(dataMessageDataItems.size());

        // If no policies are found, we will return the original message
        boolean noPoliciesFound = true;

        for (DataItem<?> dataItem : dataMessageDataItems) {

            final String attribute = dataItem.getKey();
            final DeviceModelAttribute deviceModelAttribute =
                    deviceModel.getDeviceModelAttributes().get(attribute);

            if (deviceModelAttribute == null) {
                getLogger().log(Level.INFO, deviceModel.getURN() + " does not contain attribute " + attribute);
                skippedDataItems.add(dataItem);
                continue;
            }

            final DevicePolicyManager devicePolicyManager =
                    DevicePolicyManager.getDevicePolicyManager(directlyConnectedDevice);
            final DevicePolicy devicePolicy =
                    devicePolicyManager.getPolicy(deviceModelUrn, endpointId);
            if (devicePolicy == null) {
                deviceAnalog.setAttributeValue(attribute, cast(deviceModelAttribute.getType(), dataItem.getValue()));
                skippedDataItems.add(dataItem);
                continue;
            }

            final List<DevicePolicy.Function> pipeline = devicePolicy.getPipeline(attribute);

            // No policies for this attribute, retain the data item.
            if (pipeline == null || pipeline.isEmpty()) {
                deviceAnalog.setAttributeValue(attribute, cast(deviceModelAttribute.getType(), dataItem.getValue()));
                skippedDataItems.add(dataItem);
                continue;
            }

            noPoliciesFound = false;

            // if this is a computed metric, skip it for now
            if (triggerMap.containsKey(attribute)) {
                continue;
            }

            DataItem<?> policyDataItem =
                    applyAttributePolicy(deviceAnalog, dataItem, pipeline,currentTimeMillis );
            if (policyDataItem != null) {
                policyDataItems.add(policyDataItem);
            }
        }

        // if no policies were found, return the original message
        if (noPoliciesFound) {
            return dataMessage;
        }

        // if policies were found, but there are no policyDataItems
        // and no skipped data items, then return null.
        if (policyDataItems.isEmpty() && skippedDataItems.isEmpty()) {
            return null;
        }

        // This looks like a good place to check for computed metrics, too.
        if (!policyDataItems.isEmpty()) {
            checkComputedMetrics(policyDataItems, deviceAnalog, triggerMap, currentTimeMillis);
        }

        // Since we can't modify the original data message, we have to create a copy.
        final DataMessage.Builder dataMessageBuilder =
                new DataMessage.Builder()
                        .format(format)
                        .clientId(dataMessage.getClientId())
                        .source(dataMessage.getSource())
                        .destination(dataMessage.getDestination())
                        .priority(dataMessage.getPriority())
                        .reliability(dataMessage.getReliability())
                        .eventTime(dataMessage.getEventTime())
                        .properties(dataMessage.getProperties())
                        .sender(dataMessage.getSender());

        if (dataMessage.getDiagnostics() != null) {
            Map<String, Object> diagnostics = dataMessage.getDiagnostics();
            for (Map.Entry<String, Object> entry : diagnostics.entrySet()) {
                dataMessageBuilder.diagnostic(entry.getKey(), entry.getValue());
            }
        }

        dataMessageBuilder.dataItems(policyDataItems);
        dataMessageBuilder.dataItems(skippedDataItems);

        return dataMessageBuilder.build();

    }

    // Return DataItem if it should be included in the Message, null if it should not be.
    private DataItem<?> applyAttributePolicy(DeviceAnalog deviceAnalog,
                                             DataItem<?> dataItem,
                                             List<DevicePolicy.Function> pipeline,
                                             long currentTimeMillis) {

        final String attribute = dataItem.getKey();
        final DeviceModelImpl deviceModel = (DeviceModelImpl) deviceAnalog.getDeviceModel();

        Object policyValue = dataItem.getValue();

        // Create the pipeline data for this attribute
        List<Map<String, Object>> pipelineData = pipelineDataCache.get(attribute);
        if (pipelineData == null) {
            pipelineData = new ArrayList<Map<String, Object>>();
            pipelineDataCache.put(attribute, pipelineData);
        }

        DeviceFunction.putInProcessValue(deviceAnalog.getEndpointId(), deviceModel.getURN(), attribute, policyValue);

        for (int index = 0, maxIndex = pipeline.size(); index < maxIndex; index++) {

            final DevicePolicy.Function function = pipeline.get(index);
            final Map<String, Object> functionData;
            if (index < pipelineData.size()) {
                functionData = pipelineData.get(index);
            } else {
                functionData = new HashMap<String, Object>();
                pipelineData.add(functionData);
            }

            final String key = function.getId();
            final Map<String, ?> parameters = function.getParameters();

            final DeviceFunction deviceFunction =
                    DeviceFunction.getDeviceFunction(key);

            if (deviceFunction == null) continue;

            final boolean windowExpired;
            long window = DeviceFunction.getWindow(parameters);
            long slide = DeviceFunction.getSlide(parameters, window);
            if (window > 0) {

                // This could be more succinct, but it makes the key easy to read in the debugger.
                final String k =
                        deviceModel.getURN().concat(":".concat(attribute.concat(":".concat(deviceFunction.getId().concat(".expiry")))));
                Long expiry = windowMap.get(k);
                if (expiry == null) {
                    expiry = currentTimeMillis + window;
                    windowMap.put(k, expiry);
                }

                windowExpired = expiry <= currentTimeMillis;

                if (windowExpired) {
                    windowMap.put(k, expiry + slide);
                }
            } else {
                windowExpired = false;
            }


            if (deviceFunction.apply(deviceAnalog, attribute, parameters, functionData, policyValue) || windowExpired) {

                final Object valueFromPolicy = deviceFunction.get(
                        deviceAnalog,
                        attribute,
                        parameters,
                        functionData
                );

                if (valueFromPolicy != null) {
                    policyValue = valueFromPolicy;
                    DeviceFunction.putInProcessValue(deviceAnalog.getEndpointId(), deviceModel.getURN(), attribute, policyValue);
                } else {
                    getLogger().log(Level.WARNING, attribute +
                            " got null value from policy" + deviceFunction.getDetails(parameters));
                    break;
                }

            } else {
                // apply returned false
                policyValue = null;
                break;
            }

        }

        // After the policy loop, if the policyValue is null, then the policy
        // either filtered out the attribute, or the policy parameters have not
        // been met (e.g., sampleQuality rate is not met).
        // If it is not null, then create a new DataItem to replace the old
        // in the data message.
        final DataItem<?> policyDataItem;
        if (policyValue != null) {
            final DeviceModelAttribute deviceModelAttribute = deviceModel.getDeviceModelAttributes().get(attribute);
            deviceAnalog.setAttributeValue(attribute, cast(deviceModelAttribute.getType(), policyValue));
            policyDataItem = createDataItem(dataItem.getType(), dataItem.getKey(), policyValue);
        } else {
            policyDataItem = null;
        }

        DeviceFunction.removeInProcessValue(deviceAnalog.getEndpointId(), deviceModel.getURN(), attribute);
        return policyDataItem;

    }

    private void checkComputedMetrics(
            List<DataItem<?>> dataItems,
            DeviceAnalog deviceAnalog,
            Map<String, Set<String>> triggerMap,
            long currentTimeMillis)
            throws IOException, GeneralSecurityException {

        if (triggerMap.isEmpty() || dataItems.isEmpty()) {
            return;
        }

        Set<String> updatedAttributes = new HashSet<String>();
        for (DataItem<?> dataItem : dataItems) {
            updatedAttributes.add(dataItem.getKey());
        }

        final String endpointId = deviceAnalog.getEndpointId();
        final DeviceModelImpl deviceModel = (DeviceModelImpl) deviceAnalog.getDeviceModel();
        final Map<String, DeviceModelAttribute> deviceModelAttributes = deviceModel.getDeviceModelAttributes();
        final String deviceModelUrn = deviceModel.getURN();

        for (Map.Entry<String, Set<String>> entry : triggerMap.entrySet()) {

            // This is the set of attributes that the formula refers to.
            final Set<String> key = entry.getValue();

            // If the set of attributes that the formula refers to
            // is a subset of the updated attributes, then compute
            // the value of the computedMetric.
            if (updatedAttributes.containsAll(key)) {

                final String attribute = entry.getKey();
                final DeviceModelAttribute deviceModelAttribute = deviceModelAttributes.get(attribute);
                Object attributeValue = deviceAnalog.getAttributeValue(attribute);
                if (attributeValue == null) {
                    attributeValue = deviceModelAttribute.getDefaultValue();
                }

                final DataItem<?> dataItem;
                switch (deviceModelAttribute.getType()) {
                    case NUMBER:
                    case INTEGER: {
                        final Number number = attributeValue != null ? Number.class.cast(attributeValue) : 0.0;
                        dataItem = new DataItem(attribute, number.doubleValue());
                        break;
                    }
                    case STRING:
                    case URI: {
                        final String string = attributeValue != null ? String.class.cast(attributeValue) : "";
                        dataItem = new DataItem(attribute, string);
                        break;
                    }
                    case BOOLEAN: {
                        final Boolean bool = attributeValue != null ? Boolean.class.cast(attributeValue) : Boolean.FALSE;
                        dataItem = new DataItem(attribute, bool);
                        break;
                    }
                    case DATETIME: {
                        final long value;
                        if (attributeValue instanceof Date) {
                            value = ((Date) attributeValue).getTime();
                        } else {
                            value = attributeValue != null ? Long.class.cast(attributeValue) : 0l;
                        }
                        dataItem = new DataItem(attribute, value);
                        break;
                    }
                    default:
                        getLogger().log(Level.WARNING, "unknown device model attribute type: " + deviceModelAttribute.getType());
                        continue;
                }

                final DevicePolicyManager devicePolicyManager =
                        DevicePolicyManager.getDevicePolicyManager(directlyConnectedDevice);
                final DevicePolicy devicePolicy =
                        devicePolicyManager.getPolicy(deviceModelUrn, endpointId);

                if (devicePolicy == null) {
                    continue;
                }

                final List<DevicePolicy.Function> pipeline =
                        devicePolicy.getPipeline(attribute);

                if (pipeline == null ||  pipeline.isEmpty()) {
                    continue;
                }

                final DataItem<?> policyDataItem =
                        applyAttributePolicy(deviceAnalog, dataItem, pipeline, currentTimeMillis);

                if (policyDataItem != null) {
                    dataItems.add(policyDataItem);
                }
            }
        }

    }

    // Apply policies that are targeted to a device model
    private Message[] applyDevicePolicies(Message message, long currentTimeMillis)
            throws IOException, GeneralSecurityException {

        // A data message or alert format cannot be null or empty
        // (enforced in Data/AlertMessage(Builder) constructor)
        final String format;
        final String deviceModelUrn;
        final String endpointId = message.getSource();
        if (message instanceof DataMessage) {
            format = ((DataMessage) message).getFormat();
            deviceModelUrn = format.substring(0, format.length() - ":attributes".length());
        } else if (message instanceof AlertMessage) {
            format = ((AlertMessage) message).getFormat();
            deviceModelUrn = format.substring(0, format.lastIndexOf(':'));
        } else {
            return new Message[]{message};
        }

        DeviceAnalog deviceAnalog = deviceAnalogMap.get(endpointId);
        if (deviceAnalog == null) {
            final DeviceModel deviceModel = directlyConnectedDevice.getDeviceModel(deviceModelUrn);
            if (deviceModel instanceof DeviceModelImpl) {
                deviceAnalog = new DeviceAnalogImpl(directlyConnectedDevice, (DeviceModelImpl) deviceModel, endpointId);
                deviceAnalogMap.put(endpointId, deviceAnalog);
            }

            // TODO: what to do if deviceAnalog is null?
            if (deviceAnalog == null) {
                return new Message[]{message};
            }
        }


        final DevicePolicyManager devicePolicyManager =
                DevicePolicyManager.getDevicePolicyManager(directlyConnectedDevice);
        final DevicePolicy devicePolicy =
                devicePolicyManager.getPolicy(deviceModelUrn, endpointId);
        if (devicePolicy == null) {
            return new Message[]{message};
        }

        final List<DevicePolicy.Function> pipeline =
                devicePolicy.getPipeline(DevicePolicy.ALL_ATTRIBUTES());

        // No policies for this device model, retain the data item.
        if (pipeline == null || pipeline.isEmpty()) {
            return new Message[]{message};
        }

        if (pipeline.size() > 1) {
            // TODO: handle more than one function in a pipeline for all-attributes
            // Print out a warning message about too many function for all-attributes pipeline

            for (int index = 0, maxIndex = pipeline.size(); index < maxIndex; index++) {

                final DevicePolicy.Function function = pipeline.get(index);
                final String id = function.getId();
                final Map<String, ?> parameters = function.getParameters();
                final DeviceFunction deviceFunction = DeviceFunction.getDeviceFunction(id);

                if (index == 0) {
                    getLogger().log(Level.WARNING, "Only one function allowed for all-attribute pipeline.");
                    getLogger().log(Level.WARNING, "\tApplying: " + deviceFunction.getDetails(parameters));
                } else {
                    getLogger().log(Level.WARNING, "\tIgnoring: " + deviceFunction.getDetails(parameters));
                }
            }
        }

        // Create the pipeline data for this device model
        List<Map<String, Object>> pipelineData = pipelineDataCache.get(null);
        if (pipelineData == null) {
            pipelineData = new ArrayList<Map<String, Object>>();
            pipelineDataCache.put(null, pipelineData);
        }

        // Handle pipleline for device policy
//         TODO: handle more than one function in a pipeline for all-attributes
//         Note: A warning is printed out, several lines above this, if there
//         is more than one function for an all-attributes policy.
//         Leaving original line here, commented-out, for future reference.
//         for (int index = 0, maxIndex = pipeline.size(); index < maxIndex; index++) {
        for (int index = 0, maxIndex = 1; index < maxIndex; index++) {
            final DevicePolicy.Function function = pipeline.get(index);
            final Map<String, Object> functionData;
            if (index < pipelineData.size()) {
                functionData = pipelineData.get(index);
            } else {
                functionData = new HashMap<String, Object>();
                pipelineData.add(functionData);
            }

            final String key = function.getId();
            final Map<String, ?> parameters = function.getParameters();

            final DeviceFunction deviceFunction =
                    DeviceFunction.getDeviceFunction(key);

            if (deviceFunction == null) continue;

            final boolean windowExpired;
            final long window = DeviceFunction.getWindow(parameters);
            final long slide = DeviceFunction.getSlide(parameters, window);
            if (window > 0) {

                final String k = deviceModelUrn.concat("::".concat(deviceFunction.getId().concat(".expiry")));
                Long expiry = windowMap.get(k);
                if (expiry == null) {
                    expiry = currentTimeMillis + window;
                    windowMap.put(k, expiry);
                }

                windowExpired = expiry <= currentTimeMillis;

                if (windowExpired) {
                    windowMap.put(k, expiry + slide);
                }

            } else {
                windowExpired = false;
            }

            final boolean alertOverridesPolicy;
            if (message instanceof AlertMessage) {
                final AlertMessage alertMessage = (AlertMessage) message;
                final AlertMessage.Severity alertMessageSeverity = alertMessage.getSeverity();

                AlertMessage.Severity configuredSeverity = AlertMessage.Severity.CRITICAL;
                final String criterion = (String) parameters.get("alertSeverity");
                if (criterion != null) {
                    try {
                        configuredSeverity = AlertMessage.Severity.valueOf(criterion);
                    } catch (IllegalArgumentException e) {
                        configuredSeverity = AlertMessage.Severity.CRITICAL;
                    }
                }
                alertOverridesPolicy = configuredSeverity.compareTo(alertMessageSeverity) <= 0;

            } else {
                alertOverridesPolicy = false;
            }

            // VirtualDevice stores Pair<Message,StorageObjectImpl>, do the same here.
            final Pair<Message,StorageObjectImpl> pair = new Pair<Message,StorageObjectImpl>(message, null);
            if (deviceFunction.apply(deviceAnalog, null, parameters, functionData, pair)
                    || windowExpired
                    || alertOverridesPolicy) {

                final Object valueFromPolicy = deviceFunction.get(
                        deviceAnalog,
                        null,
                        parameters,
                        functionData
                );

                if (valueFromPolicy != null) {
                    final List<Pair<Message,StorageObjectImpl>> messageList = (List<Pair<Message,StorageObjectImpl>>) valueFromPolicy;
                    final Message[] messages = new Message[messageList.size()];
                    for (int m=0, mMax=messageList.size(); m<mMax; m++) {
                        Pair<Message,StorageObjectImpl> element = messageList.get(m);
                        // For LL, we don't care about StorageObject since it isn't queued with message.
                        // For HL, we do care because MessageDispatcherImpl logically ties the two together.
                        // But this is LL code here, so we only want the message. Besides,
                        // when this Pair is created (just before calling apply), the value
                        // side of the Pair is passed as null.
                        messages[m] = element.getKey();
                    }
                    return messages;

                }
            }
            return new Message[0];
        }

        return new Message[0];
    }


    private List<Message> expirePolicy(final DevicePolicy devicePolicy, long currentTimeMillis) {

        final List<Message> messageList = expirePolicy(devicePolicy);

        final List<Message> consolidatedMessageList = new ArrayList<Message>();
        if (!messageList.isEmpty()) {

            // consolidate messages
            final Map<String,List<DataItem<?>>> dataItemMap = new HashMap<String,List<DataItem<?>>>();
            for (Message message : messageList) {
                if (message instanceof DataMessage) {
                    final String endpointId = message.getSource();
                    List<DataItem<?>> dataItems = dataItemMap.get(endpointId);
                    if (dataItems == null) {
                        dataItems = new ArrayList<DataItem<?>>();
                        dataItemMap.put(endpointId, dataItems);
                    }
                    dataItems.addAll(((DataMessage) message).getDataItems());
                } else {
                    consolidatedMessageList.add(message);
                }
            }

            for (Map.Entry<String, List<DataItem<?>>> entry : dataItemMap.entrySet()) {
                final DeviceAnalog deviceAnalog = deviceAnalogMap.get(entry.getKey());
                if (deviceAnalog == null) {
                    continue;
                }
                final List<DataItem<?>> dataItems = entry.getValue();

                final String format = deviceAnalog.getDeviceModel().getURN();

                if (!computedMetricTriggers.isEmpty()) {

                    Map<String,Set<String>> triggerMap = computedMetricTriggers.get(format);
                    if (triggerMap != null && !triggerMap.isEmpty()) {
                        try {
                            checkComputedMetrics(dataItems, deviceAnalog, triggerMap, currentTimeMillis);
                        } catch (IOException e) {
                            getLogger().log(Level.WARNING, e.getMessage());
                        } catch (GeneralSecurityException e) {
                            getLogger().log(Level.WARNING, e.getMessage());
                        }
                    }
                }

                final DataMessage dataMessage = new DataMessage.Builder()
                        .source(deviceAnalog.getEndpointId())
                        .format(deviceAnalog.getDeviceModel().getURN())
                        .dataItems(dataItems)
                        .build();
                consolidatedMessageList.add(dataMessage);

            }
        }
        return consolidatedMessageList;

    }

    private List<Message> expirePolicy(DevicePolicy devicePolicy) {

        final List<Message> messageList = new ArrayList<Message>();

        for (DeviceAnalog deviceAnalog : deviceAnalogMap.values()) {
            final List<Message> messages = expirePolicy(devicePolicy, deviceAnalog);
            if (messages != null && !messages.isEmpty()) {
                messageList.addAll(messages);
            }
        }

        return messageList;
    }

    private List<Message> expirePolicy(final DevicePolicy devicePolicy, final DeviceAnalog deviceAnalog) {

        final Set<Map.Entry<String, List<DevicePolicy.Function>>> entries = devicePolicy.getPipelines().entrySet();
        final List<Message> messageList = new ArrayList<Message>();
        for (Map.Entry<String, List<DevicePolicy.Function>> entry : entries) {
            final List<Message> messages = expirePolicy(entry.getKey(), entry.getValue(), deviceAnalog);
            if (messages != null) {
                messageList.addAll(messages);
            }
        }
        return messageList;
    }

    private List<Message> expirePolicy(final String attributeName,
                                       final List<DevicePolicy.Function> pipeline,
                                       final DeviceAnalog deviceAnalog) {

        if (pipeline == null || pipeline.isEmpty()) {
            return null;
        }

        // attributeName may be null.
        // Note that we are _removing_ the pipeline data cache for this attribute (which may be null)
        final List<Map<String, Object>> pipelineData = pipelineDataCache.remove(attributeName);
        if (pipelineData == null) {
            return null;
        }

        for (int index = 0, maxIndex = pipeline.size(); index < maxIndex; index++) {

            DevicePolicy.Function function = pipeline.get(index);
            if (function == null) {
                continue;
            }

            DeviceFunction deviceFunction = DeviceFunction.getDeviceFunction(function.getId());
            if (deviceFunction == null) {
                return null;
            }

            // Looking for the first policy function in the pipeline that has a "window".
            // If there isn't one, we'll drop out of the loop and return null.
            // If there is one, we process the remaining pipeline from there.
            final long window = DeviceFunction.getWindow(function.getParameters());
            if (window == -1) {
                continue;
            }

            Map<String, Object> functionData = index < pipelineData.size() ? pipelineData.get(index) : null;
            if (functionData == null) {
                // there is no data for this function, so return
                return null;
            }

            Object valueFromPolicy = deviceFunction.get(
                    deviceAnalog,
                    attributeName,
                    function.getParameters(),
                    functionData
            );

            if (valueFromPolicy == null) {
                return null;
            }

            for (int next = index+1; next < maxIndex; next++) {
                function = pipeline.get(next);
                deviceFunction = DeviceFunction.getDeviceFunction(function.getId());
                if (deviceFunction == null) {
                    return null;
                }
                functionData = next < pipelineData.size() ? pipelineData.get(next) : null;
                if (function == null) {
                    return null;
                }

                if (deviceFunction.apply(deviceAnalog, attributeName, function.getParameters(), functionData, valueFromPolicy)) {
                    valueFromPolicy = deviceFunction.get(
                            deviceAnalog,
                            attributeName,
                            function.getParameters(),
                            functionData
                    );
                    if (valueFromPolicy == null) {
                        return null;
                    }
                } else {
                    return null;
                }

            }

            // If we get here, valueFromPolicy is not null.
            if (valueFromPolicy instanceof List) {
                return (List<Message>)valueFromPolicy;
            }

            final DeviceModelImpl deviceModel = (DeviceModelImpl)deviceAnalog.getDeviceModel();

            final DataMessage.Builder dataMessageBuilder = new DataMessage.Builder()
                    .source(deviceAnalog.getEndpointId())
                    .format(deviceModel.getURN());


            final DeviceModelAttribute deviceModelAttribute = deviceModel.getDeviceModelAttributes().get(attributeName);
            switch (deviceModelAttribute.getType()) {
                case DATETIME:
                case NUMBER:
                case INTEGER:
                    dataMessageBuilder.dataItem(attributeName, (Double)cast(DeviceModelAttribute.Type.NUMBER, valueFromPolicy));
                    break;
                case STRING:
                case URI:
                default:
                    dataMessageBuilder.dataItem(attributeName, (String)cast(DeviceModelAttribute.Type.STRING, valueFromPolicy));
                    break;
                case BOOLEAN:
                    dataMessageBuilder.dataItem(attributeName, (Double)cast(DeviceModelAttribute.Type.BOOLEAN, valueFromPolicy));
                    break;
            }
            List<Message> messages = new ArrayList<Message>();
            messages.add(dataMessageBuilder.build());
            return messages;
        }

        return null;
    }

    private DataItem createDataItem(final DataItem.Type type, final String key, final Object newValue) {

        DataItem dataItem = null;

        switch (type) {
            case DOUBLE: {
                final Number number = Number.class.cast(newValue);
                dataItem = new DataItem(key, number.doubleValue());
                break;
            }
            case STRING: {
                dataItem = new DataItem(key, (String.class.cast(newValue)));
                break;
            }
            case BOOLEAN: {
                dataItem = new DataItem(key, (Boolean.class.cast(newValue)));
                break;
            }
        }

        return dataItem;

    }

    // TODO: Maybe this should be a method in DeviceModelAttribute? Also duplicated in VirtualDeviceImpl
    private Object cast(final DeviceModelAttribute.Type type, final Object newValue) {

        Object castValue = null;

        switch (type) {
            case INTEGER: {
                final Number number = Number.class.cast(newValue);
                final Long roundedValue = Math.round(number.doubleValue());
                castValue = roundedValue.intValue();
                break;
            }
            case NUMBER: {
                final Number number = Number.class.cast(newValue);
                castValue = number.doubleValue();
                break;
            }
            case STRING: {
                castValue = String.class.cast(newValue);
                break;
            }
            case BOOLEAN: {
                castValue = Boolean.class.cast(newValue);
                break;
            }
            case DATETIME: {
                if (newValue instanceof Date) {
                    castValue = Date.class.cast(newValue).getTime();
                } else {
                    final Number number = Number.class.cast(newValue);
                    castValue = number.longValue();
                }
                break;
            }
            case URI: {
                castValue = String.class.cast(newValue);
                break;
            }
        }

        return castValue;

    }


    private final DirectlyConnectedDevice directlyConnectedDevice;

    // Data pertaining to this virtual device and its attributes for computing policies.
    // The key is attribute name (or null for the device model policies), value is a
    // list. Each element in the list corresponds to a function in the pipeline for
    // the attribute, and the map is used by the function to hold data between calls.
    // Note that there is a 1:1 correspondence between the pipeline configuration
    // data for the attribute, and the pipeline data for the attribute.
    private final Map<String, List<Map<String, Object>>> pipelineDataCache =
            new HashMap<String, List<Map<String, Object>>>();

    // deviceModelUrn -> DeviceAnalog
    // We need more than one DeviceAnalog because the DCD can have more than one device model.
    private final Map<String, DeviceAnalog> deviceAnalogMap =
            new HashMap<String, DeviceAnalog>();

    // deviceModelUrn:attribute:deviceFunctionId -> start time of last window
    // For a window policy, this maps the policy target plus the function to
    // when the window started. When the attribute for a timed function is in
    // the message, we can compare this start time to the elapsed time to
    // determine if the window has expired. If the window has expired, the value
    // computed by the function is passed to the remaining functions in the pipeline.
    //
    private final Map<String, Long> windowMap =
            new HashMap<String, Long>();

    // Key is device model urn, value is attribute -> trigger attributes
    // (a trigger attribute is a referenced attribute in a computedMetric formula).
    private final Map<String, Map<String, Set<String>>> computedMetricTriggers =
            new HashMap<String, Map<String, Set<String>>>();

    private final List<Message> messagesFromExpiredPolicies =
            new ArrayList<Message>();

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }

}
