package com.kenlikdev.qmarket.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kenlikdev.qmarket.api.OrderDto
import com.kenlikdev.qmarket.api.OrderStatusDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class OrdersScreenTest {
    private fun order(
        id: String = "o1",
        status: OrderStatusDto = OrderStatusDto.PENDING,
        total: String = "99.00",
    ) = OrderDto(
        id = id,
        userId = "u1",
        status = status,
        totalAmount = total,
        shippingAddress = "Moscow, Tverskaya 1",
        items = emptyList(),
    )

    @Test
    fun pendingOrderShowsPayAndCancel() =
        runComposeUiTest {
            setContent {
                OrdersScreen(
                    orders = listOf(order()),
                    loggedIn = true,
                    userLabel = "user@test.com",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    paymentProviderId = "mock",
                    onPay = {},
                    onCancel = {},
                    onCart = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("orderPay").assertExists().assertIsEnabled()
            onNodeWithTag("orderCancel").assertExists().assertIsEnabled()
            onNodeWithText("PENDING", substring = true).assertExists()
        }

    @Test
    fun paidOrderHidesPayAndCancel() =
        runComposeUiTest {
            setContent {
                OrdersScreen(
                    orders = listOf(order(status = OrderStatusDto.PAID)),
                    loggedIn = true,
                    userLabel = "user@test.com",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onPay = {},
                    onCancel = {},
                    onCart = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("orderPay").assertDoesNotExist()
            onNodeWithTag("orderCancel").assertDoesNotExist()
            onNodeWithText("PAID", substring = true).assertExists()
        }

    @Test
    fun payClickInvokesCallback() =
        runComposeUiTest {
            var paidId: String? = null
            setContent {
                OrdersScreen(
                    orders = listOf(order(id = "order-42")),
                    loggedIn = true,
                    userLabel = "user@test.com",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    paymentProviderId = "mock",
                    onPay = { paidId = it.id },
                    onCancel = {},
                    onCart = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("orderPay").performClick()
            assertEquals("order-42", paidId)
        }

    @Test
    fun openOrderClickInvokesCallback() =
        runComposeUiTest {
            var opened: String? = null
            setContent {
                OrdersScreen(
                    orders = listOf(order(id = "o99")),
                    loggedIn = true,
                    userLabel = "user@test.com",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onOpenOrder = { opened = it.id },
                    onPay = {},
                    onCancel = {},
                    onCart = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("orderCard_o99").performClick()
            assertEquals("o99", opened)
        }

    @Test
    fun stripeProviderReplacesPayWithOpenOrderAction() =
        runComposeUiTest {
            var openedOrderId: String? = null
            setContent {
                OrdersScreen(
                    orders = listOf(order(id = "stripe-order")),
                    loggedIn = true,
                    userLabel = "user@test.com",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onOpenOrder = { openedOrderId = it.id },
                    paymentProviderId = "stripe",
                    onPay = { error("Generic Pay must not be invoked for Stripe") },
                    onCancel = {},
                    onCart = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }

            onNodeWithTag("orderPay").assertDoesNotExist()
            onNodeWithTag("orderViewPaymentStatus").assertIsEnabled().performClick()
            assertEquals("stripe-order", openedOrderId)
        }

    @Test
    fun unknownProviderFailsClosedInOrdersList() =
        runComposeUiTest {
            setContent {
                OrdersScreen(
                    orders = listOf(order()),
                    loggedIn = true,
                    userLabel = "user@test.com",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onOpenOrder = {},
                    paymentProviderId = null,
                    onPay = { error("Pay is unsafe when provider configuration is unknown") },
                    onCancel = {},
                    onCart = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }

            onNodeWithTag("orderPay").assertDoesNotExist()
            onNodeWithTag("orderViewPaymentStatus").assertDoesNotExist()
            onNodeWithText("Payment unavailable").assertExists()
            onNodeWithTag("orderCancel").assertExists()
        }

}
