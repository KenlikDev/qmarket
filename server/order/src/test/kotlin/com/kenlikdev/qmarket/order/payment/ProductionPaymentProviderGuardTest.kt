package com.kenlikdev.qmarket.order.payment

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ProductionPaymentProviderGuardTest {
    @Test
    fun `production requires Stripe provider`() {
        assertThrows<IllegalArgumentException> {
            ProductionPaymentProviderGuard("mock").validate()
        }
        assertDoesNotThrow {
            ProductionPaymentProviderGuard("stripe").validate()
        }
    }
}
