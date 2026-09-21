package com.kenlikdev.qmarket.order.payment

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Production safety guard: the mock payment gateway must never be active under the
 * `prod` Spring profile, even when an environment variable overrides configuration.
 */
@Configuration
@Profile("prod")
class ProductionPaymentProviderGuard(
    @Value("${qmarket.payment.provider}") private val provider: String,
) {
    @PostConstruct
    fun validate() {
        require(provider.equals("stripe", ignoreCase = true)) {
            "Production profile requires qmarket.payment.provider=stripe"
        }
    }
}
