/*
 * Copyright (c) 2018, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

/**
 * VirtualDeviceAttribute is an attribute in the device model.
 */
class VirtualDeviceAttribute {
    // Instance "variables"/properties...see constructor.
    /**
     *
     * @param {VirtualDevice} virtualDevice
     * @param {DeviceModelAttribute} deviceModelAttribute
     */
    constructor(virtualDevice, deviceModelAttribute) {
        // Instance "variables"/properties.
        this.virtualDevice = virtualDevice;
        this.deviceModelAttribute = deviceModelAttribute;
        this.lastKnownValue = null;
        // Instance "variables"/properties.
    }

    /**
     *
     * @returns {DeviceModelAttribute}
     */
    getDeviceModelAttribute() {
        return this.deviceModelAttribute;
    }

    /**
     *
     * @returns {string}
     */
    getName() {
        return this.name;
    }

    /**
     * @return {boolean}
     */
    isSettable() {
        // An attribute is always settable on the device-client side
        return true;
    }

    /**
     * @param {object} value
     * @return {boolean} 
     */
    update(value) {
        // Validate throws an Error if value is not valid.
        this.validate(this.deviceModelAttribute, value);

        debug('\nVirtualDevice: ' + this.virtualDevice.toString() +
            '\n\t attributeName ' + this.deviceModelAttribute.getName() +
            // '\n\t attributeValue ' + this.deviceModelAttribute.value +
            '\n\t newValue ' + value +
            '\n'
        );

        this.lastKnownValue = this.value = value;
        return true;
    }

    /**
     * TODO: implement.
     * @param {DeviceModelAttribute} deviceModelAttribute
     * @param {object} value
     * @throws Error if the value is not valid for the attribute.
     */ 
    validate(attribute, value) {
        // if (!value) {
        //     return;
        // }

        // final DeviceModelAttribute.Type type = attribute.getType();

        // // block assumes value is not null
        // switch (type) {
        //     case INTEGER:
        //         if (!(value instanceof Integer)) {
        //             throw new IllegalArgumentException("value is not INTEGER");
        //         }
        //         break;
        //     case NUMBER:
        //         if (!(value instanceof Number)) {
        //             throw new IllegalArgumentException("value is not NUMBER");
        //         }
        //         break;
        //     case STRING:
        //         if (!(value instanceof String)) {
        //             throw new IllegalArgumentException("value is not STRING");
        //         }
        //         break;
        //     case BOOLEAN:
        //         if (!(value instanceof Boolean)) {
        //             throw new IllegalArgumentException("value is not BOOLEAN");
        //         }
        //         break;
        //     case DATETIME:
        //         if (!(value instanceof Date) && !(value instanceof Long)) {
        //             throw new IllegalArgumentException("value is not DATETIME");
        //         }
        //         break;
        //     case URI:
        //         if (!(value instanceof oracle.iot.client.ExternalObject)) {
        //             throw new IllegalArgumentException("value is not an ExternalObject");
        //         }
        //         break;
        // }

        // if (((type == DeviceModelAttribute.Type.INTEGER) || (type == DeviceModelAttribute.Type.NUMBER))) {
        //     // Assumption here is that lowerBound <= upperBound
        //     final double val = ((Number) value).doubleValue();
        //     if (attribute.getUpperBound() != null) {
        //         final double upper = attribute.getUpperBound().doubleValue();
        //         if (Double.compare(val, upper) > 0) {
        //             throw new IllegalArgumentException(val + " > " + upper);
        //         }
        //     }
        //     if (attribute.getLowerBound() != null) {
        //         final double lower = attribute.getLowerBound().doubleValue();
        //         if(Double.compare(val, lower) < 0) {
        //             throw new IllegalArgumentException(val + " < " + lower);
        //         }
        //     }
        // }
    }


// /** {@inheritDoc} */
// @Override
// public void set(T value) {
//
//     // validate throws IllegalArgumentException if value is not valid
//     validate(model, value);
//
//     if (getLogger().isLoggable(Level.FINEST)) {
//         getLogger().log(Level.FINEST,
//             "\nVirtualDevice: " + virtualDevice.toString() +
//             "\n\t attributeName " + getDeviceModelAttribute().getName() +
//             "\n\t attributeValue " + this.value +
//             "\n\t newValue " + value  +
//             "\n"
//         );
//     }
//
//     this.lastKnownValue = this.value = value;
//
//     // This may set up an infinite loop!
//     ((VirtualDeviceImpl) virtualDevice).processOnChange(this, this.value);
// }
//
// @Override
// public boolean equals(Object obj) {
//     if (obj == null) return false;
//     if (this == obj) return true;
//     if (obj.getClass() != this.getClass()) return false;
//     VirtualDeviceAttributeImpl other = (VirtualDeviceAttributeImpl)obj;
//
//     if (this.value != null ? !this.value.equals(other.value) : other.value != null) return false;
//     return this.getDeviceModelAttribute().equals(((VirtualDeviceAttributeImpl) obj).getDeviceModelAttribute());
// }
//
// @Override
// public int hashCode() {
//     int hash = 37;
//     hash = 37 * hash + (this.value != null ? this.value.hashCode() : 0);
//     hash = 37 *  this.getDeviceModelAttribute().hashCode();
//     return hash;
// }
}
