package com.kenlikdev.qmarket.order.payment

import jakarta.annotation.PostConstruct
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["qmarket.payment.provider"], havingValue = "stripe")
@EnableConfigurationProperties(StripeProperties::class)
class PaymentConfiguration(
    private val properties: StripeProperties,
) {
    @PostConstruct
    fun validateStripeConfiguration() {
        require(properties.secretKey.isNotBlank()) {
            "qmarket.payment.stripe.secret-key is required when qmarket.payment.provider=stripe"
        }
        require(properties.publishableKey.isNotBlank()) {
            "qmarket.payment.stripe.publishable-key is required when qmarket.payment.provider=stripe"
        }
        require(properties.webhookSecret.isNotBlank()) {
            "qmarket.payment.stripe.webhook-secret is required when qmarket.payment.provider=stripe"
        }
        require(properties.defaultCurrency.trim().matches(Regex("^[A-Za-z]{3}$"))) {
            "qmarket.payment.stripe.default-currency must be a 3-letter ISO currency code"
        }
    }
}
