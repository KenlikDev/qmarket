package com.kenlikdev.qmarket.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApiConfigTest {
    @Test
    fun `normalizes secure endpoint`() {
        assertEquals("https://api.example.com", validateApiBaseUrl(" https://api.example.com/ "))
    }

    @Test
    fun `allows local HTTP development endpoint`() {
        assertEquals("http://localhost:8080", validateApiBaseUrl("http://localhost:8080"))
        assertEquals("http://127.0.0.1:8080", validateApiBaseUrl("http://127.0.0.1:8080"))
        assertEquals("http://10.0.2.2:8080", validateApiBaseUrl("http://10.0.2.2:8080"))
    }

    @Test
    fun `rejects remote HTTP endpoint`() {
        assertFailsWith<IllegalArgumentException> {
            validateApiBaseUrl("http://api.example.com")
        }
    }

    @Test
    fun `rejects local looking host suffix`() {
        assertFailsWith<IllegalArgumentException> {
            validateApiBaseUrl("http://localhost.example.com")
        }
    }

    @Test
    fun `rejects blank endpoint`() {
        assertFailsWith<IllegalArgumentException> {
            validateApiBaseUrl("   ")
        }
    }
}
