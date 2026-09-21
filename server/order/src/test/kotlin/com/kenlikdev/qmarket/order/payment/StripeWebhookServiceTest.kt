package com.kenlikdev.qmarket.order.payment

import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.order.domain.StripeWebhookEvent
import com.kenlikdev.qmarket.order.dto.OrderResponse
import com.kenlikdev.qmarket.order.repository.StripeWebhookEventRepository
import com.kenlikdev.qmarket.order.service.OrderService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.Optional
import java.util.UUID

class StripeWebhookServiceTest {
    private val props =
        StripeProperties(
            secretKey = "sk_test",
            webhookSecret = "whsec_test",
            defaultCurrency = "usd",
            webhookToleranceSeconds = 300,
        )
    private val objectMapper = ObjectMapper()
    private lateinit var orderService: OrderService
    private lateinit var eventRepository: StripeWebhookEventRepository
    private lateinit var paymentMetricsProvider: ObjectProvider<PaymentMetrics>
    private lateinit var transactionManager: PlatformTransactionManager
    private lateinit var transactionStatus: TransactionStatus
    private lateinit var service: StripeWebhookService

    @BeforeEach
    fun setUp() {
        orderService = mockk()
        eventRepository = mockk(relaxed = true)
        paymentMetricsProvider = mockk()
        transactionManager = mockk()
        transactionStatus = mockk(relaxed = true)

        every { paymentMetricsProvider.getIfAvailable() } returns null
        every { transactionManager.getTransaction(any()) } returns transactionStatus
        every { transactionManager.commit(transactionStatus) } just runs
        every { transactionManager.rollback(transactionStatus) } just runs

        service =
            StripeWebhookService(
                props = props,
                orderService = orderService,
                eventRepository = eventRepository,
                objectMapper = objectMapper,
                paymentMetrics = paymentMetricsProvider,
                transactionManager = transactionManager,
            )
    }

    private fun signedPayload(
        payload: String,
        timestamp: Long = Instant.now().epochSecond,
    ): String {
        val signature =
            StripeWebhookVerifier.hmacSha256Hex(
                props.webhookSecret,
                "$timestamp.$payload",
            )
        return "t=$timestamp,v1=$signature"
    }

    private fun payload(
        eventId: String,
        orderId: UUID,
        amount: Long = 2599,
        currency: String = "usd",
    ): String =
        """
        {
          "id": "$eventId",
          "type": "payment_intent.succeeded",
          "data": {
            "object": {
              "id": "pi_$eventId",
              "amount": $amount,
              "currency": "$currency",
              "status": "succeeded",
              "metadata": {
                "order_id": "$orderId"
              }
            }
          }
        }
        """.trimIndent()

    @Test
    fun invalidSignatureThrowsBeforeStartingTransaction() {
        assertThrows(UnauthorizedException::class.java) {
            service.handle(
                payload = """{"id":"evt_x","type":"payment_intent.succeeded"}""",
                signatureHeader = "t=1,v1=deadbeef",
            )
        }
        verify(exactly = 0) { transactionManager.getTransaction(any()) }
    }

    @Test
    fun successfulEventIsProcessedAndMarkedProcessedInTransaction() {
        val orderId = UUID.randomUUID()
        val event =
            StripeWebhookEvent(
                eventId = "evt_success",
                eventType = "payment_intent.succeeded",
            )
        val response = mockk<OrderResponse>(relaxed = true)

        every { eventRepository.tryClaim("evt_success", "payment_intent.succeeded") } returns 1
        every { eventRepository.findByEventIdForUpdate("evt_success") } returns event
        every {
            orderService.markPaidFromProvider(
                orderId,
                "stripe",
                "pi_evt_success",
                2599L,
                "usd",
            )
        } returns response
        every { eventRepository.save(any()) } answers { firstArg() }

        val body = payload("evt_success", orderId)
        service.handle(body, signedPayload(body))

        assertEquals(StripeWebhookEvent.STATUS_PROCESSED, event.status)
        assertEquals(orderId, event.orderId)
        assertEquals("pi_evt_success", event.providerReference)
        verify(exactly = 1) {
            orderService.markPaidFromProvider(orderId, "stripe", "pi_evt_success", 2599L, "usd")
        }
        verify(exactly = 1) { transactionManager.commit(transactionStatus) }
    }

    @Test
    fun alreadyProcessedEventIsIgnored() {
        val orderId = UUID.randomUUID()
        val event =
            StripeWebhookEvent(
                eventId = "evt_duplicate",
                eventType = "payment_intent.succeeded",
                status = StripeWebhookEvent.STATUS_PROCESSED,
            )

        every { eventRepository.tryClaim("evt_duplicate", "payment_intent.succeeded") } returns 0
        every { eventRepository.findByEventIdForUpdate("evt_duplicate") } returns event

        val body = payload("evt_duplicate", orderId)
        service.handle(body, signedPayload(body))

        verify(exactly = 0) {
            orderService.markPaidFromProvider(any(), any(), any(), any(), any())
        }
        verify(exactly = 0) { eventRepository.save(any()) }
    }

    @Test
    fun failedEventIsRetried() {
        val orderId = UUID.randomUUID()
        val event =
            StripeWebhookEvent(
                eventId = "evt_retry",
                eventType = "payment_intent.succeeded",
                status = StripeWebhookEvent.STATUS_FAILED,
            )

        every { eventRepository.tryClaim("evt_retry", "payment_intent.succeeded") } returns 0
        every { eventRepository.findByEventIdForUpdate("evt_retry") } returns event
        every {
            orderService.markPaidFromProvider(
                orderId,
                "stripe",
                "pi_evt_retry",
                2599L,
                "usd",
            )
        } returns mockk(relaxed = true)
        every { eventRepository.save(any()) } answers { firstArg() }

        val body = payload("evt_retry", orderId)
        service.handle(body, signedPayload(body))

        assertEquals(StripeWebhookEvent.STATUS_PROCESSED, event.status)
        verify(exactly = 1) {
            orderService.markPaidFromProvider(orderId, "stripe", "pi_evt_retry", 2599L, "usd")
        }
    }

    @Test
    fun processingFailureIsPersistedAsFailedAfterRollback() {
        val orderId = UUID.randomUUID()
        val event =
            StripeWebhookEvent(
                eventId = "evt_failed",
                eventType = "payment_intent.succeeded",
            )
        every { eventRepository.tryClaim("evt_failed", "payment_intent.succeeded") } returns 1
        every { eventRepository.findByEventIdForUpdate("evt_failed") } returns event
        every {
            orderService.markPaidFromProvider(
                orderId,
                "stripe",
                "pi_evt_failed",
                2599L,
                "usd",
            )
        } throws BadRequestException("order conflict")
        every { eventRepository.findById("evt_failed") } returns Optional.empty()
        every { eventRepository.save(any()) } answers { firstArg() }

        val body = payload("evt_failed", orderId)
        assertThrows(BadRequestException::class.java) {
            service.handle(body, signedPayload(body))
        }

        verify(exactly = 1) {
            eventRepository.save(
                match {
                    it.eventId == "evt_failed" &&
                        it.status == StripeWebhookEvent.STATUS_FAILED &&
                        it.errorMessage == "order conflict"
                },
            )
        }
        verify(exactly = 1) { transactionManager.rollback(transactionStatus) }
        verify(atLeast = 1) { transactionManager.commit(transactionStatus) }
    }
}
