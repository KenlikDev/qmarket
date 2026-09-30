package com.kenlikdev.qmarket.order.service

import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.NotFoundException
import com.kenlikdev.qmarket.common.exception.PaymentProviderException
import com.kenlikdev.qmarket.common.util.Money
import com.kenlikdev.qmarket.order.domain.OrderStatus
import com.kenlikdev.qmarket.order.domain.PaymentOperationState
import com.kenlikdev.qmarket.order.dto.OrderResponse
import com.kenlikdev.qmarket.order.dto.PaymentSessionResponse
import com.kenlikdev.qmarket.order.payment.PaymentGateway
import com.kenlikdev.qmarket.order.payment.StripeApiClient
import com.kenlikdev.qmarket.order.payment.StripeApiException
import com.kenlikdev.qmarket.order.payment.StripeProperties
import com.kenlikdev.qmarket.order.repository.OrderRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.Locale
import java.util.UUID

/**
 * Payment use-cases for orders: charge, client payment-session, provider webhook mark-paid.
 * Kept separate from [OrderService] (checkout / lifecycle) to limit class size and coupling.
 */
@Service
class OrderPaymentService(
    private val orderRepository: OrderRepository,
    private val notificationService: NotificationService,
    private val paymentGateway: PaymentGateway,
    private val stripeApiClient: ObjectProvider<StripeApiClient>,
    private val stripeProperties: ObjectProvider<StripeProperties>,
    transactionManager: PlatformTransactionManager,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    /**
     * Charge via [PaymentGateway] then mark order PAID.
     * Default gateway is mock; swap with a real PSP adapter without changing this flow.
     */
    @Transactional
    fun pay(
        userId: UUID,
        orderId: UUID,
    ): OrderResponse {
        val order =
            orderRepository
                .findByIdAndUserId(orderId, userId) ?: throw NotFoundException("Order not found")

        // Fail closed before PSP if status cannot become PAID
        when (order.status) {
            OrderStatus.PENDING, OrderStatus.CONFIRMED -> Unit
            OrderStatus.PAID -> throw BadRequestException("Order is already paid")
            OrderStatus.CANCELLED -> throw BadRequestException("Cannot pay a cancelled order")
            OrderStatus.SHIPPED, OrderStatus.DELIVERED ->
                throw BadRequestException("Order is already fulfilled")
        }

        val charge =
            paymentGateway.charge(
                orderId = orderId,
                userId = userId,
                amount = order.totalAmount,
            )
        if (!charge.success) {
            throw BadRequestException(charge.message ?: "Payment declined by ${paymentGateway.providerId}")
        }

        order.markPaid()

        val saved = orderRepository.save(order)
        notificationService.notifyOrderEvent(
            userId = userId,
            type = "ORDER_PAID",
            title = "Payment received",
            body = "Order ${saved.id} is paid. Total ${saved.totalAmount}.",
            orderId = saved.id,
        )
        return OrderMapper.toResponse(saved)
    }

    /**
     * Create a Stripe PaymentIntent without holding a database transaction during network I/O.
     *
     * A short transaction records a durable operation key/state. Stripe is then called outside
     * the transaction. A second short transaction persists the provider reference or observes
     * a concurrent cancellation/payment result.
     */
    fun createPaymentSession(
        userId: UUID,
        orderId: UUID,
    ): PaymentSessionResponse {
        val stripeApi =
            stripeApiClient.ifAvailable
                ?: throw BadRequestException(
                    "Payment session requires qmarket.payment.provider=stripe",
                )
        val stripeProps = stripeProperties.ifAvailable ?: StripeProperties()

        val plan =
            requireNotNull(
                transactionTemplate.execute {
                    val order =
                        orderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                            ?: throw NotFoundException("Order not found")
                    validatePayableStatus(order.status)
                    if (order.totalAmount <= BigDecimal.ZERO) {
                        throw BadRequestException("Amount must be positive")
                    }
                    if (order.paymentProvider != null && order.paymentProvider != "stripe") {
                        throw BadRequestException("Order already has a different payment provider")
                    }
                    if (order.paymentOperationState == PaymentOperationState.CANCELLING) {
                        throw BadRequestException("Order payment is being cancelled")
                    }

                    val operationKey =
                        order.paymentOperationKey
                            ?: "qmarket-payment-intent-v2-$orderId"
                    val currency =
                        (order.paymentCurrency ?: stripeProps.defaultCurrency)
                            .trim()
                            .lowercase(Locale.ROOT)
                    require(currency.matches(Regex("^[a-z]{3}$"))) {
                        "Order payment currency is invalid"
                    }
                    order.paymentProvider = "stripe"
                    order.paymentCurrency = currency
                    order.paymentOperationKey = operationKey
                    order.paymentOperationState = PaymentOperationState.CREATING
                    orderRepository.save(order)

                    PaymentSessionPlan(
                        orderId = orderId,
                        userId = userId,
                        amountMinor = Money.toMinorUnits(order.totalAmount),
                        currency = currency,
                        idempotencyKey = operationKey,
                    )
                },
            ) { "Payment-session preparation transaction returned no result" }

        val intent =
            try {
                stripeApi.createPaymentIntentForClient(
                    amountMinor = plan.amountMinor,
                    currency = plan.currency,
                    orderId = plan.orderId,
                    userId = plan.userId,
                    idempotencyKey = plan.idempotencyKey,
                )
            } catch (ex: StripeApiException) {
                throw PaymentProviderException(cause = ex)
            }

        val clientSecret =
            intent.clientSecret
                ?: throw PaymentProviderException(
                    cause = IllegalStateException("Stripe did not return client_secret"),
                )

        val finalization =
            requireNotNull(
                transactionTemplate.execute {
                    val order =
                        orderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                            ?: throw NotFoundException("Order not found")

                    when (order.status) {
                        OrderStatus.PENDING, OrderStatus.CONFIRMED -> {
                            if (order.paymentOperationState == PaymentOperationState.CANCELLING) {
                                PaymentSessionFinalization.CANCEL_PROVIDER
                            } else {
                                if (order.paymentProvider != null && order.paymentProvider != "stripe") {
                                    throw BadRequestException("Order already has a different payment provider")
                                }
                                if (
                                    order.paymentProviderReference != null &&
                                    order.paymentProviderReference != intent.id
                                ) {
                                    throw BadRequestException(
                                        "Stripe PaymentIntent does not match the order payment session",
                                    )
                                }
                                order.paymentProvider = "stripe"
                                order.paymentProviderReference = intent.id
                                order.paymentOperationState = PaymentOperationState.NONE
                                orderRepository.save(order)
                                PaymentSessionFinalization.PERSISTED
                            }
                        }
                        OrderStatus.PAID -> PaymentSessionFinalization.PAID
                        OrderStatus.CANCELLED -> PaymentSessionFinalization.CANCEL_PROVIDER
                        OrderStatus.SHIPPED, OrderStatus.DELIVERED ->
                            throw BadRequestException("Order is already fulfilled")
                    }
                },
            ) { "Payment-session finalization transaction returned no result" }

        return when (finalization) {
            PaymentSessionFinalization.PERSISTED ->
                PaymentSessionResponse(
                    orderId = orderId,
                    providerId = "stripe",
                    paymentIntentId = intent.id,
                    clientSecret = clientSecret,
                    publishableKey = stripeProps.publishableKey,
                )
            PaymentSessionFinalization.PAID ->
                throw BadRequestException("Order is already paid")
            PaymentSessionFinalization.CANCEL_PROVIDER -> {
                cancelProviderPayment(
                    providerId = "stripe",
                    providerReference = intent.id,
                    paymentOperationKey = null,
                    amountMinor = null,
                    currency = null,
                    orderId = orderId,
                    userId = userId,
                )
                throw BadRequestException("Order was cancelled while the payment session was being created")
            }
        }
    }

    /**
     * Cancel a Stripe PaymentIntent without holding a database transaction during network I/O.
     *
     * When the provider reference has not been persisted yet, the durable operation key is used
     * to resolve the same idempotent PaymentIntent before cancellation.
     */
    fun cancelProviderPayment(
        providerId: String?,
        providerReference: String?,
        paymentOperationKey: String?,
        amountMinor: Long?,
        currency: String?,
        orderId: UUID,
        userId: UUID,
    ) {
        if (providerId != "stripe") return

        val stripeApi =
            stripeApiClient.ifAvailable
                ?: throw PaymentProviderException(
                    cause = IllegalStateException("Stripe API client is unavailable"),
                )

        val intentId =
            providerReference
                ?: run {
                    val key =
                        paymentOperationKey
                            ?: throw PaymentProviderException(
                                cause =
                                    IllegalStateException(
                                        "Stripe payment operation has no provider reference or operation key",
                                    ),
                            )
                    val amount =
                        amountMinor
                            ?: throw PaymentProviderException(
                                cause = IllegalStateException("Stripe payment operation has no amount"),
                            )
                    val selectedCurrency =
                        currency
                            ?: stripeProperties.ifAvailable?.defaultCurrency
                            ?: throw PaymentProviderException(
                                cause = IllegalStateException("Stripe payment operation has no currency"),
                            )
                    try {
                        stripeApi.createPaymentIntentForClient(
                            amountMinor = amount,
                            currency = selectedCurrency,
                            orderId = orderId,
                            userId = userId,
                            idempotencyKey = key,
                        ).id
                    } catch (ex: StripeApiException) {
                        throw PaymentProviderException(cause = ex)
                    }
                }

        try {
            val result = stripeApi.cancelPaymentIntent(intentId)
            if (result.status != "canceled") {
                throw PaymentProviderException(
                    cause =
                        IllegalStateException(
                            "Stripe PaymentIntent $intentId was not canceled: ${result.status}",
                        ),
                )
            }
        } catch (ex: StripeApiException) {
            throw PaymentProviderException(cause = ex)
        }
    }

    private fun validatePayableStatus(status: OrderStatus) {
        when (status) {
            OrderStatus.PENDING, OrderStatus.CONFIRMED -> Unit
            OrderStatus.PAID -> throw BadRequestException("Order is already paid")
            OrderStatus.CANCELLED -> throw BadRequestException("Cannot pay a cancelled order")
            OrderStatus.SHIPPED, OrderStatus.DELIVERED -> throw BadRequestException("Order is already fulfilled")
        }
    }

    private data class PaymentSessionPlan(
        val orderId: UUID,
        val userId: UUID,
        val amountMinor: Long,
        val currency: String,
        val idempotencyKey: String,
    )

    private enum class PaymentSessionFinalization {
        PERSISTED,
        PAID,
        CANCEL_PROVIDER,
    }

    /**
     * Provider webhook / async capture path: mark order PAID without re-charging.
     * Idempotent when already PAID.
     *
     * When [amountMinor] is provided (Stripe PaymentIntent.amount in minor units),
     * it must match [Order.totalAmount] converted with scale 2. Stripe [currency]
     * must exactly match the order's persisted payment currency.
     */
    @Transactional
    fun markPaidFromProvider(
        orderId: UUID,
        providerId: String,
        providerReference: String?,
        amountMinor: Long? = null,
        currency: String? = null,
    ): OrderResponse {
        val order =
            orderRepository.findByIdForUpdate(orderId)
                ?: throw NotFoundException("Order not found: $orderId")
        if (order.paymentProvider != null && order.paymentProvider != providerId) {
            throw BadRequestException("Payment provider mismatch for order $orderId")
        }
        if (providerReference != null) {
            if (order.paymentProviderReference != null && order.paymentProviderReference != providerReference) {
                throw BadRequestException("Payment provider reference mismatch for order $orderId")
            }
            order.paymentProvider = providerId
            order.paymentProviderReference = providerReference
        }
        when (order.status) {
            OrderStatus.PAID -> return OrderMapper.toResponse(order)
            OrderStatus.PENDING, OrderStatus.CONFIRMED -> Unit
            else ->
                throw BadRequestException(
                    "Cannot mark order ${order.status} as PAID from $providerId",
                )
        }

        if (amountMinor != null) {
            val expectedMinor = Money.toMinorUnits(order.totalAmount)
            if (amountMinor != expectedMinor) {
                throw BadRequestException(
                    "Payment amount mismatch for order $orderId: " +
                        "provider=$amountMinor minor, order=$expectedMinor minor",
                )
            }
        }
        if (providerId == "stripe") {
            val expectedCurrency =
                order.paymentCurrency?.trim()?.lowercase(Locale.ROOT)
                    ?: throw BadRequestException("Order payment currency is missing")
            val normalizedCurrency =
                currency?.trim()?.lowercase(Locale.ROOT)
                    ?: throw BadRequestException("Provider payment currency is missing")
            if (normalizedCurrency != expectedCurrency) {
                throw BadRequestException(
                    "Payment currency mismatch for order $orderId: expected $expectedCurrency, received $normalizedCurrency",
                )
            }
        } else if (!currency.isNullOrBlank()) {
            val normalized = currency.trim().lowercase(Locale.ROOT)
            if (!normalized.matches(Regex("^[a-z]{3}$"))) {
                throw BadRequestException("Invalid provider currency: $currency")
            }
        }

        order.paymentOperationState = PaymentOperationState.NONE
        order.markPaid()
        val saved = orderRepository.save(order)
        notificationService.notifyOrderEvent(
            userId = saved.userId,
            type = "ORDER_PAID",
            title = "Payment received",
            body =
                "Order ${saved.id} is paid via $providerId" +
                    (providerReference?.let { " ($it)" } ?: "") +
                    ". Total ${saved.totalAmount}.",
            orderId = saved.id,
        )
        return OrderMapper.toResponse(saved)
    }
}
