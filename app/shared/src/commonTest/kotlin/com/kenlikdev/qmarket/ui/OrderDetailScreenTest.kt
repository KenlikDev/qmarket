package com.kenlikdev.qmarket.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kenlikdev.qmarket.api.OrderDto
import com.kenlikdev.qmarket.api.OrderItemDto
import com.kenlikdev.qmarket.api.OrderStatusDto
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class OrderDetailScreenTest {
    private fun sample(
        status: OrderStatusDto = OrderStatusDto.PENDING,
    ) = OrderDto(
        id = "o1",
        userId = "u1",
        status = status,
        totalAmount = "198.00",
        shippingAddress = "Moscow, Tverskaya 1",
        items =
            listOf(
                OrderItemDto(
                    productId = "p1",
                    productName = "Demo Phone",
                    productSlug = "demo-phone",
                    unitPrice = "99.00",
                    quantity = 2,
                    lineTotal = "198.00",
                ),
            ),
        createdAt = "2026-01-01T00:00:00Z",
    )

    @Test
    fun showsItemsAndPayWhenPending() =
        runComposeUiTest {
            var paid = false
            setContent {
                OrderDetailScreen(
                    order = sample(),
                    loggedIn = true,
                    userLabel = "user@test.com",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToOrders = {},
                    onPay = { paid = true },
                    onCancel = {},
                    onRefresh = {},
                    onCart = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("orderDetailStatus").assertExists()
            onNodeWithTag("orderDetailItem_p1").assertExists()
            onNodeWithText("Demo Phone").assertExists()
            onNodeWithTag("orderDetailPay").assertIsEnabled()
            onNodeWithTag("orderDetailPay").performClick()
            assertTrue(paid)
        }

    @Test
    fun paidHidesPayCancel() =
        runComposeUiTest {
            setContent {
                OrderDetailScreen(
                    order = sample(OrderStatusDto.PAID),
                    loggedIn = true,
                    userLabel = "user@test.com",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToOrders = {},
                    onPay = {},
                    onCancel = {},
                    onRefresh = {},
                    onCart = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("orderDetailPay").assertDoesNotExist()
            onNodeWithTag("orderDetailCancel").assertDoesNotExist()
            onNodeWithTag("orderDetailRefresh").assertExists()
        }
}
