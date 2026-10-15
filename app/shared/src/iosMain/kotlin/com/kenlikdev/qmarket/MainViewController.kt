package com.kenlikdev.qmarket

import androidx.compose.ui.window.ComposeUIViewController
import com.kenlikdev.qmarket.api.PaymentSessionDto

/**
 * iOS Compose host.
 *
 * Swift owns the native Stripe PaymentSheet UI and calls it only after the shared model asks
 * for a server-created payment session. The result returns to Compose through [paymentHost].
 */
fun MainViewController(
    paymentHost: IosStripePaymentHost,
    onConfirmStripePayment: (PaymentSessionDto) -> Boolean,
) = ComposeUIViewController {
    App(
        onConfirmStripePayment = onConfirmStripePayment,
        stripePaymentResult = paymentHost.stripePaymentResult,
        onStripePaymentResultConsumed = { eventId -> paymentHost.consumePaymentResult(eventId) },
    )
}
