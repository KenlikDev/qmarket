package com.kenlikdev.qmarket.common.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class JwtServiceTest {

    private lateinit var jwtService: JwtService
    private val props = JwtProperties(
        secret = "test-secret-key-that-is-long-enough-for-hs256-algorithm-12345",
        accessTokenExpirationMs = 3_600_000,
        refreshTokenExpirationMs = 86_400_000
    )

    @BeforeEach
    fun setUp() {
        jwtService = JwtService(props)
    }

    @Test
    fun `generate and parse access token`() {
        val userId = UUID.randomUUID()
        val token = jwtService.generateAccessToken(userId, "user@test.com", listOf("ROLE_USER", "ROLE_ADMIN"))

        assertTrue(token.isNotBlank())

        val claims = jwtService.parseClaims(token)
        assertTrue(jwtService.isAccessToken(claims))
        assertFalse(jwtService.isRefreshToken(claims))
        assertEquals(userId, jwtService.getUserId(claims))
        assertEquals("user@test.com", claims["email"])
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf("ROLE_USER", "ROLE_ADMIN"), claims["roles"] as List<*>)
    }

    @Test
    fun `generate and parse refresh token`() {
        val userId = UUID.randomUUID()
        val token = jwtService.generateRefreshToken(userId)

        val claims = jwtService.parseClaims(token)
        assertTrue(jwtService.isRefreshToken(claims))
        assertFalse(jwtService.isAccessToken(claims))
        assertEquals(userId, jwtService.getUserId(claims))
    }

    @Test
    fun `invalid token throws exception`() {
        assertThrows(Exception::class.java) {
            jwtService.parseClaims("not.a.valid.token")
        }
    }
}
