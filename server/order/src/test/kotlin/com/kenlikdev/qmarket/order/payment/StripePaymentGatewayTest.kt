package com.kenlikdev.qmarket.order.payment

import com.kenlikdev.qmarket.common.util.Money
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class StripePaymentGatewayTest {
    private val props = StripeProperties(secretKey = "sk_test_x", defaultCurrency = "rub")
    private val api = mockk<StripeApiClient>()
    private val gateway = StripePaymentGateway(props, api)

    @Test
    fun `provider id is stripe`() {
        assertEquals("stripe", gateway.providerId)
    }

    @Test
    fun `Money toMinorUnits converts major to minor units`() {
        assertEquals(1050L, Money.toMinorUnits(BigDecimal("10.50")))
        assertEquals(1L, Money.toMinorUnits(BigDecimal("0.01")))
    }

    @Test
    fun `charge succeeds when PaymentIntent succeeded`() {
        val orderId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        every {
            api.createPaymentIntent(1050L, "rub", orderId, userId)
        } returns StripePaymentIntentResult(id = "pi_123", status = "succeeded")

        val result = gateway.charge(orderId, userId, BigDecimal("10.50"), "RUB")

        assertTrue(result.success)
        assertEquals("pi_123", result.providerReference)
        verify(exactly = 1) { api.createPaymentIntent(1050L, "rub", orderId, userId) }
    }

    @Test
    fun `charge fails when Stripe returns non-success status`() {
        val orderId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        every {
            api.createPaymentIntent(any(), any(), orderId, userId)
        } returns StripePaymentIntentResult(id = "pi_456", status = "requires_action")

        val result = gateway.charge(orderId, userId, BigDecimal("1.00"), "rub")

        assertFalse(result.success)
        assertEquals("pi_456", result.providerReference)
    }

    @Test
    fun `charge maps StripeApiException to declined result`() {
        val orderId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        every {
            api.createPaymentIntent(any(), any(), orderId, userId)
        } throws StripeApiException("card_declined", 402, null)

        val result = gateway.charge(orderId, userId, BigDecimal("5.00"), "rub")

        assertFalse(result.success)
        assertTrue(result.message!!.contains("card_declined"))
    }

    @Test
    fun `zero amount is rejected without calling API`() {
        val result = gateway.charge(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO, "rub")
        assertFalse(result.success)
        verify(exactly = 0) { api.createPaymentIntent(any(), any(), any(), any()) }
    }
}
