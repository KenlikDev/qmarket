package com.kenlikdev.qmarket.catalog.web

import com.kenlikdev.qmarket.catalog.dto.PageResponse
import com.kenlikdev.qmarket.catalog.dto.ProductResponse
import com.kenlikdev.qmarket.catalog.service.CatalogService
import com.kenlikdev.qmarket.common.exception.GlobalExceptionHandler
import com.kenlikdev.qmarket.common.exception.NotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class ProductControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var catalogService: CatalogService

    private val productId = UUID.randomUUID()
    private val sampleProduct = ProductResponse(
        id = productId,
        name = "Headphones",
        slug = "headphones",
        description = null,
        shortDescription = null,
        sku = "SKU-1",
        price = BigDecimal("99.99"),
        compareAtPrice = null,
        stockQuantity = 10,
        active = true,
        featured = false,
        categoryId = null,
        categoryName = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @BeforeEach
    fun setUp() {
        catalogService = mockk()
        mockMvc = MockMvcBuilders
            .standaloneSetup(ProductController(catalogService))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `GET products returns page`() {
        every {
            catalogService.searchProducts(any(), any(), any(), any(), any(), any())
        } returns PageResponse(
            content = listOf(sampleProduct),
            page = 0,
            size = 20,
            totalElements = 1,
            totalPages = 1
        )

        mockMvc.perform(get("/api/v1/products"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].name").value("Headphones"))
            .andExpect(jsonPath("$.totalElements").value(1))
    }

    @Test
    fun `GET product by id returns product`() {
        every { catalogService.getProduct(productId) } returns sampleProduct

        mockMvc.perform(get("/api/v1/products/$productId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.slug").value("headphones"))
            .andExpect(jsonPath("$.price").value(99.99))
    }

    @Test
    fun `GET product by id not found returns 404`() {
        val missing = UUID.randomUUID()
        every { catalogService.getProduct(missing) } throws NotFoundException("Product not found")

        mockMvc.perform(get("/api/v1/products/$missing"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `POST product returns 201`() {
        every { catalogService.createProduct(any()) } returns sampleProduct

        mockMvc.perform(
            post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Headphones",
                      "slug": "headphones",
                      "price": 99.99,
                      "stockQuantity": 10
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Headphones"))

        verify(exactly = 1) { catalogService.createProduct(any()) }
    }

    @Test
    fun `POST product with invalid body returns 400`() {
        mockMvc.perform(
            post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "", "slug": "", "price": -1}""")
        )
            .andExpect(status().isBadRequest)
    }
}
