/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.device;

import com.oracle.iot.client.DeviceModelAction;
import com.oracle.iot.client.DeviceModelAttribute;
import com.oracle.iot.client.DeviceModelFormat;
import com.oracle.iot.client.VirtualDeviceAttribute;
import com.oracle.iot.client.device.DirectlyConnectedDevice;
import com.oracle.iot.client.device.util.MessageDispatcher;
import com.oracle.iot.client.device.util.MessageDispatcher.DeliveryCallback;
import com.oracle.iot.client.device.util.RequestDispatcher;
import com.oracle.iot.client.device.util.RequestHandler;
import com.oracle.iot.client.impl.DeviceModelImpl;
import com.oracle.iot.client.impl.StorageConnectionBase;
import com.oracle.iot.client.impl.VirtualDeviceAttributeBase;
import com.oracle.iot.client.impl.VirtualDeviceBase;
import com.oracle.iot.client.impl.util.WritableValue;
import com.oracle.iot.client.message.AlertMessage;
import com.oracle.iot.client.message.DataItem;
import com.oracle.iot.client.message.DataMessage;
import com.oracle.iot.client.message.Message;
import com.oracle.iot.client.message.Message.Type;
import com.oracle.iot.client.message.RequestMessage;
import com.oracle.iot.client.message.ResponseMessage;
import com.oracle.iot.client.message.StatusCode;
import com.oracle.iot.client.impl.util.Pair;

import java.util.Collection;
import oracle.iot.client.AbstractVirtualDevice;
import oracle.iot.client.DeviceModel;
import oracle.iot.client.StorageObject;
import oracle.iot.client.device.Alert;
import oracle.iot.client.device.Data;
import oracle.iot.client.device.VirtualDevice;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * VirtualDeviceImpl
 */
