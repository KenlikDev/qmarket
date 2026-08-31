package com.kenlikdev.qmarket.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kenlikdev.qmarket.api.CategoryDto
import com.kenlikdev.qmarket.api.ProductDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CatalogScreenTest {
    @Test
    fun searchAndApplyTagsPresent() =
        runComposeUiTest {
            var applied = false
            setContent {
                CatalogScreen(
                    products = emptyList(),
                    catalogQuery = "phone",
                    catalogFeaturedOnly = false,
                    loggedIn = false,
                    userLabel = null,
                    cartCount = null,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onQueryChange = {},
                    onSortNewest = {},
                    onSortPrice = {},
                    onSortName = {},
                    onToggleFeatured = {},
                    onApplySearch = { applied = true },
                    onAddToCart = {},
                    onCart = {},
                    onOrders = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("catalogSearch").assertExists()
            onNodeWithTag("catalogSearchApply").assertIsEnabled()
            onNodeWithTag("catalogSearchApply").performClick()
            assertTrue(applied)
        }

    @Test
    fun showsProductNameWhenLoggedInGuest() =
        runComposeUiTest {
            setContent {
                CatalogScreen(
                    products =
                        listOf(
                            ProductDto(
                                id = "p1",
                                name = "Demo Phone",
                                slug = "demo-phone",
                                sku = "SKU-1",
                                price = "99.00",
                                stockQuantity = 5,
                                active = true,
                                featured = false,
                            ),
                        ),
                    catalogQuery = "",
                    catalogFeaturedOnly = false,
                    loggedIn = false,
                    userLabel = null,
                    cartCount = null,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onQueryChange = {},
                    onSortNewest = {},
                    onSortPrice = {},
                    onSortName = {},
                    onToggleFeatured = {},
                    onApplySearch = {},
                    onAddToCart = {},
                    onCart = {},
                    onOrders = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithText("Demo Phone").assertExists()
            onNodeWithText("Add to cart").assertDoesNotExist()
        }

    @Test
    fun categoryAllSelectInvokesCallback() =
        runComposeUiTest {
            var selected: String? = "c1"
            setContent {
                CatalogScreen(
                    products = emptyList(),
                    catalogQuery = "",
                    catalogFeaturedOnly = false,
                    catalogCategories =
                        listOf(
                            CategoryDto(id = "c1", name = "Electronics", slug = "electronics"),
                        ),
                    selectedCategoryId = "c1",
                    onCategorySelect = { selected = it },
                    loggedIn = false,
                    userLabel = null,
                    cartCount = null,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onQueryChange = {},
                    onSortNewest = {},
                    onSortPrice = {},
                    onSortName = {},
                    onToggleFeatured = {},
                    onApplySearch = {},
                    onAddToCart = {},
                    onCart = {},
                    onOrders = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("catalogCategoryAll").performClick()
            assertEquals(null, selected)
        }
}
