package com.kenlikdev.qmarket.api

import kotlinx.serialization.Serializable

@Serializable
data class AddCartItemRequestDto(
    val productId: String,
    val quantity: Int = 1,
)

@Serializable
data class UpdateCartItemRequestDto(
    val quantity: Int,
)

@Serializable
data class CartItemDto(
    val productId: String,
    val productName: String,
    val productSlug: String,
    val unitPrice: String,
    val quantity: Int,
    val lineTotal: String,
    val stockQuantity: Int,
)

@Serializable
data class CartDto(
    val id: String,
    val userId: String,
    val items: List<CartItemDto> = emptyList(),
    val totalItems: Int = 0,
    val totalPrice: String = "0",
    val updatedAt: String? = null,
)
