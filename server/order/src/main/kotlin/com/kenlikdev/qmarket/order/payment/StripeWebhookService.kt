package com.kenlikdev.qmarket.order.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
 * Stripe webhook handler with database-backed idempotency and Jackson [JsonNode] parsing
 * (no jackson-module-kotlin required).
 *
 * Flow:
 * 1. Verify signature
 * 2. Parse JSON tree
 * 3. Claim event (INSERT ON CONFLICT DO NOTHING)
 * 4. payment_intent.succeeded → amount/currency + markPaidFromProvider
 * 5. Mark PROCESSED or FAILED
 */
@Service
@ConditionalOnProperty(name = ["qmarket.payment.provider"], havingValue = "stripe")
class StripeWebhookService(
    private val props: StripeProperties,
    private val orderService: OrderService,
    private val eventRepository: StripeWebhookEventRepository,
    private val objectMapper: ObjectMapper,
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

        val root: JsonNode =
            try {
                objectMapper.readTree(payload)
            } catch (ex: Exception) {
                log.warn("Unparseable Stripe webhook payload: {}", ex.message)
                throw BadRequestException("Invalid Stripe webhook payload")
            }

        val eventId =
            root.path("id").asText(null)
                ?: run {
                    log.warn("Stripe webhook without event id")
                    return
                }
        val type = root.path("type").asText("unknown")

        val claimed = eventRepository.tryClaim(eventId, type) == 1
        if (!claimed) {
            val existing = eventRepository.findById(eventId).orElse(null)
            when (existing?.status) {
                StripeWebhookEvent.STATUS_PROCESSED -> {
                    log.info("Ignoring already PROCESSED Stripe event {}", eventId)
                    return
                }
                StripeWebhookEvent.STATUS_RECEIVED -> {
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
                StripeWebhookEvent(eventId = eventId, eventType = type).also { eventRepository.save(it) }
            }

        try {
            when (type) {
                "payment_intent.succeeded" -> {
                    val result = onPaymentIntentSucceeded(root)
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

    private fun onPaymentIntentSucceeded(root: JsonNode): ProcessResult {
        val pi = root.path("data").path("object")
        val intentId = pi.path("id").asText(null)
        val orderIdStr =
            pi.path("metadata").path("order_id").asText(null)
                ?: run {
                    log.warn("payment_intent.succeeded without metadata.order_id, pi={}", intentId)
                    return ProcessResult(orderId = null, providerReference = intentId)
                }
        val orderId =
            runCatching { UUID.fromString(orderIdStr) }.getOrElse {
                log.warn("Invalid order_id in Stripe metadata: {}", orderIdStr)
                return ProcessResult(orderId = null, providerReference = intentId)
            }

        val amountNode = pi.get("amount")
        val amountMinor = if (amountNode != null && amountNode.isNumber) amountNode.asLong() else null
        val currency = pi.path("currency").asText(null)

        orderService.markPaidFromProvider(
            orderId = orderId,
            providerId = "stripe",
            providerReference = intentId,
            amountMinor = amountMinor,
            currency = currency,
        )
        return ProcessResult(orderId = orderId, providerReference = intentId)
    }
}
