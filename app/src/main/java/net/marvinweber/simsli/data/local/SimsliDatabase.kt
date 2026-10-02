package net.marvinweber.simsli.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.marvinweber.simsli.data.local.dao.CategoryDao
import net.marvinweber.simsli.data.local.dao.HouseholdDao
import net.marvinweber.simsli.data.local.dao.ItemDao
import net.marvinweber.simsli.data.local.dao.ItemStoreDao
import net.marvinweber.simsli.data.local.dao.ListEntryDao
import net.marvinweber.simsli.data.local.dao.OutboxDao
import net.marvinweber.simsli.data.local.dao.StoreCategoryDao
import net.marvinweber.simsli.data.local.dao.StoreDao
import net.marvinweber.simsli.data.local.dao.SyncStateDao
import net.marvinweber.simsli.data.local.entity.Converters
import net.marvinweber.simsli.data.local.entity.DbCategory
import net.marvinweber.simsli.data.local.entity.DbHousehold
import net.marvinweber.simsli.data.local.entity.DbItem
import net.marvinweber.simsli.data.local.entity.DbItemStore
import net.marvinweber.simsli.data.local.entity.DbListEntry
import net.marvinweber.simsli.data.local.entity.DbOutboxEntry
import net.marvinweber.simsli.data.local.entity.DbStore
import net.marvinweber.simsli.data.local.entity.DbStoreCategory
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
        DbCategory::class,
        DbStoreCategory::class,
        DbSyncState::class,
        DbOutboxEntry::class
    ],
    version = 3
)
@TypeConverters(Converters::class)
abstract class SimsliDatabase : RoomDatabase() {
    abstract fun householdDao(): HouseholdDao
    abstract fun itemDao(): ItemDao
    abstract fun storeDao(): StoreDao
    abstract fun itemStoreDao(): ItemStoreDao
    abstract fun listEntryDao(): ListEntryDao
    abstract fun categoryDao(): CategoryDao
    abstract fun storeCategoryDao(): StoreCategoryDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        private const val DATABASE_NAME = "simsli_database"

        /** v2 → v3: categories (CAT-1), store_categories (STORE-4), items.categoryId (CAT-2). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `categories` (" +
                        "`id` TEXT NOT NULL, " +
                        "`householdId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`emoji` TEXT, " +
                        "`sortOrder` REAL NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `store_categories` (" +
                        "`storeId` TEXT NOT NULL, " +
                        "`categoryId` TEXT NOT NULL, " +
                        "`sortOrder` REAL NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`storeId`, `categoryId`))"
                )
                db.execSQL("ALTER TABLE `items` ADD COLUMN `categoryId` TEXT")
            }
        }

        fun create(context: Context): SimsliDatabase {
            return Room.databaseBuilder(
                context,
                SimsliDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_2_3)
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
