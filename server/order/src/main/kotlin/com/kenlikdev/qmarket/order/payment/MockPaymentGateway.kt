package com.kenlikdev.qmarket.order.payment

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID

/**
 * Always-succeeding gateway for local/dev and until a real PSP is configured.
 * Activate with `qmarket.payment.provider=mock` (default).
 */
@Component
@ConditionalOnProperty(name = ["qmarket.payment.provider"], havingValue = "mock", matchIfMissing = true)
class MockPaymentGateway : PaymentGateway {
    override val providerId: String = "mock"

    override fun charge(
        orderId: UUID,
        userId: UUID,
        amount: BigDecimal,
        currency: String,
    ): PaymentChargeResult =
        PaymentChargeResult(
            success = true,
            providerReference = "mock_$orderId",
            message = "Mock charge OK",
        )
}
