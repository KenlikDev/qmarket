package com.kenlikdev.qmarket.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createPlatformHttpClient(
    baseUrl: String,
    tokenProvider: TokenProvider?,
): HttpClient =
    HttpClient(OkHttp) {
        qMarketConfig(baseUrl, tokenProvider)
    }
