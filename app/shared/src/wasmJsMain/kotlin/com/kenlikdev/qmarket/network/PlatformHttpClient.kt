package com.kenlikdev.qmarket.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

// Wasm runs in the browser, so use Ktor's Fetch-based Js engine explicitly.
actual fun createPlatformHttpClient(
    baseUrl: String,
    tokenProvider: TokenProvider?,
): HttpClient =
    HttpClient(Js) {
        qMarketConfig(baseUrl, tokenProvider)
    }
