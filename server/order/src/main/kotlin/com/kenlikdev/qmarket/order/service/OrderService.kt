package com.kenlikdev.qmarket.order.service

import com.kenlikdev.qmarket.cart.repository.CartRepository
import com.kenlikdev.qmarket.catalog.api.ProductCatalog
import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.NotFoundException
import com.kenlikdev.qmarket.identity.repository.AddressRepository
import com.kenlikdev.qmarket.order.domain.Order
import com.kenlikdev.qmarket.order.domain.OrderItem
import com.kenlikdev.qmarket.order.domain.OrderStatus
import com.kenlikdev.qmarket.order.dto.CreateOrderRequest
import com.kenlikdev.qmarket.order.dto.OrderItemResponse
import com.kenlikdev.qmarket.order.dto.OrderResponse
import com.kenlikdev.qmarket.order.dto.PageResponse
import com.kenlikdev.qmarket.order.dto.UpdateOrderStatusRequest
import com.kenlikdev.qmarket.order.repository.OrderRepository
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
    private val productCatalog: ProductCatalog,
    private val addressRepository: AddressRepository,
) {
    @Transactional
    fun createFromCart(
        userId: UUID,
        request: CreateOrderRequest,
    ): OrderResponse {
        val cart =
            cartRepository.findByUserId(userId)
                ?: throw BadRequestException("Cart is empty")
        if (cart.items.isEmpty()) {
            throw BadRequestException("Cart is empty")
        }

        val shipping = resolveShippingAddress(userId, request)

        val order =
            Order(
                userId = userId,
                status = OrderStatus.PENDING,
                shippingAddress = shipping,
                customerNote = request.customerNote?.trim()?.takeIf { it.isNotEmpty() },
            )

        var total = BigDecimal.ZERO
        for (cartItem in cart.items) {
            val product = productCatalog.requireActive(cartItem.productId)
            if (cartItem.quantity > product.stockQuantity) {
                throw BadRequestException(
                    "Insufficient stock for product ${product.slug}: available ${product.stockQuantity}, requested ${cartItem.quantity}",
                )
            }
            productCatalog.decreaseStock(cartItem.productId, cartItem.quantity)

            val lineTotal = product.price.multiply(BigDecimal(cartItem.quantity))
            total = total.add(lineTotal)
            order.items.add(
                OrderItem(
                    order = order,
                    productId = product.id,
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
                .findByIdAndUserId(orderId, userId) ?: throw NotFoundException("Order not found")
        return toResponse(order)
    }

    @Transactional(readOnly = true)
    fun listMyOrders(
        userId: UUID,
        page: Int,
        size: Int,
    ): PageResponse<OrderResponse> {
        val pageable =
            PageRequest.of(
                page.coerceAtLeast(0),
                size.coerceIn(1, 100),
            )
        val result = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        return PageResponse(
            content = result.content.map { toResponse(it) },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
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
    ): PageResponse<OrderResponse> {
        val pageable =
            PageRequest.of(
                page.coerceAtLeast(0),
                size.coerceIn(1, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"),
            )
        val result = orderRepository.findAll(pageable)
        return PageResponse(
            content = result.content.map { toResponse(it) },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
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

        order.applyAdminStatus(request.status)
        return toResponse(orderRepository.save(order))
    }

    @Transactional
    fun cancelMyOrder(
        userId: UUID,
        orderId: UUID,
    ): OrderResponse {
        val order =
            orderRepository
                .findByIdAndUserId(orderId, userId) ?: throw NotFoundException("Order not found")

        // restore stock before status change
        for (item in order.items) {
            productCatalog.increaseStock(item.productId, item.quantity)
        }
        order.cancel()
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
                .findByIdAndUserId(orderId, userId) ?: throw NotFoundException("Order not found")

        order.markPaid()

        return toResponse(orderRepository.save(order))
    }

    private fun resolveShippingAddress(
        userId: UUID,
        request: CreateOrderRequest,
    ): String {
        if (request.addressId != null) {
            val address =
                addressRepository
                    .findByIdAndUserId(request.addressId, userId) ?: throw NotFoundException("Address not found")
            return address.formatSingleLine()
        }
        val freeForm = request.shippingAddress?.trim().orEmpty()
        if (freeForm.isEmpty()) {
            throw BadRequestException("Either shippingAddress or addressId is required")
        }
        return freeForm
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
