package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.TooManyRequestsException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * In-process sliding-window limiter for failed logins.
 *
 * A login slot is reserved atomically before BCrypt work begins. The reservation
 * is converted to a failure, released on infrastructure failure, or removed on
 * successful authentication. The lock protects only limiter state and is never
 * held during password hashing.
 *
 * Distributed deployments still need an edge/Redis implementation with the same
 * reservation semantics.
 */
@Component
class LoginRateLimiter(
    @Value("\${qmarket.auth.login-max-attempts:5}") private val maxAttempts: Int,
    @Value("\${qmarket.auth.login-window-seconds:300}") private val windowSeconds: Long,
    @Value("\${qmarket.auth.login-rate-max-keys:10000}") private val maxKeys: Int = 10_000,
) {
    var clock: Clock = Clock.systemUTC()

    private val lock = Any()
    private val nextReservationId = AtomicLong(0)
    private val attemptsByKey = mutableMapOf<String, ArrayDeque<Attempt>>()

    init {
        require(maxAttempts > 0) { "qmarket.auth.login-max-attempts must be greater than 0" }
        require(windowSeconds > 0) { "qmarket.auth.login-window-seconds must be greater than 0" }
        require(maxKeys > 0) { "qmarket.auth.login-rate-max-keys must be greater than 0" }
    }

    fun beginAttempt(
        email: String,
        clientKey: String? = null,
    ): Reservation {
        val keys = keysFor(email, clientKey)
        val now = clock.millis()
        synchronized(lock) {
            pruneAllQueues(now)
            val missingKeys = keys.count { it !in attemptsByKey }
            if (attemptsByKey.size + missingKeys > maxKeys) {
                throw TooManyRequestsException("Login rate limiter capacity reached; try again later")
            }

            keys.forEach { key ->
                val queue = attemptsByKey.getOrPut(key) { ArrayDeque() }
                pruneQueue(queue, now)
                if (queue.size >= maxAttempts) {
                    throw TooManyRequestsException(
                        "Too many failed login attempts; try again later",
                    )
                }
            }

            val reservation =
                Reservation(
                    id = nextReservationId.incrementAndGet(),
                    keys = keys,
                )
            val attempt = Attempt(reservation.id, now)
            keys.forEach { key -> attemptsByKey.getValue(key).addLast(attempt) }
            return reservation
        }
    }

    fun recordFailure(reservation: Reservation) {
        synchronized(lock) {
            findAttempts(reservation).forEach { it.failed = true }
        }
    }

    fun recordSuccess(
        reservation: Reservation,
        email: String,
    ) {
        synchronized(lock) {
            removeReservation(reservation)
            val emailKey = "email:" + email.trim().lowercase()
            attemptsByKey[emailKey]?.removeIf { it.failed }
            cleanupEmptyQueues()
        }
    }

    fun release(reservation: Reservation) {
        synchronized(lock) {
            removeReservation(reservation)
            cleanupEmptyQueues()
        }
    }

    private fun keysFor(
        email: String,
        clientKey: String?,
    ): List<String> =
        buildList {
            add("email:${email.trim().lowercase()}")
            clientKey?.trim()?.takeIf { it.isNotEmpty() }?.let { add("client:$it") }
        }

    private fun pruneQueue(
        queue: ArrayDeque<Attempt>,
        now: Long,
    ) {
        val cutoff = now - windowSeconds * 1000
        while (queue.isNotEmpty() && queue.first.timestamp < cutoff) {
            queue.removeFirst()
        }
    }

    private fun findAttempts(reservation: Reservation): List<Attempt> =
        reservation.keys.flatMap { key ->
            attemptsByKey[key]?.filter { it.reservationId == reservation.id }.orEmpty()
        }

    private fun removeReservation(reservation: Reservation) {
        reservation.keys.forEach { key ->
            attemptsByKey[key]?.removeIf { it.reservationId == reservation.id }
        }
    }

    private fun pruneAllQueues(now: Long) {
        attemptsByKey.values.forEach { pruneQueue(it, now) }
        cleanupEmptyQueues()
    }

    private fun cleanupEmptyQueues() {
        attemptsByKey.entries.removeIf { it.value.isEmpty() }
    }

    private data class Attempt(
        val reservationId: Long,
        val timestamp: Long,
        var failed: Boolean = false,
    )

    class Reservation internal constructor(
        internal val id: Long,
        internal val keys: List<String>,
    )
}
