package net.marvinweber.simsli.ui.screens.settings

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val renameValue = uiState.renameInput
        if (renameValue != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissRename,
                title = { Text("Rename household") },
                text = {
                    OutlinedTextField(
                        value = renameValue,
                        onValueChange = viewModel::onRenameChange,
                        label = { Text("Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = viewModel::confirmRename,
                        enabled = renameValue.isNotBlank()
                    ) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissRename) {
                        Text("Cancel")
                    }
                }
            )
        }
        val inviteCode = uiState.inviteCode
        if (inviteCode != null) {
            val clipboard = LocalClipboard.current
            val scope = rememberCoroutineScope()
            AlertDialog(
                onDismissRequest = viewModel::dismissInvite,
                title = { Text("Invite someone") },
                text = {
                    Column {
                        Text(
                            text = "Share this code. It works once and expires after 24 hours.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = inviteCode,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipData.newPlainText("Simsli invite code", inviteCode).toClipEntry()
                            )
                        }
                    }) {
                        Text("Copy code")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissInvite) {
                        Text("Close")
                    }
                }
            )
        }
        val joinValue = uiState.joinInput
        if (joinValue != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissJoin,
                title = { Text("Join household") },
                text = {
                    OutlinedTextField(
                        value = joinValue,
                        onValueChange = viewModel::onJoinChange,
                        label = { Text("Invite code") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = viewModel::confirmJoin,
                        enabled = joinValue.isNotBlank()
                    ) {
                        Text("Join")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissJoin) {
                        Text("Cancel")
                    }
                }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Household", style = MaterialTheme.typography.titleMedium)
            Text(
                text = uiState.householdName ?: "Not set up yet",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(
                onClick = { viewModel.startRename(uiState.householdName) },
                enabled = uiState.householdName != null && !uiState.isBusy
            ) {
                Text("Rename")
            }
            OutlinedButton(
                onClick = viewModel::startInvite,
                enabled = !uiState.isBusy
            ) {
                Text("Invite someone")
            }
            OutlinedButton(
                onClick = viewModel::startJoin,
                enabled = !uiState.isBusy
            ) {
                Text("Join household")
            }

            HorizontalDivider()

            Text("Account", style = MaterialTheme.typography.titleMedium)
            if (uiState.isSignedIn) {
                Text(
                    text = "Signed in as ${uiState.userEmail ?: "unknown"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    onClick = viewModel::signOut,
                    enabled = !uiState.isBusy
                ) {
                    Text("Sign out")
                }
            } else {
                Text(
                    text = "Sign in to sync your list across devices.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = uiState.emailInput,
                    onValueChange = viewModel::onEmailChange,
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !uiState.isBusy,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = viewModel::sendMagicLink,
                    enabled = !uiState.isBusy
                ) {
                    Text("Send magic link")
                }
            }

            HorizontalDivider()

            Text("Sync", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = viewModel::syncNow,
                enabled = !uiState.isBusy
            ) {
                Text("Sync now")
            }
            if (uiState.isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
            uiState.statusMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
