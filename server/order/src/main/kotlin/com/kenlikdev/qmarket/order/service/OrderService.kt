package com.kenlikdev.qmarket.order.service

import com.kenlikdev.qmarket.cart.repository.CartRepository
import com.kenlikdev.qmarket.catalog.api.ProductCatalog
import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.NotFoundException
import com.kenlikdev.qmarket.identity.repository.AddressRepository
import com.kenlikdev.qmarket.order.domain.Order
import com.kenlikdev.qmarket.order.domain.OrderIdempotencyKey
import com.kenlikdev.qmarket.order.domain.OrderItem
import com.kenlikdev.qmarket.order.domain.OrderStatus
import com.kenlikdev.qmarket.order.dto.CreateOrderRequest
import com.kenlikdev.qmarket.order.dto.OrderItemResponse
import com.kenlikdev.qmarket.order.dto.OrderResponse
import com.kenlikdev.qmarket.order.dto.PageResponse
import com.kenlikdev.qmarket.order.dto.UpdateOrderStatusRequest
import com.kenlikdev.qmarket.order.repository.OrderIdempotencyKeyRepository
import com.kenlikdev.qmarket.order.repository.OrderRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.UUID

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val cartRepository: CartRepository,
    private val productCatalog: ProductCatalog,
    private val addressRepository: AddressRepository,
    private val idempotencyKeyRepository: OrderIdempotencyKeyRepository,
    transactionManager: PlatformTransactionManager,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    /**
     * Checkout with optional Idempotency-Key.
     * Concurrent duplicate keys: one TX commits the order+key; the other hits UNIQUE(user_id,idem_key),
     * rolls back (stock restored), and this method re-reads the winner's order (not a 500).
     */
    fun createFromCart(
        userId: UUID,
        request: CreateOrderRequest,
        idempotencyKey: String? = null,
    ): OrderResponse {
        val normalizedKey = normalizeIdempotencyKey(idempotencyKey)
        if (normalizedKey != null) {
            loadIdempotentOrder(userId, normalizedKey)?.let { return it }
        }

        return try {
            requireNotNull(
                transactionTemplate.execute {
                    createFromCartInTransaction(userId, request, normalizedKey)
                },
            ) { "Checkout transaction returned no result" }
        } catch (ex: DataIntegrityViolationException) {
            // Only treat UNIQUE(user_id, idem_key) as an idempotency race; rethrow other DB errors.
            if (normalizedKey != null && isIdempotencyKeyConstraint(ex)) {
                loadIdempotentOrder(userId, normalizedKey)?.let { return it }
                throw BadRequestException("Concurrent checkout conflict — retry with the same Idempotency-Key")
            }
            throw ex
        }
    }

    private fun loadIdempotentOrder(
        userId: UUID,
        normalizedKey: String,
    ): OrderResponse? {
        val existing = idempotencyKeyRepository.findByUserIdAndKey(userId, normalizedKey) ?: return null
        val order =
            orderRepository.findById(existing.orderId).orElseThrow {
                NotFoundException("Order not found for idempotency key")
            }
        return toResponse(order)
    }

    private fun createFromCartInTransaction(
        userId: UUID,
        request: CreateOrderRequest,
        normalizedKey: String?,
    ): OrderResponse {
        if (normalizedKey != null) {
            loadIdempotentOrder(userId, normalizedKey)?.let { return it }
        }

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

        if (normalizedKey != null) {
            idempotencyKeyRepository.save(
                OrderIdempotencyKey(
                    userId = userId,
                    key = normalizedKey,
                    orderId = saved.id!!,
                ),
            )
        }

        cart.items.clear()
        cartRepository.save(cart)

        return toResponse(saved)
    }

    fun findIdempotentOrderId(
        userId: UUID,
        rawKey: String,
    ): UUID? {
        val key = normalizeIdempotencyKey(rawKey) ?: return null
        return idempotencyKeyRepository.findByUserIdAndKey(userId, key)?.orderId
    }

    private fun isIdempotencyKeyConstraint(ex: DataIntegrityViolationException): Boolean {
        val msg =
            buildString {
                append(ex.message.orEmpty())
                append(' ')
                append(ex.mostSpecificCause.message.orEmpty())
            }.lowercase()
        return "uq_order_idempotency" in msg || "order_idempotency_keys" in msg
    }

    private fun normalizeIdempotencyKey(raw: String?): String? {
        val key = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (key.length > 128) {
            throw BadRequestException("Idempotency-Key must be at most 128 characters")
        }
        return key
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

        // Snapshot items while session is open (LAZY collection)
        val lines = order.items.map { it.productId to it.quantity }
        val needsRestock = order.applyAdminStatus(request.status)
        if (needsRestock) {
            for ((productId, quantity) in lines) {
                productCatalog.increaseStock(productId, quantity)
            }
        }
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

        // Status transition first (fails fast if already cancelled / paid)
        order.cancel()
        for (item in order.items) {
            productCatalog.increaseStock(item.productId, item.quantity)
        }
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
