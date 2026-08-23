package com.kenlikdev.qmarket.network

import com.kenlikdev.qmarket.api.AuthResponseDto
import com.kenlikdev.qmarket.api.UserDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MutableTokenProviderTest {
    @Test
    fun applyAuthStoresBothTokens() {
        val provider = MutableTokenProvider()
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
        provider.clear()
        assertNull(provider.accessToken())
        assertNull(provider.refreshToken())
    }
}
