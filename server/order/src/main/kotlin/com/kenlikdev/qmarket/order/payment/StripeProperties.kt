package com.kenlikdev.qmarket.order.payment

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Stripe credentials and endpoints. Used only when `qmarket.payment.provider=stripe`.
 */
@ConfigurationProperties(prefix = "qmarket.payment.stripe")
data class StripeProperties(
    /** Secret key (`sk_test_…` / `sk_live_…`). Required when provider=stripe. */
    val secretKey: String = "",
    val apiBaseUrl: String = "https://api.stripe.com",
    /** Default ISO currency for PaymentIntents (lowercase), e.g. rub, usd. */
    val defaultCurrency: String = "rub",
)
