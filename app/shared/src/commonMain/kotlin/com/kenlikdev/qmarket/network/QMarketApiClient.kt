package com.kenlikdev.qmarket.network

import com.kenlikdev.qmarket.api.AddCartItemRequestDto
import com.kenlikdev.qmarket.api.AddressDto
import com.kenlikdev.qmarket.api.ApiErrorDto
import com.kenlikdev.qmarket.api.AuthResponseDto
import com.kenlikdev.qmarket.api.CartDto
import com.kenlikdev.qmarket.api.ChangePasswordRequestDto
import com.kenlikdev.qmarket.api.CreateAddressRequestDto
import com.kenlikdev.qmarket.api.CreateOrderRequestDto
import com.kenlikdev.qmarket.api.CategoryDto
import com.kenlikdev.qmarket.api.CreateCategoryRequestDto
import com.kenlikdev.qmarket.api.CreateProductRequestDto
import com.kenlikdev.qmarket.api.UpdateCategoryRequestDto
import com.kenlikdev.qmarket.api.UpdateProductRequestDto
import com.kenlikdev.qmarket.api.GoogleOAuthRequestDto
import com.kenlikdev.qmarket.api.LoginRequestDto
import com.kenlikdev.qmarket.api.NotificationDto
import com.kenlikdev.qmarket.api.AdminUserDto
import com.kenlikdev.qmarket.api.OrderDto
import com.kenlikdev.qmarket.api.PaymentSessionDto
import com.kenlikdev.qmarket.api.UnreadCountDto
import com.kenlikdev.qmarket.api.OrderStatusDto
import com.kenlikdev.qmarket.api.UpdateOrderStatusRequestDto
import com.kenlikdev.qmarket.api.PageDto
import com.kenlikdev.qmarket.api.ProductDto
import com.kenlikdev.qmarket.api.ProfileDto
import com.kenlikdev.qmarket.api.QMarketJson
import com.kenlikdev.qmarket.api.RefreshTokenRequestDto
import com.kenlikdev.qmarket.api.RegisterRequestDto
import com.kenlikdev.qmarket.api.UpdateAddressRequestDto
import com.kenlikdev.qmarket.api.UpdateCartItemRequestDto
import com.kenlikdev.qmarket.api.UpdateProfileRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * Typed HTTP client for QMarket REST API (`/api/v1/...`).
 * Uses shared DTOs from `:core`.
 */
