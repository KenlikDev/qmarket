package com.kenlikdev.qmarket.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kenlikdev.qmarket.api.AddressDto
import com.kenlikdev.qmarket.api.CartDto
import com.kenlikdev.qmarket.api.CartItemDto

@Composable
fun CartScreen(
    cart: CartDto?,
    addresses: List<AddressDto>,
    selectedAddressId: String?,
    shippingAddress: String,
    loggedIn: Boolean,
    userLabel: String?,
    error: String?,
    loading: Boolean,
    onBackToCatalog: () -> Unit,
    onDecreaseQty: (CartItemDto) -> Unit,
    onIncreaseQty: (CartItemDto) -> Unit,
    onRemoveItem: (CartItemDto) -> Unit,
    onSelectAddress: (String) -> Unit,
    onManageAddresses: () -> Unit,
    onShippingChange: (String) -> Unit,
    onCheckout: () -> Unit,
    onClearCart: () -> Unit,
    onOrders: () -> Unit,
    onAddresses: () -> Unit,
    onProfile: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopBar(
            title = "Cart",
            subtitle = userLabel ?: "Guest",
            loggedIn = loggedIn,
            cartCount = cart?.totalItems,
            onCart = null,
            onOrders = onOrders,
            onAddresses = onAddresses,
            onProfile = onProfile,
            onLogout = onLogout,
        )
        TextButton(onClick = onBackToCatalog) {
            Text("← Back to catalog")
        }
        ErrorText(error, modifier = Modifier.padding(horizontal = 16.dp))
        if (loading && cart == null) {
            LoadingCenter()
        } else if (cart == null || cart.items.isEmpty()) {
            Text(
                "Cart is empty",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = true),
            ) {
                items(cart.items, key = { it.productId }) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(item.productName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "×${item.quantity} · ${item.lineTotal}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { onDecreaseQty(item) },
                                    enabled = !loading,
                                    modifier = Modifier.testTag("cartQtyDec"),
                                ) {
                                    Text("−")
                                }
                                Text(
                                    "${item.quantity}",
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                                TextButton(
                                    onClick = { onIncreaseQty(item) },
                                    enabled = !loading,
                                    modifier = Modifier.testTag("cartQtyInc"),
                                ) {
                                    Text("+")
                                }
                                TextButton(
                                    onClick = { onRemoveItem(item) },
                                    enabled = !loading,
                                ) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Total: ${cart.totalPrice} (${cart.totalItems} items)",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (addresses.isNotEmpty()) {
                    Text(
                        "Ship to saved address",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    addresses.forEach { addr ->
                        val selected = addr.id == selectedAddressId
                        TextButton(
                            onClick = { onSelectAddress(addr.id) },
                            enabled = !loading,
                        ) {
                            Text(
                                (if (selected) "● " else "○ ") +
                                    (addr.formatted ?: "${addr.city}, ${addr.streetLine1}"),
                            )
                        }
                    }
                    TextButton(onClick = onManageAddresses) {
                        Text("Manage addresses")
                    }
                } else {
                    TextButton(onClick = onManageAddresses) {
                        Text("Add a shipping address")
                    }
                }
                OutlinedTextField(
                    value = shippingAddress,
                    onValueChange = onShippingChange,
                    label = { Text("Or free-form shipping address") },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                )
                Button(
                    onClick = onCheckout,
                    enabled =
                        !loading &&
                            CheckoutSelection.canCheckout(selectedAddressId, shippingAddress),
                    modifier = Modifier.fillMaxWidth().testTag("checkoutSubmit"),
                ) {
                    Text(if (loading) "…" else "Checkout")
                }
                TextButton(onClick = onClearCart, enabled = !loading) {
                    Text("Clear cart")
                }
            }
        }
    }
}
