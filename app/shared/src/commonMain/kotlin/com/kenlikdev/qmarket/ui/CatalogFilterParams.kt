package com.kenlikdev.qmarket.ui

/**
 * Maps catalog UI controls to [com.kenlikdev.qmarket.network.QMarketApiClient.listProducts] query params.
 */
object CatalogFilterParams {
    fun queryParam(raw: String): String? = raw.trim().ifBlank { null }

    fun featuredParam(featuredOnly: Boolean): Boolean? = if (featuredOnly) true else null

    fun sortDir(sortBy: String?): String = if (sortBy == "price") "asc" else "desc"

    /** Blank / whitespace → no category filter (all products). */
    fun categoryParam(categoryId: String?): String? = categoryId?.trim()?.ifBlank { null }
}
