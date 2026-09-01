package com.kenlikdev.qmarket.catalog.web

import com.kenlikdev.qmarket.catalog.dto.CategoryResponse
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class CategoryControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var catalogService: CatalogService

    private val categoryId = UUID.randomUUID()
    private val sample =
        CategoryResponse(
            id = categoryId,
            name = "Electronics",
            slug = "electronics",
            description = null,
            parentId = null,
            sortOrder = 0,
            active = true,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    @BeforeEach
    fun setUp() {
        catalogService = mockk()
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(CategoryController(catalogService))
                .setControllerAdvice(GlobalExceptionHandler())
                .build()
    }

    @Test
    fun `GET categories returns list`() {
        every { catalogService.listCategories(activeOnly = true) } returns listOf(sample)

        mockMvc
            .perform(get("/api/v1/categories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].slug").value("electronics"))
    }

    @Test
    fun `GET category by id returns body`() {
        every { catalogService.getCategory(categoryId) } returns sample

        mockMvc
            .perform(get("/api/v1/categories/$categoryId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Electronics"))
    }

    @Test
    fun `GET category not found returns 404`() {
        every { catalogService.getCategory(categoryId) } throws NotFoundException("missing")

        mockMvc
            .perform(get("/api/v1/categories/$categoryId"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST create returns 201`() {
        every { catalogService.createCategory(any()) } returns sample

        mockMvc
            .perform(
                post("/api/v1/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Electronics","slug":"electronics"}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(categoryId.toString()))

        verify(exactly = 1) { catalogService.createCategory(any()) }
    }

    @Test
    fun `PUT update returns category`() {
        every { catalogService.updateCategory(categoryId, any()) } returns sample.copy(name = "Updated")

        mockMvc
            .perform(
                put("/api/v1/categories/$categoryId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Updated"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Updated"))
    }

    @Test
    fun `DELETE returns 204`() {
        every { catalogService.deleteCategory(categoryId) } returns Unit

        mockMvc
            .perform(delete("/api/v1/categories/$categoryId"))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { catalogService.deleteCategory(categoryId) }
    }
}
