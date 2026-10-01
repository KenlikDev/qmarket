package com.kenlikdev.qmarket.order.service

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "qmarket.order.payment-expiry")
class PaymentExpiryProperties {
    var enabled: Boolean = true
    var timeoutSeconds: Long = 900
    var pollIntervalMs: Long = 60_000
    var initialDelayMs: Long = 15_000
    var batchSize: Int = 100
    var operationStaleAfterSeconds: Long = 120
}
