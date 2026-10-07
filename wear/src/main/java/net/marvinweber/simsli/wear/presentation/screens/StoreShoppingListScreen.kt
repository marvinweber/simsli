package net.marvinweber.simsli.wear.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import net.marvinweber.simsli.wear.common.WearShoppingItem

@Composable
fun StoreShoppingListScreen(
    storeId: String?,
    storeName: String,
    items: List<WearShoppingItem>,
    onCheckItem: (entryId: String) -> Unit
) {
    val haptic = LocalHapticFeedback.current

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

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (activeCount == 0) "All done! 🎉" else "$activeCount remaining",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activeCount == 0) Color(0xFF81C784) else Color.LightGray
                    )
                }
            }
        }

        items(sortedItems, key = { it.entryId }) { item ->
            val detailsText = formatItemDetails(item)

            Button(
                onClick = {
                    if (!item.isDone) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCheckItem(item.entryId)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                colors = if (item.isDone) {
                    ButtonDefaults.filledTonalButtonColors()
                } else {
                    ButtonDefaults.buttonColors()
                },
                label = {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.labelMedium,
                        textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                        color = if (item.isDone) Color.Gray else Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                secondaryLabel = detailsText?.let {
                    {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.isDone) Color.DarkGray else Color.LightGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                icon = {
                    if (item.isDone) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = Color(0xFF81C784)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Mark done",
                            tint = Color.LightGray
                        )
                    }
                }
            )
        }
    }
}

private fun formatItemDetails(item: WearShoppingItem): String? {
    val parts = mutableListOf<String>()

    val quantityStr = item.quantity?.let { q ->
        if (q % 1.0 == 0.0) q.toLong().toString() else q.toString()
    }
    val unit = item.unit
    val comment = item.comment

    if (quantityStr != null && unit != null) {
        parts.add("$quantityStr $unit")
    } else if (quantityStr != null) {
        parts.add(quantityStr)
    } else if (unit != null) {
        parts.add(unit)
    }

    if (!comment.isNullOrBlank()) {
        parts.add(comment)
    }

    return if (parts.isEmpty()) null else parts.joinToString(" • ")
}
