package net.marvinweber.simsli.ui.screens.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.marvinweber.simsli.domain.model.Store
import net.marvinweber.simsli.ui.components.EntryDetailsSheet
import net.marvinweber.simsli.ui.components.formatQuantity

/** Catalog suggestions shown per query in the quick-add sheet. */
private const val MAX_SUGGESTIONS = 5

/**
 * The shopping list tab: active entries on top, "Recently checked" below.
 * Entries checked off stay here (undo-able) until garbage collection removes
 * them everywhere after the TTL.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ListTabContent(
    onNavigateToItemDetail: (String?) -> Unit,
    viewModel: ListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedStoreId by viewModel.selectedStoreId.collectAsState()
    val events by viewModel.events.collectAsState()

    events?.let { event ->
        when (event) {
            is ListUiEvent.ShowError -> {
                // TODO: Show error snackbar
                viewModel.onEventConsumed()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Simsli") }
            )
        },
        bottomBar = {
            StoreFilterBar(
                stores = uiState.stores,
                selectedStoreId = selectedStoreId,
                onStoreSelected = viewModel::selectStore,
                onAddClick = viewModel::onAddClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                LoadingView()
            } else if (uiState.activeEntries.isEmpty() && uiState.recentlyChecked.isEmpty()) {
                EmptyListView()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.activeEntries, key = { it.listEntry.id }) { entryItem ->
                        ListEntryCard(
                            listEntryItem = entryItem,
                            onToggleDone = { viewModel.onToggleItemDone(entryItem.listEntry.id) },
                            onClick = { viewModel.onEntryClick(entryItem.listEntry.id) }
                        )
                    }

                    if (uiState.recentlyChecked.isNotEmpty()) {
                        item(key = "recently_checked_header") {
                            Text(
                                text = "Recently checked",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        items(uiState.recentlyChecked, key = { it.listEntry.id }) { entryItem ->
                            ListEntryCard(
                                listEntryItem = entryItem,
                                onToggleDone = { viewModel.onToggleItemDone(entryItem.listEntry.id) },
                                onClick = { viewModel.onEntryClick(entryItem.listEntry.id) }
                            )
                        }
                    }
                }
            }
        }

        if (uiState.isAddSheetOpen) {
            AddItemsSheet(
                uiState = uiState,
                onQueryChange = viewModel::onAddQueryChange,
                onSuggestionClick = viewModel::onSuggestionClick,
                onSelectNew = viewModel::onSelectNew,
                onBackToSearch = viewModel::backToSearch,
                onAdd = { saveToCatalog, quantity, unit, comment, closeAfter ->
                    viewModel.addSelected(saveToCatalog, quantity, unit, comment, closeAfter)
                },
                onDismiss = viewModel::dismissAddSheet
            )
        }

        uiState.editingEntry?.let { entryItem ->
            EntryDetailsSheet(
                title = entryItem.item?.name ?: "Unknown item",
                stateKey = entryItem.listEntry.id,
                quantity = entryItem.listEntry.quantity,
                unit = entryItem.listEntry.unit,
                comment = entryItem.listEntry.comment,
                onSave = { quantity, unit, comment ->
                    viewModel.saveEntryDetails(entryItem.listEntry.id, quantity, unit, comment)
                },
                onRemove = { viewModel.removeEntry(entryItem.listEntry.id) },
                onDismiss = viewModel::dismissEntryEditor
            )
        }
    }
}

/**
 * The store filter line pinned above the navigation bar: horizontally
 * scrollable chips that run against a divider, with the add FAB to the
 * right of it — one continuous bottom area.
 */
@Composable
private fun StoreFilterBar(
    stores: List<Store>,
    selectedStoreId: String?,
    onStoreSelected: (String?) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        // IntrinsicSize.Min: the VerticalDivider wants all available height, so
        // the Row's height must come from its tallest child (the FAB) instead.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StoreFilterChip(
                    text = "All",
                    selected = selectedStoreId == null,
                    onClick = { onStoreSelected(null) }
                )

                stores.forEach { store ->
                    StoreFilterChip(
                        text = store.name,
                        selected = selectedStoreId == store.id,
                        onClick = { onStoreSelected(store.id) }
                    )
                }
            }

            VerticalDivider()

            FloatingActionButton(
                onClick = onAddClick,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add items")
            }
        }
    }
}

@Composable
private fun StoreFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            null
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator()
    }
}

