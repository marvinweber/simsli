package net.marvinweber.simsli.wear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.marvinweber.simsli.data.repository.ListEntryRepository
import net.marvinweber.simsli.wear.common.WEAR_CHECK_ENTRY_PATH
import javax.inject.Inject

private const val TAG = "SimsliWearListener"

@AndroidEntryPoint
class PlayWearListenerService : WearableListenerService() {

    @Inject
    lateinit var listEntryRepository: ListEntryRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == WEAR_CHECK_ENTRY_PATH) {
            val text = String(messageEvent.data, Charsets.UTF_8)
            val (entryId, done) = if (text.contains(":")) {
                val parts = text.split(":", limit = 2)
                parts[0] to parts[1].toBoolean()
            } else {
                text to true
            }
            Log.d(TAG, "Received toggle event from watch: entry=$entryId, done=$done")
            serviceScope.launch {
                val result = listEntryRepository.updateListEntryDoneStatus(entryId, done)
                if (result.isFailure) {
                    Log.w(TAG, "Failed to update entry $entryId (done=$done): ${result.exceptionOrNull()?.message}")
                } else {
                    Log.d(TAG, "Successfully updated entry $entryId (done=$done) from watch")
                }
            }
        } else {
            super.onMessageReceived(messageEvent)
        }
    }
}
