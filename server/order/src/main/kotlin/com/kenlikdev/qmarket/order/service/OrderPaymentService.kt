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
import com.kenlikdev.qmarket.order.payment.StripePaymentIntentResult
import com.kenlikdev.qmarket.order.payment.StripeProperties
import com.kenlikdev.qmarket.order.repository.OrderRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
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
    private val paymentExpiryProperties: PaymentExpiryProperties,
    transactionManager: PlatformTransactionManager,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    /**
     * Charge via [PaymentGateway] without holding a database transaction during external I/O.
     *
     * A short transaction marks the order as owned by a durable payment operation. The gateway
     * call runs after that transaction commits. A second short transaction finalizes the result.
     * If the provider call fails with an unknown outcome, the CREATING marker remains durable so
     * the scheduler can retry the same idempotent order operation later.
     */
    fun pay(
        userId: UUID,
        orderId: UUID,
    ): OrderResponse = payInternal(userId, orderId, allowStaleCreatingRecovery = false)

    /**
     * Retry a stale generic payment operation after its original provider call may have timed out
     * or the process may have crashed before local finalization.
     */
    fun recoverStaleGenericPaymentOperation(
        userId: UUID,
        orderId: UUID,
    ): OrderResponse = payInternal(userId, orderId, allowStaleCreatingRecovery = true)

    private fun payInternal(
        userId: UUID,
        orderId: UUID,
        allowStaleCreatingRecovery: Boolean,
    ): OrderResponse {
        val plan =
            requireNotNull(
                transactionTemplate.execute {
                    val order =
                        orderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                            ?: throw NotFoundException("Order not found")

                    when (order.paymentOperationState) {
                        PaymentOperationState.NONE -> Unit
                        PaymentOperationState.CANCELLING ->
                            throw BadRequestException("Order cancellation is already in progress")
                        PaymentOperationState.CREATING -> {
                            if (
                                !allowStaleCreatingRecovery ||
                                !order.isPaymentOperationStale(
                                    now = Instant.now(),
                                    staleAfterSeconds = paymentExpiryProperties.operationStaleAfterSeconds,
                                )
                            ) {
                                throw BadRequestException("Order payment is already in progress")
                            }
                        }
                    }

                    validatePayableStatus(order.status)
                    val recoveringStaleOperation =
                        allowStaleCreatingRecovery &&
                            order.paymentOperationState == PaymentOperationState.CREATING &&
                            order.isPaymentOperationStale(
                                now = Instant.now(),
                                staleAfterSeconds = paymentExpiryProperties.operationStaleAfterSeconds,
                            )
                    if (order.isPaymentExpired() && !recoveringStaleOperation) {
                        throw BadRequestException("Payment window has expired; cancel the order")
                    }

                    val providerId = paymentGateway.providerId
                    val currency =
                        (order.paymentCurrency ?: "RUB")
                            .trim()
                            .lowercase(Locale.ROOT)
                    require(currency.matches(Regex("^[a-z]{3}$"))) {
                        "Order payment currency is invalid"
                    }

                    order.paymentProvider = providerId
                    order.paymentCurrency = currency
                    val operationId = UUID.randomUUID()
                    order.paymentOperationState = PaymentOperationState.CREATING
                    order.paymentOperationId = operationId
                    order.paymentOperationStartedAt = Instant.now()
                    orderRepository.save(order)

                    PaymentChargePlan(
                        orderId = orderId,
                        userId = userId,
                        amount = order.totalAmount,
                        currency = currency,
                        providerId = providerId,
                        operationId = operationId,
                    )
                },
            ) { "Payment preparation transaction returned no result" }

        val charge =
            // No Spring transaction is active here: provider I/O must not hold a DB connection.
            paymentGateway.charge(
                orderId = plan.orderId,
                userId = plan.userId,
                amount = plan.amount,
                currency = plan.currency.uppercase(Locale.ROOT),
            )

        if (!charge.success) {
            requireNotNull(
                transactionTemplate.execute {
                    val order =
                        orderRepository.findByIdAndUserIdForUpdate(plan.orderId, plan.userId)
                            ?: throw NotFoundException("Order not found")
                    if (
                        order.paymentOperationState == PaymentOperationState.CREATING &&
                        order.paymentOperationId == plan.operationId
                    ) {
                        order.paymentOperationState = PaymentOperationState.NONE
                        order.paymentOperationId = null
                        order.paymentOperationStartedAt = null
                        orderRepository.save(order)
                    }
                },
            ) { "Payment decline finalization transaction returned no result" }
            throw BadRequestException(charge.message ?: "Payment declined by ${paymentGateway.providerId}")
        }

        return requireNotNull(
            transactionTemplate.execute {
                val order =
                    orderRepository.findByIdAndUserIdForUpdate(plan.orderId, plan.userId)
                        ?: throw NotFoundException("Order not found")

                if (order.status == OrderStatus.PAID) {
                    return@execute OrderMapper.toResponse(order)
                }

                if (
                    order.paymentOperationState != PaymentOperationState.CREATING ||
                    order.paymentOperationId != plan.operationId
                ) {
                    throw BadRequestException("Order payment state changed during payment")
                }

                order.paymentProvider = plan.providerId
                order.paymentProviderReference = charge.providerReference
                order.paymentCurrency = plan.currency
                order.paymentOperationState = PaymentOperationState.NONE
                order.paymentOperationId = null
                order.paymentOperationStartedAt = null
                order.markPaid()

                val saved = orderRepository.save(order)
                notificationService.notifyOrderEvent(
                    userId = saved.userId,
                    type = "ORDER_PAID",
                    title = "Payment received",
                    body = "Order ${saved.id} is paid. Total ${saved.totalAmount}.",
                    orderId = saved.id,
                )
                OrderMapper.toResponse(saved)
            },
        ) { "Payment finalization transaction returned no result" }
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
                    if (order.isPaymentExpired()) {
                        throw BadRequestException("Payment window has expired; cancel the order")
                    }
                    if (order.totalAmount <= BigDecimal.ZERO) {
                        throw BadRequestException("Amount must be positive")
                    }
                    if (order.paymentProvider != null && order.paymentProvider != "stripe") {
                        throw BadRequestException("Order already has a different payment provider")
                    }
                    when (order.paymentOperationState) {
                        PaymentOperationState.NONE -> Unit
                        PaymentOperationState.CANCELLING ->
                            throw BadRequestException("Order payment is being cancelled")
                        PaymentOperationState.CREATING -> {
                            if (
                                !order.isPaymentOperationStale(
                                    now = Instant.now(),
                                    staleAfterSeconds = paymentExpiryProperties.operationStaleAfterSeconds,
                                )
                            ) {
                                throw BadRequestException("Order payment session is already in progress")
                            }
                        }
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
                    val operationId = UUID.randomUUID()
                    order.paymentOperationState = PaymentOperationState.CREATING
                    order.paymentOperationId = operationId
                    order.paymentOperationStartedAt = Instant.now()
                    orderRepository.save(order)

                    PaymentSessionPlan(
                        orderId = orderId,
                        userId = userId,
                        amountMinor = Money.toMinorUnits(order.totalAmount),
                        currency = currency,
                        idempotencyKey = operationKey,
                        operationId = operationId,
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
            } catch (exception: Exception) {
                resetFailedPaymentOperation(plan)
                if (exception is StripeApiException) {
                    throw PaymentProviderException(cause = exception)
                }
                throw exception
            }

        val clientSecret =
            intent.clientSecret
                ?: run {
                    resetFailedPaymentOperation(plan)
                    throw PaymentProviderException(
                        cause = IllegalStateException("Stripe did not return client_secret"),
                    )
                }

        val finalization =
            try {
                requireNotNull(
                    transactionTemplate.execute {
                        val order =
                            orderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                                ?: throw NotFoundException("Order not found")

                        when (order.status) {
                            OrderStatus.PENDING, OrderStatus.CONFIRMED -> {
                                when (order.paymentOperationState) {
                                    PaymentOperationState.CANCELLING ->
                                        PaymentSessionFinalization.CANCEL_PROVIDER

                                    PaymentOperationState.CREATING -> {
                                        if (order.paymentOperationId != plan.operationId) {
                                            throw BadRequestException(
                                                "Another payment session operation owns this order",
                                            )
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
                                        order.paymentOperationId = null
                                        order.paymentOperationStartedAt = null
                                        orderRepository.save(order)
                                        PaymentSessionFinalization.PERSISTED
                                    }

                                    PaymentOperationState.NONE -> {
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
                                        orderRepository.save(order)
                                        PaymentSessionFinalization.PERSISTED
                                    }
                                }
                            }

                            OrderStatus.PAID -> PaymentSessionFinalization.PAID
                            OrderStatus.CANCELLED -> PaymentSessionFinalization.CANCEL_PROVIDER
                            OrderStatus.SHIPPED, OrderStatus.DELIVERED ->
                                throw BadRequestException("Order is already fulfilled")
                        }
                    },
                ) { "Payment-session finalization transaction returned no result" }
            } catch (exception: Exception) {
                resetFailedPaymentOperation(plan)
                throw exception
            }

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
                val cancellationResult =
                    cancelProviderPayment(
                        providerId = "stripe",
                        providerReference = intent.id,
                        paymentOperationKey = null,
                        amountMinor = plan.amountMinor,
                        currency = plan.currency,
                        orderId = orderId,
                        userId = userId,
                    )
                if (cancellationResult?.status == "succeeded") {
                    markPaidFromProvider(
                        orderId = orderId,
                        providerId = "stripe",
                        providerReference = cancellationResult.id,
                        amountMinor = cancellationResult.amountMinor ?: plan.amountMinor,
                        currency = cancellationResult.currency ?: plan.currency,
                    )
                    throw BadRequestException(
                        "Order was paid while the payment session was being created",
                    )
                }
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
    ): StripePaymentIntentResult? {
        if (providerId != "stripe") return null

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

        fun retrieve(): StripePaymentIntentResult =
            try {
                stripeApi.retrievePaymentIntent(intentId)
            } catch (ex: StripeApiException) {
                throw PaymentProviderException(cause = ex)
            }

        val current = retrieve()
        validateProviderIntent(
            intent = current,
            expectedId = intentId,
            expectedAmountMinor = amountMinor,
            expectedCurrency = currency,
        )
        when (current.status) {
            "canceled" -> return current
            "succeeded" -> return current
        }

        try {
            val result = stripeApi.cancelPaymentIntent(intentId)
            validateProviderIntent(
                intent = result,
                expectedId = intentId,
                expectedAmountMinor = amountMinor,
                expectedCurrency = currency,
            )
            if (result.status == "canceled" || result.status == "succeeded") return result
        } catch (ex: StripeApiException) {
            val reconciled =
                try {
                    retrieve()
                } catch (_: PaymentProviderException) {
                    throw PaymentProviderException(cause = ex)
                }
            when (reconciled.status) {
                "canceled", "succeeded" -> return reconciled
                else -> throw PaymentProviderException(cause = ex)
            }
        }

        val reconciled = retrieve()
        return when (reconciled.status) {
            "canceled", "succeeded" -> reconciled
            else ->
                throw PaymentProviderException(
                    cause =
                        IllegalStateException(
                            "Stripe PaymentIntent $intentId was not canceled: ${reconciled.status}",
                        ),
                )
        }
    }

    private fun validateProviderIntent(
        intent: StripePaymentIntentResult,
        expectedId: String,
        expectedAmountMinor: Long?,
        expectedCurrency: String?,
    ) {
        if (intent.id != expectedId) {
            throw PaymentProviderException(
                cause =
                    IllegalStateException(
                        "Stripe PaymentIntent reference mismatch: expected $expectedId, received ${intent.id}",
                    ),
            )
        }
        if (expectedAmountMinor != null) {
            val providerAmount =
                intent.amountMinor
                    ?: throw PaymentProviderException(
                        cause = IllegalStateException("Stripe PaymentIntent amount is missing during reconciliation"),
                    )
            if (providerAmount != expectedAmountMinor) {
                throw PaymentProviderException(
                    cause =
                        IllegalStateException(
                            "Stripe PaymentIntent amount mismatch: expected $expectedAmountMinor, received $providerAmount",
                        ),
                )
            }
        }
        if (expectedCurrency != null) {
            val providerCurrency =
                intent.currency?.trim()?.lowercase(Locale.ROOT)
                    ?: throw PaymentProviderException(
                        cause =
                            IllegalStateException("Stripe PaymentIntent currency is missing during reconciliation"),
                    )
            if (providerCurrency != expectedCurrency.trim().lowercase(Locale.ROOT)) {
                throw PaymentProviderException(
                    cause =
                        IllegalStateException(
                            "Stripe PaymentIntent currency mismatch: " +
                                "expected ${expectedCurrency.trim().lowercase(Locale.ROOT)}, " +
                                "received $providerCurrency",
                        ),
                )
            }
        }
    }

    private fun resetFailedPaymentOperation(plan: PaymentSessionPlan) {
        transactionTemplate.execute {
            val order = orderRepository.findByIdAndUserIdForUpdate(plan.orderId, plan.userId) ?: return@execute
            if (
                order.paymentOperationState == PaymentOperationState.CREATING &&
                order.paymentOperationId == plan.operationId
            ) {
                order.paymentOperationState = PaymentOperationState.NONE
                order.paymentOperationId = null
                order.paymentOperationStartedAt = null
                orderRepository.save(order)
            }
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

    private data class PaymentChargePlan(
        val orderId: UUID,
        val userId: UUID,
        val amount: BigDecimal,
        val currency: String,
        val providerId: String,
        val operationId: UUID,
    )
    private data class PaymentSessionPlan(
        val orderId: UUID,
        val userId: UUID,
        val amountMinor: Long,
        val currency: String,
        val idempotencyKey: String,
        val operationId: UUID,
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

        // Provider success is authoritative when it wins the external race against local cancellation.
        // A later cancellation finalization will observe PAID and must not restore inventory.

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

        if (order.paymentOperationState == PaymentOperationState.CANCELLING) {
            // Stripe/PSP success wins the cancellation race. The cancellation caller will
            // observe PAID during its finalization transaction and must not restore stock.
        }
        order.paymentOperationState = PaymentOperationState.NONE
        order.paymentOperationId = null
        order.paymentOperationStartedAt = null
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
