package net.marvinweber.simsli.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import net.marvinweber.simsli.R
import net.marvinweber.simsli.ui.components.SimsliTopAppBarTitle
import net.marvinweber.simsli.ui.resolve

/** One core-concepts pager page (SCREENS-5 step 2). */
private data class ConceptPage(
    val emoji: String,
    val titleRes: Int,
    val bodyRes: Int
)

private val CONCEPT_PAGES = listOf(
    ConceptPage("📚", R.string.onboarding_concepts_1_title, R.string.onboarding_concepts_1_body),
    ConceptPage("🏪", R.string.onboarding_concepts_2_title, R.string.onboarding_concepts_2_body),
    ConceptPage("🛒", R.string.onboarding_concepts_3_title, R.string.onboarding_concepts_3_body),
    ConceptPage("👥", R.string.onboarding_concepts_4_title, R.string.onboarding_concepts_4_body)
)

/**
 * Onboarding (SCREENS-5): welcome → core-concepts pager → create-or-join.
 * One route with an internal step machine ([OnboardingViewModel]); FIRST is the
 * start destination on a fresh install, REPLAY (from Settings) shows only
 * welcome + concepts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onNavigateToSignIn: () -> Unit,
    onNavigateHome: () -> Unit,
    onClose: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    BackHandler(enabled = uiState.step != OnboardingStep.WELCOME || uiState.mode == OnboardingMode.REPLAY) {
        viewModel.back()
    }

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage
        if (message != null) {
            val text = message.resolve(context)
            snackbarHostState.showSnackbar(
                message = text,
                withDismissAction = true,
                duration = if (message.isError) SnackbarDuration.Long else SnackbarDuration.Short
            )
            viewModel.dismissStatusMessage()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                OnboardingEvent.NavigateHome -> onNavigateHome()
                OnboardingEvent.Close -> onClose()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    when {
                        uiState.mode == OnboardingMode.REPLAY -> IconButton(onClick = viewModel::back) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.common_cancel)
                            )
                        }
                        uiState.step != OnboardingStep.WELCOME -> IconButton(onClick = viewModel::back) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.common_back)
                            )
                        }
                    }
                },
                title = {
                    SimsliTopAppBarTitle(
                        title = "",
                        isSyncing = false,
                        isOffline = false,
                        hasPendingWrites = false
                    )
                },
                actions = {
                    if (uiState.step == OnboardingStep.CONCEPTS) {
                        TextButton(onClick = viewModel::skipConcepts) {
                            Text(stringResource(R.string.onboarding_skip))
                        }
                    }
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
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (uiState.step) {
                OnboardingStep.WELCOME -> WelcomeContent(onGetStarted = viewModel::next)
                OnboardingStep.CONCEPTS -> ConceptsPagerContent(
                    onNext = viewModel::next
                )
                OnboardingStep.CREATE_OR_JOIN -> CreateOrJoinContent(
                    onCreate = viewModel::next,
                    onJoin = onNavigateToSignIn
                )
                OnboardingStep.CREATE -> CreateHouseholdContent(
                    uiState = uiState,
                    onNameChange = viewModel::onHouseholdNameChange,
                    onUsePresetsChange = viewModel::onUsePresetsChange,
                    onCreate = {
                        keyboardController?.hide()
                        viewModel.createHousehold(context.getString(R.string.onboarding_household_name_default))
                    }
                )
                OnboardingStep.JOIN_CODE -> JoinCodeContent(
                    uiState = uiState,
                    onCodeChange = viewModel::onInviteCodeChange,
                    onJoin = {
                        keyboardController?.hide()
                        viewModel.joinHousehold()
                    }
                )
            }
        }
    }
}

@Composable
private fun WelcomeContent(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.25f))
        Image(
            painter = painterResource(R.drawable.ic_simsli_logo),
            contentDescription = null,
            modifier = Modifier.size(112.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.weight(0.35f))
        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_get_started),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun ConceptsPagerContent(
    onNext: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { CONCEPT_PAGES.size })
    val onClickScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            val concept = CONCEPT_PAGES[page]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = concept.emoji, fontSize = 64.sp)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(concept.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(concept.bodyRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Page indicator dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CONCEPT_PAGES.forEachIndexed { index, _ ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (selected) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                )
            }
        }

        Button(
            onClick = {
                if (pagerState.currentPage < CONCEPT_PAGES.lastIndex) {
                    onClickScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                } else {
                    onNext()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = stringResource(
                    if (pagerState.currentPage < CONCEPT_PAGES.lastIndex) {
                        R.string.onboarding_next
                    } else {
                        R.string.onboarding_lets_go
                    }
                ),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun CreateOrJoinContent(
    onCreate: () -> Unit,
    onJoin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.onboarding_create_or_join_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        OnboardingChoiceCard(
            emoji = "🏠",
            title = stringResource(R.string.onboarding_create_household),
            hint = stringResource(R.string.onboarding_create_household_hint),
            onClick = onCreate
        )
        OnboardingChoiceCard(
            emoji = "🤝",
            title = stringResource(R.string.onboarding_join_household),
            hint = stringResource(R.string.onboarding_join_household_hint),
            onClick = onJoin
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingChoiceCard(
    emoji: String,
    title: String,
    hint: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, fontSize = 32.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CreateHouseholdContent(
    uiState: OnboardingUiState,
    onNameChange: (String) -> Unit,
    onUsePresetsChange: (Boolean) -> Unit,
    onCreate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.onboarding_create_household),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = uiState.householdNameInput,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.onboarding_household_name_label)) },
            placeholder = { Text(stringResource(R.string.onboarding_household_name_default)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.onboarding_use_preset_categories),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = uiState.usePresetCategories,
                onCheckedChange = onUsePresetsChange
            )
        }

        if (uiState.usePresetCategories) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(CATEGORY_PRESETS) { _, preset ->
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text("${preset.emoji} ${stringResource(preset.nameRes)}")
                        }
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.onboarding_create_sign_in_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = onCreate,
            enabled = !uiState.isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (uiState.isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = stringResource(R.string.onboarding_create_button),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun JoinCodeContent(
    uiState: OnboardingUiState,
    onCodeChange: (String) -> Unit,
    onJoin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.onboarding_join_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.hh_join_enter_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = uiState.inviteCodeInput,
            onValueChange = onCodeChange,
            label = { Text(stringResource(R.string.onboarding_join_code_label)) },
            leadingIcon = {
                Icon(Icons.Outlined.GroupAdd, contentDescription = null)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (!uiState.isBusy && uiState.inviteCodeInput.isNotBlank()) onJoin() }
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        Button(
            onClick = onJoin,
            enabled = !uiState.isBusy && uiState.inviteCodeInput.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (uiState.isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = stringResource(R.string.onboarding_join_button),
                style = MaterialTheme.typography.titleMedium
            )
        }

        Text(
            text = stringResource(R.string.onboarding_join_link_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
