package com.kenlikdev.qmarket.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kenlikdev.qmarket.api.ProductDto

@Composable
fun ProductDetailScreen(
    product: ProductDto?,
    quantity: Int = 1,
    onQuantityChange: (Int) -> Unit = {},
    loggedIn: Boolean,
    userLabel: String?,
    cartCount: Int?,
    error: String?,
    statusMessage: String?,
    loading: Boolean,
    onBackToCatalog: () -> Unit,
    onAddToCart: () -> Unit,
    onCart: () -> Unit,
    onOrders: () -> Unit,
    onAddresses: () -> Unit,
    onProfile: () -> Unit,
    onAdmin: (() -> Unit)? = null,
    onNotifications: (() -> Unit)? = null,
    notificationsUnread: Long = 0L,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxQty = product?.stockQuantity?.coerceAtLeast(0) ?: 0
    Column(modifier = modifier.fillMaxSize()) {
        TopBar(
            title = product?.name ?: "Product",
            subtitle = userLabel ?: "Guest",
            loggedIn = loggedIn,
            cartCount = cartCount,
            onCart = onCart,
            onOrders = onOrders,
            onAddresses = onAddresses,
            onProfile = onProfile,
            onAdmin = onAdmin,
            onNotifications = onNotifications,
            notificationsUnread = notificationsUnread,
            onLogout = onLogout,
        )
        ErrorText(error, modifier = Modifier.padding(horizontal = 16.dp))
        statusMessage?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        TextButton(
            onClick = onBackToCatalog,
            enabled = !loading,
            modifier =
                Modifier
                    .padding(horizontal = 8.dp)
                    .testTag("productDetailBack"),
        ) {
            Text("← Catalog")
        }
        when {
            loading && product == null -> LoadingCenter()
            product == null -> {
                Text(
                    "Product not found",
                    modifier =
                        Modifier
                            .padding(16.dp)
                            .testTag("productDetailMissing"),
                )
            }
            else -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                            .testTag("productDetailContent"),
                ) {
                    Text(
                        product.name,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.testTag("productDetailName"),
                    )
                    product.categoryName?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Text(
                        product.price,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 12.dp).testTag("productDetailPrice"),
                    )
                    product.compareAtPrice?.let {
                        Text(
                            "Was $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Text(
                        "Stock: ${product.stockQuantity}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp).testTag("productDetailStock"),
                    )
                    product.sku?.let {
                        Text("SKU: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    product.shortDescription?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    product.description?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp).testTag("productDetailDescription"),
                        )
                    }
                    if (loggedIn && maxQty > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 20.dp).testTag("productDetailQtyRow"),
                        ) {
                            Text("Qty", style = MaterialTheme.typography.bodyMedium)
                            TextButton(
                                onClick = { onQuantityChange((quantity - 1).coerceAtLeast(1)) },
                                enabled = !loading && quantity > 1,
                                modifier = Modifier.testTag("productDetailQtyMinus"),
                            ) {
                                Text("−")
                            }
                            Text(
                                "$quantity",
                                modifier = Modifier.padding(horizontal = 8.dp).testTag("productDetailQty"),
                            )
                            TextButton(
                                onClick = { onQuantityChange((quantity + 1).coerceAtMost(maxQty)) },
                                enabled = !loading && quantity < maxQty,
                                modifier = Modifier.testTag("productDetailQtyPlus"),
                            ) {
                                Text("+")
                            }
                        }
                        Button(
                            onClick = onAddToCart,
                            enabled = !loading,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                                    .testTag("productDetailAddToCart"),
                        ) {
                            Text("Add to cart")
                        }
                    } else if (loggedIn) {
                        Text(
                            "Out of stock",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 24.dp).testTag("productDetailOutOfStock"),
                        )
                    } else {
                        Text(
                            "Sign in to add to cart",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                }
            }
        }
    }
}
