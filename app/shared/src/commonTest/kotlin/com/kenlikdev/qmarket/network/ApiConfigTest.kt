package com.kenlikdev.qmarket.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApiConfigTest {
    @Test
    fun acceptsLocalDevelopmentHttpEndpoints() {
        assertEquals("http://localhost:8080", validateApiBaseUrl(" http://localhost:8080/ "))
        assertEquals("http://10.0.2.2:8080", validateApiBaseUrl("http://10.0.2.2:8080"))
    }

    @Test
    fun requiresHttpsForRemoteEndpoints() {
        assertFailsWith<IllegalArgumentException> {
            validateApiBaseUrl("http://api.example.com")
        }
        assertEquals("https://api.example.com", validateApiBaseUrl("https://api.example.com/"))
    }

    @Test
    fun rejectsBlankAndUnsupportedEndpoints() {
        assertFailsWith<IllegalArgumentException> { validateApiBaseUrl(" ") }
        assertFailsWith<IllegalArgumentException> { validateApiBaseUrl("ftp://example.com") }
    }
}