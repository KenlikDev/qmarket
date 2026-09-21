package com.kenlikdev.qmarket.identity.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class CreateAddressRequest(
    @field:Size(max = 100)
    val label: String? = null,
    @field:NotBlank
    @field:Size(max = 200)
    val recipientName: String,
    @field:Size(max = 30)
    val phone: String? = null,
    @field:Size(max = 100)
    val country: String = "RU",
    @field:Size(max = 100)
    val region: String? = null,
    @field:NotBlank
    @field:Size(max = 100)
    val city: String,
    @field:NotBlank
    @field:Size(max = 255)
    val streetLine1: String,
    @field:Size(max = 255)
    val streetLine2: String? = null,
    @field:Size(max = 20)
    val postalCode: String? = null,
    val default: Boolean = false,
)

data class UpdateAddressRequest(
    @field:Size(max = 100)
    val label: String? = null,
    @field:Size(max = 200)
    val recipientName: String? = null,
    @field:Size(max = 30)
    val phone: String? = null,
    @field:Size(max = 100)
    val country: String? = null,
    @field:Size(max = 100)
    val region: String? = null,
    @field:Size(max = 100)
    val city: String? = null,
    @field:Size(max = 255)
    val streetLine1: String? = null,
    @field:Size(max = 255)
    val streetLine2: String? = null,
    @field:Size(max = 20)
    val postalCode: String? = null,
    val default: Boolean? = null,
)

data class AddressResponse(
    val id: UUID,
    val label: String?,
    val recipientName: String,
    val phone: String?,
    val country: String,
    val region: String?,
    val city: String,
    val streetLine1: String,
    val streetLine2: String?,
    val postalCode: String?,
    val default: Boolean,
    val formatted: String,
)
