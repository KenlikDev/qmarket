package com.kenlikdev.qmarket.identity.web

import com.kenlikdev.qmarket.common.exception.GlobalExceptionHandler
import com.kenlikdev.qmarket.common.exception.NotFoundException
import com.kenlikdev.qmarket.identity.dto.ProfileResponse
import com.kenlikdev.qmarket.identity.service.ProfileService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class ProfileControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var profileService: ProfileService
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        profileService = mockk()
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(ProfileController(profileService))
                .setControllerAdvice(GlobalExceptionHandler())
                .build()
    }

    @Test
    fun `GET me returns profile`() {
        every { profileService.getMyProfile(userId) } returns
            ProfileResponse(
                id = userId,
                email = "u@test.com",
                firstName = "A",
                lastName = "B",
                phone = null,
                emailVerified = false,
                roles = listOf("ROLE_USER"),
            )
        val auth = UsernamePasswordAuthenticationToken(userId, null, emptyList())

        mockMvc
            .perform(get("/api/v1/users/me").principal(auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("u@test.com"))
            .andExpect(jsonPath("$.firstName").value("A"))
    }

    @Test
    fun `PATCH me updates profile`() {
        every { profileService.updateMyProfile(userId, any()) } returns
            ProfileResponse(
                id = userId,
                email = "u@test.com",
                firstName = "New",
                lastName = "Name",
                phone = "+1",
                emailVerified = false,
                roles = listOf("ROLE_USER"),
            )
        val auth = UsernamePasswordAuthenticationToken(userId, null, emptyList())
        val body =
            """
            {"firstName":"New","lastName":"Name","phone":"+1"}
            """.trimIndent()

        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .principal(auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.firstName").value("New"))

        verify { profileService.updateMyProfile(userId, any()) }
    }

    @Test
    fun `GET me not found returns 404`() {
        every { profileService.getMyProfile(userId) } throws NotFoundException("User not found")
        val auth = UsernamePasswordAuthenticationToken(userId, null, emptyList())

        mockMvc
            .perform(get("/api/v1/users/me").principal(auth))
            .andExpect(status().isNotFound)
    }
}
