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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kenlikdev.qmarket.api.ProductDto

@Composable
fun CatalogScreen(
    products: List<ProductDto>,
    catalogQuery: String,
    catalogFeaturedOnly: Boolean,
    loggedIn: Boolean,
    userLabel: String?,
    cartCount: Int?,
    error: String?,
    statusMessage: String?,
    loading: Boolean,
    onQueryChange: (String) -> Unit,
    onSortNewest: () -> Unit,
    onSortPrice: () -> Unit,
    onSortName: () -> Unit,
    onToggleFeatured: () -> Unit,
    onApplySearch: () -> Unit,
    onOpenProduct: (ProductDto) -> Unit = {},
    onAddToCart: (ProductDto) -> Unit,
    onCart: () -> Unit,
    onOrders: () -> Unit,
    onAddresses: () -> Unit,
    onProfile: () -> Unit,
    onAdmin: (() -> Unit)? = null,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopBar(
            title = "Catalog",
            subtitle = userLabel ?: "Guest",
            loggedIn = loggedIn,
            cartCount = cartCount,
            onCart = onCart,
            onOrders = onOrders,
            onAddresses = onAddresses,
            onProfile = onProfile,
            onAdmin = onAdmin,
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
        OutlinedTextField(
            value = catalogQuery,
            onValueChange = onQueryChange,
            label = { Text("Search") },
            singleLine = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("catalogSearch"),
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onSortNewest) { Text("Newest") }
            TextButton(onClick = onSortPrice) { Text("Price") }
            TextButton(onClick = onSortName) { Text("Name") }
            TextButton(onClick = onToggleFeatured) {
                Text(if (catalogFeaturedOnly) "Featured ✓" else "Featured")
            }
            Button(
                onClick = onApplySearch,
                enabled = !loading,
                modifier = Modifier.testTag("catalogSearchApply"),
            ) {
                Text("Apply")
            }
        }
        if (loading && products.isEmpty()) {
            LoadingCenter()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(products, key = { it.id }) { product ->
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !loading) { onOpenProduct(product) }
                                .testTag("catalogProduct_${product.id}"),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(product.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${product.price} · stock ${product.stockQuantity}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            product.shortDescription?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                            if (loggedIn) {
                                Button(
                                    onClick = { onAddToCart(product) },
                                    enabled = !loading && product.stockQuantity > 0,
                                    modifier =
                                        Modifier
                                            .padding(top = 8.dp)
                                            .testTag("catalogAdd_${product.id}"),
                                ) {
                                    Text("Add to cart")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
