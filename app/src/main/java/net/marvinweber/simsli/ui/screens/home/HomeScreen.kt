package net.marvinweber.simsli.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import net.marvinweber.simsli.ui.screens.catalog.CatalogScreen
import net.marvinweber.simsli.ui.screens.list.ListTabContent

enum class HomeTab(val label: String) {
    LIST("List"),
    CATALOG("Catalog")
}

/**
 * Main screen: bottom tabs for the shopping list and the item catalog.
 * Settings / stores / item detail open as full-screen routes above it.
 * A thin progress bar at the top shows when a sync run is in progress.
 */
@Composable
fun HomeScreen(
    onNavigateToStores: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToItemDetail: (String?) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    var selectedTabName by rememberSaveable { mutableStateOf(HomeTab.LIST.name) }
    val selectedTab = HomeTab.entries.firstOrNull { it.name == selectedTabName } ?: HomeTab.LIST
    val isSyncing by viewModel.isSyncing.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = { selectedTabName = tab.name },
                        icon = {
                            Icon(
                                imageVector = if (tab == HomeTab.LIST) Icons.Default.ShoppingCart else Icons.Default.Inventory2,
                                contentDescription = null
                            )
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedVisibility(
                visible = isSyncing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    HomeTab.LIST -> ListTabContent(
                        onNavigateToSettings = onNavigateToSettings
                    )
                    HomeTab.CATALOG -> CatalogScreen(
                        onNavigateToStores = onNavigateToStores,
                        onNavigateToItemDetail = onNavigateToItemDetail
                    )
                }
            }
        }
    }
}