public final class VirtualDeviceImpl
        extends VirtualDevice
        implements VirtualDeviceBase.Adapter<VirtualDevice>, DeviceAnalog,
                   RequestHandler, DevicePolicyManager.ChangeListener {

    private final VirtualDeviceBase<VirtualDevice> base;

    // used from AlertImpl
    final DirectlyConnectedDevice directlyConnectedDevice;

    final private DevicePolicyManager devicePolicyManager;

    private final Map<String,VirtualDeviceAttributeBase<VirtualDevice, Object>> attributeMap;

    // Data pertaining to this virtual device and its attributes for computing policies.
    // The key is attribute name (or null for the device model policies), value is a
    // list. Each element in the list corresponds to a function in the pipeline for
    // the attribute, and the map is used by the function to hold data between calls.
    // Note that there is a 1:1 correspondence between the configuration
    // data for the attribute, and the pipeline data for the attribute.
    private final Map<String,List<Map<String,Object>>> pipelineDataCache;

    private final Object UPDATE_LOCK = new int[]{};

    private static final ErrorCallbackBridge ERROR_CALLBACK_BRIDGE =
            new ErrorCallbackBridge();

    private static final MessageDispatcher.DeliveryCallback DELIVERY_CALLBACK =
        new DeliveryCallback() {
            @Override
            public void delivered(List<Message> messages) {
                for (Message message : messages) {
                    if (message.getType() == Type.ALERT || message.getType() == Type.DATA) {
                        removeOnErrorCallback(message.getClientId());
                    }
                }
            }
        };

    public VirtualDeviceImpl(DirectlyConnectedDevice directlyConnectedDevice,
                             String endpointId,
                             DeviceModelImpl deviceModel) {
        super();
        this.base = new VirtualDeviceBase(this, endpointId, deviceModel);
        this.directlyConnectedDevice = directlyConnectedDevice;
        this.attributeMap = createAttributeMap(this, deviceModel);

        // device policy related stuff
        this.pipelineDataCache = new HashMap<String,List<Map<String,Object>>>();
        this.devicePolicyManager = DevicePolicyManager.getDevicePolicyManager(directlyConnectedDevice);

//        // set up device model policies
//        DevicePolicy devicePolicy =
//                devicePolicyManager.getPolicy(deviceModel.getURN(), getEndpointId());
//
//        if (devicePolicy != null) {
//            final Set<String> assignedDevices = new HashSet<String>();
//            assignedDevices.add(getEndpointId());
//            policyAssigned(devicePolicy, assignedDevices);
//        }

        this.devicePolicyManager.addChangeListener(this);

        final MessageDispatcher messageDispatcher =
                MessageDispatcher.getMessageDispatcher(directlyConnectedDevice);

        final RequestDispatcher requestDispatcher =
                messageDispatcher.getRequestDispatcher();

        // handler for all attribute and action requests
        requestDispatcher.registerRequestHandler(endpointId, "deviceModels/"+deviceModel.getURN(), this);

        ERROR_CALLBACK_BRIDGE.add(this);
        messageDispatcher.setOnError(ERROR_CALLBACK_BRIDGE);
        messageDispatcher.setOnDelivery(DELIVERY_CALLBACK);
    }

    private static Map<String, VirtualDeviceAttributeBase<VirtualDevice, Object>> createAttributeMap(VirtualDeviceImpl virtualDevice, DeviceModel deviceModel) {

        final Map<String, VirtualDeviceAttributeBase<VirtualDevice, Object>> map =
                new HashMap<String, VirtualDeviceAttributeBase<VirtualDevice, Object>>();

        if (deviceModel instanceof DeviceModelImpl) {
            DeviceModelImpl deviceModelImpl = (DeviceModelImpl) deviceModel;
            for(DeviceModelAttribute attribute : deviceModelImpl.getDeviceModelAttributes().values()) {
                VirtualDeviceAttributeImpl<Object> vda = new VirtualDeviceAttributeImpl(virtualDevice, attribute);
                map.put(attribute.getName(), vda);
                String alias = attribute.getAlias();
                if (alias != null && alias.length() != 0) {
                    map.put(alias, vda);
                }
            }
        }
        return map;
    }

    /**
     * VirtualDeviceBase.Adapter API
     * {@inheritDoc}
     */
    @Override
    public VirtualDeviceAttributeBase<VirtualDevice, Object> getAttribute(String attributeName) {

        final VirtualDeviceAttributeBase<VirtualDevice, Object> virtualDeviceAttribute =
                attributeMap.get(attributeName);

        if (virtualDeviceAttribute == null) {
            throw new IllegalArgumentException
                ("no such attribute '" + attributeName +
                        "'.\n\tVerify that the URN for the device model you created " +
                        "matches the URN that you use when activating the device in " +
                        "the Java application.\n\tVerify that the attribute name " +
                        "(and spelling) you chose for your device model matches the " +
                        "attribute you are setting in the Java application.");
        }

        return virtualDeviceAttribute;
    }


    /**
     * VirtualDeviceBase.Adapter API
     * {@inheritDoc}
     */
    @Override
    public void setValue(VirtualDeviceAttributeBase<VirtualDevice, Object> attribute, Object value) {

        if (attribute == null) {
            throw new IllegalArgumentException("attribute may not be null");
        }

        attribute.set(value);
    }

    /**
     * Set all the attributes in an update batch. Errors are handled in the
     * set call, including calling the on error handler.
     * {@inheritDoc}
     * @param updatedAttributes
     */
    @Override
    public void updateFields(List<Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>> updatedAttributes) {

        final Set<String> updatedAttributeSet = new HashSet<String>();

        final Iterator<Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>> iterator
                = updatedAttributes.iterator();

        synchronized (UPDATE_LOCK) {
            while (iterator.hasNext()) {
                final Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object> entry = iterator.next();
                final VirtualDeviceAttributeBase<VirtualDevice, Object> attribute = entry.getKey();
                final Object value = entry.getValue();
                final String attributeName = attribute.getDeviceModelAttribute().getName();

                try {

                    // Here, we assume
                    // 1. That attribute is not null. If the attribute were not found
                    //    an exception would have been thrown from the VirtualDevice
                    //    set(String attributeName, T value) method.
                    // 2. That the set method validates the value. The call to
                    //    update here should not throw an exception because the
                    //    value is bad.
                    // 3. The author of this code knew what he was doing.
                    if (!attribute.update(value)) {
                        iterator.remove();
                    } else {
                        updatedAttributeSet.add(attributeName);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    DeviceFunction.removeInProcessValue(getEndpointId(), getDeviceModel().getURN(), attributeName);
                }
            }

            // Here is the check to see if the updated attributes will trigger computedMetrics.
            // There returned set are the attributes whose computedMetrics resulted in an
            // attribute.update(value). Those attributes are added to the list of updated attributes
            // so that they are included in the resulting data message.
            final Set<String> computedMetrics = checkComputedMetrics(updatedAttributeSet);
            if (!computedMetrics.isEmpty()) {
                for (String attr : computedMetrics) {
                    final VirtualDeviceAttributeBase attributeBase = getAttribute(attr);
                    final Object value = attributeBase.get();
                    final Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object> pair =
                            new Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>(attributeBase, value);
                    updatedAttributes.add(pair);
                }
            }
        }
        // This block removes an attribute from processOnChange if the attribute is used
        // in a computed metric.
//        TODO: This needs to be handled at the policy level.
//        synchronized (computedMetricTriggerMap) {
//
//            for (Pair<Set<String>, String> entry : computedMetricTriggerMap) {
//                // This is the set of attributes that the formula refers to.
//                final Set<String> triggers = entry.getKey();
//                final Iterator<String> keyIterator = triggers.iterator();
//                while (keyIterator.hasNext()) {
//                    final String trigger = keyIterator.next();
//                    final Iterator<Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>> updatedAttributesIterator
//                            = updatedAttributes.iterator();
//                    while (updatedAttributesIterator.hasNext()) {
//                        final Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object> pair =
//                                updatedAttributesIterator.next();
//                        final VirtualDeviceAttributeBase<VirtualDevice, Object> attr = pair.getKey();
//                        if (trigger.equals(attr.getDeviceModelAttribute().getName())) {
//                            updatedAttributesIterator.remove();
//                            break;
//                        }
//                    }
//                }
//            }
//        }

        processOnChange(updatedAttributes);

    }

    @Override
    public DeviceModel getDeviceModel() {
        return base.getDeviceModel();
    }

    @Override
    public String getEndpointId() {
        return base.getEndpointId();
    }

    @Override
    public <T> T get(String attributeName) {
        VirtualDeviceAttributeBase<VirtualDevice, Object> attribute = getAttribute(attributeName);
        return (T)attribute.get();
    }

    @Override
    public <T> T getLastKnown(String attributeName) {
        VirtualDeviceAttributeBase<VirtualDevice, Object> attribute = getAttribute(attributeName);
        return (T)attribute.getLastKnown();
    }

    @Override
    public <T> VirtualDevice set(String attributeName, T value) {
        base.set(attributeName, value);
        return this;
    }

    @Override
    public VirtualDevice update() {
        base.update();
        return this;
    }

    @Override
    public void finish() {
        base.finish();
    }

    @Override
    public <T> VirtualDevice offer(String attributeName, T value) {

        final VirtualDeviceAttributeBase<VirtualDevice,Object> attribute = getAttribute(attributeName);

        if (!attribute.isSettable()) {
            throw new UnsupportedOperationException("attempt to modify read-only attribute '" + attributeName + "'");
        }

        final DevicePolicy devicePolicy =
                    devicePolicyManager.getPolicy(getDeviceModel().getURN(), getEndpointId());

        if (devicePolicy == null) {
            return set(attributeName, value);
        }

        final List<DevicePolicy.Function> pipeline = devicePolicy.getPipeline(attributeName);
        if (pipeline == null || pipeline.isEmpty()) {
            return set(attributeName, value);
        }

        final List<Map<String,Object>> pipelineData = getPipelineData(attributeName);

        final T policyValue = (T)offer0(attribute.getDeviceModelAttribute(), value, pipeline, pipelineData);
        if (policyValue != null) {
            if (getLogger().isLoggable(Level.FINE)) {
                getLogger().log(Level.FINE,
                        getEndpointId() +
                                " : Set   : \"" + attributeName + "\"=" + policyValue);
            }
            if (base.isUpdateMode()) {
                set(attributeName, policyValue);
            } else {
                //
                // Handle calling offer outside of an update when there are computed metrics involved.
                //
                // Call updateFields ito ensure the computed metrics get run,
                // and will put this attribute and computed attributes into
                // one data message.
                //
                final List<Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>> updatedAttributes =
                        new ArrayList<Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>>(1);

                updatedAttributes.add(
                        new Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>(
                                attribute,
                                policyValue
                        )
                );

                updateFields(updatedAttributes);
            }
        }
        return this;
    }

    // This is the main logic for handling a policy pipeline.
    // Returns true if attribute is set.
   private Object offer0(DeviceModelAttribute attribute, Object value,
                          List<DevicePolicy.Function> pipeline,
                          List<Map<String,Object>> pipelineData) {

        final String attributeName = attribute.getName();

        Object policyValue = value;

       if (pipeline != null && !pipeline.isEmpty()) {

           // offer0 can be entered by different threads.
           // make sure only one at a time thread can modify
           synchronized (UPDATE_LOCK) {

               // InProcessValue helps resolve $(<attribute-name>) in formulas and conditions
               DeviceFunction.putInProcessValue(getEndpointId(), getDeviceModel().getURN(), attributeName, policyValue);

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

                   if (deviceFunction.apply(this, attributeName, parameters, functionData, policyValue)) {
                       Object valueFromPolicy = deviceFunction.get(
                               this,
                               attributeName,
                               parameters,
                               functionData
                       );
                       if (valueFromPolicy != null) {
                           policyValue = cast(attribute.getType(), valueFromPolicy);
                           DeviceFunction.putInProcessValue(getEndpointId(), getDeviceModel().getURN(), attributeName, policyValue);
                       } else {
                           if (getLogger().isLoggable(Level.FINEST)) {
                               getLogger().log(Level.FINEST, attributeName +
                                       " got null value from policy" + deviceFunction.getDetails(parameters));
                           }
                           return null;
                       }

                   } else {
                       if (getLogger().isLoggable(Level.FINEST)) {
                           if (deviceFunction.getId().startsWith("filter")) {
                               getLogger().log(Level.FINEST,
                                       "VirtualDevice: " + getEndpointId() +
                                               ": offer '" + attributeName + "' = " + policyValue +
                                               " rejected by policy '" + deviceFunction.getDetails(parameters) + "'");
                           }
                       }
                       return null;
                   }
               }
           }

       }

       return policyValue;
   }

    @Override
    public void setOnChange(String attributeName, ChangeCallback callback) {
        VirtualDeviceAttribute attribute = getAttribute(attributeName);
        attribute.setOnChange(callback);
    }

    @Override
    public void setOnChange(ChangeCallback callback) {
        base.setOnChange(callback);
    }

    ChangeCallback getChangeCallback() {
        return base.getOnChangeCallback();
    }

    @Override
    public void setOnError(ErrorCallback callback) {
        base.setOnError(callback);
    }

    ErrorCallback getErrorCallback() {
        return base.getOnErrorCallback();
    }

    @Override
    public void setOnError(String attributeName, ErrorCallback<VirtualDevice> callback) {
        VirtualDeviceAttribute<VirtualDevice,?> attribute = getAttribute(attributeName);
        attribute.setOnError(callback);
    }

    @Override
    public ResponseMessage handleRequest(RequestMessage requestMessage) throws Exception {

            //
            // Dispatch the request message to the appropriate handler for the method
            //

            final String method = requestMessage.getMethod().toUpperCase(Locale.ROOT);

            StatusCode responseStatus = StatusCode.BAD_REQUEST;

            if ("POST".equals(method)) {
                String methodOverride =
                        requestMessage.getHeaderValue("X-HTTP-Method-Override");
                if ("PATCH".equalsIgnoreCase(methodOverride)) {
                    responseStatus = handlePatch(requestMessage);
                } else {
                    responseStatus = handlePost(requestMessage);
                }

            } else if ("PUT".equals(method)) {
                responseStatus = handlePut(requestMessage);

            } else if ("PATCH".equals(method)) {
                responseStatus = handlePatch(requestMessage);

//        } else if ("GET".equals(method)) {
//            For a GET, the server should return the value

            } else {
                getLogger().log(Level.SEVERE, "unexpected method: " + method);
            }

            return new ResponseMessage.Builder(requestMessage)
                    .statusCode(responseStatus)
                    .build();
    }

    // Patch is always a multi-attribute PUT
    private StatusCode handlePatch(RequestMessage requestMessage) {
        NamedValue<?> root = null;
        VirtualDeviceBase.NamedValueImpl<?> last = null;
        try {
            byte[] rawData = requestMessage.getBody();
            String json = new String(rawData, "UTF-8");
            JSONObject jsonObject = new JSONObject(json);

            if (base.getOnChangeCallback() == null) {
                /*
                 * If there is no device level onChange callback then all
                 * attributes in the message must have handlers.
                 */
                Iterator<String> keys = jsonObject.keys();
                while (keys.hasNext()) {
                    String attributeName = keys.next();
                    Object jsonValue = jsonObject.get(attributeName);

                    /*
                     * Throws IllegalArgumentException if attribute is not
                     * in model.
                     */
                    VirtualDeviceAttributeImpl attribute =
                        (VirtualDeviceAttributeImpl)getAttribute(attributeName);

                    if (attribute.getOnChange() == null) {
                        getLogger().log(Level.INFO, "No handler for: '" +
                                requestMessage.getMethod().toUpperCase(Locale.ROOT) + " " + requestMessage.getURL());
                        return StatusCode.NOT_FOUND;
                    }
                }
            }

            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String attributeName = keys.next();
                Object jsonValue = jsonObject.get(attributeName);

                // Throws IllegalArgumentException if attribute is not in model
                VirtualDeviceAttributeImpl attribute =
                    (VirtualDeviceAttributeImpl) getAttribute(attributeName);

                DeviceModelAttribute dma = attribute.getDeviceModelAttribute();
                Object newValue = getValue(dma.getType(), jsonValue, attributeName);

                // Throws IllegalArgumentException if newValue isn't right type
                attribute.validate(dma, newValue);

                final Object oldValue = attribute.get();
                // Do not update the value, or call onChange, if the value hasn't changed.
                if (oldValue != null ? oldValue.equals(newValue) : newValue == null) {
                    continue;
                }

                VirtualDeviceBase.NamedValueImpl<?> nameValue =
                    new VirtualDeviceBase.NamedValueImpl<Object>(attributeName,
                    newValue);

                if (attribute.getOnChange() != null) {
                    attribute.getOnChange().onChange(
                        new VirtualDeviceBase.ChangeEvent<VirtualDevice>(
                        this, nameValue));
                }

                if (last != null) {
                    last.setNext(nameValue);
                    last = nameValue;
                } else {
                    root = last = nameValue;
                }
            }
        } catch (Exception e) {
            //
            // The onChange method may throw exceptions. Since the CL has no
            // knowledge of what these might be, Exception is caught here.
            //
            // Possible exceptions from CL internals are JSONException,
            // IllegalArgumentException, NullPointerException, and
            // ClassCastException.
            //
            getLogger().log(Level.SEVERE, e.getMessage(), e);
            return StatusCode.BAD_REQUEST;
        }

        // root == null just means that no values were updated,
        // which can happen if the new values equal the old values.
        if (root == null) {
            return StatusCode.ACCEPTED;
        }

        // patch is handled by change callback set on VirtualDevice
        ChangeCallback<VirtualDevice> changeCallback =
            base.getOnChangeCallback();
        try {
            if (changeCallback != null) {
                changeCallback.onChange(
                    new VirtualDeviceBase.ChangeEvent<VirtualDevice>(
                            this,
                            root
                    )
                );
            }

            List<DataItem<?>> dataItems = new ArrayList<DataItem<?>>();

            // If no exception, then assume the event was handled.
            // Now go update the values.
            for (NamedValue<?> namedValue = root; namedValue != null;
                    namedValue = namedValue.next()) {
                String attributeName = namedValue.getName();
                VirtualDeviceAttributeImpl attribute =
                    (VirtualDeviceAttributeImpl) getAttribute(attributeName);
                Object newValue = namedValue.getValue();

                // Call update, not set! Update sets the value but does not
                // cause a DataMessage to be sent. We want to send one
                // DataMessage with all the updated data items.
                //
                // There should be no issues with calling update here. All the
                // checking was done while building the event chain, and
                // newValue should be the converted data from the
                // RequestMessage.
                attribute.update(newValue);

                DeviceModelAttribute dma = attribute.getDeviceModelAttribute();
                switch (dma.getType()) {
                    case INTEGER:
                        dataItems.add(new DataItem<Object>(attributeName,
                            (Integer)newValue));
                        break;
                    case NUMBER:
                        dataItems.add(new DataItem<Object>(attributeName,
                            ((Number)newValue).doubleValue()));
                        break;
                    case BOOLEAN:
                        dataItems.add(new DataItem<Object>(attributeName,
                            (Boolean)newValue));
                        break;
                    case URI:
                        dataItems.add(new DataItem<Object>(attributeName,
                            ((oracle.iot.client.ExternalObject)newValue).getURI()));
                        break;
                    case STRING:
                        dataItems.add(new DataItem<Object>(attributeName,
                            (String)newValue));
                        break;
                    case DATETIME:
                        dataItems.add(new DataItem<Object>(attributeName,
                            (Long)newValue));
                        break;
                    default:
                        getLogger().log(Level.SEVERE, "'"+
                            dma.getType() + "' not handled");
                }
            }

            DataMessage dataMessage = new DataMessage.Builder()
                    .format(getDeviceModel().getURN() + ":attributes")
                    .source(getEndpointId())
                    .dataItems(dataItems)
                    .build();

            MessageDispatcher messageDispatcher =
                MessageDispatcher.getMessageDispatcher(directlyConnectedDevice);
            messageDispatcher.queue(dataMessage);

            return StatusCode.ACCEPTED;

        } catch (Exception e) {
            // The onChange method may throw exceptions. Since the CL has no
            // knowledge of what these might be, Exception is caught here.
            getLogger().log(Level.SEVERE, e.getMessage(), e);

            return StatusCode.INTERNAL_SERVER_ERROR;
        }

    }

    // POST is for actions
    private StatusCode handlePost(RequestMessage requestMessage) {
        final String path = requestMessage.getURL();
        final String dmURN = "deviceModels/"+getDeviceModel().getURN()+"/actions";
        final String actionName =
                dmURN.regionMatches(0, path, 0, dmURN.length())
                        ? path.substring(dmURN.length()+1)
                        : path;
		final Callable callable;
		if (actionMap == null) {
			callable = null;
		} else {
			synchronized (actionMapLock) {
				callable = actionMap.get(actionName);
			}
		}
        if (callable != null) {
            try {
                DeviceModelAction action
                        = getDeviceModelAction(base.getDeviceModel(), actionName);
                Object data = action.getArgType() != null
                        ? getValue(action.getArgType(), requestMessage.getBody(), actionName)
                        : null;
                callable.call(this, data);
                return StatusCode.ACCEPTED;
            } catch (Exception e) {
                // The call method may throw exceptions. Since the CL has no
                // knowledge of what these might be, Exception is caught here.
                //
                // Possible exceptions from CL internals are JSONException,
                // IllegalArgumentException, NullPointerException, and
                // ClassCastException.
                //
                getLogger().log(Level.FINEST, e.getMessage(), e);
                return  StatusCode.BAD_REQUEST;
            }
        }

        getLogger().log(Level.INFO, "No handler for: '" +
                requestMessage.getMethod().toUpperCase(Locale.ROOT) + " " + requestMessage.getURL());

        return StatusCode.NOT_FOUND;

    }

    // PUT is for attributes or for resources
    private StatusCode handlePut(RequestMessage requestMessage) {

        try {

            final String path = requestMessage.getURL();
            final String dmURN =
                    "deviceModels/"+getDeviceModel().getURN()+"/attributes";
            final String attributeName =
                    dmURN.regionMatches(0, path, 0, dmURN.length())
                            ? path.substring(dmURN.length()+1)
                            : path;

            final VirtualDeviceAttributeBase<VirtualDevice, Object>
                    virtualDeviceAttribute = getAttribute(attributeName);
            final DeviceModelAttribute deviceModelAttribute =
                    virtualDeviceAttribute.getDeviceModelAttribute();
            final DeviceModelAttribute.Type attributeType =
                    deviceModelAttribute.getType();

            final byte[] data = requestMessage.getBody();
            final Object newValue = getValue(attributeType,data,attributeName);
            final Object oldValue = virtualDeviceAttribute.get();
            // Do not update the value, or call onChange, if the value hasn't changed.
            if (oldValue != null ? oldValue.equals(newValue) : newValue == null) {
                return StatusCode.ACCEPTED;
            }

            final NamedValue<?> namedValue =
                    new VirtualDeviceBase.NamedValueImpl<Object>(attributeName, newValue);
            boolean attrOnChangeCalled = false;

            if (virtualDeviceAttribute.getOnChange() != null) {
                virtualDeviceAttribute.getOnChange().onChange(
                        new VirtualDeviceBase.ChangeEvent<VirtualDevice>(
                                this,
                                namedValue
                        )
                );

                attrOnChangeCalled = true;
            }

            if (base.getOnChangeCallback() != null) {
                base.getOnChangeCallback().onChange(
                        new VirtualDeviceBase.ChangeEvent<VirtualDevice>(
                                this,
                                namedValue
                        )
                );

                attrOnChangeCalled = true;
            }


            if (attrOnChangeCalled) {
                // Call set, not update! Update sets the value but does not
                // cause a DataMessage to be sent. Since the put is for a
                // single attribute only, we want the DataMessage to be sent.
                // This is different than handlePatch.
                virtualDeviceAttribute.set(newValue);
                return StatusCode.ACCEPTED;

            } else {

                getLogger().log(Level.INFO, "No handler for: '" +
                        requestMessage.getMethod().toUpperCase(Locale.ROOT) + " " + requestMessage.getURL());
                return StatusCode.NOT_FOUND;
            }


        } catch (Exception e) {
            // The call method may throw exceptions. Since the CL has no
            // knowledge of what these might be, Exception is caught here.
            //
            // Possible exceptions from CL internals are JSONException,
            // IllegalArgumentException, NullPointerException, and
            // ClassCastException.
            //
            getLogger().log(Level.FINEST, e.getMessage(), e);
            return StatusCode.BAD_REQUEST;
        }

    }

	private final Object actionMapLock = new Object();
    private volatile Map<String, Callable<?>> actionMap;

    private static DeviceModelAction getDeviceModelAction(DeviceModelImpl deviceModel, String actionName) {
        if (deviceModel != null) {
            Map<String, DeviceModelAction> actions = deviceModel.getDeviceModelActions();
            if (!actions.isEmpty()) {
                DeviceModelAction act = actions.get(actionName);
                if (act != null)
                    return act;
                for (DeviceModelAction action : actions.values()) {
                    if (actionName.equals(action.getAlias())) {
                        return action;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void setCallable(String actionName, Callable<?> callable) {
        if (actionMap == null) {
			synchronized (actionMapLock) {
				if (actionMap == null) {
					actionMap = new HashMap<String, Callable<?>>();
				}
			}
        }

        Map<String, DeviceModelAction> actions = base.getDeviceModel().getDeviceModelActions();
        DeviceModelAction deviceModelAction = actions.get(actionName);
        if (deviceModelAction == null) {
            for(DeviceModelAction action : actions.values()) {
                if (actionName.equals(action.getAlias())) {
                    deviceModelAction = action;
                    break;
                }
            }
        }
        if (deviceModelAction == null) {
            throw new IllegalArgumentException("action not found in model");
        }

		synchronized (actionMapLock) {
			actionMap.put(deviceModelAction.getName(), callable);
			if (deviceModelAction.getAlias() != null) {
				actionMap.put(deviceModelAction.getAlias(), callable);
			}
		}
    }

    @Override
    public Alert createAlert(String format) {
        return new AlertImpl(this, format);
    }

    @Override
    public Data createData(String format) {
        return new DataImpl(this, format);
    }

    // called from VirtualDeviceAttributeImpl
    <T> void processOnChange(VirtualDeviceAttribute<VirtualDevice, T> virtualDeviceAttribute, Object newValue) {

        final DataMessage.Builder builder = new DataMessage.Builder();
        WritableValue<StorageObjectImpl> storageObject = new WritableValue<StorageObjectImpl>();
        try {
            processOnChange(builder, (VirtualDeviceAttributeImpl) virtualDeviceAttribute, newValue, storageObject);
        } catch (RuntimeException re) {
            return;
        }
        DataMessage dataMessage = builder.build();

        try {
            queueMessage(dataMessage, storageObject.getValue());
        } catch(ArrayStoreException e) {
            // MessageDispatcher queue is full.
            Set<VirtualDeviceAttributeBase<VirtualDevice, Object>> attributes =
                    new HashSet<VirtualDeviceAttributeBase<VirtualDevice, Object>>(1);
            attributes.add((VirtualDeviceAttributeImpl)virtualDeviceAttribute);
            notifyException(attributes, e);
        }
    }

    // Also called from AlertImpl
    void queueMessage(Message message, StorageObjectImpl storageObject)
            throws ArrayStoreException {

        final Pair<Message,StorageObjectImpl> pair =
                new Pair<Message,StorageObjectImpl>(message, storageObject);

        Pair<Message,StorageObjectImpl>[] pairs = new Pair[] {pair};

        final String deviceModelURN = getDeviceModel().getURN();

        DevicePolicy devicePolicy =
                    devicePolicyManager.getPolicy(getDeviceModel().getURN(), getEndpointId());

        //
        // Handling of device model level policies here...
        //
        if (devicePolicy != null && devicePolicy.getPipeline(DevicePolicy.ALL_ATTRIBUTES()) != null) {

            // Some policies are negated by an alert of a given severity
            // (batching policies, for example)
            final AlertMessage.Severity alertSeverity;
            if (message instanceof AlertMessage) {
                AlertMessage alertMessage = (AlertMessage)message;
                alertSeverity = alertMessage.getSeverity();
            } else {
                alertSeverity = null;
            }

            final List<DevicePolicy.Function> pipeline = devicePolicy.getPipeline(DevicePolicy.ALL_ATTRIBUTES());

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

            final List<Map<String,Object>> pipelineData = getPipelineData(DevicePolicy.ALL_ATTRIBUTES());

//          TODO: handle more than one function in a pipeline for all-attributes
//          Note: A warning is printed out, several lines above this, if there
//          is more than one function for an all-attributes policy.
//          Leaving original line here, commented-out, for future reference.
//          for (int index = 0, maxIndex = pipeline.size(); index < maxIndex; index++) {
            for (int index=0, maxIndex=1; index<maxIndex; index++) {

                final DevicePolicy.Function function = pipeline.get(index);
                final String id = function.getId();
                final Map<String, ?> parameters = function.getParameters();

                final DeviceFunction deviceFunction =
                        DeviceFunction.getDeviceFunction(id);

                if (deviceFunction == null) {
                    continue;
                }

//                if ("batchByTime".equals(deviceFunction.getId())) {
//                    continue;
//                }

                final boolean alertOverridesPolicy;
                if (alertSeverity != null) {
                    AlertMessage.Severity configuredSeverity = AlertMessage.Severity.CRITICAL;
                    final String criterion = (String)parameters.get("alertSeverity");
                    if (criterion != null) {
                        try {
                            configuredSeverity =  AlertMessage.Severity.valueOf(criterion);
                        } catch (IllegalArgumentException e) {
                            configuredSeverity = AlertMessage.Severity.CRITICAL;
                        }
                    }
                    alertOverridesPolicy = configuredSeverity.compareTo(alertSeverity) <= 0;

                } else {
                    alertOverridesPolicy = false;
                }

                final Map<String, Object> functionData;
                if (index < pipelineData.size()) {
                    functionData = pipelineData.get(index);
                } else {
                    functionData = new HashMap<String,Object>();
                    pipelineData.add(functionData);
                }

                if (deviceFunction.apply(this, null, parameters, functionData, pair) || alertOverridesPolicy) {

                    // If the policy was scheduled
                    final long window = DeviceFunction.getWindow(parameters);
                    if (window > 0) {
                        final long slide = DeviceFunction.getSlide(parameters, window);
                        final ScheduledPolicyData.Key key = new ScheduledPolicyData.Key(window, slide);
                        final ScheduledPolicyData scheduledPolicyData = scheduledPolicies.get(key);
                        final long timeZero = System.currentTimeMillis();
                        if (scheduledPolicyData != null) {
                            final List<Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>> updatedAttributes
                                    = new ArrayList<Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>>();
                            scheduledPolicyData.processExpiredFunction(this, updatedAttributes, timeZero);
                            if (!updatedAttributes.isEmpty()) {
                                updateFields(updatedAttributes);
                            }
                            return;
                        }
                    }

                    List<Pair> value = (List<Pair>)deviceFunction.get(this, null, parameters, functionData);
                    pairs = value.toArray(new Pair[value.size()]);
                    if (getLogger().isLoggable(Level.FINE)) {
                        getLogger().log(Level.FINE,
                                "VirtualDevice: " + getEndpointId() +
                                        " dispatching " + pairs.length +
                                        " messages per policy '" +
                                        deviceFunction.getDetails(parameters) + "'" );
                    }
                } else {
                    return;
                }
            }
        }

        try {

            Message[] messages = new Message[pairs.length];

            MessageDispatcherImpl messageDispatcher =
                (MessageDispatcherImpl)MessageDispatcher.getMessageDispatcher(directlyConnectedDevice);

            for (int n=0; n<messages.length; n++) {
                messages[n] = pairs[n].getKey();
                StorageObjectImpl so = pairs[n].getValue();
                if (so != null) {
                    messageDispatcher.addStorageObjectDependency(so, message.getClientId());
                    so.sync();
                }
            }

            messageDispatcher.queue(messages);

        } catch (ArrayStoreException e) {
            throw e;
        } catch(Throwable t) {
            getLogger().log(Level.SEVERE, t.toString());
        }

    }

    // called from updateFields
    private void processOnChange(List<Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>> updatedAttributes) {

        if (updatedAttributes.isEmpty()) return;

        final Set<VirtualDeviceAttributeBase<VirtualDevice, Object>> keySet =
                new HashSet<VirtualDeviceAttributeBase<VirtualDevice, Object>>();

        final DataMessage.Builder builder = new DataMessage.Builder();
        WritableValue<StorageObjectImpl> storageObject = new WritableValue<StorageObjectImpl>();

        for (Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object> entry : updatedAttributes) {

            final VirtualDeviceAttributeBase<VirtualDevice, Object> attribute = entry.getKey();
            keySet.add(attribute);
            final Object newValue = entry.getValue();

            try {
                processOnChange(builder, attribute, newValue, storageObject);
            } catch(RuntimeException re) {
                getLogger().log(Level.SEVERE, re.getMessage(), re);
                return;
            }
        }
        DataMessage dataMessage = builder.build();

        try {
            queueMessage(dataMessage, storageObject.getValue());
        } catch(ArrayStoreException e) {
            // MessageDispatcher queue is full.
            notifyException(keySet, e);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, e.getMessage(), e);
        }
    }

    void handleStorageObjectStateChange(StorageObjectImpl so) {
        MessageDispatcherImpl messageDispatcherImpl =
                (MessageDispatcherImpl)MessageDispatcher.getMessageDispatcher(directlyConnectedDevice);
        messageDispatcherImpl.removeStorageObjectDependency(so);
    }

    @Override
    public String toString() {
        return base.toString();
    }

    private Object getValue(DeviceModelAttribute.Type attributeType,
            byte[] payload, String name) throws JSONException, IllegalArgumentException {
        String json;
        try {
             json = new String(payload, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new JSONException(e.getMessage());
        }

        JSONObject jsonObject = new JSONObject(json);
        Object jsonValue = jsonObject.opt("value");
        if (jsonValue == null) {
            throw new IllegalArgumentException("bad payload " +
                String.valueOf(jsonObject));
        }

        return getValue(attributeType, jsonValue, name);
    }

    private Object getValue(DeviceModelAttribute.Type attributeType,
            Object jsonValue, String name) throws IllegalArgumentException {

        if (jsonValue == null) {
            return null;
        }

        Object data = null;

        switch (attributeType) {
            case BOOLEAN:
                if (!(jsonValue instanceof Boolean)) {
                    throw new IllegalArgumentException("value is not BOOLEAN");
                }

                data = jsonValue;
                break;

            case INTEGER:
                if (!(jsonValue instanceof Number)) {
                    throw new IllegalArgumentException("value is not NUMBER");
                }

                data = ((Number)jsonValue).intValue();
                break;

            case NUMBER:
                if (!(jsonValue instanceof Number)) {
                    throw new IllegalArgumentException("value is not NUMBER");
                }

                data = jsonValue;
                break;

            case DATETIME:
                if (!(jsonValue instanceof Number)) {
                    throw new IllegalArgumentException("value is not NUMBER");
                }

                data = ((Number)jsonValue).longValue();
                break;

            case STRING:
                if (!(jsonValue instanceof String)) {
                    throw new IllegalArgumentException("value is not STRING");
                }

                data = jsonValue;
                break;
            case URI:
                if (!(jsonValue instanceof String)) {
                    throw new IllegalArgumentException("value is not STRING");
                }
                try {
                    if (StorageConnectionBase.isStorageCloudURI((String)jsonValue)) {
                        try {
                            com.oracle.iot.client.StorageObject delegate =
                                    directlyConnectedDevice.createStorageObject((String)jsonValue);
                            StorageObjectImpl storageObjectImpl = new StorageObjectImpl(directlyConnectedDevice, delegate);
                            storageObjectImpl.setSyncEventInfo(this, name);
                            data = storageObjectImpl;
                            break;
                        } catch (Exception e) {
                            //If SCS object in inaccessable, log it and create ExternalObject data instead.
                            getLogger().log(Level.WARNING, "Storage CS object access failed: " + e.getMessage());
                        }
                    }
                    data = new oracle.iot.client.ExternalObject((String)jsonValue);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Cannot get value for attribute" + name, e);
                }
                break;

            default:
                throw new IllegalArgumentException("unexpected type '" +
                    attributeType + "'");
        }

        return data;
    }

    //
    // Hooks for handling Alert and Data onError callbacks.
    //
    // When AlertImpl#raise() or DataImpl#submit() are called, the message clientId
    // and the callback are added to onErrorCallbacks map via addOnErrorCallback.
    // The message's clientId is the key, and a weak reference to the callback is
    // the value. If the message from calling Alert#raise() or Data#submit() cannot
    // be delivered, then ErrorCallbackBridge#failed(List<Message>, Exception) method
    // ends up getting called (from MessageDispatcherImpl.Transmitter send method).
    // The ErrorCallbackBridge#invokeErrorCallback does a removeOnErrorCallback,
    // if the message is an alert format or a data format, and invokes that callback
    // instead of the one for the virtual device.
    // If the message is delivered successfully, the entry is removed by way of
    // MessageDispatcher calling DELIVERY_CALLBACK
    ////
    /* package */ static final Map<String, WeakReference<ErrorCallback<VirtualDevice>>> onErrorCallbacks =
        new HashMap<String, WeakReference<ErrorCallback<VirtualDevice>>>();

    /* package */ static final void addOnErrorCallback(String messageId, ErrorCallback<VirtualDevice> callback) {
        final WeakReference<ErrorCallback<VirtualDevice>> ref = new WeakReference<ErrorCallback<VirtualDevice>>(callback);
        synchronized (onErrorCallbacks) {
            onErrorCallbacks.put(messageId, ref);
        }
    }

    /* package */ static final ErrorCallback<VirtualDevice> removeOnErrorCallback(String messageId) {
        final WeakReference<ErrorCallback<VirtualDevice>> ref;
        synchronized (onErrorCallbacks) {
            ref = onErrorCallbacks.remove(messageId);
        }
        final ErrorCallback<VirtualDevice> callback;
        if (ref != null && (callback = ref.get()) != null) {
            return callback;
        }
        return null;
    }

    // Replace oldCallback with newCallback in the onErrorCallbacks map
    /* package */ static final void replaceOnErrorCallback(
            ErrorCallback<VirtualDevice> oldCallback,
            ErrorCallback<VirtualDevice> newCallback) {

        final WeakReference<ErrorCallback<VirtualDevice>> newCallbackRef =
            newCallback != null
                ? new WeakReference<ErrorCallback<VirtualDevice>>(newCallback)
                : null;

        if (oldCallback != null) {
            synchronized (onErrorCallbacks) {
                // Operate on copy of keys to avoid concurrent mod.
                final Set<String> keys = new HashSet<String>(onErrorCallbacks.keySet());
                final Iterator<String> iterator = keys.iterator();
                while (iterator.hasNext()) {
                    final String key = iterator.next();
                    final WeakReference<ErrorCallback<VirtualDevice>> entry
                        = onErrorCallbacks.remove(key);
                    if (entry != null) {
                        final ErrorCallback<VirtualDevice> errorCallback = entry.get();
                        if (errorCallback == oldCallback && newCallback != null) {
                            onErrorCallbacks.put(key, newCallbackRef);
                        }
                    }
                }
            }
        }
    }


    private static class ErrorCallbackBridge implements MessageDispatcher.ErrorCallback {

        private final Map<String, WeakReference<VirtualDeviceImpl>> deviceSet =
            new HashMap<String, WeakReference<VirtualDeviceImpl>> ();

        void add(VirtualDeviceImpl virtualDevice) {

            if (deviceSet.size() > 0) {
                // TODO: this will be a performance problem if the map is very large
                final Iterator<Map.Entry<String,WeakReference<VirtualDeviceImpl>>> iterator =
                    deviceSet.entrySet().iterator();
                while(iterator.hasNext()) {
                    final Map.Entry<String,WeakReference<VirtualDeviceImpl>> entry =
                        iterator.next();
                    final WeakReference<VirtualDeviceImpl> ref = entry.getValue();
                    if (ref.get() == null) iterator.remove();
                }
            }

            assert virtualDevice.getEndpointId() != null;
            deviceSet.put(
                virtualDevice.getEndpointId(),
                new WeakReference<VirtualDeviceImpl>(virtualDevice)
            );
        }

        @Override
        public void failed(List<Message> messages, Exception exception) {

            final Map<String,List<Message>> collatedMessages =
                new HashMap<String, List<Message>>(messages.size());

            // Collate the messages by source
            for(Message message : messages) {
                final String source = message.getSource();
                List<Message> list = collatedMessages.get(source);
                if (list == null) {
                    list = new ArrayList<Message>();
                    collatedMessages.put(source, list);
                }
                list.add(message);
            }

            // Call the onError method of
            for(Map.Entry<String,List<Message>> entry : collatedMessages.entrySet()) {

                final String source = entry.getKey();
                final List<Message> list = entry.getValue();

                final WeakReference<VirtualDeviceImpl> ref = deviceSet.get(source);
                if (ref == null) {
                    new Exception("DEBUG source="+source+" deviceSet="+deviceSet).printStackTrace();
                }
                final VirtualDeviceImpl virtualDevice = (ref == null ? null : ref.get());
                if (virtualDevice != null) {
                    invokeErrorCallback(virtualDevice, list, exception);
                }
            }

        }

        // Return true if the format is a data format
        private boolean isDataFormat(VirtualDeviceImpl virtualDevice, String format) {
            if (format != null) {
                final DeviceModelImpl deviceModel = (DeviceModelImpl)virtualDevice.getDeviceModel();
                final Map<String, DeviceModelFormat> deviceModelFormats = deviceModel.getDeviceModelFormats();
                if (deviceModelFormats != null) {
                    final DeviceModelFormat deviceModelFormat = deviceModelFormats.get(format);
                    if (deviceModelFormat != null) {
                        return deviceModelFormat.getType() == DeviceModelFormat.Type.DATA;
                    }
                }
            }
            return false;
        }

        private void invokeErrorCallback(VirtualDeviceImpl virtualDevice, List<Message> messages, Exception exception) {

            VirtualDeviceBase.NamedValueImpl<?> values = null;
            for (Message message : messages) {

                // This flag is set to true if the message is for a ALERT format.
                // We do not want to call the VirtualDevice onError callback for an Alert.
                // Rather, we want to call back the Alert's onError callback.
                boolean isAlertFormat = false;

                // This flag is set to true if the message is for a DATA format.
                // We do not want to call the VirtualDevice onError callback for a Data.
                // Rather, we want to call back the Data's onError callback.
                boolean isDataFormat = false;

                List<DataItem<?>> dataItems = null;
                if (message instanceof DataMessage) {
                    final DataMessage dataMessage = (DataMessage) message;
                    dataItems = dataMessage.getDataItems();
                    // DataMessage could be for attribute data or for DATA format.
                    final String formatUrn = dataMessage.getFormat();
                    isDataFormat = isDataFormat(virtualDevice, formatUrn);
                } else if (message instanceof AlertMessage) {
                    final AlertMessage alertMessage = (AlertMessage) message;
                    dataItems = alertMessage.getDataItems();
                    isAlertFormat = true;
                }

                // This block takes the List<DataItem> and creates a chain of NamedValue.
                if (dataItems != null) {
                    VirtualDeviceBase.NamedValueImpl<?> last = null;
                    for (DataItem<?> dataItem : dataItems) {

                        VirtualDeviceBase.NamedValueImpl<?> namedValue =
                            new VirtualDeviceBase.NamedValueImpl<Object>(
                                dataItem.getKey(),
                                dataItem.getValue());
                        // If this data is not from an alert or data format, then
                        // invoke the attribute specific onError callback. This
                        // is done here because we want to call the onError with
                        // just this one NamedValue, which is for the specific attribute,
                        // and not a chain of NamedValue. This callback is not
                        // invoked for an alert or a data format because the callback
                        // is specifically for an attribute, not a field.
                        if (!isAlertFormat
                            && !isDataFormat
                            && virtualDevice.attributeMap.containsKey(dataItem.getKey())) {
                            try {
                                VirtualDeviceAttributeBase<VirtualDevice, ?> attribute =
                                    virtualDevice.getAttribute(dataItem.getKey());
                                if (attribute.getOnError() != null) {
                                    attribute.getOnError().onError(
                                        new VirtualDeviceBase.ErrorEvent<VirtualDevice>(
                                            virtualDevice,
                                            namedValue,
                                            exception.getMessage()
                                        )
                                    );
                                }
                            } catch (IllegalArgumentException e) {
                                getLogger().log(Level.FINE, e.getMessage(), e);
                            } catch (Exception e) {
                                // onError could throw an exception
                                getLogger().log(Level.FINE, e.getMessage(), e);
                            }
                        }

                        // Add the namedValue to the chain
                        if (last != null) {
                            last.setNext(namedValue);
                            last = namedValue;
                        } else {
                            // values is the root of the chain
                            values = last = namedValue;
                        }
                    }
                }

                final ErrorCallback<VirtualDevice> errorCallback;
                if (isAlertFormat || isDataFormat) {
                    errorCallback = removeOnErrorCallback(message.getClientId());
                } else {
                    errorCallback = virtualDevice.getErrorCallback();
                }

                if (errorCallback != null) {
                    try {
                        VirtualDeviceBase.ErrorEvent<VirtualDevice> errorEvent =
                            new VirtualDeviceBase.ErrorEvent<VirtualDevice>(virtualDevice, values,
                                exception.getMessage());
                        errorCallback.onError(errorEvent);
                    } catch (Exception e) {
                        // onError could throw an exception
                        getLogger().log(Level.FINE, e.getMessage(), e);
                    }
                }
            }
        }
    }


    private void processOnChange(final DataMessage.Builder builder,
                                 VirtualDeviceAttributeBase<VirtualDevice, Object>attribute,
            final Object newValue, WritableValue<StorageObjectImpl> storageObject) {
        final DeviceModelAttribute deviceModelAttribute =
            attribute.getDeviceModelAttribute();

        final String attributeName = deviceModelAttribute.getName();
        builder
            .format(base.getDeviceModel().getURN() + ":attributes")
            .source(base.getEndpointId());

        switch (deviceModelAttribute.getType()) {
        case INTEGER:
        case NUMBER:
            builder.dataItem(attributeName, ((Number) newValue).doubleValue());
            break;
        case STRING:
            builder.dataItem(attributeName, (String) newValue);
            break;
        case URI:
            if (newValue instanceof StorageObjectImpl) {
                StorageObjectImpl storageObjectImpl = (StorageObjectImpl)newValue;
                if ((storageObjectImpl.getSyncStatus() == StorageObject.SyncStatus.NOT_IN_SYNC) ||
                        (storageObjectImpl.getSyncStatus() == StorageObject.SyncStatus.SYNC_PENDING)) {
                    storageObject.setValue(storageObjectImpl);
                }
                storageObjectImpl.setSyncEventInfo(this, attributeName);
            }
            builder.dataItem(attributeName, ((oracle.iot.client.ExternalObject)newValue).getURI());
            break;
        case BOOLEAN:
            builder.dataItem(attributeName, ((Boolean) newValue));
            break;
        case DATETIME:
            if (newValue instanceof Date) {
                builder.dataItem(attributeName, ((Date) newValue).getTime());
            } else if (newValue instanceof Number) {
                builder.dataItem(attributeName, ((Number) newValue).longValue());
            }
            break;
        default:
            getLogger().log(Level.SEVERE, "unknown attribute type " + deviceModelAttribute.getType());
            throw new RuntimeException("unknown attribute type " + deviceModelAttribute.getType());
        }
    }

    private static final ExecutorService errorEventDispatcher =
            Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    final SecurityManager s = System.getSecurityManager();
                    final ThreadGroup group = (s != null) ? s.getThreadGroup() :
                            Thread.currentThread().getThreadGroup();
                    Thread t = new Thread(group, r, "errorEventDispatchingThread", 0);

                    // this is opposite of what the Executors.DefaultThreadFactory does
                    if (!t.isDaemon())
                        t.setDaemon(true);
                    if (t.getPriority() != Thread.NORM_PRIORITY)
                        t.setPriority(Thread.NORM_PRIORITY);
                    return t;
                }
            });

    private void notifyException(Set<VirtualDeviceAttributeBase<VirtualDevice, Object>> attributes, Exception e) {

        final AbstractVirtualDevice.ErrorCallback errorCallback = this.getErrorCallback();
        if (errorCallback == null) {
            return;
        }

        VirtualDeviceBase.NamedValueImpl<Object> head = null, tail = null;
        for (VirtualDeviceAttributeBase<VirtualDevice, Object> attribute : attributes) {
            String name = attribute.getDeviceModelAttribute().getName();
            Object value = attribute.get();
            VirtualDeviceBase.NamedValueImpl<Object> next = new VirtualDeviceBase.NamedValueImpl(name, value);
            if (head != null) {
                tail.setNext(next);
                tail = next;
            } else {
                head = tail = next;
            }
        }

        final ErrorEvent<VirtualDevice> errorEvent =
                new VirtualDeviceBase.ErrorEvent<VirtualDevice>(this, head, e.getMessage());
        final String msg = e.getMessage();

        errorEventDispatcher.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    errorCallback.onError(errorEvent);
                } catch (Exception ignored) {
                    getLogger().log(Level.WARNING, "onError threw: " + msg);
                }
            }
        });
    }

    // Get the pipeline data for the attribute
    private List<Map<String,Object>> getPipelineData(String attribute) {

        DevicePolicy devicePolicy =
                    devicePolicyManager.getPolicy(getDeviceModel().getURN(), getEndpointId());

        if (devicePolicy == null) {
            return Collections.emptyList();
        }

        final List<DevicePolicy.Function> pipeline = devicePolicy.getPipeline(attribute);
        if (pipeline == null || pipeline.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String,Object>> pipelineData = pipelineDataCache.get(attribute);
        if (pipelineData == null) {
            pipelineData = new ArrayList<Map<String,Object>>();
            pipelineDataCache.put(attribute, pipelineData);
        }

        // create missing function maps
        if (pipelineData.size() < pipeline.size()) {
            // create missing function data maps
            for (int n=pipelineData.size(), nMax=pipeline.size(); n<nMax; n++) {
                pipelineData.add(new HashMap<String,Object>());
            }
        }

        return pipelineData;
    }


    //
    // Window based policy support (as in "window", not Windows OS).
    // Have one scheduled task for each policy "slide" value. The slide value is
    // how much to move the window, so we want to run the policy when the slide expires.
    // When the slide expires, the runnable will call back each VirtualDeviceAttribute
    // that has a policy for that slide value.
    //
    // Window and slide are the key.
    // { {window,slide} : ScheduledPolicyData }
    private final Map<ScheduledPolicyData.Key, ScheduledPolicyData> scheduledPolicies =
            new HashMap<ScheduledPolicyData.Key, ScheduledPolicyData>();

    private final TimedPolicyThread timedPolicyThread = new TimedPolicyThread();

    private void addScheduledPolicy(long window, long slide, long timeZero, String attributeName, int pipelineIndex) {
        synchronized (scheduledPolicies) {
            final ScheduledPolicyData.Key key = new ScheduledPolicyData.Key(window, slide);
            ScheduledPolicyData scheduledPolicyData = scheduledPolicies.get(key);
            if (scheduledPolicyData == null) {
                scheduledPolicyData = new ScheduledPolicyData(window, slide, timeZero);
                scheduledPolicies.put(key, scheduledPolicyData);
                timedPolicyThread.addTimedPolicyData(scheduledPolicyData);
                if (!timedPolicyThread.isAlive() && !timedPolicyThread.isCancelled()) {
                    timedPolicyThread.start();
                }
            }
            scheduledPolicyData.addAttribute(attributeName, pipelineIndex);
        }
    }

    private void removeScheduledPolicy(long slide, String attributeName, int pipelineIndex, long window) {
        synchronized (scheduledPolicies) {
            final ScheduledPolicyData.Key key = new ScheduledPolicyData.Key(window, slide);
            final ScheduledPolicyData scheduledPolicyData = scheduledPolicies.get(key);
            if (scheduledPolicyData != null) {
                scheduledPolicyData.removeAttribute(attributeName, pipelineIndex);
                if (scheduledPolicyData.isEmpty()) {
                    scheduledPolicies.remove(key);
                    timedPolicyThread.removeTimedPolicyData(scheduledPolicyData);
                }
            }
        }
    }

    private static class ScheduledPolicyData {

        private static class Key {
            private final long window;
            private final long slide;

            private Key(long window, long slide) {
                this.window = window;
                this.slide = slide;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                Key key = (Key) o;
                return window == key.window &&
                        slide == key.slide;
            }

            @Override
            public int hashCode() {
                return (int) ((window ^ (window >>> 32)) + (slide ^ (slide >>> 32)));
            }
        }

        // Millisecond time in the future at which the policy value should be computed
        private long expiry;

        // How much the window moves is used to calculate expiry.
        private final long slide;

        // { attributeName : pipelineIndex }
        private final Map<String, Integer> pipelineIndices;

        private ScheduledPolicyData(long window, long slide, long timeZero) {

            this.slide = slide;
            this.pipelineIndices = new HashMap<String, Integer>();

            // Initial expiry is window milliseconds past time zero.
            // Tenth of a millisecond resolution helps group
            // intersecting slide values (10 second and 20 second,
            // for example).
            this.expiry = ((window + timeZero) / 10) * 10;
        }

        private void addAttribute(String attributeName, int pipelineIndex) {
            synchronized (pipelineIndices) {
                pipelineIndices.put(attributeName, pipelineIndex);
            }
        }

        private void removeAttribute(String attributeName, int pipelineIndex) {
            synchronized (pipelineIndices) {
                pipelineIndices.remove(attributeName);
            }
        }

        private boolean isEmpty() {
            return pipelineIndices.isEmpty();
        }

        private long getDelay(long now) {
            final long delay = expiry - now;
            return (delay > 0) ? delay : 0;
        }

        private void processExpiredFunction(
                final VirtualDeviceImpl virtualDeviceImpl,
                final List<Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>> updatedAttributes,
                long timeZero
        ) {
            try {
                handleExpiredFunction(virtualDeviceImpl,updatedAttributes);
            } finally {
                // ensure expiry is reset
                // tenth of a millisecond resolution
                this.expiry = ((slide + timeZero) / 10) * 10;
            }
        }

        private void handleExpiredFunction(
                final VirtualDeviceImpl virtualDeviceImpl,
                final List<Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>> updatedAttributes
        ) {

            final DevicePolicy devicePolicy =
                    virtualDeviceImpl.devicePolicyManager.getPolicy(
                            virtualDeviceImpl.getDeviceModel().getURN(),
                            virtualDeviceImpl.getEndpointId());

            if (devicePolicy == null) {
                if (getLogger().isLoggable(Level.SEVERE)) {
                    // TODO: better log message here
                    getLogger().log(Level.SEVERE,
                            "could not find " + virtualDeviceImpl.getDeviceModel().getURN() + " in policy configuration");
                }
                return;
            }

            final Map<String, Integer> pipelineIndicesCopy;
            synchronized (pipelineIndices) {
                pipelineIndicesCopy = new HashMap<String, Integer>(pipelineIndices);
            }

            handleExpiredFunction(virtualDeviceImpl, updatedAttributes, devicePolicy, pipelineIndicesCopy);
        }

        private void handleExpiredFunction(
                final VirtualDeviceImpl virtualDeviceImpl,
                final List<Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>> updatedAttributes,
                DevicePolicy devicePolicy,
                Map<String, Integer> _pipelineIndices) {

            for (Map.Entry<String, Integer> entry : _pipelineIndices.entrySet()) {
                final String attributeName = entry.getKey();
                final Integer pipelineIndex = entry.getValue();
                handleExpiredFunction(virtualDeviceImpl, updatedAttributes, devicePolicy, attributeName, pipelineIndex);
            }
        }

        private void handleExpiredFunction(
                final VirtualDeviceImpl virtualDeviceImpl,
                final List<Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>> updatedAttributes,
                DevicePolicy devicePolicy,
                String attributeName,
                int pipelineIndex) {

            final List<DevicePolicy.Function> pipeline = devicePolicy.getPipeline(attributeName);

            if (pipeline == null || pipeline.isEmpty()) {
                return;
            }

            if (pipeline.size() <= pipelineIndex) {
                if (getLogger().isLoggable(Level.SEVERE)) {
                    // TODO: better log message here
                    getLogger().log(Level.SEVERE,
                            "pipeline does not match configuration");
                }
                return;
            }

            final List<Map<String,Object>> pipelineData = virtualDeviceImpl.getPipelineData(attributeName);
            if (pipelineData.size() <= pipelineIndex) {
                if (getLogger().isLoggable(Level.SEVERE)) {
                    // TODO: better log message here
                    getLogger().log(Level.SEVERE,
                            "pipeline data does not match configuration");
                }
                return;
            }

            final List<DevicePolicy.Function> remainingPipelineConfigs =
                    pipeline.subList(pipelineIndex, pipeline.size());

            final List<Map<String,Object>> remainingPipelineData =
                    pipelineData.subList(pipelineIndex, pipelineData.size());

            if (!DevicePolicy.ALL_ATTRIBUTES().equals(attributeName)) {
                virtualDeviceImpl.processExpiredFunction(
                        updatedAttributes,
                        attributeName,
                        remainingPipelineConfigs,
                        remainingPipelineData);
            } else {
                virtualDeviceImpl.processExpiredFunction(
                        remainingPipelineConfigs,
                        remainingPipelineData
                );
            }
        }

        @Override
        public int hashCode() {
            int hc = (int)(slide^(slide>>>32));
            return hc;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            ScheduledPolicyData other = (ScheduledPolicyData)obj;
            return (this.slide == other.slide);
        }
    }

    private static int timed_policy_thread_count = 0;

    // TODO: can we have only one of these threads for all virtual devices?
    private class TimedPolicyThread extends Thread {

        private final List<ScheduledPolicyData> scheduledPolicyData =
                new ArrayList<ScheduledPolicyData>();

        private transient boolean cancelled = false;

        private TimedPolicyThread() {
            super("timed-policy-thread"+timed_policy_thread_count++);
            setDaemon(true);
        }

        private void addTimedPolicyData(ScheduledPolicyData data) {
            synchronized (scheduledPolicyData) {
                int index = scheduledPolicyData.indexOf(data);
                if (index == -1) {
                    scheduledPolicyData.add(data);
                } else {
                    scheduledPolicyData.set(index, data);
                }
                scheduledPolicyData.notify();
            }
        }

        private void removeTimedPolicyData(ScheduledPolicyData data) {
            synchronized (scheduledPolicyData) {
                scheduledPolicyData.remove(data);
                scheduledPolicyData.notify();
            }
        }

        private boolean isCancelled() { return cancelled; }

        // TODO: never used. Do we need cancelled and cancel()?
        private void cancel() { cancelled = true; this.interrupt(); }

        @Override
        public void run() {

            while (!cancelled) {

                long delay = -1;

                synchronized (scheduledPolicyData) {
                    // Wait for a timed function to be added
                    while (!cancelled && scheduledPolicyData.isEmpty()) {
                        try {
                            scheduledPolicyData.wait();
                        } catch (InterruptedException e) {
                            // Restore interrupt status
                            Thread.currentThread().interrupt();
                        }
                    }

                    final long now = System.currentTimeMillis();
                    Collections.sort(scheduledPolicyData, new Comparator<ScheduledPolicyData>() {
                        @Override
                        public int compare(ScheduledPolicyData o1, ScheduledPolicyData o2) {
                            final long x = o1.getDelay(now);
                            final long y = o2.getDelay(now);
                            return (x < y) ? -1 : ((x == y) ? 0 : 1);
                        }
                    });

                    delay = scheduledPolicyData.get(0).getDelay(now);

                    // May have been cancelled while waiting for data to be
                    // added to scheduledPolicyData
                    if (!cancelled && delay > 0) {
                        try {
                            scheduledPolicyData.wait(delay);
                        } catch (InterruptedException e) {
                            // Restore the interrupted status
                            Thread.currentThread().interrupt();
                        } catch (IllegalArgumentException e) {
                            getLogger().log(Level.SEVERE, "wait(" + delay + ")", e);
                        }
                    }

                    final long currentTimeMillis = System.currentTimeMillis();

                    final List<Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>> updatedAttributes
                            = new ArrayList<Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>>();

                    for (ScheduledPolicyData policyData : scheduledPolicyData) {
                        // Run through all the timed function data
                        if (policyData.getDelay(currentTimeMillis) <= 0) {
                            policyData.processExpiredFunction(VirtualDeviceImpl.this, updatedAttributes, currentTimeMillis);
                        }
                    }
                    if (!updatedAttributes.isEmpty()) {
                        //
                        // Call updateFields to ensure the computed metrics get run,
                        // and will put all attributes into one data message.
                        //
                        updateFields(updatedAttributes);
                    }
                }
            }
        }
    }

    // DevicePolicyManager.ChangeListener interface
    @Override
    public void policyAssigned(DevicePolicy policy, Set<String> assignedDevices) {

        if (assignedDevices == null || !assignedDevices.contains(getEndpointId())) {
            return;
        }

        if (getLogger().isLoggable(Level.FINE)) {
            getLogger().log(Level.FINE,
                    getEndpointId() + " : Policy assigned : " + policy.getId());
        }

        final long timeZero = System.currentTimeMillis();
        final Set<Map.Entry<String, List<DevicePolicy.Function>>> entries = policy.getPipelines().entrySet();
        for (Map.Entry<String, List<DevicePolicy.Function>> entry : entries) {
            policyAssigned(entry.getKey(), entry.getValue(), timeZero);
        }
    }

    private void policyAssigned(String attributeName, List<DevicePolicy.Function> newPipeline, long timeZero) {

        if (newPipeline != null && !newPipeline.isEmpty()) {

            for (int index=0, indexMax=newPipeline.size(); index<indexMax; index++) {

                final DevicePolicy.Function function = newPipeline.get(index);

                final String id = function.getId();
                final Map<String, ?> parameters = function.getParameters();

                final long window = DeviceFunction.getWindow(parameters);
                if (window > -1 && !("eliminateDuplicates".equals(id) || "detectDuplicates".equals(id))) {
                    final long slide = DeviceFunction.getSlide(parameters, window);
                    addScheduledPolicy(window, slide, timeZero, attributeName, index);
                }

                // If the first policy in the chain is a computed metric,
                // see if it refers to other attributes.
                if (index == 0 && "computedMetric".equals(id)) {
                    final String formula = (String)parameters.get("formula");
                    final Set<String> triggerAttributes = new HashSet<String>();
                    int pos = formula.indexOf("$(");
                    while (pos != -1) {
                        final int end = formula.indexOf(')', pos + 1);
                        if (pos == 0 || formula.charAt(pos-1) != '$') {
                            final String attr = formula.substring(pos + "$(".length(), end);
                            if (!attr.equals(attributeName)) {
                                triggerAttributes.add(attr);
                            }
                        }
                        pos = formula.indexOf("$(", end + 1);
                    }
                    if (!triggerAttributes.isEmpty()) {
                        synchronized (computedMetricTriggerMap) {
                            computedMetricTriggerMap.add(new Pair<Set<String>,String>(triggerAttributes, attributeName));
                        }
                    }
                }
            }
        }
    }

    @Override
    public void policyUnassigned(DevicePolicy devicePolicy, Set<String> unassignedDevices) {

        if (unassignedDevices == null || !unassignedDevices.contains(getEndpointId())) {
            return;
        }

        if (getLogger().isLoggable(Level.FINE)) {
            getLogger().log(Level.FINE,
                    getEndpointId() + " : Policy un-assigned : " + devicePolicy.getId());
        }


        final List<Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>> updatedAttributes =
                new ArrayList<Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>>();

        final Set<Map.Entry<String, List<DevicePolicy.Function>>> entries = devicePolicy.getPipelines().entrySet();
        for (Map.Entry<String, List<DevicePolicy.Function>> entry : entries) {
            policyUnassigned(updatedAttributes, entry.getKey(), entry.getValue());
        }

        if (!updatedAttributes.isEmpty()) {
            //
            // Call updateFields to ensure the computed metrics get run,
            // and will put all attributes into one data message.
            //
            updateFields(updatedAttributes);
        }
    }

    private void policyUnassigned(
            List<Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>> updatedAttributes,
            String attributeName,
            List<DevicePolicy.Function> oldPipeline) {

        if (oldPipeline != null && !oldPipeline.isEmpty()) {

            //
            // The order in which the oldPipeline is finalized is important.
            // First, remove any scheduled policies so they don't get executed. Any
            // pending data will be committed in the next step.
            // Second, commit any "in process" values. This may cause a computedMetric
            // to be triggered.
            // Third, remove any computed metric triggers.
            // Lastly, remove any data for this pipeline from the policy data cache
            //
            for (int index=0,indexMax=oldPipeline.size(); index<indexMax; index++) {
                final DevicePolicy.Function function = oldPipeline.get(index);
                final String id = function.getId();
                final Map<String, ?> parameters = function.getParameters();

                final long window = DeviceFunction.getWindow(parameters);
                if (window > -1 && !("eliminateDuplicates".equals(id) || "detectDuplicates".equals(id))) {
                    final long slide = DeviceFunction.getSlide(parameters, window);
                    removeScheduledPolicy(slide, attributeName, index, window);
                }

            }

            // commit values from old pipeline
            final List<Map<String,Object>> pipelineData = getPipelineData(attributeName);
            if (pipelineData != null && !pipelineData.isEmpty()) {
                if (!DevicePolicy.ALL_ATTRIBUTES().equals(attributeName)) {
                    processExpiredFunction(updatedAttributes, attributeName, oldPipeline, pipelineData);
                } else {
                    processExpiredFunction(oldPipeline, pipelineData);
                }
            }

            if (attributeName != null) {
                // remove this attribute from the computedMetricTriggerMap
                for (int index = computedMetricTriggerMap.size() - 1; 0 <= index; --index) {
                    final Pair<Set<String>, String> pair = computedMetricTriggerMap.get(index);
                    if (attributeName.equals(pair.getValue())) {
                        computedMetricTriggerMap.remove(index);
                    }
                }
            }

            // remove data from cache
            pipelineDataCache.remove(attributeName);

        }

    }

    //
    // Routine for handling invocation of a policy function when the window's
    // slide expires. This routine gets the value of the function, and then
    // processes the remaining functions in the pipeline (if any).
    //
    private void processExpiredFunction(
            final List<Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>> updatedAttributes,
            final String attributeName,
            final List<DevicePolicy.Function> pipeline,
            final List<Map<String, Object>> pipelineData) {

        if (pipeline == null || pipeline.isEmpty()) {
            return;
        }

        try {
            final VirtualDeviceAttributeBase attribute = getAttribute(attributeName);
            final DeviceModelAttribute deviceModelAttribute = attribute.getDeviceModelAttribute();

            final DevicePolicy.Function function = pipeline.get(0);
            final String functionId = function.getId();
            final Map<String,?> config = function.getParameters();
            final Map<String,Object> data = pipelineData.get(0);
            final DeviceFunction deviceFunction = DeviceFunction.getDeviceFunction(functionId);

            if (deviceFunction == null) {
                getLogger().log(Level.SEVERE, "device function " + functionId + " not found");
                return;
            }

            Object value = deviceFunction.get(
                    VirtualDeviceImpl.this,
                    attributeName,
                    config,
                    data
            );

            if (value != null && pipeline.size() > 1) {
                // process remaining policies in the pipeline
                value = offer0(
                        deviceModelAttribute,
                        value,
                        pipeline.subList(1, pipeline.size()),
                        pipelineData.subList(1, pipelineData.size()));
            }


            if (value != null) {

                Object policyValue = cast(attribute.getDeviceModelAttribute().getType(), value);

                if (policyValue != null) {

                    if (getLogger().isLoggable(Level.FINE)) {
                        getLogger().log(Level.FINE,
                                getEndpointId() +
                                        " : Set   : \"" + attributeName + "\"=" + policyValue);
                    }

                    updatedAttributes.add(
                            new Pair<VirtualDeviceAttributeBase<VirtualDevice, Object>, Object>(
                                    attribute,
                                    policyValue
                            )
                    );
                }
            }

        } catch (IllegalArgumentException e) {
            getLogger().log(Level.WARNING, e.getMessage());

        } catch (ClassCastException e) {
            getLogger().log(Level.WARNING, attributeName, e);
        }
    }

    //
    // Routine for handling invocation of a device model policy when the window's
    // slide expires. This routine gets the value of the function, and then
    // processes the remaining functions in the pipeline (if any).
    //
    @SuppressWarnings({"unchecked"})
    private void processExpiredFunction(
            final List<DevicePolicy.Function> pipeline,
            final List<Map<String,Object>> pipelineData) {

        if (pipeline == null || pipeline.isEmpty()) {
            return;
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

        try {

            final DevicePolicy.Function function = pipeline.get(0);
            final String functionId = function.getId();
            final Map<String,?> config = function.getParameters();
            final Map<String,Object> data = pipelineData.get(0);
            final DeviceFunction deviceFunction = DeviceFunction.getDeviceFunction(functionId);

            if (deviceFunction == null) {
                getLogger().log(Level.SEVERE, "device function " + functionId + " not found");
                return;
            }

            Object value = deviceFunction.get(
                    VirtualDeviceImpl.this,
                    null,
                    config,
                    data
            );

//        TODO: handle more than one function in a pipeline for all-attributes
//        Leave code here for future reference.
//            if (value != null && pipeline.size() > 1) {
//                // process remaining policies in the pipeline
//                value = offer0(
//                        null,
//                        value,
//                        pipeline.subList(1, pipeline.size()),
//                        pipelineData.subList(1, pipelineData.size()));
//            }

            if (value != null) {

                final List<Pair<Message, StorageObjectImpl>> pairs =
                        (List<Pair<Message, StorageObjectImpl>>) value;

                if (pairs.isEmpty()) {
                    return;
                }

                Message[] messages = new Message[pairs.size()];

                MessageDispatcherImpl messageDispatcher =
                        (MessageDispatcherImpl) MessageDispatcher.getMessageDispatcher(directlyConnectedDevice);

                for (int n = 0, nMax = pairs.size(); n < nMax; n++) {
                    final Pair<Message, StorageObjectImpl> messagePair = pairs.get(n);
                    messages[n] = messagePair.getKey();
                    final StorageObjectImpl so = messagePair.getValue();
                    if (so != null) {
                        messageDispatcher.addStorageObjectDependency(so, messages[n].getClientId());
                        so.sync();
                    }
                }

                messageDispatcher.queue(messages);

            }

        } catch (ArrayStoreException e) {
            throw e;
        } catch(Throwable t) {
            getLogger().log(Level.SEVERE, t.toString());
        }
    }

    // The key is the set of attributes that are referred to in the computedMetric formula.
    // The value is the attribute that is computed.
    private List<Pair<Set<String>,String>> computedMetricTriggerMap =
            new ArrayList<Pair<Set<String>,String>>();

    private Set<String> checkComputedMetrics(Set<String> updatedAttributes) {

        if (updatedAttributes == null || updatedAttributes.isEmpty()) {
            return Collections.EMPTY_SET;
        }

        if (computedMetricTriggerMap.isEmpty()) {
            return Collections.EMPTY_SET;
        }

        // This will be the set of attributes that have computed metrics
        // that are triggered by the set of updated attributes.
        final Set<String> computedAttributes = new HashSet<String>();

        synchronized (computedMetricTriggerMap) {

            for (Pair<Set<String>, String> entry : computedMetricTriggerMap) {

                // This is the set of attributes that the formula refers to.
                final Set<String> key = entry.getKey();

                // If the set of attributes that the formula refers to
                // is a subset of the updated attributes, then compute
                // the value of the attribute.
                if (updatedAttributes.containsAll(key)) {
                    computedAttributes.add(entry.getValue());
                }
            }
        }

        if (!computedAttributes.isEmpty()) {

            final Iterator<String> iterator = computedAttributes.iterator();
            while(iterator.hasNext()) {
                final String attributeName = iterator.next();
                VirtualDeviceAttributeBase<VirtualDevice, Object> attribute = getAttribute(attributeName);

                if (!attribute.isSettable()) {
                    getLogger().log(Level.WARNING, "attempt to modify read-only attribute '" + attributeName + "'");
                    computedAttributes.remove(attributeName);
                    continue;
                }

                final DevicePolicy devicePolicy =
                            devicePolicyManager.getPolicy(getDeviceModel().getURN(), getEndpointId());

                if (devicePolicy == null) {
                    continue;
                }

                final List<DevicePolicy.Function> pipeline = devicePolicy.getPipeline(attributeName);
                if (pipeline == null || pipeline.isEmpty()) {
                    continue;
                }

                final List<Map<String,Object>> pipelineData = getPipelineData(attributeName);

                // offer0 returns true if the attribute was set. If the attribute was not set,
                // then remove the attribute name from the list of attributesToCompute
                final Object policyValue =
                        offer0(attribute.getDeviceModelAttribute(),
                                attribute.get(),
                                pipeline,
                                pipelineData);

                if (policyValue != null) {
                    if (getLogger().isLoggable(Level.FINE)) {
                        getLogger().log(Level.FINE,
                                getEndpointId() +
                                        " : Set   : \"" + attributeName + "\"=" + policyValue);
                    }
                    attribute.update(policyValue);
                } else {
                    iterator.remove();
                }
            }
        }

        return computedAttributes;
    }

    @SuppressWarnings({"unchecked"})
    private <T> T cast(final DeviceModelAttribute.Type type, final Object newValue) {

        T castValue = null;

        switch (type) {
            case INTEGER: {
                final Number number = Number.class.cast(newValue);
                final Long roundedValue = Math.round(number.doubleValue());
                castValue = (T) Integer.valueOf(roundedValue.intValue());
                break;
            }
            case NUMBER: {
                final Number number = Number.class.cast(newValue);
                castValue = (T) Double.valueOf(number.doubleValue());
                break;
            }
            case STRING: {
                castValue = (T) ((String.class.cast(newValue)));
                break;
            }
            case BOOLEAN: {
                castValue = (T) ((Boolean.class.cast(newValue)));
                break;
            }
            case DATETIME: {
                if (newValue instanceof Date) {
                    castValue = (T) Long.valueOf((Date.class.cast(newValue)).getTime());
                } else {
                    final Number number = Number.class.cast(newValue);
                    castValue = (T) Long.valueOf(number.longValue());
                }
                break;
            }
            case URI: {
                castValue = (T) ((String.class.cast(newValue)));
                break;
            }
        }

        return castValue;

    }

    //
    // DeviceAnalog implementation
    //
    @Override
    public void setAttributeValue(String attribute, Object value) {
        this.set(attribute, value);
    }

    @Override
    public Object getAttributeValue(String attribute) {
        return this.get(attribute);
    }

    @Override
    public void call(String actionName, Object... args) {

        final Callable callable = actionMap.get(actionName);
        if (callable == null) {
            getLogger().log(Level.WARNING, getDeviceModel().getURN() + " does not contain action '" + actionName + "'");
            return;
        }

        final DeviceModelAction deviceModelAction
                = getDeviceModelAction(base.getDeviceModel(), actionName);
        final DeviceModelAttribute.Type argType = deviceModelAction.getArgType();

        // check arg for correct type
        if (argType != null) {
            // TODO: currently, call only supports one arg
            Object arg = args != null && args.length > 0 ? args[0] : null;

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
                    goodArg = arg instanceof Date;
                    break;
                case BOOLEAN:
                    goodArg = arg instanceof Boolean;
                    break;
                case STRING:
                case URI:
                    goodArg = arg instanceof String;
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

            callable.call(this, arg);
        } else {
            // argType is null. Action takes no args.
            callable.call(this, null);
        }

    }

    @Override
    public void queueMessage(Message message) {
        this.queueMessage(message, null);
    }

    private static final Logger LOGGER = Logger.getLogger("oracle.iot.client");
    private static Logger getLogger() { return LOGGER; }
}
