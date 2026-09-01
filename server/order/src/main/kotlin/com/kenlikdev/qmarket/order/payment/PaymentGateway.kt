package com.kenlikdev.qmarket.order.payment

import java.math.BigDecimal
import java.util.UUID

/**
 * Port for card/PSP charges. v1.0 ships with [MockPaymentGateway];
 * a real adapter (Stripe, YooKassa, …) implements the same contract.
 */
interface PaymentGateway {
    /** Stable id for logs and future webhook correlation (e.g. "mock", "stripe"). */
    val providerId: String

    /**
     * Authorize/capture payment for an order.
     * Implementations must be idempotent for the same [orderId] when possible.
     */
    fun charge(
        orderId: UUID,
        userId: UUID,
        amount: BigDecimal,
        currency: String = "RUB",
    ): PaymentChargeResult
}

data class PaymentChargeResult(
    val success: Boolean,
    val providerReference: String? = null,
    val message: String? = null,
)
