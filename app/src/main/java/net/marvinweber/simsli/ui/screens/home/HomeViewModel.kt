package net.marvinweber.simsli.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import net.marvinweber.simsli.data.repository.HouseholdRepository
import net.marvinweber.simsli.data.sync.SyncManager
import net.marvinweber.simsli.domain.model.Household
import net.marvinweber.simsli.ui.navigation.QuickAction
import net.marvinweber.simsli.ui.navigation.QuickActionManager
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    syncManager: SyncManager,
    householdRepository: HouseholdRepository,
    val quickActionManager: QuickActionManager
) : ViewModel() {

    /** True while a sync run is executing (or queued) — drives the progress bar. */
    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing

    /** Household row for the Settings tab's personalized label + icon (SCREENS-1/4). */
    val household: StateFlow<Household?> = householdRepository.getHousehold()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val quickActions: SharedFlow<QuickAction> = quickActionManager.actions
}
