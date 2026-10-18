package net.marvinweber.simsli.ui.components

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.ui.res.stringResource
import net.marvinweber.simsli.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.marvinweber.simsli.domain.model.ItemLink

/**
 * The shared middle of both entry modals — the entry editor
 * ([EntryDetailsSheet]) and the add-to-list form (AddItemsSheet): quantity +
 * unit dropdown, comment, plus the read-only catalog note and link chips.
 * Callers own the text state so their buttons can read the current values.
 */
@Composable
fun EntryFormFields(
    quantityText: String,
    onQuantityChange: (String) -> Unit,
    unitText: String,
    onUnitChange: (String) -> Unit,
    commentText: String,
    onCommentChange: (String) -> Unit,
    note: String? = null,
    links: List<ItemLink> = emptyList()
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                            text = stringResource(R.string.entry_note_label),
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
                onValueChange = onQuantityChange,
                label = { Text(stringResource(R.string.entry_quantity)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            UnitDropdownField(
                value = unitText,
                onValueChange = onUnitChange,
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = commentText,
            onValueChange = onCommentChange,
            label = { Text(stringResource(R.string.entry_comment)) },
            placeholder = { Text(stringResource(R.string.entry_comment_placeholder)) },
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
                .map { ItemLink(url = it, title = null) }
            links + additionalLinks
        }
        if (allLinks.isNotEmpty()) {
            ItemLinkChipsRow(links = allLinks)
        }
    }
}
