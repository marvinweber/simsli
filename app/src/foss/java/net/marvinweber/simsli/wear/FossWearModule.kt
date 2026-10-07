package net.marvinweber.simsli.wear

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.marvinweber.simsli.domain.model.Category
import net.marvinweber.simsli.domain.model.Item
import net.marvinweber.simsli.domain.model.ItemStore
import net.marvinweber.simsli.domain.model.ListEntry
import net.marvinweber.simsli.domain.model.Store
import net.marvinweber.simsli.domain.wear.WearSyncBridge
import javax.inject.Inject
import javax.inject.Singleton

class NoOpWearSyncBridge @Inject constructor() : WearSyncBridge {
    override suspend fun publishShoppingData(
        stores: List<Store>,
        entries: List<ListEntry>,
        items: Map<String, Item>,
        itemStores: List<ItemStore>,
        categories: List<Category>
    ) {
        // No-op on FOSS build without Google Play Services
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FossWearModule {
    @Binds
    @Singleton
    abstract fun bindWearSyncBridge(impl: NoOpWearSyncBridge): WearSyncBridge
}
