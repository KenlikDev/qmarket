package com.kenlikdev.qmarket.order.payment

import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.order.domain.StripeWebhookEvent
import com.kenlikdev.qmarket.order.repository.StripeWebhookEventRepository
import com.kenlikdev.qmarket.order.service.OrderService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Stripe webhook handler with **database-backed** idempotency.
 *
 * Flow:
 * 1. Verify signature (outside business TX concerns for auth failures).
 * 2. Parse event id/type.
 * 3. Claim event via INSERT … ON CONFLICT DO NOTHING (source of truth in Postgres).
 * 4. Only the winner runs business logic; then marks PROCESSED (or FAILED).
 *
 * Transient failures after claim leave status RECEIVED/FAILED so Stripe retries
 * can re-process (we allow re-entry only for FAILED; PROCESSED is terminal no-op).
 */
@Service
@ConditionalOnProperty(name = ["qmarket.payment.provider"], havingValue = "stripe")
class StripeWebhookService(
    private val props: StripeProperties,
    private val orderService: OrderService,
    private val eventRepository: StripeWebhookEventRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
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

        val eventId =
            extractJsonString(payload, "id")
                ?: run {
                    log.warn("Stripe webhook without event id")
                    return
                }
        val type = extractJsonString(payload, "type") ?: "unknown"

        val claimed = eventRepository.tryClaim(eventId, type) == 1
        if (!claimed) {
            val existing = eventRepository.findById(eventId).orElse(null)
            when (existing?.status) {
                StripeWebhookEvent.STATUS_PROCESSED -> {
                    log.info("Ignoring already PROCESSED Stripe event {}", eventId)
                    return
                }
                StripeWebhookEvent.STATUS_RECEIVED -> {
                    // Another in-flight worker claimed it; avoid double process.
                    log.info("Stripe event {} already claimed (RECEIVED), skipping", eventId)
                    return
                }
                StripeWebhookEvent.STATUS_FAILED -> {
                    log.info("Retrying FAILED Stripe event {}", eventId)
                }
                else -> {
                    log.info("Ignoring duplicate Stripe event {}", eventId)
                    return
                }
            }
        }

        val event =
            eventRepository.findById(eventId).orElseGet {
                // Should exist after claim; defensive create for FAILED retry path.
                StripeWebhookEvent(eventId = eventId, eventType = type).also { eventRepository.save(it) }
            }

        try {
            when (type) {
                "payment_intent.succeeded" -> {
                    val result = onPaymentIntentSucceeded(payload)
                    event.markProcessed(result.providerReference, result.orderId)
                }
                else -> {
                    log.debug("Ignoring Stripe event type={}", type)
                    event.markProcessed(providerReference = null, orderId = null)
                }
            }
            eventRepository.save(event)
        } catch (ex: Exception) {
            log.error("Failed processing Stripe event {}: {}", eventId, ex.message)
            event.markFailed(ex.message ?: ex.javaClass.simpleName)
            eventRepository.save(event)
            throw ex
        }
    }

    private data class ProcessResult(
        val orderId: UUID?,
        val providerReference: String?,
    )

    private fun onPaymentIntentSucceeded(payload: String): ProcessResult {
        val intentId = extractNestedId(payload) ?: extractJsonString(payload, "id")
        val orderIdStr =
            extractMetadataOrderId(payload)
                ?: run {
                    log.warn("payment_intent.succeeded without metadata.order_id, pi={}", intentId)
                    return ProcessResult(orderId = null, providerReference = intentId)
                }
        val orderId =
            runCatching { UUID.fromString(orderIdStr) }.getOrElse {
                log.warn("Invalid order_id in Stripe metadata: {}", orderIdStr)
                return ProcessResult(orderId = null, providerReference = intentId)
            }
        orderService.markPaidFromProvider(
            orderId = orderId,
            providerId = "stripe",
            providerReference = intentId,
        )
        return ProcessResult(orderId = orderId, providerReference = intentId)
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
