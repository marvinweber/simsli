package net.marvinweber.simsli.ui.screens.household

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import net.marvinweber.simsli.ui.components.EmojiPickerDialog
import net.marvinweber.simsli.ui.components.SimsliTopAppBarTitle

/**
 * Full household management screen (SCREENS-3):
 * Details, rename & emoji, members list, invite creation, full resync,
 * and visually separated join household card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHouseholdMembers: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HouseholdViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val hasPendingWrites by viewModel.hasPendingWrites.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage
        if (message != null) {
            val isError = message.contains("failed", ignoreCase = true) ||
                    message.contains("error", ignoreCase = true) ||
                    message.contains("denied", ignoreCase = true) ||
                    message.contains("couldn't", ignoreCase = true)
            val actionLabel = if (isError) "Copy" else null
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = true,
                duration = if (isError) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed && isError) {
                clipboard.setClipEntry(
                    ClipData.newPlainText("Simsli error", message).toClipEntry()
                )
                snackbarHostState.showSnackbar(
                    message = "Error copied to clipboard",
                    withDismissAction = true,
                    duration = SnackbarDuration.Short
                )
            }
            viewModel.dismissStatusMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = {
                    SimsliTopAppBarTitle(
                        title = "Household",
                        isSyncing = isSyncing,
                        isOffline = isOffline,
                        hasPendingWrites = hasPendingWrites
                    )
                }
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 8.dp),
                snackbar = { data ->
                    SwipeToDismissBox(
                        state = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value != SwipeToDismissBoxValue.Settled) {
                                    data.dismiss()
                                    true
                                } else {
                                    false
                                }
                            }
                        ),
                        backgroundContent = {},
                        enableDismissFromStartToEnd = true,
                        enableDismissFromEndToStart = true
                    ) {
                        Snackbar(
                            snackbarData = data,
                            actionOnNewLine = data.visuals.actionLabel != null,
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            actionColor = MaterialTheme.colorScheme.primary,
                            actionContentColor = MaterialTheme.colorScheme.primary,
                            dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->

        // --- Dialogs ---

        val renameValue = uiState.renameInput
        if (renameValue != null) {
            val currentIcon = uiState.householdIcon
            var emoji by rememberSaveable { mutableStateOf(currentIcon ?: "") }
            var showEmojiPicker by rememberSaveable { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = viewModel::dismissRename,
                icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                title = { Text("Edit household") },
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { showEmojiPicker = true },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (emoji.isNotBlank()) {
                                    Text(
                                        text = emoji,
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                } else {
                                    Text(
                                        text = "🏠",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = renameValue,
                            onValueChange = viewModel::onRenameChange,
                            label = { Text("Household name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.confirmRename(emoji.ifBlank { null }) },
                        enabled = renameValue.isNotBlank() &&
                                (renameValue != uiState.householdName || emoji != (currentIcon ?: ""))
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissRename) {
                        Text("Cancel")
                    }
                }
            )

            if (showEmojiPicker) {
                EmojiPickerDialog(
                    initialEmoji = emoji,
                    onPicked = { picked ->
                        emoji = picked.orEmpty()
                        showEmojiPicker = false
                    },
                    onDismiss = { showEmojiPicker = false }
                )
            }
        }

        val inviteCode = uiState.inviteCode
        if (inviteCode != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissInvite,
                icon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null) },
                title = { Text("Invite to household") },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Share this invite link or 8-character code. It works once and expires after 24 hours.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = inviteCode,
                                    style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 4.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "simsli://invite/$inviteCode",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        clipboard.setClipEntry(
                                            ClipData.newPlainText("Simsli invite code", inviteCode).toClipEntry()
                                        )
                                        snackbarHostState.showSnackbar("Invite code copied to clipboard")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy")
                            }
                            Button(
                                onClick = {
                                    val shareText = "Join my household on Simsli: simsli://invite/$inviteCode (code: $inviteCode, valid for 24 hours)"
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share invite link"))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissInvite) {
                        Text("Done")
                    }
                }
            )
        }

        if (uiState.showFullResyncConfirm) {
            AlertDialog(
                onDismissRequest = viewModel::dismissFullResync,
                icon = {
                    Icon(
                        Icons.Outlined.CloudQueue,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text("Full resync?") },
                text = {
                    Text(
                        "Pending changes are uploaded first, then local data is wiped and everything is re-downloaded from the server. The server state wins."
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmFullResync) {
                        Text("Resync")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissFullResync) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (uiState.showJoinWarning) {
            AlertDialog(
                onDismissRequest = viewModel::dismissJoinWarning,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text("Join another household?") },
                text = {
                    val householdName = uiState.householdName ?: "your current household"
                    val codePrefix = uiState.pendingJoinCode?.let { "You are about to join another household with invite code $it.\n\n" }.orEmpty()
                    val warningBody = if (uiState.isOwner) {
                        "Warning: Joining another household will delete \"$householdName\" and all its data. This cannot be undone."
                    } else {
                        "Warning: Joining another household will remove you from \"$householdName\". You will lose access to its list and items."
                    }
                    val question = "\n\nDo you want to continue?"
                    Text(
                        text = "$codePrefix$warningBody$question",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::confirmJoinWarning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(if (uiState.pendingJoinCode != null) "Join" else "Continue")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissJoinWarning) {
                        Text("Cancel")
                    }
                }
            )
        }

        val joinValue = uiState.joinInput
        if (joinValue != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissJoin,
                icon = { Icon(Icons.Outlined.GroupAdd, contentDescription = null) },
                title = { Text("Join household") },
                text = {
                    Column {
                        Text(
                            text = "Enter the 8-character invite code to join an existing household.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = joinValue,
                            onValueChange = { viewModel.onJoinChange(it.uppercase()) },
                            label = { Text("Invite code") },
                            placeholder = { Text("e.g. A2B3C4D5") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
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

        // --- Main Content ---

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Household Overview & Edit Card ---
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            val householdIcon = uiState.householdIcon
                            if (!householdIcon.isNullOrBlank()) {
                                Text(
                                    text = householdIcon,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Home,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = uiState.householdName ?: "My Household",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val statusLabel = when {
                                !uiState.isSignedIn -> "Local household (offline)"
                                uiState.isOwner -> "Shared household • Owner"
                                else -> "Shared household • Member"
                            }
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (uiState.householdId != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            scope.launch {
                                                clipboard.setClipEntry(
                                                    ClipData.newPlainText("Simsli Household ID", uiState.householdId).toClipEntry()
                                                )
                                                snackbarHostState.showSnackbar("Household ID copied to clipboard")
                                            }
                                        }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "ID: ${uiState.householdId}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = "Copy household ID",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = { viewModel.startRename(uiState.householdName) },
                            enabled = !uiState.isBusy && uiState.isOwner
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit household")
                        }
                    }

                    if (uiState.isSignedIn) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // Members row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !uiState.isBusy, onClick = onNavigateToHouseholdMembers)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Group,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Household members",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (uiState.memberCount > 0) {
                                        "${uiState.memberCount} member" + if (uiState.memberCount != 1) "s" else ""
                                    } else {
                                        "View members"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // Invite row
                        val canInvite = uiState.canInvite
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !uiState.isBusy && canInvite, onClick = viewModel::startInvite)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PersonAdd,
                                contentDescription = null,
                                tint = if (canInvite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Invite someone",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = if (canInvite) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                                Text(
                                    text = if (canInvite) {
                                        "Share an invite code to add a member"
                                    } else {
                                        uiState.inviteDisabledReason ?: "Only owners and admins can invite members"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (canInvite) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                            if (canInvite) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // --- Maintenance & Sync Recovery Card ---
            if (uiState.isSignedIn) {
                Text(
                    text = "MAINTENANCE & RECOVERY",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudQueue,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Full resync",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Wipes local data and re-downloads everything from the server — recovery if the list ever looks wrong",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = viewModel::startFullResync,
                            enabled = !uiState.isBusy && !isSyncing,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Resync")
                        }
                    }
                }
            }

            // --- Visually Separated: Join Another Household Card ---
            Text(
                text = "SWITCH HOUSEHOLD",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.GroupAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Join another household",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Switch this device to a different household",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Joining another household will leave or delete your current household and replace local data with the joined household.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val canJoin = uiState.canJoin
                    OutlinedButton(
                        onClick = {
                            if (!uiState.isSignedIn) {
                                onNavigateToSettings()
                            } else {
                                viewModel.startJoin()
                            }
                        },
                        enabled = !uiState.isBusy && (canJoin || !uiState.isSignedIn),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.GroupAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (!uiState.isSignedIn) "Sign in first to join" else "Join with invite code"
                        )
                    }

                    if (uiState.joinDisabledReason != null && uiState.isSignedIn) {
                        Text(
                            text = uiState.joinDisabledReason!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
