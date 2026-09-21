package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.NotFoundException
import com.kenlikdev.qmarket.identity.domain.User
import com.kenlikdev.qmarket.identity.dto.AdminUserPageResponse
import com.kenlikdev.qmarket.identity.dto.AdminUserResponse
import com.kenlikdev.qmarket.identity.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AdminUserService(
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun listUsers(
        page: Int,
        size: Int,
        q: String? = null,
    ): AdminUserPageResponse {
        val pageable =
            PageRequest.of(
                page.coerceAtLeast(0),
                size.coerceIn(1, 100),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"),
            )
        val query = q?.trim()?.takeIf { it.isNotEmpty() }
        val result =
            if (query == null) {
                userRepository.findAllBy(pageable)
            } else {
                userRepository.findByEmailContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
                    query,
                    query,
                    query,
                    pageable,
                )
            }
        return AdminUserPageResponse(
            content = result.content.map { it.toAdminResponse() },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    @Transactional(readOnly = true)
    fun getUser(id: UUID): AdminUserResponse {
        val user = userRepository.findById(id).orElseThrow { NotFoundException("User not found") }
        return user.toAdminResponse()
    }

    private fun User.toAdminResponse(): AdminUserResponse =
        AdminUserResponse(
            id = id ?: error("User id is null"),
            email = email,
            firstName = firstName,
            lastName = lastName,
            phone = phone,
            enabled = enabled,
            emailVerified = emailVerified,
            roles = roles.map { it.name }.sorted(),
            createdAt = createdAt,
        )
}
