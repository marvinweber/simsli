package net.marvinweber.simsli.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.marvinweber.simsli.R
import net.marvinweber.simsli.domain.model.UnitPresets

/**
 * Read-only outlined unit field with an exposed dropdown — the one unit
 * selector for list entries (entry editor, add-item sheet): None, the unit
 * presets, and Custom… with a free-text dialog. The current value shows in
 * the field even when custom (its menu entry then reads "Edit custom unit…").
 * Preselect by passing the entry's or item's unit as [value].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var customInput by remember { mutableStateOf("") }

    ExposedDropdownMenuBox(
        expanded = menuExpanded,
        onExpandedChange = { menuExpanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.item_unit)) },
            placeholder = { Text(stringResource(R.string.unit_field_placeholder)) },
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded)
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.unit_none)) },
                onClick = {
                    onValueChange("")
                    menuExpanded = false
                },
                leadingIcon = {
                    if (value.isBlank()) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            )
            UnitPresets.ALL.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(unitPresetLabel(preset)) },
                    onClick = {
                        onValueChange(preset)
                        menuExpanded = false
                    },
                    leadingIcon = {
                        if (value.equals(preset, ignoreCase = true)) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                )
            }
            HorizontalDivider()
            val isPreset = UnitPresets.ALL.any { it.equals(value, ignoreCase = true) }
            DropdownMenuItem(
                text = {
                    Text(
                        if (isPreset || value.isBlank()) stringResource(R.string.unit_custom)
                        else stringResource(R.string.unit_edit_custom)
                    )
                },
                onClick = {
                    // Prefill only when editing an existing custom value
                    customInput = if (isPreset || value.isBlank()) "" else value
                    menuExpanded = false
                    showCustomDialog = true
                }
            )
        }
    }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text(stringResource(R.string.unit_custom_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { customInput = it },
                    label = { Text(stringResource(R.string.item_unit)) },
                    placeholder = { Text(stringResource(R.string.unit_custom_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onValueChange(customInput.trim())
                        showCustomDialog = false
                    },
                    enabled = customInput.isNotBlank()
                ) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
