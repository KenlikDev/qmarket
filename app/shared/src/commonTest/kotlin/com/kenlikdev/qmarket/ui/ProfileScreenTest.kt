package com.kenlikdev.qmarket.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kenlikdev.qmarket.api.ProfileDto
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProfileScreenTest {
    private val sampleProfile =
        ProfileDto(
            id = "u1",
            email = "user@test.com",
            firstName = "Ada",
            lastName = "Lovelace",
            phone = "+79001234567",
            emailVerified = false,
            roles = listOf("ROLE_USER"),
        )

    @Test
    fun saveEnabledWhenNamesAndPhoneValid() =
        runComposeUiTest {
            setContent {
                ProfileScreen(
                    profile = sampleProfile,
                    firstName = "Ada",
                    lastName = "Lovelace",
                    phone = "+79001234567",
                    currentPassword = "",
                    newPassword = "",
                    loggedIn = true,
                    userLabel = "user@test.com",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onFirstNameChange = {},
                    onLastNameChange = {},
                    onPhoneChange = {},
                    onSaveProfile = {},
                    onCurrentPasswordChange = {},
                    onNewPasswordChange = {},
                    onUpdatePassword = {},
                    onCart = {},
                    onOrders = {},
                    onAddresses = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("profileSave").assertIsEnabled()
            onNodeWithTag("profileFirstName").assertExists()
            onNodeWithTag("profilePhone").assertExists()
        }

    @Test
    fun saveDisabledWhenPhoneInvalid() =
        runComposeUiTest {
            setContent {
                ProfileScreen(
                    profile = sampleProfile,
                    firstName = "Ada",
                    lastName = "Lovelace",
                    phone = "abc",
                    currentPassword = "",
                    newPassword = "",
                    loggedIn = true,
                    userLabel = "user@test.com",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onFirstNameChange = {},
                    onLastNameChange = {},
                    onPhoneChange = {},
                    onSaveProfile = {},
                    onCurrentPasswordChange = {},
                    onNewPasswordChange = {},
                    onUpdatePassword = {},
                    onCart = {},
                    onOrders = {},
                    onAddresses = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("profileSave").assertIsNotEnabled()
            onNodeWithText("Phone: 7-15 digits, optional leading +").assertExists()
        }

    @Test
    fun changePasswordDisabledWhenNewPasswordShort() =
        runComposeUiTest {
            setContent {
                ProfileScreen(
                    profile = sampleProfile,
                    firstName = "Ada",
                    lastName = "Lovelace",
                    phone = "",
                    currentPassword = "oldpass12",
                    newPassword = "short",
                    loggedIn = true,
                    userLabel = "user@test.com",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onFirstNameChange = {},
                    onLastNameChange = {},
                    onPhoneChange = {},
                    onSaveProfile = {},
                    onCurrentPasswordChange = {},
                    onNewPasswordChange = {},
                    onUpdatePassword = {},
                    onCart = {},
                    onOrders = {},
                    onAddresses = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("profileChangePassword").assertIsNotEnabled()
        }

    @Test
    fun saveClickInvokesCallback() =
        runComposeUiTest {
            var saved = false
            setContent {
                ProfileScreen(
                    profile = sampleProfile,
                    firstName = "Ada",
                    lastName = "Lovelace",
                    phone = "+79001234567",
                    currentPassword = "",
                    newPassword = "",
                    loggedIn = true,
                    userLabel = "user@test.com",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onFirstNameChange = {},
                    onLastNameChange = {},
                    onPhoneChange = {},
                    onSaveProfile = { saved = true },
                    onCurrentPasswordChange = {},
                    onNewPasswordChange = {},
                    onUpdatePassword = {},
                    onCart = {},
                    onOrders = {},
                    onAddresses = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("profileSave").performClick()
            assertTrue(saved)
        }
}
