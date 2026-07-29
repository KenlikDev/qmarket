package com.kenlikdev.qmarket.cart.repository

import com.kenlikdev.qmarket.cart.domain.Cart
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface CartRepository : JpaRepository<Cart, UUID> {
    fun findByUserId(userId: UUID): Optional<Cart>
}
