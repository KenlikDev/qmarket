package com.kenlikdev.qmarket.common.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.util.ArrayList
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    private val props: JwtProperties,
) {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(props.secret.toByteArray(Charsets.UTF_8))
    }

    fun generateAccessToken(
        userId: UUID,
        email: String,
        roles: Collection<String>,
    ): String {
        val now = Date()
        val expiry = Date(now.time + props.accessTokenExpirationMs)
        // ArrayList avoids JWT/Kotlin List interop issues
        val roleList = ArrayList(roles)
        return Jwts
            .builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("roles", roleList)
            .claim("type", "access")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun generateRefreshToken(
        userId: UUID,
        jti: UUID = UUID.randomUUID(),
    ): String {
        val now = Date()
        val expiry = Date(now.time + props.refreshTokenExpirationMs)
        return Jwts
            .builder()
            .id(jti.toString())
            .subject(userId.toString())
            .claim("type", "refresh")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun parseClaims(token: String): Claims =
        Jwts
            .parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload

    fun isAccessToken(claims: Claims): Boolean = claims["type"] == "access"

    fun isRefreshToken(claims: Claims): Boolean = claims["type"] == "refresh"

    fun getUserId(claims: Claims): UUID = UUID.fromString(claims.subject)

    fun getJti(claims: Claims): UUID {
        val id = claims.id ?: throw IllegalArgumentException("Missing jti")
        return UUID.fromString(id)
    }

    fun refreshTokenExpiresAt(from: Date = Date()): Date = Date(from.time + props.refreshTokenExpirationMs)
}
