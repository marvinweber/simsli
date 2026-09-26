package net.marvinweber.simsli.ui.screens.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import net.marvinweber.simsli.data.sync.SyncManager
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    syncManager: SyncManager
) : ViewModel() {

    /** True while a sync run is executing (or queued) — drives the progress bar. */
    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing
}
