package com.kenlikdev.qmarket.order.service

import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.order.domain.OrderStatus
import com.kenlikdev.qmarket.order.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.time.Instant

/**
 * Periodically releases inventory held by unpaid, expired orders.
 * The order service owns row locking, provider cancellation, and stock restoration.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = ["qmarket.order.payment-expiry.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class UnpaidOrderExpiryScheduler(
    private val orderRepository: OrderRepository,
    private val orderService: OrderService,
    private val properties: PaymentExpiryProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${qmarket.order.payment-expiry.poll-interval-ms:60000}",
        initialDelayString = "\${qmarket.order.payment-expiry.initial-delay-ms:15000}",
    )
    fun expireDueOrders() {
        val batchSize = properties.batchSize.coerceIn(1, 1000)
        val now = Instant.now()
        val staleBefore =
            now.minusSeconds(
                properties.operationStaleAfterSeconds.coerceAtLeast(1),
            )
        val ids =
            orderRepository.findOrdersRequiringPaymentRecovery(
                statuses = setOf(OrderStatus.PENDING, OrderStatus.CONFIRMED),
                now = now,
                staleBefore = staleBefore,
                pageable = PageRequest.of(0, batchSize),
            )

        ids.forEach { orderId ->
            try {
                orderService.expireUnpaidOrder(orderId)
            } catch (_: BadRequestException) {
                // A concurrent payment or lifecycle transition may have changed the state.
            } catch (exception: Exception) {
                log.warn("Failed to expire unpaid order {}", orderId, exception)
            }
        }
    }
}
