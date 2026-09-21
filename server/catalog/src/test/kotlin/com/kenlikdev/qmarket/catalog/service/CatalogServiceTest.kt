package com.kenlikdev.qmarket.catalog.service

import com.kenlikdev.qmarket.catalog.domain.Category
import com.kenlikdev.qmarket.catalog.domain.Product
import com.kenlikdev.qmarket.catalog.dto.CreateCategoryRequest
import com.kenlikdev.qmarket.catalog.dto.CreateProductRequest
import com.kenlikdev.qmarket.catalog.repository.CategoryRepository
import com.kenlikdev.qmarket.catalog.repository.ProductRepository
import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.ConflictException
import com.kenlikdev.qmarket.common.exception.NotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class CatalogServiceTest {
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
    fun `createCategory succeeds`() {
        val request = CreateCategoryRequest(name = "Electronics", slug = "electronics")
        val savedId = UUID.randomUUID()

        every { categoryRepository.existsBySlug("electronics") } returns false
        every { categoryRepository.save(any()) } answers {
            firstArg<Category>().also { it.id = savedId }
        }

        val result = catalogService.createCategory(request)

        assertEquals("Electronics", result.name)
        assertEquals("electronics", result.slug)
        assertEquals(savedId, result.id)
        verify(exactly = 1) { categoryRepository.save(any()) }
    }

    @Test
    fun `createCategory with duplicate slug throws ConflictException`() {
        every { categoryRepository.existsBySlug("electronics") } returns true

        assertThrows<ConflictException> {
            catalogService.createCategory(CreateCategoryRequest(name = "Electronics", slug = "electronics"))
        }
    }

    @Test
    fun `getCategory not found throws NotFoundException`() {
        val id = UUID.randomUUID()
        every { categoryRepository.findById(id) } returns Optional.empty()

        assertThrows<NotFoundException> {
            catalogService.getCategory(id)
        }
    }

    @Test
    fun `createProduct succeeds`() {
        val request =
            CreateProductRequest(
                name = "Headphones",
                slug = "headphones",
                price = BigDecimal("99.99"),
                stockQuantity = 10,
            )
        val savedId = UUID.randomUUID()

        every { productRepository.existsBySlug("headphones") } returns false
        every { productRepository.save(any()) } answers {
            firstArg<Product>().also { it.id = savedId }
        }

        val result = catalogService.createProduct(request)

        assertEquals("Headphones", result.name)
        assertEquals(BigDecimal("99.99"), result.price)
        assertEquals(savedId, result.id)
    }

    @Test
    fun `createProduct with duplicate slug throws ConflictException`() {
        every { productRepository.existsBySlug("headphones") } returns true

        assertThrows<ConflictException> {
            catalogService.createProduct(
                CreateProductRequest(name = "Headphones", slug = "headphones", price = BigDecimal("10")),
            )
        }
    }

    @Test
    fun `searchProducts returns page`() {
        val product =
            Product(
                id = UUID.randomUUID(),
                name = "Test",
                slug = "test",
                price = BigDecimal("10"),
                stockQuantity = 5,
            )
        every {
            productRepository.search(any(), any(), any(), any(), any(), any(), any())
        } returns PageImpl(listOf(product))

        val result = catalogService.searchProducts(query = "test")

        assertEquals(1, result.content.size)
        assertEquals("Test", result.content[0].name)
        assertEquals(1, result.totalElements)
    }


    @Test
    fun `updateCategory rejects self parent`() {
        val id = UUID.randomUUID()
        val category =
            Category(
                id = id,
                name = "Parent",
                slug = "parent",
            )
        every { categoryRepository.findById(id) } returns Optional.of(category)

        assertThrows<BadRequestException> {
            catalogService.updateCategory(
                id,
                com.kenlikdev.qmarket.catalog.dto.UpdateCategoryRequest(parentId = id),
            )
        }
    }

    @Test
    fun `getProductBySlug normalizes slug`() {
        val product =
            Product(
                id = UUID.randomUUID(),
                name = "Phone",
                slug = "phone",
                price = BigDecimal("10.00"),
            )
        every { productRepository.findBySlug("phone") } returns Optional.of(product)

        val result = catalogService.getProductBySlug(" PHONE ")

        assertEquals(product.id, result.id)
        verify(exactly = 1) { productRepository.findBySlug("phone") }
    }

    @Test
    fun `updateProduct trims and can clear sku`() {
        val id = UUID.randomUUID()
        val product =
            Product(
                id = id,
                name = "Phone",
                slug = "phone",
                sku = "OLD-SKU",
                price = BigDecimal("10.00"),
            )
        every { productRepository.findById(id) } returns Optional.of(product)
        every { productRepository.save(any()) } answers { firstArg() }

        catalogService.updateProduct(
            id,
            com.kenlikdev.qmarket.catalog.dto.UpdateProductRequest(sku = "   "),
        )

        assertEquals(null, product.sku)
    }

    @Test
    fun `deleteProduct not found throws NotFoundException`() {
        val id = UUID.randomUUID()
        every { productRepository.findById(id) } returns Optional.empty()

        assertThrows<NotFoundException> {
            catalogService.deleteProduct(id)
        }
    }

    @Test
    fun `deleteProduct soft-deletes active product`() {
        val id = UUID.randomUUID()
        val product =
            Product(
                id = id,
                name = "To Delete",
                slug = "to-delete",
                price = BigDecimal("10.00"),
                stockQuantity = 5,
                active = true,
            )
        every { productRepository.findById(id) } returns Optional.of(product)
        every { productRepository.save(any()) } answers { firstArg() }

        catalogService.deleteProduct(id)

        assertEquals(false, product.active)
        verify(exactly = 1) { productRepository.save(product) }
        verify(exactly = 0) { productRepository.deleteById(any()) }
    }

    @Test
    fun `searchProducts rejects minPrice greater than maxPrice`() {
        assertThrows<BadRequestException> {
            catalogService.searchProducts(
                minPrice = BigDecimal("100"),
                maxPrice = BigDecimal("10"),
            )
        }
    }
}
