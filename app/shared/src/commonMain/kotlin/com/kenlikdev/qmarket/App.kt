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
        val m =
            remember(api, tokens, scope) {
                QMarketAppModel(
                    api = api,
                    tokens = tokens,
                    scope = scope,
                    restoredSession = restoredSession,
                )
            }

        LaunchedEffect(restoredSession) {
            m.restoreSessionIfNeeded(restoredSession)
        }

        Scaffold { padding ->

            val screenModifier = Modifier.padding(padding)
            when (val current = m.screen) {
                AppScreen.Login -> {
                    LoginScreen(
                        email = m.email,
                        password = m.password,
                        error = m.error,
                        loading = m.loading,
                        onEmailChange = { m.email = it },
                        onPasswordChange = { m.password = it },
                        onLogin = { m.login() },
                        onCreateAccount = {
                            m.error = null
                            m.screen = AppScreen.Register
                        },
                        onBrowseCatalog = { m.browseAsGuest() },
                        modifier = screenModifier,
                    )
                }

                AppScreen.Register -> {
                    RegisterScreen(
                        email = m.email,
                        password = m.password,
                        firstName = m.firstName,
                        lastName = m.lastName,
                        error = m.error,
                        loading = m.loading,
                        onEmailChange = { m.email = it },
                        onPasswordChange = { m.password = it },
                        onFirstNameChange = { m.firstName = it },
                        onLastNameChange = { m.lastName = it },
                        onRegister = { m.register() },
                        onBackToLogin = {
                            m.error = null
                            m.screen = AppScreen.Login
                        },
                        modifier = screenModifier,
                    )
                }

                AppScreen.Catalog -> {
                    CatalogScreen(
                        products = m.products,
                        catalogQuery = m.catalogQuery,
                        catalogFeaturedOnly = m.catalogFeaturedOnly,
                        catalogCategories = m.catalogCategories,
                        selectedCategoryId = m.catalogCategoryId,
                        onCategorySelect = { id ->
                            m.catalogCategoryId = id
                            m.loadCatalog(navigate = false)
                        },
                        loggedIn = m.loggedIn,
                        userLabel = m.userLabel,
                        cartCount = m.cart?.totalItems,
                        error = m.error,
                        statusMessage = m.statusMessage,
                        loading = m.loading,
                        onQueryChange = { m.catalogQuery = it },
                        onSortNewest = {
                            m.catalogSortBy = "createdAt"
                            m.loadCatalog(navigate = false)
                        },
                        onSortPrice = {
                            m.catalogSortBy = "price"
                            m.loadCatalog(navigate = false)
                        },
                        onSortName = {
                            m.catalogSortBy = "name"
                            m.loadCatalog(navigate = false)
                        },
                        onToggleFeatured = {
                            m.catalogFeaturedOnly = !m.catalogFeaturedOnly
                            m.loadCatalog(navigate = false)
                        },
                        onApplySearch = { m.loadCatalog(navigate = false) },
                        onOpenProduct = { product -> m.openProduct(product) },
                        onAddToCart = { product ->
                            m.runApi {
                                m.cart =
                                    api.addCartItem(
                                        AddCartItemRequestDto(
                                            productId = product.id,
                                            quantity = 1,
                                        ),
                                    )
                                m.statusMessage = "Added ${product.name} to m.cart"
                            }
                        },
                        onCart = { m.loadCart() },
                        onOrders = { m.loadOrders() },
                        onAddresses = { m.loadAddresses() },
                        onProfile = { m.loadProfile() },
                        onAdmin = if (m.isAdmin) {
                            {
                                m.runApi {
                                    m.categories = api.listCategories(activeOnly = false)
                                    m.adminOrders = api.listAdminOrders().content
                                    m.screen = AppScreen.Admin
                                }
                            }
                        } else {
                            null
                        },
                        onNotifications = { m.loadNotifications() },
                        notificationsUnread = m.notificationsUnread,
                        onLogout = { m.logout() },
                        modifier = screenModifier,
                    )
                }

                AppScreen.ProductDetail -> {
                    ProductDetailScreen(
                        product = m.detailProduct,
                        quantity = m.detailQuantity,
                        onQuantityChange = { m.detailQuantity = it },
                        loggedIn = m.loggedIn,
                        userLabel = m.userLabel,
                        cartCount = m.cart?.totalItems,
                        error = m.error,
                        statusMessage = m.statusMessage,
                        loading = m.loading,
                        onBackToCatalog = { m.loadCatalog() },
                        onAddToCart = {
                            val p = m.detailProduct
                            if (p != null) {
                                m.runApi {
                                    m.cart =
                                        api.addCartItem(
                                            AddCartItemRequestDto(
                                                productId = p.id,
                                                quantity = m.detailQuantity,
                                            ),
                                        )
                                    m.statusMessage = "Added ${p.name} ×$m.detailQuantity to m.cart"
                                    m.detailProduct = api.getProduct(p.id)
                                    m.detailQuantity = 1
                                }
                            }
                        },
                        onCart = { m.loadCart() },
                        onOrders = { m.loadOrders() },
                        onAddresses = { m.loadAddresses() },
                        onProfile = { m.loadProfile() },
                        onAdmin = if (m.isAdmin) {
                            {
                                m.runApi {
                                    m.categories = api.listCategories(activeOnly = false)
                                    m.adminOrders = api.listAdminOrders().content
                                    m.screen = AppScreen.Admin
                                }
                            }
                        } else {
                            null
                        },
                        onNotifications = { m.loadNotifications() },
                        notificationsUnread = m.notificationsUnread,
                        onLogout = { m.logout() },
                        modifier = screenModifier,
                    )
                }

                AppScreen.Cart -> {
                    CartScreen(
                        cart = m.cart,
                        addresses = m.addresses,
                        selectedAddressId = m.selectedAddressId,
                        shippingAddress = m.shippingAddress,
                        loggedIn = m.loggedIn,
                        userLabel = m.userLabel,
                        error = m.error,
                        loading = m.loading,
                        onBackToCatalog = { m.loadCatalog() },
                        onDecreaseQty = { item ->
                            m.runApi {
                                m.pendingCheckoutKey = null
                                m.cart =
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
                            m.runApi {
                                m.pendingCheckoutKey = null
                                m.cart =
                                    api.updateCartItem(
                                        item.productId,
                                        UpdateCartItemRequestDto(quantity = item.quantity + 1),
                                    )
                            }
                        },
                        onRemoveItem = { item ->
                            m.runApi {
                                m.pendingCheckoutKey = null
                                m.cart = api.removeCartItem(item.productId)
                            }
                        },
                        onSelectAddress = {
                            m.selectedAddressId = it
                            m.pendingCheckoutKey = null
                        },
                        onManageAddresses = { m.loadAddresses() },
                        onShippingChange = {
                            m.shippingAddress = it
                            if (it.isNotBlank()) m.selectedAddressId = null
                            m.pendingCheckoutKey = null
                        },
                        onCheckout = { m.checkout() },
                        onClearCart = { m.clearCart() },
                        onOrders = { m.loadOrders() },
                        onAddresses = { m.loadAddresses() },
                        onProfile = { m.loadProfile() },
                                                onNotifications = { m.loadNotifications() },
                        notificationsUnread = m.notificationsUnread,
                        onLogout = { m.logout() },
                        modifier = screenModifier,
                    )
                }

                AppScreen.Addresses -> {
                    AddressesScreen(
                        addresses = m.addresses,
                        addrRecipient = m.addrRecipient,
                        addrCity = m.addrCity,
                        addrStreet = m.addrStreet,
                        addrPhone = m.addrPhone,
                        loggedIn = m.loggedIn,
                        userLabel = m.userLabel,
                        cartCount = m.cart?.totalItems,
                        error = m.error,
                        statusMessage = m.statusMessage,
                        loading = m.loading,
                        onBackToCatalog = { m.loadCatalog() },
                        onSetDefault = { addr ->
                            m.runApi {
                                api.updateAddress(
                                    addr.id,
                                    UpdateAddressRequestDto(default = true),
                                )
                                m.addresses = api.listAddresses()
                                m.selectedAddressId = addr.id
                                m.statusMessage = "Default address updated"
                            }
                        },
                        onDelete = { addr ->
                            m.runApi {
                                api.deleteAddress(addr.id)
                                m.addresses = api.listAddresses()
                                if (m.selectedAddressId == addr.id) {
                                    m.selectedAddressId = m.addresses.firstOrNull { it.default }?.id
                                        ?: m.addresses.firstOrNull()?.id
                                }
                            }
                        },
                        onRecipientChange = { m.addrRecipient = it },
                        onCityChange = { m.addrCity = it },
                        onStreetChange = { m.addrStreet = it },
                        onPhoneChange = {
                            m.addrPhone = ClientInputValidation.filterPhoneInput(it)
                        },
                        onSave = {
                            m.runApi {
                                val created =
                                    api.createAddress(
                                        CreateAddressRequestDto(
                                            recipientName = m.addrRecipient.trim(),
                                            city = m.addrCity.trim(),
                                            streetLine1 = m.addrStreet.trim(),
                                            phone = m.addrPhone.trim().ifBlank { null },
                                            default = m.addresses.isEmpty(),
                                        ),
                                    )
                                m.addresses = api.listAddresses()
                                m.selectedAddressId = created.id
                                m.addrRecipient = ""
                                m.addrCity = ""
                                m.addrStreet = ""
                                m.addrPhone = ""
                                m.statusMessage = "Address saved"
                            }
                        },
                        onCart = { m.loadCart() },
                        onOrders = { m.loadOrders() },
                        onProfile = { m.loadProfile() },
                                                onNotifications = { m.loadNotifications() },
                        notificationsUnread = m.notificationsUnread,
                        onLogout = { m.logout() },
                        modifier = screenModifier,
                    )
                }

                AppScreen.Notifications -> {
                    NotificationsScreen(
                        notifications = m.notifications,
                        unreadCount = m.notificationsUnread,
                        loggedIn = m.loggedIn,
                        userLabel = m.userLabel,
                        cartCount = m.cart?.totalItems,
                        error = m.error,
                        statusMessage = m.statusMessage,
                        loading = m.loading,
                        onBackToCatalog = { m.loadCatalog() },
                        onMarkRead = { n ->
                            m.runApi {
                                api.markNotificationRead(n.id)
                                m.notifications = api.listNotifications(size = 50).content
                                m.notificationsUnread = api.notificationsUnreadCount().unread
                            }
                        },
                        onMarkAllRead = {
                            m.runApi {
                                m.notificationsUnread = api.markAllNotificationsRead().unread
                                m.notifications = api.listNotifications(size = 50).content
                                m.statusMessage = "All m.notifications marked read"
                            }
                        },
                        onRefresh = {
                            m.runApi {
                                m.notifications = api.listNotifications(size = 50).content
                                m.notificationsUnread = api.notificationsUnreadCount().unread
                            }
                        },
                        onCart = { m.loadCart() },
                        onOrders = { m.loadOrders() },
                        onAddresses = { m.loadAddresses() },
                        onProfile = { m.loadProfile() },
                                                onNotifications = { m.loadNotifications() },
                        notificationsUnread = m.notificationsUnread,
                        onLogout = { m.logout() },
                        modifier = screenModifier,
                    )
                }

                AppScreen.Profile -> {
                    ProfileScreen(
                        profile = m.profile,
                        firstName = m.firstName,
                        lastName = m.lastName,
                        phone = m.phone,
                        currentPassword = m.currentPassword,
                        newPassword = m.newPassword,
                        loggedIn = m.loggedIn,
                        userLabel = m.userLabel,
                        cartCount = m.cart?.totalItems,
                        error = m.error,
                        statusMessage = m.statusMessage,
                        loading = m.loading,
                        onBackToCatalog = { m.loadCatalog() },
                        onFirstNameChange = { m.firstName = it },
                        onLastNameChange = { m.lastName = it },
                        onPhoneChange = {
                            m.phone = ClientInputValidation.filterPhoneInput(it)
                        },
                        onSaveProfile = {
                            m.runApi {
                                m.profile =
                                    api.updateProfile(
                                        UpdateProfileRequestDto(
                                            firstName = m.firstName.trim().ifBlank { null },
                                            lastName = m.lastName.trim().ifBlank { null },
                                            phone = m.phone.trim().ifBlank { null },
                                        ),
                                    )
                                m.statusMessage = "Profile saved"
                            }
                        },
                        onCurrentPasswordChange = { m.currentPassword = it },
                        onNewPasswordChange = { m.newPassword = it },
                        onUpdatePassword = {
                            m.runApi {
                                api.changePassword(
                                    ChangePasswordRequestDto(
                                        currentPassword = m.currentPassword,
                                        newPassword = m.newPassword,
                                    ),
                                )
                                m.currentPassword = ""
                                m.newPassword = ""
                                m.statusMessage = "Password updated"
                            }
                        },
                        onNotifications = { m.loadNotifications() },
                        onCart = { m.loadCart() },
                        onOrders = { m.loadOrders() },
                        onAddresses = { m.loadAddresses() },
                        onLogout = { m.logout() },
                        modifier = screenModifier,
                    )
                }

                AppScreen.OrderDetail -> {
                    OrderDetailScreen(
                        order = m.detailOrder,
                        loggedIn = m.loggedIn,
                        userLabel = m.userLabel,
                        cartCount = m.cart?.totalItems,
                        error = m.error,
                        statusMessage = m.statusMessage,
                        loading = m.loading,
                        onBackToOrders = { m.loadOrders() },
                        onPay = {
                            val o = m.detailOrder
                            if (o != null) {
                                m.runApi {
                                    m.detailOrder = api.payOrder(o.id)
                                    m.statusMessage = "Payment recorded"
                                    m.orders = api.listMyOrders(size = 50).content
                                }
                            }
                        },
                        onCancel = {
                            val o = m.detailOrder
                            if (o != null) {
                                m.runApi {
                                    m.detailOrder = api.cancelOrder(o.id)
                                    m.statusMessage = "Order cancelled"
                                    m.orders = api.listMyOrders(size = 50).content
                                }
                            }
                        },
                        onRefresh = {
                            val o = m.detailOrder
                            if (o != null) {
                                m.runApi {
                                    m.detailOrder = api.getOrder(o.id)
                                }
                            }
                        },
                        onCart = { m.loadCart() },
                        onAddresses = { m.loadAddresses() },
                        onProfile = { m.loadProfile() },
                                                onNotifications = { m.loadNotifications() },
                        notificationsUnread = m.notificationsUnread,
                        onLogout = { m.logout() },
                        modifier = screenModifier,
                    )
                }

                AppScreen.Orders -> {
                    OrdersScreen(
                        orders = m.orders,
                        loggedIn = m.loggedIn,
                        userLabel = m.userLabel,
                        cartCount = m.cart?.totalItems,
                        error = m.error,
                        statusMessage = m.statusMessage,
                        loading = m.loading,
                        onBackToCatalog = { m.loadCatalog() },
                        onOpenOrder = { order -> m.openOrder(order) },
                        onPay = { order ->
                            m.runApi {
                                val paid = api.payOrder(order.id)
                                m.orders =
                                    m.orders.map {
                                        if (it.id == paid.id) paid else it
                                    }
                                m.statusMessage = "Order paid"
                            }
                        },
                        onCancel = { order ->
                            m.runApi {
                                val cancelled = api.cancelOrder(order.id)
                                m.orders =
                                    m.orders.map {
                                        if (it.id == cancelled.id) cancelled else it
                                    }
                            }
                        },
                        onCart = { m.loadCart() },
                        onAddresses = { m.loadAddresses() },
                        onProfile = { m.loadProfile() },
                        onNotifications = { m.loadNotifications() },
                        notificationsUnread = m.notificationsUnread,
                        onLogout = { m.logout() },
                        modifier = screenModifier,
                    )
                }

                AppScreen.Admin -> {
                    AdminScreen(
                        categories = m.categories,
                        categoryName = m.adminCategoryName,
                        categorySlug = m.adminCategorySlug,
                        editingCategoryId = m.editingCategoryId,
                        adminOrders = m.adminOrders,
                        products = m.products,
                        editingProductId = m.editingProductId,
                        productName = m.adminProductName,
                        productSlug = m.adminProductSlug,
                        productPrice = m.adminProductPrice,
                        productStock = m.adminProductStock,
                        productFeatured = m.adminProductFeatured,
                        productCategoryId = m.adminProductCategoryId,
                        loggedIn = m.loggedIn,
                        userLabel = m.userLabel,
                        cartCount = m.cart?.totalItems,
                        error = m.error,
                        statusMessage = m.statusMessage,
                        loading = m.loading,
                        onBackToCatalog = { m.loadCatalog() },
                        onCategoryNameChange = { name ->
                            val previousAutoSlug = slugifyProductName(m.adminCategoryName)
                            m.adminCategoryName = name
                            if (m.adminCategorySlug.isBlank() || m.adminCategorySlug == previousAutoSlug) {
                                m.adminCategorySlug = slugifyProductName(name)
                            }
                        },
                        onCategorySlugChange = { m.adminCategorySlug = it },
                        onCreateCategory = {
                            m.runApi {
                                api.createCategory(
                                    CreateCategoryRequestDto(
                                        name = m.adminCategoryName.trim(),
                                        slug = m.adminCategorySlug.trim(),
                                    ),
                                )
                                m.categories = api.listCategories(activeOnly = false)
                                m.adminCategoryName = ""
                                m.adminCategorySlug = ""
                                m.editingCategoryId = null
                                m.statusMessage = "Category created"
                            }
                        },
                        onDeleteCategory = { category ->
                            m.runApi {
                                api.deleteCategory(category.id)
                                m.categories = api.listCategories(activeOnly = false)
                                if (m.editingCategoryId == category.id) {
                                    m.editingCategoryId = null
                                    m.adminCategoryName = ""
                                    m.adminCategorySlug = ""
                                }
                                m.statusMessage = "Category deleted"
                            }
                        },
                        onEditCategory = { category ->
                            m.editingCategoryId = category.id
                            m.adminCategoryName = category.name
                            m.adminCategorySlug = category.slug
                            m.error = null
                            m.statusMessage = null
                        },
                        onUpdateCategory = {
                            val id = m.editingCategoryId
                            if (id != null) {
                                m.runApi {
                                    api.updateCategory(
                                        id,
                                        UpdateCategoryRequestDto(
                                            name = m.adminCategoryName.trim(),
                                            slug = m.adminCategorySlug.trim(),
                                        ),
                                    )
                                    m.categories = api.listCategories(activeOnly = false)
                                    m.editingCategoryId = null
                                    m.adminCategoryName = ""
                                    m.adminCategorySlug = ""
                                    m.statusMessage = "Category updated"
                                }
                            }
                        },
                        onClearCategoryEdit = {
                            m.editingCategoryId = null
                            m.adminCategoryName = ""
                            m.adminCategorySlug = ""
                        },
                        onUpdateOrderStatus = { order, status ->
                            m.runApi {
                                api.updateAdminOrderStatus(order.id, status)
                                m.adminOrders = api.listAdminOrders().content
                                m.statusMessage = "Order → ${status.name}"
                            }
                        },
                        onNameChange = { name ->
                            val previousAutoSlug = slugifyProductName(m.adminProductName)
                            m.adminProductName = name
                            if (m.adminProductSlug.isBlank() || m.adminProductSlug == previousAutoSlug) {
                                m.adminProductSlug = slugifyProductName(name)
                            }
                        },
                        onSlugChange = { m.adminProductSlug = it },
                        onPriceChange = { m.adminProductPrice = it },
                        onStockChange = { m.adminProductStock = it.filter { ch -> ch.isDigit() } },
                        onToggleFeatured = { m.adminProductFeatured = !m.adminProductFeatured },
                        onProductCategoryChange = { m.adminProductCategoryId = it },
                        onCreate = {
                            m.runApi {
                                val created =
                                    api.createProduct(
                                        CreateProductRequestDto(
                                            name = m.adminProductName.trim(),
                                            slug = m.adminProductSlug.trim(),
                                            price = m.adminProductPrice.trim(),
                                            stockQuantity = m.adminProductStock.toIntOrNull() ?: 0,
                                            featured = m.adminProductFeatured,
                                            categoryId = m.adminProductCategoryId,
                                        ),
                                    )
                                m.statusMessage = "Created ${created.name}"
                                m.adminProductName = ""
                                m.adminProductSlug = ""
                                m.adminProductPrice = "9.99"
                                m.adminProductStock = "10"
                                m.adminProductFeatured = false
                                m.adminProductCategoryId = null
                                val page = api.listProducts(size = 50)
                                m.products = page.content
                            }
                        },
                        onUpdate = {
                            val id = m.editingProductId
                            if (id != null) {
                                m.runApi {
                                    api.updateProduct(
                                        id,
                                        UpdateProductRequestDto(
                                            name = m.adminProductName.trim(),
                                            slug = m.adminProductSlug.trim(),
                                            price = m.adminProductPrice.trim(),
                                            stockQuantity = m.adminProductStock.toIntOrNull() ?: 0,
                                            featured = m.adminProductFeatured,
                                            categoryId = m.adminProductCategoryId,
                                        ),
                                    )
                                    m.statusMessage = "Updated product"
                                    m.editingProductId = null
                                    m.adminProductName = ""
                                    m.adminProductSlug = ""
                                    m.adminProductPrice = "9.99"
                                    m.adminProductStock = "10"
                                    m.adminProductFeatured = false
                                    m.adminProductCategoryId = null
                                    val page = api.listProducts(size = 50)
                                    m.products = page.content
                                }
                            }
                        },
                        onDelete = { product ->
                            m.runApi {
                                api.deleteProduct(product.id)
                                m.statusMessage = "Deleted ${product.name}"
                                if (m.editingProductId == product.id) {
                                    m.editingProductId = null
                                }
                                val page = api.listProducts(size = 50)
                                m.products = page.content
                            }
                        },
                        onEdit = { product ->
                            m.editingProductId = product.id
                            m.adminProductName = product.name
                            m.adminProductSlug = product.slug
                            m.adminProductPrice = product.price
                            m.adminProductStock = product.stockQuantity.toString()
                            m.adminProductFeatured = product.featured
                            m.adminProductCategoryId = product.categoryId
                            m.error = null
                            m.statusMessage = null
                        },
                        onClearEdit = {
                            m.editingProductId = null
                            m.adminProductName = ""
                            m.adminProductSlug = ""
                            m.adminProductPrice = "9.99"
                            m.adminProductStock = "10"
                            m.adminProductFeatured = false
                            m.adminProductCategoryId = null
                        },
                        onCart = { m.loadCart() },
                        onOrders = { m.loadOrders() },
                        onAddresses = { m.loadAddresses() },
                        onProfile = { m.loadProfile() },
                        onNotifications = { m.loadNotifications() },
                        notificationsUnread = m.notificationsUnread,
                        onLogout = { m.logout() },
                    )

                }

                is AppScreen.OrderDone -> {
                    OrderDoneScreen(
                        order = current.order,
                        error = m.error,
                        statusMessage = m.statusMessage,
                        loading = m.loading,
                        onPay = {
                            m.runApi {
                                val paid = api.payOrder(current.order.id)
                                m.screen = AppScreen.OrderDone(paid)
                                m.statusMessage = "Paid"
                            }
                        },
                        onMyOrders = { m.loadOrders() },
                        onBackToCatalog = { m.loadCatalog() },
                        onOpenCart = { m.loadCart() },
                        modifier = screenModifier,
                    )
                }
            }
        }
    }
}
