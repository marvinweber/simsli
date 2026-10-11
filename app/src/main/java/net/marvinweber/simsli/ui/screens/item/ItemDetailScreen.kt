package net.marvinweber.simsli.ui.screens.item

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.marvinweber.simsli.domain.model.ItemType
import net.marvinweber.simsli.domain.model.Store
import net.marvinweber.simsli.domain.model.StoreGroup
import net.marvinweber.simsli.domain.model.StoreSelection
import net.marvinweber.simsli.domain.model.UnitPresets
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
    var itemDefaultUnit by rememberSaveable { mutableStateOf("") }
    var itemType by rememberSaveable { mutableStateOf<ItemType>(ItemType.PERMANENT) }
    var selectedStoreIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var selectedGroupIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var excludedStoreIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var prefilled by rememberSaveable { mutableStateOf(false) }
    var showAddLinkDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showStoreSelectionDialog by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
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
        itemDefaultUnit = item.defaultUnit.orEmpty()
        itemType = item.type
        val groupStoreIds = uiState.stores
            .filter { it.groupId != null && it.groupId in uiState.existingSelection.groupIds }
            .map { it.id }
            .toSet()
        selectedStoreIds = uiState.existingSelection.memberStoreIds - groupStoreIds
        selectedGroupIds = uiState.existingSelection.groupIds
        excludedStoreIds = uiState.existingSelection.excludedStoreIds
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

                        // Category
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Box {
                            val selectedCategory = uiState.categories.find { it.id == selectedCategoryId }
                            val categoryLabel = if (selectedCategory != null) {
                                if (!selectedCategory.emoji.isNullOrBlank()) "${selectedCategory.emoji} ${selectedCategory.name}" else selectedCategory.name
                            } else {
                                "None (Uncategorized)"
                            }
                            FilterChip(
                                selected = selectedCategoryId != null,
                                onClick = { categoryMenuExpanded = true },
                                label = { Text(categoryLabel) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Category,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                            DropdownMenu(
                                expanded = categoryMenuExpanded,
                                onDismissRequest = { categoryMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("None (Uncategorized)") },
                                    onClick = {
                                        selectedCategoryId = null
                                        categoryMenuExpanded = false
                                    },
                                    leadingIcon = {
                                        if (selectedCategoryId == null) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                )
                                uiState.categories.forEach { category ->
                                    DropdownMenuItem(
                                        text = {
                                            val catText = if (!category.emoji.isNullOrBlank()) "${category.emoji} ${category.name}" else category.name
                                            Text(catText)
                                        },
                                        onClick = {
                                            selectedCategoryId = category.id
                                            categoryMenuExpanded = false
                                        },
                                        leadingIcon = {
                                            if (selectedCategoryId == category.id) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Stores (STORE-7): group + explicit store chips, edited in the tree dialog
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Stores",
                                style = MaterialTheme.typography.titleSmall
                            )
                            TextButton(
                                onClick = { showStoreSelectionDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("Edit", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        val explicitStores = uiState.stores.filter { it.id in selectedStoreIds && it.groupId !in selectedGroupIds }
                        val selectedGroups = uiState.storeGroups.filter { it.id in selectedGroupIds }

                        if (uiState.stores.isEmpty() && uiState.storeGroups.isEmpty()) {
                            Text(
                                text = "No stores created yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (selectedGroups.isEmpty() && explicitStores.isEmpty()) {
                            Text(
                                text = "No stores assigned",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                selectedGroups.forEach { group ->
                                    val excludedStores = uiState.stores
                                        .filter { it.groupId == group.id && it.id in excludedStoreIds }
                                    InputChip(
                                        selected = true,
                                        onClick = { showStoreSelectionDialog = true },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Category,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        colors = InputChipDefaults.inputChipColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                        ),
                                        label = {
                                            if (excludedStores.isEmpty()) {
                                                Text(group.name)
                                            } else {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(group.name)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    excludedStores.forEach { store ->
                                                        Text(
                                                            text = "⊘ ${store.name}",
                                                            textDecoration = TextDecoration.LineThrough,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                                            modifier = Modifier.clickable {
                                                                // Tap an excluded store to re-include it
                                                                excludedStoreIds = excludedStoreIds - store.id
                                                            }
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                    }
                                                }
                                            }
                                        },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove ${group.name}",
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { selectedGroupIds = selectedGroupIds - group.id }
                                            )
                                        }
                                    )
                                }
                                explicitStores.forEach { store ->
                                    InputChip(
                                        selected = true,
                                        onClick = { selectedStoreIds = selectedStoreIds - store.id },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Storefront,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        label = { Text(store.name) },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove ${store.name}",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        // Default unit (optional, ITEM-5)
                        Text(
                            text = "Default unit (optional)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UnitPresets.ALL.forEach { preset ->
                                val isSelected = itemDefaultUnit.equals(preset, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        itemDefaultUnit = if (isSelected) "" else preset
                                    },
                                    label = { Text(preset) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = itemDefaultUnit,
                            onValueChange = { itemDefaultUnit = it },
                            label = { Text("Custom unit") },
                            placeholder = { Text("e.g. pcs, pack, kg, bottle …") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Preselected when adding this item to the shopping list.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Item type: only show for existing ONE_TIME items to allow promoting to permanent
                        if (uiState.existingItem != null && itemType == ItemType.ONE_TIME) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "One-time item",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "This item will be deleted with the daily cleanup (24 h) after being checked off on the shopping list.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(
                                        onClick = { itemType = ItemType.PERMANENT },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text("Promote to permanent item")
                                    }
                                }
                            }
                        }

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
                                val groupStoreIds = uiState.stores
                                    .filter { it.groupId != null && it.groupId in selectedGroupIds }
                                    .map { it.id }
                                    .toSet()
                                viewModel.saveItem(
                                    name = itemName,
                                    notes = itemNotes,
                                    type = itemType,
                                    selection = StoreSelection(
                                        memberStoreIds = selectedStoreIds - groupStoreIds,
                                        excludedStoreIds = excludedStoreIds,
                                        groupIds = selectedGroupIds
                                    ),
                                    selectedCategoryId = selectedCategoryId,
                                    defaultUnit = itemDefaultUnit
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

    if (showStoreSelectionDialog) {
        StoreSelectionDialog(
            groups = uiState.storeGroups,
            stores = uiState.stores,
            selectedGroupIds = selectedGroupIds,
            selectedStoreIds = selectedStoreIds,
            excludedStoreIds = excludedStoreIds,
            onToggleGroup = { groupId ->
                val adding = groupId !in selectedGroupIds
                selectedGroupIds = if (adding) selectedGroupIds + groupId else selectedGroupIds - groupId
                if (adding) {
                    val groupMemberIds = uiState.stores.filter { it.groupId == groupId }.map { it.id }.toSet()
                    selectedStoreIds = selectedStoreIds - groupMemberIds
                    excludedStoreIds = excludedStoreIds - groupMemberIds
                }
            },
            onToggleStore = { storeId ->
                selectedStoreIds = if (storeId in selectedStoreIds) selectedStoreIds - storeId else selectedStoreIds + storeId
            },
            onToggleExclusion = { storeId ->
                excludedStoreIds = if (storeId in excludedStoreIds) excludedStoreIds - storeId else excludedStoreIds + storeId
            },
            onDismiss = { showStoreSelectionDialog = false }
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
                val groupStoreIds = uiState.stores
                    .filter { it.groupId != null && it.groupId in selectedGroupIds }
                    .map { it.id }
                    .toSet()
                viewModel.saveItem(
                    name = itemName,
                    notes = itemNotes,
                    type = itemType,
                    selection = StoreSelection(
                        memberStoreIds = selectedStoreIds - groupStoreIds,
                        excludedStoreIds = excludedStoreIds,
                        groupIds = selectedGroupIds
                    ),
                    selectedCategoryId = selectedCategoryId,
                    defaultUnit = itemDefaultUnit,
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


/**
 * Checkbox tree for the item's store selection (STORE-7): groups by sortOrder
 * with nested member stores, ungrouped stores as plain rows. Checking a group
 * selects it and switches its members to exclude toggles (⊘, struck-through
 * when excluded, tap to re-include); with the group unchecked, members are
 * normal include checkboxes.
 */
@Composable
private fun StoreSelectionDialog(
    groups: List<StoreGroup>,
    stores: List<Store>,
    selectedGroupIds: Set<String>,
    selectedStoreIds: Set<String>,
    excludedStoreIds: Set<String>,
    onToggleGroup: (String) -> Unit,
    onToggleStore: (String) -> Unit,
    onToggleExclusion: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        title = { Text("Select stores") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                groups.forEach { group ->
                    val groupSelected = group.id in selectedGroupIds
                    val members = stores.filter { it.groupId == group.id }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleGroup(group.id) }
                    ) {
                        Checkbox(
                            checked = groupSelected,
                            onCheckedChange = { onToggleGroup(group.id) }
                        )
                        Icon(
                            imageVector = Icons.Outlined.Category,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        if (groupSelected) {
                            Text(
                                text = "tap store to exclude",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    members.forEach { member ->
                        val excluded = member.id in excludedStoreIds
                        if (groupSelected) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleExclusion(member.id) }
                                    .padding(start = 32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Storefront,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = member.name,
                                    textDecoration = TextDecoration.LineThrough.takeIf { excluded },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = if (excluded) 0.6f else 1f
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Outlined.Block,
                                    contentDescription = if (excluded) "Re-include ${member.name}" else "Exclude ${member.name}",
                                    tint = if (excluded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleStore(member.id) }
                                    .padding(start = 32.dp)
                            ) {
                                Checkbox(
                                    checked = member.id in selectedStoreIds,
                                    onCheckedChange = { onToggleStore(member.id) }
                                )
                                Icon(
                                    imageVector = Icons.Outlined.Storefront,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(member.name)
                            }
                        }
                    }
                }
                val ungroupedStores = stores.filter { it.groupId == null }
                if (groups.isNotEmpty() && ungroupedStores.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                ungroupedStores.forEach { store ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleStore(store.id) }
                    ) {
                        Checkbox(
                            checked = store.id in selectedStoreIds,
                            onCheckedChange = { onToggleStore(store.id) }
                        )
                        Icon(
                            imageVector = Icons.Outlined.Storefront,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(store.name)
                    }
                }
            }
        }
    )
}
