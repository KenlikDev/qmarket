package com.kenlikdev.qmarket.order.payment

import jakarta.annotation.PostConstruct
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.util.Currency
import java.util.Locale

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
        val currencyCode = properties.defaultCurrency.trim().uppercase(Locale.ROOT)
        require(currencyCode.matches(Regex("^[A-Z]{3}$"))) {
            "qmarket.payment.stripe.default-currency must be a 3-letter ISO currency code"
        }

        val currency =
            runCatching { Currency.getInstance(currencyCode) }.getOrNull()
        require(currency != null) {
            "qmarket.payment.stripe.default-currency must be a supported ISO currency"
        }
        require(currency.defaultFractionDigits == 2) {
            "qmarket.payment.stripe.default-currency must use two fractional digits"
        }

        require(properties.webhookToleranceSeconds in 1..86_400) {
            "qmarket.payment.stripe.webhook-tolerance-seconds must be between 1 and 86400"
        }
    }
}
