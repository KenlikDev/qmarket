package com.kenlikdev.qmarket.order.payment

import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.order.domain.StripeWebhookEvent
import com.kenlikdev.qmarket.order.dto.OrderResponse
import com.kenlikdev.qmarket.order.repository.StripeWebhookEventRepository
import com.kenlikdev.qmarket.order.service.OrderService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class StripeWebhookServiceTest {
    private val props =
        StripeProperties(
            secretKey = "sk_test",
            webhookSecret = "whsec_test",
            webhookToleranceSeconds = 300,
        )
    private lateinit var orderService: OrderService
    private lateinit var eventRepository: StripeWebhookEventRepository
    private lateinit var service: StripeWebhookService

    @BeforeEach
    fun setUp() {
        orderService = mockk()
        eventRepository = mockk(relaxed = true)
        service = StripeWebhookService(props, orderService, eventRepository)
        // Default: claim succeeds, find returns the claimed row
        every { eventRepository.tryClaim(any(), any()) } returns 1
        every { eventRepository.findById(any()) } answers {
            Optional.of(
                StripeWebhookEvent(
                    eventId = firstArg(),
                    eventType = "payment_intent.succeeded",
                    status = StripeWebhookEvent.STATUS_RECEIVED,
                ),
            )
        }
        every { eventRepository.save(any()) } answers { firstArg() }
    }

    private fun signedPayload(
        payload: String,
        ts: Long = System.currentTimeMillis() / 1000,
    ): String {
        val v1 = StripeWebhookVerifier.hmacSha256Hex(props.webhookSecret, "$ts.$payload")
        return "t=$ts,v1=$v1"
    }

    @Test
    fun `invalid signature throws`() {
        assertThrows(UnauthorizedException::class.java) {
            service.handle("""{"id":"evt_x","type":"payment_intent.succeeded"}""", "t=1,v1=deadbeef")
        }
    }

    @Test
    fun `payment_intent succeeded marks order paid`() {
        val orderId = UUID.randomUUID()
        val payload =
            """
            {
              "id": "evt_${orderId.toString().take(8)}",
              "type": "payment_intent.succeeded",
              "data": {
                "object": {
                  "id": "pi_abc",
                  "metadata": {
                    "order_id": "$orderId",
                    "user_id": "${UUID.randomUUID()}"
                  }
                }
              }
            }
            """.trimIndent()
        every {
            orderService.markPaidFromProvider(orderId, "stripe", "pi_abc")
        } returns mockk<OrderResponse>(relaxed = true)

        service.handle(payload, signedPayload(payload))

        verify(exactly = 1) {
            orderService.markPaidFromProvider(orderId, "stripe", "pi_abc")
        }
        verify { eventRepository.tryClaim(match { it.startsWith("evt_") }, "payment_intent.succeeded") }
        verify {
            eventRepository.save(
                match {
                    it.status == StripeWebhookEvent.STATUS_PROCESSED &&
                        it.orderId == orderId &&
                        it.providerReference == "pi_abc"
                },
            )
        }
    }

    @Test
    fun `duplicate PROCESSED event is ignored`() {
        val orderId = UUID.randomUUID()
        val payload =
            """
            {
              "id": "evt_dup_once",
              "type": "payment_intent.succeeded",
              "data": {
                "object": {
                  "id": "pi_dup",
                  "metadata": { "order_id": "$orderId" }
                }
              }
            }
            """.trimIndent()
        every { eventRepository.tryClaim("evt_dup_once", any()) } returns 0
        every { eventRepository.findById("evt_dup_once") } returns
            Optional.of(
                StripeWebhookEvent(
                    eventId = "evt_dup_once",
                    eventType = "payment_intent.succeeded",
                    status = StripeWebhookEvent.STATUS_PROCESSED,
                ),
            )

        service.handle(payload, signedPayload(payload))

        verify(exactly = 0) {
            orderService.markPaidFromProvider(any(), any(), any())
        }
    }

    @Test
    fun `FAILED event is retried`() {
        val orderId = UUID.randomUUID()
        val payload =
            """
            {
              "id": "evt_retry",
              "type": "payment_intent.succeeded",
              "data": {
                "object": {
                  "id": "pi_retry",
                  "metadata": { "order_id": "$orderId" }
                }
              }
            }
            """.trimIndent()
        every { eventRepository.tryClaim("evt_retry", any()) } returns 0
        every { eventRepository.findById("evt_retry") } returns
            Optional.of(
                StripeWebhookEvent(
                    eventId = "evt_retry",
                    eventType = "payment_intent.succeeded",
                    status = StripeWebhookEvent.STATUS_FAILED,
                    errorMessage = "previous failure",
                ),
            )
        every {
            orderService.markPaidFromProvider(orderId, "stripe", "pi_retry")
        } returns mockk(relaxed = true)

        service.handle(payload, signedPayload(payload))

        verify(exactly = 1) {
            orderService.markPaidFromProvider(orderId, "stripe", "pi_retry")
        }
        verify {
            eventRepository.save(match { it.status == StripeWebhookEvent.STATUS_PROCESSED })
        }
    }
}
