package com.kenlikdev.qmarket.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kenlikdev.qmarket.api.NotificationDto
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NotificationsScreenTest {
    @Test
    fun showsItemAndMarkRead() =
        runComposeUiTest {
            var marked = false
            setContent {
                NotificationsScreen(
                    notifications =
                        listOf(
                            NotificationDto(
                                id = "n1",
                                type = "ORDER_PLACED",
                                title = "Order placed",
                                body = "Order abc pending",
                                read = false,
                            ),
                        ),
                    unreadCount = 1,
                    loggedIn = true,
                    userLabel = "u@test.com",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onMarkRead = { marked = true },
                    onMarkAllRead = {},
                    onRefresh = {},
                    onCart = {},
                    onOrders = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithText("Order placed", substring = true).assertExists()
            onNodeWithTag("notificationMarkRead_n1").performClick()
            assertTrue(marked)
        }

    @Test
    fun emptyState() =
        runComposeUiTest {
            setContent {
                NotificationsScreen(
                    notifications = emptyList(),
                    unreadCount = 0,
                    loggedIn = true,
                    userLabel = "u@test.com",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onMarkRead = {},
                    onMarkAllRead = {},
                    onRefresh = {},
                    onCart = {},
                    onOrders = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("notificationsEmpty").assertExists()
        }
}
