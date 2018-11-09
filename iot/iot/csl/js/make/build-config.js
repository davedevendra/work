/**
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

this.config = {
    bundles : [
        // enterprise client library
        {
            output: '../build/modules/enterprise-library.node.js',
            wrapper: '../src/shared/wrapper-node-bundle.js',
            fields: {
                LIBNAME:'iotcs',
                DESCRIPTION:'Oracle IoT Cloud Enterprise Client Library',
                VERSION:'1.1',
                FILES: [
                    '../src/enterprise/@overview.js',
                    '../src/enterprise/@globals.js',
                    '../src/shared/$port-node.js',
                    '../src/shared/$impl.js',
                    '../src/shared/Client.js',
                    '../src/shared/Monitor.js',
                    '../src/shared/AbstractVirtualDevice.js',
                    '../src/shared/UnifiedTrustStore.js',
                    '../src/enterprise/$impl-ecl.js',
                    '../src/enterprise/Action.js',
                    '../src/enterprise/Alert.js',
                    '../src/enterprise/Data.js',
                    '../src/enterprise/Attribute.js',
                    '../src/enterprise/AsyncRequestMonitor.js',
                    '../src/enterprise/Controller.js',
                    '../src/enterprise/Pageable.js',
                    '../src/enterprise/MessageEnumerator.js',
                    '../src/enterprise/ResourceEnumerator.js',
                    '../src/enterprise/DeviceAppEnumerator.js',
                    '../src/enterprise/EnterpriseClientImpl.js',
                    '../src/enterprise/EnterpriseClient.js',
                    '../src/enterprise/Filter.js',
                    '../src/enterprise/VirtualDevice.js',
                    '../src/enterprise/TrustedAssetsManager.js'
                ]
            }
        },

        {
            output: '../build/modules/enterprise-library.web.js',
            wrapper: '../src/shared/wrapper-browser-bundle.js',
            fields: {
                LIBNAME:'iotcs',
                DESCRIPTION:'Oracle IoT Cloud Enterprise Client Library',
                VERSION:'1.1',
                FILES: [
                    '../src/enterprise/@overview.js',
                    '../src/enterprise/@globals.js',
                    '../src/shared/$port-browser.js',
                    '../src/shared/$impl.js',
                    '../src/shared/Client.js',
                    '../src/shared/Monitor.js',
                    '../src/shared/AbstractVirtualDevice.js',
                    '../src/shared/UnifiedTrustStore.js',
                    '../src/enterprise/$impl-ecl.js',
                    '../src/enterprise/Action.js',
                    '../src/enterprise/Alert.js',
                    '../src/enterprise/Data.js',
                    '../src/enterprise/Attribute.js',
                    '../src/enterprise/AsyncRequestMonitor.js',
                    '../src/enterprise/Controller.js',
                    '../src/enterprise/Pageable.js',
                    '../src/enterprise/MessageEnumerator.js',
                    '../src/enterprise/ResourceEnumerator.js',
                    '../src/enterprise/DeviceAppEnumerator.js',
                    '../src/enterprise/EnterpriseClientImpl.js',
                    '../src/enterprise/EnterpriseClient.js',
                    '../src/enterprise/Filter.js',
                    '../src/enterprise/VirtualDevice.js',
                    '../src/enterprise/TrustedAssetsManager.js'
               ]
            }
        },

        //enterprise bundle for Virtualization API docs
        {
            output: '../build/temp/iotcs.bundle.hl.web.js',
            wrapper: '../src/shared/wrapper-browser-bundle.js',
            fields: {
                LIBNAME:'iotcs',
                DESCRIPTION:'Oracle IoT Cloud Enterprise Client Library High Level API',
                VERSION:'1.1',
                FILES: [
                    '../src/enterprise/@overview.js',
                    '../src/enterprise/@globals.js',
                    '../src/device/@globals.js',
                    '../src/shared/Client.js',
                    '../src/shared/AbstractVirtualDevice.js',
                    '../src/enterprise/TrustedAssetsManager.js',
                    '../src/enterprise/AsyncRequestMonitor.js',
                    '../src/enterprise/Pageable.js',
                    '../src/enterprise/EnterpriseClient.js',
                    '../src/enterprise/Filter.js',
                    '../src/enterprise/VirtualDevice.js',
                    '../src/device/TrustedAssetsManager.js',
                    '../src/device/DirectlyConnectedDevice.js',
                    '../src/device/GatewayDevice.js',
                    '../src/device/Alert.js',
                    '../src/device/Data.js',
                    '../src/device/VirtualDevice.js'
                ]
            }
        },

        //enterprise bundle for extras API docs
        {
            output: '../build/temp/iotcs.bundle.ll.web.js',
            wrapper: '../src/shared/wrapper-browser-bundle.js',
            fields: {
                LIBNAME:'iotcs',
                DESCRIPTION:'Oracle IoT Cloud Enterprise Client Library Low Level API',
                VERSION:'1.1',
                FILES: [
                    '../src/device/@overview.js',
                    '../src/enterprise/@globals.js',
                    '../src/device/@globals.js',
                    '../src/shared/UnifiedTrustStore.js',
                    '../src/enterprise/TrustedAssetsManager.js',
                    '../src/enterprise/Pageable.js',
                    '../src/enterprise/MessageEnumerator.js',
                    '../src/enterprise/ResourceEnumerator.js',
                    '../src/enterprise/DeviceAppEnumerator.js',
                    '../src/device/DirectlyConnectedDeviceUtil.js',
                    '../src/device/GatewayDeviceUtil.js',
                    '../src/device/Message.js',
                    '../src/device/MessageDispatcher.js',
                    '../src/device/RequestDispatcher.js'
                ]
            }
        },

        // device client library
        {
            output: '../build/modules/device-library.node.js',
            wrapper: '../src/shared/wrapper-node-bundle.js',
            fields: {
                LIBNAME:'iotcs',
                DESCRIPTION:'Oracle IoT Cloud Device Client Library',
                VERSION:'1.1',
                FILES: [
                    '../src/device/@overview.js',
                    '../src/device/@globals.js',
                    '../src/shared/$port-node.js',
                    '../src/shared/$port-mqtt.js',
                    '../src/shared/$impl.js',
                    '../src/shared/MqttController.js',
                    '../src/device/$impl-dcl.js',
                    '../src/shared/Client.js',
                    '../src/shared/Monitor.js',
                    '../src/shared/AbstractVirtualDevice.js',
                    '../src/shared/UnifiedTrustStore.js',
                    '../src/device/TrustedAssetsManager.js',
                    '../src/device/Message.js',
                    '../src/device/DirectlyConnectedDeviceImpl.js',
                    '../src/device/DirectlyConnectedDeviceUtil.js',
                    '../src/device/GatewayDeviceUtil.js',
                    '../src/device/DeviceModelFactory.js',
                    '../src/device/TestConnectivity.js',
                    '../src/device/MessageDispatcher.js',
                    '../src/device/RequestDispatcher.js',
                    '../src/device/Attribute.js',
                    '../src/device/Action.js',
                    '../src/device/Alert.js',
                    '../src/device/Data.js',
                    '../src/device/DirectlyConnectedDevice.js',
                    '../src/device/GatewayDevice.js',
                    '../src/device/VirtualDevice.js'
                ]
            }
        }
    ]
};
