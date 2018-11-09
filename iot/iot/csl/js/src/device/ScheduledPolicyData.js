/**
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

class ScheduledPolicyData {
    // Instance "variables"/properties...see constructor.

    /**
     *
     * @param {number} window
     * @param {number} slide
     * @param {number} timeZero
     */
    constructor(window, slide, timeZero) {
        // Instance "variables"/properties.
        // Initial expiry is window milliseconds past time zero.
        // Tenth of a millisecond resolution helps group
        // intersecting slide values (10 second and 20 second,
        // for example).
        this.expiry = ((window + timeZero) / 10) * 10;
        this.slide = slide;
        this.window = window;
        // { attributeName : pipelineIndex }
        // @type {Map<string, number>}
        this.pipelineIndices = new Map();
        // Instance "variables"/properties.
    }

    /**
     *
     * @param {string} attributeName
     * @param {number} pipelineIndex
     */
    addAttribute(attributeName, pipelineIndex) {
        this.pipelineIndices.set(attributeName, pipelineIndex);
    }

    /**
     *
     * @param {object} o
     * @return {boolean}
     */
    equals(o) {
        if (this === o) {return true;}
        if (!o) {return false;}
        return (this.window === o.window) && (this.slide === o.slide);
    }


    /**
     * @return {number}
     */
    hashCode() {
        return ((this.window ^ (this.window >>> 32)) + (this.slide ^ (this.slide >>> 32)));
    }

