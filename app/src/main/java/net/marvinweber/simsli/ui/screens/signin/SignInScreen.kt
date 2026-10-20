package net.marvinweber.simsli.ui.screens.signin

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.remember
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import net.marvinweber.simsli.BuildConfig
import net.marvinweber.simsli.R
import net.marvinweber.simsli.data.repository.AuthState
import net.marvinweber.simsli.data.repository.EndpointHealth
import net.marvinweber.simsli.data.repository.EndpointStatus
import net.marvinweber.simsli.data.repository.HostingMode
import net.marvinweber.simsli.ui.components.SimsliTopAppBarTitle
import net.marvinweber.simsli.ui.resolve
import net.marvinweber.simsli.ui.text

/** Documentation link for self-hosters (placeholder until DOCS-1 ships GitHub Pages). */
private const val SELF_HOSTING_DOCS_URL = "https://github.com/marvinweber/simsli#readme"

/**
 * Sign-in screen (AUTH-5): hosting choice (Cloud closed beta | Self-Hosted) +
 * magic-link sign-in. The invite deep link's sign-in target; onboarding's join
 * path reuses it later (SCREENS-5). Signed-out only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    onNavigateBack: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current

    // Sign-in succeeded: the invite path navigates to the Household screen via
    // InviteDeepLinkManager; without a pending code, leave the sign-in screen.
    LaunchedEffect(authState) {
        if (authState is AuthState.SignedIn) {
            onNavigateBack()
        }
    }

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
                        title = stringResource(R.string.sign_in_title),
                        isSyncing = false,
                        isOffline = false,
                        hasPendingWrites = false
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.pendingInviteCode != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.GroupAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.hh_join_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = stringResource(R.string.settings_join_invite_hint, uiState.pendingInviteCode.orEmpty()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Selection shown by primary colors only — no check icon (the
                    // badge beneath the label already makes both segments two rows).
                    val selectedColors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        activeBorderColor = MaterialTheme.colorScheme.primary
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = uiState.hostingMode == HostingMode.CLOUD,
                            onClick = { viewModel.onHostingModeChange(HostingMode.CLOUD) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            colors = selectedColors,
                            icon = { }
                        ) {
                            HostingSegmentContent(
                                icon = Icons.Outlined.Cloud,
                                labelRes = R.string.sign_in_hosting_cloud,
                                badgeRes = R.string.sign_in_beta_badge
                            )
                        }
                        SegmentedButton(
                            selected = uiState.hostingMode == HostingMode.SELF_HOSTED,
                            onClick = { viewModel.onHostingModeChange(HostingMode.SELF_HOSTED) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            colors = selectedColors,
                            icon = { }
                        ) {
                            HostingSegmentContent(
                                icon = Icons.Outlined.Dns,
                                labelRes = R.string.sign_in_hosting_selfhosted,
                                badgeRes = R.string.sign_in_selfhosted_badge
                            )
                        }
                    }

                    if (uiState.hostingMode == HostingMode.CLOUD) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.sign_in_cloud_beta_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.sign_in_cloud_endpoint, BuildConfig.SERVER_URL),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.sign_in_selfhosted_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = uiState.selfHostedUrlInput,
                            onValueChange = viewModel::onSelfHostedUrlChange,
                            label = { Text(stringResource(R.string.sign_in_server_url_label)) },
                            placeholder = { Text("https://simsli.example.com") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Dns, contentDescription = null)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    viewModel.probeUrl()
                                }
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )

                        ServerProbeRow(
                            probe = uiState.probe,
                            onCheck = viewModel::probeUrl
                        )

                        TextButton(onClick = { uriHandler.openUri(SELF_HOSTING_DOCS_URL) }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.sign_in_docs_link))
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.settings_sign_in_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = uiState.emailInput,
                onValueChange = viewModel::onEmailChange,
                label = { Text(stringResource(R.string.settings_email_label)) },
                placeholder = { Text(stringResource(R.string.settings_email_placeholder)) },
                leadingIcon = {
                    Icon(Icons.Outlined.Email, contentDescription = null)
                },
                trailingIcon = {
                    if (uiState.emailInput.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onEmailChange("") }) {
                            Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.common_clear))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (!uiState.isBusy && uiState.emailInput.isNotBlank()) {
                            keyboardController?.hide()
                            viewModel.sendMagicLink()
                        }
                    }
                ),
                enabled = !uiState.isBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Button(
                onClick = {
                    keyboardController?.hide()
                    viewModel.sendMagicLink()
                },
                enabled = !uiState.isBusy && uiState.emailInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (uiState.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_sending_magic_link))
                } else {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_send_magic_link))
                }
            }

            Text(
                text = stringResource(R.string.settings_no_password_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Icon + label with a small, muted badge chip beneath (cloud: closed beta, self-hosted: open). */
@Composable
private fun HostingSegmentContent(icon: ImageVector, @StringRes labelRes: Int, @StringRes badgeRes: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(
                stringResource(labelRes),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = stringResource(badgeRes),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ServerProbeRow(
    probe: EndpointHealth?,
    onCheck: () -> Unit
) {
    if (probe == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        when (probe.status) {
            EndpointStatus.CHECKING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            }
            EndpointStatus.ONLINE -> {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            probe.detail?.let { detail ->
                Text(
                    text = detail.text(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (probe.status == EndpointStatus.ONLINE) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            if (probe.status == EndpointStatus.ONLINE) {
                val modeLabel = if (probe.serverMode == "cloud") {
                    stringResource(R.string.settings_cloud_mode)
                } else {
                    stringResource(R.string.settings_selfhosted_mode)
                }
                Text(
                    text = stringResource(
                        R.string.settings_server_line,
                        probe.serverVersion.orEmpty(),
                        probe.apiVersion?.toString().orEmpty(),
                        modeLabel
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        if (probe.registrationOpen) {
                            R.string.sign_in_registration_open
                        } else {
                            R.string.sign_in_registration_invite_only
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (probe.status != EndpointStatus.CHECKING) {
            TextButton(onClick = onCheck) {
                Text(stringResource(R.string.sign_in_check_server))
            }
        }
    }
}
