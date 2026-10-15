package com.kenlikdev.qmarket.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

actual fun createPlatformHttpClient(
    baseUrl: String,
    tokenProvider: TokenProvider?,
): HttpClient =
    HttpClient(Js) {
        engine {
            configureRequest {
                credentials = "include"
            }
        }
        qMarketConfig(baseUrl, tokenProvider, browserCookieAuth = true)
    }
