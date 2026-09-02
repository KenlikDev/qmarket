package com.kenlikdev.qmarket.order.payment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class MockPaymentGatewayTest {
    private val gateway = MockPaymentGateway()

    @Test
    fun `provider id is mock`() {
        assertEquals("mock", gateway.providerId)
    }

    @Test
    fun `charge always succeeds with order-scoped reference`() {
        val orderId = UUID.randomUUID()
        val result =
            gateway.charge(
                orderId = orderId,
                userId = UUID.randomUUID(),
                amount = BigDecimal("42.50"),
                currency = "USD",
            )
        assertTrue(result.success)
        assertEquals("mock_$orderId", result.providerReference)
        assertEquals("Mock charge OK", result.message)
    }
}
