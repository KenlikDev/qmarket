package com.kenlikdev.qmarket.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kenlikdev.qmarket.api.AddressDto
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AddressesScreenTest {
    private val saved =
        AddressDto(
            id = "a1",
            label = "Home",
            recipientName = "Ada Lovelace",
            phone = "+79001234567",
            country = "RU",
            region = null,
            city = "Moscow",
            streetLine1 = "Tverskaya 1",
            streetLine2 = null,
            postalCode = "101000",
            default = true,
        )

    @Test
    fun saveEnabledWhenFormValid() =
        runComposeUiTest {
            setContent {
                AddressesScreen(
                    addresses = emptyList(),
                    addrRecipient = "Ada Lovelace",
                    addrCity = "Moscow",
                    addrStreet = "Tverskaya 1",
                    addrPhone = "+79001234567",
                    loggedIn = true,
                    userLabel = "user@test.com",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onSetDefault = {},
                    onDelete = {},
                    onRecipientChange = {},
                    onCityChange = {},
                    onStreetChange = {},
                    onPhoneChange = {},
                    onSave = {},
                    onCart = {},
                    onOrders = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("addressSave").assertIsEnabled()
        }

    @Test
    fun saveDisabledWhenPhoneInvalid() =
        runComposeUiTest {
            setContent {
                AddressesScreen(
                    addresses = emptyList(),
                    addrRecipient = "Ada Lovelace",
                    addrCity = "Moscow",
                    addrStreet = "Tverskaya 1",
                    addrPhone = "12",
                    loggedIn = true,
                    userLabel = "user@test.com",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onSetDefault = {},
                    onDelete = {},
                    onRecipientChange = {},
                    onCityChange = {},
                    onStreetChange = {},
                    onPhoneChange = {},
                    onSave = {},
                    onCart = {},
                    onOrders = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("addressSave").assertIsNotEnabled()
            onNodeWithText("Phone: 7-15 digits, optional leading +").assertExists()
        }

    @Test
    fun listShowsSetDefaultForNonDefault() =
        runComposeUiTest {
            // UI shows recipientName + city/street (label is not rendered)
            val other =
                saved.copy(
                    id = "a2",
                    label = "Work",
                    city = "Saint Petersburg",
                    streetLine1 = "Nevsky 10",
                    default = false,
                )
            setContent {
                AddressesScreen(
                    addresses = listOf(saved, other),
                    addrRecipient = "",
                    addrCity = "",
                    addrStreet = "",
                    addrPhone = "",
                    loggedIn = true,
                    userLabel = "user@test.com",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onSetDefault = {},
                    onDelete = {},
                    onRecipientChange = {},
                    onCityChange = {},
                    onStreetChange = {},
                    onPhoneChange = {},
                    onSave = {},
                    onCart = {},
                    onOrders = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("addressSetDefault").assertExists().assertIsEnabled()
            onNodeWithText("Saint Petersburg", substring = true).assertExists()
            onNodeWithText("Default").assertExists()
        }
}
