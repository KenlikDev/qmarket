package com.kenlikdev.qmarket.catalog.adapter

import com.kenlikdev.qmarket.catalog.domain.Product
import com.kenlikdev.qmarket.catalog.repository.ProductRepository
import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.NotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class JpaProductCatalogTest {
    private lateinit var productRepository: ProductRepository
    private lateinit var catalog: JpaProductCatalog

    @BeforeEach
    fun setUp() {
        productRepository = mockk()
        catalog = JpaProductCatalog(productRepository)
    }

    private fun product(
        id: UUID = UUID.randomUUID(),
        active: Boolean = true,
        stock: Int = 10,
        slug: String = "item",
    ) = Product(
        id = id,
        name = "Item",
        slug = slug,
        price = BigDecimal("9.99"),
        stockQuantity = stock,
        active = active,
    )

    @Test
    fun `findById returns null when missing`() {
        val id = UUID.randomUUID()
        every { productRepository.findById(id) } returns Optional.empty()
        assertNull(catalog.findById(id))
    }

    @Test
    fun `findById maps ProductInfo`() {
        val id = UUID.randomUUID()
        every { productRepository.findById(id) } returns Optional.of(product(id = id, stock = 3))
        val info = requireNotNull(catalog.findById(id))
        assertEquals(id, info.id)
        assertEquals(3, info.stockQuantity)
        assertEquals("item", info.slug)
    }

    @Test
    fun `requireActive throws when not found`() {
        val id = UUID.randomUUID()
        every { productRepository.findById(id) } returns Optional.empty()
        assertThrows<NotFoundException> { catalog.requireActive(id) }
    }

    @Test
    fun `requireActive throws when inactive`() {
        val id = UUID.randomUUID()
        every { productRepository.findById(id) } returns Optional.of(product(id = id, active = false))
        assertThrows<BadRequestException> { catalog.requireActive(id) }
    }

    @Test
    fun `requireActive returns active product`() {
        val id = UUID.randomUUID()
        every { productRepository.findById(id) } returns Optional.of(product(id = id, active = true))
        val info = catalog.requireActive(id)
        assertEquals(id, info.id)
        assertEquals(true, info.active)
    }

    @Test
    fun `findByIds empty returns empty map`() {
        val result = catalog.findByIds(emptyList())
        assertEquals(0, result.size)
        assertTrue(result.isEmpty())
        verify(exactly = 0) { productRepository.findAllById(any()) }
    }

    @Test
    fun `findByIds maps found products`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        every { productRepository.findAllById(listOf(a, b)) } returns listOf(product(id = a), product(id = b, slug = "b"))
        val map = catalog.findByIds(listOf(a, b))
        assertEquals(2, map.size)
        assertEquals("item", map[a]?.slug)
        assertEquals("b", map[b]?.slug)
    }

    @Test
    fun `decreaseStock rejects non-positive quantity`() {
        assertThrows<IllegalArgumentException> {
            catalog.decreaseStock(UUID.randomUUID(), 0)
        }
    }

    @Test
    fun `decreaseStock succeeds when repository updates a row`() {
        val id = UUID.randomUUID()
        every { productRepository.decreaseStockIfAvailable(id, 2) } returns 1
        catalog.decreaseStock(id, 2)
        verify(exactly = 1) { productRepository.decreaseStockIfAvailable(id, 2) }
    }

    @Test
    fun `decreaseStock throws NotFound when no row and product missing`() {
        val id = UUID.randomUUID()
        every { productRepository.decreaseStockIfAvailable(id, 1) } returns 0
        every { productRepository.findById(id) } returns Optional.empty()
        assertThrows<NotFoundException> { catalog.decreaseStock(id, 1) }
    }

    @Test
    fun `decreaseStock throws BadRequest on insufficient stock`() {
        val id = UUID.randomUUID()
        every { productRepository.decreaseStockIfAvailable(id, 5) } returns 0
        every { productRepository.findById(id) } returns Optional.of(product(id = id, stock = 2, slug = "low-stock"))
        val ex = assertThrows<BadRequestException> { catalog.decreaseStock(id, 5) }
        assert(ex.message.orEmpty().contains("Insufficient stock"))
        assert(ex.message.orEmpty().contains("low-stock"))
    }

    @Test
    fun `increaseStock rejects non-positive quantity`() {
        assertThrows<IllegalArgumentException> {
            catalog.increaseStock(UUID.randomUUID(), -1)
        }
    }

    @Test
    fun `increaseStock succeeds when repository updates`() {
        val id = UUID.randomUUID()
        every { productRepository.increaseStockBy(id, 3) } returns 1
        catalog.increaseStock(id, 3)
        verify(exactly = 1) { productRepository.increaseStockBy(id, 3) }
    }

    @Test
    fun `increaseStock throws NotFound when no row updated`() {
        val id = UUID.randomUUID()
        every { productRepository.increaseStockBy(id, 1) } returns 0
        assertThrows<NotFoundException> { catalog.increaseStock(id, 1) }
    }
}
