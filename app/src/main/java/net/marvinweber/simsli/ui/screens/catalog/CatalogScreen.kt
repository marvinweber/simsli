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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.ui.graphics.vector.ImageVector
import net.marvinweber.simsli.domain.model.ItemType
import net.marvinweber.simsli.ui.components.CategorySectionHeader
import net.marvinweber.simsli.ui.components.LinkUtils
import net.marvinweber.simsli.ui.components.SimsliSearchTopAppBar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import net.marvinweber.simsli.ui.screens.categories.CategoriesTabContent
import net.marvinweber.simsli.ui.screens.stores.StoresTabContent

enum class CatalogTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    ITEMS("Items", Icons.Filled.ShoppingBag, Icons.Outlined.ShoppingBag),
    CATEGORIES("Categories", Icons.Filled.Category, Icons.Outlined.Category),
    STORES("Stores", Icons.Filled.Storefront, Icons.Outlined.Storefront)
}

/**
 * The Catalog tab: tabbed view of Items | Categories | Stores. Every item of
 * the household lives here independent of whether it is currently on the
 * list. Tap to edit, + to add to the list, FAB to create a new item.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CatalogScreen(
    onNavigateToItemDetail: (String?) -> Unit,
    isSyncing: Boolean = false,
    householdIcon: String? = null,
    householdName: String? = null,
    onNavigateToSettings: () -> Unit = {},
    viewModel: CatalogViewModel = hiltViewModel()
) {
    var selectedTabName by rememberSaveable { mutableStateOf(CatalogTab.ITEMS.name) }
    val selectedTab = CatalogTab.entries.firstOrNull { it.name == selectedTabName } ?: CatalogTab.ITEMS
    val tabStateHolder = rememberSaveableStateHolder()

    // Top-bar search state (SEARCH-1): one open/closed flag, one query per sub-tab —
    // all saveable so they survive bottom-tab switches.
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var itemsQuery by rememberSaveable { mutableStateOf("") }
    var categoriesQuery by rememberSaveable { mutableStateOf("") }
    var storesQuery by rememberSaveable { mutableStateOf("") }
    val activeQuery = when (selectedTab) {
        CatalogTab.ITEMS -> itemsQuery
        CatalogTab.CATEGORIES -> categoriesQuery
        CatalogTab.STORES -> storesQuery
    }

    fun closeSearch() {
        isSearchActive = false
        itemsQuery = ""
        categoriesQuery = ""
        storesQuery = ""
        viewModel.setSearchQuery("")
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SimsliSearchTopAppBar(
                title = "Catalog",
                isSyncing = isSyncing,
                isSearchActive = isSearchActive,
                query = activeQuery,
                onQueryChange = { query ->
                    when (selectedTab) {
                        CatalogTab.ITEMS -> itemsQuery = query
                        CatalogTab.CATEGORIES -> categoriesQuery = query
                        CatalogTab.STORES -> storesQuery = query
                    }
                },
                onSearchClick = { isSearchActive = true },
                onCloseSearch = { closeSearch() },
                searchPlaceholder = "Search catalog",
                householdIcon = householdIcon,
                householdName = householdName,
                onHouseholdClick = onNavigateToSettings
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
                    val isSelected = tab == selectedTab
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTabName = tab.name },
                        text = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = null
                            )
                        }
                    )
                }
            }

            val pullToRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isSyncing,
                onRefresh = viewModel::refresh,
                state = pullToRefreshState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullToRefreshState,
                        isRefreshing = isSyncing,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            ) {
                when (selectedTab) {
                    CatalogTab.ITEMS -> tabStateHolder.SaveableStateProvider(CatalogTab.ITEMS.name) {
                        ItemsTabContent(
                            onNavigateToItemDetail = onNavigateToItemDetail,
                            searchQuery = itemsQuery,
                            onSearchQueryChange = { itemsQuery = it },
                            viewModel = viewModel
                        )
                    }
                    CatalogTab.CATEGORIES -> tabStateHolder.SaveableStateProvider(CatalogTab.CATEGORIES.name) {
                        CategoriesTabContent(
                            searchQuery = categoriesQuery,
                            onSearchQueryChange = { categoriesQuery = it }
                        )
                    }
                    CatalogTab.STORES -> tabStateHolder.SaveableStateProvider(CatalogTab.STORES.name) {
                        StoresTabContent(
                            searchQuery = storesQuery,
                            onSearchQueryChange = { storesQuery = it }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ItemsTabContent(
    onNavigateToItemDetail: (String?) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    viewModel: CatalogViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    // Re-sync the ViewModel whenever this sub-tab enters composition so a restored
    // query (e.g. after switching bottom tabs) filters again.
    LaunchedEffect(searchQuery) { viewModel.setSearchQuery(searchQuery) }

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
                unit = row.item.defaultUnit,
                comment = null,
                note = row.item.notes,
                links = row.item.links,
                saveButtonText = "Add to list",
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
            uiState.totalItemCount == 0 -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState()),
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
            uiState.groups.isEmpty() && uiState.searchQuery.isNotBlank() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No results",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Nothing matches “${uiState.searchQuery}”",
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
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    uiState.groups.forEach { group ->
                        item(key = "catalog_group_${group.key}") {
                            CategorySectionHeader(
                                title = group.title,
                                emoji = group.emoji,
                                isImplicit = group.isImplicit,
                                modifier = Modifier.animateItem()
                            )
                        }
                        itemsIndexed(group.items, key = { _, it -> it.item.id }) { index, row ->
                            Column(modifier = Modifier.animateItem()) {
                                CatalogItemRow(
                                    row = row,
                                    onClick = { onNavigateToItemDetail(row.item.id) },
                                    onAddToList = { viewModel.onAddToListClick(row.item.id) }
                                )
                                if (index < group.items.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogItemRow(
    row: CatalogItemRow,
    onClick: () -> Unit,
    onAddToList: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = row.item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (row.item.type == ItemType.ONE_TIME) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = "One-time",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (!row.item.notes.isNullOrBlank()) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = "Has note",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    val hasLinks = row.item.links.isNotEmpty() || LinkUtils.extractUrls(row.item.notes).isNotEmpty()
                    if (hasLinks) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Has link",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                if (row.isOnActiveList) {
                    Text(
                        text = "On list",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (!row.item.defaultUnit.isNullOrBlank()) {
                    Text(
                        text = "Default: ${row.item.defaultUnit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            if (row.isOnActiveList) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "On list",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onAddToList,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add to list",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
