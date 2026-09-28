package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.identity.repository.RefreshTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Security-sensitive refresh-token revocation runs in an independent transaction.
 *
 * AuthService.refresh() intentionally throws after detecting a replay. Keeping this
 * mutation in REQUIRES_NEW prevents the outer authentication transaction from rolling
 * the revocation back with the 401 response.
 */
@Service
class RefreshTokenRevocationService(
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun revokeFamily(familyId: UUID) {
        refreshTokenRepository.revokeFamily(familyId, Instant.now())
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun revokeToken(jti: UUID) {
        refreshTokenRepository.revokeIfActive(jti, Instant.now())
    }
}
