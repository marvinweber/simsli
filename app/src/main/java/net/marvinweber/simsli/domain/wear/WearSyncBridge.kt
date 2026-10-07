package net.marvinweber.simsli.domain.wear

import kotlinx.coroutines.flow.StateFlow
import net.marvinweber.simsli.domain.model.Category
import net.marvinweber.simsli.domain.model.Item
import net.marvinweber.simsli.domain.model.ItemStore
import net.marvinweber.simsli.domain.model.ListEntry
import net.marvinweber.simsli.domain.model.Store

data class WearCompanionStatus(
    val isSupported: Boolean = false,
    val isConnected: Boolean = false,
    val deviceName: String? = null
)

interface WearSyncBridge {
    val companionStatus: StateFlow<WearCompanionStatus>

    suspend fun checkStatus(): WearCompanionStatus

    suspend fun publishShoppingData(
        stores: List<Store>,
        entries: List<ListEntry>,
        items: Map<String, Item>,
        itemStores: List<ItemStore>,
        categories: List<Category>
    )
}
