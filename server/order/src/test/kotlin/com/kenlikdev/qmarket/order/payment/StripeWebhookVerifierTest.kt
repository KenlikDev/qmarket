package com.kenlikdev.qmarket.order.payment

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StripeWebhookVerifierTest {
    private val secret = "whsec_test_secret"
    private val payload = """{"id":"evt_1","type":"payment_intent.succeeded"}"""
    private val ts = 1_700_000_000L

    @Test
    fun `valid signature is accepted`() {
        val v1 = StripeWebhookVerifier.hmacSha256Hex(secret, "$ts.$payload")
        val header = "t=$ts,v1=$v1"
        assertTrue(
            StripeWebhookVerifier.verify(
                payload = payload,
                signatureHeader = header,
                secret = secret,
                toleranceSeconds = 300,
                nowEpochSeconds = ts + 10,
            ),
        )
    }

    @Test
    fun `tampered payload is rejected`() {
        val v1 = StripeWebhookVerifier.hmacSha256Hex(secret, "$ts.$payload")
        val header = "t=$ts,v1=$v1"
        assertFalse(
            StripeWebhookVerifier.verify(
                payload = payload + " ",
                signatureHeader = header,
                secret = secret,
                toleranceSeconds = 300,
                nowEpochSeconds = ts,
            ),
        )
    }

    @Test
    fun `stale timestamp is rejected`() {
        val v1 = StripeWebhookVerifier.hmacSha256Hex(secret, "$ts.$payload")
        val header = "t=$ts,v1=$v1"
        assertFalse(
            StripeWebhookVerifier.verify(
                payload = payload,
                signatureHeader = header,
                secret = secret,
                toleranceSeconds = 60,
                nowEpochSeconds = ts + 120,
            ),
        )
    }
}
