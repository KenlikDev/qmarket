package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.NotFoundException
import com.kenlikdev.qmarket.identity.domain.Role
import com.kenlikdev.qmarket.identity.domain.User
import com.kenlikdev.qmarket.identity.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.Optional
import java.util.UUID

class AdminUserServiceTest {
    private lateinit var userRepository: UserRepository
    private lateinit var service: AdminUserService

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        service = AdminUserService(userRepository)
    }

    @Test
    fun `listUsers without q uses findAll`() {
        val id = UUID.randomUUID()
        val user =
            User(
                id = id,
                email = "u@test.com",
                passwordHash = "x",
                firstName = "A",
                lastName = "B",
                enabled = true,
                emailVerified = false,
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                roles = mutableSetOf(Role(name = "ROLE_USER")),
            )
        every { userRepository.findAll(any<Pageable>()) } returns
            PageImpl(listOf(user), PageRequest.of(0, 20), 1)

        val page = service.listUsers(0, 20, null)
        assertEquals(1, page.totalElements)
        assertEquals("u@test.com", page.content.single().email)
        verify(exactly = 1) { userRepository.findAll(any<Pageable>()) }
    }

    @Test
    fun `listUsers with q uses derived search`() {
        every {
            userRepository.findByEmailContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
                "ada",
                "ada",
                "ada",
                any(),
            )
        } returns PageImpl(emptyList(), PageRequest.of(0, 20), 0)

        val page = service.listUsers(0, 20, "ada")
        assertEquals(0, page.totalElements)
    }

    @Test
    fun `getUser not found`() {
        val id = UUID.randomUUID()
        every { userRepository.findById(id) } returns Optional.empty()
        assertThrows(NotFoundException::class.java) { service.getUser(id) }
    }
}
