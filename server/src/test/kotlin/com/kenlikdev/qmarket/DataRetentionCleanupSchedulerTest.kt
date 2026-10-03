package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.identity.repository.RefreshTokenRepository
import com.kenlikdev.qmarket.order.repository.OrderIdempotencyKeyRepository
import com.kenlikdev.qmarket.order.repository.StripeWebhookEventRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DataRetentionCleanupSchedulerTest {
    private lateinit var properties: DataRetentionProperties
    private lateinit var orderIdempotencyKeyRepository: OrderIdempotencyKeyRepository
    private lateinit var stripeWebhookEventRepository: StripeWebhookEventRepository
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var scheduler: DataRetentionCleanupScheduler

    @BeforeEach
    fun setUp() {
        properties =
            DataRetentionProperties().apply {
                batchSize = 17
                idempotencyRetentionDays = 30
                webhookRetentionDays = 30
                refreshTokenRetentionAfterExpiryDays = 7
            }
        orderIdempotencyKeyRepository = mockk()
        stripeWebhookEventRepository = mockk()
        refreshTokenRepository = mockk()
        scheduler =
            DataRetentionCleanupScheduler(
                properties,
                orderIdempotencyKeyRepository,
                stripeWebhookEventRepository,
                refreshTokenRepository,
            )
    }

    @Test
    fun `cleanup requests one bounded batch per table`() {
        every { orderIdempotencyKeyRepository.deleteCreatedBefore(any(), 17) } returns 2
        every { stripeWebhookEventRepository.deleteReceivedBefore(any(), 17) } returns 3
        every { refreshTokenRepository.deleteExpiredBefore(any(), 17) } returns 4

        scheduler.cleanup()

        verify(exactly = 1) { orderIdempotencyKeyRepository.deleteCreatedBefore(any(), 17) }
        verify(exactly = 1) { stripeWebhookEventRepository.deleteReceivedBefore(any(), 17) }
        verify(exactly = 1) { refreshTokenRepository.deleteExpiredBefore(any(), 17) }
    }

    @Test
    fun `cleanup clamps oversized batch size`() {
        properties.batchSize = 10_000
        every { orderIdempotencyKeyRepository.deleteCreatedBefore(any(), 5000) } returns 0
        every { stripeWebhookEventRepository.deleteReceivedBefore(any(), 5000) } returns 0
        every { refreshTokenRepository.deleteExpiredBefore(any(), 5000) } returns 0

        scheduler.cleanup()

        verify(exactly = 1) { orderIdempotencyKeyRepository.deleteCreatedBefore(any(), 5000) }
        verify(exactly = 1) { stripeWebhookEventRepository.deleteReceivedBefore(any(), 5000) }
        verify(exactly = 1) { refreshTokenRepository.deleteExpiredBefore(any(), 5000) }
    }
}
