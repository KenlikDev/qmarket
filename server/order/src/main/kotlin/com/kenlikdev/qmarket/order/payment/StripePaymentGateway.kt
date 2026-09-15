package com.kenlikdev.qmarket.order.payment

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Stripe PaymentIntents adapter. Enable with:
 * `QMARKET_PAYMENT_PROVIDER=stripe` and `STRIPE_SECRET_KEY=sk_…`
 *
 * Default remains [MockPaymentGateway]. Production should move to
 * client-side confirmation + webhooks instead of server-side `pm_card_visa`.
 */
@Component
@ConditionalOnProperty(name = ["qmarket.payment.provider"], havingValue = "stripe")
class StripePaymentGateway(
    private val props: StripeProperties,
    private val stripeApi: StripeApiClient,
) : PaymentGateway {
    override val providerId: String = "stripe"

    override fun charge(
        orderId: UUID,
        userId: UUID,
        amount: BigDecimal,
        currency: String,
    ): PaymentChargeResult {
        if (amount <= BigDecimal.ZERO) {
            return PaymentChargeResult(success = false, message = "Amount must be positive")
        }
        val cur = currency.ifBlank { props.defaultCurrency }.lowercase()
        val amountMinor = toMinorUnits(amount)
        return try {
            val intent = stripeApi.createPaymentIntent(amountMinor, cur, orderId, userId)
            val ok = intent.status in SUCCEEDED_STATUSES
            PaymentChargeResult(
                success = ok,
                providerReference = intent.id,
                message = if (ok) "Stripe ${intent.status}" else "Stripe status=${intent.status}",
            )
        } catch (ex: StripeApiException) {
            PaymentChargeResult(
                success = false,
                providerReference = null,
                message = "Stripe error: ${ex.message}",
            )
        } catch (ex: Exception) {
            PaymentChargeResult(
                success = false,
                message = "Stripe call failed: ${ex.message}",
            )
        }
    }

    companion object {
        private val SUCCEEDED_STATUSES = setOf("succeeded", "requires_capture")

        fun toMinorUnits(amount: BigDecimal): Long =
            amount
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact()
    }
}
