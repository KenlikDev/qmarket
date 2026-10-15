package com.kenlikdev.qmarket.payment

import com.kenlikdev.qmarket.api.PaymentSessionDto
import com.kenlikdev.qmarket.network.StripePaymentResult

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

/** Data returned after Stripe redirects the browser back to the application. */
data class StripeConfirmationReturn(
    val orderId: String,
    val result: StripeConfirmationResult,
)

/**
 * Convert a provider SDK result to a client result understood by the shared order model.
 * Processing is intentionally treated like a completed UI flow: the model reloads the order
 * and waits for the server's verified webhook reconciliation.
 */
fun StripeConfirmationOutcome.toStripePaymentResult(): StripePaymentResult =
    when (this) {
        StripeConfirmationOutcome.SUCCEEDED,
        StripeConfirmationOutcome.PROCESSING -> StripePaymentResult.COMPLETED
        StripeConfirmationOutcome.CANCELLED -> StripePaymentResult.CANCELED
        StripeConfirmationOutcome.FAILED,
        StripeConfirmationOutcome.UNAVAILABLE -> StripePaymentResult.FAILED
    }

/** Whether this runtime has a real Stripe confirmation adapter, not merely session creation. */
expect val supportsBrowserStripeClientConfirmation: Boolean

/**
 * Starts the platform confirmation UI for an already-created PaymentIntent.
 * The callback must be invoked exactly once when the UI finishes or becomes unavailable.
 */
expect fun launchBrowserStripeClientConfirmation(
    session: PaymentSessionDto,
    onResult: (StripeConfirmationResult) -> Unit,
)

/**
 * Consume and clean Stripe's redirect result, if the browser returned from an external
 * authentication step. Implementations must not return or persist the PaymentIntent client secret.
 */
expect fun consumeBrowserStripeClientReturn(): StripeConfirmationReturn?
