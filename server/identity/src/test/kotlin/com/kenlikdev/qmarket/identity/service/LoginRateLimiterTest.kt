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
    fun `email limit survives client key rotation`() {
        val lim = limiter(max = 2)
        val email = "user@test.com"

        lim.recordFailure(email, "10.0.0.1")
        lim.recordFailure(email, "10.0.0.2")

        assertThrows<TooManyRequestsException> {
            lim.assertAllowed(email, "10.0.0.3")
        }
    }

    @Test
    fun `client limit applies across different accounts`() {
        val lim = limiter(max = 2)
        val client = "10.0.0.1"

        lim.recordFailure("one@test.com", client)
        lim.recordFailure("two@test.com", client)

        assertThrows<TooManyRequestsException> {
            lim.assertAllowed("three@test.com", client)
        }
    }

    @Test
    fun `keys are case-insensitive`() {
        val lim = limiter(max = 1)

        lim.recordFailure("User@Test.COM")
        assertThrows<TooManyRequestsException> {
            lim.assertAllowed("user@test.com")
        }
    }

    @Test
    fun `client key cannot bypass account limit`() {
        val lim = limiter(max = 2)
        val email = "user@test.com"

        lim.recordFailure(email, "10.0.0.1")
        lim.recordFailure(email, "10.0.0.2")

        assertThrows<TooManyRequestsException> {
            lim.assertAllowed(email, "10.0.0.3")
        }
    }

    @Test
    fun `client key limits attempts across different accounts`() {
        val lim = limiter(max = 2)
        val client = "10.0.0.1"

        lim.recordFailure("first@test.com", client)
        lim.recordFailure("second@test.com", client)

        assertThrows<TooManyRequestsException> {
            lim.assertAllowed("third@test.com", client)
        }
    }

    @Test
    fun `clearAccount resets only the account limit`() {
        val lim = limiter(max = 1)
        val email = "user@test.com"
        val client = "10.0.0.1"

        lim.recordFailure(email, client)
        lim.clearAccount(email)

        assertDoesNotThrow { lim.assertAllowed(email, "10.0.0.2") }
        assertThrows<TooManyRequestsException> {
            lim.assertAllowed("other@test.com", client)
        }
    }
}
