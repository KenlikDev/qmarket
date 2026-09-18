package com.kenlikdev.qmarket.order.payment

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Stripe credentials and endpoints. Used only when `qmarket.payment.provider=stripe`.
 */
@ConfigurationProperties(prefix = "qmarket.payment.stripe")
data class StripeProperties(
    /** Secret key (`sk_test_…` / `sk_live_…`). Required when provider=stripe. */
    val secretKey: String = "",
    /**
     * Publishable key (`pk_test_…` / `pk_live_…`) for client Payment Element / mobile SDK.
     * Safe to expose to the client; returned from payment-session API.
     */
    val publishableKey: String = "",
    val apiBaseUrl: String = "https://api.stripe.com",
    /** Default ISO currency for PaymentIntents (lowercase), e.g. rub, usd. */
    val defaultCurrency: String = "rub",
    /**
     * Webhook signing secret (`whsec_…`) from Stripe Dashboard → Developers → Webhooks.
     * Required to accept `POST /api/v1/payments/stripe/webhook`.
     */
    val webhookSecret: String = "",
    /** Max age of Stripe-Signature timestamp (seconds). */
    val webhookToleranceSeconds: Long = 300,
)
