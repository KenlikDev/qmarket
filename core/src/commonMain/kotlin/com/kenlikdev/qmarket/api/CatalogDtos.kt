package com.kenlikdev.qmarket.api

import kotlinx.serialization.Serializable

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val slug: String,
    val description: String? = null,
    val parentId: String? = null,
)

@Serializable
data class ProductDto(
    val id: String,
    val name: String,
    val slug: String,
    val description: String? = null,
    val shortDescription: String? = null,
    val sku: String? = null,
    /** Decimal amount as string for multiplatform safety (e.g. "19.99"). */
    val price: String,
    val compareAtPrice: String? = null,
    val stockQuantity: Int,
    val active: Boolean = true,
    val featured: Boolean = false,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/**
 * Unified page envelope.
 * Catalog uses [page]; raw Spring Data Page JSON uses [number] — both accepted.
 */
@Serializable
data class PageDto<T>(
    val content: List<T> = emptyList(),
    val page: Int? = null,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    /** Present when server returns org.springframework.data.domain.Page. */
    val number: Int? = null,
) {
    fun pageIndex(): Int = page ?: number ?: 0
}

@Serializable
data class CreateProductRequestDto(
    val name: String,
    val slug: String,
    val description: String? = null,
    val shortDescription: String? = null,
    val sku: String? = null,
    /** Decimal amount as string (e.g. "19.99"). */
    val price: String,
    val compareAtPrice: String? = null,
    val costPrice: String? = null,
    val stockQuantity: Int = 0,
    val active: Boolean = true,
    val featured: Boolean = false,
    val categoryId: String? = null,
)

@Serializable
data class UpdateProductRequestDto(
    val name: String? = null,
    val slug: String? = null,
    val description: String? = null,
    val shortDescription: String? = null,
    val sku: String? = null,
    val price: String? = null,
    val compareAtPrice: String? = null,
    val costPrice: String? = null,
    val stockQuantity: Int? = null,
    val active: Boolean? = null,
    val featured: Boolean? = null,
    val categoryId: String? = null,
)