@Composable
private fun EmptyListView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Your list is empty",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Tap + to add items from your catalog",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ListEntryCard(
    listEntryItem: ListEntryItem,
    onToggleDone: () -> Unit,
    onClick: () -> Unit
) {
    val listEntry = listEntryItem.listEntry
    val item = listEntryItem.item

    val displayName = item?.name ?: "Unknown item"

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
            Checkbox(
                checked = listEntry.done,
                onCheckedChange = { onToggleDone() }
            )

            Spacer(modifier = Modifier.size(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                // "1 Stk Käse" on one line: the quantity/unit prefix in the
                // secondary color and a size smaller, flowing inline so the
                // row height adapts to the actual line count.
                val meta = buildString {
                    listEntry.quantity?.let { append(formatQuantity(it)) }
                    listEntry.unit?.let {
                        if (isNotEmpty()) append(' ')
                        append(it)
                    }
                }
                Text(
                    text = buildAnnotatedString {
                        if (meta.isNotEmpty()) {
                            withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                                )
                            ) {
                                append(meta)
                                append(' ')
                            }
                        }
                        append(displayName)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (listEntry.done)
                        TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!listEntry.comment.isNullOrBlank()) {
                    Text(
                        text = listEntry.comment,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Quick-add flow (LIST-2): search state with up to [MAX_SUGGESTIONS] catalog
 * suggestions plus a permanent NEW row; selecting either moves to a compact
 * form with Add / Add & Close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemsSheet(
    uiState: ListUiState,
    onQueryChange: (String) -> Unit,
    onSuggestionClick: (CatalogItemUi) -> Unit,
    onSelectNew: () -> Unit,
    onBackToSearch: () -> Unit,
    onAdd: (
        saveToCatalog: Boolean,
        quantity: String,
        unit: String,
        comment: String,
        closeAfter: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val selection = uiState.addSelection
        if (selection == null) {
            AddItemsSearchContent(
                uiState = uiState,
                onQueryChange = onQueryChange,
                onSuggestionClick = onSuggestionClick,
                onSelectNew = onSelectNew
            )
        } else {
            AddItemsSelectedContent(
                selection = selection,
                isAddInProgress = uiState.isAddInProgress,
                onBackToSearch = onBackToSearch,
                onAdd = onAdd
            )
        }
    }
}

@Composable
private fun AddItemsSearchContent(
    uiState: ListUiState,
    onQueryChange: (String) -> Unit,
    onSuggestionClick: (CatalogItemUi) -> Unit,
    onSelectNew: () -> Unit
) {
    // Keystrokes land in local state first: routing the field value through the
    // ViewModel round-trip (combine chain) lags a frame, so fast typing applied
    // against the stale value jumps the cursor backwards. The ViewModel copy is
    // kept in sync only for its reset paths (after "Add", on suggestion→editor).
    // The search content re-initializes from it whenever it (re-)enters
    // composition — backToSearch keeps the query, "Add" clears it.
    var queryText by remember { mutableStateOf(uiState.addQuery) }
    val query = queryText.trim()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Runs on open, after "Add" resets back here, and on back-to-search —
    // the search content re-enters composition each time.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
    ) {
        item(key = "search") {
            OutlinedTextField(
                value = queryText,
                onValueChange = { queryText = it; onQueryChange(it) },
                label = { Text("Search or add item") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }

        if (query.isNotEmpty()) {
            item(key = "create_new") {
                ListItem(
                    onClick = {
                        keyboard?.hide()
                        onSelectNew()
                    },
                    leadingContent = { NewItemBadge() },
                    supportingContent = { Text("New — not in catalog") }
                ) {
                    Text("\"$query\"")
                }
            }

            val suggestions = uiState.catalog
                .filter { it.item.name.contains(query, ignoreCase = true) }
                .sortedWith(
                    compareBy(
                        { !it.item.name.startsWith(query, ignoreCase = true) },
                        { it.item.name.lowercase() }
                    )
                )
                .take(MAX_SUGGESTIONS)

            items(suggestions, key = { it.item.id }) { catalogItem ->
                ListItem(
                    onClick = {
                        keyboard?.hide()
                        onSuggestionClick(catalogItem)
                    },
                    trailingContent = {
                        if (catalogItem.isOnActiveList) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "On list",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                ) {
                    Text(
                        text = catalogItem.item.name,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AddItemsSelectedContent(
    selection: AddSelection,
    isAddInProgress: Boolean,
    onBackToSearch: () -> Unit,
    onAdd: (
        saveToCatalog: Boolean,
        quantity: String,
        unit: String,
        comment: String,
        closeAfter: Boolean
    ) -> Unit
) {
    var quantityText by remember { mutableStateOf("") }
    var unitText by remember { mutableStateOf("") }
    var commentText by remember { mutableStateOf("") }
    var saveToCatalog by remember { mutableStateOf(false) }
    var showTypeHelp by remember { mutableStateOf(false) }

    val name = when (selection) {
        is AddSelection.Existing -> selection.item.name
        is AddSelection.New -> selection.name
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ListItem(
            onClick = onBackToSearch,
            leadingContent = if (selection is AddSelection.New) {
                { NewItemBadge() }
            } else {
                null
            },
            supportingContent = if (selection is AddSelection.New) {
                { Text("New — not in catalog") }
            } else {
                null
            },
            trailingContent = {
                IconButton(onClick = onBackToSearch) {
                    Icon(Icons.Default.Close, contentDescription = "Back to search")
                }
            }
        ) {
            Text(
                text = name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = quantityText,
                onValueChange = { quantityText = it },
                label = { Text("Quantity") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = unitText,
                onValueChange = { unitText = it },
                label = { Text("Unit") },
                placeholder = { Text("kg, pack …") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = commentText,
            onValueChange = { commentText = it },
            label = { Text("Comment (optional)") },
            placeholder = { Text("e.g., get the organic one") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        if (selection is AddSelection.New) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = saveToCatalog,
                    onCheckedChange = { saveToCatalog = it }
                )
                Text(
                    text = "Save \"$name\" to Catalog",
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showTypeHelp = !showTypeHelp }) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Explain this option"
                    )
                }
            }
            AnimatedVisibility(visible = showTypeHelp) {
                Text(
                    text = if (saveToCatalog) {
                        "The item stays in your catalog — it shows up in search next time."
                    } else {
                        "The item is only on the list for this trip: after you check it off, " +
                            "it is removed with the daily cleanup (24 h) — right for one-time things."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = { onAdd(saveToCatalog, quantityText, unitText, commentText, false) },
                enabled = !isAddInProgress,
                modifier = Modifier.weight(1f)
            ) {
                Text("Add")
            }
            Button(
                onClick = { onAdd(saveToCatalog, quantityText, unitText, commentText, true) },
                enabled = !isAddInProgress,
                modifier = Modifier.weight(1f)
            ) {
                Text("Add & Close")
            }
        }
    }
}

/** Leading badge marking the NEW row: an Add icon in a tonal circle, distinct from catalog hits. */
@Composable
private fun NewItemBadge() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .padding(6.dp)
                .size(18.dp)
        )
    }
}
