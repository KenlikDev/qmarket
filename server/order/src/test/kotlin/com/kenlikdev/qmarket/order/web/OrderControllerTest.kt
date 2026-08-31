package com.kenlikdev.qmarket.order.web

import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.GlobalExceptionHandler
import com.kenlikdev.qmarket.common.exception.NotFoundException
import com.kenlikdev.qmarket.order.domain.OrderStatus
import com.kenlikdev.qmarket.order.dto.OrderItemResponse
import com.kenlikdev.qmarket.order.dto.OrderResponse
import com.kenlikdev.qmarket.order.dto.PageResponse
import com.kenlikdev.qmarket.order.service.OrderService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Controller slice for Order API (standalone MockMvc + MockK).
 * Authentication is supplied via request.principal (SecurityContext is not used in standalone).
 * @PreAuthorize is covered by integration tests.
 */
class OrderControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var orderService: OrderService

    private val userId = UUID.randomUUID()
    private val orderId = UUID.randomUUID()
    private val productId = UUID.randomUUID()

    private val auth = UsernamePasswordAuthenticationToken(userId, null, emptyList())

    private val sampleOrder =
        OrderResponse(
            id = orderId,
            userId = userId,
            status = OrderStatus.PENDING,
            totalAmount = BigDecimal("99.99"),
            shippingAddress = "Test Street 1",
            customerNote = "note",
            items =
                listOf(
                    OrderItemResponse(
                        productId = productId,
                        productName = "Headphones",
                        productSlug = "headphones",
                        unitPrice = BigDecimal("99.99"),
                        quantity = 1,
                        lineTotal = BigDecimal("99.99"),
                    ),
                ),
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

    @BeforeEach
    fun setUp() {
        orderService = mockk()
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(OrderController(orderService))
                .setControllerAdvice(GlobalExceptionHandler())
                .build()
    }

    @Test
    fun `POST create returns 201`() {
        every { orderService.createFromCart(userId, any(), any()) } returns sampleOrder
        every { orderService.findIdempotentReplay(userId, any(), any()) } returns null

        mockMvc
            .perform(
                post("/api/v1/orders")
                    .principal(auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "shippingAddress": "Test Street 1",
                          "customerNote": "note"
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(orderId.toString()))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.totalAmount").value(99.99))

        verify(exactly = 1) { orderService.createFromCart(userId, any(), any()) }
    }

    @Test
    fun `POST create with known Idempotency-Key returns 200 on replay`() {
        every { orderService.findIdempotentReplay(userId, any(), "replay-key") } returns sampleOrder

        mockMvc
            .perform(
                post("/api/v1/orders")
                    .principal(auth)
                    .header("Idempotency-Key", "replay-key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"shippingAddress":"Test Street 1"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(orderId.toString()))

        verify(exactly = 0) { orderService.createFromCart(any(), any(), any()) }
    }

    @Test
    fun `POST create with blank address returns 400`() {
        every { orderService.createFromCart(userId, any(), any()) } throws
            BadRequestException("Either shippingAddress or addressId is required")

        mockMvc
            .perform(
                post("/api/v1/orders")
                    .principal(auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"shippingAddress":""}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
    }

    @Test
    fun `POST create empty cart returns 400`() {
        every { orderService.createFromCart(userId, any(), any()) } throws
            BadRequestException("Cart is empty")

        mockMvc
            .perform(
                post("/api/v1/orders")
                    .principal(auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"shippingAddress":"Test Street 1"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
    }

    @Test
    fun `GET my orders returns page`() {
        every { orderService.listMyOrders(userId, 0, 20) } returns
            PageResponse(content = listOf(sampleOrder), page = 0, size = 20, totalElements = 1L, totalPages = 1)

        mockMvc
            .perform(
                get("/api/v1/orders")
                    .principal(auth)
                    .param("page", "0")
                    .param("size", "20"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(orderId.toString()))
            .andExpect(jsonPath("$.totalElements").value(1))

        verify(exactly = 1) { orderService.listMyOrders(userId, 0, 20) }
    }

    @Test
    fun `GET my order by id returns 200`() {
        every { orderService.getMyOrder(userId, orderId) } returns sampleOrder

        mockMvc
            .perform(get("/api/v1/orders/$orderId").principal(auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(orderId.toString()))
    }

    @Test
    fun `GET my order not found returns 404`() {
        every { orderService.getMyOrder(userId, orderId) } throws
            NotFoundException("Order not found")

        mockMvc
            .perform(get("/api/v1/orders/$orderId").principal(auth))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `POST cancel returns cancelled order`() {
        val cancelled = sampleOrder.copy(status = OrderStatus.CANCELLED)
        every { orderService.cancelMyOrder(userId, orderId) } returns cancelled

        mockMvc
            .perform(post("/api/v1/orders/$orderId/cancel").principal(auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELLED"))
    }

    @Test
    fun `GET admin all returns page`() {
        every { orderService.listAllOrders(0, 20) } returns
            PageResponse(content = listOf(sampleOrder), page = 0, size = 20, totalElements = 1L, totalPages = 1)

        mockMvc
            .perform(
                get("/api/v1/orders/admin/all")
                    .param("page", "0")
                    .param("size", "20"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(orderId.toString()))
    }

    @Test
    fun `PUT admin status returns updated order`() {
        val confirmed = sampleOrder.copy(status = OrderStatus.CONFIRMED)
        every { orderService.updateStatus(orderId, any()) } returns confirmed

        mockMvc
            .perform(
                put("/api/v1/orders/admin/$orderId/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"CONFIRMED"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
    }

    @Test
    fun `POST pay returns paid order`() {
        val orderId = UUID.randomUUID()
        val response =
            OrderResponse(
                id = orderId,
                userId = userId,
                status = OrderStatus.PAID,
                totalAmount = BigDecimal("10.00"),
                shippingAddress = "Addr",
                customerNote = null,
                items = emptyList(),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        every { orderService.payMock(userId, orderId) } returns response
        val auth = UsernamePasswordAuthenticationToken(userId, null, emptyList())

        mockMvc
            .perform(post("/api/v1/orders/$orderId/pay").principal(auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PAID"))
    }
}
