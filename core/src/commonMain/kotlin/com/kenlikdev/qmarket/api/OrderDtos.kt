package com.kenlikdev.qmarket.api

import kotlinx.serialization.Serializable

@Serializable
enum class OrderStatusDto {
    PENDING,
    CONFIRMED,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    ;

    fun nextAdminTargets(): List<OrderStatusDto> =
        when (this) {
            PENDING -> listOf(CONFIRMED, PAID, CANCELLED)
            CONFIRMED -> listOf(PAID, CANCELLED)
            PAID -> listOf(SHIPPED)
            SHIPPED -> listOf(DELIVERED)
            DELIVERED, CANCELLED -> emptyList()
        }
}

@Serializable
data class CreateOrderRequestDto(
    val shippingAddress: String? = null,
    val addressId: String? = null,
    val customerNote: String? = null,
)

@Serializable
data class UpdateOrderStatusRequestDto(
    val status: OrderStatusDto,
)

@Serializable
data class OrderItemDto(
    val productId: String,
    val productName: String,
    val productSlug: String,
    val unitPrice: String,
    val quantity: Int,
    val lineTotal: String,
)

@Serializable
data class OrderDto(
    val id: String,
    val userId: String,
    val status: OrderStatusDto,
    val totalAmount: String,
    val shippingAddress: String? = null,
    val customerNote: String? = null,
    val items: List<OrderItemDto> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
