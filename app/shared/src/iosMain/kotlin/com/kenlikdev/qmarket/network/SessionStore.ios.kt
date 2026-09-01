package com.kenlikdev.qmarket.network

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
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

/**
 * iOS Keychain-backed [SessionStore].
 *
 * Stores access/refresh tokens and email as generic passwords under a fixed service.
 * Accessibility: [kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly] (device-bound).
 *
 * Compatible with Kotlin/Native 2.4.x: Security APIs require [CFDictionaryRef]
 * (`CPointer<__CFDictionary>?`). Dictionaries are built via
 * [CFDictionaryCreateMutable] + [CFDictionaryAddValue].
 */
@OptIn(ExperimentalForeignApi::class)
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
        val query = createQuery(account, returnData = true) ?: return null

        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)

        if (status == errSecItemNotFound) return null
        if (status != errSecSuccess) return null

        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        val nsString = NSString.create(data = data, encoding = NSUTF8StringEncoding)
        return nsString?.toString()?.ifBlank { null }
    }

    private fun write(account: String, value: String) {
        val nsData = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val cfData = CFBridgingRetain(nsData) ?: return

        val query = createQuery(account, returnData = false) ?: return

        val attributes = CFDictionaryCreateMutable(kCFAllocatorDefault, 1, null, null)
        if (attributes == null) return
        CFDictionaryAddValue(attributes, kSecValueData, cfData)

        var status = SecItemUpdate(query, attributes)
        if (status == errSecItemNotFound) {
            val addQuery = CFDictionaryCreateMutable(kCFAllocatorDefault, 5, null, null)
            if (addQuery != null) {
                CFDictionaryAddValue(addQuery, kSecClass, kSecClassGenericPassword)
                CFDictionaryAddValue(addQuery, kSecAttrService, SERVICE.cfString())
                CFDictionaryAddValue(addQuery, kSecAttrAccount, account.cfString())
                CFDictionaryAddValue(addQuery, kSecValueData, cfData)
                CFDictionaryAddValue(
                    addQuery,
                    kSecAttrAccessible,
                    kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                )
                status = SecItemAdd(addQuery, null)
            }
        }

        // Best-effort; session store does not throw on Keychain errors
        if (status != errSecSuccess && status != errSecDuplicateItem) {
            // optionally log status
        }
    }

    private fun delete(account: String) {
        val query = createQuery(account, returnData = false) ?: return
        SecItemDelete(query)
    }

    private fun createQuery(account: String, returnData: Boolean): CFDictionaryRef? {
        val capacity = if (returnData) 5L else 3L
        val dict = CFDictionaryCreateMutable(kCFAllocatorDefault, capacity, null, null) ?: return null
        CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(dict, kSecAttrService, SERVICE.cfString())
        CFDictionaryAddValue(dict, kSecAttrAccount, account.cfString())
        if (returnData) {
            CFDictionaryAddValue(dict, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(dict, kSecMatchLimit, kSecMatchLimitOne)
        }
        return dict
    }

    private fun String.cfString(): CFTypeRef? =
        CFStringCreateWithCString(kCFAllocatorDefault, this, kCFStringEncodingUTF8)

    private companion object {
        const val SERVICE = "com.kenlikdev.qmarket.session"
        const val KEY_ACCESS = "accessToken"
        const val KEY_REFRESH = "refreshToken"
        const val KEY_EMAIL = "email"
    }
}

actual fun createPlatformSessionStore(): SessionStore = IosKeychainSessionStore()
