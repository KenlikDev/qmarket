package com.kenlikdev.qmarket.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Minimal admin catalog form: create product (ROLE_ADMIN / ROLE_MANAGER on server).
 */
@Composable
fun AdminScreen(
    productName: String,
    productSlug: String,
    productPrice: String,
    productStock: String,
    productFeatured: Boolean,
    loggedIn: Boolean,
    userLabel: String?,
    cartCount: Int?,
    error: String?,
    statusMessage: String?,
    loading: Boolean,
    onBackToCatalog: () -> Unit,
    onNameChange: (String) -> Unit,
    onSlugChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onStockChange: (String) -> Unit,
    onToggleFeatured: () -> Unit,
    onCreate: () -> Unit,
    onCart: () -> Unit,
    onOrders: () -> Unit,
    onAddresses: () -> Unit,
    onProfile: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val priceOk = productPrice.toDoubleOrNull()?.let { it >= 0.0 } == true
    val stockOk = productStock.toIntOrNull()?.let { it >= 0 } == true
    val formOk =
        productName.isNotBlank() &&
            productSlug.isNotBlank() &&
            priceOk &&
            stockOk

    Column(modifier = modifier.fillMaxSize()) {
        TopBar(
            title = "Admin",
            subtitle = userLabel ?: "Guest",
            loggedIn = loggedIn,
            cartCount = cartCount,
            onCart = onCart,
            onOrders = onOrders,
            onAddresses = onAddresses,
            onProfile = onProfile,
            onAdmin = null,
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
        Column(
            modifier =
                Modifier
                    .weight(1f, fill = true)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            TextButton(onClick = onBackToCatalog) {
                Text("← Catalog")
            }
            Text(
                "Create product",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            OutlinedTextField(
                value = productName,
                onValueChange = onNameChange,
                label = { Text("Name") },
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("adminProductName"),
            )
            OutlinedTextField(
                value = productSlug,
                onValueChange = onSlugChange,
                label = { Text("Slug") },
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("adminProductSlug"),
            )
            OutlinedTextField(
                value = productPrice,
                onValueChange = onPriceChange,
                label = { Text("Price") },
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("adminProductPrice"),
            )
            OutlinedTextField(
                value = productStock,
                onValueChange = onStockChange,
                label = { Text("Stock") },
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("adminProductStock"),
            )
            TextButton(
                onClick = onToggleFeatured,
                modifier = Modifier.testTag("adminProductFeatured"),
            ) {
                Text(if (productFeatured) "Featured: yes" else "Featured: no")
            }
            Button(
                onClick = onCreate,
                enabled = !loading && formOk,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag("adminCreateProduct"),
            ) {
                Text("Create product")
            }
        }
    }
}

/** Derive a URL-safe slug from a product name (client-side default). */
fun slugifyProductName(name: String): String =
    name
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(80)
        .ifBlank { "product" }
