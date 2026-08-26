package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.ui.CheckoutSelection
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards checkout enable rules so UI regressions (lost addressId path) fail in CI.
 */
class CheckoutSelectionTest {
    @Test
    fun enabledWhenSavedAddressSelected() {
        assertTrue(
            CheckoutSelection.canCheckout(
                selectedAddressId = "addr-1",
                shippingAddress = "",
            ),
        )
    }

    @Test
    fun enabledWhenFreeFormOnly() {
        assertTrue(
            CheckoutSelection.canCheckout(
                selectedAddressId = null,
                shippingAddress = "Moscow",
            ),
        )
    }

    @Test
    fun disabledWhenNeither() {
        assertFalse(
            CheckoutSelection.canCheckout(
                selectedAddressId = null,
                shippingAddress = "",
            ),
        )
    }
}
