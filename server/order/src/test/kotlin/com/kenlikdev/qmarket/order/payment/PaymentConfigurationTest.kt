package com.kenlikdev.qmarket.order.payment

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PaymentConfigurationTest {
    private fun configuration(url: String): PaymentConfiguration =
        PaymentConfiguration(
            StripeProperties(
                secretKey = "sk_test_key",
                publishableKey = "pk_test_key",
                apiBaseUrl = url,
                webhookSecret = "whsec_test",
            ),
        )

    @Test
    fun `accepts official Stripe API HTTPS endpoint`() {
        assertDoesNotThrow {
            configuration("https://api.stripe.com").validateStripeConfiguration()
        }
    }

    @Test
    fun `rejects insecure Stripe API endpoint`() {
        assertThrows<IllegalArgumentException> {
            configuration("http://api.stripe.com").validateStripeConfiguration()
        }
    }

    @Test
    fun `rejects untrusted Stripe API host`() {
        assertThrows<IllegalArgumentException> {
            configuration("https://stripe-proxy.example.test").validateStripeConfiguration()
        }
    }
}
