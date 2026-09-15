package com.kenlikdev.qmarket.order.payment

import java.util.UUID

/** Thin HTTP port over Stripe PaymentIntents (testable without network). */
interface StripeApiClient {
    fun createPaymentIntent(
        amountMinor: Long,
        currency: String,
        orderId: UUID,
        userId: UUID,
    ): StripePaymentIntentResult
}

data class StripePaymentIntentResult(
    val id: String,
    val status: String,
    val rawBody: String? = null,
)
