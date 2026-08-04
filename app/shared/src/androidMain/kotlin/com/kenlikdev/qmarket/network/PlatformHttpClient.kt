package com.kenlikdev.qmarket.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

fun createPlatformHttpClient(
    baseUrl: String,
    tokenProvider: TokenProvider? = null,
): HttpClient =
    HttpClient(OkHttp) {
        qMarketConfig(baseUrl, tokenProvider)
    }
