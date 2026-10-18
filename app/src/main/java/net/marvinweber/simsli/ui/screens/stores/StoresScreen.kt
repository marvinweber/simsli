package net.marvinweber.simsli.ui.screens.stores

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import net.marvinweber.simsli.R
import net.marvinweber.simsli.domain.model.Category
import net.marvinweber.simsli.domain.model.Store
import net.marvinweber.simsli.domain.model.StoreGroup

/**
 * The Stores sub-tab of the Catalog tab (STORE-7): store groups as draggable
 * sections with their member stores nested, ungrouped stores below. Owns its
 * own Scaffold so the add FAB and dialogs stay self-contained; the title
 * lives in the Catalog tab row, not here.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StoresTabContent(
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    viewModel: StoresViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val events by viewModel.events.collectAsState()
    // Re-sync the ViewModel whenever this sub-tab enters composition so a restored
    // query (e.g. after switching bottom tabs) filters again.
    LaunchedEffect(searchQuery) { viewModel.setSearchQuery(searchQuery) }
    val isFiltering = uiState.searchQuery.isNotBlank()

    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingStoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDelete by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
    var showAddGroupDialog by rememberSaveable { mutableStateOf(false) }
    var editingGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteGroup by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }

    events?.let { event ->
        when (event) {
            is StoresUiEvent.ShowError -> {
                // TODO: Show error snackbar
                viewModel.onEventConsumed()
            }
            is StoresUiEvent.ConfirmDeleteStore -> {
                pendingDelete = event.storeId to event.storeName
                viewModel.onEventConsumed()
            }
            is StoresUiEvent.ConfirmDeleteGroup -> {
                pendingDeleteGroup = event.groupId to event.groupName
                viewModel.onEventConsumed()
            }
        }
    }

    if (pendingDelete != null) {
        DeleteStoreDialog(
            storeName = pendingDelete!!.second,
            onConfirm = {
                viewModel.confirmDeleteStore(pendingDelete!!.first)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }

    if (pendingDeleteGroup != null) {
        DeleteGroupDialog(
            groupName = pendingDeleteGroup!!.second,
            onConfirm = {
                viewModel.confirmDeleteGroup(pendingDeleteGroup!!.first)
                pendingDeleteGroup = null
            },
            onDismiss = { pendingDeleteGroup = null }
        )
    }

    if (showAddDialog) {
        AddStoreDialog(
            groupOptions = uiState.storeGroups,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, groupId ->
                viewModel.createStore(name, groupId)
                showAddDialog = false
            }
        )
    }

    val editingStore = uiState.stores.firstOrNull { it.id == editingStoreId }
    if (editingStore != null) {
        val initialStoreCategoryIds = remember(editingStore.id, uiState.storeCategoriesByStoreId) {
            uiState.storeCategoriesByStoreId[editingStore.id]?.map { it.categoryId } ?: emptyList()
        }
        StoreEditDialog(
            store = editingStore,
            groupOptions = uiState.storeGroups,
            householdCategories = uiState.categories,
            initialStoreCategoryIds = initialStoreCategoryIds,
            onDismiss = { editingStoreId = null },
            onConfirm = { name, groupId, orderedCategoryIds ->
                viewModel.updateStore(editingStore.id, name, groupId, orderedCategoryIds)
                editingStoreId = null
            }
        )
    }

    if (showAddGroupDialog) {
        GroupDialog(
            title = stringResource(R.string.stores_add_group),
            confirmText = stringResource(R.string.common_add),
            initialName = "",
            onDismiss = { showAddGroupDialog = false },
            onConfirm = { name ->
                viewModel.createGroup(name)
                showAddGroupDialog = false
            }
        )
    }

    val editingGroup = uiState.storeGroups.firstOrNull { it.id == editingGroupId }
    if (editingGroup != null) {
        GroupDialog(
            title = stringResource(R.string.stores_rename_group),
            confirmText = stringResource(R.string.common_save),
            initialName = editingGroup.name,
            onDismiss = { editingGroupId = null },
            onConfirm = { name ->
                viewModel.updateGroup(editingGroup.id, name)
                editingGroupId = null
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.stores_add_store))
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    ContainedLoadingIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.stores.isEmpty() && uiState.storeGroups.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isFiltering) {
                                "Nothing matches “${uiState.searchQuery}”"
                            } else {
                                "No stores yet. Tap + to add one."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                else -> {
                    StoresList(
                        storeGroups = uiState.storeGroups,
                        stores = uiState.stores,
                        isFiltering = isFiltering,
                        onEditStore = { editingStoreId = it.id },
                        onDeleteStore = { viewModel.onDeleteStore(it) },
                        onEditGroup = { editingGroupId = it.id },
                        onDeleteGroup = { viewModel.onDeleteGroup(it) },
                        onAddGroup = { showAddGroupDialog = true },
                        onReorderGroups = { viewModel.onReorderGroups(it) }
                    )
                }
            }
        }
    }
}

/**
 * Group sections (drag to reorder) with nested store rows, then ungrouped
 * stores. Drag mechanics lifted from CategoriesScreen's list.
 */
