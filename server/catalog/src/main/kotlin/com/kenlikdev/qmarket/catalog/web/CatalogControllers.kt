package com.kenlikdev.qmarket.catalog.web

import com.kenlikdev.qmarket.catalog.dto.*
import com.kenlikdev.qmarket.catalog.service.CatalogService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/categories")
class CategoryController(
    private val catalogService: CatalogService
) {

    @GetMapping
    fun list(@RequestParam(defaultValue = "true") activeOnly: Boolean): List<CategoryResponse> =
        catalogService.listCategories(activeOnly)

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): CategoryResponse =
        catalogService.getCategory(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    fun create(@Valid @RequestBody request: CreateCategoryRequest): CategoryResponse =
        catalogService.createCategory(request)

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateCategoryRequest
    ): CategoryResponse = catalogService.updateCategory(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    fun delete(@PathVariable id: UUID) = catalogService.deleteCategory(id)
}

@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val catalogService: CatalogService
) {

    @GetMapping
    fun search(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) categoryId: UUID?,
        @RequestParam(defaultValue = "true") activeOnly: Boolean,
        @RequestParam(defaultValue = "false") featuredOnly: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): PageResponse<ProductResponse> =
        catalogService.searchProducts(q, categoryId, activeOnly, featuredOnly, page, size)

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ProductResponse =
        catalogService.getProduct(id)

    @GetMapping("/slug/{slug}")
    fun getBySlug(@PathVariable slug: String): ProductResponse =
        catalogService.getProductBySlug(slug)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    fun create(@Valid @RequestBody request: CreateProductRequest): ProductResponse =
        catalogService.createProduct(request)

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateProductRequest
    ): ProductResponse = catalogService.updateProduct(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    fun delete(@PathVariable id: UUID) = catalogService.deleteProduct(id)
}
