package com.kenlikdev.qmarket

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kenlikdev.qmarket.api.AddCartItemRequestDto
import com.kenlikdev.qmarket.api.AddressDto
import com.kenlikdev.qmarket.api.CartDto
import com.kenlikdev.qmarket.api.ChangePasswordRequestDto
import com.kenlikdev.qmarket.api.CreateAddressRequestDto
import com.kenlikdev.qmarket.api.CreateOrderRequestDto
import com.kenlikdev.qmarket.api.CreateProductRequestDto
import com.kenlikdev.qmarket.api.UpdateProductRequestDto
import com.kenlikdev.qmarket.api.LoginRequestDto
import com.kenlikdev.qmarket.api.OrderDto
import com.kenlikdev.qmarket.api.ProductDto
import com.kenlikdev.qmarket.api.ProfileDto
import com.kenlikdev.qmarket.api.RegisterRequestDto
import com.kenlikdev.qmarket.api.UpdateAddressRequestDto
import com.kenlikdev.qmarket.api.UpdateCartItemRequestDto
import com.kenlikdev.qmarket.api.UpdateProfileRequestDto
import com.kenlikdev.qmarket.network.ApiException
import com.kenlikdev.qmarket.network.MutableTokenProvider
import com.kenlikdev.qmarket.network.QMarketApiClient
import com.kenlikdev.qmarket.network.createPlatformHttpClient
import com.kenlikdev.qmarket.network.createPlatformSessionStore
import com.kenlikdev.qmarket.network.defaultApiBaseUrl
import com.kenlikdev.qmarket.ui.AddressesScreen
import com.kenlikdev.qmarket.ui.AdminScreen
import com.kenlikdev.qmarket.ui.slugifyProductName
import com.kenlikdev.qmarket.ui.AppScreen
import com.kenlikdev.qmarket.ui.CartScreen
import com.kenlikdev.qmarket.ui.CatalogFilterParams
import com.kenlikdev.qmarket.ui.CatalogScreen
import com.kenlikdev.qmarket.ui.LoginScreen
import com.kenlikdev.qmarket.ui.OrderDoneScreen
import com.kenlikdev.qmarket.ui.OrdersScreen
import com.kenlikdev.qmarket.ui.ProfileScreen
import com.kenlikdev.qmarket.ui.RegisterScreen
import com.kenlikdev.qmarket.validation.ClientInputValidation
import kotlinx.coroutines.launch

