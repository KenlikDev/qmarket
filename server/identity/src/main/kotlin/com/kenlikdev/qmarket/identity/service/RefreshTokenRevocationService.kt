package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.identity.repository.RefreshTokenRepository
import com.kenlikdev.qmarket.identity.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class RefreshTokenRevocationService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userRepository: UserRepository,
) {
    /**
     * Family revocation must commit independently from the authentication request's 401 rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun revokeFamily(familyId: UUID) {
        refreshTokenRepository.revokeFamily(familyId, Instant.now())
    }

    /**
     * Logout uses the same user-row lock as refresh rotation. This creates one ordering point:
     * either refresh commits its successor first and logout revokes it, or logout commits first
     * and refresh observes the revoked family. There must be no active successor after logout.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun revokeFamilyAfterLogout(
        userId: UUID,
        familyId: UUID,
    ) {
        // Token rows are rotated only while holding this lock in AuthService.refresh().
        // If a legacy user row is missing, still revoke any remaining rows for the family.
        userRepository.findByIdForUpdate(userId)
        refreshTokenRepository.revokeFamily(familyId, Instant.now())
    }
}
