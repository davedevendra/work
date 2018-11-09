/**
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

// TODO: can we have only one of these threads for all virtual devices?
class TimedPolicyThread {
    // Instance "variables"/properties...see constructor.

    constructor(virtualDevice) {
        // Instance "variables"/properties.
        this.virtualDevice = virtualDevice;
        this.canceled = false;
        // Timer interval.
        this.interval = 1000;
        // @type {ScheduledPolicyData[]}
        this.scheduledPolicyData = [];
        // Instance "variables"/properties.

        this.interval = 1000;
        this.i = 0;
        let self = this;

        /**
         *
         */
        this.run = function() {
            debug('TimedPolicyThread.run called.');
            self.timer = null;

            // @type {number}
            const now = new Date().getTime();
            // @type {Set<Pair<VirtualDeviceAttribute, object>>}
            const updatedAttributes = new Set();
            debug('TimedPolicyThread.run scheduledPolicyData = ' + util.inspect(self.scheduledPolicyData));

            // self.scheduledPolicyData.forEach(policyData => {
            //     debug('TimedPolicyThread.run scheduledPolicyData delay = '+  policyData.getDelay(now));

            //     // Run through all the timed function data
            //     if (policyData.getDelay(now) <= 0) {
            //         debug('TimedPolicyThread.run scheduledPolicyData calling processExpiredFunction, updatedAttributes = ' + util.inspect(updatedAttributes));
            //         policyData.processExpiredFunction(self.virtualDevice, updatedAttributes,
            //             now);
            //         debug('TimedPolicyThread.run scheduledPolicyData after calling processExpiredFunction, updatedAttributes = ' + util.inspect(updatedAttributes));
            //     }
            // });

            // debug('TimedPolicyThread.run scheduledPolicyData updatedAttributes = ' + util.inspect(updatedAttributes));

            // if (updatedAttributes.size > 0) {
            //     // Call updateFields to ensure the computed metrics get run,
            //     // and will put all attributes into one data message.
            //     self.virtualDevice.updateFields(updatedAttributes);
            // }

            // self.start(now);
            if (self.scheduledPolicyData) {
                const scheduledPolicyDataAry = Array.from(self.scheduledPolicyData);

                let requests = scheduledPolicyDataAry.map(policyData => {
                    debug('TimedPolicyThread.run scheduledPolicyData delay = '+
                        policyData.getDelay(now));

                    // Run through all the timed function data
                    if (policyData.getDelay(now) <= 0) {
                        debug('TimedPolicyThread.run scheduledPolicyData calling processExpiredFunction, updatedAttributes = ' + util.inspect(updatedAttributes));

                        policyData.processExpiredFunction(self.virtualDevice, updatedAttributes,
                            now);

                        debug('TimedPolicyThread.run scheduledPolicyData after calling processExpiredFunction, updatedAttributes = ' + util.inspect(updatedAttributes));
                    }
                });

                self.start(now);

                return Promise.all(requests).then(() => {
                    debug('TimedPolicyThread.run after Promise.all, updatedAttributes = ' +
                        util.inspect(updatedAttributes));

                    if (updatedAttributes.size > 0) {
                        // Call updateFields to ensure the computed metrics get run,
                        // and will put all attributes into one data message.
                        self.virtualDevice.updateFields(updatedAttributes);
                    }
                });
            }
        };
    }

    /**
     *
     * @param {ScheduledPolicyData} data
     */
    addTimedPolicyData(data) {
        debug('TimedPolicyThread.addTimedPolicyData called, data = ' + data.window);
        // @type {number}
        let index = this.scheduledPolicyData.findIndex(function(element) {
            return element.equals(data);
        });

        if (index === -1) {
            this.scheduledPolicyData.push(data);
        } else {
            this.scheduledPolicyData.splice(index, 0, data);
        }

        // @type {number}
        const now = new Date().getTime();

        // Sort the set by delay time.
        this.scheduledPolicyData.sort(function(o1, o2) {
            // @type {number}
            const x = o1.getDelay(now);
            // @type {number}
            const y = o2.getDelay(now);
            return (x < y) ? -1 : ((x === y) ? 0 : 1);
        });

        // Is the one we're adding the first in the list?  If yes, cancel and re-start.
        // @type {number}
        index = this.scheduledPolicyData.findIndex(function(element) {
            return element.equals(data);
        });

        if (index === 0) {
            this.cancel();
            this.start(now);
        }
    }

    // TODO: never used. Do we need cancelled and cancel()?
    /**
     *
     */
    cancel() {
        debug('TimedPolicyThread.cancel called.');
        this.cancelled = true;

        if (this.timer) {
            clearInterval(this.timer.id);
        }
    }

    /**
     * @return {boolean} {@code true} if the timer is alive.
     */
    isAlive() {
        if (this.timer) {
            return true;
        }

        return false;
    }

    /**
     *
     * @return {boolean}
     */
    isCancelled() {
        return this.cancelled;
    }


    /**
     *
     * @param {ScheduledPolicyData} data
     */
    removeTimedPolicyData(data) {
        debug('TimedPolicyThread.removeTimedPolicyData called, data = ' + data.window);

        // TODO: Optimize this.
        for (let i = 0; i < this.scheduledPolicyData.length; i++) {
            debug('TimedPolicyThread.removeTimedPolicyData checking item #' + i + ' for removal.');
            if (data.toString() === this.scheduledPolicyData[i].toString()) {
                debug('TimedPolicyThread.removeTimedPolicyData removing item #' + i);
                this.scheduledPolicyData.splice(i, 1);
            }
        }

        this.cancel();
        this.start(new Date().getTime());
    }

    /**
     *
     * @param {number} now
     */
    start(now) {
        debug('TimedPolicyThread.start called.');
        // Sort the timers by time.
        if (this.scheduledPolicyData.length > 0) {
            const interval = this.scheduledPolicyData[0].getDelay(now);
            this.timer = setTimeout(this.run, interval);
        }
    }
}

// @type {number}
TimedPolicyThread.timed_policy_thread_count = 0;
