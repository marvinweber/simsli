package net.marvinweber.simsli.domain.wear

import net.marvinweber.simsli.domain.model.Category
import net.marvinweber.simsli.domain.model.Item
import net.marvinweber.simsli.domain.model.ItemStore
import net.marvinweber.simsli.domain.model.ListEntry
import net.marvinweber.simsli.domain.model.Store

interface WearSyncBridge {
    suspend fun publishShoppingData(
        stores: List<Store>,
        entries: List<ListEntry>,
        items: Map<String, Item>,
        itemStores: List<ItemStore>,
        categories: List<Category>
    )
}
