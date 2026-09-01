package com.kenlikdev.qmarket

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kenlikdev.qmarket.api.AddressDto
import com.kenlikdev.qmarket.api.CartDto
import com.kenlikdev.qmarket.api.CategoryDto
import com.kenlikdev.qmarket.api.NotificationDto
import com.kenlikdev.qmarket.api.OrderDto
import com.kenlikdev.qmarket.api.ProductDto
import com.kenlikdev.qmarket.api.ProfileDto
import com.kenlikdev.qmarket.network.ApiException
import com.kenlikdev.qmarket.network.MutableTokenProvider
import com.kenlikdev.qmarket.network.QMarketApiClient
import com.kenlikdev.qmarket.ui.AppScreen
import com.kenlikdev.qmarket.ui.CatalogFilterParams
import com.kenlikdev.qmarket.ui.CheckoutIdempotency
import com.kenlikdev.qmarket.api.UpdateOrderStatusRequestDto
import com.kenlikdev.qmarket.api.UpdateProfileRequestDto
import com.kenlikdev.qmarket.api.UpdateProductRequestDto
import com.kenlikdev.qmarket.api.UpdateCategoryRequestDto
import com.kenlikdev.qmarket.api.UpdateCartItemRequestDto
import com.kenlikdev.qmarket.api.UpdateAddressRequestDto
import com.kenlikdev.qmarket.api.OrderStatusDto
import com.kenlikdev.qmarket.api.CreateProductRequestDto
import com.kenlikdev.qmarket.api.CreateCategoryRequestDto
import com.kenlikdev.qmarket.api.CreateAddressRequestDto
import com.kenlikdev.qmarket.api.ChangePasswordRequestDto
import com.kenlikdev.qmarket.api.AddCartItemRequestDto
import com.kenlikdev.qmarket.api.RegisterRequestDto
import com.kenlikdev.qmarket.api.LoginRequestDto
import com.kenlikdev.qmarket.api.CreateOrderRequestDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Mutable UI + session state for the shopper/admin shell.
 * Keeps [App] as composition root while isolating loaders and session logic.
 */
