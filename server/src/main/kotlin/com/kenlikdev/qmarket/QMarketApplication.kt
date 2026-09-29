package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.identity.service.RefreshTokenRevocationService
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@Import(RefreshTokenRevocationService::class)
@SpringBootApplication(
    scanBasePackages = [
        "com.kenlikdev.qmarket",
    ],
)
class QMarketApplication

fun main(args: Array<String>) {
    runApplication<QMarketApplication>(*args)
}
