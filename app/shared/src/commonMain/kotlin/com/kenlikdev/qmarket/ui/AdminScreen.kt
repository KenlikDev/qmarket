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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kenlikdev.qmarket.api.CategoryDto
import com.kenlikdev.qmarket.api.OrderDto
import com.kenlikdev.qmarket.api.OrderStatusDto
import com.kenlikdev.qmarket.api.ProductDto

/**
 * Admin catalog: categories + create / update / delete products (ROLE_ADMIN / MANAGER on server).
 */
@Composable
fun AdminScreen(
    categories: List<CategoryDto>,
    categoryName: String,
    categorySlug: String,
    adminOrders: List<OrderDto> = emptyList(),
    products: List<ProductDto>,
    editingProductId: String?,
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
    onCategoryNameChange: (String) -> Unit,
    onCategorySlugChange: (String) -> Unit,
    onCreateCategory: () -> Unit,
    onDeleteCategory: (CategoryDto) -> Unit,
    editingCategoryId: String? = null,
    onEditCategory: (CategoryDto) -> Unit = {},
    onUpdateCategory: () -> Unit = {},
    onClearCategoryEdit: () -> Unit = {},
    onUpdateOrderStatus: (OrderDto, OrderStatusDto) -> Unit = { _, _ -> },
    onNameChange: (String) -> Unit,
    onSlugChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onStockChange: (String) -> Unit,
    onToggleFeatured: () -> Unit,
    onCreate: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: (ProductDto) -> Unit,
    onEdit: (ProductDto) -> Unit,
    onClearEdit: () -> Unit,
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
    val categoryFormOk = categoryName.isNotBlank() && categorySlug.isNotBlank()
    val editingCategory = editingCategoryId != null
    val editing = editingProductId != null

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
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            TextButton(
                onClick = onBackToCatalog,
                modifier = Modifier.testTag("adminBackToCatalog"),
            ) {
                Text("← Catalog")
            }
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("adminError"))
            }
            if (statusMessage != null) {
                Text(statusMessage, color = MaterialTheme.colorScheme.primary)
            }

            Text(
                if (editingCategory) "Edit category" else "Categories",
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = categoryName,
                onValueChange = onCategoryNameChange,
                label = { Text("Category name") },
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("adminCategoryName"),
            )
            OutlinedTextField(
                value = categorySlug,
                onValueChange = onCategorySlugChange,
                label = { Text("Category slug") },
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("adminCategorySlug"),
            )
            Button(
                onClick = if (editingCategory) onUpdateCategory else onCreateCategory,
                enabled = !loading && categoryFormOk,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag(if (editingCategory) "adminUpdateCategory" else "adminCreateCategory"),
            ) {
                Text(if (editingCategory) "Save category" else "Create category")
            }
            if (editingCategory) {
                TextButton(
                    onClick = onClearCategoryEdit,
                    enabled = !loading,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("adminClearCategoryEdit"),
                ) {
                    Text("Cancel category edit")
                }
            }
            categories.forEach { category ->
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("adminCategoryCard"),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(category.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${category.slug} · " + if (category.active) "active" else "inactive",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row {
                            TextButton(
                                onClick = { onEditCategory(category) },
                                enabled = !loading,
                                modifier = Modifier.testTag("adminEditCategory"),
                            ) {
                                Text("Edit")
                            }
                            TextButton(
                                onClick = { onDeleteCategory(category) },
                                enabled = !loading,
                                modifier = Modifier.testTag("adminDeleteCategory"),
                            ) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }

            Text(
                if (editing) "Edit product" else "New product",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp),
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
                enabled = !loading,
                modifier = Modifier.testTag("adminProductFeatured"),
            ) {
                Text(if (productFeatured) "Featured: yes" else "Featured: no")
            }

            if (editing) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Button(
                        onClick = onUpdate,
                        enabled = !loading && formOk,
                        modifier =
                            Modifier
                                .weight(1f)
                                .testTag("adminUpdateProduct"),
                    ) {
                        Text("Save changes")
                    }
                    TextButton(
                        onClick = onClearEdit,
                        enabled = !loading,
                        modifier = Modifier.testTag("adminClearEdit"),
                    ) {
                        Text("Cancel edit")
                    }
                }
            } else {
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

            Text(
                "Orders (${adminOrders.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
            if (adminOrders.isEmpty()) {
                Text("No orders yet", style = MaterialTheme.typography.bodySmall)
            }
            adminOrders.forEach { order ->
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("adminOrderCard"),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "${order.status.name} · ${order.totalAmount}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(order.id.take(8) + "…", style = MaterialTheme.typography.bodySmall)
                        Row {
                            order.status.nextAdminTargets().forEach { target ->
                                TextButton(
                                    onClick = { onUpdateOrderStatus(order, target) },
                                    enabled = !loading,
                                    modifier = Modifier.testTag("adminOrderStatus_${target.name}"),
                                ) {
                                    Text(target.name)
                                }
                            }
                        }
                    }
                }
            }

            Text(
                "Catalog (${products.size})",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 24.dp),
            )
            products.forEach { product ->
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("adminProductCard"),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(product.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${product.price} · stock ${product.stockQuantity}" +
                                if (product.featured) " · featured" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row {
                            TextButton(
                                onClick = { onEdit(product) },
                                enabled = !loading,
                                modifier = Modifier.testTag("adminEditProduct"),
                            ) {
                                Text("Edit")
                            }
                            TextButton(
                                onClick = { onDelete(product) },
                                enabled = !loading,
                                modifier = Modifier.testTag("adminDeleteProduct"),
                            ) {
                                Text("Delete")
                            }
                        }
                    }
                }
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
