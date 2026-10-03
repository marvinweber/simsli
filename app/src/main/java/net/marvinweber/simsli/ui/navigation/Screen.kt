package net.marvinweber.simsli.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import net.marvinweber.simsli.ui.screens.household.HouseholdMembersScreen
import net.marvinweber.simsli.ui.screens.home.HomeScreen
import net.marvinweber.simsli.ui.screens.item.ItemDetailScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object ItemDetail : Screen("item_detail/{itemId}") {
        fun createRoute(itemId: String?) = "item_detail/${itemId.orEmpty()}"
    }
    data object Onboarding : Screen("onboarding")
    data object HouseholdMembers : Screen("household_members")
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
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                targetOffset = { fullWidth -> fullWidth / 3 },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                initialOffset = { fullWidth -> fullWidth / 3 },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToItemDetail = { itemId ->
                    navController.navigate(Screen.ItemDetail.createRoute(itemId))
                },
                onNavigateToHouseholdMembers = {
                    navController.navigate(Screen.HouseholdMembers.route)
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
        composable(Screen.HouseholdMembers.route) {
            HouseholdMembersScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
