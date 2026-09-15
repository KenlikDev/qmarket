package com.kenlikdev.qmarket.order.payment

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Verifies Stripe-Signature header (t=…,v1=…).
 * See https://docs.stripe.com/webhooks/signatures
 */
object StripeWebhookVerifier {
    fun verify(
        payload: String,
        signatureHeader: String,
        secret: String,
        toleranceSeconds: Long,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
    ): Boolean {
        if (secret.isBlank() || signatureHeader.isBlank()) return false
        val parts =
            signatureHeader
                .split(",")
                .mapNotNull { piece ->
                    val idx = piece.indexOf('=')
                    if (idx <= 0) null else piece.substring(0, idx).trim() to piece.substring(idx + 1).trim()
                }.toMap()
        val timestamp = parts["t"]?.toLongOrNull() ?: return false
        if (abs(nowEpochSeconds - timestamp) > toleranceSeconds) return false
        val signedPayload = "$timestamp.$payload"
        val expected = hmacSha256Hex(secret, signedPayload)
        val candidates =
            signatureHeader.split(",").mapNotNull { piece ->
                val idx = piece.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val k = piece.substring(0, idx).trim()
                val v = piece.substring(idx + 1).trim()
                if (k == "v1") v else null
            }
        return candidates.any { secureEquals(it, expected) }
    }

    fun hmacSha256Hex(
        secret: String,
        message: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8)).joinToString("") { b ->
            ((b.toInt() and 0xff) + 0x100).toString(16).substring(1)
        }
    }

    private fun secureEquals(
        a: String,
        b: String,
    ): Boolean {
        if (a.length != b.length) return false
        var r = 0
        for (i in a.indices) {
            r = r or (a[i].code xor b[i].code)
        }
        return r == 0
    }
}
