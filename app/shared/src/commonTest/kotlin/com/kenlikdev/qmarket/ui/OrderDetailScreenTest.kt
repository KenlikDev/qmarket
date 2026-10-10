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
import com.kenlikdev.qmarket.api.PaymentSessionDto
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
                    paymentProviderId = "mock",
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

    @Test
    fun stripeProviderShowsSessionActionAndNeverShowsGenericPay() =
        runComposeUiTest {
            var sessionRequested = false
            setContent {
                OrderDetailScreen(
                    order = sample(),
                    loggedIn = true,
                    userLabel = "user@test.com",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    paymentProviderId = "stripe",
                    onBackToOrders = {},
                    onPay = { error("Generic Pay must not be invoked for Stripe") },
                    onStartPaymentSession = { sessionRequested = true },
                    paymentSession = null,
                    onCancel = {},
                    onRefresh = {},
                    onCart = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }

            onNodeWithTag("orderDetailPay").assertDoesNotExist()
            onNodeWithTag("orderDetailPaymentSessionBtn").assertIsEnabled()
            onNodeWithText("Create Stripe session").performClick()
            assertTrue(sessionRequested)
        }

    @Test
    fun unknownProviderFailsClosedInsteadOfShowingPay() =
        runComposeUiTest {
            setContent {
                OrderDetailScreen(
                    order = sample(),
                    loggedIn = true,
                    userLabel = "user@test.com",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    paymentProviderId = null,
                    onBackToOrders = {},
                    onPay = { error("Payment must remain disabled until provider is known") },
                    onStartPaymentSession = {},
                    onCancel = {},
                    onRefresh = {},
                    onCart = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }

            onNodeWithTag("orderDetailPay").assertDoesNotExist()
            onNodeWithTag("orderDetailPaymentSessionBtn").assertDoesNotExist()
            onNodeWithTag("orderPaymentUnavailable").assertExists()
        }


    @Test
    fun createdStripeSessionDoesNotImplyOrderIsPaid() =
        runComposeUiTest {
            setContent {
                OrderDetailScreen(
                    order = sample(),
                    loggedIn = true,
                    userLabel = "user@test.com",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    paymentProviderId = "stripe",
                    paymentSession =
                        PaymentSessionDto(
                            orderId = "o1",
                            providerId = "stripe",
                            paymentIntentId = "pi_test",
                            clientSecret = "pi_test_secret",
                            publishableKey = "pk_test",
                        ),
                    onBackToOrders = {},
                    onPay = { error("Stripe must not use generic Pay") },
                    onStartPaymentSession = {},
                    onCancel = {},
                    onRefresh = {},
                    onCart = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }

            onNodeWithTag("orderDetailStatus").assertExists()
            onNodeWithText("PENDING").assertExists()
            onNodeWithTag("orderDetailPaymentSession").assertExists()
            onNodeWithTag("orderDetailPaymentIntentId").assertExists()
            onNodeWithText("This app build cannot confirm Stripe payments.", substring = true).assertExists()
            onNodeWithTag("orderDetailPay").assertDoesNotExist()
        }

}
