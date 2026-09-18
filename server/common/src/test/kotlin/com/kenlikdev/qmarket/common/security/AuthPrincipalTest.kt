package com.kenlikdev.qmarket.common.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import java.util.UUID

class AuthPrincipalTest {
    @Test
    fun `userId returns UUID principal`() {
        val id = UUID.randomUUID()
        val auth = UsernamePasswordAuthenticationToken(id, null)
        assertEquals(id, auth.userId())
    }

    @Test
    fun `userId rejects non-UUID principal`() {
        val auth = UsernamePasswordAuthenticationToken("user@example.com", null)
        assertThrows(IllegalStateException::class.java) { auth.userId() }
    }
}
