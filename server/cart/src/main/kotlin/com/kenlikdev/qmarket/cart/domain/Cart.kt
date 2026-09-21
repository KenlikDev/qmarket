package com.kenlikdev.qmarket.cart.domain

import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.NotFoundException
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Cart aggregate: holds invariants for line quantities and membership.
 * Stock availability is checked against values supplied by the application
 * (from [com.kenlikdev.qmarket.catalog.api.ProductCatalog]) — cart does not
 * load catalog itself.
 */
@Entity
@Table(name = "carts")
class Cart(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(name = "user_id", nullable = false, unique = true)
    var userId: UUID = UUID.randomUUID(),
    @OneToMany(
        mappedBy = "cart",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY,
    )
    var items: MutableList<CartItem> = mutableListOf(),
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }

    /** Add quantity for [productId], merging with existing line if present. */
    fun addItem(
        productId: UUID,
        quantity: Int,
        availableStock: Int,
        productSlug: String,
    ) {
        require(quantity >= 1) { "quantity must be at least 1" }
        val existing = items.find { it.productId == productId }
        val newQty = (existing?.quantity ?: 0) + quantity
        ensureStock(productSlug, availableStock, newQty)
        if (existing != null) {
            existing.quantity = newQty
        } else {
            items.add(
                CartItem(
                    cart = this,
                    productId = productId,
                    quantity = quantity,
                ),
            )
        }
    }

    /** Set absolute quantity for an existing line. */
    fun changeQuantity(
        productId: UUID,
        quantity: Int,
        availableStock: Int,
        productSlug: String,
    ) {
        require(quantity >= 1) { "quantity must be at least 1" }
        val item =
            items.find { it.productId == productId }
                ?: throw NotFoundException("Product not in cart")
        ensureStock(productSlug, availableStock, quantity)
        item.quantity = quantity
    }

    fun removeItem(productId: UUID) {
        val removed = items.removeIf { it.productId == productId }
        if (!removed) {
            throw NotFoundException("Product not in cart")
        }
    }

    fun clearItems() {
        items.clear()
    }

    fun isEmpty(): Boolean = items.isEmpty()

    private fun ensureStock(
        productSlug: String,
        availableStock: Int,
        requested: Int,
    ) {
        if (requested > availableStock) {
            throw BadRequestException(
                "Insufficient stock for product $productSlug: available $availableStock, requested $requested",
            )
        }
    }
}

@Entity
@Table(name = "cart_items")
class CartItem(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    var cart: Cart? = null,
    @Column(name = "product_id", nullable = false)
    var productId: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var quantity: Int = 1,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}
