package net.marvinweber.simsli.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import net.marvinweber.simsli.data.sync.SyncManager
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val syncManager: SyncManager
) : ViewModel() {
    val notice = syncManager.appUpdateNotice

    fun dismiss() = syncManager.dismissAppUpdateNotice()
}

/**
 * App-level "please update" dialog: shown when the server advertises a
 * `min_app_version` newer than the installed app (see [SyncManager]).
 * Advisory only — the server does not reject outdated clients.
 */
@Composable
fun AppUpdateDialog(viewModel: AppUpdateViewModel = hiltViewModel()) {
    val notice by viewModel.notice.collectAsState()
    val current = notice ?: return

    AlertDialog(
        onDismissRequest = viewModel::dismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.SystemUpdateAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }
        },
        title = { Text("Please update Simsli") },
        text = {
            Text(
                "The server expects app version ${current.minAppVersion} or newer, " +
                    "but this device is running ${current.appVersion}. " +
                    "Continuing without updating may lead to unexpected behavior — " +
                    "syncing may not work reliably. " +
                    "Please update the app to keep everything running smoothly."
            )
        },
        confirmButton = {
            TextButton(onClick = viewModel::dismiss) {
                Text("Okay")
            }
        }
    )
}
