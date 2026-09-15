package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.NotFoundException
import com.kenlikdev.qmarket.identity.domain.Address
import com.kenlikdev.qmarket.identity.dto.CreateAddressRequest
import com.kenlikdev.qmarket.identity.dto.UpdateAddressRequest
import com.kenlikdev.qmarket.identity.repository.AddressRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class AddressServiceTest {
    private lateinit var addressRepository: AddressRepository
    private lateinit var addressService: AddressService
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        addressRepository = mockk(relaxed = true)
        addressService = AddressService(addressRepository)
    }

    @Test
    fun `create first address becomes default`() {
        every { addressRepository.countByUserId(userId) } returns 0L
        every { addressRepository.clearDefaultForUser(userId) } returns Unit
        every { addressRepository.save(any()) } answers {
            firstArg<Address>().also { it.id = UUID.randomUUID() }
        }

        val result =
            addressService.create(
                userId,
                CreateAddressRequest(
                    recipientName = "Ivan",
                    city = "Moscow",
                    streetLine1 = "Tverskaya 1",
                    default = false,
                ),
            )

        assertTrue(result.default)
        verify { addressRepository.clearDefaultForUser(userId) }
        verify { addressRepository.save(match { it.isDefault }) }
    }

    @Test
    fun `get foreign address throws NotFound`() {
        val id = UUID.randomUUID()
        every { addressRepository.findByIdAndUserId(id, userId) } returns null

        assertThrows<NotFoundException> {
            addressService.get(userId, id)
        }
    }

    @Test
    fun `update sets default and clears previous`() {
        val id = UUID.randomUUID()
        val address =
            Address(
                id = id,
                userId = userId,
                recipientName = "Ivan",
                city = "Moscow",
                streetLine1 = "Tverskaya 1",
                isDefault = false,
            )
        every { addressRepository.findByIdAndUserId(id, userId) } returns address
        every { addressRepository.clearDefaultForUser(userId) } returns Unit
        every { addressRepository.save(any()) } answers { firstArg() }

        val result =
            addressService.update(
                userId,
                id,
                UpdateAddressRequest(default = true, city = "SPb"),
            )

        assertTrue(result.default)
        assertEquals("SPb", result.city)
        verify { addressRepository.clearDefaultForUser(userId) }
    }

    @Test
    fun `delete promotes remaining address to default`() {
        val id = UUID.randomUUID()
        val otherId = UUID.randomUUID()
        val address =
            Address(
                id = id,
                userId = userId,
                recipientName = "Ivan",
                city = "Moscow",
                streetLine1 = "A",
                isDefault = true,
            )
        val other =
            Address(
                id = otherId,
                userId = userId,
                recipientName = "Petr",
                city = "Kazan",
                streetLine1 = "B",
                isDefault = false,
            )
        every { addressRepository.findByIdAndUserId(id, userId) } returns address
        every { addressRepository.delete(address) } returns Unit
        every {
            addressRepository.findAllByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
        } returns listOf(other)
        every { addressRepository.save(any()) } answers { firstArg() }

        addressService.delete(userId, id)

        verify { addressRepository.save(match { it.id == otherId && it.isDefault }) }
    }

    @Test
    fun `list returns mapped addresses`() {
        val id = UUID.randomUUID()
        every { addressRepository.findAllByUserIdOrderByIsDefaultDescCreatedAtDesc(userId) } returns
            listOf(
                Address(
                    id = id,
                    userId = userId,
                    recipientName = "Ivan",
                    city = "Moscow",
                    streetLine1 = "Tverskaya 1",
                    isDefault = true,
                ),
            )

        val result = addressService.list(userId)
        assertEquals(1, result.size)
        assertEquals("Ivan", result[0].recipientName)
        assertTrue(result[0].default)
    }

    @Test
    fun `create subsequent address keeps non-default when not requested`() {
        every { addressRepository.countByUserId(userId) } returns 2L
        every { addressRepository.save(any()) } answers {
            firstArg<Address>().also { it.id = UUID.randomUUID() }
        }

        val result =
            addressService.create(
                userId,
                CreateAddressRequest(
                    recipientName = "Petr",
                    city = "Kazan",
                    streetLine1 = "Bauman 5",
                    default = false,
                ),
            )

        assertEquals(false, result.default)
        verify(exactly = 0) { addressRepository.clearDefaultForUser(userId) }
    }

    @Test
    fun `update not found throws`() {
        val id = UUID.randomUUID()
        every { addressRepository.findByIdAndUserId(id, userId) } returns null
        assertThrows<NotFoundException> {
            addressService.update(userId, id, UpdateAddressRequest(city = "X"))
        }
    }

    @Test
    fun `delete not found throws`() {
        val id = UUID.randomUUID()
        every { addressRepository.findByIdAndUserId(id, userId) } returns null
        assertThrows<NotFoundException> {
            addressService.delete(userId, id)
        }
    }

    @Test
    fun `delete non-default does not promote`() {
        val id = UUID.randomUUID()
        val address =
            Address(
                id = id,
                userId = userId,
                recipientName = "Ivan",
                city = "Moscow",
                streetLine1 = "A",
                isDefault = false,
            )
        every { addressRepository.findByIdAndUserId(id, userId) } returns address
        every { addressRepository.delete(address) } returns Unit

        addressService.delete(userId, id)

        verify(exactly = 1) { addressRepository.delete(address) }
        verify(exactly = 0) {
            addressRepository.findAllByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
        }
    }
}
