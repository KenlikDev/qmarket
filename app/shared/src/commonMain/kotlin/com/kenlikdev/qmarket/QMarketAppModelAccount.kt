package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.api.ChangePasswordRequestDto
import com.kenlikdev.qmarket.api.CreateAddressRequestDto
import com.kenlikdev.qmarket.api.UpdateAddressRequestDto
import com.kenlikdev.qmarket.api.UpdateProfileRequestDto
import com.kenlikdev.qmarket.ui.AppScreen

/**
 * Profile, addresses, and in-app notifications for [QMarketAppModel].
 */

fun QMarketAppModel.loadNotifications() {
    runApi {
        notifications = api.listNotifications(size = 50).content
        notificationsUnread = api.notificationsUnreadCount().unread
        screen = AppScreen.Notifications
    }
}

fun QMarketAppModel.loadProfile() {
    runApi {
        val p = api.getProfile()
        profile = p
        firstName = p.firstName.orEmpty()
        lastName = p.lastName.orEmpty()
        phone = p.phone.orEmpty()
        screen = AppScreen.Profile
    }
}

fun QMarketAppModel.loadAddresses(navigate: Boolean = true) {
    runApi {
        addresses = api.listAddresses()
        if (selectedAddressId == null) {
            selectedAddressId = addresses.firstOrNull { it.default }?.id
                ?: addresses.firstOrNull()?.id
        }
        if (navigate) screen = AppScreen.Addresses
    }
}

fun QMarketAppModel.saveProfile() {
    runApi {
        profile =
            api.updateProfile(
                UpdateProfileRequestDto(
                    firstName = firstName.trim().ifBlank { null },
                    lastName = lastName.trim().ifBlank { null },
                    phone = phone.trim().ifBlank { null },
                ),
            )
        statusMessage = "Profile saved"
    }
}

fun QMarketAppModel.updatePassword() {
    runApi {
        api.changePassword(
            ChangePasswordRequestDto(
                currentPassword = currentPassword,
                newPassword = newPassword,
            ),
        )
        currentPassword = ""
        newPassword = ""
        statusMessage = "Password updated"
    }
}

fun QMarketAppModel.addAddress() {
    runApi {
        val created =
            api.createAddress(
                CreateAddressRequestDto(
                    recipientName = addrRecipient.trim(),
                    city = addrCity.trim(),
                    streetLine1 = addrStreet.trim(),
                    phone = addrPhone.trim().ifBlank { null },
                    default = addresses.isEmpty(),
                ),
            )
        addresses = api.listAddresses()
        selectedAddressId = created.id
        addrRecipient = ""
        addrCity = ""
        addrStreet = ""
        addrPhone = ""
        statusMessage = "Address saved"
    }
}

fun QMarketAppModel.deleteAddress(id: String) {
    runApi {
        api.deleteAddress(id)
        addresses = api.listAddresses()
        if (selectedAddressId == id) {
            selectedAddressId = addresses.firstOrNull { it.default }?.id
                ?: addresses.firstOrNull()?.id
        }
        pendingCheckoutKey = null
        statusMessage = "Address deleted"
    }
}

fun QMarketAppModel.setDefaultAddress(id: String) {
    runApi {
        api.updateAddress(id, UpdateAddressRequestDto(default = true))
        addresses = api.listAddresses()
        selectedAddressId = id
        pendingCheckoutKey = null
        statusMessage = "Default address updated"
    }
}

fun QMarketAppModel.markNotificationRead(id: String) {
    runApi {
        api.markNotificationRead(id)
        notifications = api.listNotifications(size = 50).content
        notificationsUnread = api.notificationsUnreadCount().unread
    }
}

fun QMarketAppModel.refreshNotifications() {
    runApi {
        notifications = api.listNotifications(size = 50).content
        notificationsUnread = api.notificationsUnreadCount().unread
    }
}

fun QMarketAppModel.markAllNotificationsRead() {
    runApi {
        notificationsUnread = api.markAllNotificationsRead().unread
        notifications = api.listNotifications(size = 50).content
        statusMessage = "All notifications marked read"
    }
}

