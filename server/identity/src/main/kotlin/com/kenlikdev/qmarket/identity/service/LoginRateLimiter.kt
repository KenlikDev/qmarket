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
 * Failed attempts are tracked independently by normalized email and, when available,
 * by client key (typically the client IP). This prevents an attacker from bypassing
 * account protection simply by rotating source IPs and also limits abuse from a
 * single client against many accounts.
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
        keysFor(email, clientKey).forEach(::assertKeyAllowed)
    }

    fun recordFailure(
        email: String,
        clientKey: String? = null,
    ) {
        val now = clock.millis()
        keysFor(email, clientKey).forEach { key ->
            val q = failures.computeIfAbsent(key) { ArrayDeque() }
            synchronized(q) {
                pruneDeque(q, now)
                if (q.size < maxAttempts) {
                    q.addLast(now)
                }
            }
        }
        boundMapSize()
    }

    /** Clear failed attempts for one account after a successful authentication. */
    fun clearAccount(email: String) {
        failures.remove("email:${email.trim().lowercase()}")
    }

    private fun keysFor(
        email: String,
        clientKey: String?,
    ): List<String> {
        val normalizedEmail = email.trim().lowercase()
        val normalizedClientKey = clientKey?.trim()?.takeIf { it.isNotEmpty() }
        return buildList {
            add("email:$normalizedEmail")
            normalizedClientKey?.let { add("client:$it") }
        }
    }

    private fun assertKeyAllowed(key: String) {
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
