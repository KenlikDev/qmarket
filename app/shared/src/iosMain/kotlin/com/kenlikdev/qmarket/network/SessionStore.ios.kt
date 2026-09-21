package com.kenlikdev.qmarket.network

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.OSStatus
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

/**
 * iOS Keychain-backed [SessionStore].
 *
 * CFDictionary for SecItem* (KN 2.4). Values as CFData via encodeToByteArray /
 * CFDataCreate — no NSString casts.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosKeychainSessionStore : SessionStore {

    override fun readAccessToken(): String? = read(KEY_ACCESS)

    override fun readRefreshToken(): String? = read(KEY_REFRESH)

    override fun readEmail(): String? = read(KEY_EMAIL)

    override fun write(
        accessToken: String,
        refreshToken: String,
        email: String?,
    ) {
        write(KEY_ACCESS, accessToken)
        write(KEY_REFRESH, refreshToken)
        if (email != null) {
            write(KEY_EMAIL, email)
        } else {
            delete(KEY_EMAIL)
        }
    }

    override fun clear() {
        delete(KEY_ACCESS)
        delete(KEY_REFRESH)
        delete(KEY_EMAIL)
    }

    private fun read(account: String): String? = memScoped {
        withQuery(account, returnData = true) { query ->
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)

            if (status == errSecItemNotFound) return@withQuery null
            if (status != errSecSuccess) {
                throw keychainError("Keychain read failed", status)
            }

            val cfType = result.value ?: return@withQuery null
            val cfData: CFDataRef = cfType.reinterpret()
            try {
                val length = CFDataGetLength(cfData).toInt()
                if (length <= 0) return@withQuery null
                val src = CFDataGetBytePtr(cfData) ?: return@withQuery null
                val bytes = ByteArray(length)
                bytes.usePinned { pinned ->
                    memcpy(pinned.addressOf(0), src, length.convert())
                }
                return@withQuery bytes.decodeToString().ifBlank { null }
            } finally {
                CFRelease(cfData)
            }
        }
    }

    private fun write(account: String, value: String) {
        val utf8 = value.encodeToByteArray()
        if (utf8.isEmpty()) return

        val cfData: CFDataRef =
            utf8.usePinned { pinned ->
                val bytesPtr: CPointer<UByteVar> = pinned.addressOf(0).reinterpret()
                CFDataCreate(
                    kCFAllocatorDefault,
                    bytesPtr,
                    utf8.size.convert(),
                )
            } ?: return

        val attributes = CFDictionaryCreateMutable(kCFAllocatorDefault, 1, null, null)
        if (attributes == null) {
            CFRelease(cfData)
            return
        }

        var status: OSStatus
        try {
            CFDictionaryAddValue(attributes, kSecValueData, cfData)
            status =
                withQuery(account, returnData = false) { query ->
                    SecItemUpdate(query, attributes)
                }

            if (status == errSecItemNotFound) {
                status = add(account, cfData)
            } else if (status != errSecSuccess) {
                throw keychainError("Keychain update failed", status)
            }
        } finally {
            CFRelease(attributes)
            CFRelease(cfData)
        }

        if (status != errSecSuccess) {
            throw keychainError("Keychain write failed", status)
        }
    }

    private fun add(
        account: String,
        value: CFDataRef,
    ): OSStatus {
        val addQuery = CFDictionaryCreateMutable(kCFAllocatorDefault, 5, null, null)
            ?: throw IllegalStateException("Unable to create Keychain add query")
        return try {
            addQuery.addStringValue(kSecAttrService, SERVICE)
            addQuery.addStringValue(kSecAttrAccount, account)
            CFDictionaryAddValue(addQuery, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(addQuery, kSecValueData, value)
            CFDictionaryAddValue(
                addQuery,
                kSecAttrAccessible,
                kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            )
            var status = SecItemAdd(addQuery, null)
            if (status == errSecDuplicateItem) {
                status =
                    withQuery(account, returnData = false) { query ->
                        val attributes = CFDictionaryCreateMutable(kCFAllocatorDefault, 1, null, null)
                            ?: throw IllegalStateException("Unable to create Keychain update attributes")
                        try {
                            CFDictionaryAddValue(attributes, kSecValueData, value)
                            SecItemUpdate(query, attributes)
                        } finally {
                            CFRelease(attributes)
                        }
                    }
            }
        } finally {
            CFRelease(addQuery)
        }
    }

    private fun delete(account: String) {
        val status =
            withQuery(account, returnData = false) { query ->
                SecItemDelete(query)
            }
        if (status != errSecSuccess && status != errSecItemNotFound) {
            throw keychainError("Keychain delete failed", status)
        }
    }

    private fun keychainError(
        message: String,
        status: OSStatus,
    ): IllegalStateException = IllegalStateException("$message (OSStatus $status)")

    private fun createQuery(account: String, returnData: Boolean): CFDictionaryRef? {
        val capacity = if (returnData) 5L else 3L
        val dict = CFDictionaryCreateMutable(kCFAllocatorDefault, capacity, null, null) ?: return null
        try {
            dict.addStringValue(kSecAttrService, SERVICE)
            dict.addStringValue(kSecAttrAccount, account)
            CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
            if (returnData) {
                CFDictionaryAddValue(dict, kSecReturnData, kCFBooleanTrue)
                CFDictionaryAddValue(dict, kSecMatchLimit, kSecMatchLimitOne)
            }
            return dict
        } catch (throwable: Throwable) {
            CFRelease(dict)
            throw throwable
        }
    }

    private inline fun <T> withQuery(
        account: String,
        returnData: Boolean,
        block: (CFDictionaryRef) -> T,
    ): T {
        val query = createQuery(account, returnData) ?: error("Unable to create Keychain query")
        return try {
            block(query)
        } finally {
            CFRelease(query)
        }
    }

    private fun CFDictionaryRef.addStringValue(
        key: CFStringRef,
        value: String,
    ) {
        val string = CFStringCreateWithCString(kCFAllocatorDefault, value, kCFStringEncodingUTF8)
        if (string != null) {
            try {
                CFDictionaryAddValue(this, key, string)
            } finally {
                CFRelease(string)
            }
        }
    }

    private companion object {
        const val SERVICE = "com.kenlikdev.qmarket.session"
        const val KEY_ACCESS = "accessToken"
        const val KEY_REFRESH = "refreshToken"
        const val KEY_EMAIL = "email"
    }
}

actual fun createPlatformSessionStore(): SessionStore = IosKeychainSessionStore()
