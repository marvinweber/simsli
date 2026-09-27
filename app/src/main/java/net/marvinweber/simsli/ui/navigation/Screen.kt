package net.marvinweber.simsli.ui.navigation

import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.marvinweber.simsli.ui.screens.home.HomeScreen
import net.marvinweber.simsli.ui.screens.item.ItemDetailScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object ItemDetail : Screen("item_detail/{itemId}") {
        fun createRoute(itemId: String?) = "item_detail/${itemId.orEmpty()}"
    }
    data object Onboarding : Screen("onboarding")
}

/**
 * Route-level navigation. The three tabs (List · Catalog · Settings) live
 * inside HomeScreen's bottom bar; only detail-style screens are pushed here.
 */
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
                onNavigateToItemDetail = { itemId ->
                    navController.navigate(Screen.ItemDetail.createRoute(itemId))
                }
            )
        }
        composable(Screen.Onboarding.route) {
            Text("Onboarding")
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
