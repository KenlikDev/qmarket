package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.TooManyRequestsException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Sliding-window limiter for failed login attempts, keyed by normalized email.
 * Successful login clears the window for that key.
 */
@Component
class LoginRateLimiter(
    @Value("\${qmarket.auth.login-max-attempts:5}") private val maxAttempts: Int,
    @Value("\${qmarket.auth.login-window-seconds:300}") private val windowSeconds: Long,
) {
    /** Overridable in unit tests; production uses UTC system clock. */
    @Volatile
    var clock: Clock = Clock.systemUTC()

    private val failures = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun assertAllowed(email: String) {
        val key = normalize(email)
        pruneAndCount(key)
        val count = failures[key]?.size ?: 0
        if (count >= maxAttempts) {
            throw TooManyRequestsException(
                "Too many failed login attempts. Try again in a few minutes.",
            )
        }
    }

    fun recordFailure(email: String) {
        val key = normalize(email)
        val now = clock.millis()
        val q = failures.computeIfAbsent(key) { ArrayDeque() }
        synchronized(q) {
            pruneLocked(q, now)
            q.addLast(now)
        }
    }

    fun clear(email: String) {
        failures.remove(normalize(email))
    }

    private fun pruneAndCount(key: String) {
        val q = failures[key] ?: return
        synchronized(q) {
            pruneLocked(q, clock.millis())
            if (q.isEmpty()) {
                failures.remove(key, q)
            }
        }
    }

    private fun pruneLocked(
        q: ArrayDeque<Long>,
        nowMs: Long,
    ) {
        val cutoff = nowMs - windowSeconds * 1000
        while (q.isNotEmpty() && q.peekFirst() < cutoff) {
            q.removeFirst()
        }
    }

    private fun normalize(email: String): String = email.trim().lowercase()
}
