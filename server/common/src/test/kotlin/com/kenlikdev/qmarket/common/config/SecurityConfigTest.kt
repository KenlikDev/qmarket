package com.kenlikdev.qmarket.common.config

import com.kenlikdev.qmarket.common.security.JwtAuthenticationFilter
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.ObjectMapper

class SecurityConfigTest {
    private val jwtFilter = mockk<JwtAuthenticationFilter>(relaxed = true)
    private val objectMapper = mockk<ObjectMapper>(relaxed = true)

    @Test
    fun `passwordEncoder is BCrypt`() {
        val config = SecurityConfig(jwtFilter, objectMapper, "http://localhost:*", true)
        val encoder = config.passwordEncoder()
        assertTrue(encoder is BCryptPasswordEncoder)
        val hash = encoder.encode("secret")
        assertTrue(encoder.matches("secret", hash))
    }

    @Test
    fun `corsConfigurationSource parses comma-separated origin patterns`() {
        val config =
            SecurityConfig(
                jwtFilter,
                objectMapper,
                "http://localhost:*, http://127.0.0.1:*, https://app.example.com",
                apiDocsPublic = true,
            )
        val source = config.corsConfigurationSource()
        assertTrue(source is UrlBasedCorsConfigurationSource)
        val cors =
            requireNotNull(
                source.getCorsConfiguration(MockHttpServletRequest("GET", "/api/v1/products")),
            )
        val origins = requireNotNull(cors.allowedOriginPatterns)
        assertTrue(origins.contains("http://localhost:*"))
        assertTrue(origins.contains("https://app.example.com"))
        val methods = requireNotNull(cors.allowedMethods)
        assertTrue(methods.contains("GET"))
        assertTrue(methods.contains("DELETE"))
        assertEquals(true, cors.allowCredentials)
    }

    @Test
    fun `corsConfigurationSource ignores blank patterns`() {
        val config = SecurityConfig(jwtFilter, objectMapper, "http://localhost:*,  ,", true)
        val source = config.corsConfigurationSource()
        val cors =
            requireNotNull(
                source.getCorsConfiguration(MockHttpServletRequest("GET", "/")),
            )
        assertEquals(listOf("http://localhost:*"), cors.allowedOriginPatterns)
    }
}
