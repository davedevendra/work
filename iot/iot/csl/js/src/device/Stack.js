/**
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL). See the LICENSE file in the root
 * directory for license terms. You may choose either license, or both.
 *
 */

class Stack {
    // Instance "variables"/properties...see constructor.

    constructor() {
        // Instance "variables"/properties.
        this.count = 0;
        this.items = [];
        // Instance "variables"/properties.
    }

    get(idx) {
        return this.items[idx];
    }

    getLength() {
        return this.count;
    }

    peek() {
        return this.items.slice(-1) [0];
    }

    push(item) {
        this.items.push(item);
        this.count++;
    }

    pop() {
        if (this.count > 0) {
            this.count--;
        }

        return this.items.pop();
    }
}