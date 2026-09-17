package com.kenlikdev.qmarket.order.repository

import com.kenlikdev.qmarket.order.domain.StripeWebhookEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StripeWebhookEventRepository : JpaRepository<StripeWebhookEvent, String> {
    /**
     * Atomic claim: returns 1 if this process owns the event, 0 if already present.
     * Does not overwrite an existing row (idempotent under concurrent Stripe retries).
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
}