/**
     *
     * @param {number} now
     * @return {number}
     */
    getDelay(now) {
        // @type {number}
        const delay = this.expiry - now;
        return (delay > 0) ? delay : 0;
    }

    /**
     * @param {VirtualDevice} virtualDevice
     * @param {Set<Pair<VirtualDeviceAttribute<VirtualDevice, object>, object>>} updatedAttributes
     */
    handleExpiredFunction1(virtualDevice, updatedAttributes) {
        return new Promise((resolve, reject) => {
            debug('ScheduledPolicyData.handleExpiredFunction1 called.');
            // @type {DevicePolicy}
            virtualDevice.devicePolicyManager.getPolicy(virtualDevice.deviceModel.urn,
                virtualDevice.endpointId).then(devicePolicy =>
            {
                debug('ScheduledPolicyData.handleExpiredFunction1 devicePolicy = ' + devicePolicy);

                if (!devicePolicy) {
                    // TODO: better log message here
                    console.log('Could not find ' + virtualDevice.deviceModel.urn +
                        ' in policy configuration.');

                    return;
                }

                // @type {Map<string, number}
                const pipelineIndicesCopy = new Map(this.pipelineIndices);

                this.handleExpiredFunction2(virtualDevice, updatedAttributes, devicePolicy,
                    pipelineIndicesCopy).then(() => {
                        debug('ScheduledPolicyData.handleExpiredFunction1 updatedAttributes = ' +
                            util.inspect(updatedAttributes));
                        resolve();
                    });
            });
        });
    }


    /**
     *
     * @param {VirtualDevice} virtualDevice
     * @param {Set<Pair<VirtualDeviceAttribute<VirtualDevice, Object>, Object>>} updatedAttributes
     * @param {DevicePolicy} devicePolicy
     * @param {Map<string, number>} pipelineIndicesTmp
     */
    handleExpiredFunction2(virtualDevice, updatedAttributes, devicePolicy, pipelineIndicesTmp) {
        debug('ScheduledPolicyData.handleExpiredFunction2 called, pipelineIndices = ' +
            util.inspect(pipelineIndicesTmp));

        let pipelineIndicesTmpAry = Array.from(pipelineIndicesTmp);

        let requests = pipelineIndicesTmpAry.map(entry => {
            debug('ScheduledPolicyData.handleExpiredFunction2 calling handleExpiredFunction3.');

            return new Promise((resolve, reject) => {
                this.handleExpiredFunction3(virtualDevice, updatedAttributes, devicePolicy,
                    entry[0], entry[1]).then(() =>
                {
                    debug('ScheduledPolicyData.handleExpiredFunction2 updatedAttributes = ' +
                        util.inspect(updatedAttributes));

                    resolve();
                });
            });
        });

        return Promise.all(requests).then(() => {
            debug('ScheduledPolicyData.handleExpiredFunction2 after Promise.all, updatedAttributes = ' +
                util.inspect(updatedAttributes));
        });
    }

    /**
     * @param {VirtualDevice} virtualDevice
     * @param {Set<Pair<VirtualDeviceAttribute<VirtualDevice, Object>, Object>>} updatedAttributes
     * @param {DevicePolicy} devicePolicy
     * @param {string} attributeName
     * @param {number} pipelineIndex
     */
    handleExpiredFunction3(virtualDevice, updatedAttributes, devicePolicy, attributeName,
                           pipelineIndex)
    {
        return new Promise((resolve, reject) => {
            debug('ScheduledPolicyData.handleExpiredFunction3 called, attributeName = ' +
                attributeName);

            // @type {Set<DevicePolicyFunction}
            const pipeline = devicePolicy.getPipeline(attributeName);
            debug('ScheduledPolicyData.handleExpiredFunction3 pipeline = ' +
                util.inspect(pipeline));

            if (!pipeline || pipeline.size === 0) {
                return;
            }

            if (pipeline.size <= pipelineIndex) {
                // TODO: better log message here
                console.log('Pipeline does not match configuration.');
                return;
            }

            debug('ScheduledPolicyData.handleExpiredFunction3 calling virtualDevice.getPipelineData.');

            // @type {Set<Map<string, object>>}
            virtualDevice.getPipelineData(attributeName, function(pipelineData) {
                if (pipelineData.size <= pipelineIndex) {
                    // TODO: better log message here
                    console.log('Pipeline data does not match configuration.');
                    return;
                }

                // @type {Set<DevicePolicyFunction}
                const remainingPipelineConfigs =
                    new Set(Array.from(pipeline).slice(pipelineIndex, pipeline.size));

                // @type {Set<Map<string, object>>}
                const remainingPipelineData =
                    new Set(Array.from(pipelineData).slice(pipelineIndex, pipelineData.size));

                let isAllAttributes = DevicePolicy.ALL_ATTRIBUTES === attributeName;

                if (!isAllAttributes) {
                    virtualDevice.processExpiredFunction2(updatedAttributes, attributeName,
                        remainingPipelineConfigs, remainingPipelineData);

                    debug('ScheduledPolicyData.handleExpiredFunction3 updatedAttributes = ' +
                        util.inspect(updatedAttributes));

                    resolve();
                } else {
                    virtualDevice.processExpiredFunction1(remainingPipelineConfigs,
                            remainingPipelineData);

                    resolve();
                }
            });
        }).catch(error => {
            console.log('Error handling expired function: ' + error);
        });
    }

    /**
     *
     * @returns {boolean}
     */
    isEmpty() {
        return this.pipelineIndices.size === 0;
    }

    /**
     * @param {VirtualDevice} virtualDevice
     * @param {Set<Pair<VirtualDeviceAttribute<VirtualDevice, Object>, Object>>} updatedAttributes
     * @param {number} timeZero
     */
    processExpiredFunction(virtualDevice, updatedAttributes, timeZero) {
        return new Promise((resolve, reject) => {
            debug('ScheduledPolicyData.processExpiredFunction called.');
            this.handleExpiredFunction1(virtualDevice, updatedAttributes).then(() => {
                debug('ScheduledPolicyData.processExpiredFunction updatedAttributes = ' +
                    util.inspect(updatedAttributes));

                // Ensure expiry is reset. 1/10th of a millisecond resolution.
                this.expiry = ((this.slide + timeZero) / 10) * 10;
                resolve();
            }).catch(error => {
                // Ensure expiry is reset. 1/10th of a millisecond resolution.
                this.expiry = ((this.slide + timeZero) / 10) * 10;
            });
        });
    }

    /**
     *
     * @param {string} attributeName
     * @param {number} pipelineIndex
     */
    removeAttribute(attributeName, pipelineIndex) {
        this.pipelineIndices.delete(attributeName);
    }
}
