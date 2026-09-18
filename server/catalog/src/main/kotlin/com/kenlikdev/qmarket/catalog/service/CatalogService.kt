package com.kenlikdev.qmarket.catalog.service

import com.kenlikdev.qmarket.catalog.domain.Category
import com.kenlikdev.qmarket.catalog.domain.Product
import com.kenlikdev.qmarket.catalog.dto.CategoryResponse
import com.kenlikdev.qmarket.catalog.dto.CreateCategoryRequest
import com.kenlikdev.qmarket.catalog.dto.CreateProductRequest
import com.kenlikdev.qmarket.catalog.dto.PageResponse
import com.kenlikdev.qmarket.catalog.dto.ProductResponse
import com.kenlikdev.qmarket.catalog.dto.UpdateCategoryRequest
import com.kenlikdev.qmarket.catalog.dto.UpdateProductRequest
import com.kenlikdev.qmarket.catalog.repository.CategoryRepository
import com.kenlikdev.qmarket.catalog.repository.ProductRepository
import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.ConflictException
import com.kenlikdev.qmarket.common.exception.NotFoundException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class CatalogService(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
) {
    // ---------- Categories ----------

    @Transactional(readOnly = true)
    fun listCategories(activeOnly: Boolean = true): List<CategoryResponse> {
        val categories =
            if (activeOnly) {
                categoryRepository.findAllByActiveTrueOrderBySortOrderAsc()
            } else {
                categoryRepository.findAll(Sort.by("sortOrder"))
            }
        return categories.map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun getCategory(id: UUID): CategoryResponse =
        categoryRepository
            .findById(id)
            .orElseThrow { NotFoundException("Category $id not found") }
            .toResponse()

    @Transactional
    fun createCategory(request: CreateCategoryRequest): CategoryResponse {
        val slug = request.slug.trim().lowercase()
        if (categoryRepository.existsBySlug(slug)) {
            throw ConflictException("Category with slug '$slug' already exists")
        }
        val parent =
            request.parentId?.let {
                categoryRepository.findById(it).orElseThrow { NotFoundException("Parent category $it not found") }
            }
        val category =
            Category(
                name = request.name.trim(),
                slug = slug,
                description = request.description,
                parent = parent,
                sortOrder = request.sortOrder,
                active = request.active,
            )
        return categoryRepository.save(category).toResponse()
    }

    @Transactional
    fun updateCategory(
        id: UUID,
        request: UpdateCategoryRequest,
    ): CategoryResponse {
        val category =
            categoryRepository
                .findById(id)
                .orElseThrow { NotFoundException("Category $id not found") }

        request.name?.let { category.name = it.trim() }
        request.slug?.let {
            val newSlug = it.trim().lowercase()
            if (newSlug != category.slug && categoryRepository.existsBySlug(newSlug)) {
                throw ConflictException("Category with slug '$newSlug' already exists")
            }
            category.slug = newSlug
        }
        request.description?.let { category.description = it }
        request.sortOrder?.let { category.sortOrder = it }
        request.active?.let { category.active = it }
        request.parentId?.let { parentId ->
            category.parent = resolveParentCategory(category.id, parentId)
        }

        return categoryRepository.save(category).toResponse()
    }

    @Transactional
    fun deleteCategory(id: UUID) {
        if (!categoryRepository.existsById(id)) {
            throw NotFoundException("Category $id not found")
        }
        categoryRepository.deleteById(id)
    }

    // ---------- Products ----------

    @Transactional(readOnly = true)
    fun searchProducts(
        query: String? = null,
        categoryId: UUID? = null,
        activeOnly: Boolean = true,
        featuredOnly: Boolean = false,
        minPrice: BigDecimal? = null,
        maxPrice: BigDecimal? = null,
        sortBy: String = "createdAt",
        sortDir: String = "desc",
        page: Int = 0,
        size: Int = 20,
    ): PageResponse<ProductResponse> {
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw BadRequestException("minPrice must be less than or equal to maxPrice")
        }
        val direction =
            if (sortDir.equals("asc", ignoreCase = true)) {
                Sort.Direction.ASC
            } else {
                Sort.Direction.DESC
            }
        val sortProperty =
            when (sortBy.lowercase()) {
                "price" -> "price"
                "name" -> "name"
                "createdat", "created_at", "createdAt" -> "createdAt"
                else -> "createdAt"
            }
        val pageable =
            PageRequest.of(
                page.coerceAtLeast(0),
                size.coerceIn(1, 100),
                Sort.by(direction, sortProperty),
            )
        val result =
            productRepository.search(
                query,
                categoryId,
                activeOnly,
                featuredOnly,
                minPrice,
                maxPrice,
                pageable,
            )
        return PageResponse(
            content = result.content.map { it.toResponse() },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    @Transactional(readOnly = true)
    fun getProduct(
        id: UUID,
        requireActive: Boolean = true,
    ): ProductResponse {
        val product =
            productRepository
                .findById(id)
                .orElseThrow { NotFoundException("Product $id not found") }
        if (requireActive && !product.active) {
            throw NotFoundException("Product $id not found")
        }
        return product.toResponse()
    }

    @Transactional(readOnly = true)
    fun getProductBySlug(
        slug: String,
        requireActive: Boolean = true,
    ): ProductResponse {
        val normalizedSlug = slug.trim().lowercase()
        val product =
            productRepository
                .findBySlug(normalizedSlug)
                .orElseThrow { NotFoundException("Product with slug '$normalizedSlug' not found") }
        if (requireActive && !product.active) {
            throw NotFoundException("Product with slug '$slug' not found")
        }
        return product.toResponse()
    }

    @Transactional
    fun createProduct(request: CreateProductRequest): ProductResponse {
        val slug = request.slug.trim().lowercase()
        val sku = request.sku?.trim()?.takeIf { it.isNotEmpty() }
        if (productRepository.existsBySlug(slug)) {
            throw ConflictException("Product with slug '$slug' already exists")
        }
        if (sku != null && productRepository.existsBySku(sku)) {
            throw ConflictException("Product with SKU '$sku' already exists")
        }
        val category =
            request.categoryId?.let {
                categoryRepository.findById(it).orElseThrow { NotFoundException("Category $it not found") }
            }
        val product =
            Product(
                name = request.name.trim(),
                slug = slug,
                description = request.description,
                shortDescription = request.shortDescription,
                sku = sku,
                price = request.price,
                compareAtPrice = request.compareAtPrice,
                costPrice = request.costPrice,
                stockQuantity = request.stockQuantity,
                active = request.active,
                featured = request.featured,
                category = category,
            )
        return productRepository.save(product).toResponse()
    }

    @Transactional
    fun updateProduct(
        id: UUID,
        request: UpdateProductRequest,
    ): ProductResponse {
        val product =
            productRepository
                .findById(id)
                .orElseThrow { NotFoundException("Product $id not found") }

        request.name?.let { product.name = it.trim() }
        request.slug?.let {
            val newSlug = it.trim().lowercase()
            if (newSlug != product.slug && productRepository.existsBySlug(newSlug)) {
                throw ConflictException("Product with slug '$newSlug' already exists")
            }
            product.slug = newSlug
        }
        request.description?.let { product.description = it }
        request.shortDescription?.let { product.shortDescription = it }
        request.sku?.let {
            val newSku = it.trim().ifEmpty { null }
            if (newSku != null && newSku != product.sku && productRepository.existsBySku(newSku)) {
                throw ConflictException("Product with SKU '$newSku' already exists")
            }
            product.sku = newSku
        }
        request.price?.let { product.price = it }
        request.compareAtPrice?.let { product.compareAtPrice = it }
        request.costPrice?.let { product.costPrice = it }
        request.stockQuantity?.let { product.stockQuantity = it }
        request.active?.let { product.active = it }
        request.featured?.let { product.featured = it }
        request.categoryId?.let { catId ->
            product.category =
                categoryRepository
                    .findById(catId)
                    .orElseThrow { NotFoundException("Category $catId not found") }
        }

        return productRepository.save(product).toResponse()
    }

    private fun resolveParentCategory(
        categoryId: UUID?,
        parentId: UUID,
    ): Category {
        if (categoryId == parentId) {
            throw BadRequestException("A category cannot be its own parent")
        }

        val parent =
            categoryRepository
                .findById(parentId)
                .orElseThrow { NotFoundException("Parent category $parentId not found") }
        var current = parent
        val visited = mutableSetOf<UUID>()

        while (true) {
            val currentId =
                requireNotNull(current.id) {
                    "Category id is missing while validating hierarchy"
                }
            if (currentId == categoryId) {
                throw BadRequestException("Category hierarchy would contain a cycle")
            }
            if (!visited.add(currentId)) {
                throw BadRequestException("Category hierarchy already contains a cycle")
            }

            current = current.parent ?: break
        }

        return parent
    }

    /**
     * Soft-delete: marks product inactive so historical order_items can still restock
     * and snapshots remain consistent. Physical DELETE would break cancel/restock.
     */
    @Transactional
    fun deleteProduct(id: UUID) {
        val product =
            productRepository
                .findById(id)
                .orElseThrow { NotFoundException("Product $id not found") }
        if (product.active) {
            product.active = false
            productRepository.save(product)
        }
    }

    private fun Category.toResponse() =
        CategoryResponse(
            id = requireNotNull(id) { "Category id missing after persist" },
            name = name,
            slug = slug,
            description = description,
            parentId = parent?.id,
            sortOrder = sortOrder,
            active = active,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun Product.toResponse() =
        ProductResponse(
            id = requireNotNull(id) { "Product id missing after persist" },
            name = name,
            slug = slug,
            description = description,
            shortDescription = shortDescription,
            sku = sku,
            price = price,
            compareAtPrice = compareAtPrice,
            stockQuantity = stockQuantity,
            active = active,
            featured = featured,
            categoryId = category?.id,
            categoryName = category?.name,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
