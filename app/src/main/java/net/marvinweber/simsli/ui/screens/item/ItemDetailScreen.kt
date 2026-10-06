package net.marvinweber.simsli.ui.screens.item

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.marvinweber.simsli.domain.model.ItemType
import net.marvinweber.simsli.ui.components.ItemLinkChipsRow
import net.marvinweber.simsli.ui.components.LinkUtils

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
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var prefilled by rememberSaveable { mutableStateOf(false) }
    var showAddLinkDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var duplicateWarningName by rememberSaveable { mutableStateOf<String?>(null) }
    var duplicateWarningIsSimilar by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Prefill the form once when editing an existing item.
    LaunchedEffect(uiState.existingItem) {
        val item = uiState.existingItem ?: return@LaunchedEffect
        if (prefilled) return@LaunchedEffect
        val notes = item.notes.orEmpty()
        val stripResult = LinkUtils.stripUrls(notes)
        itemName = item.name
        itemNotes = stripResult.remainingText
        itemType = item.type
        selectedStoreIds = uiState.existingStoreIds
        selectedCategoryId = item.categoryId
        stripResult.extractedUrls.forEach { url ->
            viewModel.addLink(url)
        }
        prefilled = true
    }

    events?.let { event ->
        when (event) {
            is ItemDetailUiEvent.NavigateBack -> {
                onNavigateBack()
                viewModel.onEventConsumed()
            }
            is ItemDetailUiEvent.ShowError -> {
                LaunchedEffect(event) {
                    snackbarHostState.showSnackbar(event.message)
                    viewModel.onEventConsumed()
                }
            }
            is ItemDetailUiEvent.ShowDuplicateWarning -> {
                duplicateWarningName = event.matchedName
                duplicateWarningIsSimilar = event.matchType == net.marvinweber.simsli.domain.model.ItemMatchType.SIMILAR
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
                },
                actions = {
                    if (uiState.existingItem != null) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            enabled = !uiState.isSaving
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete item",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

                        // Category assignment (single-select, optional)
                        if (uiState.categories.isNotEmpty()) {
                            Text(
                                text = "Category (optional)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                uiState.categories.forEach { category ->
                                    val isSelected = selectedCategoryId == category.id
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedCategoryId = if (isSelected) null else category.id
                                        },
                                        label = {
                                            val labelText = if (!category.emoji.isNullOrBlank()) {
                                                "${category.emoji} ${category.name}"
                                            } else {
                                                category.name
                                            }
                                            Text(labelText)
                                        }
                                    )
                                }
                            }
                        }

                        // Store assignment (multi-select)
                        if (uiState.stores.isNotEmpty()) {
                            Text(
                                text = "Stores (optional)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            onValueChange = { newText ->
                                val stripResult = LinkUtils.stripUrls(newText)
                                itemNotes = stripResult.remainingText
                                if (stripResult.extractedUrls.isNotEmpty()) {
                                    stripResult.extractedUrls.forEach { url ->
                                        viewModel.addLink(url)
                                    }
                                }
                            },
                            label = { Text("Notes (optional)") },
                            placeholder = { Text("Any additional details") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )

                        // Links section
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Links",
                                style = MaterialTheme.typography.titleSmall
                            )
                            TextButton(
                                onClick = { showAddLinkDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("Add link", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        if (uiState.links.isNotEmpty()) {
                            ItemLinkChipsRow(
                                links = uiState.links,
                                onRemove = { link -> viewModel.removeLink(link.url) }
                            )
                        } else {
                            Text(
                                text = "Paste a link into notes or tap \"Add link\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        if (showAddLinkDialog) {
                            var linkInput by remember { mutableStateOf("") }
                            AlertDialog(
                                onDismissRequest = { showAddLinkDialog = false },
                                title = { Text("Add link") },
                                text = {
                                    OutlinedTextField(
                                        value = linkInput,
                                        onValueChange = { linkInput = it },
                                        label = { Text("URL") },
                                        placeholder = { Text("https://example.com") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            val trimmed = linkInput.trim()
                                            if (trimmed.isNotBlank()) {
                                                viewModel.addLink(trimmed)
                                            }
                                            showAddLinkDialog = false
                                        },
                                        enabled = linkInput.isNotBlank()
                                    ) {
                                        Text("Add")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showAddLinkDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Save button
                        Button(
                            onClick = {
                                viewModel.saveItem(
                                    name = itemName,
                                    notes = itemNotes,
                                    type = itemType,
                                    selectedStoreIds = selectedStoreIds.toList(),
                                    selectedCategoryId = selectedCategoryId
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = itemName.isNotBlank() && !uiState.isSaving
                        ) {
                            Text(if (uiState.existingItem == null) "Add item" else "Save")
                        }

                        // Delete button (existing items only)
                        if (uiState.existingItem != null) {
                            OutlinedButton(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                enabled = !uiState.isSaving
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Delete item")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        DeleteItemDialog(
            itemName = itemName,
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteItem()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    duplicateWarningName?.let { name ->
        DuplicateItemWarningDialog(
            matchedName = name,
            isSimilar = duplicateWarningIsSimilar,
            isEditing = uiState.existingItem != null,
            onConfirm = {
                duplicateWarningName = null
                duplicateWarningIsSimilar = false
                viewModel.saveItem(
                    name = itemName,
                    notes = itemNotes,
                    type = itemType,
                    selectedStoreIds = selectedStoreIds.toList(),
                    selectedCategoryId = selectedCategoryId,
                    force = true
                )
            },
            onDismiss = {
                duplicateWarningName = null
                duplicateWarningIsSimilar = false
            }
        )
    }
}

@Composable
private fun DuplicateItemWarningDialog(
    matchedName: String,
    isSimilar: Boolean,
    isEditing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isSimilar) "Similar item found" else "Item already exists")
        },
        text = {
            Text(
                if (isSimilar) {
                    if (isEditing) {
                        "A similar item named \"$matchedName\" already exists in your catalog. Save anyway?"
                    } else {
                        "A similar item named \"$matchedName\" already exists in your catalog. Add anyway?"
                    }
                } else {
                    if (isEditing) {
                        "An item named \"$matchedName\" already exists in your catalog. Save anyway?"
                    } else {
                        "An item named \"$matchedName\" already exists in your catalog. Add anyway?"
                    }
                }
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(if (isEditing) "Save anyway" else "Add anyway")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeleteItemDialog(
    itemName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Item?") },
        text = {
            Text("Delete \"$itemName\"? It will be removed from your catalog and shopping list.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

