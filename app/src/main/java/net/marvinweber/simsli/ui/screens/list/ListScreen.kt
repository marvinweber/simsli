package net.marvinweber.simsli.ui.screens.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.marvinweber.simsli.domain.model.Store

/**
 * The shopping list tab: active entries on top, "Recently checked" below.
 * Entries checked off stay here (undo-able) until garbage collection removes
 * them everywhere after the TTL.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListTabContent(
    onNavigateToSettings: () -> Unit,
    viewModel: ListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedStoreId by viewModel.selectedStoreId.collectAsState()
    val events by viewModel.events.collectAsState()

    events?.let { event ->
        when (event) {
            is ListUiEvent.NavigateToSettings -> {
                onNavigateToSettings()
                viewModel.onEventConsumed()
            }
            is ListUiEvent.ShowError -> {
                // TODO: Show error snackbar
                viewModel.onEventConsumed()
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Simsli") },
                navigationIcon = {
                    IconButton(onClick = viewModel::onSettingsClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "Add items")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            StoreFilterRow(
                stores = uiState.stores,
                selectedStoreId = selectedStoreId,
                onStoreSelected = viewModel::selectStore
            )

            Spacer(modifier = Modifier.size(16.dp))

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
                onAddExisting = viewModel::addCatalogItemToList,
                onCreateAndAdd = viewModel::createAndAddItem,
                onDismiss = viewModel::dismissAddSheet
            )
        }

        uiState.editingEntry?.let { entryItem ->
            EntryEditorSheet(
                entryItem = entryItem,
                onSave = { quantity, unit, comment ->
                    viewModel.saveEntryDetails(entryItem.listEntry.id, quantity, unit, comment)
                },
                onRemove = { viewModel.removeEntry(entryItem.listEntry.id) },
                onDismiss = viewModel::dismissEntryEditor
            )
        }
    }
}

@Composable
private fun StoreFilterRow(
    stores: List<Store>,
    selectedStoreId: String?,
    onStoreSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(
            onClick = { onStoreSelected(null) },
            label = { Text("All") },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (selectedStoreId == null)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface
            )
        )

        stores.forEach { store ->
            AssistChip(
                onClick = { onStoreSelected(store.id) },
                label = { Text(store.name) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (selectedStoreId == store.id)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface
                )
            )
        }
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Loading...")
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
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (listEntry.done)
                        TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (listEntry.quantity != null || listEntry.unit != null) {
                    Text(
                        text = buildString {
                            listEntry.quantity?.let { append(it) }
                            listEntry.unit?.let {
                                if (listEntry.quantity != null) append(" ")
                                append(it)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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

/** Search the catalog and add items — or create a new one from the query inline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemsSheet(
    uiState: ListUiState,
    onQueryChange: (String) -> Unit,
    onAddExisting: (String) -> Unit,
    onCreateAndAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    val query = uiState.addQuery.trim()
    val matches = uiState.catalog.filter {
        query.isEmpty() || it.item.name.contains(query, ignoreCase = true)
    }
    val exactMatchExists = matches.any { it.item.name.equals(query, ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = uiState.addQuery,
                onValueChange = onQueryChange,
                label = { Text("Search or add item") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.size(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(matches, key = { it.item.id }) { catalogItem ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = catalogItem.item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (catalogItem.isOnActiveList) {
                            Text(
                                text = "On list",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            TextButton(onClick = { onAddExisting(catalogItem.item.id) }) {
                                Text("Add")
                            }
                        }
                    }
                }

                if (query.isNotEmpty() && !exactMatchExists) {
                    item(key = "create_new") {
                        OutlinedButton(
                            onClick = onCreateAndAdd,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add \"$query\"")
                        }
                    }
                }
            }
        }
    }
}

/** Edit the entry-level data (quantity, unit, comment) or remove the item from the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryEditorSheet(
    entryItem: ListEntryItem,
    onSave: (quantity: String, unit: String, comment: String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val entry = entryItem.listEntry

    fun quantityToText(quantity: Double?): String = when {
        quantity == null -> ""
        quantity % 1.0 == 0.0 -> quantity.toInt().toString()
        else -> quantity.toString()
    }

    var quantityText by remember(entry.id) { mutableStateOf(quantityToText(entry.quantity)) }
    var unitText by remember(entry.id) { mutableStateOf(entry.unit.orEmpty()) }
    var commentText by remember(entry.id) { mutableStateOf(entry.comment.orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = entryItem.item?.name ?: "Unknown item",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Quantity") },
                    singleLine = true,
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
                minLines = 2,
                maxLines = 4
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onRemove) {
                    Text("Remove from list")
                }
                OutlinedButton(
                    onClick = { onSave(quantityText, unitText, commentText) }
                ) {
                    Text("Save")
                }
            }
        }
    }
}
