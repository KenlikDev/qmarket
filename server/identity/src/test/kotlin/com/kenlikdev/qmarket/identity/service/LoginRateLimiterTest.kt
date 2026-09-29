package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.TooManyRequestsException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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
        repeat(3) { lim.recordFailure(lim.beginAttempt("user@test.com")) }
        assertThrows<TooManyRequestsException> { lim.beginAttempt("user@test.com") }
    }

    @Test
    fun `success clears account failures and releases its reservation`() {
        val lim = limiter(max = 2)
        lim.recordFailure(lim.beginAttempt("user@test.com"))
        val success = lim.beginAttempt("user@test.com")
        lim.recordSuccess(success, "user@test.com")
        assertDoesNotThrow { lim.beginAttempt("user@test.com") }
    }

    @Test
    fun `account limit survives client rotation`() {
        val lim = limiter(max = 2)
        lim.recordFailure(lim.beginAttempt("user@test.com", "10.0.0.1"))
        lim.recordFailure(lim.beginAttempt("user@test.com", "10.0.0.2"))
        assertThrows<TooManyRequestsException> { lim.beginAttempt("user@test.com", "10.0.0.3") }
    }

    @Test
    fun `client limit applies across different accounts`() {
        val lim = limiter(max = 2)
        val client = "10.0.0.1"
        lim.recordFailure(lim.beginAttempt("one@test.com", client))
        lim.recordFailure(lim.beginAttempt("two@test.com", client))
        assertThrows<TooManyRequestsException> { lim.beginAttempt("three@test.com", client) }
    }

    @Test
    fun `expired attempts are pruned`() {
        val lim = limiter(max = 1, window = 1)
        lim.recordFailure(lim.beginAttempt("user@test.com"))
        lim.clock = Clock.fixed(Instant.parse("2026-08-01T12:00:02Z"), ZoneOffset.UTC)
        assertDoesNotThrow { lim.beginAttempt("user@test.com") }
    }

    @Test
    fun `concurrent reservations cannot exceed the limit`() {
        val lim = limiter(max = 1)
        val executor = Executors.newFixedThreadPool(20)
        val start = CountDownLatch(1)
        val done = CountDownLatch(20)
        val successes = AtomicInteger()

        repeat(20) {
            executor.submit {
                try {
                    start.await(10, TimeUnit.SECONDS)
                    runCatching { lim.beginAttempt("same@test.com") }
                        .onSuccess { successes.incrementAndGet() }
                } finally {
                    done.countDown()
                }
            }
        }

        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        executor.shutdownNow()
        assertEquals(1, successes.get())
    }
}
