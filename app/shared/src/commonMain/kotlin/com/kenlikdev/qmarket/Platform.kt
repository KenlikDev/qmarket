package com.kenlikdev.qmarket

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform