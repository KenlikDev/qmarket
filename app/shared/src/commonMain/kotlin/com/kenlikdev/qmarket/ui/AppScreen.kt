package com.kenlikdev.qmarket.ui

import com.kenlikdev.qmarket.api.OrderDto

sealed interface AppScreen {
    data object Login : AppScreen

    data object Register : AppScreen

    data object Catalog : AppScreen

    data object Cart : AppScreen

    data object Orders : AppScreen

    data object Profile : AppScreen

    data object Addresses : AppScreen

    data object Admin : AppScreen

    data class OrderDone(val order: OrderDto) : AppScreen
}
