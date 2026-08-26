package com.kenlikdev.qmarket.network

import com.kenlikdev.qmarket.api.CreateProductRequestDto
import com.kenlikdev.qmarket.api.LoginRequestDto
import com.kenlikdev.qmarket.api.QMarketJson
import com.kenlikdev.qmarket.api.RefreshTokenRequestDto
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

    @Test
    fun listProductsParsesPageEnvelope() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content =
                            ByteReadChannel(
                                """
                                {
                                  "content": [
                                    {
                                      "id": "11111111-1111-1111-1111-111111111111",
                                      "name": "Phone",
                                      "slug": "phone",
                                      "price": "499.00",
                                      "stockQuantity": 5,
                                      "active": true,
                                      "featured": false
                                    }
                                  ],
                                  "page": 0,
                                  "size": 20,
                                  "totalElements": 1,
                                  "totalPages": 1
                                }
                                """.trimIndent(),
                            ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = clientWith(engine)
            try {
                val page = QMarketApiClient(client).listProducts(size = 20)
                assertEquals(1, page.content.size)
                assertEquals("Phone", page.content[0].name)
                assertEquals(1, page.totalElements)
            } finally {
                client.close()
            }
        }

    @Test
    fun listOrdersParsesPageEnvelope() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content =
                            ByteReadChannel(
                                """
                                {
                                  "content": [
                                    {
                                      "id": "22222222-2222-2222-2222-222222222222",
                                      "userId": "11111111-1111-1111-1111-111111111111",
                                      "status": "PENDING",
                                      "totalAmount": "10.00",
                                      "items": []
                                    }
                                  ],
                                  "page": 0,
                                  "size": 20,
                                  "totalElements": 1,
                                  "totalPages": 1
                                }
                                """.trimIndent(),
                            ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = clientWith(engine)
            try {
                val page = QMarketApiClient(client).listMyOrders(size = 20)
                assertEquals(1, page.content.size)
                assertEquals("PENDING", page.content[0].status.name)
            } finally {
                client.close()
            }
        }

    @Test
    fun refreshParsesAuthResponse() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content =
                            ByteReadChannel(
                                """
                                {
                                  "accessToken": "new-access",
                                  "refreshToken": "new-refresh",
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
                val auth =
                    QMarketApiClient(client).refresh(
                        RefreshTokenRequestDto(refreshToken = "old-refresh"),
                    )
                assertEquals("new-access", auth.accessToken)
                assertEquals("new-refresh", auth.refreshToken)
            } finally {
                client.close()
            }
        }

    @Test
    fun createProductParsesResponse() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content =
                            ByteReadChannel(
                                """
                                {
                                  "id": "33333333-3333-3333-3333-333333333333",
                                  "name": "Widget",
                                  "slug": "widget",
                                  "price": "12.50",
                                  "stockQuantity": 5,
                                  "active": true,
                                  "featured": false
                                }
                                """.trimIndent(),
                            ),
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = clientWith(engine)
            try {
                val product =
                    QMarketApiClient(client).createProduct(
                        CreateProductRequestDto(
                            name = "Widget",
                            slug = "widget",
                            price = "12.50",
                            stockQuantity = 5,
                        ),
                    )
                assertEquals("Widget", product.name)
                assertEquals("12.50", product.price)
            } finally {
                client.close()
            }
        }

}
