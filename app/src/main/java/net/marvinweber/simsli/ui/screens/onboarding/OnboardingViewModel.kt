package net.marvinweber.simsli.ui.screens.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.marvinweber.simsli.R
import net.marvinweber.simsli.data.local.OnboardingStorage
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.AuthState
import net.marvinweber.simsli.data.repository.CategoryRepository
import net.marvinweber.simsli.data.repository.HouseholdRepository
import net.marvinweber.simsli.data.repository.PostJoinSyncer
import net.marvinweber.simsli.di.IoDispatcher
import net.marvinweber.simsli.domain.model.Category
import net.marvinweber.simsli.ui.UiMessage
import net.marvinweber.simsli.ui.navigation.InviteDeepLinkManager
import javax.inject.Inject

enum class OnboardingStep {
    WELCOME, CONCEPTS, CREATE_OR_JOIN, CREATE, JOIN_CODE
}

enum class OnboardingMode {
    /** First launch: the full flow, Onboarding is the start destination. */
    FIRST,

    /** Replay from Settings ("Show the guide again"): welcome + concepts only. */
    REPLAY
}

sealed interface OnboardingEvent {
    /** Create/join succeeded — into the app (Home, back stack cleared). */
    data object NavigateHome : OnboardingEvent

    /** Replay mode finished (skip, last page, or back on welcome). */
    data object Close : OnboardingEvent
}

