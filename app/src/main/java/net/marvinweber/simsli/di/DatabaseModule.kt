package net.marvinweber.simsli.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.marvinweber.simsli.data.local.SimsliDatabase
import net.marvinweber.simsli.data.local.dao.CategoryDao
import net.marvinweber.simsli.data.local.dao.HouseholdDao
import net.marvinweber.simsli.data.local.dao.ItemDao
import net.marvinweber.simsli.data.local.dao.ItemStoreDao
import net.marvinweber.simsli.data.local.dao.ListEntryDao
import net.marvinweber.simsli.data.local.dao.OutboxDao
import net.marvinweber.simsli.data.local.dao.StoreCategoryDao
import net.marvinweber.simsli.data.local.dao.StoreDao
import net.marvinweber.simsli.data.local.dao.SyncStateDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSimsliDatabase(@ApplicationContext context: Context): SimsliDatabase {
        return SimsliDatabase.create(context)
    }

    @Provides
    fun provideHouseholdDao(database: SimsliDatabase): HouseholdDao {
        return database.householdDao()
    }

    @Provides
    fun provideItemDao(database: SimsliDatabase): ItemDao {
        return database.itemDao()
    }

    @Provides
    fun provideStoreDao(database: SimsliDatabase): StoreDao {
        return database.storeDao()
    }

    @Provides
    fun provideItemStoreDao(database: SimsliDatabase): ItemStoreDao {
        return database.itemStoreDao()
    }

    @Provides
    fun provideListEntryDao(database: SimsliDatabase): ListEntryDao {
        return database.listEntryDao()
    }

    @Provides
    fun provideCategoryDao(database: SimsliDatabase): CategoryDao {
        return database.categoryDao()
    }

    @Provides
    fun provideStoreCategoryDao(database: SimsliDatabase): StoreCategoryDao {
        return database.storeCategoryDao()
    }

    @Provides
    fun provideSyncStateDao(database: SimsliDatabase): SyncStateDao {
        return database.syncStateDao()
    }

    @Provides
    fun provideOutboxDao(database: SimsliDatabase): OutboxDao {
        return database.outboxDao()
    }
}
