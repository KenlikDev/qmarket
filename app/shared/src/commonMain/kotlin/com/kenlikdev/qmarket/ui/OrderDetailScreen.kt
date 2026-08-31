package com.kenlikdev.qmarket.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
fun OrderDetailScreen(
    order: OrderDto?,
    loggedIn: Boolean,
    userLabel: String?,
    cartCount: Int?,
    error: String?,
    statusMessage: String?,
    loading: Boolean,
    onBackToOrders: () -> Unit,
    onPay: () -> Unit,
    onCancel: () -> Unit,
    onRefresh: () -> Unit,
    onCart: () -> Unit,
    onAddresses: () -> Unit,
    onProfile: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopBar(
            title = "Order",
            subtitle = userLabel ?: "Guest",
            loggedIn = loggedIn,
            cartCount = cartCount,
            onCart = onCart,
            onOrders = onBackToOrders,
            onAddresses = onAddresses,
            onProfile = onProfile,
            onLogout = onLogout,
        )
        TextButton(
            onClick = onBackToOrders,
            enabled = !loading,
            modifier = Modifier.padding(horizontal = 8.dp).testTag("orderDetailBack"),
        ) {
            Text("← My orders")
        }
        ErrorText(error, modifier = Modifier.padding(horizontal = 16.dp))
        statusMessage?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        when {
            loading && order == null -> LoadingCenter()
            order == null -> {
                Text(
                    "Order not found",
                    modifier = Modifier.padding(16.dp).testTag("orderDetailMissing"),
                )
            }
            else -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                            .testTag("orderDetailContent"),
                ) {
                    Text(
                        order.status.name,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.testTag("orderDetailStatus"),
                    )
                    Text(
                        "Total: ${order.totalAmount}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp).testTag("orderDetailTotal"),
                    )
                    Text(
                        "Id: ${order.id}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    order.createdAt?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    order.shippingAddress?.let {
                        Text(
                            "Ship to: $it",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp).testTag("orderDetailShipping"),
                        )
                    }
                    order.customerNote?.let {
                        Text(
                            "Note: $it",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Text(
                        "Items",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    if (order.items.isEmpty()) {
                        Text(
                            "No line items",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("orderDetailNoItems"),
                        )
                    } else {
                        order.items.forEach { item ->
                            Card(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .testTag("orderDetailItem_${item.productId}"),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(item.productName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "×${item.quantity} · ${item.unitPrice} = ${item.lineTotal}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                    Row(modifier = Modifier.padding(top = 16.dp)) {
                        if (order.status == OrderStatusDto.PENDING) {
                            Button(
                                onClick = onPay,
                                enabled = !loading,
                                modifier = Modifier.testTag("orderDetailPay"),
                            ) {
                                Text("Pay (mock)")
                            }
                            TextButton(
                                onClick = onCancel,
                                enabled = !loading,
                                modifier = Modifier.testTag("orderDetailCancel"),
                            ) {
                                Text("Cancel")
                            }
                        }
                        TextButton(
                            onClick = onRefresh,
                            enabled = !loading,
                            modifier = Modifier.testTag("orderDetailRefresh"),
                        ) {
                            Text("Refresh")
                        }
                    }
                }
            }
        }
    }
}
