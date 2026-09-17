package com.kenlikdev.qmarket.order.payment

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Business metrics for payment / webhook flows (Micrometer → actuator /prometheus).
 */
@Component
class PaymentMetrics(
    registry: MeterRegistry,
) {
    private val webhookReceived: Counter =
        Counter
            .builder("qmarket.payment.webhook.received")
            .description("Stripe webhook requests accepted after signature check")
            .register(registry)

    private val webhookDuplicate: Counter =
        Counter
            .builder("qmarket.payment.webhook.duplicate")
            .description("Stripe webhook events ignored as already claimed/processed")
            .register(registry)

    private val webhookFailed: Counter =
        Counter
            .builder("qmarket.payment.webhook.failed")
            .description("Stripe webhook processing failures")
            .register(registry)

    private val webhookProcessed: Counter =
        Counter
            .builder("qmarket.payment.webhook.processed")
            .description("Stripe webhook events successfully processed")
            .register(registry)

    private val orderPaidFromProvider: Counter =
        Counter
            .builder("qmarket.payment.order.paid_from_provider")
            .description("Orders marked PAID via provider webhook")
            .register(registry)

    fun webhookReceived() = webhookReceived.increment()

    fun webhookDuplicate() = webhookDuplicate.increment()

    fun webhookFailed() = webhookFailed.increment()

    fun webhookProcessed() = webhookProcessed.increment()

    fun orderPaidFromProvider() = orderPaidFromProvider.increment()
}
