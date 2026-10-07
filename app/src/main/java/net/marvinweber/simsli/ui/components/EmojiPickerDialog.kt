package net.marvinweber.simsli.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.emoji2.emojipicker.EmojiPickerView

/**
 * Full-screen-ish emoji picker dialog (ADR-0014). Reports the picked emoji via
 * [onPicked]; "Remove" (only shown when [initialEmoji] is non-blank) reports null.
 */
@Composable
fun EmojiPickerDialog(
    initialEmoji: String?,
    onPicked: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
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
                        if (!initialEmoji.isNullOrBlank()) {
                            TextButton(onClick = { onPicked(null) }) {
                                Text("Remove")
                            }
                        }
                        TextButton(onClick = onDismiss) {
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
                                onPicked(item.emoji)
                            }
                        }
                    }
                )
            }
        }
    }
}
