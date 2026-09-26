package net.marvinweber.simsli.ui.navigation

import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.marvinweber.simsli.ui.screens.home.HomeScreen
import net.marvinweber.simsli.ui.screens.item.ItemDetailScreen
import net.marvinweber.simsli.ui.screens.settings.SettingsScreen
import net.marvinweber.simsli.ui.screens.stores.StoresScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object ItemDetail : Screen("item_detail/{itemId}") {
        fun createRoute(itemId: String?) = "item_detail/${itemId.orEmpty()}"
    }
    data object StoreList : Screen("store_list")
    data object Settings : Screen("settings")
    data object Onboarding : Screen("onboarding")
}

@Composable
fun SimsliNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToStores = {
                    navController.navigate(Screen.StoreList.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToItemDetail = { itemId ->
                    navController.navigate(Screen.ItemDetail.createRoute(itemId))
                }
            )
        }
        composable(Screen.Onboarding.route) {
            Text("Onboarding")
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.StoreList.route) {
            StoresScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.ItemDetail.route) {
            ItemDetailScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
