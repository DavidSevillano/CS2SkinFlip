package com.burixer85.cs2skinflip.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object Portfolio : Screen("portfolio")
    object Settings : Screen("settings")
    object Watchlist : Screen("watchlist")
    object Alerts : Screen("alerts")
    object SkinDetail : Screen("skin_detail/{skinId}") {
        fun createRoute(skinId: String) = "skin_detail/$skinId"
    }
}