@Composable
@Preview
fun App() {
    MaterialTheme {
        val sessionStore = remember { createPlatformSessionStore() }
        val tokens = remember { MutableTokenProvider(sessionStore) }
        val http =
            remember {
                createPlatformHttpClient(
                    baseUrl = defaultApiBaseUrl(),
                    tokenProvider = tokens,
                )
            }
        val api = remember(http) { QMarketApiClient(http) }
        val scope = rememberCoroutineScope()

        val restoredSession = tokens.hasSession()
        var screen by remember {
            mutableStateOf<AppScreen>(
                if (restoredSession) AppScreen.Catalog else AppScreen.Login,
            )
        }
        var email by remember { mutableStateOf(tokens.sessionEmail() ?: "admin@qmarket.local") }
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
        var catalogQuery by remember { mutableStateOf("") }
        var catalogSortBy by remember { mutableStateOf("createdAt") }
        var catalogFeaturedOnly by remember { mutableStateOf(false) }
        var isAdmin by remember { mutableStateOf(false) }
        var adminProductName by remember { mutableStateOf("") }
        var adminProductSlug by remember { mutableStateOf("") }
        var adminProductPrice by remember { mutableStateOf("9.99") }
        var adminProductStock by remember { mutableStateOf("10") }
        var adminProductFeatured by remember { mutableStateOf(false) }
        var editingProductId by remember { mutableStateOf<String?>(null) }
        var cart by remember { mutableStateOf<CartDto?>(null) }
        var orders by remember { mutableStateOf<List<OrderDto>>(emptyList()) }
        var userLabel by remember { mutableStateOf(tokens.sessionEmail()) }
        var loggedIn by remember { mutableStateOf(restoredSession) }
        var addresses by remember { mutableStateOf<List<AddressDto>>(emptyList()) }
        var selectedAddressId by remember { mutableStateOf<String?>(null) }
        var addrRecipient by remember { mutableStateOf("") }
        var addrCity by remember { mutableStateOf("") }
        var addrStreet by remember { mutableStateOf("") }
        var addrPhone by remember { mutableStateOf("") }

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

        // Restore session: load catalog (and cart) after process restart
        LaunchedEffect(restoredSession) {
            if (!restoredSession) return@LaunchedEffect
            loading = true
            error = null
            try {
                val page = api.listProducts(size = 50)
                products = page.content
                cart = runCatching { api.getCart() }.getOrNull()
                runCatching { api.getProfile() }.onSuccess { p ->
                    tokens.applyRoles(p.roles)
                    isAdmin = tokens.isAdmin()
                }
                loggedIn = true
                userLabel = tokens.sessionEmail()
                isAdmin = tokens.isAdmin()
                screen = AppScreen.Catalog
            } catch (e: Exception) {
                tokens.clear()
                loggedIn = false
                isAdmin = false
                userLabel = null
                products = emptyList()
                cart = null
                screen = AppScreen.Login
                error = e.message ?: "Session expired — please sign in again"
            } finally {
                loading = false
            }
        }

        fun loadCatalog(navigate: Boolean = true) {
            runApi {
                val page =
                    api.listProducts(
                        size = 50,
                        q = CatalogFilterParams.queryParam(catalogQuery),
                        sortBy = catalogSortBy,
                        sortDir = CatalogFilterParams.sortDir(catalogSortBy),
                        featuredOnly = CatalogFilterParams.featuredParam(catalogFeaturedOnly),
                    )
                products = page.content
                if (navigate) screen = AppScreen.Catalog
            }
        }

        fun loadCart() {
            runApi {
                cart = api.getCart()
                addresses = runCatching { api.listAddresses() }.getOrElse { addresses }
                if (selectedAddressId == null) {
                    selectedAddressId = addresses.firstOrNull { it.default }?.id
                        ?: addresses.firstOrNull()?.id
                }
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

        fun loadAddresses(navigate: Boolean = true) {
            runApi {
                addresses = api.listAddresses()
                if (selectedAddressId == null) {
                    selectedAddressId = addresses.firstOrNull { it.default }?.id
                        ?: addresses.firstOrNull()?.id
                }
                if (navigate) screen = AppScreen.Addresses
            }
        }

        fun logout() {
            isAdmin = false
            tokens.clear()
            loggedIn = false
            userLabel = null
            products = emptyList()
            cart = null
            orders = emptyList()
            error = null
            screen = AppScreen.Login
        }

        fun applySession(authEmail: String) {
            userLabel = authEmail
            loggedIn = true
            isAdmin = tokens.isAdmin()
        }

        Scaffold { padding ->
            val screenModifier = Modifier.padding(padding)
            when (val current = screen) {
                AppScreen.Login -> {
                    LoginScreen(
                        email = email,
                        password = password,
                        error = error,
                        loading = loading,
                        onEmailChange = { email = it },
                        onPasswordChange = { password = it },
                        onLogin = {
                            runApi {
                                val auth =
                                    api.login(
                                        LoginRequestDto(
                                            email = email.trim(),
                                            password = password,
                                        ),
                                    )
                                tokens.applyAuth(auth)
                                applySession(auth.user.email)
                                val page = api.listProducts(size = 50)
                                products = page.content
                                cart = runCatching { api.getCart() }.getOrNull()
                                screen = AppScreen.Catalog
                            }
                        },
                        onCreateAccount = {
                            error = null
                            screen = AppScreen.Register
                        },
                        onBrowseCatalog = {
                            tokens.clear()
                            loggedIn = false
                            userLabel = null
                            cart = null
                            loadCatalog()
                        },
                        modifier = screenModifier,
                    )
                }

                AppScreen.Register -> {
                    RegisterScreen(
                        email = email,
                        password = password,
                        firstName = firstName,
                        lastName = lastName,
                        error = error,
                        loading = loading,
                        onEmailChange = { email = it },
                        onPasswordChange = { password = it },
                        onFirstNameChange = { firstName = it },
                        onLastNameChange = { lastName = it },
                        onRegister = {
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
                                tokens.applyAuth(auth)
                                applySession(auth.user.email)
                                val page = api.listProducts(size = 50)
                                products = page.content
                                cart = runCatching { api.getCart() }.getOrNull()
                                screen = AppScreen.Catalog
                            }
                        },
                        onBackToLogin = {
                            error = null
                            screen = AppScreen.Login
                        },
                        modifier = screenModifier,
                    )
                }

                AppScreen.Catalog -> {
                    CatalogScreen(
                        products = products,
                        catalogQuery = catalogQuery,
                        catalogFeaturedOnly = catalogFeaturedOnly,
                        loggedIn = loggedIn,
                        userLabel = userLabel,
                        cartCount = cart?.totalItems,
                        error = error,
                        statusMessage = statusMessage,
                        loading = loading,
                        onQueryChange = { catalogQuery = it },
                        onSortNewest = {
                            catalogSortBy = "createdAt"
                            loadCatalog(navigate = false)
                        },
                        onSortPrice = {
                            catalogSortBy = "price"
                            loadCatalog(navigate = false)
                        },
                        onSortName = {
                            catalogSortBy = "name"
                            loadCatalog(navigate = false)
                        },
                        onToggleFeatured = {
                            catalogFeaturedOnly = !catalogFeaturedOnly
                            loadCatalog(navigate = false)
                        },
                        onApplySearch = { loadCatalog(navigate = false) },
                        onAddToCart = { product ->
                            runApi {
                                cart =
                                    api.addCartItem(
                                        AddCartItemRequestDto(
                                            productId = product.id,
                                            quantity = 1,
                                        ),
                                    )
                                statusMessage = "Added ${product.name} to cart"
                            }
                        },
                        onCart = { loadCart() },
                        onOrders = { loadOrders() },
                        onAddresses = { loadAddresses() },
                        onProfile = { loadProfile() },
                        onAdmin = if (isAdmin) {
                            { screen = AppScreen.Admin }
                        } else {
                            null
                        },
                        onLogout = { logout() },
                        modifier = screenModifier,
                    )
                }

                AppScreen.Cart -> {
                    CartScreen(
                        cart = cart,
                        addresses = addresses,
                        selectedAddressId = selectedAddressId,
                        shippingAddress = shippingAddress,
                        loggedIn = loggedIn,
                        userLabel = userLabel,
                        error = error,
                        loading = loading,
                        onBackToCatalog = { loadCatalog() },
                        onDecreaseQty = { item ->
                            runApi {
                                cart =
                                    if (item.quantity <= 1) {
                                        api.removeCartItem(item.productId)
                                    } else {
                                        api.updateCartItem(
                                            item.productId,
                                            UpdateCartItemRequestDto(quantity = item.quantity - 1),
                                        )
                                    }
                            }
                        },
                        onIncreaseQty = { item ->
                            runApi {
                                cart =
                                    api.updateCartItem(
                                        item.productId,
                                        UpdateCartItemRequestDto(quantity = item.quantity + 1),
                                    )
                            }
                        },
                        onRemoveItem = { item ->
                            runApi { cart = api.removeCartItem(item.productId) }
                        },
                        onSelectAddress = { selectedAddressId = it },
                        onManageAddresses = { loadAddresses() },
                        onShippingChange = {
                            shippingAddress = it
                            if (it.isNotBlank()) selectedAddressId = null
                        },
                        onCheckout = {
                            runApi {
                                val checkoutKey =
                                    buildString {
                                        repeat(32) {
                                            append("0123456789abcdef"[kotlin.random.Random.nextInt(16)])
                                        }
                                    }
                                val order =
                                    api.createOrder(
                                        request =
                                            CreateOrderRequestDto(
                                                addressId = selectedAddressId,
                                                shippingAddress =
                                                    if (selectedAddressId == null) {
                                                        shippingAddress.trim().ifBlank { null }
                                                    } else {
                                                        null
                                                    },
                                            ),
                                        idempotencyKey = checkoutKey,
                                    )
                                cart = api.getCart()
                                screen = AppScreen.OrderDone(order)
                            }
                        },
                        onClearCart = {
                            runApi { cart = api.clearCart() }
                        },
                        onOrders = { loadOrders() },
                        onAddresses = { loadAddresses() },
                        onProfile = { loadProfile() },
                        onLogout = { logout() },
                        modifier = screenModifier,
                    )
                }

                AppScreen.Addresses -> {
                    AddressesScreen(
                        addresses = addresses,
                        addrRecipient = addrRecipient,
                        addrCity = addrCity,
                        addrStreet = addrStreet,
                        addrPhone = addrPhone,
                        loggedIn = loggedIn,
                        userLabel = userLabel,
                        cartCount = cart?.totalItems,
                        error = error,
                        statusMessage = statusMessage,
                        loading = loading,
                        onBackToCatalog = { loadCatalog() },
                        onSetDefault = { addr ->
                            runApi {
                                api.updateAddress(
                                    addr.id,
                                    UpdateAddressRequestDto(default = true),
                                )
                                addresses = api.listAddresses()
                                selectedAddressId = addr.id
                                statusMessage = "Default address updated"
                            }
                        },
                        onDelete = { addr ->
                            runApi {
                                api.deleteAddress(addr.id)
                                addresses = api.listAddresses()
                                if (selectedAddressId == addr.id) {
                                    selectedAddressId = addresses.firstOrNull { it.default }?.id
                                        ?: addresses.firstOrNull()?.id
                                }
                            }
                        },
                        onRecipientChange = { addrRecipient = it },
                        onCityChange = { addrCity = it },
                        onStreetChange = { addrStreet = it },
                        onPhoneChange = {
                            addrPhone = ClientInputValidation.filterPhoneInput(it)
                        },
                        onSave = {
                            runApi {
                                val created =
                                    api.createAddress(
                                        CreateAddressRequestDto(
                                            recipientName = addrRecipient.trim(),
                                            city = addrCity.trim(),
                                            streetLine1 = addrStreet.trim(),
                                            phone = addrPhone.trim().ifBlank { null },
                                            default = addresses.isEmpty(),
                                        ),
                                    )
                                addresses = api.listAddresses()
                                selectedAddressId = created.id
                                addrRecipient = ""
                                addrCity = ""
                                addrStreet = ""
                                addrPhone = ""
                                statusMessage = "Address saved"
                            }
                        },
                        onCart = { loadCart() },
                        onOrders = { loadOrders() },
                        onProfile = { loadProfile() },
                        onLogout = { logout() },
                        modifier = screenModifier,
                    )
                }

                AppScreen.Profile -> {
                    ProfileScreen(
                        profile = profile,
                        firstName = firstName,
                        lastName = lastName,
                        phone = phone,
                        currentPassword = currentPassword,
                        newPassword = newPassword,
                        loggedIn = loggedIn,
                        userLabel = userLabel,
                        cartCount = cart?.totalItems,
                        error = error,
                        statusMessage = statusMessage,
                        loading = loading,
                        onBackToCatalog = { loadCatalog() },
                        onFirstNameChange = { firstName = it },
                        onLastNameChange = { lastName = it },
                        onPhoneChange = {
                            phone = ClientInputValidation.filterPhoneInput(it)
                        },
                        onSaveProfile = {
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
                        onCurrentPasswordChange = { currentPassword = it },
                        onNewPasswordChange = { newPassword = it },
                        onUpdatePassword = {
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
                        onCart = { loadCart() },
                        onOrders = { loadOrders() },
                        onAddresses = { loadAddresses() },
                        onLogout = { logout() },
                        modifier = screenModifier,
                    )
                }

                AppScreen.Orders -> {
                    OrdersScreen(
                        orders = orders,
                        loggedIn = loggedIn,
                        userLabel = userLabel,
                        cartCount = cart?.totalItems,
                        error = error,
                        statusMessage = statusMessage,
                        loading = loading,
                        onBackToCatalog = { loadCatalog() },
                        onPay = { order ->
                            runApi {
                                val paid = api.payOrder(order.id)
                                orders =
                                    orders.map {
                                        if (it.id == paid.id) paid else it
                                    }
                                statusMessage = "Order paid"
                            }
                        },
                        onCancel = { order ->
                            runApi {
                                val cancelled = api.cancelOrder(order.id)
                                orders =
                                    orders.map {
                                        if (it.id == cancelled.id) cancelled else it
                                    }
                            }
                        },
                        onCart = { loadCart() },
                        onAddresses = { loadAddresses() },
                        onProfile = { loadProfile() },
                        onLogout = { logout() },
                        modifier = screenModifier,
                    )
                }

                AppScreen.Admin -> {
                    AdminScreen(
                        products = products,
                        editingProductId = editingProductId,
                        productName = adminProductName,
                        productSlug = adminProductSlug,
                        productPrice = adminProductPrice,
                        productStock = adminProductStock,
                        productFeatured = adminProductFeatured,
                        loggedIn = loggedIn,
                        userLabel = userLabel,
                        cartCount = cart?.totalItems,
                        error = error,
                        statusMessage = statusMessage,
                        loading = loading,
                        onBackToCatalog = { loadCatalog() },
                        onNameChange = { name ->
                            val previousAutoSlug = slugifyProductName(adminProductName)
                            adminProductName = name
                            if (adminProductSlug.isBlank() || adminProductSlug == previousAutoSlug) {
                                adminProductSlug = slugifyProductName(name)
                            }
                        },
                        onSlugChange = { adminProductSlug = it },
                        onPriceChange = { adminProductPrice = it },
                        onStockChange = { adminProductStock = it.filter { ch -> ch.isDigit() } },
                        onToggleFeatured = { adminProductFeatured = !adminProductFeatured },
                        onCreate = {
                            runApi {
                                val created =
                                    api.createProduct(
                                        CreateProductRequestDto(
                                            name = adminProductName.trim(),
                                            slug = adminProductSlug.trim(),
                                            price = adminProductPrice.trim(),
                                            stockQuantity = adminProductStock.toIntOrNull() ?: 0,
                                            featured = adminProductFeatured,
                                            active = true,
                                        ),
                                    )
                                statusMessage = "Created ${created.name}"
                                adminProductName = ""
                                adminProductSlug = ""
                                adminProductPrice = "9.99"
                                adminProductStock = "10"
                                adminProductFeatured = false
                                editingProductId = null
                                loadCatalog(navigate = false)
                                screen = AppScreen.Admin
                            }
                        },
                        onUpdate = {
                            val id = editingProductId
                            if (id != null) {
                                runApi {
                                    val updated =
                                        api.updateProduct(
                                            id,
                                            UpdateProductRequestDto(
                                                name = adminProductName.trim(),
                                                slug = adminProductSlug.trim(),
                                                price = adminProductPrice.trim(),
                                                stockQuantity = adminProductStock.toIntOrNull() ?: 0,
                                                featured = adminProductFeatured,
                                            ),
                                        )
                                    statusMessage = "Updated ${updated.name}"
                                    editingProductId = null
                                    adminProductName = ""
                                    adminProductSlug = ""
                                    adminProductPrice = "9.99"
                                    adminProductStock = "10"
                                    adminProductFeatured = false
                                    loadCatalog(navigate = false)
                                    screen = AppScreen.Admin
                                }
                            }
                        },
                        onDelete = { product ->
                            runApi {
                                api.deleteProduct(product.id)
                                statusMessage = "Deleted ${product.name}"
                                if (editingProductId == product.id) {
                                    editingProductId = null
                                    adminProductName = ""
                                    adminProductSlug = ""
                                }
                                loadCatalog(navigate = false)
                                screen = AppScreen.Admin
                            }
                        },
                        onEdit = { product ->
                            editingProductId = product.id
                            adminProductName = product.name
                            adminProductSlug = product.slug
                            adminProductPrice = product.price
                            adminProductStock = product.stockQuantity.toString()
                            adminProductFeatured = product.featured
                            error = null
                            statusMessage = null
                        },
                        onClearEdit = {
                            editingProductId = null
                            adminProductName = ""
                            adminProductSlug = ""
                            adminProductPrice = "9.99"
                            adminProductStock = "10"
                            adminProductFeatured = false
                        },
                        onCart = { loadCart() },
                        onOrders = { loadOrders() },
                        onAddresses = { loadAddresses() },
                        onProfile = { loadProfile() },
                        onLogout = { logout() },
                        modifier = screenModifier,
                    )
                }

                is AppScreen.OrderDone -> {
                    OrderDoneScreen(
                        order = current.order,
                        error = error,
                        statusMessage = statusMessage,
                        loading = loading,
                        onPay = {
                            runApi {
                                val paid = api.payOrder(current.order.id)
                                screen = AppScreen.OrderDone(paid)
                                statusMessage = "Paid"
                            }
                        },
                        onMyOrders = { loadOrders() },
                        onBackToCatalog = { loadCatalog() },
                        onOpenCart = { loadCart() },
                        modifier = screenModifier,
                    )
                }
            }
        }
    }
}
