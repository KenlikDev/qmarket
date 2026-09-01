package com.kenlikdev.qmarket.identity.web

import com.kenlikdev.qmarket.common.exception.GlobalExceptionHandler
import com.kenlikdev.qmarket.common.exception.NotFoundException
import com.kenlikdev.qmarket.identity.dto.AdminUserPageResponse
import com.kenlikdev.qmarket.identity.dto.AdminUserResponse
import com.kenlikdev.qmarket.identity.service.AdminUserService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class AdminUserControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var adminUserService: AdminUserService

    private val userId = UUID.randomUUID()
    private val sample =
        AdminUserResponse(
            id = userId,
            email = "admin@qmarket.local",
            firstName = "Ada",
            lastName = "Admin",
            phone = null,
            enabled = true,
            emailVerified = true,
            roles = listOf("ROLE_ADMIN"),
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

    @BeforeEach
    fun setUp() {
        adminUserService = mockk()
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(AdminUserController(adminUserService))
                .setControllerAdvice(GlobalExceptionHandler())
                .build()
    }

    @Test
    fun `GET list returns page`() {
        every { adminUserService.listUsers(0, 20, null) } returns
            AdminUserPageResponse(
                content = listOf(sample),
                page = 0,
                size = 20,
                totalElements = 1,
                totalPages = 1,
            )

        mockMvc
            .perform(get("/api/v1/admin/users"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].email").value("admin@qmarket.local"))
            .andExpect(jsonPath("$.totalElements").value(1))

        verify(exactly = 1) { adminUserService.listUsers(0, 20, null) }
    }

    @Test
    fun `GET list with q passes query`() {
        every { adminUserService.listUsers(0, 10, "ada") } returns
            AdminUserPageResponse(emptyList(), 0, 10, 0, 0)

        mockMvc
            .perform(get("/api/v1/admin/users").param("page", "0").param("size", "10").param("q", "ada"))
            .andExpect(status().isOk)

        verify(exactly = 1) { adminUserService.listUsers(0, 10, "ada") }
    }

    @Test
    fun `GET by id returns user`() {
        every { adminUserService.getUser(userId) } returns sample

        mockMvc
            .perform(get("/api/v1/admin/users/$userId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("admin@qmarket.local"))
    }

    @Test
    fun `GET by id not found returns 404`() {
        every { adminUserService.getUser(userId) } throws NotFoundException("User not found")

        mockMvc
            .perform(get("/api/v1/admin/users/$userId"))
            .andExpect(status().isNotFound)
    }
}
