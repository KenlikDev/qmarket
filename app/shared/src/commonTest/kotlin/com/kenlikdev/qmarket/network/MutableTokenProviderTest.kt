package com.kenlikdev.qmarket.network

import com.kenlikdev.qmarket.api.AuthResponseDto
import com.kenlikdev.qmarket.api.UserDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class MutableTokenProviderTest {
    @Test
    fun applyAuthStoresBothTokensAndEmail() {
        val store = InMemorySessionStore()
        val provider = MutableTokenProvider(store)
        provider.applyAuth(
            AuthResponseDto(
                accessToken = "a1",
                refreshToken = "r1",
                expiresIn = 60,
                user = UserDto(id = "1", email = "a@b.c"),
            ),
        )
        assertEquals("a1", provider.accessToken())
        assertEquals("r1", provider.refreshToken())
        assertEquals("a@b.c", provider.sessionEmail())
        assertTrue(provider.hasSession())
        assertEquals("a1", store.readAccessToken())
        assertEquals("r1", store.readRefreshToken())
        assertEquals("a@b.c", store.readEmail())
        provider.clear()
        assertNull(provider.accessToken())
        assertNull(provider.refreshToken())
        assertNull(provider.sessionEmail())
        assertFalse(provider.hasSession())
        assertNull(store.readAccessToken())
    }

    @Test
    fun clearAlwaysClearsMemoryWhenStoreClearFails() {
        val store =
            object : SessionStore {
                override fun readAccessToken(): String? = "access"
                override fun readRefreshToken(): String? = "refresh"
                override fun readEmail(): String? = "user@example.com"

                override fun write(
                    accessToken: String,
                    refreshToken: String,
                    email: String?,
                ) = Unit

                override fun clear(): Unit = error("persistent storage unavailable")
            }
        val provider = MutableTokenProvider(store)

        assertFailsWith<IllegalStateException> {
            provider.clear()
        }
        assertNull(provider.accessToken())
        assertNull(provider.refreshToken())
        assertNull(provider.sessionEmail())
        assertFalse(provider.hasSession())
    }

    @Test
    fun loadsExistingSessionFromStore() {
        val store = InMemorySessionStore()
        store.write("access", "refresh", "u@qmarket.local")
        val provider = MutableTokenProvider(store)
        assertEquals("access", provider.accessToken())
        assertEquals("refresh", provider.refreshToken())
        assertEquals("u@qmarket.local", provider.sessionEmail())
        assertTrue(provider.hasSession())
    }

    @Test
    fun isAdminDoesNotTrustArbitraryAdminSuffix() {
        val provider = MutableTokenProvider(InMemorySessionStore())
        provider.applyRoles(listOf("NOT_ADMIN"))
        assertFalse(provider.isAdmin())
    }

    @Test
    fun isAdminFromRoles() {
        val provider = MutableTokenProvider(InMemorySessionStore())
        assertFalse(provider.isAdmin())
        provider.applyAuth(
            AuthResponseDto(
                accessToken = "a",
                refreshToken = "r",
                expiresIn = 60,
                user =
                    UserDto(
                        id = "1",
                        email = "admin@qmarket.local",
                        roles = listOf("ROLE_ADMIN"),
                    ),
            ),
        )
        assertTrue(provider.isAdmin())
        provider.applyRoles(listOf("ROLE_USER"))
        assertFalse(provider.isAdmin())
    }
}

class InMemorySessionStoreTest {
    @Test
    fun writeAndClear() {
        val store = InMemorySessionStore()
        store.write("a", "r", "e@x.com")
        assertEquals("a", store.readAccessToken())
        assertEquals("r", store.readRefreshToken())
        assertEquals("e@x.com", store.readEmail())
        store.clear()
        assertNull(store.readAccessToken())
        assertNull(store.readRefreshToken())
        assertNull(store.readEmail())
    }
}
