package net.marvinweber.simsli.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
    onRemove: (() -> Unit)? = null
) {
    var quantityText by remember(stateKey) { mutableStateOf(formatQuantity(quantity)) }
    var unitText by remember(stateKey) { mutableStateOf(unit.orEmpty()) }
    var commentText by remember(stateKey) { mutableStateOf(comment.orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onRemove ?: onDismiss
                ) {
                    Text(if (onRemove != null) "Remove from list" else "Cancel")
                }
                OutlinedButton(
                    onClick = { onSave(quantityText, unitText, commentText) }
                ) {
                    Text("Save")
                }
            }
        }
    }
}
