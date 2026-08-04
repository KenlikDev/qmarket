package com.kenlikdev.qmarket

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kenlikdev.qmarket.api.AddCartItemRequestDto
import com.kenlikdev.qmarket.api.CartDto
import com.kenlikdev.qmarket.api.CreateOrderRequestDto
import com.kenlikdev.qmarket.api.LoginRequestDto
import com.kenlikdev.qmarket.api.OrderDto
import com.kenlikdev.qmarket.api.ProductDto
import com.kenlikdev.qmarket.network.ApiException
import com.kenlikdev.qmarket.network.MutableTokenProvider
import com.kenlikdev.qmarket.network.QMarketApiClient
import com.kenlikdev.qmarket.network.createPlatformHttpClient
import com.kenlikdev.qmarket.network.defaultApiBaseUrl
import kotlinx.coroutines.launch

private sealed interface AppScreen {
    data object Login : AppScreen

    data object Catalog : AppScreen

    data object Cart : AppScreen

    data class OrderDone(val order: OrderDto) : AppScreen
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        val tokens = remember { MutableTokenProvider() }
        val http =
            remember {
                createPlatformHttpClient(
                    baseUrl = defaultApiBaseUrl(),
                    tokenProvider = tokens,
                )
            }
        val api = remember(http) { QMarketApiClient(http) }
        val scope = rememberCoroutineScope()

        var screen by remember { mutableStateOf<AppScreen>(AppScreen.Login) }
        var email by remember { mutableStateOf("admin@qmarket.local") }
        var password by remember { mutableStateOf("admin123") }
        var shippingAddress by remember { mutableStateOf("Moscow, Tverskaya 1") }
        var error by remember { mutableStateOf<String?>(null) }
        var statusMessage by remember { mutableStateOf<String?>(null) }
        var loading by remember { mutableStateOf(false) }
        var products by remember { mutableStateOf<List<ProductDto>>(emptyList()) }
        var cart by remember { mutableStateOf<CartDto?>(null) }
        var userLabel by remember { mutableStateOf<String?>(null) }
        var loggedIn by remember { mutableStateOf(false) }

        fun runApi(block: suspend () -> Unit) {
            scope.launch {
                loading = true
                error = null
                statusMessage = null
                try {
                    block()
                } catch (e: ApiException) {
                    error = e.message
                } catch (e: Exception) {
                    error = e.message ?: e.toString()
                } finally {
                    loading = false
                }
            }
        }

        fun loadCatalog() {
            runApi {
                val page = api.listProducts(size = 50)
                products = page.content
                screen = AppScreen.Catalog
            }
        }

        fun loadCart() {
            runApi {
                cart = api.getCart()
                screen = AppScreen.Cart
            }
        }

        fun refreshCartQuiet() {
            scope.launch {
                try {
                    cart = api.getCart()
                } catch (_: Exception) {
                    // ignore background refresh errors
                }
            }
        }

        Scaffold { padding ->
            when (val current = screen) {
                AppScreen.Login -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("QMarket", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "API: ${defaultApiBaseUrl()}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                        )
                        ErrorText(error)
                        Button(
                            onClick = {
                                runApi {
                                    val auth =
                                        api.login(
                                            LoginRequestDto(
                                                email = email.trim(),
                                                password = password,
                                            ),
                                        )
                                    tokens.token = auth.accessToken
                                    userLabel = auth.user.email
                                    loggedIn = true
                                    val page = api.listProducts(size = 50)
                                    products = page.content
                                    cart = runCatching { api.getCart() }.getOrNull()
                                    screen = AppScreen.Catalog
                                }
                            },
                            enabled = !loading,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                        ) {
                            Text(if (loading) "…" else "Login")
                        }
                        TextButton(
                            onClick = {
                                tokens.clear()
                                loggedIn = false
                                userLabel = null
                                cart = null
                                loadCatalog()
                            },
                            enabled = !loading,
                        ) {
                            Text("Browse catalog (anonymous)")
                        }
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
                        }
                    }
                }

