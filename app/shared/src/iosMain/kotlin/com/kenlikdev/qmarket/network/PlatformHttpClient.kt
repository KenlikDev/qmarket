package com.kenlikdev.qmarket.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

fun createPlatformHttpClient(
    baseUrl: String,
    tokenProvider: TokenProvider? = null,
): HttpClient =
    HttpClient(Darwin) {
        qMarketConfig(baseUrl, tokenProvider)
    }
