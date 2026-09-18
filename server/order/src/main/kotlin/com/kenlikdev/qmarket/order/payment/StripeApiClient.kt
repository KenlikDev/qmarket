package com.kenlikdev.qmarket.order.payment

import java.util.UUID

/** Thin HTTP port over Stripe PaymentIntents (testable without network). */
interface StripeApiClient {
    /**
     * Server-side confirm path (test helper / legacy [StripePaymentGateway.charge]).
     * Uses confirm=true + test PM — not for Payment Element.
     */
    fun createPaymentIntent(
        amountMinor: Long,
        currency: String,
        orderId: UUID,
        userId: UUID,
    ): StripePaymentIntentResult

    /**
     * Unconfirmed PaymentIntent for client-side confirmation (Payment Element / mobile SDK).
     * Returns [StripePaymentIntentResult.clientSecret].
     */
    fun createPaymentIntentForClient(
        amountMinor: Long,
        currency: String,
        orderId: UUID,
        userId: UUID,
    ): StripePaymentIntentResult
}

data class StripePaymentIntentResult(
    val id: String,
    val status: String,
    val clientSecret: String? = null,
    val rawBody: String? = null,
)
