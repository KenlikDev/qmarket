package com.kenlikdev.qmarket

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.lang.System.getenv
import java.lang.System.getProperty

private fun desktopApiBaseUrl(): String =
    getProperty("qmarket.api.base-url")
        ?.takeIf { it.isNotBlank() }
        ?: getenv("QMARKET_API_BASE_URL")?.takeIf { it.isNotBlank() }
        ?: "http://localhost:8080"

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "QMarket",
    ) {
        App(apiBaseUrl = desktopApiBaseUrl())
    }
}