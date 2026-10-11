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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
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
    viewModel: StoresViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val events by viewModel.events.collectAsState()

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
        StoreEditDialog(
            store = editingStore,
            groupOptions = uiState.storeGroups,
            onDismiss = { editingStoreId = null },
            onConfirm = { name, groupId ->
                viewModel.updateStore(editingStore.id, name, groupId)
                editingStoreId = null
            }
        )
    }

    if (showAddGroupDialog) {
        GroupDialog(
            title = "Add Group",
            confirmText = "Add",
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
            title = "Rename Group",
            confirmText = "Save",
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
                Icon(Icons.Default.Add, contentDescription = "Add Store")
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
                            text = "No stores yet. Tap + to add one.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                else -> {
                    StoresList(
                        storeGroups = uiState.storeGroups,
                        stores = uiState.stores,
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
                        dragHandleModifier = Modifier.pointerInput(group.id) {
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
                    Text("Add group")
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
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Reorder ${group.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    contentDescription = "Delete group",
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
                    contentDescription = "Delete store",
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
        title = "Add Store",
        confirmText = "Add",
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
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    StoreFormDialog(
        title = "Edit Store",
        confirmText = "Save",
        initialName = store.name,
        initialGroupId = store.groupId,
        groupOptions = groupOptions,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
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
                    label = { Text("Store name") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.padding(top = 12.dp))
                Text(
                    text = "Group",
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
                            text = { Text("No group") },
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
                Text("Cancel")
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
                label = { Text("Group name") },
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
                Text("Cancel")
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
        title = { Text("Delete Store?") },
        text = {
            Text("Delete \"$storeName\"? Items assigned only to this store will become unassigned.")
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

@Composable
private fun DeleteGroupDialog(
    groupName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Group?") },
        text = {
            Text(
                "Delete \"$groupName\"? Its stores become ungrouped (not deleted) and items " +
                    "assigned via this group lose that coverage. Individual store exclusions stay."
            )
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
