package net.marvinweber.simsli.ui.screens.item

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.marvinweber.simsli.R
import net.marvinweber.simsli.domain.model.ItemType
import net.marvinweber.simsli.domain.model.Store
import net.marvinweber.simsli.domain.model.StoreGroup
import net.marvinweber.simsli.domain.model.StoreSelection
import net.marvinweber.simsli.domain.model.UnitPresets
import net.marvinweber.simsli.ui.text
import net.marvinweber.simsli.ui.components.ItemLinkChipsRow
import net.marvinweber.simsli.ui.components.unitPresetLabel
import net.marvinweber.simsli.ui.components.LinkUtils

/**
 * Create/edit a catalog item: name, store assignments, type, notes.
 * Entry-level data (quantity, unit, comment) is entered on the list, not here.
 */

/** Form values as they were when the screen opened; compared against to detect pending changes. */
private data class FormBaseline(
    val name: String,
    val notes: String,
    val defaultUnit: String,
    val type: ItemType,
    val storeIds: Set<String>,
    val groupIds: Set<String>,
    val excludedIds: Set<String>,
    val favoriteIds: Set<String>,
    val categoryId: String?,
    /** Link URLs only — titles arrive asynchronously from the web and are not user edits. */
    val linkUrls: Set<String>
) {
    companion object {
        fun empty() = FormBaseline(
            name = "", notes = "", defaultUnit = "", type = ItemType.PERMANENT,
            storeIds = emptySet(), groupIds = emptySet(), excludedIds = emptySet(),
            favoriteIds = emptySet(), categoryId = null, linkUrls = emptySet()
        )
    }
}

