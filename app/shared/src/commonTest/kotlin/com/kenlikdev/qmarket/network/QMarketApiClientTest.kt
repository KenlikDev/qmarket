package com.kenlikdev.qmarket.network

import com.kenlikdev.qmarket.api.LoginRequestDto
import com.kenlikdev.qmarket.api.QMarketJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QMarketApiClientTest {
    private fun clientWith(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(QMarketJson.format)
            }
        }

    @Test
    fun loginParsesAuthResponse() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content =
                            ByteReadChannel(
                                """
                                {
                                  "accessToken": "tok",
                                  "refreshToken": "ref",
                                  "tokenType": "Bearer",
                                  "expiresIn": 3600,
                                  "user": {
                                    "id": "11111111-1111-1111-1111-111111111111",
                                    "email": "a@b.c",
                                    "roles": ["ROLE_USER"]
                                  }
                                }
                                """.trimIndent(),
                            ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = clientWith(engine)
            try {
                val api = QMarketApiClient(client)
                val auth = api.login(LoginRequestDto(email = "a@b.c", password = "x"))
                assertEquals("tok", auth.accessToken)
                assertEquals("a@b.c", auth.user.email)
            } finally {
                client.close()
            }
        }

    @Test
    fun errorBodyBecomesApiException() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content =
                            ByteReadChannel(
                                """
                                {
                                  "status": 401,
                                  "error": "Unauthorized",
                                  "code": "AUTH_FAILED",
                                  "message": "Bad credentials"
                                }
                                """.trimIndent(),
                            ),
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = clientWith(engine)
            try {
                val api = QMarketApiClient(client)
                val ex =
                    assertFailsWith<ApiException> {
                        api.login(LoginRequestDto(email = "a@b.c", password = "wrong"))
                    }
                assertEquals(401, ex.status)
                assertEquals("Bad credentials", ex.message)
            } finally {
                client.close()
            }
        }
}
