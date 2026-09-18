package com.kenlikdev.qmarket.common.security

import org.springframework.security.core.Authentication
import java.util.UUID

/**
 * Resolve the authenticated user id from the security [Authentication] principal.
 * Controllers should use this instead of duplicating `principal as UUID`.
 */
fun Authentication.userId(): UUID =
    when (val p = principal) {
        is UUID -> p
        else ->
            error(
                "Unexpected authentication principal type: ${p?.javaClass?.name ?: "null"} " +
                    "(expected ${UUID::class.java.name})",
            )
    }
