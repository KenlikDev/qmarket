package com.kenlikdev.qmarket.payment

import com.kenlikdev.qmarket.api.PaymentSessionDto

/**
 * Outcome of a platform-owned Stripe confirmation attempt.
 *
 * A browser/native SDK result is never authoritative for the order state. The API/webhook must
 * reconcile the provider event before the UI reports the order as PAID.
 */
enum class StripeConfirmationOutcome {
    SUCCEEDED,
    PROCESSING,
    CANCELLED,
    FAILED,
    UNAVAILABLE,
}

data class StripeConfirmationResult(
    val outcome: StripeConfirmationOutcome,
    val message: String,
)

/** Whether this runtime has a real Stripe confirmation adapter, not merely session creation. */
expect val supportsStripeClientConfirmation: Boolean

/**
 * Starts a platform-native confirmation UI for an already-created PaymentIntent.
 *
 * The callback must be invoked exactly once when the UI finishes or becomes unavailable.
 */
expect fun launchStripeClientConfirmation(
    session: PaymentSessionDto,
    onResult: (StripeConfirmationResult) -> Unit,
)
