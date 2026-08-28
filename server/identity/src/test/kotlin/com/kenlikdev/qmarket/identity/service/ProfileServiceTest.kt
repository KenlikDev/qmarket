package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.NotFoundException
import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.identity.domain.Role
import com.kenlikdev.qmarket.identity.domain.User
import com.kenlikdev.qmarket.identity.dto.ChangePasswordRequest
import com.kenlikdev.qmarket.identity.dto.UpdateProfileRequest
import com.kenlikdev.qmarket.identity.repository.RefreshTokenRepository
import com.kenlikdev.qmarket.identity.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional
import java.util.UUID

class ProfileServiceTest {
    private lateinit var userRepository: UserRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var profileService: ProfileService

    private val userId = UUID.randomUUID()
    private val role = Role(id = UUID.randomUUID(), name = "ROLE_USER")

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        passwordEncoder = mockk()
        refreshTokenRepository = mockk(relaxed = true)
        every { refreshTokenRepository.revokeAllForUser(any(), any()) } returns 1
        profileService = ProfileService(userRepository, passwordEncoder, refreshTokenRepository)
    }

    @Test
    fun `getMyProfile returns profile`() {
        val user =
            User(
                id = userId,
                email = "user@test.com",
                passwordHash = "hash",
                firstName = "John",
                lastName = "Doe",
                phone = "+79001112233",
            ).apply { roles.add(role) }

        every { userRepository.findById(userId) } returns Optional.of(user)

        val result = profileService.getMyProfile(userId)

        assertEquals(userId, result.id)
        assertEquals("user@test.com", result.email)
        assertEquals("John", result.firstName)
        assertEquals("+79001112233", result.phone)
        assertEquals(listOf("ROLE_USER"), result.roles)
    }

    @Test
    fun `getMyProfile not found`() {
        every { userRepository.findById(userId) } returns Optional.empty()

        assertThrows<NotFoundException> {
            profileService.getMyProfile(userId)
        }
    }

    @Test
    fun `updateMyProfile changes fields`() {
        val user =
            User(
                id = userId,
                email = "user@test.com",
                passwordHash = "hash",
                firstName = "Old",
            ).apply { roles.add(role) }

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { userRepository.save(any()) } answers { firstArg() }

        val result =
            profileService.updateMyProfile(
                userId,
                UpdateProfileRequest(firstName = "New", lastName = "Name", phone = "+79001234567"),
            )

        assertEquals("New", result.firstName)
        assertEquals("Name", result.lastName)
        assertEquals("+79001234567", result.phone)
        verify { userRepository.save(any()) }
    }

    @Test
    fun `changePassword updates hash when current matches`() {
        val user =
            User(
                id = userId,
                email = "user@test.com",
                passwordHash = "old-hash",
            ).apply { roles.add(role) }

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { passwordEncoder.matches("old-pass", "old-hash") } returns true
        every { passwordEncoder.encode("new-pass-123") } returns "new-hash"
        every { userRepository.save(any()) } answers { firstArg() }

        profileService.changePassword(
            userId,
            ChangePasswordRequest(currentPassword = "old-pass", newPassword = "new-pass-123"),
        )

        verify { userRepository.save(match { it.passwordHash == "new-hash" }) }
        verify(exactly = 1) { refreshTokenRepository.revokeAllForUser(userId, any()) }
    }

    @Test
    fun `changePassword rejects wrong current password`() {
        val user =
            User(
                id = userId,
                email = "user@test.com",
                passwordHash = "old-hash",
            ).apply { roles.add(role) }

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { passwordEncoder.matches("wrong", "old-hash") } returns false

        assertThrows<UnauthorizedException> {
            profileService.changePassword(
                userId,
                ChangePasswordRequest(currentPassword = "wrong", newPassword = "new-pass-123"),
            )
        }
    }

    @Test
    fun `changePassword rejects same password`() {
        val user =
            User(
                id = userId,
                email = "user@test.com",
                passwordHash = "old-hash",
            ).apply { roles.add(role) }

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { passwordEncoder.matches("same-pass", "old-hash") } returns true

        assertThrows<BadRequestException> {
            profileService.changePassword(
                userId,
                ChangePasswordRequest(currentPassword = "same-pass", newPassword = "same-pass"),
            )
        }
    }
}
