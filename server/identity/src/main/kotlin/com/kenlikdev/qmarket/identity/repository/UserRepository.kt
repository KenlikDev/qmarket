package com.kenlikdev.qmarket.identity.repository

import com.kenlikdev.qmarket.identity.domain.Role
import com.kenlikdev.qmarket.identity.domain.User
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    fun findByIdForUpdate(@Param("userId") userId: UUID): User?

    fun existsByEmail(email: String): Boolean

    /**
     * Case-insensitive search on email / first / last name.
     * Derived query avoids JPQL CONCAT+LOWER issues on PostgreSQL (lower(bytea)).
     */
    fun findByEmailContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
        email: String,
        firstName: String,
        lastName: String,
        pageable: Pageable,
    ): Page<User>
}

interface RoleRepository : JpaRepository<Role, UUID> {
    fun findByName(name: String): Role?
}
