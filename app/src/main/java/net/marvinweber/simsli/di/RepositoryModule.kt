package net.marvinweber.simsli.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.HouseholdRepository
import net.marvinweber.simsli.data.repository.ItemRepository
import net.marvinweber.simsli.data.repository.ItemStoreRepository
import net.marvinweber.simsli.data.repository.ListEntryRepository
import net.marvinweber.simsli.data.repository.StoreRepository
import net.marvinweber.simsli.data.repository.impl.AuthRepositoryImpl
import net.marvinweber.simsli.data.repository.impl.HouseholdRepositoryImpl
import net.marvinweber.simsli.data.repository.impl.ItemRepositoryImpl
import net.marvinweber.simsli.data.repository.impl.ItemStoreRepositoryImpl
import net.marvinweber.simsli.data.repository.impl.ListEntryRepositoryImpl
import net.marvinweber.simsli.data.repository.impl.StoreRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    abstract fun bindHouseholdRepository(
        impl: HouseholdRepositoryImpl
    ): HouseholdRepository

    @Binds
    abstract fun bindStoreRepository(
        impl: StoreRepositoryImpl
    ): StoreRepository

    @Binds
    abstract fun bindItemRepository(
        impl: ItemRepositoryImpl
    ): ItemRepository

    @Binds
    abstract fun bindListEntryRepository(
        impl: ListEntryRepositoryImpl
    ): ListEntryRepository

    @Binds
    abstract fun bindItemStoreRepository(
        impl: ItemStoreRepositoryImpl
    ): ItemStoreRepository
}
