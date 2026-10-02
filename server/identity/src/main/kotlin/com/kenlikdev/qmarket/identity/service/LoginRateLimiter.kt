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

    class Attempt internal constructor(
        internal val keys: List<String>,
    ) {
        internal var completed: Boolean = false
    }

    private val attemptLock = Any()
    private val inFlight = HashMap<String, Int>()

    /** Atomically reserves one expensive login verification across all limiter keys. */
    fun beginAttempt(
        email: String,
        clientKey: String? = null,
    ): Attempt =
        synchronized(attemptLock) {
            val now = clock.millis()
            val keys = keysFor(email, clientKey)

            val existingQueues =
                keys.associateWith { key ->
                    failures[key]?.also { pruneDeque(it, now) }
                }
            if (
                keys.any { key ->
                    val queueSize = existingQueues[key]?.size ?: 0
                    queueSize + (inFlight[key] ?: 0) >= maxAttempts
                }
            ) {
                throw TooManyRequestsException(
                    "Too many failed login attempts; try again later",
                )
            }

            boundMapSize()
            val newKeyCount = keys.count { !failures.containsKey(it) }
            if (failures.size + newKeyCount > maxKeys) {
                throw TooManyRequestsException(
                    "Login rate limiter capacity is temporarily exhausted; try again later",
                )
            }

            keys.forEach { key ->
                failures.computeIfAbsent(key) { ArrayDeque() }
            }
            keys.forEach { key -> inFlight[key] = (inFlight[key] ?: 0) + 1 }
            Attempt(keys)
        }

    /** Record a failed login and release its in-flight reservation. */
    fun recordFailure(attempt: Attempt) {
        synchronized(attemptLock) {
            if (attempt.completed) return
            val now = clock.millis()
            attempt.keys.forEach { key ->
                val queue = failures.computeIfAbsent(key) { ArrayDeque() }
                pruneDeque(queue, now)
                inFlight[key] = ((inFlight[key] ?: 1) - 1).coerceAtLeast(0)
                if (queue.size < maxAttempts) queue.addLast(now)
                if (inFlight[key] == 0) inFlight.remove(key)
            }
            attempt.completed = true
            boundMapSize()
        }
    }

    /** Record a successful login, releasing its reservation and clearing account failures. */
    fun recordSuccess(
        attempt: Attempt,
        email: String,
    ) {
        synchronized(attemptLock) {
            if (attempt.completed) return
            attempt.keys.forEach { key ->
                val remaining = ((inFlight[key] ?: 1) - 1).coerceAtLeast(0)
                if (remaining == 0) inFlight.remove(key) else inFlight[key] = remaining
            }
            failures.remove("email:${email.trim().lowercase()}")
            attempt.completed = true
            boundMapSize()
        }
    }

    /** Release a reservation when authentication aborts before a result is recorded. */
    fun releaseAttempt(attempt: Attempt) {
        synchronized(attemptLock) {
            if (attempt.completed) return
            attempt.keys.forEach { key ->
                val remaining = ((inFlight[key] ?: 1) - 1).coerceAtLeast(0)
                if (remaining == 0) inFlight.remove(key) else inFlight[key] = remaining
            }
            attempt.completed = true
            boundMapSize()
        }
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
            if ((inFlight[e.key] ?: 0) > 0) continue
            synchronized(q) {
                pruneDeque(q, now)
                if (q.isEmpty()) {
                    iter.remove()
                }
            }
        }
        while (failures.size > maxKeys) {
            val key =
                failures.entries
                    .firstOrNull { (inFlight[it.key] ?: 0) == 0 }
                    ?.key
                    ?: break
            failures.remove(key)
        }
    }
}
