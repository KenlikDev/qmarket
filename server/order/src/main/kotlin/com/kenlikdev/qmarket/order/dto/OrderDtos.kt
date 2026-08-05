package com.kenlikdev.qmarket.order.dto

import com.kenlikdev.qmarket.order.domain.OrderStatus
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Provide either [shippingAddress] (free-form) or [addressId] from the user's address book.
 * If both are set, [addressId] wins.
 */
data class CreateOrderRequest(
    @field:Size(max = 500)
    val shippingAddress: String? = null,
    val addressId: UUID? = null,
    @field:Size(max = 1000)
    val customerNote: String? = null,
)

data class UpdateOrderStatusRequest(
    val status: OrderStatus,
)

data class OrderItemResponse(
    val productId: UUID,
    val productName: String,
    val productSlug: String,
    val unitPrice: BigDecimal,
    val quantity: Int,
    val lineTotal: BigDecimal,
)

data class OrderResponse(
    val id: UUID,
    val userId: UUID,
    val status: OrderStatus,
    val totalAmount: BigDecimal,
    val shippingAddress: String?,
    val customerNote: String?,
    val items: List<OrderItemResponse>,
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
