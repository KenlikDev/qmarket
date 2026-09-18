package com.kenlikdev.qmarket.catalog.web

import com.kenlikdev.qmarket.catalog.dto.CategoryResponse
import com.kenlikdev.qmarket.catalog.dto.CreateCategoryRequest
import com.kenlikdev.qmarket.catalog.dto.CreateProductRequest
import com.kenlikdev.qmarket.catalog.dto.PageResponse
import com.kenlikdev.qmarket.catalog.dto.ProductResponse
import com.kenlikdev.qmarket.catalog.dto.UpdateCategoryRequest
import com.kenlikdev.qmarket.catalog.dto.UpdateProductRequest
import com.kenlikdev.qmarket.catalog.service.CatalogService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/api/v1/categories")
class CategoryController(
    private val catalogService: CatalogService,
) {
    @GetMapping
    fun list(): List<CategoryResponse> = catalogService.listCategories(activeOnly = true)

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    fun listAdmin(
        @RequestParam(defaultValue = "false") activeOnly: Boolean,
    ): List<CategoryResponse> = catalogService.listCategories(activeOnly)

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): CategoryResponse = catalogService.getCategory(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    fun create(
        @Valid @RequestBody request: CreateCategoryRequest,
    ): CategoryResponse = catalogService.createCategory(request)

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateCategoryRequest,
    ): CategoryResponse = catalogService.updateCategory(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    fun delete(
        @PathVariable id: UUID,
    ) = catalogService.deleteCategory(id)
}

@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val catalogService: CatalogService,
) {
    @GetMapping
    fun search(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) categoryId: UUID?,
        @RequestParam(defaultValue = "false") featuredOnly: Boolean,
        @RequestParam(required = false) minPrice: BigDecimal?,
        @RequestParam(required = false) maxPrice: BigDecimal?,
        @RequestParam(defaultValue = "createdAt") sortBy: String,
        @RequestParam(defaultValue = "desc") sortDir: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<ProductResponse> =
        catalogService.searchProducts(
            query = q,
            categoryId = categoryId,
            activeOnly = true,
            featuredOnly = featuredOnly,
            minPrice = minPrice,
            maxPrice = maxPrice,
            sortBy = sortBy,
            sortDir = sortDir,
            page = page,
            size = size,
        )

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    fun searchAdmin(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) categoryId: UUID?,
        @RequestParam(defaultValue = "false") featuredOnly: Boolean,
        @RequestParam(required = false) minPrice: BigDecimal?,
        @RequestParam(required = false) maxPrice: BigDecimal?,
        @RequestParam(defaultValue = "createdAt") sortBy: String,
        @RequestParam(defaultValue = "desc") sortDir: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<ProductResponse> =
        catalogService.searchProducts(
            query = q,
            categoryId = categoryId,
            activeOnly = false,
            featuredOnly = featuredOnly,
            minPrice = minPrice,
            maxPrice = maxPrice,
            sortBy = sortBy,
            sortDir = sortDir,
            page = page,
            size = size,
        )

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): ProductResponse = catalogService.getProduct(id)

    @GetMapping("/slug/{slug}")
    fun getBySlug(
        @PathVariable slug: String,
    ): ProductResponse = catalogService.getProductBySlug(slug)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    fun create(
        @Valid @RequestBody request: CreateProductRequest,
    ): ProductResponse = catalogService.createProduct(request)

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateProductRequest,
    ): ProductResponse = catalogService.updateProduct(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    fun delete(
        @PathVariable id: UUID,
    ) = catalogService.deleteProduct(id)
}
