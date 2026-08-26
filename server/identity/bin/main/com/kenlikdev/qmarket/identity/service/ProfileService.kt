package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.NotFoundException
import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.common.validation.InputValidation
import com.kenlikdev.qmarket.identity.domain.User
import com.kenlikdev.qmarket.identity.dto.ChangePasswordRequest
import com.kenlikdev.qmarket.identity.dto.ProfileResponse
import com.kenlikdev.qmarket.identity.dto.UpdateProfileRequest
import com.kenlikdev.qmarket.identity.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ProfileService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
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

        request.firstName?.let {
            user.firstName = InputValidation.normalizeOptionalName(it, "First name")
        }
        request.lastName?.let {
            user.lastName = InputValidation.normalizeOptionalName(it, "Last name")
        }
        request.phone?.let {
            user.phone = InputValidation.normalizeOptionalPhone(it)
        }

        return toResponse(userRepository.save(user))
    }

    @Transactional
    fun changePassword(
        userId: UUID,
        request: ChangePasswordRequest,
    ) {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { NotFoundException("User not found") }

        if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
            throw UnauthorizedException("Current password is incorrect")
        }
        if (request.currentPassword == request.newPassword) {
            throw BadRequestException("New password must differ from the current password")
        }

        val encoded =
            passwordEncoder.encode(request.newPassword)
                ?: throw IllegalStateException("Password encoding returned null")
        user.passwordHash = encoded
        userRepository.save(user)
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
