package com.kenlikdev.qmarket.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

fun createPlatformHttpClient(
    baseUrl: String,
    tokenProvider: TokenProvider? = null,
): HttpClient =
    HttpClient(Js) {
        qMarketConfig(baseUrl, tokenProvider)
    }