data class OnboardingUiState(
    val mode: OnboardingMode = OnboardingMode.FIRST,
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val householdNameInput: String = "",
    val usePresetCategories: Boolean = true,
    val inviteCodeInput: String = "",
    val isBusy: Boolean = false,
    val statusMessage: UiMessage? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val householdRepository: HouseholdRepository,
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository,
    private val inviteDeepLinkManager: InviteDeepLinkManager,
    private val onboardingStorage: OnboardingStorage,
    private val categoryPresets: CategoryPresets,
    private val postJoinSyncer: PostJoinSyncer,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mode: OnboardingMode =
        if (savedStateHandle.get<String>("mode") == "replay") OnboardingMode.REPLAY else OnboardingMode.FIRST

    private val step = MutableStateFlow(OnboardingStep.WELCOME)
    private val householdNameInput = MutableStateFlow("")
    private val usePresetCategories = MutableStateFlow(true)
    private val inviteCodeInput = MutableStateFlow("")
    private val isBusy = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<UiMessage?>(null)

    private val _events = MutableSharedFlow<OnboardingEvent>()
    val events: SharedFlow<OnboardingEvent> = _events.asSharedFlow()

    val uiState: StateFlow<OnboardingUiState> = combine(
        step,
        householdNameInput,
        usePresetCategories,
        combine(inviteCodeInput, isBusy, statusMessage) { code, busy, message ->
            Triple(code, busy, message)
        }
    ) { currentStep, name, presets, (code, busy, message) ->
        OnboardingUiState(
            mode = mode,
            step = currentStep,
            householdNameInput = name,
            usePresetCategories = presets,
            inviteCodeInput = code,
            isBusy = busy,
            statusMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OnboardingUiState(mode = mode)
    )

    init {
        // Deep-link case: a pending invite code becomes the join-code prefill.
        inviteDeepLinkManager.pendingInviteCode.value?.let { inviteCodeInput.value = it }

        // Join path: the user went to sign-in and came back signed in — continue
        // with the invite-code step. (With a pending deep-link code the manager
        // navigates to the Household screen instead; this collector is a no-op then.)
        if (mode == OnboardingMode.FIRST) {
            viewModelScope.launch {
                authRepository.authState.collect { state ->
                    if (state is AuthState.SignedIn && step.value == OnboardingStep.CREATE_OR_JOIN) {
                        step.value = OnboardingStep.JOIN_CODE
                    }
                }
            }
        }
    }

    // --- Step machine ---

    fun next() {
        step.value = when (step.value) {
            OnboardingStep.WELCOME -> OnboardingStep.CONCEPTS
            OnboardingStep.CONCEPTS -> if (mode == OnboardingMode.REPLAY) {
                viewModelScope.launch { _events.emit(OnboardingEvent.Close) }
                OnboardingStep.CONCEPTS
            } else {
                OnboardingStep.CREATE_OR_JOIN
            }
            OnboardingStep.CREATE_OR_JOIN -> OnboardingStep.CREATE
            else -> step.value
        }
    }

    fun back() {
        when (step.value) {
            OnboardingStep.CONCEPTS ->
                if (mode == OnboardingMode.REPLAY) {
                    viewModelScope.launch { _events.emit(OnboardingEvent.Close) }
                } else {
                    step.value = OnboardingStep.WELCOME
                }
            OnboardingStep.CREATE_OR_JOIN -> step.value = OnboardingStep.CONCEPTS
            OnboardingStep.CREATE, OnboardingStep.JOIN_CODE -> step.value = OnboardingStep.CREATE_OR_JOIN
            OnboardingStep.WELCOME -> Unit
        }
    }

    /** Skips the concepts pager. */
    fun skipConcepts() {
        if (step.value != OnboardingStep.CONCEPTS) return
        if (mode == OnboardingMode.REPLAY) {
            viewModelScope.launch { _events.emit(OnboardingEvent.Close) }
        } else {
            step.value = OnboardingStep.CREATE_OR_JOIN
        }
    }

    // --- Inputs ---

    fun onHouseholdNameChange(name: String) {
        householdNameInput.value = name
    }

    fun onUsePresetsChange(enabled: Boolean) {
        usePresetCategories.value = enabled
    }

    fun onInviteCodeChange(code: String) {
        inviteCodeInput.value = code.uppercase()
        // The user is typing a code manually — a stale deep-link code must not win.
        inviteDeepLinkManager.clearPendingInviteCode()
    }

    fun dismissStatusMessage() {
        statusMessage.value = null
    }

    // --- Actions ---

    /**
     * Creates the household locally (no account needed, AUTH-3) and applies the
     * CAT-4 presets when enabled. [defaultName] is resolved in the screen — the
     * ViewModel stays context-free.
     */
    fun createHousehold(defaultName: String) {
        viewModelScope.launch(ioDispatcher) {
            if (isBusy.value) return@launch
            isBusy.value = true
            try {
                val name = householdNameInput.value.trim().ifBlank { defaultName }

                // Retry-safety: a previous attempt may have created the household
                // before a category insert failed — reuse it instead of duplicating.
                val household = householdRepository.getHousehold().first()
                    ?: householdRepository.createHousehold(name).getOrElse { error ->
                        statusMessage.value = UiMessage(
                            R.string.onboarding_error_setup_failed,
                            listOf(error.message.orEmpty()),
                            isError = true
                        )
                        return@launch
                    }

                if (usePresetCategories.value &&
                    categoryRepository.getCategoriesByHousehold(household.id).first().isEmpty()
                ) {
                    categoryPresets.resolved().forEachIndexed { index, (presetName, emoji) ->
                        val now = Instant.now()
                        val result = categoryRepository.createCategory(
                            Category(
                                id = UUID.randomUUID().toString(),
                                householdId = household.id,
                                name = presetName,
                                emoji = emoji,
                                sortOrder = 1000f * (index + 1),
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                        if (result.isFailure) {
                            statusMessage.value = UiMessage(
                                R.string.onboarding_error_setup_failed,
                                listOf(result.exceptionOrNull()?.message.orEmpty()),
                                isError = true
                            )
                            return@launch // not done — the user retries
                        }
                    }
                }

                onboardingStorage.markDone()
                _events.emit(OnboardingEvent.NavigateHome)
            } finally {
                isBusy.value = false
            }
        }
    }

    /** Joins via invite code (HH-4); requires sign-in — the join path goes through sign-in first. */
    fun joinHousehold() {
        viewModelScope.launch(ioDispatcher) {
            if (isBusy.value) return@launch
            val code = inviteCodeInput.value.trim().uppercase()
            if (code.isBlank()) return@launch
            isBusy.value = true
            val result = householdRepository.joinHousehold(code)
            isBusy.value = false
            result.fold(
                onSuccess = {
                    inviteDeepLinkManager.clearPendingInviteCode()
                    onboardingStorage.markDone()
                    postJoinSyncer.syncNow()
                    _events.emit(OnboardingEvent.NavigateHome)
                },
                onFailure = { error ->
                    statusMessage.value = UiMessage(
                        R.string.hh_error_join_failed,
                        listOf(error.message.orEmpty()),
                        isError = true
                    )
                }
            )
        }
    }
}
