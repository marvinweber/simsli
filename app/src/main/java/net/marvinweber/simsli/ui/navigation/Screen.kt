package net.marvinweber.simsli.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import net.marvinweber.simsli.R
import net.marvinweber.simsli.ui.screens.home.HomeScreen
import net.marvinweber.simsli.ui.screens.household.HouseholdMembersScreen
import net.marvinweber.simsli.ui.screens.household.HouseholdScreen
import net.marvinweber.simsli.ui.screens.household.HouseholdStatisticsScreen
import net.marvinweber.simsli.ui.screens.item.ItemDetailScreen
import net.marvinweber.simsli.ui.screens.item.ItemPricesScreen
import net.marvinweber.simsli.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")

    /** Nested graph for the item editor: detail form + Store prices share one ItemDetailViewModel. */
    data object ItemEdit : Screen("item_edit/{itemId}") {
        fun createRoute(itemId: String?) = "item_edit/${itemId.orEmpty()}"
    }
    data object ItemDetail : Screen("item_detail")
    data object ItemPrices : Screen("item_prices")
    data object Onboarding : Screen("onboarding")
    data object Settings : Screen("settings")
    data object Household : Screen("household")
    data object HouseholdMembers : Screen("household_members")
    data object HouseholdStatistics : Screen("household_statistics")
}

/**
 * Route-level navigation. The three tabs (Dashboard · List · Catalog) live
 * inside HomeScreen's bottom bar; detail & settings screens are pushed here.
 */
@Composable
fun SimsliNavHost(
    navController: NavHostController,
    inviteDeepLinkManager: InviteDeepLinkManager? = null,
    modifier: Modifier = Modifier
) {
    if (inviteDeepLinkManager != null) {
        androidx.compose.runtime.LaunchedEffect(inviteDeepLinkManager) {
            inviteDeepLinkManager.navEvents.collect { target ->
                when (target) {
                    is InviteNavTarget.JoinHousehold -> {
                        navController.navigate(Screen.Household.route) {
                            launchSingleTop = true
                        }
                        inviteDeepLinkManager.consumeNavEvent()
                    }
                    is InviteNavTarget.SignIn -> {
                        navController.navigate(Screen.Settings.route) {
                            launchSingleTop = true
                        }
                        inviteDeepLinkManager.consumeNavEvent()
                    }
                }
            }
        }
    }

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
                    navController.navigate(Screen.ItemEdit.createRoute(itemId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToHousehold = {
                    navController.navigate(Screen.Household.route)
                },
                onNavigateToHouseholdStatistics = {
                    navController.navigate(Screen.HouseholdStatistics.route)
                }
            )
        }
        composable(Screen.Household.route) {
            HouseholdScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToHouseholdMembers = {
                    navController.navigate(Screen.HouseholdMembers.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        composable(Screen.Onboarding.route) {
            Text(stringResource(R.string.onboarding_placeholder))
        }
        // Item detail + Store prices live in one nested graph so both share a
        // graph-scoped ItemDetailViewModel: the prices screen edits pending
        // changes there, applied when the item's Save button is pressed.
        navigation(
            route = Screen.ItemEdit.route,
            startDestination = Screen.ItemDetail.route
        ) {
            composable(Screen.ItemDetail.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ItemEdit.route) }
                ItemDetailScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToItemPrices = {
                        navController.navigate(Screen.ItemPrices.route)
                    },
                    viewModel = hiltViewModel(parentEntry)
                )
            }
            composable(Screen.ItemPrices.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.ItemEdit.route) }
                ItemPricesScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    viewModel = hiltViewModel(parentEntry)
                )
            }
        }
        composable(Screen.HouseholdMembers.route) {
            HouseholdMembersScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.HouseholdStatistics.route) {
            HouseholdStatisticsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
