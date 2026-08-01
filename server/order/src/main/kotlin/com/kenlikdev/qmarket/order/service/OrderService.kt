package com.kenlikdev.qmarket.order.service

import com.kenlikdev.qmarket.cart.repository.CartRepository
import com.kenlikdev.qmarket.catalog.repository.ProductRepository
import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.NotFoundException
import com.kenlikdev.qmarket.order.domain.Order
import com.kenlikdev.qmarket.order.domain.OrderItem
import com.kenlikdev.qmarket.order.domain.OrderStatus
import com.kenlikdev.qmarket.order.dto.CreateOrderRequest
import com.kenlikdev.qmarket.order.dto.OrderItemResponse
import com.kenlikdev.qmarket.order.dto.OrderResponse
import com.kenlikdev.qmarket.order.dto.UpdateOrderStatusRequest
import com.kenlikdev.qmarket.order.repository.OrderRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val cartRepository: CartRepository,
    private val productRepository: ProductRepository,
) {
    @Transactional
    fun createFromCart(
        userId: UUID,
        request: CreateOrderRequest,
    ): OrderResponse {
        val cart =
            cartRepository
                .findByUserId(userId)
                .orElseThrow { BadRequestException("Cart is empty") }
        if (cart.items.isEmpty()) {
            throw BadRequestException("Cart is empty")
        }

        val order =
            Order(
                userId = userId,
                status = OrderStatus.PENDING,
                shippingAddress = request.shippingAddress.trim(),
                customerNote = request.customerNote?.trim()?.takeIf { it.isNotEmpty() },
            )

        var total = BigDecimal.ZERO
        for (cartItem in cart.items) {
            val product =
                productRepository
                    .findById(cartItem.productId)
                    .orElseThrow { NotFoundException("Product not found: ${cartItem.productId}") }
            if (!product.active) {
                throw BadRequestException("Product is not available: ${product.slug}")
            }
            if (cartItem.quantity > product.stockQuantity) {
                throw BadRequestException(
                    "Insufficient stock for ${product.slug}: available ${product.stockQuantity}",
                )
            }

            val lineTotal = product.price.multiply(BigDecimal(cartItem.quantity))
            total = total.add(lineTotal)

            product.stockQuantity -= cartItem.quantity
            productRepository.save(product)

            order.items.add(
                OrderItem(
                    order = order,
                    productId = product.id!!,
                    productName = product.name,
                    productSlug = product.slug,
                    unitPrice = product.price,
                    quantity = cartItem.quantity,
                    lineTotal = lineTotal,
                ),
            )
        }

        order.totalAmount = total
        val saved = orderRepository.save(order)

        cart.items.clear()
        cartRepository.save(cart)

        return toResponse(saved)
    }

    @Transactional(readOnly = true)
    fun getMyOrder(
        userId: UUID,
        orderId: UUID,
    ): OrderResponse {
        val order =
            orderRepository
                .findByIdAndUserId(orderId, userId)
                .orElseThrow { NotFoundException("Order not found") }
        return toResponse(order)
    }

    @Transactional(readOnly = true)
    fun listMyOrders(
        userId: UUID,
        page: Int,
        size: Int,
    ): Page<OrderResponse> {
        val pageable =
            PageRequest.of(
                page.coerceAtLeast(0),
                size.coerceIn(1, 100),
            )
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map { toResponse(it) }
    }

    @Transactional(readOnly = true)
    fun getOrderAdmin(orderId: UUID): OrderResponse {
        val order =
            orderRepository
                .findById(orderId)
                .orElseThrow { NotFoundException("Order not found") }
        return toResponse(order)
    }

    @Transactional(readOnly = true)
    fun listAllOrders(
        page: Int,
        size: Int,
    ): Page<OrderResponse> {
        val pageable =
            PageRequest.of(
                page.coerceAtLeast(0),
                size.coerceIn(1, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"),
            )
        return orderRepository.findAll(pageable).map { toResponse(it) }
    }

    @Transactional
    fun updateStatus(
        orderId: UUID,
        request: UpdateOrderStatusRequest,
    ): OrderResponse {
        val order =
            orderRepository
                .findById(orderId)
                .orElseThrow { NotFoundException("Order not found") }

        if (order.status == OrderStatus.CANCELLED && request.status != OrderStatus.CANCELLED) {
            throw BadRequestException("Cannot change status of a cancelled order")
        }
        if (order.status == OrderStatus.DELIVERED) {
            throw BadRequestException("Cannot change status of a delivered order")
        }

        order.status = request.status
        return toResponse(orderRepository.save(order))
    }

    @Transactional
    fun cancelMyOrder(
        userId: UUID,
        orderId: UUID,
    ): OrderResponse {
        val order =
            orderRepository
                .findByIdAndUserId(orderId, userId)
                .orElseThrow { NotFoundException("Order not found") }

        if (order.status != OrderStatus.PENDING && order.status != OrderStatus.CONFIRMED) {
            throw BadRequestException("Only PENDING or CONFIRMED orders can be cancelled")
        }

        // restore stock
        for (item in order.items) {
            productRepository.findById(item.productId).ifPresent { product ->
                product.stockQuantity += item.quantity
                productRepository.save(product)
            }
        }

        order.status = OrderStatus.CANCELLED
        return toResponse(orderRepository.save(order))
    }

    /**
     * Mock payment provider: marks order PAID if owned by user and status is PENDING or CONFIRMED.
     * Real PSP integration will replace this in v1.0+.
     */
    @Transactional
    fun payMock(
        userId: UUID,
        orderId: UUID,
    ): OrderResponse {
        val order =
            orderRepository
                .findByIdAndUserId(orderId, userId)
                .orElseThrow { NotFoundException("Order not found") }

        when (order.status) {
            OrderStatus.PENDING, OrderStatus.CONFIRMED -> {
                order.status = OrderStatus.PAID
            }
            OrderStatus.PAID -> throw BadRequestException("Order is already paid")
            OrderStatus.CANCELLED -> throw BadRequestException("Cannot pay a cancelled order")
            OrderStatus.SHIPPED, OrderStatus.DELIVERED ->
                throw BadRequestException("Order is already fulfilled")
        }

        return toResponse(orderRepository.save(order))
    }

    private fun toResponse(order: Order): OrderResponse =
        OrderResponse(
            id = order.id ?: error("Order id is null"),
            userId = order.userId,
            status = order.status,
            totalAmount = order.totalAmount,
            shippingAddress = order.shippingAddress,
            customerNote = order.customerNote,
            items =
                order.items.map {
                    OrderItemResponse(
                        productId = it.productId,
                        productName = it.productName,
                        productSlug = it.productSlug,
                        unitPrice = it.unitPrice,
                        quantity = it.quantity,
                        lineTotal = it.lineTotal,
                    )
                },
            createdAt = order.createdAt,
            updatedAt = order.updatedAt,
        )
}
