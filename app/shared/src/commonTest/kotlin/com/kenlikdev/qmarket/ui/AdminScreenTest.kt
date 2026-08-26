package com.kenlikdev.qmarket.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AdminScreenTest {
    @Test
    fun createDisabledWhenPriceInvalid() =
        runComposeUiTest {
            setContent {
                AdminScreen(
                    productName = "Widget",
                    productSlug = "widget",
                    productPrice = "abc",
                    productStock = "10",
                    productFeatured = false,
                    loggedIn = true,
                    userLabel = "admin@qmarket.local",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onNameChange = {},
                    onSlugChange = {},
                    onPriceChange = {},
                    onStockChange = {},
                    onToggleFeatured = {},
                    onCreate = {},
                    onCart = {},
                    onOrders = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("adminCreateProduct").assertIsNotEnabled()
        }

    @Test
    fun createEnabledWhenFormValid() =
        runComposeUiTest {
            setContent {
                AdminScreen(
                    productName = "Widget",
                    productSlug = "widget",
                    productPrice = "12.50",
                    productStock = "3",
                    productFeatured = true,
                    loggedIn = true,
                    userLabel = "admin@qmarket.local",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onNameChange = {},
                    onSlugChange = {},
                    onPriceChange = {},
                    onStockChange = {},
                    onToggleFeatured = {},
                    onCreate = {},
                    onCart = {},
                    onOrders = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("adminCreateProduct").assertIsEnabled()
        }

    @Test
    fun createClickInvokesCallback() =
        runComposeUiTest {
            var created = false
            setContent {
                AdminScreen(
                    productName = "Widget",
                    productSlug = "widget",
                    productPrice = "9.99",
                    productStock = "1",
                    productFeatured = false,
                    loggedIn = true,
                    userLabel = "admin@qmarket.local",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onNameChange = {},
                    onSlugChange = {},
                    onPriceChange = {},
                    onStockChange = {},
                    onToggleFeatured = {},
                    onCreate = { created = true },
                    onCart = {},
                    onOrders = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("adminCreateProduct").performClick()
            assertTrue(created)
        }

    @Test
    fun slugifyProductNameBasic() {
        assertEquals("hello-world", slugifyProductName("Hello World"))
        assertEquals("a-b", slugifyProductName("  A   B  "))
        assertEquals("product", slugifyProductName("!!!"))
    }
}
