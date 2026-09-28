package com.kenlikdev.qmarket.network

import com.kenlikdev.qmarket.api.QMarketJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QMarketApiClientAdminCatalogTest {
    @Test
    fun listAdminProductsUsesAdminEndpoint() =
        runTest {
            var requestedPath = ""
            val engine =
                MockEngine { request ->
                    requestedPath = request.url.fullPath
                    respond(
                        content =
                            ByteReadChannel(
                                """{"content":[],"page":0,"size":50,"totalElements":0,"totalPages":0}""",
                            ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client =
                HttpClient(engine) {
                    expectSuccess = false
                    install(ContentNegotiation) {
                        json(QMarketJson.format)
                    }
                }

            try {
                val page = QMarketApiClient(client).listAdminProducts(size = 50)
                assertEquals("/api/v1/products/admin?page=0&size=50", requestedPath)
                assertTrue(page.content.isEmpty())
            } finally {
                client.close()
            }
        }

    @Test
    fun listAdminCategoriesUsesAdminEndpoint() =
        runTest {
            var requestedPath = ""
            val engine =
                MockEngine { request ->
                    requestedPath = request.url.fullPath
                    respond(
                        content = ByteReadChannel("[]"),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client =
                HttpClient(engine) {
                    expectSuccess = false
                    install(ContentNegotiation) {
                        json(QMarketJson.format)
                    }
                }

            try {
                val categories = QMarketApiClient(client).listAdminCategories()
                assertEquals("/api/v1/categories/admin?activeOnly=false", requestedPath)
                assertTrue(categories.isEmpty())
            } finally {
                client.close()
            }
        }
}
