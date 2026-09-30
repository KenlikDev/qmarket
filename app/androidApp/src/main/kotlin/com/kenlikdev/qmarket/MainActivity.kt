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
            App(apiBaseUrl = getString(R.string.qmarket_api_base_url))
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(apiBaseUrl = "http://10.0.2.2:8080")
}
