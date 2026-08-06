package com.kenlikdev.qmarket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.kenlikdev.qmarket.api.ChangePasswordRequestDto
import com.kenlikdev.qmarket.api.LoginRequestDto
import com.kenlikdev.qmarket.api.ProfileDto
import com.kenlikdev.qmarket.api.RegisterRequestDto
import com.kenlikdev.qmarket.api.UpdateProfileRequestDto
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

    data object Register : AppScreen

    data object Catalog : AppScreen

    data object Cart : AppScreen

    data object Orders : AppScreen

    data object Profile : AppScreen

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
        var firstName by remember { mutableStateOf("") }
        var lastName by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var currentPassword by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        var profile by remember { mutableStateOf<ProfileDto?>(null) }
        var shippingAddress by remember { mutableStateOf("Moscow, Tverskaya 1") }
        var error by remember { mutableStateOf<String?>(null) }
        var statusMessage by remember { mutableStateOf<String?>(null) }
        var loading by remember { mutableStateOf(false) }
        var products by remember { mutableStateOf<List<ProductDto>>(emptyList()) }
        var cart by remember { mutableStateOf<CartDto?>(null) }
        var orders by remember { mutableStateOf<List<OrderDto>>(emptyList()) }
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

        fun loadOrders() {
            runApi {
                val page = api.listMyOrders(size = 50)
                orders = page.content
                screen = AppScreen.Orders
            }
        }

        fun loadProfile() {
            runApi {
                val p = api.getProfile()
                profile = p
                firstName = p.firstName.orEmpty()
                lastName = p.lastName.orEmpty()
                phone = p.phone.orEmpty()
                screen = AppScreen.Profile
            }
        }

        fun logout() {
            tokens.clear()
            loggedIn = false
            userLabel = null
            products = emptyList()
            cart = null
            orders = emptyList()
            error = null
            screen = AppScreen.Login
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
                                error = null
                                screen = AppScreen.Register
                            },
                            enabled = !loading,
                        ) {
                            Text("Create account")
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


                AppScreen.Register -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Register", style = MaterialTheme.typography.headlineMedium)
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
                        OutlinedTextField(
                            value = firstName,
                            onValueChange = { firstName = it },
                            label = { Text("First name") },
                            singleLine = true,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                        )
                        OutlinedTextField(
                            value = lastName,
                            onValueChange = { lastName = it },
                            label = { Text("Last name") },
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
                                        api.register(
                                            RegisterRequestDto(
                                                email = email.trim(),
                                                password = password,
                                                firstName = firstName.trim().ifBlank { null },
                                                lastName = lastName.trim().ifBlank { null },
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
                            enabled = !loading && isValidEmail(email) && password.length >= 8 && isValidPersonName(firstName) && isValidPersonName(lastName),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                        ) {
                            Text(if (loading) "…" else "Register")
                        }
                        TextButton(
                            onClick = {
                                error = null
                                screen = AppScreen.Login
                            },
                            enabled = !loading,
                        ) {
                            Text("Back to login")
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
                            loggedIn = loggedIn,
                            cartCount = cart?.totalItems,
                            onCart = { loadCart() },
                            onOrders = { loadOrders() },
                            onProfile = { loadProfile() },
                            onLogout = { logout() },
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
                            loggedIn = loggedIn,
                            cartCount = cart?.totalItems,
                            onCart = null,
                            onOrders = { loadOrders() },
                            onProfile = { loadProfile() },
                            onLogout = { logout() },
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


                AppScreen.Profile -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding),
                    ) {
                        TopBar(
                            title = "Profile",
                            subtitle = userLabel ?: "Guest",
                            loggedIn = loggedIn,
                            cartCount = cart?.totalItems,
                            onCart = { loadCart() },
                            onOrders = { loadOrders() },
                            onProfile = null,
                            onLogout = { logout() },
                        )
                        TextButton(onClick = { loadCatalog() }) {
                            Text("← Back to catalog")
                        }
                        ErrorText(error, modifier = Modifier.padding(horizontal = 16.dp))
                        statusMessage?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        if (loading && profile == null) {
                            LoadingCenter()
                        } else {
                            Column(
                                modifier =
                                    Modifier
                                        .weight(1f, fill = true)
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp),
                            ) {
                                Text(
                                    profile?.email ?: "",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                OutlinedTextField(
                                    value = firstName,
                                    onValueChange = { firstName = it },
                                    label = { Text("First name") },
                                    singleLine = true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                )
                                OutlinedTextField(
                                    value = lastName,
                                    onValueChange = { lastName = it },
                                    label = { Text("Last name") },
                                    singleLine = true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                )
                                OutlinedTextField(
                                    value = phone,
                                    onValueChange = { input ->
                                        phone =
                                            input.filter { ch ->
                                                ch.isDigit() ||
                                                    ch == '+' ||
                                                    ch.isWhitespace() ||
                                                    ch == '-' ||
                                                    ch == '(' ||
                                                    ch == ')'
                                            }
                                    },
                                    label = { Text("Phone") },
                                    singleLine = true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                )
                                if (phone.isNotEmpty() && !isValidPhoneInput(phone)) {
                                    Text(
                                        "Phone: 7-15 digits, optional leading +",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (firstName.isNotEmpty() && !isValidPersonName(firstName)) {
                                    Text(
                                        "First name: letters, spaces, hyphen, apostrophe, period",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (lastName.isNotEmpty() && !isValidPersonName(lastName)) {
                                    Text(
                                        "Last name: letters, spaces, hyphen, apostrophe, period",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Button(
                                    onClick = {
                                        runApi {
                                            profile =
                                                api.updateProfile(
                                                    UpdateProfileRequestDto(
                                                        firstName = firstName.trim().ifBlank { null },
                                                        lastName = lastName.trim().ifBlank { null },
                                                        phone = phone.trim().ifBlank { null },
                                                    ),
                                                )
                                            statusMessage = "Profile saved"
                                        }
                                    },
                                    enabled =
                                        !loading &&
                                            isValidPhoneInput(phone) &&
                                            isValidPersonName(firstName) &&
                                            isValidPersonName(lastName),
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 12.dp),
                                ) {
                                    Text("Save profile")
                                }
                                Text(
                                    "Change password",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(top = 24.dp),
                                )
                                OutlinedTextField(
                                    value = currentPassword,
                                    onValueChange = { currentPassword = it },
                                    label = { Text("Current password") },
                                    singleLine = true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                )
                                OutlinedTextField(
                                    value = newPassword,
                                    onValueChange = { newPassword = it },
                                    label = { Text("New password") },
                                    singleLine = true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                )
                                Button(
                                    onClick = {
                                        runApi {
                                            api.changePassword(
                                                ChangePasswordRequestDto(
                                                    currentPassword = currentPassword,
                                                    newPassword = newPassword,
                                                ),
                                            )
                                            currentPassword = ""
                                            newPassword = ""
                                            statusMessage = "Password updated"
                                        }
                                    },
                                    enabled = !loading && currentPassword.isNotBlank() && newPassword.length >= 8,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 12.dp),
                                ) {
                                    Text("Update password")
                                }
                            }
                        }
                    }
                }

                AppScreen.Orders -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding),
                    ) {
                        TopBar(
                            title = "My orders",
                            subtitle = userLabel ?: "Guest",
                            loggedIn = loggedIn,
                            cartCount = cart?.totalItems,
                            onCart = { loadCart() },
                            onOrders = null,
                            onProfile = { loadProfile() },
                            onLogout = { logout() },
                        )
                        TextButton(onClick = { loadCatalog() }) {
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
                                    Card(modifier = Modifier.fillMaxWidth()) {
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
                                            Row {
                                                if (order.status.name == "PENDING") {
                                                    TextButton(
                                                        onClick = {
                                                            runApi {
                                                                val paid = api.payOrder(order.id)
                                                                orders =
                                                                    orders.map {
                                                                        if (it.id == paid.id) {
                                                                            paid
                                                                        } else {
                                                                            it
                                                                        }
                                                                    }
                                                                statusMessage = "Order paid"
                                                            }
                                                        },
                                                        enabled = !loading,
                                                    ) {
                                                        Text("Pay")
                                                    }
                                                    TextButton(
                                                        onClick = {
                                                            runApi {
                                                                val cancelled =
                                                                    api.cancelOrder(order.id)
                                                                orders =
                                                                    orders.map {
                                                                        if (it.id == cancelled.id) {
                                                                            cancelled
                                                                        } else {
                                                                            it
                                                                        }
                                                                    }
                                                            }
                                                        },
                                                        enabled = !loading,
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
                        TextButton(onClick = { loadOrders() }) {
                            Text("My orders")
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
    loggedIn: Boolean,
    cartCount: Int?,
    onCart: (() -> Unit)?,
    onOrders: (() -> Unit)?,
    onProfile: (() -> Unit)?,
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
            if (loggedIn && onProfile != null) {
                TextButton(onClick = onProfile) {
                    Text("Profile")
                }
            }
            if (loggedIn && onOrders != null) {
                TextButton(onClick = onOrders) {
                    Text("Orders")
                }
            }
            if (loggedIn && onCart != null) {
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
                Text(if (loggedIn) "Logout" else "Login")
            }
        }
    }
}


private fun isValidPhoneInput(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return true
    if (trimmed.any { it.isLetter() }) return false
    val digits = trimmed.filter { it.isDigit() }
    return digits.length in 7..15
}

private fun isValidEmail(value: String): Boolean {
    val v = value.trim()
    return v.contains("@") && v.substringAfter("@").contains(".")
}

private fun isValidPersonName(value: String): Boolean {
    val v = value.trim()
    if (v.isEmpty()) return true
    if (v.length > 100) return false
    if (v.all { it.isDigit() || it.isWhitespace() }) return false
    return v.all { ch ->
        ch.isLetter() || ch.isWhitespace() || ch == '-' || ch == '.' || ch.code == 39
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
