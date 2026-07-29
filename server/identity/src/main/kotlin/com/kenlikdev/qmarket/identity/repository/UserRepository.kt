package com.kenlikdev.qmarket.identity.repository

import com.kenlikdev.qmarket.identity.domain.Role
import com.kenlikdev.qmarket.identity.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): Optional<User>
    fun existsByEmail(email: String): Boolean
}

interface RoleRepository : JpaRepository<Role, UUID> {
    fun findByName(name: String): Optional<Role>
}
