package com.kenlikdev.qmarket.order.dto

import com.kenlikdev.qmarket.order.domain.OrderStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateOrderRequest(
    @field:NotBlank
    @field:Size(max = 500)
    val shippingAddress: String,
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
