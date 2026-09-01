package com.kenlikdev.qmarket.identity.repository

import com.kenlikdev.qmarket.identity.domain.Role
import com.kenlikdev.qmarket.identity.domain.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?

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
