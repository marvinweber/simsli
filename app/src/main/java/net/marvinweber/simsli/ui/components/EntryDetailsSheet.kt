package net.marvinweber.simsli.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.marvinweber.simsli.R
import net.marvinweber.simsli.domain.model.ItemLink
import java.text.NumberFormat
import java.util.Locale

/** Formats a quantity for display: whole numbers without decimals ("2", not "2.0"), locale-aware decimal separator. */
internal fun formatQuantity(quantity: Double?): String = when {
    quantity == null -> ""
    quantity % 1.0 == 0.0 -> quantity.toInt().toString()
    else -> NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        isGroupingUsed = false
        maximumFractionDigits = 340
    }.format(quantity)
}

/**
 * Bottom sheet for entry-level data — quantity, unit, comment. Shared by the
 * shopping list (editing an existing entry: Save + Remove from list) and the
 * catalog (adding an item to the list: Save + Cancel). The fields themselves
 * live in the shared [EntryFormFields], also used by the list's quick-add form.
 *
 * [stateKey] resets the fields whenever it changes, so consecutive uses for
 * different entries/items never inherit text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailsSheet(
    title: String,
    stateKey: Any?,
    quantity: Double?,
    unit: String?,
    comment: String?,
    onDismiss: () -> Unit,
    onSave: (quantityText: String, unit: String, comment: String) -> Unit,
    onRemove: (() -> Unit)? = null,
    saveButtonText: String = stringResource(R.string.common_save),
    note: String? = null,
    links: List<ItemLink> = emptyList(),
    onOpenItemDetails: (() -> Unit)? = null
) {
    var quantityText by remember(stateKey) { mutableStateOf(formatQuantity(quantity)) }
    var unitText by remember(stateKey) { mutableStateOf(unit.orEmpty()) }
    var commentText by remember(stateKey) { mutableStateOf(comment.orEmpty()) }

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title + chevron into the item's catalog detail screen (null for
            // entries without a catalog item); the whole title is the target
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onOpenItemDetails != null) {
                            Modifier.clickable(onClick = onOpenItemDetails)
                        } else {
                            Modifier
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (onOpenItemDetails != null) {
                    Box(modifier = Modifier.padding(8.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.common_edit_item_details),
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            EntryFormFields(
                quantityText = quantityText,
                onQuantityChange = { quantityText = it },
                unitText = unitText,
                onUnitChange = { unitText = it },
                commentText = commentText,
                onCommentChange = { commentText = it },
                note = note,
                links = links
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onRemove ?: onDismiss
                ) {
                    Text(if (onRemove != null) stringResource(R.string.common_remove_from_list) else stringResource(R.string.common_cancel))
                }
                Button(
                    onClick = { onSave(quantityText, unitText, commentText) }
                ) {
                    Text(saveButtonText)
                }
            }
        }
    }
}