class QMarketApiClient(
    private val http: HttpClient,
) {
    /**
     * Ktor Auth caches BearerTokens after the first loadTokens call.
     * Clear after login/register/logout so the next request uses the new session JWT.
     */
    fun clearBearerTokenCache() {
        http.authProvider<BearerAuthProvider>()?.clearToken()
    }

    // --- Auth ---

    suspend fun register(request: RegisterRequestDto): AuthResponseDto =
        post("/api/v1/auth/register", request)

    suspend fun login(request: LoginRequestDto): AuthResponseDto =
        post("/api/v1/auth/login", request)

    suspend fun loginWithGoogle(request: GoogleOAuthRequestDto): AuthResponseDto =
        post("/api/v1/auth/oauth/google", request)

    suspend fun refresh(request: RefreshTokenRequestDto): AuthResponseDto =
        post("/api/v1/auth/refresh", request)

    suspend fun logout(refreshToken: String) {
        runCatching {
            postNoContent(
                "/api/v1/auth/logout",
                RefreshTokenRequestDto(refreshToken = refreshToken),
            )
        }
    }

    // --- Profile ---

    suspend fun getProfile(): ProfileDto = get("/api/v1/users/me")

    suspend fun updateProfile(request: UpdateProfileRequestDto): ProfileDto =
        patch("/api/v1/users/me", request)

    suspend fun changePassword(request: ChangePasswordRequestDto) {
        postNoContent("/api/v1/users/me/password", request)
    }

    // --- Addresses ---

    suspend fun listAddresses(): List<AddressDto> = get("/api/v1/users/me/addresses")

    suspend fun createAddress(request: CreateAddressRequestDto): AddressDto =
        post("/api/v1/users/me/addresses", request)

    suspend fun updateAddress(
        id: String,
        request: UpdateAddressRequestDto,
    ): AddressDto = put("/api/v1/users/me/addresses/$id", request)

    suspend fun deleteAddress(id: String) {
        deleteNoContent("/api/v1/users/me/addresses/$id")
    }

    // --- Catalog ---

    suspend fun listProducts(
        page: Int = 0,
        size: Int = 20,
        q: String? = null,
        categoryId: String? = null,
        featuredOnly: Boolean? = null,
        minPrice: String? = null,
        maxPrice: String? = null,
        sortBy: String? = null,
        sortDir: String? = null,
    ): PageDto<ProductDto> {
        val response =
            http.get("/api/v1/products") {
                parameter("page", page)
                parameter("size", size)
                q?.let { parameter("q", it) }
                categoryId?.let { parameter("categoryId", it) }
                featuredOnly?.let { parameter("featuredOnly", it) }
                minPrice?.let { parameter("minPrice", it) }
                maxPrice?.let { parameter("maxPrice", it) }
                sortBy?.let { parameter("sortBy", it) }
                sortDir?.let { parameter("sortDir", it) }
            }
        return response.parseBody()
    }

    suspend fun getProduct(id: String): ProductDto = get("/api/v1/products/$id")

    suspend fun getProductBySlug(slug: String): ProductDto =
        get("/api/v1/products/slug/$slug")

    suspend fun createProduct(request: CreateProductRequestDto): ProductDto =
        post("/api/v1/products", request)

    suspend fun updateProduct(
        id: String,
        request: UpdateProductRequestDto,
    ): ProductDto = put("/api/v1/products/$id", request)

    suspend fun deleteProduct(id: String) {
        deleteNoContent("/api/v1/products/$id")
    }

    suspend fun listCategories(activeOnly: Boolean = false): List<CategoryDto> {
        val response =
            http.get("/api/v1/categories") {
                parameter("activeOnly", activeOnly)
            }
        return response.parseBody()
    }

    suspend fun createCategory(request: CreateCategoryRequestDto): CategoryDto =
        post("/api/v1/categories", request)

    suspend fun updateCategory(
        id: String,
        request: UpdateCategoryRequestDto,
    ): CategoryDto = put("/api/v1/categories/$id", request)

    suspend fun deleteCategory(id: String) {
        deleteNoContent("/api/v1/categories/$id")
    }

    // --- Cart ---

    suspend fun getCart(): CartDto = get("/api/v1/cart")

    suspend fun addCartItem(request: AddCartItemRequestDto): CartDto =
        post("/api/v1/cart/items", request)

    suspend fun updateCartItem(
        productId: String,
        request: UpdateCartItemRequestDto,
    ): CartDto = put("/api/v1/cart/items/$productId", request)

    suspend fun removeCartItem(productId: String): CartDto =
        delete("/api/v1/cart/items/$productId")

    suspend fun clearCart(): CartDto = delete("/api/v1/cart")

    // --- Orders ---

    suspend fun createOrder(
        request: CreateOrderRequestDto,
        idempotencyKey: String? = null,
    ): OrderDto {
        val response =
            http.post("/api/v1/orders") {
                contentType(ContentType.Application.Json)
                setBody(request)
                if (!idempotencyKey.isNullOrBlank()) {
                    header("Idempotency-Key", idempotencyKey.trim())
                }
            }
        return response.parseBody()
    }

    suspend fun listMyOrders(
        page: Int = 0,
        size: Int = 20,
    ): PageDto<OrderDto> {
        val response =
            http.get("/api/v1/orders") {
                parameter("page", page)
                parameter("size", size)
            }
        return response.parseBody()
    }

    suspend fun getOrder(id: String): OrderDto = get("/api/v1/orders/$id")

    // --- Notifications ---

    suspend fun listNotifications(
        page: Int = 0,
        size: Int = 20,
    ): PageDto<NotificationDto> {
        val response =
            http.get("/api/v1/notifications") {
                parameter("page", page)
                parameter("size", size)
            }
        return response.parseBody()
    }

    suspend fun notificationsUnreadCount(): UnreadCountDto = get("/api/v1/notifications/unread-count")

    suspend fun markNotificationRead(id: String): NotificationDto =
        postEmpty("/api/v1/notifications/$id/read")

    suspend fun markAllNotificationsRead(): UnreadCountDto =
        postEmpty("/api/v1/notifications/read-all")


    suspend fun cancelOrder(id: String): OrderDto = postEmpty("/api/v1/orders/$id/cancel")

    suspend fun payOrder(id: String): OrderDto = postEmpty("/api/v1/orders/$id/pay")

    /** Stripe Payment Element session (requires provider=stripe on server). */
    suspend fun createPaymentSession(id: String): PaymentSessionDto =
        postEmpty("/api/v1/orders/$id/payment-session")

    suspend fun listAdminOrders(
        page: Int = 0,
        size: Int = 50,
    ): PageDto<OrderDto> {
        val response =
            http.get("/api/v1/orders/admin/all") {
                parameter("page", page)
                parameter("size", size)
            }
        return response.parseBody()
    }

    suspend fun updateAdminOrderStatus(
        id: String,
        status: OrderStatusDto,
    ): OrderDto =
        put(
            "/api/v1/orders/admin/$id/status",
            UpdateOrderStatusRequestDto(status = status),
        )


    suspend fun listAdminUsers(
        page: Int = 0,
        size: Int = 50,
        q: String? = null,
    ): PageDto<AdminUserDto> {
        val response =
            http.get("/api/v1/admin/users") {
                parameter("page", page)
                parameter("size", size)
                q?.let { parameter("q", it) }
            }
        return response.parseBody()
    }

    suspend fun getAdminUser(id: String): AdminUserDto = get("/api/v1/admin/users/$id")

    // --- HTTP helpers ---

    private suspend inline fun <reified T> get(path: String): T {
        val response = http.get(path)
        return response.parseBody()
    }

    private suspend inline fun <reified Req : Any, reified Res> post(
        path: String,
        body: Req,
    ): Res {
        val response =
            http.post(path) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        return response.parseBody()
    }

    private suspend inline fun <reified Res> postEmpty(path: String): Res {
        val response = http.post(path)
        return response.parseBody()
    }

    private suspend inline fun <reified Req : Any> postNoContent(
        path: String,
        body: Req,
    ) {
        val response =
            http.post(path) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        response.ensureSuccess()
    }

    private suspend inline fun <reified Req : Any, reified Res> put(
        path: String,
        body: Req,
    ): Res {
        val response =
            http.put(path) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        return response.parseBody()
    }

    private suspend inline fun <reified Req : Any, reified Res> patch(
        path: String,
        body: Req,
    ): Res {
        val response =
            http.patch(path) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        return response.parseBody()
    }

    private suspend inline fun <reified Res> delete(path: String): Res {
        val response = http.delete(path)
        return response.parseBody()
    }

    private suspend fun deleteNoContent(path: String) {
        val response = http.delete(path)
        response.ensureSuccess()
    }

    private suspend fun HttpResponse.ensureSuccess() {
        if (!status.isSuccess()) {
            val text = runCatching { bodyAsText() }.getOrDefault("")
            val error =
                runCatching {
                    if (text.isNotBlank()) {
                        QMarketJson.format.decodeFromString(ApiErrorDto.serializer(), text)
                    } else {
                        null
                    }
                }.getOrNull()
            throw ApiException(
                status = status.value,
                message = error?.message ?: text.ifBlank { status.description },
                body = error,
            )
        }
    }

    private suspend inline fun <reified T> HttpResponse.parseBody(): T {
        ensureSuccess()
        if (status == HttpStatusCode.NoContent) {
            @Suppress("UNCHECKED_CAST")
            return Unit as T
        }
        return body()
    }
}
