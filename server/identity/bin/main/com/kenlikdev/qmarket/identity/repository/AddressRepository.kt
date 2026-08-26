package com.kenlikdev.qmarket.identity.repository

import com.kenlikdev.qmarket.identity.domain.Address
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface AddressRepository : JpaRepository<Address, UUID> {
    fun findAllByUserIdOrderByIsDefaultDescCreatedAtDesc(userId: UUID): List<Address>

    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): Address?

    fun countByUserId(userId: UUID): Long

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Address a SET a.isDefault = false WHERE a.userId = :userId AND a.isDefault = true")
    fun clearDefaultForUser(
        @Param("userId") userId: UUID,
    )
}
