package com.kenlikdev.qmarket.network

import com.kenlikdev.qmarket.api.AddCartItemRequestDto
import com.kenlikdev.qmarket.api.ApiErrorDto
import com.kenlikdev.qmarket.api.AuthResponseDto
import com.kenlikdev.qmarket.api.CartDto
import com.kenlikdev.qmarket.api.LoginRequestDto
import com.kenlikdev.qmarket.api.PageDto
import com.kenlikdev.qmarket.api.ProductDto
import com.kenlikdev.qmarket.api.QMarketJson
import com.kenlikdev.qmarket.api.RegisterRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
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
    suspend fun register(request: RegisterRequestDto): AuthResponseDto =
        post("/api/v1/auth/register", request)

    suspend fun login(request: LoginRequestDto): AuthResponseDto =
        post("/api/v1/auth/login", request)

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

    suspend fun getCart(): CartDto = get("/api/v1/cart")

    suspend fun addCartItem(request: AddCartItemRequestDto): CartDto =
        post("/api/v1/cart/items", request)

    private suspend inline fun <reified T> get(path: String): T {
        val response = http.get(path)
        return response.parseBody()
    }

    /** [Req] must stay reified so ContentNegotiation can pick kotlinx serializer. */
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

    private suspend inline fun <reified T> HttpResponse.parseBody(): T {
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
        if (status == HttpStatusCode.NoContent) {
            @Suppress("UNCHECKED_CAST")
            return Unit as T
        }
        return body()
    }
}
