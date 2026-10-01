package net.marvinweber.simsli.ui.screens.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.hilt.navigation.compose.hiltViewModel
import net.marvinweber.simsli.domain.model.Category

/**
 * The Categories sub-tab of the Catalog tab (CAT-3).
 * Supports creating, editing (name + emoji), deleting (with uncategorize warning),
 * and drag-to-reorder global category order.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CategoriesTabContent(
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val events by viewModel.events.collectAsState()

    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDelete by rememberSaveable { mutableStateOf<Triple<String, String, String?>?>(null) }

    events?.let { event ->
        when (event) {
            is CategoriesUiEvent.ShowError -> {
                // Handled via snackbar in future revisions
                viewModel.onEventConsumed()
            }
            is CategoriesUiEvent.ConfirmDeleteCategory -> {
                pendingDelete = Triple(event.categoryId, event.categoryName, event.categoryEmoji)
                viewModel.onEventConsumed()
            }
        }
    }

    if (showAddDialog) {
        CategoryDialog(
            title = "Add Category",
            confirmText = "Add",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, emoji ->
                viewModel.createCategory(name, emoji)
                showAddDialog = false
            }
        )
    }

    val editingCategory = uiState.categories.firstOrNull { it.id == editingCategoryId }
    if (editingCategory != null) {
        CategoryDialog(
            title = "Edit Category",
            confirmText = "Save",
            initialName = editingCategory.name,
            initialEmoji = editingCategory.emoji,
            onDismiss = { editingCategoryId = null },
            onConfirm = { name, emoji ->
                viewModel.updateCategory(editingCategory.id, name, emoji)
                editingCategoryId = null
            }
        )
    }

    pendingDelete?.let { (id, name, emoji) ->
        DeleteCategoryDialog(
            categoryName = name,
            categoryEmoji = emoji,
            onConfirm = {
                viewModel.confirmDeleteCategory(id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Category")
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
                uiState.categories.isEmpty() -> {
                    Text(
                        text = "No categories yet. Tap + to add one.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                }
                else -> {
                    CategoriesList(
                        categories = uiState.categories,
                        onEditCategory = { editingCategoryId = it.id },
                        onDeleteCategory = { viewModel.onDeleteCategory(it) },
                        onReorder = { viewModel.onReorder(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoriesList(
    categories: List<Category>,
    onEditCategory: (Category) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onReorder: (List<Category>) -> Unit
) {
    var localCategories by remember(categories) { mutableStateOf(categories) }
    var draggingCategoryId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val lazyListState = rememberLazyListState()

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = localCategories,
            key = { it.id }
        ) { category ->
            val isDragging = category.id == draggingCategoryId
            CategoryCard(
                category = category,
                onEdit = { onEditCategory(category) },
                onDelete = { onDeleteCategory(category.id) },
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
                            val currentIdx = localCategories.indexOfFirst { it.id == currentId }
                            if (currentIdx == -1) return@detectDragGestures

                            val itemInfo = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == currentId }
                                ?: return@detectDragGestures

                            val currentItemCenter = itemInfo.offset + itemInfo.size / 2 + dragOffsetY

                            // Check previous item
                            if (currentIdx > 0) {
                                val prevCategory = localCategories[currentIdx - 1]
                                val prevItemInfo = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == prevCategory.id }
                                if (prevItemInfo != null && currentItemCenter < (prevItemInfo.offset + prevItemInfo.size / 2)) {
                                    val targetIdx = currentIdx - 1
                                    val updated = localCategories.toMutableList()
                                    val item = updated.removeAt(currentIdx)
                                    updated.add(targetIdx, item)
                                    localCategories = updated
                                    dragOffsetY += (itemInfo.offset - prevItemInfo.offset)
                                    return@detectDragGestures
                                }
                            }

                            // Check next item
                            if (currentIdx < localCategories.size - 1) {
                                val nextCategory = localCategories[currentIdx + 1]
                                val nextItemInfo = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == nextCategory.id }
                                if (nextItemInfo != null && currentItemCenter > (nextItemInfo.offset + nextItemInfo.size / 2)) {
                                    val targetIdx = currentIdx + 1
                                    val updated = localCategories.toMutableList()
                                    val item = updated.removeAt(currentIdx)
                                    updated.add(targetIdx, item)
                                    localCategories = updated
                                    dragOffsetY -= (nextItemInfo.offset - itemInfo.offset)
                                    return@detectDragGestures
                                }
                            }
                        },
                        onDragEnd = {
                            val movedId = draggingCategoryId
                            draggingCategoryId = null
                            dragOffsetY = 0f
                            if (movedId != null) {
                                if (categories.map { it.id } != localCategories.map { it.id }) {
                                    onReorder(localCategories)
                                }
                            }
                        },
                        onDragCancel = {
                            draggingCategoryId = null
                            dragOffsetY = 0f
                            localCategories = categories
                        }
                    )
                },
                modifier = if (isDragging) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer { translationY = dragOffsetY }
                        .shadow(8.dp, shape = MaterialTheme.shapes.medium)
                } else {
                    Modifier
                        .zIndex(0f)
                        .animateItem()
                }
            )
        }
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = dragHandleModifier
                    .size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Reorder ${category.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            if (!category.emoji.isNullOrBlank()) {
                Text(
                    text = category.emoji,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }

            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete category",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun CategoryDialog(
    title: String,
    confirmText: String,
    initialName: String = "",
    initialEmoji: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, emoji: String?) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var emoji by rememberSaveable { mutableStateOf(initialEmoji ?: "") }
    var showEmojiPicker by rememberSaveable { mutableStateOf(false) }

    if (showEmojiPicker) {
        Dialog(
            onDismissRequest = { showEmojiPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.7f),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Choose Icon",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row {
                            if (emoji.isNotBlank()) {
                                TextButton(onClick = {
                                    emoji = ""
                                    showEmojiPicker = false
                                }) {
                                    Text("Remove")
                                }
                            }
                            TextButton(onClick = { showEmojiPicker = false }) {
                                Text("Close")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            EmojiPickerView(context).apply {
                                setOnEmojiPickedListener { item ->
                                    emoji = item.emoji
                                    showEmojiPicker = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { showEmojiPicker = true },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (emoji.isNotBlank()) {
                            Text(
                                text = emoji,
                                style = MaterialTheme.typography.headlineMedium
                            )
                        } else {
                            Text(
                                text = "🏷️",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, emoji) },
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
private fun DeleteCategoryDialog(
    categoryName: String,
    categoryEmoji: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val displayLabel = if (!categoryEmoji.isNullOrBlank()) "$categoryEmoji $categoryName" else categoryName
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Category?") },
        text = {
            Text("Delete \"$displayLabel\"? Items in this category will become uncategorized.")
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
