package com.kenlikdev.qmarket.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

fun createPlatformHttpClient(
    baseUrl: String,
    tokenProvider: TokenProvider? = null,
): HttpClient =
    HttpClient(CIO) {
        qMarketConfig(baseUrl, tokenProvider)
    }
