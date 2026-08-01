package com.kenlikdev.qmarket.identity.web

import com.kenlikdev.qmarket.common.exception.GlobalExceptionHandler
import com.kenlikdev.qmarket.common.exception.NotFoundException
import com.kenlikdev.qmarket.identity.dto.AddressResponse
import com.kenlikdev.qmarket.identity.service.AddressService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class AddressControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var addressService: AddressService
    private val userId = UUID.randomUUID()
    private val addressId = UUID.randomUUID()

    private val sample =
        AddressResponse(
            id = addressId,
            label = "Home",
            recipientName = "Ivan",
            phone = "+7999",
            country = "RU",
            region = null,
            city = "Moscow",
            streetLine1 = "Tverskaya 1",
            streetLine2 = null,
            postalCode = "101000",
            default = true,
            formatted = "Ivan, Tverskaya 1, 101000 Moscow, RU",
        )

    @BeforeEach
    fun setUp() {
        addressService = mockk()
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(AddressController(addressService))
                .setControllerAdvice(GlobalExceptionHandler())
                .build()
    }

    @Test
    fun `GET list returns addresses`() {
        every { addressService.list(userId) } returns listOf(sample)
        val auth = UsernamePasswordAuthenticationToken(userId, null, emptyList())

        mockMvc
            .perform(get("/api/v1/users/me/addresses").principal(auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].city").value("Moscow"))
    }

    @Test
    fun `POST create returns 201`() {
        every { addressService.create(userId, any()) } returns sample
        val auth = UsernamePasswordAuthenticationToken(userId, null, emptyList())
        val body =
            """
            {
              "recipientName": "Ivan",
              "city": "Moscow",
              "streetLine1": "Tverskaya 1",
              "default": true
            }
            """.trimIndent()

        mockMvc
            .perform(
                post("/api/v1/users/me/addresses")
                    .principal(auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.recipientName").value("Ivan"))
    }

    @Test
    fun `GET missing returns 404`() {
        every { addressService.get(userId, addressId) } throws NotFoundException("Address not found")
        val auth = UsernamePasswordAuthenticationToken(userId, null, emptyList())

        mockMvc
            .perform(get("/api/v1/users/me/addresses/$addressId").principal(auth))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PUT update returns 200`() {
        every { addressService.update(userId, addressId, any()) } returns sample.copy(city = "SPb")
        val auth = UsernamePasswordAuthenticationToken(userId, null, emptyList())
        val body = """{"city":"SPb"}"""

        mockMvc
            .perform(
                put("/api/v1/users/me/addresses/$addressId")
                    .principal(auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.city").value("SPb"))
    }

    @Test
    fun `DELETE returns 204`() {
        every { addressService.delete(userId, addressId) } just Runs
        val auth = UsernamePasswordAuthenticationToken(userId, null, emptyList())

        mockMvc
            .perform(delete("/api/v1/users/me/addresses/$addressId").principal(auth))
            .andExpect(status().isNoContent)

        verify { addressService.delete(userId, addressId) }
    }
}
