package com.kenlikdev.qmarket.network

import com.kenlikdev.qmarket.api.QMarketJson
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

/**
 * Shared Ktor plugins for QMarket API.
 * Call from platform `HttpClient(OkHttp/CIO/Darwin) { qMarketConfig(baseUrl, tokens) }`.
 *
 * @param baseUrl e.g. `http://10.0.2.2:8080` (Android emulator) or `http://localhost:8080`
 */
fun HttpClientConfig<*>.qMarketConfig(
    baseUrl: String,
    tokenProvider: TokenProvider? = null,
) {
    val normalized = baseUrl.trimEnd('/')
    expectSuccess = false
    install(ContentNegotiation) {
        json(QMarketJson.format)
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
    }
    defaultRequest {
        url(normalized)
        contentType(ContentType.Application.Json)
        tokenProvider?.accessToken()?.let { token ->
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}
