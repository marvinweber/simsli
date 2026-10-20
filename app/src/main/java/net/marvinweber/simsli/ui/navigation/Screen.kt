package net.marvinweber.simsli.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import net.marvinweber.simsli.R
import net.marvinweber.simsli.data.repository.HostingMode
import net.marvinweber.simsli.ui.screens.home.HomeScreen
import net.marvinweber.simsli.ui.screens.household.HouseholdMembersScreen
import net.marvinweber.simsli.ui.screens.household.HouseholdScreen
import net.marvinweber.simsli.ui.screens.household.HouseholdStatisticsScreen
import net.marvinweber.simsli.ui.screens.item.ItemDetailScreen
import net.marvinweber.simsli.ui.screens.item.ItemPricesScreen
import net.marvinweber.simsli.ui.screens.onboarding.OnboardingScreen
import net.marvinweber.simsli.ui.screens.settings.SettingsScreen
import net.marvinweber.simsli.ui.screens.signin.SignInScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")

    /** Nested graph for the item editor: detail form + Store prices share one ItemDetailViewModel. */
    data object ItemEdit : Screen("item_edit/{itemId}") {
        fun createRoute(itemId: String?) = "item_edit/${itemId.orEmpty()}"
    }
    data object ItemDetail : Screen("item_detail")
    data object ItemPrices : Screen("item_prices")
    data object Onboarding : Screen("onboarding?mode={mode}") {
        fun createRoute(mode: String? = null): String =
            if (mode.isNullOrBlank()) "onboarding" else "onboarding?mode=$mode"
    }
    data object Settings : Screen("settings")
    data object SignIn : Screen("sign_in?host={host}") {
        fun createRoute(host: String?): String =
            if (host.isNullOrBlank()) "sign_in" else "sign_in?host=${Uri.encode(host)}"
    }
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
    startDestination: String = Screen.Home.route,
    modifier: Modifier = Modifier
) {
    if (inviteDeepLinkManager != null) {
        androidx.compose.runtime.LaunchedEffect(inviteDeepLinkManager) {
            inviteDeepLinkManager.navEvents.collect { target ->
                when (target) {
                    is InviteNavTarget.JoinHousehold -> {
                        // Joined out of onboarding (deep-link join path): pop the
                        // whole onboarding stack so back doesn't return to it.
                        val fromOnboarding = navController.currentBackStack.value.any {
                            it.destination.route == Screen.Onboarding.route
                        }
                        navController.navigate(Screen.Household.route) {
                            launchSingleTop = true
                            if (fromOnboarding) popUpTo(0) { inclusive = true }
                        }
                        inviteDeepLinkManager.consumeNavEvent()
                    }
                    is InviteNavTarget.SignIn -> {
                        navController.navigate(Screen.SignIn.createRoute(target.host)) {
                            launchSingleTop = true
                        }
                        inviteDeepLinkManager.consumeNavEvent()
                    }
                }
            }
        }

        // Cross-server invite (HH-3): warn before signing out + wiping + switching.
        val serverSwitch by inviteDeepLinkManager.pendingServerSwitch.collectAsState()
        serverSwitch?.let { request ->
            AlertDialog(
                onDismissRequest = inviteDeepLinkManager::dismissServerSwitch,
                title = { Text(stringResource(R.string.invite_switch_server_title)) },
                text = {
                    Text(
                        if (request.mode == HostingMode.CLOUD) {
                            stringResource(R.string.invite_switch_server_body_cloud)
                        } else {
                            stringResource(R.string.invite_switch_server_body_selfhosted, request.host.orEmpty())
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = inviteDeepLinkManager::confirmServerSwitch) {
                        Text(stringResource(R.string.invite_switch_server_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = inviteDeepLinkManager::dismissServerSwitch) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
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
                },
                onNavigateToSignIn = {
                    navController.navigate(Screen.SignIn.createRoute(null))
                },
                onNavigateToGuide = {
                    navController.navigate(Screen.Onboarding.createRoute("replay"))
                }
            )
        }
        composable(
            Screen.SignIn.route,
            arguments = listOf(navArgument("host") {
                type = NavType.StringType
                defaultValue = ""
            })
        ) {
            SignInScreen(
                onNavigateBack = {
                    // With a pending invite code, the manager has already navigated on
                    // to the Household screen — only pop while sign-in is still on top.
                    if (navController.currentDestination?.route == Screen.SignIn.route) {
                        navController.popBackStack()
                    }
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
        composable(
            Screen.Onboarding.route,
            arguments = listOf(navArgument("mode") {
                type = NavType.StringType
                defaultValue = "first"
            })
        ) {
            OnboardingScreen(
                onNavigateToSignIn = {
                    navController.navigate(Screen.SignIn.createRoute(null))
                },
                onNavigateHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onClose = {
                    navController.popBackStack()
                }
            )
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
