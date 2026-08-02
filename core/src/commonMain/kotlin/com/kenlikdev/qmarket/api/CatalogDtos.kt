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

@Serializable
data class PageDto<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
