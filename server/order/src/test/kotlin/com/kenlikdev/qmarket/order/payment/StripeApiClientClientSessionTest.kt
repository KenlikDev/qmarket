package com.kenlikdev.qmarket.order.payment

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Contract for Payment Element path: unconfirmed PI must expose client_secret.
 */
class StripeApiClientClientSessionTest {
    @Test
    fun `createPaymentIntentForClient returns client secret`() {
        val api = mockk<StripeApiClient>()
        val orderId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        every {
            api.createPaymentIntentForClient(1050L, "rub", orderId, userId)
        } returns
            StripePaymentIntentResult(
                id = "pi_client",
                status = "requires_payment_method",
                clientSecret = "pi_client_secret_abc",
            )

        val result = api.createPaymentIntentForClient(1050L, "rub", orderId, userId)

        assertEquals("pi_client", result.id)
        assertEquals("pi_client_secret_abc", result.clientSecret)
        assertNotNull(result.clientSecret)
        verify(exactly = 1) { api.createPaymentIntentForClient(1050L, "rub", orderId, userId) }
    }
}
