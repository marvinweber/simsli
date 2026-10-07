package net.marvinweber.simsli.wear.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import net.marvinweber.simsli.wear.common.WEAR_CHECK_ENTRY_PATH
import net.marvinweber.simsli.wear.common.WEAR_DATA_KEY
import net.marvinweber.simsli.wear.common.WEAR_DATA_PATH
import net.marvinweber.simsli.wear.common.WearDataPayload

private const val TAG = "SimsliWearRepo"

class WearShoppingRepository(
    private val context: Context
) : DataClient.OnDataChangedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _dataState = MutableStateFlow<WearDataPayload?>(null)
    val dataState: StateFlow<WearDataPayload?> = _dataState.asStateFlow()

    init {
        Wearable.getDataClient(context).addListener(this)
        fetchLatestData()
    }

    private fun fetchLatestData() {
        scope.launch {
            try {
                val dataItemBuffer = Wearable.getDataClient(context)
                    .getDataItems(Uri.parse("wear://*$WEAR_DATA_PATH"))
                    .await()

                for (item in dataItemBuffer) {
                    if (item.uri.path == WEAR_DATA_PATH) {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        val json = dataMap.getString(WEAR_DATA_KEY)
                        if (!json.isNullOrBlank()) {
                            val payload = WearDataPayload.fromJson(json)
                            _dataState.value = payload
                            Log.d(TAG, "Loaded initial data from DataClient with ${payload.items.size} items")
                            break
                        }
                    }
                }
                dataItemBuffer.release()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch initial DataClient items: ${e.message}")
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WEAR_DATA_PATH) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val json = dataMap.getString(WEAR_DATA_KEY)
                if (!json.isNullOrBlank()) {
                    try {
                        val payload = WearDataPayload.fromJson(json)
                        _dataState.value = payload
                        Log.d(TAG, "Received updated payload with ${payload.items.size} items")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse data payload: ${e.message}")
                    }
                }
            }
        }
    }

    fun toggleItem(entryId: String, isDone: Boolean) {
        // Optimistic update locally
        val current = _dataState.value
        if (current != null) {
            val updatedItems = current.items.map {
                if (it.entryId == entryId) it.copy(isDone = isDone) else it
            }
            _dataState.value = current.copy(items = updatedItems)
        }

        // Send check/uncheck message to phone
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                val payloadString = "$entryId:$isDone"
                val payloadBytes = payloadString.toByteArray(Charsets.UTF_8)
                for (node in nodes) {
                    Wearable.getMessageClient(context)
                        .sendMessage(node.id, WEAR_CHECK_ENTRY_PATH, payloadBytes)
                        .await()
                    Log.d(TAG, "Sent toggle message ($entryId -> $isDone) to phone node ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send toggle message to phone: ${e.message}")
            }
        }
    }

    fun destroy() {
        Wearable.getDataClient(context).removeListener(this)
    }
}
