package net.marvinweber.simsli.wear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CheckboxButtonDefaults
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SplitCheckboxButton
import androidx.wear.compose.material3.Text
import net.marvinweber.simsli.wear.common.WearShoppingItem

@Composable
fun StoreShoppingListScreen(
    storeId: String?,
    storeName: String,
    items: List<WearShoppingItem>,
    onToggleItem: (entryId: String, done: Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var detailItem by remember { mutableStateOf<WearShoppingItem?>(null) }

    // Filter items for store
    val storeItems = if (storeId == null) {
        items
    } else {
        items.filter { it.storeIds.contains(storeId) }
    }

    // Sort: pending first, completed last
    val sortedItems = storeItems.sortedBy { it.isDone }
    val activeCount = storeItems.count { !it.isDone }

    if (storeItems.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = storeName,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = "No items for this store",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        return
    }

    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .rotaryScrollable(
                behavior = RotaryScrollableDefaults.snapBehavior(listState),
                focusRequester = focusRequester
            ),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            ListHeader(
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = storeName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = if (activeCount == 0) "All done! 🎉" else "$activeCount remaining",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activeCount == 0) Color(0xFF81C784) else Color(0xFFB0BEC5)
                    )
                }
            }
        }

        items(sortedItems, key = { it.entryId }) { item ->
            val detailsText = formatItemDetails(item)

            SplitCheckboxButton(
                checked = item.isDone,
                onCheckedChange = { checked ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleItem(item.entryId, checked)
                },
                toggleContentDescription = if (item.isDone) "Mark as active" else "Mark as done",
                onContainerClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    detailItem = item
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                colors = CheckboxButtonDefaults.splitCheckboxButtonColors(),
                label = {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.labelMedium,
                        textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                        color = if (item.isDone) Color(0xFF888888) else Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                secondaryLabel = detailsText?.let {
                    {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.isDone) Color(0xFF555555) else Color(0xFFB0BEC5),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            )
        }
    }

    // WEAR-7 Item details popup
    val currentDetail = detailItem
    if (currentDetail != null) {
        Dialog(
            visible = true,
            onDismissRequest = { detailItem = null }
        ) {
            val detailListState = rememberScalingLazyListState()
            val detailFocusRequester = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                detailFocusRequester.requestFocus()
            }

            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .rotaryScrollable(
                        behavior = RotaryScrollableDefaults.snapBehavior(detailListState),
                        focusRequester = detailFocusRequester
                    ),
                state = detailListState,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    ListHeader(modifier = Modifier.padding(bottom = 2.dp)) {
                        Text(
                            text = "Item Details",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA5D6A7)
                        )
                    }
                }

                item {
                    Text(
                        text = currentDetail.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                    )
                }

                val qtyStr = formatQuantityUnit(currentDetail.quantity, currentDetail.unit)
                if (qtyStr != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .background(Color(0xFF1E2822), shape = RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = qtyStr,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF81C784)
                            )
                        }
                    }
                }

                val comment = currentDetail.comment
                if (!comment.isNullOrBlank()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .background(Color(0xFF1E2225), shape = RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Comment",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFA5D6A7),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = comment,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                val notes = currentDetail.notes
                if (!notes.isNullOrBlank()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .background(Color(0xFF1E2225), shape = RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Notes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFA5D6A7),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                if (qtyStr == null && currentDetail.comment.isNullOrBlank() && currentDetail.notes.isNullOrBlank()) {
                    item {
                        Text(
                            text = "No additional details",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF888888),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                item {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleItem(currentDetail.entryId, !currentDetail.isDone)
                            detailItem = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentDetail.isDone) Color(0xFF263238) else Color(0xFF1E2822),
                            contentColor = if (currentDetail.isDone) Color.White else Color(0xFF81C784)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (currentDetail.isDone) "Mark as active" else "Mark as done",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                item {
                    FilledTonalButton(
                        onClick = { detailItem = null },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF141618),
                            contentColor = Color(0xFFB0BEC5)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Close",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

private fun formatQuantityUnit(quantity: Double?, unit: String?): String? {
    val quantityStr = quantity?.let { q ->
        if (q % 1.0 == 0.0) q.toLong().toString() else q.toString()
    }
    return when {
        quantityStr != null && unit != null -> "$quantityStr $unit"
        quantityStr != null -> quantityStr
        unit != null -> unit
        else -> null
    }
}

private fun formatItemDetails(item: WearShoppingItem): String? {
    val parts = mutableListOf<String>()
    val qtyUnit = formatQuantityUnit(item.quantity, item.unit)
    if (qtyUnit != null) parts.add(qtyUnit)
    val comment = item.comment
    val notes = item.notes
    if (!comment.isNullOrBlank()) {
        parts.add(comment)
    } else if (!notes.isNullOrBlank()) {
        parts.add(notes)
    }
    return if (parts.isEmpty()) null else parts.joinToString(" • ")
}
