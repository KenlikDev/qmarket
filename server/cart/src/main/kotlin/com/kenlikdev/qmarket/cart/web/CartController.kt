package com.kenlikdev.qmarket.cart.web

import com.kenlikdev.qmarket.cart.dto.AddCartItemRequest
import com.kenlikdev.qmarket.cart.dto.CartResponse
import com.kenlikdev.qmarket.cart.dto.UpdateCartItemRequest
import com.kenlikdev.qmarket.cart.service.CartService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/cart")
class CartController(
    private val cartService: CartService,
) {

    @GetMapping
    fun getCart(authentication: Authentication): CartResponse {
        return cartService.getCart(currentUserId(authentication))
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.OK)
    fun addItem(
        authentication: Authentication,
        @Valid @RequestBody request: AddCartItemRequest,
    ): CartResponse {
        return cartService.addItem(currentUserId(authentication), request)
    }

    @PutMapping("/items/{productId}")
    fun updateItem(
        authentication: Authentication,
        @PathVariable productId: UUID,
        @Valid @RequestBody request: UpdateCartItemRequest,
    ): CartResponse {
        return cartService.updateItem(currentUserId(authentication), productId, request)
    }

    @DeleteMapping("/items/{productId}")
    fun removeItem(
        authentication: Authentication,
        @PathVariable productId: UUID,
    ): CartResponse {
        return cartService.removeItem(currentUserId(authentication), productId)
    }

    @DeleteMapping
    fun clear(authentication: Authentication): CartResponse {
        return cartService.clear(currentUserId(authentication))
    }

    private fun currentUserId(authentication: Authentication): UUID {
        return authentication.principal as UUID
    }
}
