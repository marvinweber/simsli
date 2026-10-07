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
            val entryId = String(messageEvent.data, Charsets.UTF_8)
            Log.d(TAG, "Received check-off event from watch for entry: $entryId")
            serviceScope.launch {
                val result = listEntryRepository.updateListEntryDoneStatus(entryId, true)
                if (result.isFailure) {
                    Log.w(TAG, "Failed to mark entry $entryId done: ${result.exceptionOrNull()?.message}")
                } else {
                    Log.d(TAG, "Successfully marked entry $entryId done from watch")
                }
            }
        } else {
            super.onMessageReceived(messageEvent)
        }
    }
}
