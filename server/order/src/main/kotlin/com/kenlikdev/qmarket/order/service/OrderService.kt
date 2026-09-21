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
import com.kenlikdev.qmarket.order.dto.OrderResponse
import com.kenlikdev.qmarket.order.dto.PageResponse
import com.kenlikdev.qmarket.order.dto.PaymentSessionResponse
import com.kenlikdev.qmarket.order.dto.UpdateOrderStatusRequest
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
    private val notificationService: NotificationService,
    private val orderPaymentService: OrderPaymentService,
    private val idempotency: OrderIdempotencySupport,
    transactionManager: PlatformTransactionManager,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    /**
     * Checkout with optional Idempotency-Key (Stripe-style).
     *
     * Concurrent same-key callers are serialized with PostgreSQL `pg_advisory_xact_lock`
     * (transaction-scoped via EntityManager native query). Losers re-read the
     * winner order instead of double-charging stock or failing with "Cart is empty".
     */
    fun createFromCart(
        userId: UUID,
        request: CreateOrderRequest,
        idempotencyKey: String? = null,
    ): OrderResponse {
        val normalizedKey = idempotency.normalizeKey(idempotencyKey)
        if (normalizedKey != null) {
            idempotency.loadReplay(userId, normalizedKey, request)?.let { return it }
        }

        return try {
            requireNotNull(
                transactionTemplate.execute {
                    if (normalizedKey != null) {
                        idempotency.acquireLock(userId, normalizedKey)
                        idempotency.loadReplay(userId, normalizedKey, request)?.let { return@execute it }
                    }
                    createFromCartInTransaction(userId, request, normalizedKey)
                },
            ) { "Checkout transaction returned no result" }
        } catch (ex: DataIntegrityViolationException) {
            if (normalizedKey != null && idempotency.isKeyConstraint(ex)) {
                idempotency.loadReplay(userId, normalizedKey, request)?.let { return it }
                throw BadRequestException("Concurrent checkout conflict — retry with the same Idempotency-Key")
            }
            throw ex
        }
    }

    private fun createFromCartInTransaction(
        userId: UUID,
        request: CreateOrderRequest,
        normalizedKey: String?,
    ): OrderResponse {
        if (normalizedKey != null) {
            idempotency.loadReplay(userId, normalizedKey, request)?.let { return it }
        }

        cartRepository.insertIfMissing(userId)
        val cart =
            cartRepository.findByUserIdForUpdate(userId)
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
            idempotency.saveKey(
                userId = userId,
                normalizedKey = normalizedKey,
                orderId = requireNotNull(saved.id) { "Order id missing after persist" },
                request = request,
            )
        }

        cart.items.clear()
        cartRepository.save(cart)

        notificationService.notifyOrderEvent(
            userId = userId,
            type = "ORDER_PLACED",
            title = "Order placed",
            body = "Order ${saved.id} for ${saved.totalAmount} is pending payment.",
            orderId = saved.id,
        )

        return OrderMapper.toResponse(saved)
    }

    fun findIdempotentReplay(
        userId: UUID,
        request: CreateOrderRequest,
        rawKey: String,
    ): OrderResponse? = idempotency.findReplay(userId, request, rawKey)

    @Transactional(readOnly = true)
    fun getMyOrder(
        userId: UUID,
        orderId: UUID,
    ): OrderResponse {
        val order =
            orderRepository
                .findByIdAndUserId(orderId, userId) ?: throw NotFoundException("Order not found")
        return OrderMapper.toResponse(order)
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
        val result = orderRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable)
        return PageResponse(
            content = result.content.map { OrderMapper.toResponse(it) },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    @Transactional(readOnly = true)
    fun getOrderAdmin(orderId: UUID): OrderResponse {
        val order =
            orderRepository.findByIdForUpdate(orderId)
                ?: throw NotFoundException("Order not found")
        return OrderMapper.toResponse(order)
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
                Sort.by(Sort.Direction.DESC, "createdAt", "id"),
            )
        val result = orderRepository.findAll(pageable)
        return PageResponse(
            content = result.content.map { OrderMapper.toResponse(it) },
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
        val previous = order.status
        val needsRestock = order.applyAdminStatus(request.status)
        if (needsRestock) {
            for ((productId, quantity) in lines) {
                productCatalog.increaseStock(productId, quantity)
            }
        }
        val saved = orderRepository.save(order)
        if (previous != saved.status) {
            notificationService.notifyOrderEvent(
                userId = saved.userId,
                type = "ORDER_STATUS_${saved.status.name}",
                title = "Order status updated",
                body = "Order ${saved.id} is now ${saved.status.name}.",
                orderId = saved.id,
            )
        }
        return OrderMapper.toResponse(saved)
    }

    @Transactional
    fun cancelMyOrder(
        userId: UUID,
        orderId: UUID,
    ): OrderResponse {
        val order =
            orderRepository.findByIdForUpdate(orderId)
                ?.takeIf { it.userId == userId }
                ?: throw NotFoundException("Order not found")

        // Status transition first (fails fast if already cancelled / paid)
        order.cancel()
        for (item in order.items) {
            productCatalog.increaseStock(item.productId, item.quantity)
        }
        val saved = orderRepository.save(order)
        notificationService.notifyOrderEvent(
            userId = userId,
            type = "ORDER_CANCELLED",
            title = "Order cancelled",
            body = "Order ${saved.id} was cancelled. Stock restored where applicable.",
            orderId = saved.id,
        )
        return OrderMapper.toResponse(saved)
    }

    fun pay(
        userId: UUID,
        orderId: UUID,
    ): OrderResponse = orderPaymentService.pay(userId, orderId)

    fun createPaymentSession(
        userId: UUID,
        orderId: UUID,
    ): PaymentSessionResponse = orderPaymentService.createPaymentSession(userId, orderId)

    fun markPaidFromProvider(
        orderId: UUID,
        providerId: String,
        providerReference: String?,
        amountMinor: Long? = null,
        currency: String? = null,
    ): OrderResponse =
        orderPaymentService.markPaidFromProvider(
            orderId,
            providerId,
            providerReference,
            amountMinor,
            currency,
        )

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
}
