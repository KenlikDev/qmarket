package com.kenlikdev.qmarket.identity.repository

import com.kenlikdev.qmarket.identity.domain.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    fun findByJti(jti: UUID): RefreshToken?

    /**
     * Atomically consume a refresh token. Returns 1 only for the winner of concurrent refresh races.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "UPDATE RefreshToken t SET t.revokedAt = :at WHERE t.jti = :jti AND t.revokedAt IS NULL",
    )
    fun revokeIfActive(
        @Param("jti") jti: UUID,
        @Param("at") at: Instant,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "UPDATE RefreshToken t SET t.revokedAt = :at WHERE t.familyId = :familyId AND t.revokedAt IS NULL",
    )
    fun revokeFamily(
        @Param("familyId") familyId: UUID,
        @Param("at") at: Instant,
    ): Int

    /** Revoke every active refresh token for a user (password change, account disable). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "UPDATE RefreshToken t SET t.revokedAt = :at WHERE t.userId = :userId AND t.revokedAt IS NULL",
    )
    fun revokeAllForUser(
        @Param("userId") userId: UUID,
        @Param("at") at: Instant,
    ): Int
}
