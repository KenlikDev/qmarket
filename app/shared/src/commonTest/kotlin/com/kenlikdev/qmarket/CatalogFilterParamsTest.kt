package com.kenlikdev.qmarket

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Documents how catalog UI maps to listProducts query params. */
class CatalogFilterParamsTest {
    private fun queryParam(raw: String): String? = raw.trim().ifBlank { null }

    private fun featuredParam(featuredOnly: Boolean): Boolean? = if (featuredOnly) true else null

    private fun sortDir(sortBy: String): String = if (sortBy == "price") "asc" else "desc"

    @Test
    fun blankSearchBecomesNull() {
        assertNull(queryParam("  "))
        assertEquals("phone", queryParam(" phone "))
    }

    @Test
    fun featuredOnlyWhenToggled() {
        assertNull(featuredParam(false))
        assertEquals(true, featuredParam(true))
    }

    @Test
    fun priceSortAscending() {
        assertEquals("asc", sortDir("price"))
        assertEquals("desc", sortDir("name"))
    }
}
