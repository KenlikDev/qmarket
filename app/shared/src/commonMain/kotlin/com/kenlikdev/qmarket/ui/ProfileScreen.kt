package com.kenlikdev.qmarket.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kenlikdev.qmarket.api.ProfileDto
import com.kenlikdev.qmarket.validation.ClientInputValidation

@Composable
fun ProfileScreen(
    profile: ProfileDto?,
    firstName: String,
    lastName: String,
    phone: String,
    currentPassword: String,
    newPassword: String,
    loggedIn: Boolean,
    userLabel: String?,
    cartCount: Int?,
    error: String?,
    statusMessage: String?,
    loading: Boolean,
    onBackToCatalog: () -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSaveProfile: () -> Unit,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onUpdatePassword: () -> Unit,
    onCart: () -> Unit,
    onOrders: () -> Unit,
    onAddresses: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopBar(
            title = "Profile",
            subtitle = userLabel ?: "Guest",
            loggedIn = loggedIn,
            cartCount = cartCount,
            onCart = onCart,
            onOrders = onOrders,
            onAddresses = onAddresses,
            onProfile = null,
            onLogout = onLogout,
        )
        TextButton(onClick = onBackToCatalog) {
            Text("← Back to catalog")
        }
        ErrorText(error, modifier = Modifier.padding(horizontal = 16.dp))
        statusMessage?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (loading && profile == null) {
            LoadingCenter()
        } else {
            Column(
                modifier =
                    Modifier
                        .weight(1f, fill = true)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
            ) {
                Text(
                    profile?.email ?: "",
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = firstName,
                    onValueChange = onFirstNameChange,
                    label = { Text("First name") },
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("profileFirstName"),
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = onLastNameChange,
                    label = { Text("Last name") },
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("profileLastName"),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = onPhoneChange,
                    label = { Text("Phone") },
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("profilePhone"),
                )
                if (phone.isNotEmpty() && !ClientInputValidation.isValidPhoneInput(phone)) {
                    Text(
                        "Phone: 7-15 digits, optional leading +",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (firstName.isNotEmpty() && !ClientInputValidation.isValidPersonName(firstName)) {
                    Text(
                        "First name: letters, spaces, hyphen, apostrophe, period",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (lastName.isNotEmpty() && !ClientInputValidation.isValidPersonName(lastName)) {
                    Text(
                        "Last name: letters, spaces, hyphen, apostrophe, period",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = onSaveProfile,
                    enabled =
                        !loading &&
                            ClientInputValidation.isValidPhoneInput(phone) &&
                            ClientInputValidation.isValidPersonName(firstName) &&
                            ClientInputValidation.isValidPersonName(lastName),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .testTag("profileSave"),
                ) {
                    Text("Save profile")
                }
                Text(
                    "Change password",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 24.dp),
                )
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = onCurrentPasswordChange,
                    label = { Text("Current password") },
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = onNewPasswordChange,
                    label = { Text("New password") },
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                )
                Button(
                    onClick = onUpdatePassword,
                    enabled =
                        !loading &&
                            currentPassword.isNotBlank() &&
                            ClientInputValidation.isValidPassword(newPassword),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .testTag("profileChangePassword"),
                ) {
                    Text("Update password")
                }
            }
        }
    }
}
