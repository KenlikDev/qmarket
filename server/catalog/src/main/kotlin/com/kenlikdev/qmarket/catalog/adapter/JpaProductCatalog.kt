package com.kenlikdev.qmarket.catalog.adapter

import com.kenlikdev.qmarket.catalog.api.ProductCatalog
import com.kenlikdev.qmarket.catalog.api.ProductInfo
import com.kenlikdev.qmarket.catalog.domain.Product
import com.kenlikdev.qmarket.catalog.repository.ProductRepository
import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.NotFoundException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class JpaProductCatalog(
    private val productRepository: ProductRepository,
) : ProductCatalog {
    @Transactional(readOnly = true)
    override fun findById(id: UUID): ProductInfo? = productRepository.findById(id).map { it.toInfo() }.orElse(null)

    @Transactional(readOnly = true)
    override fun requireActive(id: UUID): ProductInfo {
        val product =
            productRepository
                .findById(id)
                .orElseThrow { NotFoundException("Product not found: $id") }
        if (!product.active) {
            throw BadRequestException("Product is not available")
        }
        return product.toInfo()
    }

    @Transactional(readOnly = true)
    override fun findByIds(ids: Collection<UUID>): Map<UUID, ProductInfo> {
        if (ids.isEmpty()) return emptyMap()
        return productRepository
            .findAllById(ids)
            .mapNotNull { p -> p.id?.let { it to p.toInfo() } }
            .toMap()
    }

    @Transactional
    override fun decreaseStock(
        id: UUID,
        quantity: Int,
    ) {
        require(quantity > 0) { "quantity must be positive" }
        val product =
            productRepository
                .findById(id)
                .orElseThrow { NotFoundException("Product not found: $id") }
        if (product.stockQuantity < quantity) {
            throw BadRequestException(
                "Insufficient stock for product ${product.slug}: available ${product.stockQuantity}, requested $quantity",
            )
        }
        product.stockQuantity -= quantity
        productRepository.save(product)
    }

    @Transactional
    override fun increaseStock(
        id: UUID,
        quantity: Int,
    ) {
        require(quantity > 0) { "quantity must be positive" }
        val product =
            productRepository
                .findById(id)
                .orElseThrow { NotFoundException("Product not found: $id") }
        product.stockQuantity += quantity
        productRepository.save(product)
    }

    private fun Product.toInfo(): ProductInfo =
        ProductInfo(
            id = id ?: error("Product id is null"),
            name = name,
            slug = slug,
            price = price,
            stockQuantity = stockQuantity,
            active = active,
        )
}
