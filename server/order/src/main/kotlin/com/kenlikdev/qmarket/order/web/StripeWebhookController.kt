package com.kenlikdev.qmarket.order.web

import com.kenlikdev.qmarket.order.payment.StripeWebhookService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments/stripe")
@ConditionalOnProperty(name = ["qmarket.payment.provider"], havingValue = "stripe")
class StripeWebhookController(
    private val stripeWebhookService: StripeWebhookService,
) {
    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun webhook(
        @RequestBody payload: String,
        @RequestHeader(name = "Stripe-Signature", required = false) signature: String?,
    ) {
        stripeWebhookService.handle(payload, signature)
    }
}
