package com.kenlikdev.qmarket.order.payment

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Minimal Stripe Event envelope for webhook processing.
 * Only fields we actually use — unknown properties are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class StripeEventEnvelope(
    val id: String? = null,
    val type: String? = null,
    val data: StripeEventData? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class StripeEventData(
    val `object`: StripePaymentIntentObject? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class StripePaymentIntentObject(
    val id: String? = null,
    /** Amount in the smallest currency unit (e.g. cents). */
    val amount: Long? = null,
    val currency: String? = null,
    val status: String? = null,
    val metadata: Map<String, String>? = null,
) {
    fun orderIdMetadata(): String? = metadata?.get("order_id")
}
