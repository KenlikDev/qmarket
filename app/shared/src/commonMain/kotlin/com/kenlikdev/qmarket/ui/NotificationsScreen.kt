package com.kenlikdev.qmarket.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kenlikdev.qmarket.api.NotificationDto

@Composable
fun NotificationsScreen(
    notifications: List<NotificationDto>,
    unreadCount: Long,
    loggedIn: Boolean,
    userLabel: String?,
    cartCount: Int?,
    error: String?,
    statusMessage: String?,
    loading: Boolean,
    onBackToCatalog: () -> Unit,
    onMarkRead: (NotificationDto) -> Unit,
    onMarkAllRead: () -> Unit,
    onRefresh: () -> Unit,
    onCart: () -> Unit,
    onOrders: () -> Unit,
    onAddresses: () -> Unit,
    onProfile: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopBar(
            title = if (unreadCount > 0) "Notifications ($unreadCount)" else "Notifications",
            subtitle = userLabel ?: "Guest",
            loggedIn = loggedIn,
            cartCount = cartCount,
            onCart = onCart,
            onOrders = onOrders,
            onAddresses = onAddresses,
            onProfile = onProfile,
            onLogout = onLogout,
        )
        TextButton(onClick = onBackToCatalog, modifier = Modifier.testTag("notificationsBack")) {
            Text("← Catalog")
        }
        ErrorText(error, modifier = Modifier.padding(horizontal = 16.dp))
        TextButton(
            onClick = onMarkAllRead,
            enabled = !loading && unreadCount > 0,
            modifier = Modifier.testTag("notificationsMarkAll"),
        ) {
            Text("Mark all read")
        }
        TextButton(
            onClick = onRefresh,
            enabled = !loading,
            modifier = Modifier.testTag("notificationsRefresh"),
        ) {
            Text("Refresh")
        }
        statusMessage?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (loading && notifications.isEmpty()) {
            LoadingCenter()
        } else if (notifications.isEmpty()) {
            Text(
                "No notifications yet",
                modifier = Modifier.padding(16.dp).testTag("notificationsEmpty"),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(notifications, key = { it.id }) { n ->
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag("notification_${n.id}"),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                n.title + if (!n.read) " · new" else "",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(n.body, style = MaterialTheme.typography.bodyMedium)
                            n.createdAt?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                            if (!n.read) {
                                TextButton(
                                    onClick = { onMarkRead(n) },
                                    enabled = !loading,
                                    modifier = Modifier.testTag("notificationMarkRead_${n.id}"),
                                ) {
                                    Text("Mark read")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
