package com.kenlikdev.qmarket.network

import io.ktor.client.HttpClient

/**
 * Platform HTTP client (OkHttp / CIO / Darwin / JS) with [qMarketConfig] applied.
 */
expect fun createPlatformHttpClient(
    baseUrl: String,
    tokenProvider: TokenProvider? = null,
): HttpClient
