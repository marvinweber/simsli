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
import androidx.compose.ui.res.stringResource
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
import net.marvinweber.simsli.domain.model.CurrencyPresets
import net.marvinweber.simsli.R
import net.marvinweber.simsli.ui.components.CurrencyPickerDialog
import net.marvinweber.simsli.ui.resolve
import net.marvinweber.simsli.ui.text
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
            val text = message.resolve(context)
            val actionLabel = if (message.isError) context.getString(R.string.common_copy) else null
            val result = snackbarHostState.showSnackbar(
                message = text,
                actionLabel = actionLabel,
                withDismissAction = true,
                duration = if (message.isError) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed && message.isError) {
                clipboard.setClipEntry(
                    ClipData.newPlainText("Simsli error", text).toClipEntry()
                )
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.common_error_copied),
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
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                title = {
                    SimsliTopAppBarTitle(
                        title = stringResource(R.string.hh_title),
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
            val currentCurrency = uiState.householdCurrency
            var emoji by rememberSaveable { mutableStateOf(currentIcon ?: "") }
            var currency by rememberSaveable { mutableStateOf(currentCurrency) }
            var showEmojiPicker by rememberSaveable { mutableStateOf(false) }
            var showCurrencyPicker by rememberSaveable { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = viewModel::dismissRename,
                icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                title = { Text(stringResource(R.string.hh_edit_title)) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
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
                                label = { Text(stringResource(R.string.hh_name_label)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            onClick = { showCurrencyPicker = true },
                            enabled = !uiState.isBusy && uiState.isOwner,
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.hh_currency),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (uiState.isOwner) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "$currency ${CurrencyPresets.symbolFor(currency)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.confirmRename(emoji.ifBlank { null }, currency) },
                        enabled = renameValue.isNotBlank() &&
                                (renameValue != uiState.householdName ||
                                        emoji != (currentIcon ?: "") ||
                                        currency != currentCurrency)
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissRename) {
                        Text(stringResource(R.string.common_cancel))
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

            if (showCurrencyPicker) {
                CurrencyPickerDialog(
                    currentCode = currency,
                    onPicked = { picked ->
                        currency = picked
                        showCurrencyPicker = false
                    },
                    onDismiss = { showCurrencyPicker = false }
                )
            }
        }

        val inviteCode = uiState.inviteCode
        if (inviteCode != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissInvite,
                icon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null) },
                title = { Text(stringResource(R.string.hh_invite_title)) },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.hh_invite_hint),
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
                                        snackbarHostState.showSnackbar(context.getString(R.string.hh_invite_code_copied))
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.common_copy))
                            }
                            Button(
                                onClick = {
                                    val shareText = context.getString(
                                        R.string.hh_share_invite_text,
                                        "simsli://invite/$inviteCode",
                                        inviteCode
                                    )
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.hh_share_invite_chooser)))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.common_share))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissInvite) {
                        Text(stringResource(R.string.common_done))
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
                title = { Text(stringResource(R.string.hh_resync_title)) },
                text = {
                    Text(
                        "Pending changes are uploaded first, then local data is wiped and everything is re-downloaded from the server. The server state wins."
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmFullResync) {
                        Text(stringResource(R.string.common_resync))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissFullResync) {
                        Text(stringResource(R.string.common_cancel))
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
                title = { Text(stringResource(R.string.hh_join_other_title)) },
                text = {
                    val codePrefix = uiState.pendingJoinCode?.let {
                        stringResource(R.string.hh_join_code_prefix, it)
                    }.orEmpty()
                    val warningBody = if (uiState.isOwner) {
                        stringResource(
                            R.string.hh_join_warning_owner,
                            uiState.householdName ?: stringResource(R.string.hh_current_household_nom)
                        )
                    } else {
                        stringResource(
                            R.string.hh_join_warning_member,
                            uiState.householdName ?: stringResource(R.string.hh_current_household_acc)
                        )
                    }
                    val question = stringResource(R.string.hh_join_question)
                    Text(
                        text = codePrefix + warningBody + question,
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
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }

        val joinValue = uiState.joinInput
        if (joinValue != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissJoin,
                icon = { Icon(Icons.Outlined.GroupAdd, contentDescription = null) },
                title = { Text(stringResource(R.string.hh_join_title)) },
                text = {
                    Column {
                        Text(
                            text = stringResource(R.string.hh_join_enter_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = joinValue,
                            onValueChange = { viewModel.onJoinChange(it.uppercase()) },
                            label = { Text(stringResource(R.string.hh_invite_code_label)) },
                            placeholder = { Text(stringResource(R.string.hh_invite_code_placeholder)) },
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
                        Text(stringResource(R.string.common_join))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissJoin) {
                        Text(stringResource(R.string.common_cancel))
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
                                text = uiState.householdName ?: stringResource(R.string.hh_my_household),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val statusLabel = when {
                                !uiState.isSignedIn -> stringResource(R.string.hh_local_household_status)
                                uiState.isOwner -> stringResource(R.string.hh_shared_owner_status)
                                else -> stringResource(R.string.hh_shared_member_status)
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
                                                snackbarHostState.showSnackbar(context.getString(R.string.hh_household_id_copied))
                                            }
                                        }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.hh_id_label, uiState.householdId.orEmpty()),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = stringResource(R.string.hh_copy_id_cd),
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
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.hh_edit_title))
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
                                    text = stringResource(R.string.hh_members_title),
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
                                    text = stringResource(R.string.hh_invite_someone),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = if (canInvite) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                                Text(
                                    text = if (canInvite) {
                                        "Share an invite code to add a member"
                                    } else {
                                        uiState.inviteDisabledReason?.text() ?: stringResource(R.string.hh_invite_disabled_reason)
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
                    text = stringResource(R.string.hh_section_maintenance).uppercase(),
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
                                text = stringResource(R.string.hh_full_resync),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.hh_full_resync_hint),
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
                            Text(stringResource(R.string.common_resync))
                        }
                    }
                }
            }

            // --- Visually Separated: Join Another Household Card ---
            Text(
                text = stringResource(R.string.hh_section_switch).uppercase(),
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
                                text = stringResource(R.string.hh_join_another),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.hh_join_another_hint_short),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.hh_join_another_hint),
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
                            text = if (!uiState.isSignedIn) stringResource(R.string.hh_sign_in_first_join) else stringResource(R.string.hh_join_with_code)
                        )
                    }

                    if (uiState.joinDisabledReason != null && uiState.isSignedIn) {
                        Text(
                            text = uiState.joinDisabledReason!!.text(),
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
