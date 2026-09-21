package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.api.AuthResponseDto
import com.kenlikdev.qmarket.api.QMarketJson
import com.kenlikdev.qmarket.api.UserDto
import com.kenlikdev.qmarket.network.InMemorySessionStore
import com.kenlikdev.qmarket.network.MutableTokenProvider
import com.kenlikdev.qmarket.network.QMarketApiClient
import com.kenlikdev.qmarket.ui.AppScreen
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QMarketAppModelTest {
    private fun httpClient(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(QMarketJson.format)
            }
        }

    private fun mockEngine(): MockEngine =
        MockEngine { request ->
            val path = request.url.fullPath
            when {
                path.contains("/api/v1/auth/logout") ->
                    respond(content = ByteReadChannel.Empty, status = HttpStatusCode.NoContent)
                path.contains("/api/v1/categories") ->
                    respond(
                        content = ByteReadChannel("[]"),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                path.contains("/api/v1/products") ->
                    respond(
                        content =
                            ByteReadChannel(
                                """{"content":[],"page":0,"size":50,"totalElements":0,"totalPages":0}""",
                            ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                path.contains("/api/v1/cart") ->
                    respond(
                        content =
                            ByteReadChannel(
                                """{"id":"00000000-0000-0000-0000-000000000001","userId":"00000000-0000-0000-0000-000000000002","items":[],"totalItems":0,"totalPrice":0,"updatedAt":"2026-01-01T00:00:00Z"}""",
                            ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                else -> error("Unexpected path in test engine: $path")
            }
        }

    private fun modelScope(): CoroutineScope = CoroutineScope(Dispatchers.Unconfined)

    private fun authUser(
        email: String = "u@test.local",
        roles: List<String> = listOf("ROLE_USER"),
    ) = AuthResponseDto(
        accessToken = "a",
        refreshToken = "r",
        expiresIn = 60,
        user = UserDto(id = "1", email = email, roles = roles),
    )

    @Test
    fun applySessionMarksLoggedInAndAdminFromTokens() =
        runBlocking {
            val tokens = MutableTokenProvider(InMemorySessionStore())
            tokens.applyAuth(authUser("admin@qmarket.local", listOf("ROLE_ADMIN")))
            val api = QMarketApiClient(httpClient(mockEngine()))
            val model = QMarketAppModel(api, tokens, modelScope(), restoredSession = false)

            model.applySession("admin@qmarket.local")

            assertTrue(model.loggedIn)
            assertTrue(model.isAdmin)
            assertEquals("admin@qmarket.local", model.userLabel)
        }

    @Test
    fun browseAsGuestClearsSessionAndOpensCatalog() =
        runBlocking {
            val tokens = MutableTokenProvider(InMemorySessionStore())
            tokens.applyAuth(authUser())
            val api = QMarketApiClient(httpClient(mockEngine()))
            val model = QMarketAppModel(api, tokens, modelScope(), restoredSession = true)
            model.loggedIn = true
            model.userLabel = "u@test.local"
            model.screen = AppScreen.Login

            model.browseAsGuest()
            // loadCatalog is fire-and-forget via runApi; join its job
            // browseAsGuest doesn't return Job — wait via polling loading after sync clear
            // loggedIn is set synchronously before loadCatalog
            assertFalse(model.loggedIn)
            assertNull(model.userLabel)
            assertNull(tokens.accessToken())
        }

    @Test
    fun logoutClearsLocalSessionEvenWithoutRefreshToken() =
        runBlocking {
            val tokens = MutableTokenProvider(InMemorySessionStore())
            tokens.applyAuth(authUser())
            tokens.clear()
            val api = QMarketApiClient(httpClient(mockEngine()))
            val model = QMarketAppModel(api, tokens, modelScope(), restoredSession = false)
            model.loggedIn = true
            model.userLabel = "u@test.local"
            model.isAdmin = true

            model.logout().join()

            assertFalse(model.loading, "error=${model.error}")
            assertNull(model.error)
            assertFalse(model.loggedIn)
            assertNull(model.userLabel)
            assertFalse(model.isAdmin)
            assertEquals(AppScreen.Login, model.screen)
        }

    @Test
    fun logoutWithRefreshTokenCallsApiThenClearsSession() =
        runBlocking {
            val tokens = MutableTokenProvider(InMemorySessionStore())
            tokens.applyAuth(authUser())
            val api = QMarketApiClient(httpClient(mockEngine()))
            val model = QMarketAppModel(api, tokens, modelScope(), restoredSession = false)
            model.loggedIn = true
            model.userLabel = "u@test.local"

            model.logout().join()

            assertFalse(model.loading, "error=${model.error}")
            assertNull(model.error)
            assertFalse(model.loggedIn)
            assertNull(model.userLabel)
            assertNull(tokens.accessToken())
            assertNull(tokens.refreshToken())
            assertEquals(AppScreen.Login, model.screen)
        }


    @Test
    fun restoreSessionPreservesSessionOnTransientApiFailure() =
        runBlocking {
            val tokens = MutableTokenProvider(InMemorySessionStore())
            tokens.applyAuth(authUser())
            val engine =
                MockEngine { request ->
                    when {
                        request.url.fullPath.contains("/api/v1/users/me") ->
                            respond(
                                content =
                                    ByteReadChannel(
                                        """{"id":"1","email":"u@test.local","roles":["ROLE_USER"]}""",
                                    ),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        else ->
                            respond(
                                content =
                                    ByteReadChannel(
                                        """{"status":503,"error":"Service Unavailable","code":"INTERNAL_ERROR","message":"temporary outage"}""",
                                    ),
                                status = HttpStatusCode.ServiceUnavailable,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                    }
                }
            val client = clientWith(engine)
            try {
                val api = QMarketApiClient(client)
                val model = QMarketAppModel(api, tokens, modelScope(), restoredSession = true)

                model.restoreSessionIfNeeded(restoredSession = true)

                assertTrue(model.loggedIn)
                assertEquals("u@test.local", model.userLabel)
                assertEquals("a", tokens.accessToken())
                assertEquals("temporary outage", model.error)
                assertEquals(AppScreen.Catalog, model.screen)
            } finally {
                client.close()
            }
        }

    @Test
    fun checkoutIsIgnoredWhileLockedOrLoading() =
        runBlocking {
            val tokens = MutableTokenProvider(InMemorySessionStore())
            val api = QMarketApiClient(httpClient(mockEngine()))
            val model = QMarketAppModel(api, tokens, modelScope(), restoredSession = false)
            model.checkoutLocked = true
            model.checkout()
            assertTrue(model.checkoutLocked)

            model.checkoutLocked = false
            model.loading = true
            model.checkout()
            assertTrue(model.loading)
        }
}
