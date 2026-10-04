package net.marvinweber.simsli.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.marvinweber.simsli.domain.model.ItemLink

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment

/** Formats a quantity for display: whole numbers without decimals ("2", not "2.0"). */
internal fun formatQuantity(quantity: Double?): String = when {
    quantity == null -> ""
    quantity % 1.0 == 0.0 -> quantity.toInt().toString()
    else -> quantity.toString()
}

/**
 * Bottom sheet for entry-level data — quantity, unit, comment. Shared by the
 * shopping list (editing an existing entry: Save + Remove from list) and the
 * catalog (adding an item to the list: Save + Cancel).
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
    saveButtonText: String = "Save",
    note: String? = null,
    links: List<ItemLink> = emptyList()
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )

            // Read-only catalog note if present
            if (!note.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Catalog note",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Quantity") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = unitText,
                    onValueChange = { unitText = it },
                    label = { Text("Unit") },
                    placeholder = { Text("kg, pack …") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                label = { Text("Comment (optional)") },
                placeholder = { Text("e.g., get the organic one") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            // Combined links from catalog item.links and inline URLs in comment / note
            val allLinks = remember(links, commentText, note) {
                val inlineUrls = (LinkUtils.extractUrls(commentText) + LinkUtils.extractUrls(note)).distinct()
                val existingUrls = links.map { it.url.lowercase() }.toSet()
                val additionalLinks = inlineUrls
                    .filterNot { it.lowercase() in existingUrls }
                    .map { net.marvinweber.simsli.domain.model.ItemLink(url = it, title = null) }
                links + additionalLinks
            }
            if (allLinks.isNotEmpty()) {
                ItemLinkChipsRow(links = allLinks)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onRemove ?: onDismiss
                ) {
                    Text(if (onRemove != null) "Remove from list" else "Cancel")
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
