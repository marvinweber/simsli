package net.marvinweber.simsli.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.marvinweber.simsli.data.local.dao.HouseholdDao
import net.marvinweber.simsli.data.local.dao.ItemDao
import net.marvinweber.simsli.data.local.dao.ItemStoreDao
import net.marvinweber.simsli.data.local.dao.ListEntryDao
import net.marvinweber.simsli.data.local.dao.OutboxDao
import net.marvinweber.simsli.data.local.dao.StoreDao
import net.marvinweber.simsli.data.local.dao.SyncStateDao
import net.marvinweber.simsli.data.local.entity.Converters
import net.marvinweber.simsli.data.local.entity.DbHousehold
import net.marvinweber.simsli.data.local.entity.DbItem
import net.marvinweber.simsli.data.local.entity.DbItemStore
import net.marvinweber.simsli.data.local.entity.DbListEntry
import net.marvinweber.simsli.data.local.entity.DbOutboxEntry
import net.marvinweber.simsli.data.local.entity.DbStore
import net.marvinweber.simsli.data.local.entity.DbSyncState
import net.marvinweber.simsli.domain.model.ItemType
import java.time.Instant
import java.util.UUID

@Database(
    entities = [
        DbHousehold::class,
        DbItem::class,
        DbStore::class,
        DbItemStore::class,
        DbListEntry::class,
        DbSyncState::class,
        DbOutboxEntry::class
    ],
    version = 2
)
@TypeConverters(Converters::class)
abstract class SimsliDatabase : RoomDatabase() {
    abstract fun householdDao(): HouseholdDao
    abstract fun itemDao(): ItemDao
    abstract fun storeDao(): StoreDao
    abstract fun itemStoreDao(): ItemStoreDao
    abstract fun listEntryDao(): ListEntryDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        private const val DATABASE_NAME = "simsli_database"

        fun create(context: Context): SimsliDatabase {
            return Room.databaseBuilder(
                context,
                SimsliDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .addCallback(SimsliDatabaseCallback())
                .build()
        }
    }

    private class SimsliDatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Seed data will be inserted by the ViewModel's household creation logic
            // This callback is just for structure setup if needed
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            // We could add seed data here, but we'll use a simpler approach
        }
    }
}
