package com.kenlikdev.qmarket.order.payment

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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
        if (secret.isBlank() || signatureHeader.isBlank() || toleranceSeconds < 0) {
            return false
        }

        val signatures =
            signatureHeader
                .split(',')
                .mapNotNull { piece ->
                    val separator = piece.indexOf('=')
                    if (separator <= 0) {
                        null
                    } else {
                        piece.substring(0, separator).trim() to piece.substring(separator + 1).trim()
                    }
                }

        val timestamp =
            signatures
                .firstOrNull { (key, _) -> key == "t" }
                ?.second
                ?.toLongOrNull()
                ?: return false
        if (abs(nowEpochSeconds - timestamp) > toleranceSeconds) {
            return false
        }

        val expected = hmacSha256Hex(secret, "$timestamp.$payload")
        return signatures
            .asSequence()
            .filter { (key, _) -> key == "v1" }
            .map { (_, value) -> value }
            .any { candidate ->
                secureEqualsHex(candidate, expected)
            }
    }

    fun hmacSha256Hex(
        secret: String,
        message: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(StandardCharsets.UTF_8)).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private fun secureEqualsHex(
        candidate: String,
        expected: String,
    ): Boolean {
        val candidateBytes = candidate.hexToByteArrayOrNull() ?: return false
        val expectedBytes = expected.hexToByteArrayOrNull() ?: return false
        return MessageDigest.isEqual(candidateBytes, expectedBytes)
    }

    private fun String.hexToByteArrayOrNull(): ByteArray? {
        if (length % 2 != 0) return null
        if (any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null

        val result = ByteArray(length / 2)
        for (index in result.indices) {
            val high = digitToIntOrNull(index * 2) ?: return null
            val low = digitToIntOrNull(index * 2 + 1) ?: return null
            result[index] = ((high shl 4) or low).toByte()
        }
        return result
    }

    private fun String.digitToIntOrNull(index: Int): Int? =
        when (val c = this[index]) {
            in '0'..'9' -> c.code - '0'.code
            in 'a'..'f' -> c.code - 'a'.code + 10
            in 'A'..'F' -> c.code - 'A'.code + 10
            else -> null
        }
}
