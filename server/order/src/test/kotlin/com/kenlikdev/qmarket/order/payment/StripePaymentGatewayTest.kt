package com.kenlikdev.qmarket.order.payment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class StripePaymentGatewayTest {
    private val gateway = StripePaymentGateway()

    @Test
    fun `provider id is stripe`() {
        assertEquals("stripe", gateway.providerId)
    }

    @Test
    fun `direct charge is disabled`() {
        val result =
            gateway.charge(
                orderId = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                amount = BigDecimal("10.50"),
                currency = "RUB",
            )

        assertFalse(result.success)
        assertEquals(
            "Direct Stripe charging is disabled; use payment-session and client confirmation",
            result.message,
        )
    }
}
