package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.NotFoundException
import com.kenlikdev.qmarket.identity.domain.Role
import com.kenlikdev.qmarket.identity.domain.User
import com.kenlikdev.qmarket.identity.dto.UpdateProfileRequest
import com.kenlikdev.qmarket.identity.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

class ProfileServiceTest {
    private lateinit var userRepository: UserRepository
    private lateinit var profileService: ProfileService

    private val userId = UUID.randomUUID()
    private val role = Role(id = UUID.randomUUID(), name = "ROLE_USER")

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        profileService = ProfileService(userRepository)
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
                phone = "+1000",
            ).apply { roles.add(role) }

        every { userRepository.findById(userId) } returns Optional.of(user)

        val result = profileService.getMyProfile(userId)

        assertEquals(userId, result.id)
        assertEquals("user@test.com", result.email)
        assertEquals("John", result.firstName)
        assertEquals("+1000", result.phone)
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
                UpdateProfileRequest(firstName = "New", lastName = "Name", phone = "+7999"),
            )

        assertEquals("New", result.firstName)
        assertEquals("Name", result.lastName)
        assertEquals("+7999", result.phone)
        verify { userRepository.save(any()) }
    }
}
