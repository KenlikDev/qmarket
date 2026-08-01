package com.kenlikdev.qmarket.catalog.api

import java.math.BigDecimal
import java.util.UUID

/**
 * Immutable product snapshot for other modules (cart, order, …).
 * Does not expose JPA / catalog internals.
 */
data class ProductInfo(
    val id: UUID,
    val name: String,
    val slug: String,
    val price: BigDecimal,
    val stockQuantity: Int,
    val active: Boolean,
)
