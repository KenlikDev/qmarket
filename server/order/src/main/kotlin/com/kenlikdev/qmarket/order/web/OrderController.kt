package com.kenlikdev.qmarket.order.web

import com.kenlikdev.qmarket.order.dto.CreateOrderRequest
import com.kenlikdev.qmarket.order.dto.OrderResponse
import com.kenlikdev.qmarket.order.dto.UpdateOrderStatusRequest
import com.kenlikdev.qmarket.order.service.OrderService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderService: OrderService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        authentication: Authentication,
        @Valid @RequestBody request: CreateOrderRequest,
    ): OrderResponse = orderService.createFromCart(currentUserId(authentication), request)

    /**
     * Only page/size — do not bind Spring Pageable (Swagger sends sort=string → JPA crash).
     */
    @GetMapping
    fun myOrders(
        authentication: Authentication,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): Page<OrderResponse> = orderService.listMyOrders(currentUserId(authentication), page, size)

    @GetMapping("/{id}")
    fun myOrder(
        authentication: Authentication,
        @PathVariable id: UUID,
    ): OrderResponse = orderService.getMyOrder(currentUserId(authentication), id)

    @PostMapping("/{id}/cancel")
    fun cancel(
        authentication: Authentication,
        @PathVariable id: UUID,
    ): OrderResponse = orderService.cancelMyOrder(currentUserId(authentication), id)

    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    fun listAll(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): Page<OrderResponse> = orderService.listAllOrders(page, size)

    @GetMapping("/admin/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    fun getAdmin(
        @PathVariable id: UUID,
    ): OrderResponse = orderService.getOrderAdmin(id)

    @PutMapping("/admin/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    fun updateStatus(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateOrderStatusRequest,
    ): OrderResponse = orderService.updateStatus(id, request)

    private fun currentUserId(authentication: Authentication): UUID = authentication.principal as UUID
}
