package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.identity.repository.RefreshTokenRepository
import com.kenlikdev.qmarket.order.repository.OrderIdempotencyKeyRepository
import com.kenlikdev.qmarket.order.repository.StripeWebhookEventRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Deletes durable operational history only after its documented retention window.
 *
 * Each invocation deletes at most one bounded batch per table so maintenance work stays short
 * and can safely run alongside normal application traffic.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = ["qmarket.data-retention.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class DataRetentionCleanupScheduler(
    private val properties: DataRetentionProperties,
    private val orderIdempotencyKeyRepository: OrderIdempotencyKeyRepository,
    private val stripeWebhookEventRepository: StripeWebhookEventRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "${qmarket.data-retention.cleanup-interval-ms:3600000}",
        initialDelayString = "${qmarket.data-retention.initial-delay-ms:60000}",
    )
    @Transactional
    fun cleanup() {
        val now = Instant.now()
        val batchSize = properties.batchSize.coerceIn(1, 5000)

        val idempotencyDeleted =
            orderIdempotencyKeyRepository.deleteCreatedBefore(
                cutoff = now.minus(properties.idempotencyRetentionDays.coerceAtLeast(1), ChronoUnit.DAYS),
                batchSize = batchSize,
            )
        val webhookDeleted =
            stripeWebhookEventRepository.deleteReceivedBefore(
                cutoff = now.minus(properties.webhookRetentionDays.coerceAtLeast(1), ChronoUnit.DAYS),
                batchSize = batchSize,
            )
        val refreshTokenDeleted =
            refreshTokenRepository.deleteExpiredBefore(
                cutoff =
                    now.minus(
                        properties.refreshTokenRetentionAfterExpiryDays.coerceAtLeast(0),
                        ChronoUnit.DAYS,
                    ),
                batchSize = batchSize,
            )

        val totalDeleted = idempotencyDeleted + webhookDeleted + refreshTokenDeleted
        if (totalDeleted > 0) {
            log.info(
                "Data retention cleanup deleted {} rows (idempotency={}, webhook={}, refreshTokens={})",
                totalDeleted,
                idempotencyDeleted,
                webhookDeleted,
                refreshTokenDeleted,
            )
        }
    }
}
