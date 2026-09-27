package net.marvinweber.simsli.ui.screens.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.marvinweber.simsli.domain.model.ItemType
import androidx.compose.ui.unit.Dp

/**
 * Create/edit a catalog item: name, store assignments, type, notes.
 * Entry-level data (quantity, unit, comment) is entered on the list, not here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ItemDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val events by viewModel.events.collectAsState()

    var itemName by rememberSaveable { mutableStateOf("") }
    var itemNotes by rememberSaveable { mutableStateOf("") }
    var itemType by rememberSaveable { mutableStateOf<ItemType>(ItemType.PERMANENT) }
    var selectedStoreIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var prefilled by rememberSaveable { mutableStateOf(false) }

    // Prefill the form once when editing an existing item.
    LaunchedEffect(uiState.existingItem) {
        val item = uiState.existingItem ?: return@LaunchedEffect
        if (prefilled) return@LaunchedEffect
        itemName = item.name
        itemNotes = item.notes.orEmpty()
        itemType = item.type
        selectedStoreIds = uiState.existingStoreIds
        prefilled = true
    }

    events?.let { event ->
        when (event) {
            is ItemDetailUiEvent.NavigateBack -> {
                onNavigateBack()
                viewModel.onEventConsumed()
            }
            is ItemDetailUiEvent.ShowError -> {
                // TODO: Show error snackbar
                viewModel.onEventConsumed()
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (uiState.existingItem == null) "Add Item" else "Edit Item") },
                navigationIcon = {
                    IconButton(onClick = viewModel::onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier.imePadding()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth()
        ) {
            when {
                uiState.isLoading || uiState.isWaitingForItem -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Item name (required)
                        OutlinedTextField(
                            value = itemName,
                            onValueChange = { itemName = it },
                            label = { Text("Item name *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Store assignment (multi-select)
                        if (uiState.stores.isNotEmpty()) {
                            Text(
                                text = "Stores (optional)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            FlowRow(
                                horizontalGap = 8.dp,
                                verticalGap = 8.dp
                            ) {
                                uiState.stores.forEach { store ->
                                    FilterChip(
                                        selected = selectedStoreIds.contains(store.id),
                                        onClick = {
                                            selectedStoreIds = if (selectedStoreIds.contains(store.id)) {
                                                selectedStoreIds - store.id
                                            } else {
                                                selectedStoreIds + store.id
                                            }
                                        },
                                        label = { Text(store.name) }
                                    )
                                }
                            }
                        }

                        // Item type
                        Text(
                            text = "Item type",
                            style = MaterialTheme.typography.titleSmall
                        )
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SegmentedButton(
                                selected = itemType == ItemType.PERMANENT,
                                onClick = { itemType = ItemType.PERMANENT },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text("Permanent")
                            }
                            SegmentedButton(
                                selected = itemType == ItemType.ONE_TIME,
                                onClick = { itemType = ItemType.ONE_TIME },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text("One-time")
                            }
                        }
                        Text(
                            text = when (itemType) {
                                ItemType.PERMANENT -> "Stays in the catalog — add it to the list again any time."
                                ItemType.ONE_TIME -> "Removed together with its checked-off entry after 24 h — for one-time things."
                                ItemType.CHECKLIST -> "Legacy type, treated as permanent."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Notes
                        OutlinedTextField(
                            value = itemNotes,
                            onValueChange = { itemNotes = it },
                            label = { Text("Notes (optional)") },
                            placeholder = { Text("Any additional details") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Save button
                        Button(
                            onClick = {
                                viewModel.saveItem(
                                    name = itemName,
                                    notes = itemNotes,
                                    type = itemType,
                                    selectedStoreIds = selectedStoreIds.toList()
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = itemName.isNotBlank()
                        ) {
                            Text(if (uiState.existingItem == null) "Add item" else "Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowRow(
    horizontalGap: Dp,
    verticalGap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(horizontalGap)
        ) {
            // Simple flow row implementation - all items in one row for now
            // A proper implementation would measure and wrap
            content()
        }
    }
}
