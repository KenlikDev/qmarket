package com.kenlikdev.qmarket.catalog.service

import com.kenlikdev.qmarket.catalog.domain.Category
import com.kenlikdev.qmarket.catalog.domain.Product
import com.kenlikdev.qmarket.catalog.dto.UpdateCategoryRequest
import com.kenlikdev.qmarket.catalog.dto.UpdateProductRequest
import com.kenlikdev.qmarket.catalog.repository.CategoryRepository
import com.kenlikdev.qmarket.catalog.repository.ProductRepository
import com.kenlikdev.qmarket.common.exception.ConflictException
import com.kenlikdev.qmarket.common.exception.NotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.Sort
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

/**
 * Additional CatalogService paths not covered by the original suite (update/delete/list branches).
 */
class CatalogServiceCoverageTest {
    private lateinit var productRepository: ProductRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var catalogService: CatalogService

    @BeforeEach
    fun setUp() {
        productRepository = mockk()
        categoryRepository = mockk()
        catalogService = CatalogService(productRepository, categoryRepository)
    }

    @Test
    fun `listCategories activeOnly uses active query`() {
        every { categoryRepository.findAllByActiveTrueOrderBySortOrderAsc() } returns emptyList()
        catalogService.listCategories(activeOnly = true)
        verify(exactly = 1) { categoryRepository.findAllByActiveTrueOrderBySortOrderAsc() }
        verify(exactly = 0) { categoryRepository.findAll(any<Sort>()) }
    }

    @Test
    fun `listCategories all uses findAll sorted`() {
        every { categoryRepository.findAll(any<Sort>()) } returns emptyList()
        catalogService.listCategories(activeOnly = false)
        verify(exactly = 1) { categoryRepository.findAll(any<Sort>()) }
    }

    @Test
    fun `updateCategory renames and checks slug conflict`() {
        val id = UUID.randomUUID()
        val category = Category(id = id, name = "Old", slug = "old", active = true)
        every { categoryRepository.findById(id) } returns Optional.of(category)
        every { categoryRepository.existsBySlug("new-slug") } returns false
        every { categoryRepository.save(any()) } answers { firstArg() }

        val result =
            catalogService.updateCategory(
                id,
                UpdateCategoryRequest(name = "New", slug = "new-slug"),
            )
        assertEquals("New", result.name)
        assertEquals("new-slug", result.slug)
    }

    @Test
    fun `updateCategory duplicate slug throws ConflictException`() {
        val id = UUID.randomUUID()
        val category = Category(id = id, name = "Old", slug = "old", active = true)
        every { categoryRepository.findById(id) } returns Optional.of(category)
        every { categoryRepository.existsBySlug("taken") } returns true

        assertThrows<ConflictException> {
            catalogService.updateCategory(id, UpdateCategoryRequest(slug = "taken"))
        }
    }

    @Test
    fun `deleteCategory removes entity`() {
        val id = UUID.randomUUID()
        every { categoryRepository.existsById(id) } returns true
        every { categoryRepository.deleteById(id) } returns Unit

        catalogService.deleteCategory(id)
        verify(exactly = 1) { categoryRepository.deleteById(id) }
    }

    @Test
    fun `deleteCategory not found throws`() {
        val id = UUID.randomUUID()
        every { categoryRepository.existsById(id) } returns false
        assertThrows<NotFoundException> { catalogService.deleteCategory(id) }
    }

    @Test
    fun `updateProduct applies fields and category`() {
        val id = UUID.randomUUID()
        val catId = UUID.randomUUID()
        val product =
            Product(
                id = id,
                name = "P",
                slug = "p",
                price = BigDecimal("1.00"),
                stockQuantity = 1,
            )
        val category = Category(id = catId, name = "C", slug = "c")
        every { productRepository.findById(id) } returns Optional.of(product)
        every { categoryRepository.findById(catId) } returns Optional.of(category)
        every { productRepository.save(any()) } answers { firstArg() }

        val result =
            catalogService.updateProduct(
                id,
                UpdateProductRequest(
                    name = "P2",
                    price = BigDecimal("2.00"),
                    stockQuantity = 5,
                    categoryId = catId,
                ),
            )
        assertEquals("P2", result.name)
        assertEquals(BigDecimal("2.00"), result.price)
        assertEquals(5, result.stockQuantity)
        assertEquals(catId, result.categoryId)
    }

    @Test
    fun `deleteProduct soft-deletes active product`() {
        val id = UUID.randomUUID()
        val product =
            Product(
                id = id,
                name = "P",
                slug = "p",
                price = BigDecimal.ONE,
                stockQuantity = 1,
                active = true,
            )
        every { productRepository.findById(id) } returns Optional.of(product)
        every { productRepository.save(any()) } answers { firstArg() }

        catalogService.deleteProduct(id)
        assertFalse(product.active)
        verify(exactly = 1) { productRepository.save(product) }
    }

    @Test
    fun `deleteProduct already inactive does not save`() {
        val id = UUID.randomUUID()
        val product =
            Product(
                id = id,
                name = "P",
                slug = "p",
                price = BigDecimal.ONE,
                stockQuantity = 1,
                active = false,
            )
        every { productRepository.findById(id) } returns Optional.of(product)

        catalogService.deleteProduct(id)
        verify(exactly = 0) { productRepository.save(any()) }
    }
}
