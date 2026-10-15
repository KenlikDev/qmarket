package com.kenlikdev.qmarket.payment

import com.kenlikdev.qmarket.network.StripePaymentResult
import kotlin.test.Test
import kotlin.test.assertEquals

class StripeClientConfirmationTest {
    @Test
    fun successfulAndProcessingOutcomesRequestServerReconciliation() {
        assertEquals(
            StripePaymentResult.COMPLETED,
            StripeConfirmationOutcome.SUCCEEDED.toStripePaymentResult(),
        )
        assertEquals(
            StripePaymentResult.COMPLETED,
            StripeConfirmationOutcome.PROCESSING.toStripePaymentResult(),
        )
    }

    @Test
    fun cancelledAndFailedOutcomesRemainNonPaid() {
        assertEquals(
            StripePaymentResult.CANCELED,
            StripeConfirmationOutcome.CANCELLED.toStripePaymentResult(),
        )
        assertEquals(
            StripePaymentResult.FAILED,
            StripeConfirmationOutcome.FAILED.toStripePaymentResult(),
        )
        assertEquals(
            StripePaymentResult.FAILED,
            StripeConfirmationOutcome.UNAVAILABLE.toStripePaymentResult(),
        )
    }
}
