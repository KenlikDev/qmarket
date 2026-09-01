package com.kenlikdev.qmarket.identity.web

import com.kenlikdev.qmarket.identity.dto.AdminUserPageResponse
import com.kenlikdev.qmarket.identity.dto.AdminUserResponse
import com.kenlikdev.qmarket.identity.service.AdminUserService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class AdminUserController(
    private val adminUserService: AdminUserService,
) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) q: String?,
    ): AdminUserPageResponse = adminUserService.listUsers(page, size, q)

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): AdminUserResponse = adminUserService.getUser(id)
}
