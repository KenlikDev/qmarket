package com.kenlikdev.qmarket.payment

import com.kenlikdev.qmarket.api.PaymentSessionDto

actual val supportsStripeClientConfirmation: Boolean = false

actual fun launchStripeClientConfirmation(
    session: PaymentSessionDto,
    onResult: (StripeConfirmationResult) -> Unit,
) {
    onResult(
        StripeConfirmationResult(
            outcome = StripeConfirmationOutcome.UNAVAILABLE,
            message = "Stripe confirmation is not implemented for this app target. Open QMarket in a supported web browser.",
        ),
    )
}
