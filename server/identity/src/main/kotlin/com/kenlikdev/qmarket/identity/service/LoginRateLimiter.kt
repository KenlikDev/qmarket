package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.TooManyRequestsException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * In-process sliding-window limiter for failed logins.
 *
 * Optional [clientKey] (typically client IP) is mixed into the map key so operators
 * can correlate abuse; email-only keys still work for unit tests and legacy callers.
 *
 * Not a substitute for edge rate limiting / Redis when running multiple instances.
 */
@Component
class LoginRateLimiter(
    @Value("\${qmarket.auth.login-max-attempts:5}") private val maxAttempts: Int,
    @Value("\${qmarket.auth.login-window-seconds:300}") private val windowSeconds: Long,
    @Value("\${qmarket.auth.login-rate-max-keys:10000}") private val maxKeys: Int = 10_000,
) {
    /** Overridable in tests for deterministic windows. */
    var clock: Clock = Clock.systemUTC()

    private val failures = ConcurrentHashMap<String, ArrayDeque<Long>>()

    init {
        require(maxAttempts > 0) { "qmarket.auth.login-max-attempts must be greater than 0" }
        require(windowSeconds > 0) { "qmarket.auth.login-window-seconds must be greater than 0" }
        require(maxKeys > 0) { "qmarket.auth.login-rate-max-keys must be greater than 0" }
    }

    fun assertAllowed(
        email: String,
        clientKey: String? = null,
    ) {
        val key = compositeKey(email, clientKey)
        pruneKey(key)
        val q = failures[key] ?: return
        synchronized(q) {
            if (q.size >= maxAttempts) {
                throw TooManyRequestsException(
                    "Too many failed login attempts; try again later",
                )
            }
        }
    }

    fun recordFailure(
        email: String,
        clientKey: String? = null,
    ) {
        val key = compositeKey(email, clientKey)
        val now = clock.millis()
        val q = failures.computeIfAbsent(key) { ArrayDeque() }
        synchronized(q) {
            pruneDeque(q, now)
            q.addLast(now)
        }
        boundMapSize()
    }

    fun clear(
        email: String,
        clientKey: String? = null,
    ) {
        failures.remove(compositeKey(email, clientKey))
        failures.remove(email.trim().lowercase())
    }

    private fun compositeKey(
        email: String,
        clientKey: String?,
    ): String {
        val e = email.trim().lowercase()
        val c = clientKey?.trim().orEmpty()
        return if (c.isEmpty()) e else "$e|$c"
    }

    private fun pruneKey(key: String) {
        val q = failures[key] ?: return
        val now = clock.millis()
        synchronized(q) {
            pruneDeque(q, now)
            if (q.isEmpty()) {
                failures.remove(key, q)
            }
        }
    }

    private fun pruneDeque(
        q: ArrayDeque<Long>,
        now: Long,
    ) {
        val cutoff = now - windowSeconds * 1000
        while (q.isNotEmpty() && q.first() < cutoff) {
            q.removeFirst()
        }
    }

    private fun boundMapSize() {
        if (failures.size <= maxKeys) return
        val now = clock.millis()
        val iter = failures.entries.iterator()
        while (iter.hasNext() && failures.size > maxKeys) {
            val e = iter.next()
            val q = e.value
            synchronized(q) {
                pruneDeque(q, now)
                if (q.isEmpty()) {
                    iter.remove()
                }
            }
        }
        while (failures.size > maxKeys) {
            val key = failures.keys.firstOrNull() ?: break
            failures.remove(key)
        }
    }
}
