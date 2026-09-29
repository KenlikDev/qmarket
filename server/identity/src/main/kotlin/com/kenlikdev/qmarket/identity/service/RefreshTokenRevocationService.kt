package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.identity.repository.RefreshTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class RefreshTokenRevocationService(
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    /**
     * Family revocation must commit independently from the authentication request's 401 rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun revokeFamily(familyId: UUID) {
        refreshTokenRepository.revokeFamily(familyId, Instant.now())
    }
}
