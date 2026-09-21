package com.kenlikdev.qmarket.catalog.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// ---- Category ----

data class CreateCategoryRequest(
    @field:NotBlank @field:Size(max = 150)
    val name: String,
    @field:NotBlank @field:Size(max = 150)
    @field:Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug must contain lowercase letters, digits, and single hyphens only")
    val slug: String,
    val description: String? = null,
    val parentId: UUID? = null,
    val sortOrder: Int = 0,
    val active: Boolean = true,
)

data class UpdateCategoryRequest(
    @field:Size(max = 150)
    val name: String? = null,
    @field:Size(max = 150)
    @field:Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug must contain lowercase letters, digits, and single hyphens only")
    val slug: String? = null,
    @field:Size(max = 255)
    val description: String? = null,
    val parentId: UUID? = null,
    val sortOrder: Int? = null,
    val active: Boolean? = null,
)

data class CategoryResponse(
    val id: UUID,
    val name: String,
    val slug: String,
    val description: String?,
    val parentId: UUID?,
    val sortOrder: Int,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

// ---- Product ----

data class CreateProductRequest(
    @field:NotBlank @field:Size(max = 255)
    val name: String,
    @field:NotBlank @field:Size(max = 255)
    @field:Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug must contain lowercase letters, digits, and single hyphens only")
    val slug: String,
    @field:Size(max = 255)
    val description: String? = null,
    @field:Size(max = 500)
    val shortDescription: String? = null,
    @field:Size(max = 100)
    val sku: String? = null,
    @field:NotNull @field:DecimalMin("0.0") @field:Digits(integer = 10, fraction = 2)
    val price: BigDecimal,
    @field:DecimalMin("0.0") @field:Digits(integer = 10, fraction = 2)
    val compareAtPrice: BigDecimal? = null,
    @field:DecimalMin("0.0") @field:Digits(integer = 10, fraction = 2)
    val costPrice: BigDecimal? = null,
    @field:Min(0)
    val stockQuantity: Int = 0,
    val active: Boolean = true,
    val featured: Boolean = false,
    val categoryId: UUID? = null,
)

data class UpdateProductRequest(
    @field:Size(max = 255)
    val name: String? = null,
    @field:Size(max = 255)
    @field:Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug must contain lowercase letters, digits, and single hyphens only")
    val slug: String? = null,
    @field:Size(max = 255)
    val description: String? = null,
    @field:Size(max = 500)
    val shortDescription: String? = null,
    @field:Size(max = 100)
    val sku: String? = null,
    @field:DecimalMin("0.0") @field:Digits(integer = 10, fraction = 2)
    val price: BigDecimal? = null,
    @field:DecimalMin("0.0") @field:Digits(integer = 10, fraction = 2)
    val compareAtPrice: BigDecimal? = null,
    @field:DecimalMin("0.0") @field:Digits(integer = 10, fraction = 2)
    val costPrice: BigDecimal? = null,
    @field:Min(0)
    val stockQuantity: Int? = null,
    val active: Boolean? = null,
    val featured: Boolean? = null,
    val categoryId: UUID? = null,
)

data class ProductResponse(
    val id: UUID,
    val name: String,
    val slug: String,
    val description: String?,
    val shortDescription: String?,
    val sku: String?,
    val price: BigDecimal,
    val compareAtPrice: BigDecimal?,
    val stockQuantity: Int,
    val active: Boolean,
    val featured: Boolean,
    val categoryId: UUID?,
    val categoryName: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
