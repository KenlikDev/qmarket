package com.kenlikdev.qmarket.payment

import com.kenlikdev.qmarket.api.PaymentSessionDto

actual val supportsStripeClientConfirmation: Boolean = false

/** Native/payment confirmation is wired by the corresponding platform host, not this adapter. */
actual fun launchStripeClientConfirmation(
    session: PaymentSessionDto,
    onResult: (StripeConfirmationResult) -> Unit,
) {
    onResult(
        StripeConfirmationResult(
            outcome = StripeConfirmationOutcome.UNAVAILABLE,
            message = "Stripe confirmation is not available through this adapter on the current target.",
        ),
    )
}

/** Non-browser targets have no Stripe redirect URL to consume. */
actual fun consumeStripeClientReturn(): StripeConfirmationReturn? = null
