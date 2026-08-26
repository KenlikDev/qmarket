package com.kenlikdev.qmarket.identity.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(name = "user_id", nullable = false)
    var userId: UUID,
    @Column(nullable = false, unique = true)
    var jti: UUID,
    @Column(name = "family_id", nullable = false)
    var familyId: UUID,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,
    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
) {
    val isRevoked: Boolean
        get() = revokedAt != null

    val isExpired: Boolean
        get() = Instant.now().isAfter(expiresAt)

    fun revoke(at: Instant = Instant.now()) {
        if (revokedAt == null) {
            revokedAt = at
        }
    }
}
