package com.kenlikdev.qmarket.api

import kotlinx.serialization.Serializable

@Serializable
data class AddressDto(
    val id: String,
    val label: String? = null,
    val recipientName: String,
    val phone: String? = null,
    val country: String = "RU",
    val region: String? = null,
    val city: String,
    val streetLine1: String,
    val streetLine2: String? = null,
    val postalCode: String? = null,
    val default: Boolean = false,
    val formatted: String? = null,
)

@Serializable
data class CreateAddressRequestDto(
    val label: String? = null,
    val recipientName: String,
    val phone: String? = null,
    val country: String = "RU",
    val region: String? = null,
    val city: String,
    val streetLine1: String,
    val streetLine2: String? = null,
    val postalCode: String? = null,
    val default: Boolean = false,
)

@Serializable
data class UpdateAddressRequestDto(
    val label: String? = null,
    val recipientName: String? = null,
    val phone: String? = null,
    val country: String? = null,
    val region: String? = null,
    val city: String? = null,
    val streetLine1: String? = null,
    val streetLine2: String? = null,
    val postalCode: String? = null,
    val default: Boolean? = null,
)
