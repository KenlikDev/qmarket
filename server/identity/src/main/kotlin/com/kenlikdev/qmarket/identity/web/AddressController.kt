package com.kenlikdev.qmarket.identity.web

import com.kenlikdev.qmarket.identity.dto.AddressResponse
import com.kenlikdev.qmarket.identity.dto.CreateAddressRequest
import com.kenlikdev.qmarket.identity.dto.UpdateAddressRequest
import com.kenlikdev.qmarket.identity.service.AddressService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users/me/addresses")
class AddressController(
    private val addressService: AddressService,
) {
    @GetMapping
    fun list(authentication: Authentication): List<AddressResponse> = addressService.list(currentUserId(authentication))

    @GetMapping("/{id}")
    fun get(
        authentication: Authentication,
        @PathVariable id: UUID,
    ): AddressResponse = addressService.get(currentUserId(authentication), id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        authentication: Authentication,
        @Valid @RequestBody request: CreateAddressRequest,
    ): AddressResponse = addressService.create(currentUserId(authentication), request)

    @PutMapping("/{id}")
    fun update(
        authentication: Authentication,
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateAddressRequest,
    ): AddressResponse = addressService.update(currentUserId(authentication), id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        authentication: Authentication,
        @PathVariable id: UUID,
    ) {
        addressService.delete(currentUserId(authentication), id)
    }

    private fun currentUserId(authentication: Authentication): UUID = authentication.principal as UUID
}
