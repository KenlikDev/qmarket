package com.kenlikdev.qmarket.common.web

import com.kenlikdev.qmarket.common.exception.ErrorResponse
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Caps API request bodies before Spring/Jackson parses them.
 *
 * Content-Length is checked early when present; unknown-length/chunked bodies are read only up to
 * the applicable limit plus one byte. Accepted bodies are replayed to downstream MVC handlers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class RequestBodySizeLimitFilter(
    private val objectMapper: ObjectMapper,
    @Value("\${qmarket.http.max-request-body-bytes:1048576}")
    private val maxRequestBodyBytes: Int = 1_048_576,
    @Value("\${qmarket.http.max-auth-request-body-bytes:16384}")
    private val maxAuthRequestBodyBytes: Int = 16_384,
    @Value("\${qmarket.http.max-stripe-webhook-body-bytes:1048576}")
    private val maxStripeWebhookBodyBytes: Int = 1_048_576,
) : OncePerRequestFilter() {
    init {
        require(maxRequestBodyBytes in 1 until Int.MAX_VALUE) {
            "qmarket.http.max-request-body-bytes must be between 1 and Int.MAX_VALUE - 1"
        }
        require(maxAuthRequestBodyBytes in 1..maxRequestBodyBytes) {
            "qmarket.http.max-auth-request-body-bytes must be positive and no larger than the global limit"
        }
        require(maxStripeWebhookBodyBytes in 1..maxRequestBodyBytes) {
            "qmarket.http.max-stripe-webhook-body-bytes must be positive and no larger than the global limit"
        }
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI.removePrefix(request.contextPath)
        val hasBodyMethod =
            request.method.uppercase(Locale.ROOT) in
                setOf("POST", "PUT", "PATCH", "DELETE")
        return !path.startsWith("/api/") || !hasBodyMethod
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val limit = limitFor(request)
        if (request.contentLengthLong > limit.toLong()) {
            writeTooLarge(response, request)
            return
        }

        val body = request.inputStream.readNBytes(limit + 1)
        if (body.size > limit) {
            writeTooLarge(response, request)
            return
        }

        filterChain.doFilter(CachedBodyRequest(request, body), response)
    }

    private fun limitFor(request: HttpServletRequest): Int {
        val path = request.requestURI.removePrefix(request.contextPath)
        return when {
            path == STRIPE_WEBHOOK_PATH -> maxStripeWebhookBodyBytes
            path.startsWith(AUTH_PATH_PREFIX) -> maxAuthRequestBodyBytes
            else -> maxRequestBodyBytes
        }
    }

    private fun writeTooLarge(
        response: HttpServletResponse,
        request: HttpServletRequest,
    ) {
        val status = 413
        response.status = status
        response.contentType = "application/json"
        response.characterEncoding = StandardCharsets.UTF_8.name()
        objectMapper.writeValue(
            response.outputStream,
            ErrorResponse(
                status = status,
                error = "Payload Too Large",
                code = "REQUEST_TOO_LARGE",
                message = "Request body exceeds the allowed size",
                path = request.requestURI,
            ),
        )
    }

    private class CachedBodyRequest(
        request: HttpServletRequest,
        body: ByteArray,
    ) : HttpServletRequestWrapper(request) {
        private val cachedInputStream =
            object : ServletInputStream() {
                private val input = ByteArrayInputStream(body)

                override fun read(): Int = input.read()

                override fun read(
                    target: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int = input.read(target, offset, length)

                override fun isFinished(): Boolean = input.available() == 0

                override fun isReady(): Boolean = true

                override fun setReadListener(readListener: ReadListener) {
                    try {
                        if (!isFinished) {
                            readListener.onDataAvailable()
                        }
                        if (isFinished) {
                            readListener.onAllDataRead()
                        }
                    } catch (exception: IOException) {
                        readListener.onError(exception)
                    }
                }
            }

        override fun getInputStream(): ServletInputStream = cachedInputStream

        override fun getReader(): BufferedReader =
            BufferedReader(
                InputStreamReader(
                    cachedInputStream,
                    characterEncoding?.let { java.nio.charset.Charset.forName(it) } ?: StandardCharsets.UTF_8,
                ),
            )

        override fun getContentLength(): Int = bodySize

        override fun getContentLengthLong(): Long = bodySize.toLong()

        private val bodySize = body.size
    }

    private companion object {
        const val AUTH_PATH_PREFIX = "/api/v1/auth/"
        const val STRIPE_WEBHOOK_PATH = "/api/v1/payments/stripe/webhook"
    }
}
