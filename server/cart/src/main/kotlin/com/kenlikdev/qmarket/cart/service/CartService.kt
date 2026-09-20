package com.kenlikdev.qmarket.cart.service

import com.kenlikdev.qmarket.cart.domain.Cart
import com.kenlikdev.qmarket.cart.dto.AddCartItemRequest
import com.kenlikdev.qmarket.cart.dto.CartItemResponse
import com.kenlikdev.qmarket.cart.dto.CartResponse
import com.kenlikdev.qmarket.cart.dto.UpdateCartItemRequest
import com.kenlikdev.qmarket.cart.repository.CartRepository
import com.kenlikdev.qmarket.catalog.api.ProductCatalog
import com.kenlikdev.qmarket.common.exception.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

/**
 * Application service: loads catalog snapshots, delegates invariants to [Cart] aggregate.
 */
@Service
class CartService(
    private val cartRepository: CartRepository,
    private val productCatalog: ProductCatalog,
) {
    @Transactional
    fun getCart(userId: UUID): CartResponse = toResponse(findOrCreate(userId))

    @Transactional
    fun addItem(
        userId: UUID,
        request: AddCartItemRequest,
    ): CartResponse {
        val product = productCatalog.requireActive(request.productId)
        val cart = findOrCreateForUpdate(userId)
        cart.addItem(
            productId = product.id,
            quantity = request.quantity,
            availableStock = product.stockQuantity,
            productSlug = product.slug,
        )
        return toResponse(cartRepository.save(cart))
    }

    @Transactional
    fun updateItem(
        userId: UUID,
        productId: UUID,
        request: UpdateCartItemRequest,
    ): CartResponse {
        val product = productCatalog.requireActive(productId)
        val cart = requireCartForUpdate(userId)
        cart.changeQuantity(
            productId = productId,
            quantity = request.quantity,
            availableStock = product.stockQuantity,
            productSlug = product.slug,
        )
        return toResponse(cartRepository.save(cart))
    }

    @Transactional
    fun removeItem(
        userId: UUID,
        productId: UUID,
    ): CartResponse {
        val cart = requireCart(userId)
        cart.removeItem(productId)
        return toResponse(cartRepository.save(cart))
    }

    @Transactional
    fun clear(userId: UUID): CartResponse {
        val cart = requireCart(userId)
        cart.clearItems()
        return toResponse(cartRepository.save(cart))
    }

    /**
     * Concurrent first-touch is safe through an atomic INSERT ... ON CONFLICT DO NOTHING.
     * This avoids marking the surrounding transaction rollback-only after a uniqueness race.
     */
    private fun findOrCreate(userId: UUID): Cart {
        cartRepository.findByUserId(userId)?.let { return it }
        cartRepository.insertIfMissing(userId)
        return cartRepository.findByUserId(userId)
            ?: throw IllegalStateException("Cart row was not created for user $userId")
    }

    private fun findOrCreateForUpdate(userId: UUID): Cart {
        cartRepository.findByUserIdForUpdate(userId)?.let { return it }
        cartRepository.insertIfMissing(userId)
        return cartRepository.findByUserIdForUpdate(userId)
            ?: throw IllegalStateException("Cart row was not created for user $userId")
    }

    private fun requireCartForUpdate(userId: UUID): Cart =
        cartRepository.findByUserIdForUpdate(userId)
            ?: throw NotFoundException("Cart is empty")

    private fun toResponse(cart: Cart): CartResponse {
        val productIds = cart.items.map { it.productId }.distinct()
        val products =
            if (productIds.isEmpty()) {
                emptyMap()
            } else {
                productCatalog.findByIds(productIds)
            }

        val items =
            cart.items.map { item ->
                val product =
                    products[item.productId]
                        ?: throw NotFoundException("Product not found: ${item.productId}")
                val lineTotal = product.price.multiply(BigDecimal(item.quantity))
                CartItemResponse(
                    productId = product.id,
                    productName = product.name,
                    productSlug = product.slug,
                    unitPrice = product.price,
                    quantity = item.quantity,
                    lineTotal = lineTotal,
                    stockQuantity = product.stockQuantity,
                )
            }
        val totalItems = items.sumOf { it.quantity }
        val totalPrice = items.fold(BigDecimal.ZERO) { acc, i -> acc.add(i.lineTotal) }
        return CartResponse(
            id = requireNotNull(cart.id) { "Cart id is missing" },
            userId = cart.userId,
            items = items,
            totalItems = totalItems,
            totalPrice = totalPrice,
            updatedAt = cart.updatedAt,
        )
    }
}
