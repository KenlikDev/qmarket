package com.kenlikdev.qmarket

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(
    scanBasePackages = [
        "com.kenlikdev.qmarket",
    ],
)
class QMarketApplication

fun main(args: Array<String>) {
    runApplication<QMarketApplication>(*args)
}
