package com.kenlikdev.qmarket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kenlikdev.qmarket.api.LoginRequestDto
import com.kenlikdev.qmarket.api.ProductDto
import com.kenlikdev.qmarket.network.ApiException
import com.kenlikdev.qmarket.network.MutableTokenProvider
import com.kenlikdev.qmarket.network.QMarketApiClient
import com.kenlikdev.qmarket.network.createPlatformHttpClient
import com.kenlikdev.qmarket.network.defaultApiBaseUrl
import kotlinx.coroutines.launch

private sealed interface AppScreen {
    data object Login : AppScreen

    data object Catalog : AppScreen
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        val tokens = remember { MutableTokenProvider() }
        val http =
            remember {
                createPlatformHttpClient(
                    baseUrl = defaultApiBaseUrl(),
                    tokenProvider = tokens,
                )
            }
        val api = remember(http) { QMarketApiClient(http) }
        val scope = rememberCoroutineScope()

        var screen by remember { mutableStateOf<AppScreen>(AppScreen.Login) }
        var email by remember { mutableStateOf("admin@qmarket.local") }
        var password by remember { mutableStateOf("admin123") }
        var error by remember { mutableStateOf<String?>(null) }
        var loading by remember { mutableStateOf(false) }
        var products by remember { mutableStateOf<List<ProductDto>>(emptyList()) }
        var userLabel by remember { mutableStateOf<String?>(null) }

        fun loadCatalog() {
            scope.launch {
                loading = true
                error = null
                try {
                    val page = api.listProducts(size = 50)
                    products = page.content
                    screen = AppScreen.Catalog
                } catch (e: ApiException) {
                    error = e.message
                } catch (e: Exception) {
                    error = e.message ?: e.toString()
                } finally {
                    loading = false
                }
            }
        }

        Scaffold { padding ->
            when (screen) {
                AppScreen.Login -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("QMarket", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "API: ${defaultApiBaseUrl()}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                        )
                        if (error != null) {
                            Text(
                                error!!,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    loading = true
                                    error = null
                                    try {
                                        val auth =
                                            api.login(
                                                LoginRequestDto(
                                                    email = email.trim(),
                                                    password = password,
                                                ),
                                            )
                                        tokens.token = auth.accessToken
                                        userLabel = auth.user.email
                                        loadCatalog()
                                    } catch (e: ApiException) {
                                        error = e.message
                                        loading = false
                                    } catch (e: Exception) {
                                        error = e.message ?: e.toString()
                                        loading = false
                                    }
                                }
                            },
                            enabled = !loading,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                        ) {
                            Text(if (loading) "…" else "Login")
                        }
                        TextButton(
                            onClick = {
                                tokens.clear()
                                loadCatalog()
                            },
                            enabled = !loading,
                        ) {
                            Text("Browse catalog (anonymous)")
                        }
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
                        }
                    }
                }

                AppScreen.Catalog -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding),
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
                                Text("Catalog", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    userLabel ?: "Guest",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            TextButton(
                                onClick = {
                                    tokens.clear()
                                    userLabel = null
                                    products = emptyList()
                                    error = null
                                    screen = AppScreen.Login
                                },
                            ) {
                                Text("Logout")
                            }
                        }
                        if (error != null) {
                            Text(
                                error!!,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        if (loading && products.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(products, key = { it.id }) { product ->
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                product.name,
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                            Text(
                                                "${product.price} · stock ${product.stockQuantity}",
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            product.shortDescription?.let {
                                                Text(it, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            // no auto-login
        }
    }
}
