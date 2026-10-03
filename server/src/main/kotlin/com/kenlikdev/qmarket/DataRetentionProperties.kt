package com.kenlikdev.qmarket

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "qmarket.data-retention")
class DataRetentionProperties {
    var enabled: Boolean = true
    var cleanupIntervalMs: Long = 3_600_000
    var initialDelayMs: Long = 60_000
    var batchSize: Int = 500
    var idempotencyRetentionDays: Long = 30
    var webhookRetentionDays: Long = 30
    var refreshTokenRetentionAfterExpiryDays: Long = 7
}
