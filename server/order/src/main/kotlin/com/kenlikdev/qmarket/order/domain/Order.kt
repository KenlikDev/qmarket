package com.kenlikdev.qmarket.order.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class OrderStatus {
    PENDING,
    CONFIRMED,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED,
}

@Entity
@Table(name = "orders")
class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "user_id", nullable = false)
    var userId: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: OrderStatus = OrderStatus.PENDING,

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    var totalAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "shipping_address", length = 500)
    var shippingAddress: String? = null,

    @Column(name = "customer_note", length = 1000)
    var customerNote: String? = null,

    @OneToMany(
        mappedBy = "order",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.EAGER,
    )
    var items: MutableList<OrderItem> = mutableListOf(),

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

@Entity
@Table(name = "order_items")
class OrderItem(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    var order: Order? = null,

    @Column(name = "product_id", nullable = false)
    var productId: UUID = UUID.randomUUID(),

    @Column(name = "product_name", nullable = false)
    var productName: String = "",

    @Column(name = "product_slug", nullable = false)
    var productSlug: String = "",

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    var unitPrice: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false)
    var quantity: Int = 1,

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    var lineTotal: BigDecimal = BigDecimal.ZERO,
)
