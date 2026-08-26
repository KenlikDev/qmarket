package com.kenlikdev.qmarket.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RegisterScreenTest {
    @Test
    fun registerDisabledWhenEmailInvalid() =
        runComposeUiTest {
            setContent {
                RegisterScreen(
                    email = "not-an-email",
                    password = "password12",
                    firstName = "Ann",
                    lastName = "Lee",
                    error = null,
                    loading = false,
                    onEmailChange = {},
                    onPasswordChange = {},
                    onFirstNameChange = {},
                    onLastNameChange = {},
                    onRegister = {},
                    onBackToLogin = {},
                )
            }
            onNodeWithTag("registerSubmit").assertIsNotEnabled()
        }

    @Test
    fun registerDisabledWhenPasswordShort() =
        runComposeUiTest {
            setContent {
                RegisterScreen(
                    email = "a@b.co",
                    password = "short",
                    firstName = "Ann",
                    lastName = "Lee",
                    error = null,
                    loading = false,
                    onEmailChange = {},
                    onPasswordChange = {},
                    onFirstNameChange = {},
                    onLastNameChange = {},
                    onRegister = {},
                    onBackToLogin = {},
                )
            }
            onNodeWithTag("registerSubmit").assertIsNotEnabled()
        }

    @Test
    fun registerEnabledWhenFormValid() =
        runComposeUiTest {
            var registered = false
            setContent {
                RegisterScreen(
                    email = "user@qmarket.local",
                    password = "password12",
                    firstName = "Ann",
                    lastName = "Lee",
                    error = null,
                    loading = false,
                    onEmailChange = {},
                    onPasswordChange = {},
                    onFirstNameChange = {},
                    onLastNameChange = {},
                    onRegister = { registered = true },
                    onBackToLogin = {},
                )
            }
            onNodeWithTag("registerSubmit").assertIsEnabled()
            onNodeWithTag("registerSubmit").performClick()
            assertTrue(registered)
        }
}
