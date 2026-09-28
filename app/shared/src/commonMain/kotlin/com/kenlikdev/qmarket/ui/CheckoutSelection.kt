package com.kenlikdev.qmarket.ui

/**
 * Checkout is enabled when a saved address is selected or free-form shipping is filled.
 */
object CheckoutSelection {
    fun canCheckout(
        selectedAddressId: String?,
        shippingAddress: String,
    ): Boolean = selectedAddressId?.isNotBlank() == true || shippingAddress.isNotBlank()
}
