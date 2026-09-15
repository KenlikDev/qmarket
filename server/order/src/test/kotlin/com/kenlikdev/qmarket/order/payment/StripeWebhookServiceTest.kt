package com.kenlikdev.qmarket.order.payment

import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.order.dto.OrderResponse
import com.kenlikdev.qmarket.order.service.OrderService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class StripeWebhookServiceTest {
    private val props =
        StripeProperties(
            secretKey = "sk_test",
            webhookSecret = "whsec_test",
            webhookToleranceSeconds = 300,
        )
    private val orderService = mockk<OrderService>()
    private val service = StripeWebhookService(props, orderService)

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
    }

    @Test
    fun `duplicate event is ignored`() {
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
        every {
            orderService.markPaidFromProvider(any(), any(), any())
        } returns mockk(relaxed = true)

        val sig = signedPayload(payload)
        service.handle(payload, sig)
        service.handle(payload, sig)

        verify(exactly = 1) {
            orderService.markPaidFromProvider(orderId, "stripe", "pi_dup")
        }
    }
}
