package com.kenlikdev.qmarket

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.kenlikdev.qmarket.network.StripePaymentResultEvent
import com.kenlikdev.qmarket.payment.consumeStripeClientReturn
import com.kenlikdev.qmarket.payment.launchStripeClientConfirmation
import com.kenlikdev.qmarket.payment.supportsStripeClientConfirmation
import com.kenlikdev.qmarket.payment.toStripePaymentResult

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Stripe may redirect the browser for 3DS or another payment-method authentication step.
    // Consume and scrub the URL before Compose/API requests run, then reload order state through QMarket.
    val returnedPayment = consumeStripeClientReturn()
    ComposeViewport {
        var stripePaymentResult by remember {
            mutableStateOf(
                returnedPayment?.let {
                    StripePaymentResultEvent(
                        eventId = 1L,
                        orderId = it.orderId,
                        result = it.result.outcome.toStripePaymentResult(),
                    )
                },
            )
        }
        var nextEventId by remember { mutableStateOf(2L) }

        App(
            onConfirmStripePayment =
                if (supportsStripeClientConfirmation) {
                    { session ->
                        launchStripeClientConfirmation(session) { result ->
                            val eventId = nextEventId
                            nextEventId += 1L
                            stripePaymentResult =
                                StripePaymentResultEvent(
                                    eventId = eventId,
                                    orderId = session.orderId,
                                    result = result.outcome.toStripePaymentResult(),
                                )
                        }
                        // The browser adapter owns its asynchronous overlay and reports exactly one
                        // result later. Returning true prevents the shared model from treating the
                        // session as an unavailable native-host request.
                        true
                    }
                } else {
                    null
                },
            stripePaymentResult = stripePaymentResult,
            onStripePaymentResultConsumed = { eventId ->
                if (stripePaymentResult?.eventId == eventId) {
                    stripePaymentResult = null
                }
            },
        )
    }
}
