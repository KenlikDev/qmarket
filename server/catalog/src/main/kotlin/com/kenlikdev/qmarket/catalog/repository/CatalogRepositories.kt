package com.kenlikdev.qmarket.catalog.repository

import com.kenlikdev.qmarket.catalog.domain.Category
import com.kenlikdev.qmarket.catalog.domain.Product
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

interface CategoryRepository : JpaRepository<Category, UUID> {
    fun findBySlug(slug: String): Optional<Category>

    fun existsBySlug(slug: String): Boolean

    fun findAllByActiveTrueOrderBySortOrderAscIdAsc(): List<Category>
}

interface ProductRepository : JpaRepository<Product, UUID> {
    fun findBySlug(slug: String): Optional<Product>

    fun existsBySlug(slug: String): Boolean

    fun existsBySku(sku: String): Boolean

    @Query(
        """
        SELECT p FROM Product p
        WHERE (:activeOnly = false OR p.active = true)
          AND (:categoryId IS NULL OR p.category.id = :categoryId)
          AND (:featuredOnly = false OR p.featured = true)
          AND (:minPrice IS NULL OR p.price >= :minPrice)
          AND (:maxPrice IS NULL OR p.price <= :maxPrice)
          AND (
            :query IS NULL OR :query = '' OR
            LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        """,
    )
    fun search(
        @Param("query") query: String?,
        @Param("categoryId") categoryId: UUID?,
        @Param("activeOnly") activeOnly: Boolean,
        @Param("featuredOnly") featuredOnly: Boolean,
        @Param("minPrice") minPrice: BigDecimal?,
        @Param("maxPrice") maxPrice: BigDecimal?,
        pageable: Pageable,
    ): Page<Product>

    @Modifying(flushAutomatically = true)
    @Query(
        value = """
            UPDATE products
            SET stock_quantity = stock_quantity - :quantity,
                version = version + 1,
                updated_at = NOW()
            WHERE id = :id
              AND is_active = TRUE
              AND stock_quantity >= :quantity
            """,
        nativeQuery = true,
    )
    fun decreaseStockIfAvailable(
        @Param("id") id: UUID,
        @Param("quantity") quantity: Int,
    ): Int

    @Modifying(flushAutomatically = true)
    @Query(
        value = """
            UPDATE products
            SET stock_quantity = stock_quantity + :quantity,
                version = version + 1,
                updated_at = NOW()
            WHERE id = :id
            """,
        nativeQuery = true,
    )
    fun increaseStockBy(
        @Param("id") id: UUID,
        @Param("quantity") quantity: Int,
    ): Int
}
