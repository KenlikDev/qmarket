package com.kenlikdev.qmarket

import androidx.compose.ui.window.ComposeUIViewController
import com.kenlikdev.qmarket.api.PaymentSessionDto

/**
 * iOS Compose host.
 *
 * Swift owns the native Stripe PaymentSheet UI. Pass only primitive values across the Swift
 * interop boundary because the DTO's declaring module is not exported by the Shared framework.
 * The client secret stays in memory and is never stored by the host.
 */
fun MainViewController(
    paymentHost: IosStripePaymentHost,
    onConfirmStripePayment: (
        orderId: String,
        providerId: String,
        clientSecret: String,
        publishableKey: String,
    ) -> Boolean,
) = ComposeUIViewController {
    App(
        onConfirmStripePayment = { session: PaymentSessionDto ->
            onConfirmStripePayment(
                session.orderId,
                session.providerId,
                session.clientSecret,
                session.publishableKey,
            )
        },
        stripePaymentResult = paymentHost.stripePaymentResult,
        onStripePaymentResultConsumed = { eventId -> paymentHost.consumePaymentResult(eventId) },
    )
}
