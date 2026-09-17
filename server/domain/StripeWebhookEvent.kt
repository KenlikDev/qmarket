package com.kenlikdev.qmarket.order.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "stripe_webhook_events")
class StripeWebhookEvent(
    @Id
    @Column(name = "event_id", nullable = false, length = 64)
    var eventId: String = "",
    @Column(name = "event_type", nullable = false, length = 128)
    var eventType: String = "",
    /** RECEIVED → PROCESSED | FAILED */
    @Column(name = "status", nullable = false, length = 32)
    var status: String = STATUS_RECEIVED,
    @Column(name = "provider_reference", length = 128)
    var providerReference: String? = null,
    @Column(name = "order_id")
    var orderId: UUID? = null,
    @Column(name = "received_at", nullable = false)
    var receivedAt: Instant = Instant.now(),
    @Column(name = "processed_at")
    var processedAt: Instant? = null,
    @Column(name = "error_message", length = 512)
    var errorMessage: String? = null,
) {
    fun markProcessed(
        providerReference: String?,
        orderId: UUID?,
    ) {
        this.status = STATUS_PROCESSED
        this.providerReference = providerReference
        this.orderId = orderId
        this.processedAt = Instant.now()
        this.errorMessage = null
    }

    fun markFailed(message: String) {
        this.status = STATUS_FAILED
        this.processedAt = Instant.now()
        this.errorMessage = message.take(512)
    }

    companion object {
        const val STATUS_RECEIVED = "RECEIVED"
        const val STATUS_PROCESSED = "PROCESSED"
        const val STATUS_FAILED = "FAILED"
    }
}
