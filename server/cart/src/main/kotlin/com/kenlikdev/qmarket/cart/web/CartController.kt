package com.kenlikdev.qmarket.cart.web

import com.kenlikdev.qmarket.cart.dto.AddCartItemRequest
import com.kenlikdev.qmarket.cart.dto.CartResponse
import com.kenlikdev.qmarket.cart.dto.UpdateCartItemRequest
import com.kenlikdev.qmarket.cart.service.CartService
import com.kenlikdev.qmarket.common.security.userId
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
    fun getCart(authentication: Authentication): CartResponse = cartService.getCart(authentication.userId())

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.OK)
    fun addItem(
        authentication: Authentication,
        @Valid @RequestBody request: AddCartItemRequest,
    ): CartResponse = cartService.addItem(authentication.userId(), request)

    @PutMapping("/items/{productId}")
    fun updateItem(
        authentication: Authentication,
        @PathVariable productId: UUID,
        @Valid @RequestBody request: UpdateCartItemRequest,
    ): CartResponse = cartService.updateItem(authentication.userId(), productId, request)

    @DeleteMapping("/items/{productId}")
    fun removeItem(
        authentication: Authentication,
        @PathVariable productId: UUID,
    ): CartResponse = cartService.removeItem(authentication.userId(), productId)

    @DeleteMapping
    fun clear(authentication: Authentication): CartResponse = cartService.clear(authentication.userId())
}
