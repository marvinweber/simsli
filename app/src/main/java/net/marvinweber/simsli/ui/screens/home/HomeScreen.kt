package net.marvinweber.simsli.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import net.marvinweber.simsli.ui.screens.catalog.CatalogScreen
import net.marvinweber.simsli.ui.screens.list.ListTabContent
import net.marvinweber.simsli.ui.screens.settings.SettingsTabContent

enum class HomeTab(val label: String) {
    LIST("Shopping List"),
    CATALOG("Catalog"),
    SETTINGS("Settings")
}

/**
 * Main screen: bottom tabs for the shopping list, the catalog
 * (Items | Categories | Stores), and settings. Item detail opens as a
 * full-screen route above it. A thin progress bar at the top shows when a
 * sync run is in progress.
 */
@Composable
fun HomeScreen(
    onNavigateToItemDetail: (String?) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    var selectedTabName by rememberSaveable { mutableStateOf(HomeTab.LIST.name) }
    val selectedTab = HomeTab.entries.firstOrNull { it.name == selectedTabName } ?: HomeTab.LIST
    val isSyncing by viewModel.isSyncing.collectAsState()

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
        // Tab scaffolds own their insets: the top-most app bar per column handles
        // the status bar, the NavigationBar here handles the nav bar. Nothing
        // double-applies.
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
                                    HomeTab.LIST -> Icons.Default.ShoppingCart
                                    HomeTab.CATALOG -> Icons.Default.Inventory2
                                    HomeTab.SETTINGS -> Icons.Default.Settings
                                },
                                contentDescription = null
                            )
                        },
                        label = { Text(tab.label) }
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
                HomeTab.LIST -> tabStateHolder.SaveableStateProvider(HomeTab.LIST.name) {
                    ListTabContent(
                        onNavigateToItemDetail = onNavigateToItemDetail,
                        isSyncing = isSyncing
                    )
                }
                HomeTab.CATALOG -> tabStateHolder.SaveableStateProvider(HomeTab.CATALOG.name) {
                    CatalogScreen(
                        onNavigateToItemDetail = onNavigateToItemDetail,
                        isSyncing = isSyncing
                    )
                }
                HomeTab.SETTINGS -> tabStateHolder.SaveableStateProvider(HomeTab.SETTINGS.name) {
                    SettingsTabContent(
                        isSyncing = isSyncing
                    )
                }
            }
        }
    }
}
