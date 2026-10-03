package com.kenlikdev.qmarket.order.payment

import java.util.UUID

/** Thin HTTP port over Stripe PaymentIntents (testable without network). */
interface StripeApiClient {
    /**
     * Unconfirmed PaymentIntent for client-side confirmation (Payment Element / mobile SDK).
     * Returns [StripePaymentIntentResult.clientSecret].
     */
    fun createPaymentIntentForClient(
        amountMinor: Long,
        currency: String,
        orderId: UUID,
        userId: UUID,
        idempotencyKey: String,
    ): StripePaymentIntentResult

    /** Retrieve the provider PaymentIntent to reconcile ambiguous cancel/network outcomes. */
    fun retrievePaymentIntent(paymentIntentId: String): StripePaymentIntentResult

    /** Cancel a provider PaymentIntent for an order cancellation flow. */
    fun cancelPaymentIntent(paymentIntentId: String): StripePaymentIntentResult
}

sealed interface PaymentCancellationResult {
    data object Canceled : PaymentCancellationResult

    data class AlreadySucceeded(
        val providerReference: String,
        val amountMinor: Long?,
        val currency: String?,
    ) : PaymentCancellationResult
}

data class StripePaymentIntentResult(
    val id: String,
    val status: String,
    val clientSecret: String? = null,
    val amountMinor: Long? = null,
    val currency: String? = null,
    val rawBody: String? = null,
)

