package com.kenlikdev.qmarket

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kenlikdev.qmarket.api.AddCartItemRequestDto
import com.kenlikdev.qmarket.api.AddressDto
import com.kenlikdev.qmarket.api.AdminUserDto
import com.kenlikdev.qmarket.api.CartDto
import com.kenlikdev.qmarket.api.CategoryDto
import com.kenlikdev.qmarket.api.CreateOrderRequestDto
import com.kenlikdev.qmarket.api.LoginRequestDto
import com.kenlikdev.qmarket.api.NotificationDto
import com.kenlikdev.qmarket.api.OrderDto
import com.kenlikdev.qmarket.api.PaymentSessionDto
import com.kenlikdev.qmarket.api.ProductDto
import com.kenlikdev.qmarket.api.ProfileDto
import com.kenlikdev.qmarket.api.RegisterRequestDto
import com.kenlikdev.qmarket.api.UpdateCartItemRequestDto
import com.kenlikdev.qmarket.network.ApiException
import com.kenlikdev.qmarket.network.MutableTokenProvider
import com.kenlikdev.qmarket.network.QMarketApiClient
import com.kenlikdev.qmarket.ui.AppScreen
import com.kenlikdev.qmarket.ui.CatalogFilterParams
import com.kenlikdev.qmarket.ui.CheckoutIdempotency
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    var email by mutableStateOf(tokens.sessionEmail().orEmpty())
    var password by mutableStateOf("")
    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")
    var phone by mutableStateOf("")
    var currentPassword by mutableStateOf("")
    var newPassword by mutableStateOf("")
    var profile by mutableStateOf<ProfileDto?>(null)
    var shippingAddress by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var statusMessage by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    private var activeRequestCount = 0
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
    var adminUsers by mutableStateOf<List<AdminUserDto>>(emptyList())
    var adminUserQuery by mutableStateOf("")
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

    fun runApi(block: suspend () -> Unit): Job =
        scope.launch {
            withRequestLoading {
                error = null
                statusMessage = null
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ApiException) {
                    error = e.message
                } catch (e: Exception) {
                    error = e.message ?: e.toString()
                }
            }
        }

    private suspend fun <T> withRequestLoading(block: suspend () -> T): T {
        activeRequestCount += 1
        loading = true
        try {
            return block()
        } finally {
            activeRequestCount = (activeRequestCount - 1).coerceAtLeast(0)
            loading = activeRequestCount > 0
        }
    }

    private fun clearSessionState(): Exception? {
        var cleanupError: Exception? = null
        try {
            tokens.clear()
        } catch (exception: Exception) {
            cleanupError = exception
        } finally {
            api.clearBearerTokenCache()
            clearUserScopedUiState()
            loggedIn = false
            isAdmin = false
            userLabel = null
            products = emptyList()
            screen = AppScreen.Login
        }
        return cleanupError
    }

    fun clearUserScopedUiState() {
        orders = emptyList()
        adminOrders = emptyList()
        adminUsers = emptyList()
        adminUserQuery = ""
        addresses = emptyList()
        selectedAddressId = null
        profile = null
        firstName = ""
        lastName = ""
        password = ""
        phone = ""
        currentPassword = ""
        newPassword = ""
        cart = null
        detailProduct = null
        detailOrder = null
        paymentSession = null
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
        withRequestLoading {
            error = null
            try {
                val currentProfile = api.getProfile()
                tokens.applyRoles(currentProfile.roles)
                isAdmin = tokens.isAdmin()

                val page = api.listProducts(size = 50)
                products = page.content
                cart = runCatchingCancellable { api.getCart() }.getOrNull()

                loggedIn = true
                userLabel = currentProfile.email
                screen = AppScreen.Catalog
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.status == 401) {
                    val cleanupError = clearSessionState()
                    screen = AppScreen.Login
                    error =
                        if (cleanupError == null) {
                            "Session expired — please sign in again"
                        } else {
                            "Session expired — local session cleanup failed"
                        }
                } else {
                    loggedIn = true
                    userLabel = tokens.sessionEmail()
                    isAdmin = tokens.isAdmin()
                    screen = AppScreen.Catalog
                    error = e.message
                }
            } catch (e: Exception) {
                loggedIn = true
                userLabel = tokens.sessionEmail()
                isAdmin = tokens.isAdmin()
                screen = AppScreen.Catalog
                error = e.message ?: "Unable to restore session data"
            }
        }
    }

    fun loadCatalog(navigate: Boolean = true) {
        runApi {
            catalogCategories =
                runCatchingCancellable { api.listCategories() }.getOrElse { catalogCategories }
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
            addresses = runCatchingCancellable { api.listAddresses() }.getOrElse { addresses }
            if (selectedAddressId == null) {
                selectedAddressId = addresses.firstOrNull { it.default }?.id
                    ?: addresses.firstOrNull()?.id
            }
            screen = AppScreen.Cart
        }
    }

    fun openOrder(order: OrderDto) {
        detailOrder = order
        paymentSession = null
        screen = AppScreen.OrderDetail
        runApi {
            detailOrder = api.getOrder(order.id)
        }
    }

    fun loadOrders() {
        runApi {
            val page = api.listMyOrders(size = 50)
            orders = page.content
            screen = AppScreen.Orders
        }
    }

    fun logout(): Job {
        val refresh = tokens.refreshToken()
        return runApi {
            try {
                if (!refresh.isNullOrBlank()) {
                    api.logout(refresh)
                }
            } finally {
                val cleanupError = clearSessionState()
                if (cleanupError != null) {
                    throw cleanupError
                }
                error = null
                screen = AppScreen.Login
            }
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
            password = ""
            clearUserScopedUiState()
            applySession(auth.user.email)
            val page = api.listProducts(size = 50)
            products = page.content
            cart = runCatchingCancellable { api.getCart() }.getOrNull()
            notificationsUnread =
                runCatchingCancellable { api.notificationsUnreadCount().unread }.getOrDefault(0L)
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
            cart = runCatchingCancellable { api.getCart() }.getOrNull()
            screen = AppScreen.Catalog
        }
    }

    fun browseAsGuest() {
        val cleanupError = clearSessionState()
        if (cleanupError != null) {
            error = "Unable to clear the local session"
            return
        }
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

    fun updateCartQuantity(
        productId: String,
        quantity: Int,
    ) {
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
        paymentSession = null
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

    /** Stripe client session; UI/SDK uses clientSecret. Null message on success via paymentSession state. */
    var paymentSession: PaymentSessionDto? by mutableStateOf(null)
        private set

    fun startPaymentSession(orderId: String) {
        paymentSession = null
        runApi {
            paymentSession = api.createPaymentSession(orderId)
            statusMessage = "Payment session ready (${paymentSession?.providerId})"
        }
    }

    fun cancelOrder(orderId: String) {
        paymentSession = null
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
}
