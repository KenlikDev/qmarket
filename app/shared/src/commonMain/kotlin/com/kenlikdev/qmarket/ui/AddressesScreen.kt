package com.kenlikdev.qmarket.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kenlikdev.qmarket.api.AddressDto
import com.kenlikdev.qmarket.validation.ClientInputValidation

@Composable
fun AddressesScreen(
    addresses: List<AddressDto>,
    addrRecipient: String,
    addrCity: String,
    addrStreet: String,
    addrPhone: String,
    loggedIn: Boolean,
    userLabel: String?,
    cartCount: Int?,
    error: String?,
    statusMessage: String?,
    loading: Boolean,
    onBackToCatalog: () -> Unit,
    onSetDefault: (AddressDto) -> Unit,
    onDelete: (AddressDto) -> Unit,
    onRecipientChange: (String) -> Unit,
    onCityChange: (String) -> Unit,
    onStreetChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSave: () -> Unit,
    onCart: () -> Unit,
    onOrders: () -> Unit,
    onProfile: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopBar(
            title = "Addresses",
            subtitle = userLabel ?: "Guest",
            loggedIn = loggedIn,
            cartCount = cartCount,
            onCart = onCart,
            onOrders = onOrders,
            onAddresses = null,
            onProfile = onProfile,
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
        Column(
            modifier =
                Modifier
                    .weight(1f, fill = true)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            if (addresses.isEmpty()) {
                Text("No saved addresses", style = MaterialTheme.typography.bodyLarge)
            } else {
                addresses.forEach { addr ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(addr.recipientName, style = MaterialTheme.typography.titleMedium)
                            Text(addr.formatted ?: "${addr.city}, ${addr.streetLine1}")
                            if (addr.default) {
                                Text(
                                    "Default",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Row {
                                if (!addr.default) {
                                    TextButton(
                                        onClick = { onSetDefault(addr) },
                                        enabled = !loading,
                                        modifier = Modifier.testTag("addressSetDefault"),
                                    ) {
                                        Text("Set default")
                                    }
                                }
                                TextButton(
                                    onClick = { onDelete(addr) },
                                    enabled = !loading,
                                ) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
            Text(
                "Add address",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            OutlinedTextField(
                value = addrRecipient,
                onValueChange = onRecipientChange,
                label = { Text("Recipient name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            if (addrRecipient.isNotEmpty() && !ClientInputValidation.isValidPersonName(addrRecipient)) {
                Text(
                    "Recipient: letters, spaces, hyphen, apostrophe, period",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = addrCity,
                onValueChange = onCityChange,
                label = { Text("City") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = addrStreet,
                onValueChange = onStreetChange,
                label = { Text("Street") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = addrPhone,
                onValueChange = onPhoneChange,
                label = { Text("Phone (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("addressPhone"),
            )
            if (addrPhone.isNotEmpty() && !ClientInputValidation.isValidPhoneInput(addrPhone)) {
                Text(
                    "Phone: 7-15 digits, optional leading +",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = onSave,
                enabled =
                    !loading &&
                        addrRecipient.isNotBlank() &&
                        ClientInputValidation.isValidPersonName(addrRecipient) &&
                        addrCity.isNotBlank() &&
                        addrStreet.isNotBlank() &&
                        ClientInputValidation.isValidPhoneInput(addrPhone),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("addressSave"),
            ) {
                Text("Save address")
            }
        }
    }
}
