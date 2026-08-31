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
import com.kenlikdev.qmarket.api.CategoryDto
import com.kenlikdev.qmarket.api.CreateCategoryRequestDto
import com.kenlikdev.qmarket.api.UpdateCategoryRequestDto
import com.kenlikdev.qmarket.api.ChangePasswordRequestDto
import com.kenlikdev.qmarket.api.CreateAddressRequestDto
import com.kenlikdev.qmarket.api.CreateOrderRequestDto
import com.kenlikdev.qmarket.api.CreateProductRequestDto
import com.kenlikdev.qmarket.api.UpdateProductRequestDto
import com.kenlikdev.qmarket.api.LoginRequestDto
import com.kenlikdev.qmarket.api.NotificationDto
import com.kenlikdev.qmarket.api.OrderDto
import com.kenlikdev.qmarket.api.OrderStatusDto
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
import com.kenlikdev.qmarket.ui.CheckoutIdempotency
import com.kenlikdev.qmarket.ui.CatalogScreen
import com.kenlikdev.qmarket.ui.ProductDetailScreen
import com.kenlikdev.qmarket.ui.LoginScreen
import com.kenlikdev.qmarket.ui.OrderDoneScreen
import com.kenlikdev.qmarket.ui.OrderDetailScreen
import com.kenlikdev.qmarket.ui.NotificationsScreen
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
        var detailProduct by remember { mutableStateOf<ProductDto?>(null) }
        var detailQuantity by remember { mutableStateOf(1) }
        var catalogQuery by remember { mutableStateOf("") }
        var catalogSortBy by remember { mutableStateOf("createdAt") }
        var catalogFeaturedOnly by remember { mutableStateOf(false) }
        var catalogCategoryId by remember { mutableStateOf<String?>(null) }
        var catalogCategories by remember { mutableStateOf<List<CategoryDto>>(emptyList()) }
        var isAdmin by remember { mutableStateOf(false) }
        var adminProductName by remember { mutableStateOf("") }
        var adminProductSlug by remember { mutableStateOf("") }
        var adminProductPrice by remember { mutableStateOf("9.99") }
        var adminProductStock by remember { mutableStateOf("10") }
        var adminProductFeatured by remember { mutableStateOf(false) }
        var adminProductCategoryId by remember { mutableStateOf<String?>(null) }
        var editingProductId by remember { mutableStateOf<String?>(null) }
        var editingCategoryId by remember { mutableStateOf<String?>(null) }
        var categories by remember { mutableStateOf<List<CategoryDto>>(emptyList()) }
        var adminCategoryName by remember { mutableStateOf("") }
        var adminCategorySlug by remember { mutableStateOf("") }
        var adminOrders by remember { mutableStateOf<List<OrderDto>>(emptyList()) }
        var cart by remember { mutableStateOf<CartDto?>(null) }
        var orders by remember { mutableStateOf<List<OrderDto>>(emptyList()) }
        var detailOrder by remember { mutableStateOf<OrderDto?>(null) }
        var notifications by remember { mutableStateOf<List<NotificationDto>>(emptyList()) }
        var notificationsUnread by remember { mutableStateOf(0L) }
        var userLabel by remember { mutableStateOf(tokens.sessionEmail()) }
        var loggedIn by remember { mutableStateOf(restoredSession) }
        var addresses by remember { mutableStateOf<List<AddressDto>>(emptyList()) }
        var selectedAddressId by remember { mutableStateOf<String?>(null) }
        var pendingCheckoutKey by remember { mutableStateOf<String?>(null) }
        var checkoutLocked by remember { mutableStateOf(false) }
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

        fun clearUserScopedUiState() {
            orders = emptyList()
            adminOrders = emptyList()
            addresses = emptyList()
            selectedAddressId = null
            profile = null
            firstName = ""
            lastName = ""
            phone = ""
            currentPassword = ""
            newPassword = ""
            cart = null
            detailProduct = null
            detailOrder = null
            notifications = emptyList()
            notificationsUnread = 0L
            pendingCheckoutKey = null
            checkoutLocked = false
            detailQuantity = 1
            editingProductId = null
            editingCategoryId = null
            statusMessage = null
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
                api.clearBearerTokenCache()
                clearUserScopedUiState()
                loggedIn = false
                isAdmin = false
                userLabel = null
                products = emptyList()
                screen = AppScreen.Login
                error = e.message ?: "Session expired — please sign in again"
            } finally {
                loading = false
            }
        }

        fun loadCatalog(navigate: Boolean = true) {
            runApi {
                catalogCategories =
                    runCatching { api.listCategories(activeOnly = true) }.getOrElse { catalogCategories }
                val page =
                    api.listProducts(
                        size = 50,
                        q = CatalogFilterParams.queryParam(catalogQuery),
                        sortBy = catalogSortBy,
                        sortDir = CatalogFilterParams.sortDir(catalogSortBy),
                        featuredOnly = CatalogFilterParams.featuredParam(catalogFeaturedOnly),
                        categoryId = CatalogFilterParams.categoryParam(catalogCategoryId),
                    )
                products = page.content
                if (navigate) screen = AppScreen.Catalog
            }
        }

        fun openProduct(product: ProductDto) {
            detailProduct = product
            detailQuantity = 1
            screen = AppScreen.ProductDetail
            runApi {
                detailProduct = api.getProduct(product.id)
                detailQuantity = 1
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

        fun openOrder(order: OrderDto) {
            detailOrder = order
            screen = AppScreen.OrderDetail
            runApi {
                detailOrder = api.getOrder(order.id)
            }
        }

        fun loadNotifications() {
            runApi {
                notifications = api.listNotifications(size = 50).content
                notificationsUnread = api.notificationsUnreadCount().unread
                screen = AppScreen.Notifications
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
            val refresh = tokens.refreshToken()
            runApi {
                if (!refresh.isNullOrBlank()) {
                    api.logout(refresh)
                }
                isAdmin = false
                tokens.clear()
                api.clearBearerTokenCache()
                clearUserScopedUiState()
                loggedIn = false
                userLabel = null
                products = emptyList()
                error = null
                screen = AppScreen.Login
            }
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
                                api.clearBearerTokenCache()
                                clearUserScopedUiState()
                                applySession(auth.user.email)
                                val page = api.listProducts(size = 50)
                                products = page.content
                                cart = runCatching { api.getCart() }.getOrNull()
                                notificationsUnread =
                                    runCatching { api.notificationsUnreadCount().unread }.getOrDefault(0L)
                                screen = AppScreen.Catalog
                            }
                        },
                        onCreateAccount = {
                            error = null
                            screen = AppScreen.Register
                        },
                        onBrowseCatalog = {
                            tokens.clear()
                            api.clearBearerTokenCache()
                            clearUserScopedUiState()
                            loggedIn = false
                            userLabel = null
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
                                api.clearBearerTokenCache()
                                clearUserScopedUiState()
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
                        catalogCategories = catalogCategories,
                        selectedCategoryId = catalogCategoryId,
                        onCategorySelect = { id ->
                            catalogCategoryId = id
                            loadCatalog(navigate = false)
                        },
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
                        onOpenProduct = { product -> openProduct(product) },
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
                            {
                                runApi {
                                    categories = api.listCategories(activeOnly = false)
                                    adminOrders = api.listAdminOrders().content
                                    screen = AppScreen.Admin
                                }
                            }
                        } else {
                            null
                        },
                        onNotifications = { loadNotifications() },
                        notificationsUnread = notificationsUnread,
                        onLogout = { logout() },
                        modifier = screenModifier,
                    )
                }

                AppScreen.ProductDetail -> {
                    ProductDetailScreen(
                        product = detailProduct,
                        quantity = detailQuantity,
                        onQuantityChange = { detailQuantity = it },
                        loggedIn = loggedIn,
                        userLabel = userLabel,
                        cartCount = cart?.totalItems,
                        error = error,
                        statusMessage = statusMessage,
                        loading = loading,
                        onBackToCatalog = { loadCatalog() },
                        onAddToCart = {
                            val p = detailProduct
                            if (p != null) {
                                runApi {
                                    cart =
                                        api.addCartItem(
                                            AddCartItemRequestDto(
                                                productId = p.id,
                                                quantity = detailQuantity,
                                            ),
                                        )
                                    statusMessage = "Added ${p.name} ×$detailQuantity to cart"
                                    detailProduct = api.getProduct(p.id)
                                    detailQuantity = 1
                                }
                            }
                        },
                        onCart = { loadCart() },
                        onOrders = { loadOrders() },
                        onAddresses = { loadAddresses() },
                        onProfile = { loadProfile() },
                        onAdmin = if (isAdmin) {
                            {
                                runApi {
                                    categories = api.listCategories(activeOnly = false)
                                    adminOrders = api.listAdminOrders().content
                                    screen = AppScreen.Admin
                                }
                            }
                        } else {
                            null
                        },
                        onNotifications = { loadNotifications() },
                        notificationsUnread = notificationsUnread,
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
                                pendingCheckoutKey = null
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
                                pendingCheckoutKey = null
                                cart =
                                    api.updateCartItem(
                                        item.productId,
                                        UpdateCartItemRequestDto(quantity = item.quantity + 1),
                                    )
                            }
                        },
                        onRemoveItem = { item ->
                            runApi {
                                pendingCheckoutKey = null
                                cart = api.removeCartItem(item.productId)
                            }
                        },
                        onSelectAddress = {
                            selectedAddressId = it
                            pendingCheckoutKey = null
                        },
                        onManageAddresses = { loadAddresses() },
                        onShippingChange = {
                            shippingAddress = it
                            if (it.isNotBlank()) selectedAddressId = null
                            pendingCheckoutKey = null
                        },
                        onCheckout = {
                            if (checkoutLocked || loading) return@CartScreen
                            checkoutLocked = true
                            runApi {
                                try {
                                    val checkoutKey =
                                        pendingCheckoutKey
                                            ?: CheckoutIdempotency.newKey().also { pendingCheckoutKey = it }
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
                                    pendingCheckoutKey = null
                                    cart = api.getCart()
                                    screen = AppScreen.OrderDone(order)
                                } finally {
                                    checkoutLocked = false
                                }
                            }
                        },
                        onClearCart = {
                            runApi {
                                pendingCheckoutKey = null
                                cart = api.clearCart()
                            }
                        },
                        onOrders = { loadOrders() },
                        onAddresses = { loadAddresses() },
                        onProfile = { loadProfile() },
                                                onNotifications = { loadNotifications() },
                        notificationsUnread = notificationsUnread,
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
                                                onNotifications = { loadNotifications() },
                        notificationsUnread = notificationsUnread,
                        onLogout = { logout() },
                        modifier = screenModifier,
                    )
                }

                AppScreen.Notifications -> {
                    NotificationsScreen(
                        notifications = notifications,
                        unreadCount = notificationsUnread,
                        loggedIn = loggedIn,
                        userLabel = userLabel,
                        cartCount = cart?.totalItems,
                        error = error,
                        statusMessage = statusMessage,
                        loading = loading,
                        onBackToCatalog = { loadCatalog() },
                        onMarkRead = { n ->
                            runApi {
                                api.markNotificationRead(n.id)
                                notifications = api.listNotifications(size = 50).content
                                notificationsUnread = api.notificationsUnreadCount().unread
                            }
                        },
                        onMarkAllRead = {
                            runApi {
                                notificationsUnread = api.markAllNotificationsRead().unread
                                notifications = api.listNotifications(size = 50).content
                                statusMessage = "All notifications marked read"
                            }
                        },
                        onRefresh = {
                            runApi {
                                notifications = api.listNotifications(size = 50).content
                                notificationsUnread = api.notificationsUnreadCount().unread
                            }
                        },
                        onCart = { loadCart() },
                        onOrders = { loadOrders() },
                        onAddresses = { loadAddresses() },
                        onProfile = { loadProfile() },
                                                onNotifications = { loadNotifications() },
                        notificationsUnread = notificationsUnread,
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
                        onNotifications = { loadNotifications() },
                        onCart = { loadCart() },
                        onOrders = { loadOrders() },
                        onAddresses = { loadAddresses() },
                        onLogout = { logout() },
                        modifier = screenModifier,
                    )
                }

                AppScreen.OrderDetail -> {
                    OrderDetailScreen(
                        order = detailOrder,
                        loggedIn = loggedIn,
                        userLabel = userLabel,
                        cartCount = cart?.totalItems,
                        error = error,
                        statusMessage = statusMessage,
                        loading = loading,
                        onBackToOrders = { loadOrders() },
                        onPay = {
                            val o = detailOrder
                            if (o != null) {
                                runApi {
                                    detailOrder = api.payOrder(o.id)
                                    statusMessage = "Payment recorded"
                                    orders = api.listMyOrders(size = 50).content
                                }
                            }
                        },
                        onCancel = {
                            val o = detailOrder
                            if (o != null) {
                                runApi {
                                    detailOrder = api.cancelOrder(o.id)
                                    statusMessage = "Order cancelled"
                                    orders = api.listMyOrders(size = 50).content
                                }
                            }
                        },
                        onRefresh = {
                            val o = detailOrder
                            if (o != null) {
                                runApi {
                                    detailOrder = api.getOrder(o.id)
                                }
                            }
                        },
                        onCart = { loadCart() },
                        onAddresses = { loadAddresses() },
                        onProfile = { loadProfile() },
                                                onNotifications = { loadNotifications() },
                        notificationsUnread = notificationsUnread,
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
                        onOpenOrder = { order -> openOrder(order) },
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
                        onNotifications = { loadNotifications() },
                        notificationsUnread = notificationsUnread,
                        onLogout = { logout() },
                        modifier = screenModifier,
                    )
                }

                AppScreen.Admin -> {
                    AdminScreen(
                        categories = categories,
                        categoryName = adminCategoryName,
                        categorySlug = adminCategorySlug,
                        editingCategoryId = editingCategoryId,
                        adminOrders = adminOrders,
                        products = products,
                        editingProductId = editingProductId,
                        productName = adminProductName,
                        productSlug = adminProductSlug,
                        productPrice = adminProductPrice,
                        productStock = adminProductStock,
                        productFeatured = adminProductFeatured,
                        productCategoryId = adminProductCategoryId,
                        loggedIn = loggedIn,
                        userLabel = userLabel,
                        cartCount = cart?.totalItems,
                        error = error,
                        statusMessage = statusMessage,
                        loading = loading,
                        onBackToCatalog = { loadCatalog() },
                        onCategoryNameChange = { name ->
                            val previousAutoSlug = slugifyProductName(adminCategoryName)
                            adminCategoryName = name
                            if (adminCategorySlug.isBlank() || adminCategorySlug == previousAutoSlug) {
                                adminCategorySlug = slugifyProductName(name)
                            }
                        },
                        onCategorySlugChange = { adminCategorySlug = it },
                        onCreateCategory = {
                            runApi {
                                api.createCategory(
                                    CreateCategoryRequestDto(
                                        name = adminCategoryName.trim(),
                                        slug = adminCategorySlug.trim(),
                                    ),
                                )
                                categories = api.listCategories(activeOnly = false)
                                adminCategoryName = ""
                                adminCategorySlug = ""
                                editingCategoryId = null
                                statusMessage = "Category created"
                            }
                        },
                        onDeleteCategory = { category ->
                            runApi {
                                api.deleteCategory(category.id)
                                categories = api.listCategories(activeOnly = false)
                                if (editingCategoryId == category.id) {
                                    editingCategoryId = null
                                    adminCategoryName = ""
                                    adminCategorySlug = ""
                                }
                                statusMessage = "Category deleted"
                            }
                        },
                        onEditCategory = { category ->
                            editingCategoryId = category.id
                            adminCategoryName = category.name
                            adminCategorySlug = category.slug
                            error = null
                            statusMessage = null
                        },
                        onUpdateCategory = {
                            val id = editingCategoryId
                            if (id != null) {
                                runApi {
                                    api.updateCategory(
                                        id,
                                        UpdateCategoryRequestDto(
                                            name = adminCategoryName.trim(),
                                            slug = adminCategorySlug.trim(),
                                        ),
                                    )
                                    categories = api.listCategories(activeOnly = false)
                                    editingCategoryId = null
                                    adminCategoryName = ""
                                    adminCategorySlug = ""
                                    statusMessage = "Category updated"
                                }
                            }
                        },
                        onClearCategoryEdit = {
                            editingCategoryId = null
                            adminCategoryName = ""
                            adminCategorySlug = ""
                        },
                        onUpdateOrderStatus = { order, status ->
                            runApi {
                                api.updateAdminOrderStatus(order.id, status)
                                adminOrders = api.listAdminOrders().content
                                statusMessage = "Order → ${status.name}"
                            }
                        },
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
                        onProductCategoryChange = { adminProductCategoryId = it },
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
                                            categoryId = adminProductCategoryId,
                                        ),
                                    )
                                statusMessage = "Created ${created.name}"
                                adminProductName = ""
                                adminProductSlug = ""
                                adminProductPrice = "9.99"
                                adminProductStock = "10"
                                adminProductFeatured = false
                                adminProductCategoryId = null
                                val page = api.listProducts(size = 50)
                                products = page.content
                            }
                        },
                        onUpdate = {
                            val id = editingProductId
                            if (id != null) {
                                runApi {
                                    api.updateProduct(
                                        id,
                                        UpdateProductRequestDto(
                                            name = adminProductName.trim(),
                                            slug = adminProductSlug.trim(),
                                            price = adminProductPrice.trim(),
                                            stockQuantity = adminProductStock.toIntOrNull() ?: 0,
                                            featured = adminProductFeatured,
                                            categoryId = adminProductCategoryId,
                                        ),
                                    )
                                    statusMessage = "Updated product"
                                    editingProductId = null
                                    adminProductName = ""
                                    adminProductSlug = ""
                                    adminProductPrice = "9.99"
                                    adminProductStock = "10"
                                    adminProductFeatured = false
                                    adminProductCategoryId = null
                                    val page = api.listProducts(size = 50)
                                    products = page.content
                                }
                            }
                        },
                        onDelete = { product ->
                            runApi {
                                api.deleteProduct(product.id)
                                statusMessage = "Deleted ${product.name}"
                                if (editingProductId == product.id) {
                                    editingProductId = null
                                }
                                val page = api.listProducts(size = 50)
                                products = page.content
                            }
                        },
                        onEdit = { product ->
                            editingProductId = product.id
                            adminProductName = product.name
                            adminProductSlug = product.slug
                            adminProductPrice = product.price
                            adminProductStock = product.stockQuantity.toString()
                            adminProductFeatured = product.featured
                            adminProductCategoryId = product.categoryId
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
                            adminProductCategoryId = null
                        },
                        onCart = { loadCart() },
                        onOrders = { loadOrders() },
                        onAddresses = { loadAddresses() },
                        onProfile = { loadProfile() },
                        onNotifications = { loadNotifications() },
                        notificationsUnread = notificationsUnread,
                        onLogout = { logout() },
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
