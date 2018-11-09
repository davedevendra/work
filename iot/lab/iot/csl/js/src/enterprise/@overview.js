/**
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 * @overview
 * The device and enterprise client libraries simplify working with
 * the Oracle IoT Cloud Service. These client libraries are a
 * higher-level abstraction over top of messages and REST APIs. Device
 * clients are designed to make it easy to expose device functionality
 * to the IoT Cloud Service, while enterprise clients are designed to
 * make it easy to inspect and control a device. The true state of a
 * device is within the device itself (whether the light is on or
 * off). A "virtual" device object is contained within the cloud and
 * enterprise clients that represent the last-known state of that
 * device, and allow enterprise clients to send commands and set
 * attributes of the device model (e.g., "turn the light off"). 
 *
 * <h2>Trusted Assets</h2>
 *
 * Trusted assets are defined as material that contribute to the chain
 * of trust between the client and the server. The client library
 * relies on an implementation of the
 * TrustedAssetsManager to securely manage
 * these assets on the client. The client-library has a default
 * implementation of the TrustedAssetsManager which uses a framework native
 * trust-store to secure the trust assets. To create the trust-store
 * for the default TrustedAssetsManager, the user must run the
 * TrustedAssetsProvisioner tool by using the script provided in the tools
 * depending if the provision is made for the enterprise or device
 * client library. Usage is available by running the tool without
 * arguments.
 *
 * <h2>Device Models</h2>
 *
 * A device model is a predefined specification of the attributes,
 * formats and resources of a device that can be accessed by the
 * client-library. The attributes of a device model represent the
 * basic variables that the device supports, such as temperature,
 * humidity, flow rate, valve position, and so forth. The formats of a
 * device model define the structure of a message payload. A format
 * describes the message attributes by specifying the attribute names,
 * types, optionality, and default values. The resources of a device
 * model define additional, ad-hoc REST resources which provide richer
 * REST support than what is possible with attributes and formats. 
 * <p>
 * The client library has explicit API for obtaining a device model
 * ({@link iotcs.enterprise.EnterpriseClient#getDeviceModel} or
 * {@link iotcs.device.DirectlyConnectedDevice#getDeviceModel}). This will generate for a
 * specified urn the device model associated with it and registered
 * in the cloud as JSON objects that contain all the attributes, actions
 * ant other specific information for a device model. With the generated
 * model a virtual device can be created that encapsulates all the
 * device functionality based on a specific model. If a device has more
 * than one model associated, for each model a different virtual device
 * can be crated and monitored/controlled.
 *
 * <h2>Device Policies</h2>
 *
 * A policy is a set of rules and constraints that can be associated with a
 * device client (see below) to control its basic data transformation and
 * transfer behavior. Device policies are automatically loaded, if they have
 * been configured for the device, and there is no direct API in the library
 * for manipulating the policies. The policies are applied when a value is
 * {@link iotcs.device.VirtualDevice#offer|offered} to a {@link iotcs.device.VirtualDevice}.
 *
 * <h2>Enterprise Client</h2>
 *
 * Both enterprise and device clients share common API for getting and
 * setting values through a user-interface (in the case of an
 * enterprise-client application) and for getting and setting values
 * on a physical device (in the case of a device-client
 * application).
 * <p>
 * An enterprise-client application will create an {@link iotcs.enterprise.EnterpriseClient}
 * based on an application already created on the server using the static method
 * {@link iotcs.enterprise.EnterpriseClient.newClient}. A list of applications that the user has access to can be
 * retrieved by using the static method {@link iotcs.enterprise.EnterpriseClient.getApplications}.
 * From there, the application can list all the device models
 * that are registered with it by using {@link iotcs.enterprise.EnterpriseClient#getDeviceModels}.
 * After selecting models the list of active devices that have the selected
 * models can be retrieved by using {@link iotcs.enterprise.EnterpriseClient#getActiveDevices}.
 * After selecting combination of models/devices, using the device id's and retrieved
 * models the application can create instances of {@link iotcs.enterprise.VirtualDevice}
 * which provides access to monitor and control the devices.
 *
 * <h2>Device Client</h2>
 *
 * A device-client application will create a {@link iotcs.device.DirectlyConnectedDevice} or
 * a {@link iotcs.device.GatewayDevice} (for indirectly connected devices registration)
 * based on a device already registered on the server that has a logical ID already assigned
 * and saved in a {endpointId}.json generated based on that ID and shared secret
 * associated with the device registered by the TrustedAssetsProvisioner. If the device should
 * be checked if is activated and if is not then the activation should be done.
 * In the course of the activation trusted material used for future authentication with
 * the server will be generated by the TrustedAssetsManager and saved in the
 * {endpointId}.json. In the activation method the model URN's (and capabilities) that the client
 * is implementing (if any) must be given as parameters.
 * <p>
 * After activation (done only if needed) the client should retrieve the device models for the
 * URN's that it is implementing or that other indirectly connected device that is registering
 * in the future are implementing by using the {@link iotcs.device.DirectlyConnectedDevice#getDeviceModel}
 * or {@link iotcs.device.GatewayDevice#getDeviceModel} methods.
 * <p>
 * If the client is a {@link iotcs.device.GatewayDevice}, it can use the
 * {@link iotcs.device.GatewayDevice#registerDevice} method to register other indirectly
 * connected devices that it is using. The server will assign logical endpoint id's to
 * these devices and return them to the client.<br>
 * <b>Be aware that all endpoint id's assigned by the server to indirectly connected
 * devices must be persisted and managed by the device application to use them for creating
 * virtual devices. There is no method for retrieving them from the server side and at
 * an eventual device application restart the id's must be reused.</b><br>
 * <p>
 * After selecting combination of logical endpoint id's (including the client own id) and
 * device models, the client can create instances of {@link iotcs.device.VirtualDevice} by
 * using the constructor or the {@link iotcs.device.DirectlyConnectedDevice#createVirtualDevice}
 * and {@link iotcs.device.GatewayDevice#createVirtualDevice} methods which provides
 * access to messaging to/from the cloud for the specific logical devices.
 *
 * @example <caption>Enterprise Client Quick Start</caption>
 *
 * //The following steps must be taken to run an enterprise-client application.
 *
 * var appName;
 * var ec;
 * var model;
 * var deviceId;
 * var device;
 *
 * // 1. Select an application
 *
 * iotcs.enterprise.EnterpriseClient
 *      .getApplications()
 *      .page('first')
 *      .then(function(response){
 *          response.items.forEach(function (item) {
 *              //select and application and set appName (the application name)
 *          });
 *          initializeEnterpriseClient();
 *      }, function(error){
 *          //handle error in enumeration
 *      });
 *
 * // 2. Initialize enterprise client
 *
 * function initializeEnterpriseClient(){
 *      iotcs.enterprise.EnterpriseClient.newClient(appName, function (client, error) {
 *          if (!client || error) {
 *              //handle client creation error
 *          }
 *          ec = client;
 *          selectDeviceModel();
 *      }
 * }
 *
 * // 3. Select a device model available in the app
 *
 * function selectDeviceModel(){
 *      ec.getDeviceModels()
 *          .page('first')
 *          .then(function(response){
 *              response.items.forEach(function (item) {
 *                  //select a device model and set model
 *              });
 *              selectDevice();
 *          }, function(error){
 *              //handle error in enumeration
 *          });
 * }
 *
 * // 4. Select an active device implementing the device model
 *
 * function selectDevice(){
 *      ec.getActiveDevices(model)
 *          .page('first')
 *          .then(function(response){
 *              response.items.forEach(function (item) {
 *                  //select a device and set the deviceId
 *              });
 *              createVirtualDevice();
 *          }, function(error){
 *              //handle error in enumeration
 *          });
 * }
 *
 * // 5. Create a virtual device for this model
 *
 * function createVirtualDevice(){
 *      device = ec.createVirtualDevice(deviceId, model);
 *      monitorVirtualDevice();
 *      updateVirtualDeviceAttribute();
 *      executeVirtualDeviceAction();
 * }
 *
 * // 6. Monitor the device through the virtual device
 *
 * function monitorVirtualDevice(){
 *      device.onChange = function(onChangeTuple){
 *          //print the new value and attribute
 *          console.log('Attribute '+onChangeTuple.attribute.name+' changed to '+onChangeTuple.newValue);
 *          //process change
 *      };
 *      device.onAlerts = function(alerts){
 *          for (var key in alerts) {
 *              alerts[key].forEach(function (alert) {
 *                  //print alert
 *                  console.log('Received time '+alert.eventTime+' with data '+JSON.stringify(alert.fields));
 *                  //process alert
 *              });
 *          }
 *      };
 * }
 *
 * // 7. Update the value of an attribute
 *
 * function updateVirtualDeviceAttribute(){
 *      device.onError = function(onErrorTuple){
 *          //handle error case on update
 *      };
 *      device.attributeName.value = someValue;
 * }
 *
 * // 8. Execute action on virtual device
 *
 * function executeVirtualDeviceAction(){
 *      device.someAction.onExecute = function(response){
 *          //handle execute action response from server
 *      };
 *      device.call('someAction');
 * }
 *
 * // 9. Dispose of the device
 *
 * device.close();
 *
 * // 10. Dispose of the enterprise client
 *
 * ec.close();
 *
 * @example <caption>Device Client Quick Start</caption>
 *
 * //The following steps must be taken to run a device-client application. This
 * //sample is for a gateway device that does not implement any specific model
 * //that registers one indirectly connected device.
 * //The model must be already in the cloud registered.
 *
 * var trustedAssetsFile = '0-SOMEID.json';
 * var trustedAssetsPassword = 'changeit';
 *
 * var gateway;
 * var model;
 * var indirectDeviceId;
 * var indirectDevice;
 * var indirectDeviceSerialNumber = 'someUniqueID' ;
 * var indirectDeviceMetadata = {};
 *
 * // 1. Create the device client (gateway)
 *
 * gateway = new iotcs.device.GatewayDevice(trustedAssetsFile, trustedAssetsPassword);
 *
 * // 2. Activate the device if needed
 *
 * if (!gateway.isActivated()) {
 *      gateway.activate([], function (device, error) {
 *          if (!device || error) {
 *              //handle activation error
 *          }
 *          selectDeviceModel();
 *      });
 * } else {
 *      selectDeviceModel();
 * }
 *
 * // 3. Select the device model
 *
 * function selectDeviceModel(){
 *      gateway.getDeviceModel('urn:myModel', function (response, error) {
 *          if (!response || error) {
 *              //handle get device model error
 *          }
 *          model = response;
 *          enrollDevice();
 *      });
 * }
 *
 * // 4. Register an indirectly connected device
 *
 * function enrollDevice(){
 *      gateway.registerDevice(indirectDeviceSerialNumber, indirectDeviceMetadata, ['urn:myModel'],
 *          function (response, error) {
 *              if (!response || error) {
 *                  //handle enroll error
 *              }
 *              indirectDeviceId = response;
 *              createVirtualDevice();
 *          });
 * }
 *
 * // 5. Create a virtual device for the indirectly connected device
 *
 * function createVirtualDevice(){
 *      device = gateway.createVirtualDevice(deviceId, model);
 *      monitorVirtualDevice();
 *      updateVirtualDeviceAttribute();
 *      sendVirtualDeviceAlert();
 * }
 *
 * // 6. Monitor the device through the virtual device (it has two actions: power and reset)
 *
 * function monitorVirtualDevice(){
 *      device.onChange = function(onChangeTuple){
 *          //print the new value and attribute
 *          console.log('Attribute '+onChangeTuple.attribute.name+' changed to '+onChangeTuple.newValue);
 *          //process change
 *          throw new Error('some message'); //if some error occurred
 *      };
 *      device.power.onExecute = function(value){
 *          if (value) {
 *              //shutdown the device
 *          } else {
 *              //start the device
 *          }
 *      };
 *      device.reset.onExecute = function(){
 *          //reset the device
 *          throw new Error('some message'); //if some error occurred
 *      };
 * }
 *
 * // 7. Update the value of an attribute
 *
 * function updateVirtualDeviceAttribute(){
 *      device.onError = function(onErrorTuple){
 *          //handle error case on update
 *      };
 *      device.attributeName.value = someValue;
 * }
 *
 * // 8. Raise an alert to be sent to the cloud
 *
 * function sendVirtualDeviceAlert(){
 *      var alert = device.createAlert('urn:myAlert');
 *      alert.fields.mandatoryFieldName = someValue;
 *      alert.fields.optionalFieldName = someValue; //this is optional
 *      alert.raise();
 * }
 *
 * // 9. Dispose of the virtual device
 *
 * device.close();
 *
 * // 10. Dispose of the gateway device client
 *
 * ec.close();
 *
 * @example <caption>Storage Cloud Quick Start</caption>
 *
 * // This shows how to use the Virtualization API to upload content to,
 * // or download content from, the Oracle Storage Cloud Service.
 * // To upload or download content, there must be an attribute, field,
 * // or action in the device model with type URI.
 * // When creating a DataItem for an attribute, field, or action of type URI,
 * // the value is set to the URI of the content in cloud storage.
 *
 * //
 * // Uploading content
 * //
 *
 * // An instance of iotcs.device.StorageObject is first needed to upload a file
 * // from a device client or from an enterprise client.
 * // The StorageObject is created using the createStorageObject API in iotcs.Client,
 * // which is the base class for iotcs.enterprise.EnterpriseClient, iotcs.device.DirectlyConnectedDevice,
 * // and iotcs.device.GatewayDevice. The StorageObject names the object in storage,
 * // and provides the mime-type of the content.
 * // To set the input path, the StorageObject API setInputPath(String path) is used.
 *
 * // This example shows the typical use case from a DirectlyConnectedDevice.
 * // But the code for a GatewayDevice or EnterpriseClient is the same.
 *
 * var storageObjectUpload = gateway.createStorageObject("uploadFileName", "image/jpg");
 * storageObjectUpload.setInputPath("upload.jpg");
 * virtualDevice.attributeName.value = storageObjectUpload;
 * // OR
 * virtualDevice.update({attributeName: storageObjectUpload});
 *
 * // A StorageObject may also be set on an Alert field, or as an Action parameter,
 * // provided the type in the device model is URI
 *
 * //
 * // Downloading content
 * //
 *
 * // In the Virtualization API, the client is notified through an onChange tuple,
 * // onAlert tuple, or a call callback for an action. The value in the tuple is a StorageObject.
 * // To download the content, the output path is set on the StorageObject,
 * // and the content is synchronized by calling the StorageObject sync() API.
 *
 * // This example shows the typical use case from an onChange event.
 * // The code for an onAlert or for an action callback is much the same.
 *
 * virtualDevice.attributeName.onChange = function (tuple) {
 *     var name = tuple.attribute.id;
 *     var storageObject = tuple.newValue;
 *     // only download if image is less than 4M
 *     if (storageObject.getLength() < 4 * 1024 * 1024) {
 *         storageObject.setOutputPath("download.jpg");
 *         storageObject.sync();
 *     }
 * };
 *
 * //
 * // Checking synchronization status
 * //
 *
 * // A StorageObject is a reference to some content in the Storage Cloud.
 * // The content can be in sync with the storage cloud, not in sync with the storage cloud,
 * // or in process of being sync'd with the storage cloud.
 * // The synchronization can be monitored by setting a SyncCallback with onSync.
 * // For the upload case, set the onSync callback on the storage object
 * // before setting the virtual device attribute.
 * // For the download case, set the onSync callback on the storage object
 * // from within the onChange callback.
 *
 * storageObject.onSync = function (event) {
 *     var sourceStorageObject = event.getSource();
 *     if (sourceStorageObject.getSyncStatus() === iotcs.device.StorageObject.SyncStatus.IN_SYNC) {
 *         // image was uploaded
 *     } else if (sourceStorageObject.getSyncStatus() === iotcs.device.StorageObject.SyncStatus.FAILED) {
 *         // image was not uploaded, take action!
 *     }
 * }
 */
