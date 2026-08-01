package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.NotFoundException
import com.kenlikdev.qmarket.identity.domain.User
import com.kenlikdev.qmarket.identity.dto.ProfileResponse
import com.kenlikdev.qmarket.identity.dto.UpdateProfileRequest
import com.kenlikdev.qmarket.identity.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ProfileService(
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun getMyProfile(userId: UUID): ProfileResponse {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { NotFoundException("User not found") }
        return toResponse(user)
    }

    @Transactional
    fun updateMyProfile(
        userId: UUID,
        request: UpdateProfileRequest,
    ): ProfileResponse {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { NotFoundException("User not found") }

        request.firstName?.let { user.firstName = it.trim().ifEmpty { null } }
        request.lastName?.let { user.lastName = it.trim().ifEmpty { null } }
        request.phone?.let { user.phone = it.trim().ifEmpty { null } }

        return toResponse(userRepository.save(user))
    }

    private fun toResponse(user: User): ProfileResponse =
        ProfileResponse(
            id = user.id ?: error("User id is null"),
            email = user.email,
            firstName = user.firstName,
            lastName = user.lastName,
            phone = user.phone,
            emailVerified = user.emailVerified,
            roles = user.roles.map { it.name }.sorted(),
        )
}
