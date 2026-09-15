package com.kenlikdev.qmarket.order.payment

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(StripeProperties::class)
class PaymentConfiguration
