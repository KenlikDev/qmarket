package com.kenlikdev.qmarket.identity.service

data class GoogleIdTokenClaims(
    val subject: String,
    val email: String,
    val emailVerified: Boolean,
    val givenName: String? = null,
    val familyName: String? = null,
)

interface GoogleIdTokenVerifier {
    fun verify(idToken: String): GoogleIdTokenClaims
}
