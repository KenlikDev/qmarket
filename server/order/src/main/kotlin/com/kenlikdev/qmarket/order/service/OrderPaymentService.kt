package com.kenlikdev.qmarket.order.service

import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.NotFoundException
import com.kenlikdev.qmarket.common.exception.PaymentProviderException
import com.kenlikdev.qmarket.common.util.Money
import com.kenlikdev.qmarket.order.domain.OrderStatus
import com.kenlikdev.qmarket.order.dto.OrderResponse
import com.kenlikdev.qmarket.order.dto.PaymentSessionResponse
import com.kenlikdev.qmarket.order.payment.PaymentGateway
import com.kenlikdev.qmarket.order.payment.StripeApiClient
import com.kenlikdev.qmarket.order.payment.StripeApiException
import com.kenlikdev.qmarket.order.payment.StripeProperties
import com.kenlikdev.qmarket.order.repository.OrderRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
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
) {
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
     * Create a Stripe PaymentIntent for client-side confirmation (Payment Element / mobile SDK).
     * Requires `qmarket.payment.provider=stripe`. Order stays PENDING until webhook or pay path.
     */
    @Transactional(readOnly = true)
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

        val order =
            orderRepository.findByIdAndUserId(orderId, userId)
                ?: throw NotFoundException("Order not found")
        when (order.status) {
            OrderStatus.PENDING, OrderStatus.CONFIRMED -> Unit
            OrderStatus.PAID -> throw BadRequestException("Order is already paid")
            OrderStatus.CANCELLED -> throw BadRequestException("Cannot pay a cancelled order")
            OrderStatus.SHIPPED, OrderStatus.DELIVERED ->
                throw BadRequestException("Order is already fulfilled")
        }
        if (order.totalAmount <= BigDecimal.ZERO) {
            throw BadRequestException("Amount must be positive")
        }

        val currency = stripeProps.defaultCurrency
        val amountMinor = Money.toMinorUnits(order.totalAmount)
        try {
            val intent =
                stripeApi.createPaymentIntentForClient(
                    amountMinor = amountMinor,
                    currency = currency,
                    orderId = orderId,
                    userId = userId,
                )
            val secret =
                intent.clientSecret
                    ?: throw BadRequestException("Stripe did not return client_secret")
            return PaymentSessionResponse(
                orderId = orderId,
                providerId = "stripe",
                paymentIntentId = intent.id,
                clientSecret = secret,
                publishableKey = stripeProps.publishableKey,
            )
        } catch (ex: StripeApiException) {
            throw PaymentProviderException(cause = ex)
        }
    }

    /**
     * Provider webhook / async capture path: mark order PAID without re-charging.
     * Idempotent when already PAID.
     *
     * When [amountMinor] is provided (Stripe PaymentIntent.amount in minor units),
     * it must match [Order.totalAmount] converted with scale 2. Optional [currency]
     * must be a 3-letter code when present (orders have no stored currency column yet).
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
            orderRepository.findById(orderId).orElseThrow {
                NotFoundException("Order not found: $orderId")
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
        if (!currency.isNullOrBlank()) {
            val normalized = currency.lowercase()
            if (normalized.length != 3) {
                throw BadRequestException("Invalid provider currency: $currency")
            }
        }

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