@Composable
private fun StoresList(
    storeGroups: List<StoreGroup>,
    stores: List<Store>,
    isFiltering: Boolean,
    onEditStore: (Store) -> Unit,
    onDeleteStore: (String) -> Unit,
    onEditGroup: (StoreGroup) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onAddGroup: () -> Unit,
    onReorderGroups: (List<StoreGroup>) -> Unit
) {
    var localGroups by remember(storeGroups) { mutableStateOf(storeGroups) }
    var draggingGroupId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val lazyListState = rememberLazyListState()

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        localGroups.forEachIndexed { groupIndex, group ->
            val isDragging = group.id == draggingGroupId
            item(key = "group:${group.id}") {
                Column(
                    modifier = if (isDragging) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer { translationY = dragOffsetY }
                            .shadow(8.dp, shape = MaterialTheme.shapes.small)
                    } else {
                        Modifier
                            .zIndex(0f)
                            .animateItem()
                    }
                ) {
                    GroupRow(
                        group = group,
                        isDragging = isDragging,
                        storeCount = stores.count { it.groupId == group.id },
                        onEdit = { onEditGroup(group) },
                        onDelete = { onDeleteGroup(group.id) },
                        showDragHandle = !isFiltering,
                        dragHandleModifier = if (isFiltering) {
                            // Reordering from a filtered view would corrupt sortOrder — disabled while searching
                            Modifier
                        } else {
                            Modifier.pointerInput(group.id) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingGroupId = group.id
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY += dragAmount.y

                                    val currentId = draggingGroupId ?: return@detectDragGestures
                                    val currentIdx = localGroups.indexOfFirst { it.id == currentId }
                                    if (currentIdx == -1) return@detectDragGestures

                                    val itemInfo = lazyListState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.key == "group:$currentId" }
                                        ?: return@detectDragGestures

                                    val currentItemCenter = itemInfo.offset + itemInfo.size / 2 + dragOffsetY

                                    // Check previous group
                                    if (currentIdx > 0) {
                                        val prevGroup = localGroups[currentIdx - 1]
                                        val prevItemInfo = lazyListState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.key == "group:${prevGroup.id}" }
                                        if (prevItemInfo != null && currentItemCenter < (prevItemInfo.offset + prevItemInfo.size / 2)) {
                                            val targetIdx = currentIdx - 1
                                            val updated = localGroups.toMutableList()
                                            val item = updated.removeAt(currentIdx)
                                            updated.add(targetIdx, item)
                                            localGroups = updated
                                            dragOffsetY += (itemInfo.offset - prevItemInfo.offset)
                                            return@detectDragGestures
                                        }
                                    }

                                    // Check next group
                                    if (currentIdx < localGroups.size - 1) {
                                        val nextGroup = localGroups[currentIdx + 1]
                                        val nextItemInfo = lazyListState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.key == "group:${nextGroup.id}" }
                                        if (nextItemInfo != null && currentItemCenter > (nextItemInfo.offset + nextItemInfo.size / 2)) {
                                            val targetIdx = currentIdx + 1
                                            val updated = localGroups.toMutableList()
                                            val item = updated.removeAt(currentIdx)
                                            updated.add(targetIdx, item)
                                            localGroups = updated
                                            dragOffsetY -= (nextItemInfo.offset - itemInfo.offset)
                                            return@detectDragGestures
                                        }
                                    }
                                },
                                onDragEnd = {
                                    val movedId = draggingGroupId
                                    draggingGroupId = null
                                    dragOffsetY = 0f
                                    if (movedId != null) {
                                        if (storeGroups.map { it.id } != localGroups.map { it.id }) {
                                            onReorderGroups(localGroups)
                                        }
                                    }
                                },
                                onDragCancel = {
                                    draggingGroupId = null
                                    dragOffsetY = 0f
                                    localGroups = storeGroups
                                }
                            )
                        }
                        }
                    )
                }
            }

            itemsIndexed(
                items = stores.filter { it.groupId == group.id },
                key = { _, store -> store.id }
            ) { storeIndex, store ->
                Column(modifier = Modifier.animateItem()) {
                    StoreRow(
                        store = store,
                        indented = true,
                        onClick = { onEditStore(store) },
                        onDelete = { onDeleteStore(store.id) }
                    )
                    val groupStores = stores.filter { it.groupId == group.id }
                    if (storeIndex < groupStores.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }

        if (localGroups.isNotEmpty() || stores.any { it.groupId == null }) {
            item(key = "add_group") {
                TextButton(onClick = onAddGroup) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.stores_add_group))
                }
            }
        }

        val ungroupedStores = stores.filter { it.groupId == null }
        itemsIndexed(
            items = ungroupedStores,
            key = { _, store -> "ungrouped:${store.id}" }
        ) { storeIndex, store ->
            Column(modifier = Modifier.animateItem()) {
                if (storeIndex == 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        thickness = 0.5.dp
                    )
                }
                StoreRow(
                    store = store,
                    indented = false,
                    onClick = { onEditStore(store) },
                    onDelete = { onDeleteStore(store.id) }
                )
                if (storeIndex < ungroupedStores.lastIndex) {
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

@Composable
private fun GroupRow(
    group: StoreGroup,
    isDragging: Boolean,
    storeCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showDragHandle: Boolean = true,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = dragHandleModifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (showDragHandle) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = stringResource(R.string.common_reorder_name, group.name),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = group.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "$storeCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.stores_delete_group_cd),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun StoreRow(
    store: Store,
    indented: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
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
                .padding(start = if (indented) 56.dp else 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Storefront,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 0.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = store.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.stores_delete_store_cd),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AddStoreDialog(
    groupOptions: List<StoreGroup>,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    StoreFormDialog(
        title = stringResource(R.string.stores_add_store),
        confirmText = stringResource(R.string.common_add),
        initialName = "",
        initialGroupId = null,
        groupOptions = groupOptions,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
private fun StoreEditDialog(
    store: Store,
    groupOptions: List<StoreGroup>,
    householdCategories: List<Category>,
    initialStoreCategoryIds: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, List<String>) -> Unit
) {
    var name by rememberSaveable(store.id) { mutableStateOf(store.name) }
    var selectedGroupId by rememberSaveable(store.id) { mutableStateOf(store.groupId) }
    var groupMenuExpanded by remember { mutableStateOf(false) }

    // Checked category IDs in explicit aisle order (STORE-4)
    var checkedCategoryIds by remember(store.id, initialStoreCategoryIds) {
        mutableStateOf(initialStoreCategoryIds)
    }

    var draggingCategoryId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val lazyListState = rememberLazyListState()

    val categoryById = remember(householdCategories) { householdCategories.associateBy { it.id } }
    val checkedCategories = checkedCategoryIds.mapNotNull { categoryById[it] }
    val uncheckedCategories = householdCategories.filter { it.id !in checkedCategoryIds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stores_edit_store)) },
        text = {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                item(key = "field:name") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.stores_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item(key = "field:group") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.stores_group_label),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        FilterChip(
                            selected = true,
                            onClick = { groupMenuExpanded = true },
                            label = {
                                Text(
                                    selectedGroupId?.let { id ->
                                        groupOptions.firstOrNull { it.id == id }?.name
                                    } ?: "No group",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                            expanded = groupMenuExpanded,
                            onDismissRequest = { groupMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.stores_no_group)) },
                                onClick = {
                                    selectedGroupId = null
                                    groupMenuExpanded = false
                                }
                            )
                            groupOptions.forEach { group ->
                                DropdownMenuItem(
                                    text = { Text(group.name) },
                                    onClick = {
                                        selectedGroupId = group.id
                                        groupMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.stores_aisle_order),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.stores_aisle_order_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (householdCategories.isEmpty()) {
                    item(key = "empty:categories") {
                        Text(
                            text = stringResource(R.string.stores_no_categories_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    if (checkedCategories.isEmpty()) {
                        item(key = "empty:checked") {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.stores_all_default_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = checkedCategories,
                            key = { _, cat -> "checked:${cat.id}" }
                        ) { index, category ->
                            val isDragging = category.id == draggingCategoryId
                            Column(
                                modifier = if (isDragging) {
                                    Modifier
                                        .zIndex(1f)
                                        .graphicsLayer { translationY = dragOffsetY }
                                        .shadow(8.dp, shape = MaterialTheme.shapes.small)
                                } else {
                                    Modifier
                                        .zIndex(0f)
                                        .animateItem()
                                }
                            ) {
                                CheckedCategoryRow(
                                    category = category,
                                    index = index,
                                    isDragging = isDragging,
                                    onUncheck = {
                                        checkedCategoryIds = checkedCategoryIds - category.id
                                    },
                                    dragHandleModifier = Modifier.pointerInput(category.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggingCategoryId = category.id
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetY += dragAmount.y

                                                val currentId = draggingCategoryId ?: return@detectDragGestures
                                                val currentIdx = checkedCategoryIds.indexOf(currentId)
                                                if (currentIdx == -1) return@detectDragGestures

                                                val itemInfo = lazyListState.layoutInfo.visibleItemsInfo
                                                    .firstOrNull { it.key == "checked:$currentId" }
                                                    ?: return@detectDragGestures

                                                val currentItemCenter = itemInfo.offset + itemInfo.size / 2 + dragOffsetY

                                                // Check previous item
                                                if (currentIdx > 0) {
                                                    val prevId = checkedCategoryIds[currentIdx - 1]
                                                    val prevItemInfo = lazyListState.layoutInfo.visibleItemsInfo
                                                        .firstOrNull { it.key == "checked:$prevId" }
                                                    if (prevItemInfo != null && currentItemCenter < (prevItemInfo.offset + prevItemInfo.size / 2)) {
                                                        val targetIdx = currentIdx - 1
                                                        val updated = checkedCategoryIds.toMutableList()
                                                        val item = updated.removeAt(currentIdx)
                                                        updated.add(targetIdx, item)
                                                        checkedCategoryIds = updated
                                                        dragOffsetY += (itemInfo.offset - prevItemInfo.offset)
                                                        return@detectDragGestures
                                                    }
                                                }

                                                // Check next item
                                                if (currentIdx < checkedCategoryIds.size - 1) {
                                                    val nextId = checkedCategoryIds[currentIdx + 1]
                                                    val nextItemInfo = lazyListState.layoutInfo.visibleItemsInfo
                                                        .firstOrNull { it.key == "checked:$nextId" }
                                                    if (nextItemInfo != null && currentItemCenter > (nextItemInfo.offset + nextItemInfo.size / 2)) {
                                                        val targetIdx = currentIdx + 1
                                                        val updated = checkedCategoryIds.toMutableList()
                                                        val item = updated.removeAt(currentIdx)
                                                        updated.add(targetIdx, item)
                                                        checkedCategoryIds = updated
                                                        dragOffsetY -= (nextItemInfo.offset - itemInfo.offset)
                                                        return@detectDragGestures
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                draggingCategoryId = null
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggingCategoryId = null
                                                dragOffsetY = 0f
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }

                    if (uncheckedCategories.isNotEmpty()) {
                        item(key = "header:unchecked") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (checkedCategories.isEmpty()) stringResource(R.string.stores_section_categories) else stringResource(R.string.stores_section_other_categories),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        items(
                            items = uncheckedCategories,
                            key = { "unchecked:${it.id}" }
                        ) { category ->
                            UncheckedCategoryRow(
                                category = category,
                                onCheck = {
                                    checkedCategoryIds = checkedCategoryIds + category.id
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, selectedGroupId, checkedCategoryIds) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.common_save))
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
private fun CheckedCategoryRow(
    category: Category,
    index: Int,
    isDragging: Boolean,
    onUncheck: () -> Unit,
    dragHandleModifier: Modifier = Modifier
) {
    Surface(
        color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onUncheck() }
                .padding(vertical = 4.dp, horizontal = 2.dp)
        ) {
            Checkbox(
                checked = true,
                onCheckedChange = { onUncheck() }
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (category.emoji != null) {
                Text(text = category.emoji, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.common_reorder_name, category.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = dragHandleModifier.padding(8.dp)
            )
        }
    }
}

@Composable
private fun UncheckedCategoryRow(
    category: Category,
    onCheck: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheck() }
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Checkbox(
            checked = false,
            onCheckedChange = { onCheck() }
        )
        Spacer(modifier = Modifier.width(4.dp))
        if (category.emoji != null) {
            Text(text = category.emoji, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Store name + group picker (STORE-7): the group dropdown uses the same
 *  chip-with-menu pattern as the category selector in item detail. */
@Composable
private fun StoreFormDialog(
    title: String,
    confirmText: String,
    initialName: String,
    initialGroupId: String?,
    groupOptions: List<StoreGroup>,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var selectedGroupId by rememberSaveable { mutableStateOf(initialGroupId) }
    var groupMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.stores_name_label)) },
                    singleLine = true
                )
                Spacer(modifier = Modifier.padding(top = 12.dp))
                Text(
                    text = stringResource(R.string.stores_group_label),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.padding(top = 4.dp))
                Box {
                    FilterChip(
                        selected = true,
                        onClick = { groupMenuExpanded = true },
                        label = {
                            Text(
                                selectedGroupId?.let { id ->
                                    groupOptions.firstOrNull { it.id == id }?.name
                                } ?: "No group",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                        expanded = groupMenuExpanded,
                        onDismissRequest = { groupMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.stores_no_group)) },
                            onClick = {
                                selectedGroupId = null
                                groupMenuExpanded = false
                            }
                        )
                        groupOptions.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.name) },
                                onClick = {
                                    selectedGroupId = group.id
                                    groupMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, selectedGroupId) },
                enabled = name.isNotBlank()
            ) {
                Text(confirmText)
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
private fun GroupDialog(
    title: String,
    confirmText: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by rememberSaveable { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.stores_group_name_label)) },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text(confirmText)
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
private fun DeleteStoreDialog(
    storeName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stores_delete_store_title)) },
        text = {
            Text(stringResource(R.string.stores_delete_store_text, storeName))
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
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun DeleteGroupDialog(
    groupName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stores_delete_group_title)) },
        text = {
            Text(stringResource(R.string.stores_delete_group_text, groupName))
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
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
