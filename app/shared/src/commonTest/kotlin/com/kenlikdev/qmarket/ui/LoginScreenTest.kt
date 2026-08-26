package com.kenlikdev.qmarket.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LoginScreenTest {
    @Test
    fun loginButtonVisibleAndEnabledWhenNotLoading() =
        runComposeUiTest {
            setContent {
                LoginScreen(
                    email = "admin@qmarket.local",
                    password = "admin123",
                    error = null,
                    loading = false,
                    onEmailChange = {},
                    onPasswordChange = {},
                    onLogin = {},
                    onCreateAccount = {},
                    onBrowseCatalog = {},
                )
            }
            onNodeWithTag("loginSubmit").assertIsEnabled()
            onNodeWithTag("loginEmail").assertExists()
            onNodeWithTag("loginPassword").assertExists()
            onNodeWithText("Login").assertExists()
            onNodeWithText("Create account").assertExists()
        }

    @Test
    fun loginButtonDisabledWhileLoading() =
        runComposeUiTest {
            setContent {
                LoginScreen(
                    email = "a@b.c",
                    password = "password",
                    error = null,
                    loading = true,
                    onEmailChange = {},
                    onPasswordChange = {},
                    onLogin = {},
                    onCreateAccount = {},
                    onBrowseCatalog = {},
                )
            }
            onNodeWithTag("loginSubmit").assertIsNotEnabled()
        }

    @Test
    fun loginClickInvokesCallback() =
        runComposeUiTest {
            var clicked = false
            setContent {
                LoginScreen(
                    email = "a@b.c",
                    password = "password",
                    error = null,
                    loading = false,
                    onEmailChange = {},
                    onPasswordChange = {},
                    onLogin = { clicked = true },
                    onCreateAccount = {},
                    onBrowseCatalog = {},
                )
            }
            onNodeWithTag("loginSubmit").performClick()
            assertTrue(clicked)
        }

    @Test
    fun errorTextIsShown() =
        runComposeUiTest {
            setContent {
                LoginScreen(
                    email = "a@b.c",
                    password = "x",
                    error = "Invalid credentials",
                    loading = false,
                    onEmailChange = {},
                    onPasswordChange = {},
                    onLogin = {},
                    onCreateAccount = {},
                    onBrowseCatalog = {},
                )
            }
            onNodeWithText("Invalid credentials").assertExists()
        }
}
