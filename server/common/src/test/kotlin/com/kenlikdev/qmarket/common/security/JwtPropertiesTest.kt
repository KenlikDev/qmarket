package com.kenlikdev.qmarket.common.security

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class JwtPropertiesTest {
    @Test
    fun `valid secret passes validation`() {
        val props =
            JwtProperties(
                secret = "a-sufficiently-long-unique-secret-key-for-tests-12345",
            )
        assertDoesNotThrow { props.validate() }
    }

    @Test
    fun `empty secret is rejected`() {
        val props = JwtProperties(secret = "   ")
        val ex = assertThrows(IllegalArgumentException::class.java) { props.validate() }
        assert(ex.message!!.contains("required"))
    }

    @Test
    fun `short secret is rejected`() {
        val props = JwtProperties(secret = "too-short")
        val ex = assertThrows(IllegalArgumentException::class.java) { props.validate() }
        assert(ex.message!!.contains("at least 32"))
    }

    @Test
    fun `known placeholder secret is rejected`() {
        val props =
            JwtProperties(
                secret = "change-me-to-a-very-long-and-secure-secret-key-at-least-256-bits",
            )
        val ex = assertThrows(IllegalArgumentException::class.java) { props.validate() }
        assert(ex.message!!.contains("placeholder") || ex.message!!.contains("JWT_SECRET"))
    }

    @Test
    fun `dev placeholder secret is rejected`() {
        val props =
            JwtProperties(
                secret = "dev-only-qmarket-jwt-secret-key-32chars-min",
            )
        assertThrows(IllegalArgumentException::class.java) { props.validate() }
    }
}
