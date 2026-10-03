package com.kenlikdev.qmarket.catalog.api

import java.util.UUID

/**
 * Public catalog contract for consumer modules.
 * Reflects cart/order needs — not the internal structure of the catalog module.
 */
interface ProductCatalog {
    fun findById(id: UUID): ProductInfo?

    /** Active product or [com.kenlikdev.qmarket.common.exception.NotFoundException] / BadRequest if inactive. */
    fun requireActive(id: UUID): ProductInfo

    fun findByIds(ids: Collection<UUID>): Map<UUID, ProductInfo>

    /**
     * Reserve stock and return the product snapshot captured at the reservation boundary.
     *
     * Implementations must acquire the product row lock before reading the price/name/slug
     * snapshot. This defines checkout pricing as "price at successful stock reservation".
     */
    fun reserveStock(
        id: UUID,
        quantity: Int,
    ): ProductInfo

    /** Decrease stock; throws if insufficient or product missing. */
    fun decreaseStock(
        id: UUID,
        quantity: Int,
    )

    /** Restore stock (e.g. order cancel). */
    fun increaseStock(
        id: UUID,
        quantity: Int,
    )
}
