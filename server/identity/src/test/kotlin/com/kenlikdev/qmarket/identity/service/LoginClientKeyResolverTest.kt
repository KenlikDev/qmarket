package com.kenlikdev.qmarket.identity.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class LoginClientKeyResolverTest {
    private val resolver = LoginClientKeyResolver("192.0.2.0/24")

    @Test
    fun `untrusted proxy cannot spoof forwarded client address`() {
        val request = MockHttpServletRequest().apply {
            remoteAddr = "198.51.100.10"
            addHeader("X-Forwarded-For", "203.0.113.10")
        }

        assertEquals("remote:198.51.100.10", resolver.resolve(request))
    }

    @Test
    fun `trusted proxy may supply forwarded client address`() {
        val request = MockHttpServletRequest().apply {
            remoteAddr = "192.0.2.10"
            addHeader("X-Forwarded-For", "203.0.113.10, 192.0.2.1")
        }

        assertEquals("forwarded:203.0.113.10", resolver.resolve(request))
    }
}