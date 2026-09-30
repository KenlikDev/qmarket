package com.kenlikdev.qmarket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kenlikdev.qmarket.network.initAndroidSessionStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initAndroidSessionStore(this)

        setContent {
            App(apiBaseUrl = BuildConfig.QMARKET_API_BASE_URL)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
