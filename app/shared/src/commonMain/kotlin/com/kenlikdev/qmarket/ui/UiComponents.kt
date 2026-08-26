package com.kenlikdev.qmarket.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TopBar(
    title: String,
    subtitle: String,
    loggedIn: Boolean,
    cartCount: Int?,
    onCart: (() -> Unit)?,
    onOrders: (() -> Unit)?,
    onAddresses: (() -> Unit)?,
    onProfile: (() -> Unit)?,
    onLogout: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loggedIn && onAddresses != null) {
                TextButton(onClick = onAddresses) {
                    Text("Addresses")
                }
            }
            if (loggedIn && onProfile != null) {
                TextButton(onClick = onProfile) {
                    Text("Profile")
                }
            }
            if (loggedIn && onOrders != null) {
                TextButton(onClick = onOrders) {
                    Text("Orders")
                }
            }
            if (loggedIn && onCart != null) {
                TextButton(onClick = onCart) {
                    Text(
                        if (cartCount != null && cartCount > 0) {
                            "Cart ($cartCount)"
                        } else {
                            "Cart"
                        },
                    )
                }
            }
            TextButton(onClick = onLogout) {
                Text(if (loggedIn) "Logout" else "Login")
            }
        }
    }
}

@Composable
fun ErrorText(
    error: String?,
    modifier: Modifier = Modifier,
) {
    if (error != null) {
        Text(
            error,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun LoadingCenter() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}
