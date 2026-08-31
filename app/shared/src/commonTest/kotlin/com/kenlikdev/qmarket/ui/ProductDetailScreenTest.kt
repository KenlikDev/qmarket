package com.kenlikdev.qmarket.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kenlikdev.qmarket.api.ProductDto
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProductDetailScreenTest {
    private val sample =
        ProductDto(
            id = "p1",
            name = "Demo Phone",
            slug = "demo-phone",
            description = "A solid phone",
            shortDescription = "Compact",
            sku = "SKU-1",
            price = "99.00",
            stockQuantity = 5,
            active = true,
            featured = false,
            categoryName = "Electronics",
        )

    @Test
    fun showsNamePriceAndAddWhenLoggedIn() =
        runComposeUiTest {
            var added = false
            setContent {
                ProductDetailScreen(
                    product = sample,
                    loggedIn = true,
                    userLabel = "user@test.com",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onAddToCart = { added = true },
                    onCart = {},
                    onOrders = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("productDetailName").assertExists()
            onNodeWithTag("productDetailPrice").assertExists()
            onNodeWithTag("productDetailAddToCart").assertIsEnabled()
            onNodeWithTag("productDetailAddToCart").performClick()
            assertTrue(added)
            onNodeWithText("Electronics").assertExists()
        }

    @Test
    fun guestSeesSignInHint() =
        runComposeUiTest {
            setContent {
                ProductDetailScreen(
                    product = sample,
                    loggedIn = false,
                    userLabel = null,
                    cartCount = null,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onAddToCart = {},
                    onCart = {},
                    onOrders = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("productDetailAddToCart").assertDoesNotExist()
            onNodeWithText("Sign in to add to cart").assertExists()
        }
}
