package com.kenlikdev.qmarket.order.service

import com.kenlikdev.qmarket.cart.repository.CartRepository
import com.kenlikdev.qmarket.catalog.api.ProductCatalog
import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.ConflictException
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
import com.kenlikdev.qmarket.order.dto.PaymentSessionResponse
import com.kenlikdev.qmarket.order.dto.UpdateOrderStatusRequest
import com.kenlikdev.qmarket.order.payment.PaymentGateway
import com.kenlikdev.qmarket.order.payment.StripeApiClient
import com.kenlikdev.qmarket.order.payment.StripeApiException
import com.kenlikdev.qmarket.order.payment.StripePaymentGateway
import com.kenlikdev.qmarket.order.payment.StripeProperties
import com.kenlikdev.qmarket.order.repository.OrderIdempotencyKeyRepository
import com.kenlikdev.qmarket.order.repository.OrderRepository
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.ObjectProvider
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.UUID

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val cartRepository: CartRepository,
    private val productCatalog: ProductCatalog,
    private val addressRepository: AddressRepository,
    private val idempotencyKeyRepository: OrderIdempotencyKeyRepository,
    private val entityManager: EntityManager,
    private val notificationService: NotificationService,
    private val paymentGateway: PaymentGateway,
    private val stripeApiClient: ObjectProvider<StripeApiClient>,
    private val stripeProperties: ObjectProvider<StripeProperties>,
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
        val normalizedKey = normalizeIdempotencyKey(idempotencyKey)
        if (normalizedKey != null) {
            loadIdempotentOrder(userId, normalizedKey, request)?.let { return it }
        }

        return try {
            requireNotNull(
                transactionTemplate.execute {
                    if (normalizedKey != null) {
                        acquireIdempotencyLock(userId, normalizedKey)
                        loadIdempotentOrder(userId, normalizedKey, request)?.let { return@execute it }
                    }
                    createFromCartInTransaction(userId, request, normalizedKey)
                },
            ) { "Checkout transaction returned no result" }
        } catch (ex: DataIntegrityViolationException) {
            if (normalizedKey != null && isIdempotencyKeyConstraint(ex)) {
                loadIdempotentOrder(userId, normalizedKey, request)?.let { return it }
                throw BadRequestException("Concurrent checkout conflict — retry with the same Idempotency-Key")
            }
            throw ex
        }
    }

    private fun acquireIdempotencyLock(
        userId: UUID,
        normalizedKey: String,
    ) {
        val lockId = idempotencyLockId(userId, normalizedKey)
        entityManager
            .createNativeQuery("SELECT pg_advisory_xact_lock(:lockId)")
            .setParameter("lockId", lockId)
            .singleResult
    }

    private fun idempotencyLockId(
        userId: UUID,
        normalizedKey: String,
    ): Long {
        val a = userId.mostSignificantBits xor userId.leastSignificantBits
        val b = normalizedKey.hashCode().toLong()
        return a xor (b shl 32) xor (b ushr 16)
    }

    private fun loadIdempotentOrder(
        userId: UUID,
        normalizedKey: String,
        request: CreateOrderRequest,
    ): OrderResponse? {
        val existing = idempotencyKeyRepository.findByUserIdAndKey(userId, normalizedKey) ?: return null
        assertRequestFingerprintMatches(existing, request)
        // Short TX so LAZY order.items can be initialized for toResponse.
        return transactionTemplate.execute {
            val order =
                orderRepository.findById(existing.orderId).orElseThrow {
                    NotFoundException("Order not found for idempotency key")
                }
            order.items.size
            toResponse(order)
        }
    }

    private fun assertRequestFingerprintMatches(
        existing: OrderIdempotencyKey,
        request: CreateOrderRequest,
    ) {
        val stored = existing.requestHash
        if (stored.isBlank()) {
            // Pre-V8 rows: no fingerprint stored — allow replay with any body.
            return
        }
        val incoming = requestFingerprint(request)
        if (stored != incoming) {
            throw ConflictException(
                "Idempotency-Key was already used with a different request body",
            )
        }
    }

    /**
     * Stable SHA-256 of fields that affect order creation (shipping + note).
     */
    internal fun requestFingerprint(request: CreateOrderRequest): String {
        val normalized =
            buildString {
                append("addressId=")
                append(request.addressId?.toString().orEmpty())
                append("|shipping=")
                append(request.shippingAddress?.trim().orEmpty())
                append("|note=")
                append(request.customerNote?.trim().orEmpty())
            }
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    private fun createFromCartInTransaction(
        userId: UUID,
        request: CreateOrderRequest,
        normalizedKey: String?,
    ): OrderResponse {
        if (normalizedKey != null) {
            loadIdempotentOrder(userId, normalizedKey, request)?.let { return it }
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
                    requestHash = requestFingerprint(request),
                ),
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

        return toResponse(saved)
    }

    /**
     * If [rawKey] already maps to an order for [userId], validate request fingerprint
     * and return the existing order (HTTP 200 replay). Null = first attempt.
     */
    fun findIdempotentReplay(
        userId: UUID,
        request: CreateOrderRequest,
        rawKey: String,
    ): OrderResponse? {
        val key = normalizeIdempotencyKey(rawKey) ?: return null
        return loadIdempotentOrder(userId, key, request)
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
        return toResponse(saved)
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
        val saved = orderRepository.save(order)
        notificationService.notifyOrderEvent(
            userId = userId,
            type = "ORDER_CANCELLED",
            title = "Order cancelled",
            body = "Order ${saved.id} was cancelled. Stock restored where applicable.",
            orderId = saved.id,
        )
        return toResponse(saved)
    }

    /**
     * Charge via [PaymentGateway] then mark order PAID.
     * Default gateway is mock; swap with a real PSP adapter without changing this flow.
     */
    @Transactional
    fun pay(
        userId: UUID,
        orderId: UUID,
    ): OrderResponse {
        val order =
            orderRepository
                .findByIdAndUserId(orderId, userId) ?: throw NotFoundException("Order not found")

        // Fail closed before PSP if status cannot become PAID
        when (order.status) {
            OrderStatus.PENDING, OrderStatus.CONFIRMED -> Unit
            OrderStatus.PAID -> throw BadRequestException("Order is already paid")
            OrderStatus.CANCELLED -> throw BadRequestException("Cannot pay a cancelled order")
            OrderStatus.SHIPPED, OrderStatus.DELIVERED ->
                throw BadRequestException("Order is already fulfilled")
        }

        val charge =
            paymentGateway.charge(
                orderId = orderId,
                userId = userId,
                amount = order.totalAmount,
            )
        if (!charge.success) {
            throw BadRequestException(charge.message ?: "Payment declined by ${paymentGateway.providerId}")
        }

        order.markPaid()

        val saved = orderRepository.save(order)
        notificationService.notifyOrderEvent(
            userId = userId,
            type = "ORDER_PAID",
            title = "Payment received",
            body = "Order ${saved.id} is paid. Total ${saved.totalAmount}.",
            orderId = saved.id,
        )
        return toResponse(saved)
    }

    /**
     * Create a Stripe PaymentIntent for client-side confirmation (Payment Element / mobile SDK).
     * Requires `qmarket.payment.provider=stripe`. Order stays PENDING until webhook or pay path.
     */
    @Transactional(readOnly = true)
    fun createPaymentSession(
        userId: UUID,
        orderId: UUID,
    ): PaymentSessionResponse {
        val stripeApi =
            stripeApiClient.ifAvailable
                ?: throw BadRequestException(
                    "Payment session requires qmarket.payment.provider=stripe",
                )
        val stripeProps = stripeProperties.ifAvailable ?: StripeProperties()

        val order =
            orderRepository.findByIdAndUserId(orderId, userId)
                ?: throw NotFoundException("Order not found")
        when (order.status) {
            OrderStatus.PENDING, OrderStatus.CONFIRMED -> Unit
            OrderStatus.PAID -> throw BadRequestException("Order is already paid")
            OrderStatus.CANCELLED -> throw BadRequestException("Cannot pay a cancelled order")
            OrderStatus.SHIPPED, OrderStatus.DELIVERED ->
                throw BadRequestException("Order is already fulfilled")
        }
        if (order.totalAmount <= BigDecimal.ZERO) {
            throw BadRequestException("Amount must be positive")
        }

        val currency = stripeProps.defaultCurrency
        val amountMinor = StripePaymentGateway.toMinorUnits(order.totalAmount)
        try {
            val intent =
                stripeApi.createPaymentIntentForClient(
                    amountMinor = amountMinor,
                    currency = currency,
                    orderId = orderId,
                    userId = userId,
                )
            val secret =
                intent.clientSecret
                    ?: throw BadRequestException("Stripe did not return client_secret")
            return PaymentSessionResponse(
                orderId = orderId,
                providerId = "stripe",
                paymentIntentId = intent.id,
                clientSecret = secret,
                publishableKey = stripeProps.publishableKey,
            )
        } catch (ex: StripeApiException) {
            throw BadRequestException("Stripe error: ${ex.message}")
        }
    }

    /**
     * Provider webhook / async capture path: mark order PAID without re-charging.
     * Idempotent when already PAID.
     *
     * When [amountMinor] is provided (Stripe PaymentIntent.amount in minor units),
     * it must match [Order.totalAmount] converted with scale 2. Optional [currency]
     * must be a 3-letter code when present (orders have no stored currency column yet).
     */
    @Transactional
    fun markPaidFromProvider(
        orderId: UUID,
        providerId: String,
        providerReference: String?,
        amountMinor: Long? = null,
        currency: String? = null,
    ): OrderResponse {
        val order =
            orderRepository.findById(orderId).orElseThrow {
                NotFoundException("Order not found: $orderId")
            }
        when (order.status) {
            OrderStatus.PAID -> return toResponse(order)
            OrderStatus.PENDING, OrderStatus.CONFIRMED -> Unit
            else ->
                throw BadRequestException(
                    "Cannot mark order ${order.status} as PAID from $providerId",
                )
        }

        if (amountMinor != null) {
            val expectedMinor =
                order.totalAmount
                    .setScale(2, java.math.RoundingMode.HALF_UP)
                    .movePointRight(2)
                    .longValueExact()
            if (amountMinor != expectedMinor) {
                throw BadRequestException(
                    "Payment amount mismatch for order $orderId: " +
                        "provider=$amountMinor minor, order=$expectedMinor minor",
                )
            }
        }
        if (!currency.isNullOrBlank()) {
            val normalized = currency.lowercase()
            if (normalized.length != 3) {
                throw BadRequestException("Invalid provider currency: $currency")
            }
        }

        order.markPaid()
        val saved = orderRepository.save(order)
        notificationService.notifyOrderEvent(
            userId = saved.userId,
            type = "ORDER_PAID",
            title = "Payment received",
            body =
                "Order ${saved.id} is paid via $providerId" +
                    (providerReference?.let { " ($it)" } ?: "") +
                    ". Total ${saved.totalAmount}.",
            orderId = saved.id,
        )
        return toResponse(saved)
    }

    /** @deprecated Use [pay]; kept name-compatible for older tests — prefer [pay]. */
    @Transactional
    fun payMock(
        userId: UUID,
        orderId: UUID,
    ): OrderResponse = pay(userId, orderId)

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
