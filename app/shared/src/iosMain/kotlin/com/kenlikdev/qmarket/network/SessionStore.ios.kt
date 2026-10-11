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
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
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
 * iOS Keychain key-value adapter. [AtomicSessionRecordStore] uses a single account for the full session.
 *
 * CFDictionary for SecItem* (KN 2.4). CFType callbacks retain/release dictionary keys/values, and values as CFData via encodeToByteArray /
 * CFDataCreate — no NSString casts.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosKeychainKeyValueStore : SessionKeyValueStore {

    override fun get(key: String): String? = read(key)

    override fun put(
        key: String,
        value: String,
    ) = write(key, value)

    override fun remove(key: String) = delete(key)

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
        require(utf8.isNotEmpty()) { "Keychain value must not be empty" }

        val cfData: CFDataRef =
            utf8.usePinned { pinned ->
                val bytesPtr: CPointer<UByteVar> = pinned.addressOf(0).reinterpret()
                CFDataCreate(
                    kCFAllocatorDefault,
                    bytesPtr,
                    utf8.size.convert(),
                )
            } ?: throw IllegalStateException("Unable to encode Keychain value")

        val attributes =
            CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                1,
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr,
            ) ?: run {
                CFRelease(cfData)
                throw IllegalStateException("Unable to create Keychain update attributes")
            }

        var status: Int
        try {
            CFDictionaryAddValue(attributes, requireNotNull(kSecValueData), cfData)
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
    ): Int {
        val addQuery = CFDictionaryCreateMutable(kCFAllocatorDefault, 5, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
            ?: throw IllegalStateException("Unable to create Keychain add query")
        return try {
            addQuery.addStringValue(kSecAttrService, SERVICE)
            addQuery.addStringValue(kSecAttrAccount, account)
            CFDictionaryAddValue(addQuery, requireNotNull(kSecClass), requireNotNull(kSecClassGenericPassword))
            CFDictionaryAddValue(addQuery, requireNotNull(kSecValueData), value)
            CFDictionaryAddValue(
                addQuery,
                requireNotNull(kSecAttrAccessible),
                requireNotNull(kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly),
            )
            var status = SecItemAdd(addQuery, null)
            if (status == errSecDuplicateItem) {
                status =
                    withQuery(account, returnData = false) { query ->
                        val attributes = CFDictionaryCreateMutable(kCFAllocatorDefault, 1, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
                            ?: throw IllegalStateException("Unable to create Keychain update attributes")
                        try {
                            CFDictionaryAddValue(attributes, requireNotNull(kSecValueData), value)
                            SecItemUpdate(query, attributes)
                        } finally {
                            CFRelease(attributes)
                        }
                    }
            }
            status
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
        status: Int,
    ): IllegalStateException = IllegalStateException("$message (OSStatus $status)")

    private fun createQuery(account: String, returnData: Boolean): CFDictionaryRef? {
        val capacity = if (returnData) 5L else 3L
        val dict = CFDictionaryCreateMutable(kCFAllocatorDefault, capacity, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr) ?: return null
        try {
            dict.addStringValue(kSecAttrService, SERVICE)
            dict.addStringValue(kSecAttrAccount, account)
            CFDictionaryAddValue(dict, requireNotNull(kSecClass), requireNotNull(kSecClassGenericPassword))
            if (returnData) {
                CFDictionaryAddValue(dict, requireNotNull(kSecReturnData), requireNotNull(kCFBooleanTrue))
                CFDictionaryAddValue(dict, requireNotNull(kSecMatchLimit), requireNotNull(kSecMatchLimitOne))
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
        key: CFStringRef?,
        value: String,
    ) {
        val string =
            CFStringCreateWithCString(kCFAllocatorDefault, value, kCFStringEncodingUTF8)
                ?: throw IllegalStateException("Unable to encode Keychain query string")
        try {
            CFDictionaryAddValue(this, requireNotNull(key), string)
        } finally {
            CFRelease(string)
        }
    }

    private companion object {
        const val SERVICE = "com.kenlikdev.qmarket.session"
    }
}

actual fun createPlatformSessionStore(): SessionStore =
    AtomicSessionRecordStore(
        storage = IosKeychainKeyValueStore(),
        legacyKeys =
            LegacySessionKeys(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                email = "email",
            ),
    )
