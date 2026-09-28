package com.kenlikdev.qmarket.order.payment

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID

/**
 * Stripe PaymentIntents adapter.
 *
 * Direct server-side charging is intentionally disabled. Stripe payments must use
 * [StripeApiClient.createPaymentIntentForClient] and provider webhooks so no
 * test payment method is ever used by the application.
 */
@Component
@ConditionalOnProperty(name = ["qmarket.payment.provider"], havingValue = "stripe")
class StripePaymentGateway : PaymentGateway {
    override val providerId: String = "stripe"

    override fun charge(
        orderId: UUID,
        userId: UUID,
        amount: BigDecimal,
        currency: String,
    ): PaymentChargeResult =
        PaymentChargeResult(
            success = false,
            message = "Direct Stripe charging is disabled; use payment-session and client confirmation",
        )
}
