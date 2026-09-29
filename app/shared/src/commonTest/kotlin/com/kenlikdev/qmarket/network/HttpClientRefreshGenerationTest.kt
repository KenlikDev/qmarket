package com.kenlikdev.qmarket.network

import com.kenlikdev.qmarket.api.AuthResponseDto
import com.kenlikdev.qmarket.api.UserDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull

class HttpClientRefreshGenerationTest {
    @Test
    fun staleRefreshResultCannotResurrectClearedSession() =
        runTest {
            val refreshStarted = CompletableDeferred<Unit>()
            val releaseRefresh = CompletableDeferred<Unit>()
            val engine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/api/v1/protected" ->
                            respond(
                                content = ByteReadChannel(""),
                                status = HttpStatusCode.Unauthorized,
                            )

                        "/api/v1/auth/refresh" -> {
                            refreshStarted.complete(Unit)
                            releaseRefresh.await()
                            respond(
                                content =
                                    ByteReadChannel(
                                        """
                                        {
                                          "accessToken":"stale-access",
                                          "refreshToken":"stale-refresh",
                                          "expiresIn":900,
                                          "user":{"id":"1","email":"old@qmarket.local","roles":["ROLE_USER"]}
                                        }
                                        """.trimIndent(),
                                    ),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        else ->
                            respond(
                                content = ByteReadChannel("""{"unexpected":true}"""),
                                status = HttpStatusCode.NotFound,
                            )
                    }
                }

            val store = InMemorySessionStore()
            val tokens = MutableTokenProvider(store)
            tokens.applyAuth(
                AuthResponseDto(
                    accessToken = "old-access",
                    refreshToken = "old-refresh",
                    expiresIn = 900,
                    user = UserDto(id = "1", email = "old@qmarket.local"),
                ),
            )
            val client =
                HttpClient(engine) {
                    qMarketConfig("http://test.local", tokens)
                }

            try {
                val request = async { client.get("/api/v1/protected") }
                refreshStarted.await()
                tokens.clear()
                releaseRefresh.complete(Unit)

                request.await()
                assertNull(tokens.accessToken())
                assertNull(tokens.refreshToken())
                assertNull(store.readAccessToken())
                assertNull(store.readRefreshToken())
            } finally {
                client.close()
            }
        }
}
