package com.kenlikdev.qmarket.common.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    private val props: JwtProperties
) {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(props.secret.toByteArray(Charsets.UTF_8))
    }

    fun generateAccessToken(userId: UUID, email: String, roles: Collection<String>): String {
        val now = Date()
        val expiry = Date(now.time + props.accessTokenExpirationMs)
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("roles", roles)
            .claim("type", "access")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun generateRefreshToken(userId: UUID): String {
        val now = Date()
        val expiry = Date(now.time + props.refreshTokenExpirationMs)
        return Jwts.builder()
            .subject(userId.toString())
            .claim("type", "refresh")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun parseClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    fun isAccessToken(claims: Claims): Boolean = claims["type"] == "access"
    fun isRefreshToken(claims: Claims): Boolean = claims["type"] == "refresh"

    fun getUserId(claims: Claims): UUID = UUID.fromString(claims.subject)
}
