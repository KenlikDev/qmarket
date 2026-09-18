package com.kenlikdev.qmarket.order.service

import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.ConflictException
import com.kenlikdev.qmarket.common.exception.NotFoundException
import com.kenlikdev.qmarket.order.domain.OrderIdempotencyKey
import com.kenlikdev.qmarket.order.dto.CreateOrderRequest
import com.kenlikdev.qmarket.order.dto.OrderResponse
import com.kenlikdev.qmarket.order.repository.OrderIdempotencyKeyRepository
import com.kenlikdev.qmarket.order.repository.OrderRepository
import jakarta.persistence.EntityManager
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.util.UUID

/**
 * Checkout idempotency: advisory locks, fingerprint matching, key normalization.
 */
@Component
class OrderIdempotencySupport(
    private val orderRepository: OrderRepository,
    private val idempotencyKeyRepository: OrderIdempotencyKeyRepository,
    private val entityManager: EntityManager,
    transactionManager: PlatformTransactionManager,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    fun normalizeKey(raw: String?): String? {
        val key = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (key.length > 128) {
            throw BadRequestException("Idempotency-Key must be at most 128 characters")
        }
        return key
    }

    fun acquireLock(
        userId: UUID,
        normalizedKey: String,
    ) {
        val lockId = lockId(userId, normalizedKey)
        entityManager
            .createNativeQuery("SELECT pg_advisory_xact_lock(:lockId)")
            .setParameter("lockId", lockId)
            .singleResult
    }

    fun lockId(
        userId: UUID,
        normalizedKey: String,
    ): Long {
        val a = userId.mostSignificantBits xor userId.leastSignificantBits
        val b = normalizedKey.hashCode().toLong()
        return a xor (b shl 32) xor (b ushr 16)
    }

    fun loadReplay(
        userId: UUID,
        normalizedKey: String,
        request: CreateOrderRequest,
    ): OrderResponse? {
        val existing = idempotencyKeyRepository.findByUserIdAndKey(userId, normalizedKey) ?: return null
        assertFingerprintMatches(existing, request)
        // Short TX so LAZY order.items can be initialized for toResponse.
        return transactionTemplate.execute {
            val order =
                orderRepository.findById(existing.orderId).orElseThrow {
                    NotFoundException("Order not found for idempotency key")
                }
            order.items.size
            OrderMapper.toResponse(order)
        }
    }

    /**
     * If [rawKey] already maps to an order for [userId], validate request fingerprint
     * and return the existing order (HTTP 200 replay). Null = first attempt.
     */
    fun findReplay(
        userId: UUID,
        request: CreateOrderRequest,
        rawKey: String,
    ): OrderResponse? {
        val key = normalizeKey(rawKey) ?: return null
        return loadReplay(userId, key, request)
    }

    fun isKeyConstraint(ex: DataIntegrityViolationException): Boolean {
        val msg =
            buildString {
                append(ex.message.orEmpty())
                append(' ')
                append(ex.mostSpecificCause.message.orEmpty())
            }.lowercase()
        return "uq_order_idempotency" in msg || "order_idempotency_keys" in msg
    }

    /**
     * Stable SHA-256 of fields that affect order creation (shipping + note).
     */
    fun requestFingerprint(request: CreateOrderRequest): String {
        val normalized =
            buildString {
                append("addressId=")
                append(request.addressId?.toString().orEmpty())
                append("|shipping=")
                append(request.shippingAddress?.trim().orEmpty())
                append("|note=")
                append(request.customerNote?.trim().orEmpty())
            }
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    fun saveKey(
        userId: UUID,
        normalizedKey: String,
        orderId: UUID,
        request: CreateOrderRequest,
    ) {
        idempotencyKeyRepository.save(
            OrderIdempotencyKey(
                userId = userId,
                key = normalizedKey,
                orderId = orderId,
                requestHash = requestFingerprint(request),
            ),
        )
    }

    private fun assertFingerprintMatches(
        existing: OrderIdempotencyKey,
        request: CreateOrderRequest,
    ) {
        val stored = existing.requestHash
        if (stored.isBlank()) {
            // Pre-V8 rows: no fingerprint stored — allow replay with any body.
            return
        }
        val incoming = requestFingerprint(request)
        if (stored != incoming) {
            throw ConflictException(
                "Idempotency-Key was already used with a different request body",
            )
        }
    }
}
