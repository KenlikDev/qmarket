package com.kenlikdev.qmarket.network

import com.kenlikdev.qmarket.api.AuthResponseDto
import com.kenlikdev.qmarket.api.QMarketJson
import com.kenlikdev.qmarket.api.RefreshTokenRequestDto
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

/**
 * Applies the common QMarket Ktor client configuration.
 *
 * Ktor serializes bearer-token refresh callbacks for a provider, so concurrent
 * requests that receive 401 reuse the result of a single refresh operation.
 * [oldTokens] is used as the refresh input to avoid racing with local storage
 * updates made by another coroutine.
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
    }

    if (tokenProvider != null) {
        install(Auth) {
            bearer {
                nonCancellableRefresh = true

                loadTokens {
                    val access = tokenProvider.accessToken() ?: return@loadTokens null
                    BearerTokens(
                        accessToken = access,
                        refreshToken = tokenProvider.refreshToken().orEmpty(),
                    )
                }

                refreshTokens {
                    val currentRefresh =
                        oldTokens?.refreshToken
                            ?.takeIf(String::isNotBlank)
                            ?: return@refreshTokens null

                    val response =
                        client.post("/api/v1/auth/refresh") {
                            markAsRefreshTokenRequest()
                            contentType(ContentType.Application.Json)
                            setBody(
                                RefreshTokenRequestDto(
                                    refreshToken = currentRefresh,
                                ),
                            )
                        }

                    if (response.status != HttpStatusCode.OK) {
                        if (tokenProvider is MutableTokenProvider) {
                            tokenProvider.clear()
                        }
                        return@refreshTokens null
                    }

                    val auth = response.body<AuthResponseDto>()
                    if (tokenProvider is MutableTokenProvider) {
                        tokenProvider.applyAuth(auth)
                    }

                    BearerTokens(
                        accessToken = auth.accessToken,
                        refreshToken = auth.refreshToken,
                    )
                }

                sendWithoutRequest { request ->
                    request.url.pathSegments.firstOrNull() != "api" ||
                        request.url.pathSegments.getOrNull(1) != "v1" ||
                        request.url.pathSegments.getOrNull(2) != "auth"
                }
            }
        }
    }
}
