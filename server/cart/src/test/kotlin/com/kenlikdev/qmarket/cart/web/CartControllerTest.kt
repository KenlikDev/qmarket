package com.kenlikdev.qmarket.cart.web

import com.kenlikdev.qmarket.cart.dto.CartResponse
import com.kenlikdev.qmarket.cart.service.CartService
import com.kenlikdev.qmarket.common.exception.GlobalExceptionHandler
import io.mockk.every
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
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class CartControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var cartService: CartService
    private val userId = UUID.randomUUID()
    private val auth = UsernamePasswordAuthenticationToken(userId, null, emptyList())

    private val emptyCart =
        CartResponse(
            id = UUID.randomUUID(),
            userId = userId,
            items = emptyList(),
            totalItems = 0,
            totalPrice = BigDecimal.ZERO,
            updatedAt = Instant.now(),
        )

    @BeforeEach
    fun setUp() {
        cartService = mockk()
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(CartController(cartService))
                .setControllerAdvice(GlobalExceptionHandler())
                .build()
    }

    @Test
    fun `GET cart returns body`() {
        every { cartService.getCart(userId) } returns emptyCart

        mockMvc
            .perform(get("/api/v1/cart").principal(auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItems").value(0))
    }

    @Test
    fun `POST items adds product`() {
        every { cartService.addItem(userId, any()) } returns emptyCart.copy(totalItems = 1)

        mockMvc
            .perform(
                post("/api/v1/cart/items")
                    .principal(auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"productId":"${UUID.randomUUID()}","quantity":2}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItems").value(1))

        verify(exactly = 1) { cartService.addItem(userId, any()) }
    }

    @Test
    fun `PUT item updates quantity`() {
        val productId = UUID.randomUUID()
        every { cartService.updateItem(userId, productId, any()) } returns emptyCart

        mockMvc
            .perform(
                put("/api/v1/cart/items/$productId")
                    .principal(auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"quantity":3}"""),
            ).andExpect(status().isOk)

        verify(exactly = 1) { cartService.updateItem(userId, productId, any()) }
    }

    @Test
    fun `DELETE item removes line`() {
        val productId = UUID.randomUUID()
        every { cartService.removeItem(userId, productId) } returns emptyCart

        mockMvc
            .perform(delete("/api/v1/cart/items/$productId").principal(auth))
            .andExpect(status().isOk)
    }

    @Test
    fun `DELETE cart clears`() {
        every { cartService.clear(userId) } returns emptyCart

        mockMvc
            .perform(delete("/api/v1/cart").principal(auth))
            .andExpect(status().isOk)

        verify(exactly = 1) { cartService.clear(userId) }
    }
}