                AppScreen.Catalog -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding),
                    ) {
                        TopBar(
                            title = "Catalog",
                            subtitle = userLabel ?: "Guest",
                            onCart =
                                if (loggedIn) {
                                    { loadCart() }
                                } else {
                                    null
                                },
                            cartCount = cart?.totalItems,
                            onLogout = {
                                tokens.clear()
                                loggedIn = false
                                userLabel = null
                                products = emptyList()
                                cart = null
                                error = null
                                screen = AppScreen.Login
                            },
                        )
                        ErrorText(error, modifier = Modifier.padding(horizontal = 16.dp))
                        statusMessage?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        if (loading && products.isEmpty()) {
                            LoadingCenter()
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(products, key = { it.id }) { product ->
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                product.name,
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                            Text(
                                                "${product.price} · stock ${product.stockQuantity}",
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            product.shortDescription?.let {
                                                Text(it, style = MaterialTheme.typography.bodySmall)
                                            }
                                            if (loggedIn) {
                                                Button(
                                                    onClick = {
                                                        runApi {
                                                            cart =
                                                                api.addCartItem(
                                                                    AddCartItemRequestDto(
                                                                        productId = product.id,
                                                                        quantity = 1,
                                                                    ),
                                                                )
                                                            statusMessage =
                                                                "Added ${product.name} to cart"
                                                        }
                                                    },
                                                    enabled = !loading && product.stockQuantity > 0,
                                                    modifier = Modifier.padding(top = 8.dp),
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

                AppScreen.Cart -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding),
                    ) {
                        TopBar(
                            title = "Cart",
                            subtitle = userLabel ?: "Guest",
                            onCart = null,
                            cartCount = cart?.totalItems,
                            onLogout = {
                                tokens.clear()
                                loggedIn = false
                                userLabel = null
                                products = emptyList()
                                cart = null
                                screen = AppScreen.Login
                            },
                        )
                        TextButton(onClick = { loadCatalog() }) {
                            Text("← Back to catalog")
                        }
                        ErrorText(error, modifier = Modifier.padding(horizontal = 16.dp))
                        val currentCart = cart
                        if (loading && currentCart == null) {
                            LoadingCenter()
                        } else if (currentCart == null || currentCart.items.isEmpty()) {
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
                                items(currentCart.items, key = { it.productId }) { item ->
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                item.productName,
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                            Text(
                                                "×${item.quantity} · ${item.lineTotal}",
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            TextButton(
                                                onClick = {
                                                    runApi {
                                                        cart = api.removeCartItem(item.productId)
                                                    }
                                                },
                                                enabled = !loading,
                                            ) {
                                                Text("Remove")
                                            }
                                        }
                                    }
                                }
                            }
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Total: ${currentCart.totalPrice} (${currentCart.totalItems} items)",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                OutlinedTextField(
                                    value = shippingAddress,
                                    onValueChange = { shippingAddress = it },
                                    label = { Text("Shipping address") },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                )
                                Button(
                                    onClick = {
                                        runApi {
                                            val order =
                                                api.createOrder(
                                                    CreateOrderRequestDto(
                                                        shippingAddress = shippingAddress.trim(),
                                                    ),
                                                )
                                            cart = api.getCart()
                                            screen = AppScreen.OrderDone(order)
                                        }
                                    },
                                    enabled = !loading && shippingAddress.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (loading) "…" else "Checkout")
                                }
                                TextButton(
                                    onClick = {
                                        runApi { cart = api.clearCart() }
                                    },
                                    enabled = !loading,
                                ) {
                                    Text("Clear cart")
                                }
                            }
                        }
                    }
                }

                is AppScreen.OrderDone -> {
                    val order = current.order
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
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
                            onClick = {
                                runApi {
                                    val paid = api.payOrder(order.id)
                                    screen = AppScreen.OrderDone(paid)
                                    statusMessage = "Paid"
                                }
                            },
                            enabled = !loading && order.status.name == "PENDING",
                            modifier = Modifier.padding(top = 16.dp),
                        ) {
                            Text("Pay (mock)")
                        }
                        statusMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = { loadCatalog() }) {
                            Text("Back to catalog")
                        }
                        TextButton(onClick = { loadCart() }) {
                            Text("Open cart")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String,
    onCart: (() -> Unit)?,
    cartCount: Int?,
    onLogout: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onCart != null) {
                TextButton(onClick = onCart) {
                    Text(
                        if (cartCount != null && cartCount > 0) {
                            "Cart ($cartCount)"
                        } else {
                            "Cart"
                        },
                    )
                }
            }
            TextButton(onClick = onLogout) {
                Text("Logout")
            }
        }
    }
}

@Composable
private fun ErrorText(
    error: String?,
    modifier: Modifier = Modifier,
) {
    if (error != null) {
        Text(
            error,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun LoadingCenter() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}
