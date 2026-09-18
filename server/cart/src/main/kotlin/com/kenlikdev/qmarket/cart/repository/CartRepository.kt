package com.kenlikdev.qmarket.cart.repository

import com.kenlikdev.qmarket.cart.domain.Cart
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface CartRepository : JpaRepository<Cart, UUID> {
    fun findByUserId(userId: UUID): Cart?

    @Modifying
    @Query(
        value = """
            INSERT INTO carts (user_id)
            VALUES (:userId)
            ON CONFLICT (user_id) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIfMissing(@Param("userId") userId: UUID): Int
}
