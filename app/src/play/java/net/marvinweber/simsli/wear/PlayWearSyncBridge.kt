package net.marvinweber.simsli.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import net.marvinweber.simsli.domain.model.Category
import net.marvinweber.simsli.domain.model.Item
import net.marvinweber.simsli.domain.model.ItemStore
import net.marvinweber.simsli.domain.model.ListEntry
import net.marvinweber.simsli.domain.model.Store
import net.marvinweber.simsli.domain.wear.WearCompanionStatus
import net.marvinweber.simsli.domain.wear.WearSyncBridge
import net.marvinweber.simsli.wear.common.WEAR_CAPABILITY_COMPANION
import net.marvinweber.simsli.wear.common.WEAR_DATA_KEY
import net.marvinweber.simsli.wear.common.WEAR_DATA_PATH
import net.marvinweber.simsli.wear.common.WearCategory
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

    private val _companionStatus = MutableStateFlow(WearCompanionStatus(isSupported = true))
    override val companionStatus: StateFlow<WearCompanionStatus> = _companionStatus.asStateFlow()

    override suspend fun checkStatus(): WearCompanionStatus {
        return try {
            val connectedNodes = Wearable.getNodeClient(context).connectedNodes.await()
            val hasWatch = connectedNodes.isNotEmpty()
            val watchName = connectedNodes.firstOrNull()?.displayName

            val capabilityInfo = Wearable.getCapabilityClient(context)
                .getCapability(WEAR_CAPABILITY_COMPANION, CapabilityClient.FILTER_REACHABLE)
                .await()
            val companionNodes = capabilityInfo.nodes
            val isCompanionInstalled = companionNodes.isNotEmpty()
            val deviceName = companionNodes.firstOrNull()?.displayName ?: watchName

            val status = WearCompanionStatus(
                isSupported = true,
                isConnected = isCompanionInstalled,
                isWatchConnected = hasWatch,
                deviceName = deviceName
            )
            _companionStatus.value = status
            status
        } catch (e: Exception) {
            val status = WearCompanionStatus(
                isSupported = true,
                isConnected = false,
                isWatchConnected = false,
                deviceName = null
            )
            _companionStatus.value = status
            status
        }
    }

    override suspend fun publishShoppingData(
        stores: List<Store>,
        entries: List<ListEntry>,
        items: Map<String, Item>,
        itemStores: List<ItemStore>,
        categories: List<Category>
    ) {
        try {
            val activeEntries = entries.filter { !it.done }
            val itemToStoreIds = itemStores.groupBy { it.itemId }
                .mapValues { (_, list) -> list.map { it.storeId } }

            val sortedCategories = categories.sortedBy { it.sortOrder }.map {
                WearCategory(
                    id = it.id,
                    name = it.name,
                    emoji = it.emoji
                )
            }

            val storeSummaries = mutableListOf<WearStoreSummary>()

            // 1. "All stores" option at the top
            storeSummaries.add(
                WearStoreSummary(
                    id = null,
                    name = "All stores",
                    activeCount = activeEntries.size,
                    orderedCategories = sortedCategories
                )
            )

            // 2. Individual stores (uses global category order; when STORE-4 lands, custom order goes here)
            for (store in stores) {
                val count = activeEntries.count { entry ->
                    val assigned = itemToStoreIds[entry.itemId] ?: emptyList()
                    assigned.contains(store.id)
                }
                storeSummaries.add(
                    WearStoreSummary(
                        id = store.id,
                        name = store.name,
                        activeCount = count,
                        orderedCategories = sortedCategories
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
                    notes = item?.notes,
                    storeIds = itemToStoreIds[entry.itemId] ?: emptyList(),
                    isDone = entry.done,
                    categoryId = item?.categoryId,
                    sortOrder = item?.sortOrder ?: 0f
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
