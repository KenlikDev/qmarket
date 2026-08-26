package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.ui.CatalogFilterParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Guards catalog UI → listProducts query mapping. */
class CatalogFilterParamsTest {
    @Test
    fun blankSearchBecomesNull() {
        assertNull(CatalogFilterParams.queryParam("  "))
        assertEquals("phone", CatalogFilterParams.queryParam(" phone "))
    }

    @Test
    fun featuredOnlyWhenToggled() {
        assertNull(CatalogFilterParams.featuredParam(false))
        assertEquals(true, CatalogFilterParams.featuredParam(true))
    }

    @Test
    fun priceSortAscending() {
        assertEquals("asc", CatalogFilterParams.sortDir("price"))
        assertEquals("desc", CatalogFilterParams.sortDir("name"))
        assertEquals("desc", CatalogFilterParams.sortDir("createdAt"))
    }
}
