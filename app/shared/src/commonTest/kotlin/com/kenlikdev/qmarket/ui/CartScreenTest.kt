package com.kenlikdev.qmarket.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kenlikdev.qmarket.api.AddressDto
import com.kenlikdev.qmarket.api.CartDto
import com.kenlikdev.qmarket.api.CartItemDto
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class CartScreenTest {
    private val sampleCart =
        CartDto(
            id = "c1",
            userId = "u1",
            items =
                listOf(
                    CartItemDto(
                        productId = "p1",
                        productName = "Demo Phone",
                        productSlug = "demo-phone",
                        unitPrice = "10.00",
                        quantity = 2,
                        lineTotal = "20.00",
                        stockQuantity = 10,
                    ),
                ),
            totalItems = 2,
            totalPrice = "20.00",
        )

    @Test
    fun checkoutDisabledWithoutAddressOrShipping() =
        runComposeUiTest {
            setContent {
                CartScreen(
                    cart = sampleCart,
                    addresses = emptyList(),
                    selectedAddressId = null,
                    shippingAddress = "",
                    loggedIn = true,
                    userLabel = "u@qmarket.local",
                    error = null,
                    loading = false,
                    onBackToCatalog = {},
                    onDecreaseQty = {},
                    onIncreaseQty = {},
                    onRemoveItem = {},
                    onSelectAddress = {},
                    onManageAddresses = {},
                    onShippingChange = {},
                    onCheckout = {},
                    onClearCart = {},
                    onOrders = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithText("Demo Phone").assertExists()
            onNodeWithTag("checkoutSubmit").assertIsNotEnabled()
        }

    @Test
    fun checkoutEnabledWhenSavedAddressSelected() =
        runComposeUiTest {
            setContent {
                CartScreen(
                    cart = sampleCart,
                    addresses =
                        listOf(
                            AddressDto(
                                id = "a1",
                                recipientName = "Ann",
                                city = "Moscow",
                                streetLine1 = "Tverskaya 1",
                                default = true,
                            ),
                        ),
                    selectedAddressId = "a1",
                    shippingAddress = "",
                    loggedIn = true,
                    userLabel = "u@qmarket.local",
                    error = null,
                    loading = false,
                    onBackToCatalog = {},
                    onDecreaseQty = {},
                    onIncreaseQty = {},
                    onRemoveItem = {},
                    onSelectAddress = {},
                    onManageAddresses = {},
                    onShippingChange = {},
                    onCheckout = {},
                    onClearCart = {},
                    onOrders = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("checkoutSubmit").assertIsEnabled()
            onNodeWithTag("cartQtyInc").assertExists()
            onNodeWithTag("cartQtyDec").assertExists()
        }
}
