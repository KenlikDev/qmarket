package com.kenlikdev.qmarket.order.repository

import com.kenlikdev.qmarket.order.domain.StripeWebhookEvent
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StripeWebhookEventRepository : JpaRepository<StripeWebhookEvent, String> {
    /**
     * Atomic claim. The insert is part of the same transaction as event processing.
     */
    @Modifying
    @Query(
        value =
            """
            INSERT INTO stripe_webhook_events (event_id, event_type, status, received_at)
            VALUES (:eventId, :eventType, 'RECEIVED', NOW())
            ON CONFLICT (event_id) DO NOTHING
            """,
        nativeQuery = true,
    )
    fun tryClaim(
        @Param("eventId") eventId: String,
        @Param("eventType") eventType: String,
    ): Int

    /**
     * Serialize concurrent retries for the same Stripe event.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from StripeWebhookEvent e where e.eventId = :eventId")
    fun findByEventIdForUpdate(
        @Param("eventId") eventId: String,
    ): StripeWebhookEvent?

    @Modifying
    @Query(
        value = """
            DELETE FROM stripe_webhook_events
            WHERE event_id IN (
                SELECT event_id
                FROM stripe_webhook_events
                WHERE received_at < :cutoff
                ORDER BY received_at ASC, event_id ASC
                LIMIT :batchSize
            )
            """,
        nativeQuery = true,
    )
    fun deleteReceivedBefore(
        @Param("cutoff") cutoff: java.time.Instant,
        @Param("batchSize") batchSize: Int,
    ): Int
}
