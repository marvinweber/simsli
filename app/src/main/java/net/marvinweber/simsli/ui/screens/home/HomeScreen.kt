package net.marvinweber.simsli.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import net.marvinweber.simsli.R
import net.marvinweber.simsli.ui.screens.catalog.CatalogScreen
import net.marvinweber.simsli.ui.screens.dashboard.DashboardScreen
import net.marvinweber.simsli.ui.screens.list.ListTabContent

enum class HomeTab(@StringRes val labelRes: Int) {
    DASHBOARD(R.string.nav_tab_dashboard),
    LIST(R.string.nav_tab_list),
    CATALOG(R.string.nav_tab_catalog)
}

/**
 * Main screen: bottom tabs for the dashboard (preview), shopping list,
 * and the catalog (Items | Categories | Stores).
 *
 * Tab order: Dashboard, Shopping List, Catalog.
 * Default tab on launch: Shopping List.
 * Account & settings are accessed via the top-right circle icon.
 */
@Composable
fun HomeScreen(
    onNavigateToItemDetail: (String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    var selectedTabName by rememberSaveable { mutableStateOf(HomeTab.LIST.name) }
    val selectedTab = HomeTab.entries.firstOrNull { it.name == selectedTabName } ?: HomeTab.LIST
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val hasPendingWrites by viewModel.hasPendingWrites.collectAsState()
    val household by viewModel.household.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.quickActions.collect { action ->
            when (action) {
                net.marvinweber.simsli.ui.navigation.QuickAction.ADD_ITEM -> {
                    selectedTabName = HomeTab.LIST.name
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = { selectedTabName = tab.name },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    HomeTab.DASHBOARD -> Icons.Default.Insights
                                    HomeTab.LIST -> Icons.Default.ShoppingCart
                                    HomeTab.CATALOG -> Icons.Default.Inventory2
                                },
                                contentDescription = null
                            )
                        },
                        label = {
                            Text(stringResource(tab.labelRes))
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val tabStateHolder = rememberSaveableStateHolder()
            when (selectedTab) {
                HomeTab.DASHBOARD -> tabStateHolder.SaveableStateProvider(HomeTab.DASHBOARD.name) {
                    DashboardScreen(
                        isSyncing = isSyncing,
                        isOffline = isOffline,
                        hasPendingWrites = hasPendingWrites,
                        householdIcon = household?.icon,
                        householdName = household?.name,
                        onNavigateToSettings = onNavigateToSettings
                    )
                }
                HomeTab.LIST -> tabStateHolder.SaveableStateProvider(HomeTab.LIST.name) {
                    ListTabContent(
                        onNavigateToItemDetail = onNavigateToItemDetail,
                        isSyncing = isSyncing,
                        isOffline = isOffline,
                        hasPendingWrites = hasPendingWrites,
                        householdIcon = household?.icon,
                        householdName = household?.name,
                        onNavigateToSettings = onNavigateToSettings
                    )
                }
                HomeTab.CATALOG -> tabStateHolder.SaveableStateProvider(HomeTab.CATALOG.name) {
                    CatalogScreen(
                        onNavigateToItemDetail = onNavigateToItemDetail,
                        isSyncing = isSyncing,
                        isOffline = isOffline,
                        hasPendingWrites = hasPendingWrites,
                        householdIcon = household?.icon,
                        householdName = household?.name,
                        onNavigateToSettings = onNavigateToSettings
                    )
                }
            }
        }
    }
}
