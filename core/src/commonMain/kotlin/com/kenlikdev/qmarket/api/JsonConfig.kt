package com.kenlikdev.qmarket.api

import kotlinx.serialization.json.Json

/** Shared JSON config aligned with typical Spring Boot defaults (ignore unknown, skip nulls). */
object QMarketJson {
    val format: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            explicitNulls = false
        }
}
