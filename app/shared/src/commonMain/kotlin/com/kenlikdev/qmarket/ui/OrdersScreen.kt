package com.kenlikdev.qmarket.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kenlikdev.qmarket.api.OrderDto
import com.kenlikdev.qmarket.api.OrderStatusDto

@Composable
fun OrdersScreen(
    orders: List<OrderDto>,
    loggedIn: Boolean,
    userLabel: String?,
    cartCount: Int?,
    error: String?,
    statusMessage: String?,
    loading: Boolean,
    onBackToCatalog: () -> Unit,
    onOpenOrder: (OrderDto) -> Unit = {},
    onPay: (OrderDto) -> Unit,
    onCancel: (OrderDto) -> Unit,
    onCart: () -> Unit,
    onAddresses: () -> Unit,
    onProfile: () -> Unit,
    onNotifications: (() -> Unit)? = null,
    notificationsUnread: Long = 0L,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopBar(
            title = "My orders",
            subtitle = userLabel ?: "Guest",
            loggedIn = loggedIn,
            cartCount = cartCount,
            onCart = onCart,
            onOrders = null,
            onAddresses = onAddresses,
            onProfile = onProfile,
            onNotifications = onNotifications,
            notificationsUnread = notificationsUnread,
            onLogout = onLogout,
        )
        TextButton(onClick = onBackToCatalog) {
            Text("← Back to catalog")
        }
        ErrorText(error, modifier = Modifier.padding(horizontal = 16.dp))
        if (loading && orders.isEmpty()) {
            LoadingCenter()
        } else if (orders.isEmpty()) {
            Text(
                "No orders yet",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(orders, key = { it.id }) { order ->
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !loading) { onOpenOrder(order) }
                                .testTag("orderCard_${order.id}"),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                order.status.name,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Total: ${order.totalAmount}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            order.createdAt?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "Id: ${order.id}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "Details →",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Row {
                                if (order.status == OrderStatusDto.PENDING) {
                                    TextButton(
                                        onClick = { onPay(order) },
                                        enabled = !loading,
                                        modifier = Modifier.testTag("orderPay"),
                                    ) {
                                        Text("Pay")
                                    }
                                    TextButton(
                                        onClick = { onCancel(order) },
                                        enabled = !loading,
                                        modifier = Modifier.testTag("orderCancel"),
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        statusMessage?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
