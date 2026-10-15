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
import com.kenlikdev.qmarket.network.SessionClearedWithCleanupFailure
import com.kenlikdev.qmarket.network.StripePaymentResult
import com.kenlikdev.qmarket.ui.AppScreen
import com.kenlikdev.qmarket.ui.CatalogFilterParams
import com.kenlikdev.qmarket.ui.CheckoutIdempotency
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
    private var sessionJob: Job = SupervisorJob(scope.coroutineContext[Job])
    private var sessionScope: CoroutineScope = CoroutineScope(scope.coroutineContext + sessionJob)
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
    var products by mutableStateOf<List<ProductDto>>(emptyList())
    /** Full catalog data visible to staff, including inactive products. */
    var adminProducts by mutableStateOf<List<ProductDto>>(emptyList())
    var detailProduct by mutableStateOf<ProductDto?>(null)
    var detailQuantity by mutableStateOf(1)
    var catalogQuery by mutableStateOf("")
    var catalogSortBy by mutableStateOf("createdAt")
    var catalogFeaturedOnly by mutableStateOf(false)
    var catalogCategoryId by mutableStateOf<String?>(null)
    var catalogCategories by mutableStateOf<List<CategoryDto>>(emptyList())
    var isAdmin by mutableStateOf(false)
    var canManageStore by mutableStateOf(false)
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
    /** Server-selected gateway; null disables payment actions until configuration can be read. */
    var paymentProviderId by mutableStateOf<String?>(null)
    /** In-memory Stripe session passed directly to the native SDK; never persisted or logged. */
    var paymentSession by mutableStateOf<PaymentSessionDto?>(null)
        private set
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

    fun runApi(block: suspend () -> Unit): Job {
        val requestSession = sessionJob
        return sessionScope.launch {
            loading = true
            error = null
            statusMessage = null
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (sessionJob === requestSession) {
                    error = e.message
                }
            } catch (e: Exception) {
                if (sessionJob === requestSession) {
                    error = e.message ?: e.toString()
                }
            } finally {
                if (sessionJob === requestSession) {
                    loading = false
                }
            }
        }
    }

    private fun clearSessionState(): Exception? {
        try {
            tokens.clear()
        } catch (exception: SessionClearedWithCleanupFailure) {
            // The durable tombstone committed; memory was cleared too. Report incomplete legacy
            // cleanup, but do not leave UI state logged in when persistence says the session ended.
            finishLocalSessionClear()
            return exception
        } catch (exception: Exception) {
            // The durable clear did not commit. Preserve the current in-memory session and UI so
            // the caller can surface the failure without splitting the session state.
            return exception
        }

        finishLocalSessionClear()
        return null
    }

    private fun finishLocalSessionClear() {
        api.clearBearerTokenCache()
        clearUserScopedUiState()
        loggedIn = false
        isAdmin = false
        canManageStore = false
        userLabel = null
        products = emptyList()
        screen = AppScreen.Login
    }

    /**
     * Starts a new session scope and cancels every in-flight user-scoped request.
     * This prevents responses from a previous account from mutating the next account's UI state.
     */
    private fun beginSessionTransition() {
        sessionJob.cancel()
        sessionJob = SupervisorJob(scope.coroutineContext[Job])
        sessionScope = CoroutineScope(scope.coroutineContext + sessionJob)
    }

    fun clearUserScopedUiState() {
        orders = emptyList()
        adminProducts = emptyList()
        categories = emptyList()
        adminOrders = emptyList()
        adminUsers = emptyList()
        adminUserQuery = ""
        addresses = emptyList()
        selectedAddressId = null
        shippingAddress = ""
        addrRecipient = ""
        addrCity = ""
        addrStreet = ""
        addrPhone = ""
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
        paymentProviderId = null
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
        val browserCookieBootstrap = api.browserCookieAuth && tokens.accessToken().isNullOrBlank()
        var browserCookieRestored = false
        loading = true
        error = null
        try {
            if (browserCookieBootstrap) {
                tokens.applyAuth(api.refreshBrowserSession())
                browserCookieRestored = true
            }
            val currentProfile = api.getProfile()
            tokens.applyRoles(currentProfile.roles)
            isAdmin = tokens.isAdmin()
            canManageStore = tokens.canManageStore()

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
                    if (browserCookieBootstrap && !browserCookieRestored) {
                        null
                    } else if (cleanupError == null) {
                        "Session expired — please sign in again"
                    } else {
                        "Session expired — local session cleanup failed"
                    }
            } else if (browserCookieBootstrap && !browserCookieRestored) {
                loggedIn = false
                screen = AppScreen.Login
                error = e.message
            } else {
                loggedIn = true
                userLabel = tokens.sessionEmail()
                isAdmin = tokens.isAdmin()
                canManageStore = tokens.canManageStore()
                screen = AppScreen.Catalog
                error = e.message
            }
        } catch (e: Exception) {
            if (browserCookieBootstrap && !browserCookieRestored) {
                loggedIn = false
                screen = AppScreen.Login
                error = e.message ?: "Unable to verify browser session"
            } else {
                loggedIn = true
                userLabel = tokens.sessionEmail()
                isAdmin = tokens.isAdmin()
                canManageStore = tokens.canManageStore()
                screen = AppScreen.Catalog
                error = e.message ?: "Unable to restore session data"
            }
        } finally {
            loading = false
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
        paymentProviderId = null
        detailOrder = order
        screen = AppScreen.OrderDetail
        runApi {
            detailOrder = api.getOrder(order.id)
            refreshPaymentProviderConfig()
        }
    }

    fun loadOrders() {
        paymentProviderId = null
        runApi {
            val page = api.listMyOrders(size = 50)
            orders = page.content
            refreshPaymentProviderConfig()
            screen = AppScreen.Orders
        }
    }

    /**
     * Fetch provider identity independently from order loading. A config outage must not hide orders,
     * but must fail closed: no payment action is shown until the server provider can be confirmed.
     */
    private suspend fun refreshPaymentProviderConfig() {
        paymentProviderId =
            runCatchingCancellable {
                api.getPaymentProviderConfig().providerId.trim().lowercase()
            }.getOrNull()?.takeIf { it == "mock" || it == "stripe" }
    }

    fun logout(): Job {
        beginSessionTransition()
        val refresh = tokens.refreshToken()
        val cleanupError = clearSessionState()

        if (cleanupError != null && cleanupError !is SessionClearedWithCleanupFailure) {
            // The local logout was not durably committed. Keep the current UI/session and do not
            // revoke the server refresh family until the user can successfully retry local logout.
            return runApi { throw cleanupError }
        }

        return runApi {
            // Local credentials are already durably cleared. Remote revocation is best-effort;
            // a network failure must never cause the local client to resurrect the session.
            if (!refresh.isNullOrBlank()) {
                api.logout(refresh)
            }
            if (cleanupError != null) {
                throw cleanupError
            }
            error = null
            screen = AppScreen.Login
        }
    }

    fun applySession(authEmail: String) {
        userLabel = authEmail
        loggedIn = true
        isAdmin = tokens.isAdmin()
        canManageStore = tokens.canManageStore()
    }

    fun login() {
        beginSessionTransition()
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
        beginSessionTransition()
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
        beginSessionTransition()
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
                // The order creation response is the definitive business result.
                // Do not make the user repeat checkout because an ancillary cart refresh failed.
                pendingCheckoutKey = null
                screen = AppScreen.OrderDone(order)
                cart = runCatchingCancellable { api.getCart() }.getOrElse { cart }
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
        if (paymentProviderId != "mock") {
            error =
                when (paymentProviderId) {
                    "stripe" -> "Use the secure Stripe payment option for this order."
                    else -> "Payment provider is unavailable. Refresh the order before trying again."
                }
            return
        }
        runApi {
            val paid = api.payOrder(orderId)
            updateOrderSnapshot(paid)
            statusMessage = "Paid"
        }
    }

    /**
     * Creates a short-lived client session only when the current platform has an attached
     * Stripe confirmation handler. The client secret remains in ephemeral model state and is
     * passed only to that handler; unsupported targets must not request a PaymentIntent.
     */
    fun startPaymentSession(orderId: String) {
        if (paymentProviderId != "stripe") {
            error = "Stripe payment is not available for the active payment provider."
            return
        }
        if (paymentSession != null || loading) return

        runApi {
            val session = api.createPaymentSession(orderId)
            if (
                session.providerId != "stripe" ||
                session.clientSecret.isBlank() ||
                !session.publishableKey.matches(Regex("^pk_(test|live)_[A-Za-z0-9]+$"))
            ) {
                error = "Stripe is not configured correctly. No payment sheet was opened."
                return@runApi
            }
            paymentSession = session
            statusMessage = "Opening secure Stripe payment…"
        }
    }

    /**
     * Handles the native SDK result without ever treating the client callback as authoritative.
     * The server's verified webhook remains the only source of a PAID order state.
     */
    fun handleStripePaymentResult(
        orderId: String,
        result: StripePaymentResult,
    ): Job {
        paymentSession = null
        // A native activity may have been recreated while PaymentSheet was visible. Restore the
        // affected order as the active screen so its verified server state and next action are visible.
        if (screen != AppScreen.OrderDetail && screen !is AppScreen.OrderDone) {
            screen = AppScreen.OrderDetail
        }
        return when (result) {
            StripePaymentResult.CANCELED -> {
                runApi {
                    refreshOrderSnapshot(orderId)
                    statusMessage =
                        if (detailOrder?.status?.name == "PAID") {
                            "Payment confirmed by the server."
                        } else {
                            "Payment cancelled. The order remains pending."
                        }
                }
            }
            StripePaymentResult.FAILED -> {
                runApi {
                    refreshOrderSnapshot(orderId)
                    if (detailOrder?.status?.name == "PAID") {
                        statusMessage = "Payment confirmed by the server."
                    } else {
                        error = "Stripe could not complete the payment. The order remains pending."
                    }
                }
            }
            StripePaymentResult.COMPLETED -> {
                runApi {
                    refreshOrderSnapshot(orderId)
                    statusMessage =
                        if (detailOrder?.status?.name == "PAID") {
                            "Payment confirmed by the server."
                        } else {
                            "Stripe submitted the payment. Waiting for verified server confirmation; refresh shortly."
                        }
                }
            }
        }
    }

    private suspend fun refreshOrderSnapshot(orderId: String) {
        val latest = api.getOrder(orderId)
        updateOrderSnapshot(latest)
    }

    private fun updateOrderSnapshot(order: OrderDto) {
        detailOrder = order
        orders = orders.map { if (it.id == order.id) order else it }
        adminOrders = adminOrders.map { if (it.id == order.id) order else it }
        if (screen is AppScreen.OrderDone) {
            screen = AppScreen.OrderDone(order)
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
        paymentProviderId = null
        runApi {
            detailOrder = api.getOrder(orderId)
            refreshPaymentProviderConfig()
        }
    }
}
