package net.marvinweber.simsli.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import net.marvinweber.simsli.domain.model.Item
import net.marvinweber.simsli.domain.model.ItemStore
import net.marvinweber.simsli.domain.model.ListEntry
import net.marvinweber.simsli.domain.model.Store
import net.marvinweber.simsli.domain.wear.WearSyncBridge
import net.marvinweber.simsli.wear.common.WEAR_DATA_KEY
import net.marvinweber.simsli.wear.common.WEAR_DATA_PATH
import net.marvinweber.simsli.wear.common.WearDataPayload
import net.marvinweber.simsli.wear.common.WearShoppingItem
import net.marvinweber.simsli.wear.common.WearStoreSummary
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SimsliWearSync"

@Singleton
class PlayWearSyncBridge @Inject constructor(
    @ApplicationContext private val context: Context
) : WearSyncBridge {

    override suspend fun publishShoppingData(
        stores: List<Store>,
        entries: List<ListEntry>,
        items: Map<String, Item>,
        itemStores: List<ItemStore>
    ) {
        try {
            val activeEntries = entries.filter { !it.done }
            val itemToStoreIds = itemStores.groupBy { it.itemId }
                .mapValues { (_, list) -> list.map { it.storeId } }

            val storeSummaries = mutableListOf<WearStoreSummary>()

            // 1. "All stores" option at the top
            storeSummaries.add(
                WearStoreSummary(
                    id = null,
                    name = "All stores",
                    activeCount = activeEntries.size
                )
            )

            // 2. Individual stores
            for (store in stores) {
                val count = activeEntries.count { entry ->
                    val assigned = itemToStoreIds[entry.itemId] ?: emptyList()
                    assigned.contains(store.id)
                }
                storeSummaries.add(
                    WearStoreSummary(
                        id = store.id,
                        name = store.name,
                        activeCount = count
                    )
                )
            }

            // 3. Map entries
            val shoppingItems = entries.map { entry ->
                val item = items[entry.itemId]
                WearShoppingItem(
                    entryId = entry.id,
                    itemId = entry.itemId,
                    name = item?.name ?: "Item",
                    quantity = entry.quantity,
                    unit = entry.unit,
                    comment = entry.comment,
                    storeIds = itemToStoreIds[entry.itemId] ?: emptyList(),
                    isDone = entry.done
                )
            }

            val payload = WearDataPayload(
                timestamp = System.currentTimeMillis(),
                stores = storeSummaries,
                items = shoppingItems
            )

            val json = payload.toJson()
            val request = PutDataMapRequest.create(WEAR_DATA_PATH).apply {
                dataMap.putString(WEAR_DATA_KEY, json)
                dataMap.putLong("timestamp", payload.timestamp)
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(context).putDataItem(request).await()
            Log.d(TAG, "Published ${shoppingItems.size} items to Wearable DataClient")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to publish shopping data to watch: ${e.message}")
        }
    }
}
