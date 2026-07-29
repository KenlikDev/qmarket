package com.kenlikdev.qmarket.cart.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class AddCartItemRequest(
    @field:NotNull
    val productId: UUID,

    @field:Min(1)
    val quantity: Int = 1,
)

data class UpdateCartItemRequest(
    @field:Min(1)
    val quantity: Int,
)

data class CartItemResponse(
    val productId: UUID,
    val productName: String,
    val productSlug: String,
    val unitPrice: BigDecimal,
    val quantity: Int,
    val lineTotal: BigDecimal,
    val stockQuantity: Int,
)

data class CartResponse(
    val id: UUID,
    val userId: UUID,
    val items: List<CartItemResponse>,
    val totalItems: Int,
    val totalPrice: BigDecimal,
    val updatedAt: Instant,
)