private val FormBaselineSaver = listSaver<FormBaseline, Any>(
    save = {
        listOf(
            it.name, it.notes, it.defaultUnit, it.type.name,
            it.storeIds.toList(), it.groupIds.toList(), it.excludedIds.toList(),
            it.favoriteIds.toList(),
            it.categoryId.orEmpty(), it.linkUrls.toList()
        )
    },
    restore = {
        FormBaseline(
            name = it[0] as String,
            notes = it[1] as String,
            defaultUnit = it[2] as String,
            type = ItemType.valueOf(it[3] as String),
            storeIds = (it[4] as List<String>).toSet(),
            groupIds = (it[5] as List<String>).toSet(),
            excludedIds = (it[6] as List<String>).toSet(),
            favoriteIds = (it[7] as List<String>).toSet(),
            categoryId = (it[8] as String).ifBlank { null },
            linkUrls = (it[9] as List<String>).toSet()
        )
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToItemPrices: () -> Unit = {},
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
    var favoriteStoreIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var prefilled by rememberSaveable { mutableStateOf(false) }
    var showAddLinkDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showStoreSelectionDialog by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var unitMenuExpanded by remember { mutableStateOf(false) }
    var showCustomUnitDialog by remember { mutableStateOf(false) }
    var customUnitInput by remember { mutableStateOf("") }
    var duplicateWarningName by rememberSaveable { mutableStateOf<String?>(null) }
    var duplicateWarningIsSimilar by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Baseline for pending-change detection: empty for create mode, overwritten
    // by the prefill effect once an existing item is loaded.
    var baseline by rememberSaveable(stateSaver = FormBaselineSaver) { mutableStateOf(FormBaseline.empty()) }

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
        favoriteStoreIds = uiState.existingSelection.favoriteStoreIds
        selectedCategoryId = item.categoryId
        stripResult.extractedUrls.forEach { url ->
            viewModel.addLink(url)
        }
        baseline = FormBaseline(
            name = item.name,
            notes = stripResult.remainingText,
            defaultUnit = item.defaultUnit.orEmpty(),
            type = item.type,
            storeIds = selectedStoreIds,
            groupIds = selectedGroupIds,
            excludedIds = excludedStoreIds,
            favoriteIds = favoriteStoreIds,
            categoryId = item.categoryId,
            // URLs embedded in the original notes are extracted into links on open,
            // so they count as the baseline, not as pending changes.
            linkUrls = (item.links.map { it.url } + stripResult.extractedUrls).toSet()
        )
        prefilled = true
    }

    events?.let { event ->
        when (event) {
            is ItemDetailUiEvent.NavigateBack -> {
                onNavigateBack()
                viewModel.onEventConsumed()
            }
            is ItemDetailUiEvent.ShowError -> {
                val message = event.message.text()
                LaunchedEffect(event) {
                    snackbarHostState.showSnackbar(message)
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

    // Pending-change detection: compares the current form against the baseline
    // captured on open (trimmed like the save path does; links by URL only).
    val current = FormBaseline(
        name = itemName.trim(),
        notes = itemNotes.trim(),
        defaultUnit = itemDefaultUnit.trim(),
        type = itemType,
        storeIds = selectedStoreIds,
        groupIds = selectedGroupIds,
        excludedIds = excludedStoreIds,
        favoriteIds = favoriteStoreIds,
        categoryId = selectedCategoryId,
        linkUrls = uiState.links.map { it.url }.toSet()
    )
    // Pending changes also cover edits made on the Store prices screen: a price
    // edit counts when it differs from the row's stored value, the reference size
    // edit when it differs from the item's stored value.
    val hasPriceEdits = uiState.priceEdits.prices.any { (storeId, minor) ->
        minor != uiState.priceRows.firstOrNull { it.storeId == storeId }?.priceMinor
    }
    val hasReferenceSizeEdits = uiState.priceEdits.referenceSizeEdited &&
        uiState.priceEdits.effectiveReferenceSize(uiState.existingItem?.referenceSize) !=
        uiState.existingItem?.referenceSize
    val hasPendingChanges =
        (baseline != current || hasPriceEdits || hasReferenceSizeEdits) && !uiState.isSaving

    val requestBack = {
        if (hasPendingChanges) showDiscardDialog = true else viewModel.onBack()
    }
    BackHandler(enabled = hasPendingChanges) {
        showDiscardDialog = true
    }

    val saveForm: (Boolean) -> Unit = { force ->
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
                groupIds = selectedGroupIds,
                favoriteStoreIds = favoriteStoreIds
            ),
            selectedCategoryId = selectedCategoryId,
            defaultUnit = itemDefaultUnit,
            force = force
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(if (uiState.existingItem == null) R.string.add_item else R.string.item_edit)) },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { saveForm(false) },
                        enabled = itemName.isNotBlank() && !uiState.isSaving && !uiState.isWaitingForItem
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Save,
                            contentDescription = stringResource(R.string.common_save)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Save + delete stay pinned below the scrolling content
            // (delete only for existing items)
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { saveForm(false) },
                    modifier = Modifier.weight(1f),
                    enabled = itemName.isNotBlank() && !uiState.isSaving
                ) {
                    Text(stringResource(if (uiState.existingItem == null) R.string.add_item else R.string.common_save))
                }

                if (uiState.existingItem != null) {
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.weight(1f),
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
                        Text(stringResource(R.string.item_delete_button))
                    }
                }
            }
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
                        // 12dp between modules; the DetailCards own the inner rhythm
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Item name (required): hero field — no outline, large type
                        OutlinedTextField(
                            value = itemName,
                            onValueChange = { itemName = it },
                            placeholder = { Text(stringResource(R.string.item_name_placeholder)) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.headlineSmall,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Details: category + default unit — the descriptive fields
                        DetailCard(
                            title = stringResource(R.string.item_section_details)
                        ) {
                            // Category: full-width row opening the dropdown
                            Box {
                                val selectedCategory = uiState.categories.find { it.id == selectedCategoryId }
                                val categoryLabel = if (selectedCategory != null) {
                                    if (!selectedCategory.emoji.isNullOrBlank()) "${selectedCategory.emoji} ${selectedCategory.name}" else selectedCategory.name
                                } else {
                                    stringResource(R.string.item_category_none)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable { categoryMenuExpanded = true }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.item_category),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(text = categoryLabel, style = MaterialTheme.typography.bodyLarge)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(
                                    expanded = categoryMenuExpanded,
                                    onDismissRequest = { categoryMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.item_category_none)) },
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

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Default unit (optional, ITEM-5): dropdown like category —
                            // presets + "Custom…", which opens a free-text dialog
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box {
                                    val isPreset = UnitPresets.ALL.any { it.equals(itemDefaultUnit, ignoreCase = true) }
                                    val unitLabel = when {
                                        itemDefaultUnit.isBlank() -> stringResource(R.string.unit_none)
                                        isPreset -> unitPresetLabel(UnitPresets.ALL.first { it.equals(itemDefaultUnit, ignoreCase = true) })
                                        else -> itemDefaultUnit
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.small)
                                            .clickable { unitMenuExpanded = true }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.item_unit),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(text = unitLabel, style = MaterialTheme.typography.bodyLarge)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = unitMenuExpanded,
                                        onDismissRequest = { unitMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.unit_none)) },
                                            onClick = {
                                                itemDefaultUnit = ""
                                                unitMenuExpanded = false
                                            },
                                            leadingIcon = {
                                                if (itemDefaultUnit.isBlank()) {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        )
                                        UnitPresets.ALL.forEach { preset ->
                                            DropdownMenuItem(
                                                text = { Text(unitPresetLabel(preset)) },
                                                onClick = {
                                                    itemDefaultUnit = preset
                                                    unitMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    if (itemDefaultUnit.equals(preset, ignoreCase = true)) {
                                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            )
                                        }
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (isPreset || itemDefaultUnit.isBlank()) stringResource(R.string.unit_custom)
                                                    else stringResource(R.string.unit_edit_custom)
                                                )
                                            },
                                            onClick = {
                                                // Prefill only when editing an existing custom value
                                                customUnitInput = if (isPreset || itemDefaultUnit.isBlank()) "" else itemDefaultUnit
                                                unitMenuExpanded = false
                                                showCustomUnitDialog = true
                                            }
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.item_unit_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Item type: only show for existing ONE_TIME items to allow promoting to permanent
                        if (uiState.existingItem != null && itemType == ItemType.ONE_TIME) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
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
                                            text = stringResource(R.string.item_one_time_banner),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.item_one_time_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(
                                        onClick = { itemType = ItemType.PERMANENT },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text(stringResource(R.string.item_promote_permanent))
                                    }
                                }
                            }
                        }

                        // Stores (STORE-7): group + explicit store chips, edited in the tree dialog
                        DetailCard(
                            title = stringResource(R.string.stores),
                            action = {
                                TextButton(
                                    onClick = { showStoreSelectionDialog = true },
                                    // Fixed height: the touch-target minimum would inflate
                                    // the header row and push the chips far below it
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text(stringResource(R.string.common_edit), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        ) {
                            val explicitStores = uiState.stores.filter { it.id in selectedStoreIds && it.groupId !in selectedGroupIds }
                            val selectedGroups = uiState.storeGroups.filter { it.id in selectedGroupIds }

                            if (uiState.stores.isEmpty() && uiState.storeGroups.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.item_no_stores_created),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (selectedGroups.isEmpty() && explicitStores.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.item_no_stores_assigned),
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
                                        val groupFavorites = uiState.stores
                                            .filter { it.groupId == group.id && it.id !in excludedStoreIds && it.id in favoriteStoreIds }
                                        InputChip(
                                            selected = true,
                                            // Chips are display-only; editing happens in the selection dialog
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
                                                if (excludedStores.isEmpty() && groupFavorites.isEmpty()) {
                                                    Text(group.name)
                                                } else {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(group.name)
                                                        if (groupFavorites.isNotEmpty()) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            groupFavorites.forEach { fav ->
                                                                Icon(
                                                                    imageVector = Icons.Filled.Star,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(13.dp),
                                                                    tint = Color(0xFFFFB300)
                                                                )
                                                                Spacer(modifier = Modifier.width(2.dp))
                                                                Text(
                                                                    text = fav.name,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                            }
                                                        }
                                                        if (excludedStores.isNotEmpty()) {
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            excludedStores.forEach { store ->
                                                                Text(
                                                                    text = "⊘ ${store.name}",
                                                                    textDecoration = TextDecoration.LineThrough,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    explicitStores.forEach { store ->
                                        InputChip(
                                            selected = true,
                                            // Chips are display-only; editing happens in the selection dialog
                                            onClick = { showStoreSelectionDialog = true },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Outlined.Storefront,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            label = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (store.id in favoriteStoreIds) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Star,
                                                            contentDescription = stringResource(R.string.common_favorite),
                                                            modifier = Modifier.size(14.dp),
                                                            tint = Color(0xFFFFB300)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                    }
                                                    Text(store.name)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Store prices (STORE-6): pushed screen with reference size + per-store prices
                        Card(
                            onClick = onNavigateToItemPrices,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Payments,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.prices_title),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                uiState.priceEdits
                                    .effectiveReferenceSize(uiState.existingItem?.referenceSize)
                                    ?.let { refSize ->
                                        Text(
                                            text = refSize,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Notes & links: links are pasted into notes (auto-extracted) or added via dialog
                        DetailCard(
                            title = stringResource(R.string.item_section_notes),
                            action = {
                                TextButton(
                                    onClick = { showAddLinkDialog = true },
                                    // Same as the Stores header: keep the row hug-sized
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text(stringResource(R.string.item_add_link), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        ) {
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
                                placeholder = { Text(stringResource(R.string.item_notes_placeholder)) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                )
                            )

                            if (uiState.links.isNotEmpty()) {
                                ItemLinkChipsRow(
                                    links = uiState.links,
                                    onRemove = { link -> viewModel.removeLink(link.url) }
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.item_links_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }

                        if (showAddLinkDialog) {
                            var linkInput by remember { mutableStateOf("") }
                            AlertDialog(
                                onDismissRequest = { showAddLinkDialog = false },
                                title = { Text(stringResource(R.string.item_add_link)) },
                                text = {
                                    OutlinedTextField(
                                        value = linkInput,
                                        onValueChange = { linkInput = it },
                                        label = { Text(stringResource(R.string.common_url)) },
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
                                        Text(stringResource(R.string.common_add))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showAddLinkDialog = false }) {
                                        Text(stringResource(R.string.common_cancel))
                                    }
                                }
                            )
                        }

                    }
                }
            }
        }
    }

    if (showCustomUnitDialog) {
        AlertDialog(
            onDismissRequest = { showCustomUnitDialog = false },
            title = { Text(stringResource(R.string.unit_custom_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = customUnitInput,
                    onValueChange = { customUnitInput = it },
                    label = { Text(stringResource(R.string.item_unit)) },
                    placeholder = { Text(stringResource(R.string.unit_custom_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        itemDefaultUnit = customUnitInput.trim()
                        showCustomUnitDialog = false
                    },
                    enabled = customUnitInput.isNotBlank()
                ) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomUnitDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showDiscardDialog) {
        DiscardChangesDialog(
            onDiscard = {
                showDiscardDialog = false
                viewModel.onBack()
            },
            onDismiss = { showDiscardDialog = false }
        )
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
            favoriteStoreIds = favoriteStoreIds,
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
                val willExclude = storeId !in excludedStoreIds
                excludedStoreIds = if (willExclude) excludedStoreIds + storeId else excludedStoreIds - storeId
                if (willExclude) {
                    favoriteStoreIds = favoriteStoreIds - storeId
                }
            },
            onToggleFavorite = { storeId ->
                favoriteStoreIds = if (storeId in favoriteStoreIds) {
                    favoriteStoreIds - storeId
                } else {
                    favoriteStoreIds + storeId
                }
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
                saveForm(true)
            },
            onDismiss = {
                duplicateWarningName = null
                duplicateWarningIsSimilar = false
            }
        )
    }
}

/**
 * Rounded container for one form module (details, stores, notes …). The card
 * carries the belonging: tight rhythm inside (10dp), section spacing between
 * cards (12dp, set by the parent column).
 */
@Composable
private fun DetailCard(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                action?.invoke()
            }
            content()
        }
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
            Text(stringResource(if (isSimilar) R.string.item_duplicate_similar_title else R.string.item_duplicate_exact_title))
        },
        text = {
            Text(
                when {
                    isSimilar && isEditing -> stringResource(R.string.item_duplicate_similar_save, matchedName)
                    isSimilar -> stringResource(R.string.item_duplicate_similar_add, matchedName)
                    isEditing -> stringResource(R.string.item_duplicate_exact_save, matchedName)
                    else -> stringResource(R.string.item_duplicate_exact_add, matchedName)
                }
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(if (isEditing) R.string.item_save_anyway else R.string.item_add_anyway))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun DiscardChangesDialog(
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.item_discard_title)) },
        text = { Text(stringResource(R.string.item_discard_text)) },
        confirmButton = {
            Button(
                onClick = onDiscard,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.item_discard_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.item_discard_keep))
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
        title = { Text(stringResource(R.string.item_delete_title)) },
        text = {
            Text(stringResource(R.string.item_delete_text, itemName))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.common_delete))
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
    favoriteStoreIds: Set<String>,
    onToggleGroup: (String) -> Unit,
    onToggleStore: (String) -> Unit,
    onToggleExclusion: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
        },
        title = { Text(stringResource(R.string.item_select_stores)) },
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
                                text = stringResource(R.string.item_tap_to_exclude),
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
                                if (!excluded) {
                                    val isFavorite = member.id in favoriteStoreIds
                                    IconButton(
                                        onClick = { onToggleFavorite(member.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                            contentDescription = stringResource(if (isFavorite) R.string.item_remove_favorite else R.string.item_add_favorite, member.name),
                                            tint = if (isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onToggleExclusion(member.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Block,
                                        contentDescription = stringResource(if (excluded) R.string.item_reinclude else R.string.item_exclude, member.name),
                                        tint = if (excluded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
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
                                Text(member.name, modifier = Modifier.weight(1f))
                                if (member.id in selectedStoreIds) {
                                    val isFavorite = member.id in favoriteStoreIds
                                    IconButton(
                                        onClick = { onToggleFavorite(member.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                            contentDescription = stringResource(if (isFavorite) R.string.item_remove_favorite else R.string.item_add_favorite, member.name),
                                            tint = if (isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
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
                        Text(store.name, modifier = Modifier.weight(1f))
                        if (store.id in selectedStoreIds) {
                            val isFavorite = store.id in favoriteStoreIds
                            IconButton(
                                onClick = { onToggleFavorite(store.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = stringResource(if (isFavorite) R.string.item_remove_favorite else R.string.item_add_favorite, store.name),
                                    tint = if (isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}
