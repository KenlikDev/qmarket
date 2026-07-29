package com.kenlikdev.qmarket.cart.service

import com.kenlikdev.qmarket.cart.domain.Cart
import com.kenlikdev.qmarket.cart.domain.CartItem
import com.kenlikdev.qmarket.cart.dto.AddCartItemRequest
import com.kenlikdev.qmarket.cart.dto.CartItemResponse
import com.kenlikdev.qmarket.cart.dto.CartResponse
import com.kenlikdev.qmarket.cart.dto.UpdateCartItemRequest
import com.kenlikdev.qmarket.cart.repository.CartRepository
import com.kenlikdev.qmarket.catalog.domain.Product
import com.kenlikdev.qmarket.catalog.repository.ProductRepository
import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class CartService(
    private val cartRepository: CartRepository,
    private val productRepository: ProductRepository,
) {

    @Transactional(readOnly = true)
    fun getCart(userId: UUID): CartResponse {
        val cart = findOrEmpty(userId)
        return toResponse(cart)
    }

    @Transactional
    fun addItem(userId: UUID, request: AddCartItemRequest): CartResponse {
        val product = loadActiveProduct(request.productId)
        val cart = findOrCreate(userId)
        val existing = cart.items.find { it.productId == request.productId }

        val newQty = (existing?.quantity ?: 0) + request.quantity
        if (newQty > product.stockQuantity) {
            throw BadRequestException(
                "Insufficient stock for product ${product.slug}: available ${product.stockQuantity}, requested $newQty",
            )
        }

        if (existing != null) {
            existing.quantity = newQty
        } else {
            val item = CartItem(
                cart = cart,
                productId = product.id!!,
                quantity = request.quantity,
            )
            cart.items.add(item)
        }

        return toResponse(cartRepository.save(cart))
    }

    @Transactional
    fun updateItem(userId: UUID, productId: UUID, request: UpdateCartItemRequest): CartResponse {
        val product = loadActiveProduct(productId)
        val cart = cartRepository.findByUserId(userId)
            .orElseThrow { NotFoundException("Cart is empty") }
        val item = cart.items.find { it.productId == productId }
            ?: throw NotFoundException("Product not in cart")

        if (request.quantity > product.stockQuantity) {
            throw BadRequestException(
                "Insufficient stock for product ${product.slug}: available ${product.stockQuantity}",
            )
        }

        item.quantity = request.quantity
        return toResponse(cartRepository.save(cart))
    }

    @Transactional
    fun removeItem(userId: UUID, productId: UUID): CartResponse {
        val cart = cartRepository.findByUserId(userId)
            .orElseThrow { NotFoundException("Cart is empty") }
        val removed = cart.items.removeIf { it.productId == productId }
        if (!removed) {
            throw NotFoundException("Product not in cart")
        }
        return toResponse(cartRepository.save(cart))
    }

    @Transactional
    fun clear(userId: UUID): CartResponse {
        val cart = cartRepository.findByUserId(userId)
            .orElseThrow { NotFoundException("Cart is empty") }
        cart.items.clear()
        return toResponse(cartRepository.save(cart))
    }

    private fun findOrCreate(userId: UUID): Cart {
        return cartRepository.findByUserId(userId).orElseGet {
            cartRepository.save(Cart(userId = userId))
        }
    }

    private fun findOrEmpty(userId: UUID): Cart {
        return cartRepository.findByUserId(userId).orElse(Cart(userId = userId))
    }

    private fun loadActiveProduct(productId: UUID): Product {
        val product = productRepository.findById(productId)
            .orElseThrow { NotFoundException("Product not found: $productId") }
        if (!product.active) {
            throw BadRequestException("Product is not available")
        }
        return product
    }

    private fun toResponse(cart: Cart): CartResponse {
        val productIds = cart.items.map { it.productId }.toSet()
        val products = if (productIds.isEmpty()) {
            emptyMap()
        } else {
            productRepository.findAllById(productIds).associateBy { it.id!! }
        }

        val items = cart.items.map { item ->
            val product = products[item.productId]
                ?: throw NotFoundException("Product not found: ${item.productId}")
            val lineTotal = product.price.multiply(BigDecimal(item.quantity))
            CartItemResponse(
                productId = item.productId,
                productName = product.name,
                productSlug = product.slug,
                unitPrice = product.price,
                quantity = item.quantity,
                lineTotal = lineTotal,
                stockQuantity = product.stockQuantity,
            )
        }

        val totalPrice = items.fold(BigDecimal.ZERO) { acc, i -> acc.add(i.lineTotal) }
        val totalItems = items.sumOf { it.quantity }

        return CartResponse(
            id = cart.id ?: UUID(0, 0),
            userId = cart.userId,
            items = items,
            totalItems = totalItems,
            totalPrice = totalPrice,
            updatedAt = cart.updatedAt,
        )
    }
}
