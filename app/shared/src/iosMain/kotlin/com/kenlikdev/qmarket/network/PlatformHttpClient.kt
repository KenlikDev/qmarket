package com.kenlikdev.qmarket.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun createPlatformHttpClient(
    baseUrl: String,
    tokenProvider: TokenProvider?,
): HttpClient =
    HttpClient(Darwin) {
        qMarketConfig(baseUrl, tokenProvider)
    }
