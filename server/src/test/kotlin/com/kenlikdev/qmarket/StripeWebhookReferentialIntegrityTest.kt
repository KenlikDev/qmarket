package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.order.domain.Order
import com.kenlikdev.qmarket.order.domain.OrderStatus
import com.kenlikdev.qmarket.order.domain.StripeWebhookEvent
import com.kenlikdev.qmarket.order.repository.OrderRepository
import com.kenlikdev.qmarket.order.repository.StripeWebhookEventRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.util.UUID

/**
 * Verifies the durable Stripe webhook audit relation to orders at the database boundary.
 *
 * The FK intentionally uses ON DELETE SET NULL: webhook history must remain available
 * even when the referenced order is removed.
 */
@SpringBootTest
@ActiveProfiles("test")
class StripeWebhookReferentialIntegrityTest {
    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var webhookRepository: StripeWebhookEventRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var userId: UUID

    @BeforeEach
    fun setUp() {
        userId =
            requireNotNull(
                jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE email = ?",
                    UUID::class.java,
                    "admin@qmarket.local",
                ),
            )
    }

    @Test
    fun `webhook event rejects orphan order id`() {
        val orphanOrderId = UUID.randomUUID()
        val eventId = "fk-orphan-" + UUID.randomUUID()

        assertThrows<DataIntegrityViolationException> {
            jdbcTemplate.update(
                """
                INSERT INTO stripe_webhook_events (
                    event_id,
                    event_type,
                    status,
                    order_id
                ) VALUES (?, ?, ?, ?)
                """.trimIndent(),
                eventId,
                "payment_intent.succeeded",
                StripeWebhookEvent.STATUS_RECEIVED,
                orphanOrderId,
            )
        }

        assertTrue(
            webhookRepository.findById(eventId).isEmpty(),
            "failed insert must not leave a webhook audit row behind",
        )
    }

    @Test
    fun `deleting an order keeps webhook history and clears order reference`() {
        val order =
            orderRepository.saveAndFlush(
                Order(
                    userId = userId,
                    status = OrderStatus.PENDING,
                    totalAmount = BigDecimal("10.00"),
                ),
            )
        val orderId = requireNotNull(order.id)
        val eventId = "fk-delete-" + UUID.randomUUID()

        webhookRepository.saveAndFlush(
            StripeWebhookEvent(
                eventId = eventId,
                eventType = "payment_intent.succeeded",
                status = StripeWebhookEvent.STATUS_PROCESSED,
                orderId = orderId,
            ),
        )

        orderRepository.deleteById(orderId)
        orderRepository.flush()

        // The FK update happens in PostgreSQL; clear the persistence context before
        // reloading so the assertion cannot read a stale managed entity.
        entityManager.clear()

        val event =
            requireNotNull(
                webhookRepository.findById(eventId).orElse(null),
            )

        assertNull(event.orderId, "webhook audit row must survive with a null order reference")
    }
}
