package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.TooManyRequestsException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class LoginRateLimiterTest {
    private fun limiter(
        max: Int = 3,
        window: Long = 300,
        instant: String = "2026-08-01T12:00:00Z",
    ): LoginRateLimiter {
        val fixed = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)
        return LoginRateLimiter(maxAttempts = max, windowSeconds = window).also { it.clock = fixed }
    }

    @Test
    fun `blocks after max failed attempts`() {
        val lim = limiter(max = 3)
        val email = "user@test.com"

        repeat(3) {
            assertDoesNotThrow { lim.assertAllowed(email) }
            lim.recordFailure(email)
        }

        assertThrows<TooManyRequestsException> {
            lim.assertAllowed(email)
        }
    }

    @Test
    fun `clear after success allows login again`() {
        val lim = limiter(max = 2)
        val email = "user@test.com"

        lim.recordFailure(email)
        lim.recordFailure(email)
        assertThrows<TooManyRequestsException> { lim.assertAllowed(email) }

        lim.clear(email)
        assertDoesNotThrow { lim.assertAllowed(email) }
    }

    @Test
    fun `keys are case-insensitive`() {
        val lim = limiter(max = 1)

        lim.recordFailure("User@Test.COM")
        assertThrows<TooManyRequestsException> {
            lim.assertAllowed("user@test.com")
        }
    }
}
