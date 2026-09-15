package com.kenlikdev.qmarket.order.payment

import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.order.service.OrderService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles Stripe webhook events. Idempotent on [event id] within process lifetime
 * (and no-op if order already PAID).
 */
@Service
@ConditionalOnProperty(name = ["qmarket.payment.provider"], havingValue = "stripe")
class StripeWebhookService(
    private val props: StripeProperties,
    private val orderService: OrderService,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val processedEvents = ConcurrentHashMap.newKeySet<String>()

    fun handle(
        payload: String,
        signatureHeader: String?,
    ) {
        val secret = props.webhookSecret
        if (secret.isBlank()) {
            throw BadRequestException("Stripe webhook secret is not configured")
        }
        if (signatureHeader.isNullOrBlank() ||
            !StripeWebhookVerifier.verify(
                payload = payload,
                signatureHeader = signatureHeader,
                secret = secret,
                toleranceSeconds = props.webhookToleranceSeconds,
            )
        ) {
            throw UnauthorizedException("Invalid Stripe webhook signature")
        }

        val eventId = extractJsonString(payload, "id")
        val type = extractJsonString(payload, "type")
        if (eventId != null && !processedEvents.add(eventId)) {
            log.info("Ignoring duplicate Stripe event {}", eventId)
            return
        }

        when (type) {
            "payment_intent.succeeded" -> onPaymentIntentSucceeded(payload)
            else -> log.debug("Ignoring Stripe event type={}", type)
        }
    }

    private fun onPaymentIntentSucceeded(payload: String) {
        val intentId = extractNestedId(payload) ?: extractJsonString(payload, "id")
        // metadata.order_id lives under data.object.metadata
        val orderIdStr =
            extractMetadataOrderId(payload)
                ?: run {
                    log.warn("payment_intent.succeeded without metadata.order_id, pi={}", intentId)
                    return
                }
        val orderId =
            runCatching { UUID.fromString(orderIdStr) }.getOrElse {
                log.warn("Invalid order_id in Stripe metadata: {}", orderIdStr)
                return
            }
        orderService.markPaidFromProvider(
            orderId = orderId,
            providerId = "stripe",
            providerReference = intentId,
        )
    }

    private fun extractJsonString(
        json: String,
        field: String,
    ): String? {
        val pattern = Regex("\"${Regex.escape(field)}\"\\s*:\\s*\"([^\"]+)\"")
        return pattern.find(json)?.groupValues?.get(1)
    }

    /** Prefer data.object.id for PaymentIntent id inside event envelope. */
    private fun extractNestedId(payload: String): String? {
        val objectBlock = Regex("\"object\"\\s*:\\s*\\{([^}]{0,2000})\\}").find(payload)?.groupValues?.get(1)
        return objectBlock?.let { extractJsonString("{$it}", "id") }
    }

    private fun extractMetadataOrderId(payload: String): String? {
        val meta = Regex("\"metadata\"\\s*:\\s*\\{([^}]*)\\}").find(payload)?.groupValues?.get(1) ?: return null
        return Regex("\"order_id\"\\s*:\\s*\"([^\"]+)\"").find(meta)?.groupValues?.get(1)
    }
}
