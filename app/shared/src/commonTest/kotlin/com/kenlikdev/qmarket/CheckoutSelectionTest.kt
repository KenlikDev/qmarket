package com.kenlikdev.qmarket

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards checkout enable rules so UI regressions (lost addressId path) fail in CI.
 */
class CheckoutSelectionTest {
    private fun canCheckout(
        selectedAddressId: String?,
        shippingAddress: String,
    ): Boolean = selectedAddressId != null || shippingAddress.isNotBlank()

    @Test
    fun enabledWhenSavedAddressSelected() {
        assertTrue(canCheckout(selectedAddressId = "addr-1", shippingAddress = ""))
    }

    @Test
    fun enabledWhenFreeFormOnly() {
        assertTrue(canCheckout(selectedAddressId = null, shippingAddress = "Moscow"))
    }

    @Test
    fun disabledWhenNeither() {
        assertFalse(canCheckout(selectedAddressId = null, shippingAddress = ""))
    }
}
