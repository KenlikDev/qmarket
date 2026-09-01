package com.kenlikdev.qmarket.order.web

import com.kenlikdev.qmarket.order.dto.CreateOrderRequest
import com.kenlikdev.qmarket.order.dto.OrderResponse
import com.kenlikdev.qmarket.order.dto.PageResponse
import com.kenlikdev.qmarket.order.dto.UpdateOrderStatusRequest
import com.kenlikdev.qmarket.order.service.OrderService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderService: OrderService,
) {
    /**
     * Checkout from cart. Optional [Idempotency-Key] (max 128 chars) makes retries safe:
     * the same key for the same user always returns the same order (201 first time, 200 on replay).
     */
    @PostMapping
    fun create(
        authentication: Authentication,
        @Valid @RequestBody request: CreateOrderRequest,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
    ): ResponseEntity<OrderResponse> {
        val userId = currentUserId(authentication)
        // Explicit replay: same key + same body → 200; same key + different body → 409 from service.
        if (idempotencyKey != null) {
            orderService.findIdempotentReplay(userId, request, idempotencyKey)?.let { existing ->
                return ResponseEntity.ok(existing)
            }
        }
        val response = orderService.createFromCart(userId, request, idempotencyKey)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * Only page/size — do not bind Spring Pageable (Swagger sends sort=string → JPA crash).
     */
    @GetMapping
    fun myOrders(
        authentication: Authentication,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<OrderResponse> = orderService.listMyOrders(currentUserId(authentication), page, size)

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

    @PostMapping("/{id}/pay")
    fun pay(
        authentication: Authentication,
        @PathVariable id: UUID,
    ): OrderResponse = orderService.pay(currentUserId(authentication), id)

    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    fun listAll(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<OrderResponse> = orderService.listAllOrders(page, size)

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
