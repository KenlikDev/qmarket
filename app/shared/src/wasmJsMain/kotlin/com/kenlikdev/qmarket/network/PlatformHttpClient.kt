package com.kenlikdev.qmarket.network

import io.ktor.client.HttpClient

// wasm: use default engine if available; fallback to CIO-less default client
actual fun createPlatformHttpClient(
    baseUrl: String,
    tokenProvider: TokenProvider?,
): HttpClient =
    HttpClient {
        qMarketConfig(baseUrl, tokenProvider)
    }
