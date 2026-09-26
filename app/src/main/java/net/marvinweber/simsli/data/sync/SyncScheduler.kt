package net.marvinweber.simsli.data.sync

import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decouples write paths from the SyncManager: every repository outbox enqueue is
 * followed by [requestSync], which the SyncManager debounces into a sync run.
 * Keeps repositories free of a SyncManager dependency (no cycle).
 */
@Singleton
class SyncScheduler @Inject constructor() {

    private val _requests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val requests: SharedFlow<Unit> = _requests.asSharedFlow()

    /** [reason] identifies the poke source — logged so sync loops can be attributed. */
    fun requestSync(reason: String) {
        Log.d(TAG, "requestSync($reason)")
        _requests.tryEmit(Unit)
    }

    companion object {
        const val DEBOUNCE_MS = 500L
        private const val TAG = "SimsliSync"
    }
}
