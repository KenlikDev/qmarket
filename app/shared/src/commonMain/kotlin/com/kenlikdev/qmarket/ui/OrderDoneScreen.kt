package com.kenlikdev.qmarket.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kenlikdev.qmarket.api.OrderDto

@Composable
fun OrderDoneScreen(
    order: OrderDto,
    error: String?,
    statusMessage: String?,
    loading: Boolean,
    onOpenOrder: () -> Unit,
    onMyOrders: () -> Unit,
    onBackToCatalog: () -> Unit,
    onOpenCart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Order placed", style = MaterialTheme.typography.headlineSmall)
        Text("Id: ${order.id}", modifier = Modifier.padding(top = 8.dp))
        Text("Status: ${order.status}")
        Text("Total: ${order.totalAmount}")
        ErrorText(error)
        Button(
            onClick = onOpenOrder,
            enabled = !loading,
            modifier = Modifier.padding(top = 16.dp).testTag("orderDoneOpenOrder"),
        ) {
            Text("View order")
        }
        statusMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
        TextButton(onClick = onMyOrders) {
            Text("My orders")
        }
        TextButton(onClick = onBackToCatalog) {
            Text("Back to catalog")
        }
        TextButton(onClick = onOpenCart) {
            Text("Open cart")
        }
    }
}
