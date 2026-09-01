package com.kenlikdev.qmarket.identity.repository

import com.kenlikdev.qmarket.identity.domain.Role
import com.kenlikdev.qmarket.identity.domain.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?

    fun existsByEmail(email: String): Boolean

    @Query(
        """
        SELECT u FROM User u
        WHERE (:q IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(COALESCE(u.firstName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(COALESCE(u.lastName, '')) LIKE LOWER(CONCAT('%', :q, '%')))
        """,
    )
    fun search(
        @Param("q") q: String?,
        pageable: Pageable,
    ): Page<User>
}

interface RoleRepository : JpaRepository<Role, UUID> {
    fun findByName(name: String): Role?
}
