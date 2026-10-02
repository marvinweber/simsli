package net.marvinweber.simsli.ui.screens.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import net.marvinweber.simsli.data.sync.SyncManager
import net.marvinweber.simsli.ui.navigation.QuickAction
import net.marvinweber.simsli.ui.navigation.QuickActionManager
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    syncManager: SyncManager,
    val quickActionManager: QuickActionManager
) : ViewModel() {

    /** True while a sync run is executing (or queued) — drives the progress bar. */
    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing

    val quickActions: SharedFlow<QuickAction> = quickActionManager.actions
}
