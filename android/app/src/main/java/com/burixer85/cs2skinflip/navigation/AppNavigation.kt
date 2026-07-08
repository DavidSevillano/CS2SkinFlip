package com.burixer85.cs2skinflip.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.burixer85.cs2skinflip.core.network.NetworkStatusViewModel
import com.burixer85.cs2skinflip.core.ui.components.OfflineBanner
import com.burixer85.cs2skinflip.core.ui.theme.AccentOrange
import com.burixer85.cs2skinflip.core.ui.theme.Surface
import com.burixer85.cs2skinflip.core.ui.theme.TextSecondary
import com.burixer85.cs2skinflip.features.alerts.AlertsScreen
import com.burixer85.cs2skinflip.features.home.HomeScreen
import com.burixer85.cs2skinflip.features.portfolio.PortfolioScreen
import com.burixer85.cs2skinflip.features.search.SearchScreen
import com.burixer85.cs2skinflip.features.settings.SettingsScreen
import com.burixer85.cs2skinflip.features.skindetail.SkinDetailScreen
import com.burixer85.cs2skinflip.features.watchlist.WatchlistScreen

private data class BottomNavItem(
    val label: String,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem("Home", Screen.Home.route, Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem("Search", Screen.Search.route, Icons.Filled.Search, Icons.Outlined.Search),
    BottomNavItem("Alerts", Screen.Alerts.route, Icons.Filled.Notifications, Icons.Outlined.Notifications),
    BottomNavItem("Settings", Screen.Settings.route, Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun AppNavigation(
    initialSkinId: String? = null,
    onNavigatedToSkin: () -> Unit = {},
    networkStatusViewModel: NetworkStatusViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val isOnline by networkStatusViewModel.isOnline.collectAsState()

    // Handle notification tap: navigate to skin detail once navController is ready
    LaunchedEffect(initialSkinId) {
        if (initialSkinId != null) {
            navController.navigate(Screen.SkinDetail.createRoute(initialSkinId))
            onNavigatedToSkin()
        }
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Column(Modifier.fillMaxSize()) {
        OfflineBanner(isOnline = isOnline)
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    CS2BottomBar(navController = navController)
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onSkinClick = { skinId ->
                            navController.navigate(Screen.SkinDetail.createRoute(skinId))
                        }
                    )
                }

                composable(Screen.Search.route) {
                    SearchScreen(
                        onSkinClick = { skinId ->
                            navController.navigate(Screen.SkinDetail.createRoute(skinId))
                        }
                    )
                }

                composable(Screen.Alerts.route) {
                    AlertsScreen(
                        onSkinClick = { skinId ->
                            navController.navigate(Screen.SkinDetail.createRoute(skinId))
                        }
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onWatchlistClick = {
                            navController.navigate(Screen.Watchlist.route)
                        },
                        onPortfolioClick = {
                            navController.navigate(Screen.Portfolio.route)
                        },
                    )
                }

                composable(Screen.Watchlist.route) {
                    WatchlistScreen(
                        onSkinClick = { skinId ->
                            navController.navigate(Screen.SkinDetail.createRoute(skinId))
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Portfolio.route) {
                    PortfolioScreen(
                        onSkinClick = { skinId ->
                            navController.navigate(Screen.SkinDetail.createRoute(skinId))
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Screen.SkinDetail.route,
                    arguments = listOf(navArgument("skinId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val skinId = backStackEntry.arguments?.getString("skinId") ?: return@composable
                    SkinDetailScreen(
                        skinId = skinId,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CS2BottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        containerColor = Surface
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AccentOrange,
                    selectedTextColor = AccentOrange,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = AccentOrange.copy(alpha = 0.12f)
                )
            )
        }
    }
}
