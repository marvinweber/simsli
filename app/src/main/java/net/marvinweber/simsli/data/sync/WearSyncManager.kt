package net.marvinweber.simsli.data.sync

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import net.marvinweber.simsli.data.repository.HouseholdRepository
import net.marvinweber.simsli.data.repository.ItemRepository
import net.marvinweber.simsli.data.repository.ItemStoreRepository
import net.marvinweber.simsli.data.repository.ListEntryRepository
import net.marvinweber.simsli.data.repository.StoreRepository
import net.marvinweber.simsli.di.IoDispatcher
import net.marvinweber.simsli.domain.model.Item
import net.marvinweber.simsli.domain.model.ItemStore
import net.marvinweber.simsli.domain.model.ListEntry
import net.marvinweber.simsli.domain.model.Store
import net.marvinweber.simsli.domain.wear.WearSyncBridge
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearSyncManager @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val storeRepository: StoreRepository,
    private val listEntryRepository: ListEntryRepository,
    private val itemRepository: ItemRepository,
    private val itemStoreRepository: ItemStoreRepository,
    private val wearSyncBridge: WearSyncBridge,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var isStarted = false

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun start() {
        if (isStarted) return
        isStarted = true

        scope.launch {
            householdRepository.getHousehold()
                .filterNotNull()
                .distinctUntilChanged { old, new -> old.id == new.id }
                .flatMapLatest { household ->
                    combine(
                        storeRepository.getStoresByHousehold(household.id),
                        listEntryRepository.getListEntriesByHousehold(household.id),
                        itemRepository.getItemsByHousehold(household.id),
                        itemStoreRepository.observeItemStoresByHousehold(household.id)
                    ) { stores: List<Store>, entries: List<ListEntry>, items: List<Item>, itemStores: List<ItemStore> ->
                        ShoppingSnapshot(
                            stores = stores,
                            entries = entries,
                            items = items.associateBy { it.id },
                            itemStores = itemStores
                        )
                    }
                }
                .collect { snapshot ->
                    wearSyncBridge.publishShoppingData(
                        stores = snapshot.stores,
                        entries = snapshot.entries,
                        items = snapshot.items,
                        itemStores = snapshot.itemStores
                    )
                }
        }
    }

    private data class ShoppingSnapshot(
        val stores: List<Store>,
        val entries: List<ListEntry>,
        val items: Map<String, Item>,
        val itemStores: List<ItemStore>
    )
}
