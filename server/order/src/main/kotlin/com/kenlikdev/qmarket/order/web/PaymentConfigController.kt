package com.kenlikdev.qmarket.order.web

import com.kenlikdev.qmarket.order.dto.PaymentProviderConfigResponse
import com.kenlikdev.qmarket.order.payment.PaymentGateway
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Exposes only the active payment-provider identifier; provider secrets are never returned.
 * The client uses this value to avoid showing actions that cannot work with the active gateway.
 */
@RestController
@RequestMapping("/api/v1/payments")
class PaymentConfigController(
    private val paymentGateway: PaymentGateway,
) {
    @GetMapping("/config")
    fun getConfig(): PaymentProviderConfigResponse =
        PaymentProviderConfigResponse(providerId = paymentGateway.providerId)
}