class QMarketAppModel(
    val api: QMarketApiClient,
    val tokens: MutableTokenProvider,
    private val scope: CoroutineScope,
    restoredSession: Boolean,
) {
    var screen by mutableStateOf<AppScreen>(
        if (restoredSession) AppScreen.Catalog else AppScreen.Login,
    )
    var email by mutableStateOf(tokens.sessionEmail() ?: "admin@qmarket.local")
    var password by mutableStateOf("admin123")
    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")
    var phone by mutableStateOf("")
    var currentPassword by mutableStateOf("")
    var newPassword by mutableStateOf("")
    var profile by mutableStateOf<ProfileDto?>(null)
    var shippingAddress by mutableStateOf("Moscow, Tverskaya 1")
    var error by mutableStateOf<String?>(null)
    var statusMessage by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    var products by mutableStateOf<List<ProductDto>>(emptyList())
    var detailProduct by mutableStateOf<ProductDto?>(null)
    var detailQuantity by mutableStateOf(1)
    var catalogQuery by mutableStateOf("")
    var catalogSortBy by mutableStateOf("createdAt")
    var catalogFeaturedOnly by mutableStateOf(false)
    var catalogCategoryId by mutableStateOf<String?>(null)
    var catalogCategories by mutableStateOf<List<CategoryDto>>(emptyList())
    var isAdmin by mutableStateOf(false)
    var adminProductName by mutableStateOf("")
    var adminProductSlug by mutableStateOf("")
    var adminProductPrice by mutableStateOf("9.99")
    var adminProductStock by mutableStateOf("10")
    var adminProductFeatured by mutableStateOf(false)
    var adminProductCategoryId by mutableStateOf<String?>(null)
    var editingProductId by mutableStateOf<String?>(null)
    var editingCategoryId by mutableStateOf<String?>(null)
    var categories by mutableStateOf<List<CategoryDto>>(emptyList())
    var adminCategoryName by mutableStateOf("")
    var adminCategorySlug by mutableStateOf("")
    var adminOrders by mutableStateOf<List<OrderDto>>(emptyList())
    var cart by mutableStateOf<CartDto?>(null)
    var orders by mutableStateOf<List<OrderDto>>(emptyList())
    var detailOrder by mutableStateOf<OrderDto?>(null)
    var notifications by mutableStateOf<List<NotificationDto>>(emptyList())
    var notificationsUnread by mutableStateOf(0L)
    var userLabel by mutableStateOf(tokens.sessionEmail())
    var loggedIn by mutableStateOf(restoredSession)
    var addresses by mutableStateOf<List<AddressDto>>(emptyList())
    var selectedAddressId by mutableStateOf<String?>(null)
    var pendingCheckoutKey by mutableStateOf<String?>(null)
    var checkoutLocked by mutableStateOf(false)
    var addrRecipient by mutableStateOf("")
    var addrCity by mutableStateOf("")
    var addrStreet by mutableStateOf("")
    var addrPhone by mutableStateOf("")

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

    suspend fun restoreSessionIfNeeded(restoredSession: Boolean) {
        if (!restoredSession) return
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

    fun login() {
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
    }

    fun register() {
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
    }

    fun browseAsGuest() {
        tokens.clear()
        api.clearBearerTokenCache()
        clearUserScopedUiState()
        loggedIn = false
        userLabel = null
        loadCatalog()
    }

    fun checkout() {
        if (checkoutLocked || loading) return
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
    }

    fun clearCart() {
        runApi {
            pendingCheckoutKey = null
            cart = api.clearCart()
        }
    }

    fun openAdmin() {
        runApi {
            categories = api.listCategories(activeOnly = false)
            adminOrders = api.listAdminOrders().content
            screen = AppScreen.Admin
        }
    }

    fun addToCartFromCatalog(product: ProductDto) {
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
    }

    fun addDetailToCart() {
        val p = detailProduct ?: return
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

    fun updateCartQuantity(productId: String, quantity: Int) {
        runApi {
            pendingCheckoutKey = null
            cart =
                api.updateCartItem(
                    productId,
                    UpdateCartItemRequestDto(quantity = quantity),
                )
        }
    }

    fun removeCartItem(productId: String) {
        runApi {
            pendingCheckoutKey = null
            cart = api.removeCartItem(productId)
        }
    }

    fun payOrder(orderId: String) {
        runApi {
            val paid = api.payOrder(orderId)
            if (screen is AppScreen.OrderDone) {
                screen = AppScreen.OrderDone(paid)
            }
            detailOrder = paid
            statusMessage = "Paid"
            // refresh list if visible
            orders = orders.map { if (it.id == paid.id) paid else it }
        }
    }

    fun cancelOrder(orderId: String) {
        runApi {
            val cancelled = api.cancelOrder(orderId)
            detailOrder = cancelled
            statusMessage = "Cancelled"
            orders = orders.map { if (it.id == cancelled.id) cancelled else it }
            if (screen is AppScreen.OrderDone) {
                screen = AppScreen.OrderDone(cancelled)
            }
        }
    }

    fun refreshOrder(orderId: String) {
        runApi {
            detailOrder = api.getOrder(orderId)
        }
    }

    fun saveProfile() {
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
    }

    fun updatePassword() {
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
    }

    fun addAddress() {
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
    }

    fun deleteAddress(id: String) {
        runApi {
            api.deleteAddress(id)
            addresses = api.listAddresses()
            if (selectedAddressId == id) {
                selectedAddressId = addresses.firstOrNull { it.default }?.id
                    ?: addresses.firstOrNull()?.id
            }
            pendingCheckoutKey = null
            statusMessage = "Address deleted"
        }
    }

    fun setDefaultAddress(id: String) {
        runApi {
            api.updateAddress(id, UpdateAddressRequestDto(default = true))
            addresses = api.listAddresses()
            selectedAddressId = id
            pendingCheckoutKey = null
            statusMessage = "Default address updated"
        }
    }

    fun markNotificationRead(id: String) {
        runApi {
            api.markNotificationRead(id)
            notifications = api.listNotifications(size = 50).content
            notificationsUnread = api.notificationsUnreadCount().unread
        }
    }

    fun refreshNotifications() {
        runApi {
            notifications = api.listNotifications(size = 50).content
            notificationsUnread = api.notificationsUnreadCount().unread
        }
    }

    fun markAllNotificationsRead() {
        runApi {
            notificationsUnread = api.markAllNotificationsRead().unread
            notifications = api.listNotifications(size = 50).content
            statusMessage = "All notifications marked read"
        }
    }

    fun createCategory() {
        runApi {
            api.createCategory(
                CreateCategoryRequestDto(
                    name = adminCategoryName.trim(),
                    slug = adminCategorySlug.trim(),
                ),
            )
            adminCategoryName = ""
            adminCategorySlug = ""
            categories = api.listCategories(activeOnly = false)
            catalogCategories = runCatching { api.listCategories(activeOnly = true) }.getOrElse { catalogCategories }
            statusMessage = "Category created"
        }
    }

    fun updateCategory() {
        val id = editingCategoryId ?: return
        runApi {
            api.updateCategory(
                id,
                UpdateCategoryRequestDto(
                    name = adminCategoryName.trim().ifBlank { null },
                    slug = adminCategorySlug.trim().ifBlank { null },
                ),
            )
            editingCategoryId = null
            adminCategoryName = ""
            adminCategorySlug = ""
            categories = api.listCategories(activeOnly = false)
            catalogCategories = runCatching { api.listCategories(activeOnly = true) }.getOrElse { catalogCategories }
            statusMessage = "Category updated"
        }
    }

    fun deleteCategory(id: String) {
        runApi {
            api.deleteCategory(id)
            if (editingCategoryId == id) {
                editingCategoryId = null
                adminCategoryName = ""
                adminCategorySlug = ""
            }
            categories = api.listCategories(activeOnly = false)
            catalogCategories = runCatching { api.listCategories(activeOnly = true) }.getOrElse { catalogCategories }
            statusMessage = "Category deleted"
        }
    }

    fun saveProduct() {
        val editingId = editingProductId
        runApi {
            if (editingId == null) {
                api.createProduct(
                    CreateProductRequestDto(
                        name = adminProductName.trim(),
                        slug = adminProductSlug.trim(),
                        price = adminProductPrice.trim(),
                        stockQuantity = adminProductStock.trim().toIntOrNull() ?: 0,
                        featured = adminProductFeatured,
                        categoryId = adminProductCategoryId,
                    ),
                )
                statusMessage = "Created product"
            } else {
                api.updateProduct(
                    editingId,
                    UpdateProductRequestDto(
                        name = adminProductName.trim().ifBlank { null },
                        slug = adminProductSlug.trim().ifBlank { null },
                        price = adminProductPrice.trim().ifBlank { null },
                        stockQuantity = adminProductStock.trim().toIntOrNull(),
                        featured = adminProductFeatured,
                        categoryId = adminProductCategoryId,
                    ),
                )
                statusMessage = "Updated product"
            }
            editingProductId = null
            adminProductName = ""
            adminProductSlug = ""
            adminProductPrice = "9.99"
            adminProductStock = "10"
            adminProductFeatured = false
            adminProductCategoryId = null
            products = api.listProducts(size = 50).content
        }
    }

    fun deleteProduct(product: ProductDto) {
        runApi {
            api.deleteProduct(product.id)
            statusMessage = "Deleted ${product.name}"
            if (editingProductId == product.id) {
                editingProductId = null
            }
            products = api.listProducts(size = 50).content
        }
    }

    fun beginEditProduct(product: ProductDto) {
        editingProductId = product.id
        adminProductName = product.name
        adminProductSlug = product.slug
        adminProductPrice = product.price
        adminProductStock = product.stockQuantity.toString()
        adminProductFeatured = product.featured
        adminProductCategoryId = product.categoryId
        error = null
        statusMessage = null
    }

    fun clearProductEdit() {
        editingProductId = null
        adminProductName = ""
        adminProductSlug = ""
        adminProductPrice = "9.99"
        adminProductStock = "10"
        adminProductFeatured = false
        adminProductCategoryId = null
    }

    fun beginEditCategory(id: String, name: String, slug: String) {
        editingCategoryId = id
        adminCategoryName = name
        adminCategorySlug = slug
        error = null
        statusMessage = null
    }

    fun clearCategoryEdit() {
        editingCategoryId = null
        adminCategoryName = ""
        adminCategorySlug = ""
    }

    fun updateAdminOrderStatus(orderId: String, status: OrderStatusDto) {
        runApi {
            api.updateAdminOrderStatus(orderId, status)
            adminOrders = api.listAdminOrders().content
            statusMessage = "Order → ${status.name}"
        }
    }
}
