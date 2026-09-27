package net.marvinweber.simsli.ui.screens.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.marvinweber.simsli.ui.components.EntryDetailsSheet
import net.marvinweber.simsli.ui.screens.stores.StoresTabContent

enum class CatalogTab(val label: String) {
    ITEMS("Items"),
    CATEGORIES("Categories"),
    STORES("Stores")
}

/**
 * The Catalog tab: tabbed view of Items | Categories | Stores. Every item of
 * the household lives here independent of whether it is currently on the
 * list. Tap to edit, + to add to the list, FAB to create a new item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    onNavigateToItemDetail: (String?) -> Unit,
    viewModel: CatalogViewModel = hiltViewModel()
) {
    var selectedTabName by rememberSaveable { mutableStateOf(CatalogTab.ITEMS.name) }
    val selectedTab = CatalogTab.entries.firstOrNull { it.name == selectedTabName } ?: CatalogTab.ITEMS
    val tabStateHolder = rememberSaveableStateHolder()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Catalog") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                CatalogTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == selectedTab,
                        onClick = { selectedTabName = tab.name },
                        text = { Text(tab.label) }
                    )
                }
            }

            when (selectedTab) {
                CatalogTab.ITEMS -> tabStateHolder.SaveableStateProvider(CatalogTab.ITEMS.name) {
                    ItemsTabContent(
                        onNavigateToItemDetail = onNavigateToItemDetail,
                        viewModel = viewModel
                    )
                }
                CatalogTab.CATEGORIES -> tabStateHolder.SaveableStateProvider(CatalogTab.CATEGORIES.name) {
                    CategoriesPlaceholder()
                }
                CatalogTab.STORES -> tabStateHolder.SaveableStateProvider(CatalogTab.STORES.name) {
                    StoresTabContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ItemsTabContent(
    onNavigateToItemDetail: (String?) -> Unit,
    viewModel: CatalogViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToItemDetail(null) }) {
                Icon(Icons.Default.Add, contentDescription = "New item")
            }
        }
    ) { paddingValues ->
        uiState.addingItem?.let { row ->
            EntryDetailsSheet(
                title = row.item.name,
                stateKey = row.item.id,
                quantity = null,
                unit = null,
                comment = null,
                onSave = { quantity, unit, comment ->
                    viewModel.confirmAddToList(row.item.id, quantity, unit, comment)
                },
                onDismiss = viewModel::dismissAddSheet
            )
        }

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
            uiState.items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No items yet",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Tap + to create your first item",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(uiState.items, key = { it.item.id }) { row ->
                        CatalogItemCard(
                            row = row,
                            onClick = { onNavigateToItemDetail(row.item.id) },
                            onAddToList = { viewModel.onAddToListClick(row.item.id) }
                        )
                    }
                }
            }
        }
    }
}

/** Until the category model (CAT-1..4) lands, the tab shows an honest placeholder. */
@Composable
private fun CategoriesPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Categories — coming soon",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CatalogItemCard(
    row: CatalogItemRow,
    onClick: () -> Unit,
    onAddToList: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (row.isOnActiveList) {
                    Text(
                        text = "On list",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (row.isOnActiveList) {
                // Same 48 dp footprint as the IconButton next to it, so the two
                // states line up horizontally.
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "On list",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                IconButton(onClick = onAddToList) {
                    Icon(Icons.Default.Add, contentDescription = "Add to list")
                }
            }
        }
    }
}
