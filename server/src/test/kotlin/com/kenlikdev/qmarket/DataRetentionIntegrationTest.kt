package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.identity.domain.RefreshToken
import com.kenlikdev.qmarket.identity.repository.RefreshTokenRepository
import com.kenlikdev.qmarket.order.domain.Order
import com.kenlikdev.qmarket.order.domain.OrderIdempotencyKey
import com.kenlikdev.qmarket.order.domain.OrderStatus
import com.kenlikdev.qmarket.order.domain.StripeWebhookEvent
import com.kenlikdev.qmarket.order.repository.OrderIdempotencyKeyRepository
import com.kenlikdev.qmarket.order.repository.OrderRepository
import com.kenlikdev.qmarket.order.repository.StripeWebhookEventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "qmarket.data-retention.enabled=true",
        "qmarket.data-retention.initial-delay-ms=86400000",
        "qmarket.data-retention.cleanup-interval-ms=86400000",
    ],
)
class DataRetentionIntegrationTest {
    @Autowired
    private lateinit var properties: DataRetentionProperties

    @Autowired
    private lateinit var scheduler: DataRetentionCleanupScheduler

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderIdempotencyKeyRepository: OrderIdempotencyKeyRepository

    @Autowired
    private lateinit var stripeWebhookEventRepository: StripeWebhookEventRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @BeforeEach
    fun setUp() {
        properties.idempotencyRetentionDays = 30
        properties.webhookRetentionDays = 30
        properties.refreshTokenRetentionAfterExpiryDays = 7
        properties.batchSize = 100
    }

    @Test
    fun `cleanup deletes only rows beyond configured retention windows`() {
        val now = Instant.now()
        val order =
            orderRepository.save(
                Order(
                    userId = UUID.randomUUID(),
                    status = OrderStatus.PENDING,
                    totalAmount = BigDecimal("10.00"),
                ),
            )
        val orderId = requireNotNull(order.id)

        val oldIdempotencyKey =
            orderIdempotencyKeyRepository.save(
                OrderIdempotencyKey(
                    userId = UUID.randomUUID(),
                    key = "retention-old-" + UUID.randomUUID(),
                    orderId = orderId,
                    requestHash = "old",
                    createdAt = now.minus(31, ChronoUnit.DAYS),
                ),
            )
        val freshIdempotencyKey =
            orderIdempotencyKeyRepository.save(
                OrderIdempotencyKey(
                    userId = UUID.randomUUID(),
                    key = "retention-fresh-" + UUID.randomUUID(),
                    orderId = orderId,
                    requestHash = "fresh",
                    createdAt = now.minus(29, ChronoUnit.DAYS),
                ),
            )

        val oldWebhook =
            stripeWebhookEventRepository.save(
                StripeWebhookEvent(
                    eventId = "retention-old-" + UUID.randomUUID(),
                    eventType = "payment_intent.succeeded",
                    status = StripeWebhookEvent.STATUS_FAILED,
                    receivedAt = now.minus(31, ChronoUnit.DAYS),
                    errorMessage = "old error",
                ),
            )
        val freshWebhook =
            stripeWebhookEventRepository.save(
                StripeWebhookEvent(
                    eventId = "retention-fresh-" + UUID.randomUUID(),
                    eventType = "payment_intent.succeeded",
                    status = StripeWebhookEvent.STATUS_PROCESSED,
                    receivedAt = now.minus(29, ChronoUnit.DAYS),
                    processedAt = now.minus(29, ChronoUnit.DAYS),
                ),
            )

        val oldRefresh =
            refreshTokenRepository.save(
                RefreshToken(
                    userId = UUID.randomUUID(),
                    jti = UUID.randomUUID(),
                    familyId = UUID.randomUUID(),
                    expiresAt = now.minus(8, ChronoUnit.DAYS),
                ),
            )
        val freshRefresh =
            refreshTokenRepository.save(
                RefreshToken(
                    userId = UUID.randomUUID(),
                    jti = UUID.randomUUID(),
                    familyId = UUID.randomUUID(),
                    expiresAt = now.minus(6, ChronoUnit.DAYS),
                ),
            )

        scheduler.cleanup()

        assertFalse(orderIdempotencyKeyRepository.findById(requireNotNull(oldIdempotencyKey.id)).isPresent)
        assertTrue(orderIdempotencyKeyRepository.findById(requireNotNull(freshIdempotencyKey.id)).isPresent)
        assertFalse(stripeWebhookEventRepository.findById(oldWebhook.eventId).isPresent)
        assertTrue(stripeWebhookEventRepository.findById(freshWebhook.eventId).isPresent)
        assertFalse(refreshTokenRepository.findById(requireNotNull(oldRefresh.id)).isPresent)
        assertTrue(refreshTokenRepository.findById(requireNotNull(freshRefresh.id)).isPresent)
    }

    @Test
    fun `cleanup respects batch size for every table`() {
        properties.batchSize = 1
        val now = Instant.now()
        val orderId =
            requireNotNull(
                orderRepository.save(
                    Order(
                        userId = UUID.randomUUID(),
                        status = OrderStatus.PENDING,
                        totalAmount = BigDecimal("10.00"),
                    ),
                ).id,
            )

        repeat(2) { index ->
            orderIdempotencyKeyRepository.save(
                OrderIdempotencyKey(
                    userId = UUID.randomUUID(),
                    key = "batch-idem-" + index + "-" + UUID.randomUUID(),
                    orderId = orderId,
                    requestHash = "batch",
                    createdAt = now.minus(31, ChronoUnit.DAYS),
                ),
            )
            stripeWebhookEventRepository.save(
                StripeWebhookEvent(
                    eventId = "batch-webhook-" + index + "-" + UUID.randomUUID(),
                    eventType = "payment_intent.succeeded",
                    status = StripeWebhookEvent.STATUS_FAILED,
                    receivedAt = now.minus(31, ChronoUnit.DAYS),
                ),
            )
            refreshTokenRepository.save(
                RefreshToken(
                    userId = UUID.randomUUID(),
                    jti = UUID.randomUUID(),
                    familyId = UUID.randomUUID(),
                    expiresAt = now.minus(8, ChronoUnit.DAYS),
                ),
            )
        }

        scheduler.cleanup()

        val remainingIdempotency =
            orderIdempotencyKeyRepository
                .findAll()
                .count { it.key.startsWith("batch-idem-") }
        val remainingWebhook =
            stripeWebhookEventRepository
                .findAll()
                .count { it.eventId.startsWith("batch-webhook-") }
        val remainingRefresh =
            refreshTokenRepository
                .findAll()
                .count { it.expiresAt.isBefore(now.minus(7, ChronoUnit.DAYS)) }

        assertEquals(1, remainingIdempotency)
        assertEquals(1, remainingWebhook)
        assertEquals(1, remainingRefresh)
    }
}
