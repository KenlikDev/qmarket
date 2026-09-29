package com.kenlikdev.qmarket.identity.service

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.web.util.matcher.IpAddressMatcher
import org.springframework.stereotype.Component

@Component
class LoginClientKeyResolver(
    @Value("\${qmarket.security.trusted-proxy-addresses:127.0.0.1,::1}")
    trustedProxyAddresses: String,
) {
    private val trustedProxies =
        trustedProxyAddresses
            .split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map(::IpAddressMatcher)

    fun resolve(request: HttpServletRequest): String {
        val remoteAddress = request.remoteAddr ?: "unknown"
        if (trustedProxies.none { it.matches(remoteAddress) }) {
            return "remote:" + remoteAddress
        }

        val forwarded =
            request
                .getHeader("X-Forwarded-For")
                ?.split(",")
                ?.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

        return "forwarded:" + (forwarded ?: remoteAddress)
    }
}
