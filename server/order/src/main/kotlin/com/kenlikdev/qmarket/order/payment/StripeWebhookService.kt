package com.kenlikdev.qmarket.order.payment

import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.order.domain.StripeWebhookEvent
import com.kenlikdev.qmarket.order.repository.StripeWebhookEventRepository
import com.kenlikdev.qmarket.order.service.OrderService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * Stripe webhook handler with database-backed idempotency.
 *
 * Claim, business processing, and PROCESSED state are committed atomically.
 * FAILED state is persisted in a separate transaction after a processing rollback.
 */
@Service
@ConditionalOnProperty(name = ["qmarket.payment.provider"], havingValue = "stripe")
class StripeWebhookService(
    private val props: StripeProperties,
    private val orderService: OrderService,
    private val eventRepository: StripeWebhookEventRepository,
    private val objectMapper: ObjectMapper,
    private val paymentMetrics: ObjectProvider<PaymentMetrics>,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    fun handle(
        payload: String,
        signatureHeader: String?,
    ) {
        validateSignature(payload, signatureHeader)

        val root = parsePayload(payload)
        val eventId =
            root.path("id").asString(null)?.trim()
                ?: throw BadRequestException("Stripe webhook is missing event id")
        val type = root.path("type").asString(null)?.trim().orEmpty()
        if (type.isEmpty()) {
            throw BadRequestException("Stripe webhook is missing event type")
        }

        metrics()?.webhookReceived()
        log.info("Stripe webhook received eventId={} type={}", eventId, type)

        try {
            val processed =
                requireNotNull(
                    transactionTemplate.execute {
                        processInTransaction(eventId, type, root)
                    },
                ) { "Stripe webhook transaction returned no result" }

            if (!processed) {
                metrics()?.webhookDuplicate()
                log.info("Ignoring already processed Stripe event {}", eventId)
            } else {
                metrics()?.webhookProcessed()
                log.info("Stripe webhook processed eventId={} type={}", eventId, type)
            }
        } catch (ex: Exception) {
            metrics()?.webhookFailed()
            persistFailure(eventId, type, ex)
            log.error("Failed processing Stripe event {}: {}", eventId, ex.message)
            throw ex
        }
    }

    private fun processInTransaction(
        eventId: String,
        type: String,
        root: JsonNode,
    ): Boolean {
        val claimed = eventRepository.tryClaim(eventId, type) == 1
        val event =
            eventRepository.findByEventIdForUpdate(eventId)
                ?: throw IllegalStateException("Stripe event row is missing after claim")

        if (!claimed && event.status == StripeWebhookEvent.STATUS_PROCESSED) {
            return false
        }

        when (type) {
            "payment_intent.succeeded" -> {
                val result = onPaymentIntentSucceeded(root)
                event.markProcessed(result.providerReference, result.orderId)
                if (result.orderId != null) {
                    metrics()?.orderPaidFromProvider()
                }
            }
            else -> {
                log.debug("Ignoring Stripe event type={}", type)
                event.markProcessed(providerReference = null, orderId = null)
            }
        }

        eventRepository.save(event)
        return true
    }

    private fun persistFailure(
        eventId: String,
        type: String,
        cause: Exception,
    ) {
        runCatching {
            transactionTemplate.execute {
                val event =
                    eventRepository.findById(eventId).orElseGet {
                        StripeWebhookEvent(
                            eventId = eventId,
                            eventType = type,
                        )
                    }
                event.markFailed(cause.message ?: cause.javaClass.simpleName)
                eventRepository.save(event)
            }
        }.onFailure {
            log.error("Failed to persist Stripe event failure state {}", eventId, it)
        }
    }

    private fun validateSignature(
        payload: String,
        signatureHeader: String?,
    ) {
        val secret = props.webhookSecret
        if (secret.isBlank()) {
            throw BadRequestException("Stripe webhook secret is not configured")
        }
        if (
            signatureHeader.isNullOrBlank() ||
            !StripeWebhookVerifier.verify(
                payload = payload,
                signatureHeader = signatureHeader,
                secret = secret,
                toleranceSeconds = props.webhookToleranceSeconds,
            )
        ) {
            throw UnauthorizedException("Invalid Stripe webhook signature")
        }
    }

    private fun parsePayload(payload: String): JsonNode =
        try {
            objectMapper.readTree(payload)
        } catch (ex: Exception) {
            log.warn("Unparseable Stripe webhook payload: {}", ex.message)
            throw BadRequestException("Invalid Stripe webhook payload")
        }

    private fun onPaymentIntentSucceeded(root: JsonNode): ProcessResult {
        val paymentIntent = root.path("data").path("object")
        val intentId =
            paymentIntent.path("id").asString(null)?.trim()
                ?: throw BadRequestException("Stripe PaymentIntent id is missing")

        val orderIdString =
            paymentIntent.path("metadata").path("order_id").asString(null)?.trim()
                ?: throw BadRequestException("Stripe PaymentIntent metadata.order_id is missing")

        val orderId =
            try {
                UUID.fromString(orderIdString)
            } catch (_: IllegalArgumentException) {
                throw BadRequestException("Stripe PaymentIntent metadata.order_id is invalid")
            }

        val amountNode = paymentIntent.get("amount")
        if (amountNode == null || !amountNode.isNumber) {
            throw BadRequestException("Stripe PaymentIntent amount is missing")
        }
        val amountMinor = amountNode.asLong()
        if (amountMinor <= 0) {
            throw BadRequestException("Stripe PaymentIntent amount must be positive")
        }

        val currency =
            paymentIntent.path("currency").asString(null)?.trim()?.lowercase()
                ?: throw BadRequestException("Stripe PaymentIntent currency is missing")
        val expectedCurrency = props.defaultCurrency.trim().lowercase()
        if (expectedCurrency.isNotEmpty() && currency != expectedCurrency) {
            throw BadRequestException(
                "Stripe PaymentIntent currency mismatch: expected $expectedCurrency, received $currency",
            )
        }

        orderService.markPaidFromProvider(
            orderId = orderId,
            providerId = "stripe",
            providerReference = intentId,
            amountMinor = amountMinor,
            currency = currency,
        )
        return ProcessResult(orderId = orderId, providerReference = intentId)
    }

    private data class ProcessResult(
        val orderId: UUID,
        val providerReference: String,
    )

    private fun metrics(): PaymentMetrics? = paymentMetrics.getIfAvailable()
}
