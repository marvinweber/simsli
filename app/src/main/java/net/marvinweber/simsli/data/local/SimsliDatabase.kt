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
import net.marvinweber.simsli.data.local.dao.ItemStoreGroupDao
import net.marvinweber.simsli.data.local.dao.ListEntryDao
import net.marvinweber.simsli.data.local.dao.OutboxDao
import net.marvinweber.simsli.data.local.dao.SeenListEntryDao
import net.marvinweber.simsli.data.local.dao.StoreCategoryDao
import net.marvinweber.simsli.data.local.dao.StoreDao
import net.marvinweber.simsli.data.local.dao.StoreGroupDao
import net.marvinweber.simsli.data.local.dao.SyncStateDao
import net.marvinweber.simsli.data.local.entity.Converters
import net.marvinweber.simsli.data.local.entity.DbCategory
import net.marvinweber.simsli.data.local.entity.DbHousehold
import net.marvinweber.simsli.data.local.entity.DbItem
import net.marvinweber.simsli.data.local.entity.DbItemStore
import net.marvinweber.simsli.data.local.entity.DbItemStoreGroup
import net.marvinweber.simsli.data.local.entity.DbListEntry
import net.marvinweber.simsli.data.local.entity.DbOutboxEntry
import net.marvinweber.simsli.data.local.entity.DbSeenListEntry
import net.marvinweber.simsli.data.local.entity.DbStore
import net.marvinweber.simsli.data.local.entity.DbStoreCategory
import net.marvinweber.simsli.data.local.entity.DbStoreGroup
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
        DbStoreGroup::class,
        DbItemStoreGroup::class,
        DbSyncState::class,
        DbOutboxEntry::class,
        DbSeenListEntry::class
    ],
    version = 10
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
    abstract fun storeGroupDao(): StoreGroupDao
    abstract fun itemStoreGroupDao(): ItemStoreGroupDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun outboxDao(): OutboxDao
    abstract fun seenListEntryDao(): SeenListEntryDao

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

        /** v3 → v4: items.links (ITEM-1). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `items` ADD COLUMN `links` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /** v4 → v5: items.defaultUnit (ITEM-5). */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `items` ADD COLUMN `defaultUnit` TEXT")
            }
        }

        /** v5 → v6: households.icon (HH-1, nullable emoji). */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `households` ADD COLUMN `icon` TEXT")
            }
        }

        /** v6 → v7: store groups (STORE-7) + item_stores.membership (ADR 0020). */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `store_groups` (" +
                        "`id` TEXT NOT NULL, " +
                        "`householdId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`sortOrder` REAL NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `item_store_groups` (" +
                        "`itemId` TEXT NOT NULL, " +
                        "`groupId` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`itemId`, `groupId`))"
                )
                db.execSQL("ALTER TABLE `stores` ADD COLUMN `groupId` TEXT")
                db.execSQL("ALTER TABLE `item_stores` ADD COLUMN `membership` TEXT NOT NULL DEFAULT 'MEMBER'")
            }
        }

        /** v7 → v8: item_stores.favorite (STORE-5). */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `item_stores` ADD COLUMN `favorite` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v8 → v9: seen_list_entries (LIST-9), device-local seen-state for the "new" indicator. */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `seen_list_entries` (" +
                        "`entryId` TEXT NOT NULL, " +
                        "`seenAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`entryId`))"
                )
            }
        }

        /** v9 → v10: households.currency (HH-9, ISO 4217 code, default EUR). */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `households` ADD COLUMN `currency` TEXT NOT NULL DEFAULT 'EUR'")
            }
        }

        fun create(context: Context): SimsliDatabase {
            return Room.databaseBuilder(
                context,
                SimsliDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(
                    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                    MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10
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
