package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.NotFoundException
import com.kenlikdev.qmarket.common.validation.InputValidation
import com.kenlikdev.qmarket.identity.domain.Address
import com.kenlikdev.qmarket.identity.dto.AddressResponse
import com.kenlikdev.qmarket.identity.dto.CreateAddressRequest
import com.kenlikdev.qmarket.identity.dto.UpdateAddressRequest
import com.kenlikdev.qmarket.identity.repository.AddressRepository
import com.kenlikdev.qmarket.identity.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AddressService(
    private val addressRepository: AddressRepository,
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun list(userId: UUID): List<AddressResponse> =
        addressRepository
            .findAllByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
            .map { it.toResponse() }

    @Transactional(readOnly = true)
    fun get(
        userId: UUID,
        addressId: UUID,
    ): AddressResponse {
        val address =
            addressRepository.findByIdAndUserId(addressId, userId)
                ?: throw NotFoundException("Address not found")
        return address.toResponse()
    }

    @Transactional
    fun create(
        userId: UUID,
        request: CreateAddressRequest,
    ): AddressResponse {
        lockUser(userId)
        val makeDefault = request.default || addressRepository.countByUserId(userId) == 0L
        if (makeDefault) {
            addressRepository.clearDefaultForUser(userId)
        }

        val address =
            Address(
                userId = userId,
                label = request.label?.trim()?.ifEmpty { null },
                recipientName = normalizeRecipientName(request.recipientName),
                phone = InputValidation.normalizeOptionalPhone(request.phone),
                country = request.country.trim().ifEmpty { "RU" },
                region = request.region?.trim()?.ifEmpty { null },
                city = request.city.trim(),
                streetLine1 = request.streetLine1.trim(),
                streetLine2 = request.streetLine2?.trim()?.ifEmpty { null },
                postalCode = request.postalCode?.trim()?.ifEmpty { null },
                isDefault = makeDefault,
            )
        return addressRepository.save(address).toResponse()
    }

    @Transactional
    fun update(
        userId: UUID,
        addressId: UUID,
        request: UpdateAddressRequest,
    ): AddressResponse {
        lockUser(userId)
        val address =
            addressRepository.findByIdAndUserId(addressId, userId)
                ?: throw NotFoundException("Address not found")

        request.label?.let { address.label = it.trim().ifEmpty { null } }
        request.recipientName?.let { address.recipientName = normalizeRecipientName(it) }
        request.phone?.let { address.phone = InputValidation.normalizeOptionalPhone(it) }
        request.country?.let { address.country = it.trim().ifEmpty { "RU" } }
        request.region?.let { address.region = it.trim().ifEmpty { null } }
        request.city?.let { address.city = it.trim() }
        request.streetLine1?.let { address.streetLine1 = it.trim() }
        request.streetLine2?.let { address.streetLine2 = it.trim().ifEmpty { null } }
        request.postalCode?.let { address.postalCode = it.trim().ifEmpty { null } }

        if (request.default == true && !address.isDefault) {
            addressRepository.clearDefaultForUser(userId)
            address.isDefault = true
        } else if (request.default == false) {
            address.isDefault = false
        }

        return addressRepository.save(address).toResponse()
    }

    @Transactional
    fun delete(
        userId: UUID,
        addressId: UUID,
    ) {
        lockUser(userId)
        val address =
            addressRepository.findByIdAndUserId(addressId, userId)
                ?: throw NotFoundException("Address not found")
        val wasDefault = address.isDefault
        addressRepository.delete(address)
        if (wasDefault) {
            val remaining = addressRepository.findAllByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
            remaining.firstOrNull()?.let {
                it.isDefault = true
                addressRepository.save(it)
            }
        }
    }

    private fun lockUser(userId: UUID) {
        userRepository.findByIdForUpdate(userId)
            ?: throw NotFoundException("User not found")
    }

    private fun normalizeRecipientName(value: String): String =
        InputValidation.normalizeOptionalName(value, "Recipient name")
            ?: throw IllegalArgumentException("Recipient name must not be blank")

    private fun Address.toResponse(): AddressResponse =
        AddressResponse(
            id = id ?: error("Address id is null"),
            label = label,
            recipientName = recipientName,
            phone = phone,
            country = country,
            region = region,
            city = city,
            streetLine1 = streetLine1,
            streetLine2 = streetLine2,
            postalCode = postalCode,
            default = isDefault,
            formatted = formatSingleLine(),
        )
}
