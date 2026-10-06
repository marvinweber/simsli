package net.marvinweber.simsli.data.debug

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.marvinweber.simsli.data.local.SimsliDatabase
import net.marvinweber.simsli.data.local.dao.CategoryDao
import net.marvinweber.simsli.data.local.dao.HouseholdDao
import net.marvinweber.simsli.data.local.dao.ItemDao
import net.marvinweber.simsli.data.local.dao.ItemStoreDao
import net.marvinweber.simsli.data.local.dao.ListEntryDao
import net.marvinweber.simsli.data.local.dao.StoreCategoryDao
import net.marvinweber.simsli.data.local.dao.StoreDao
import net.marvinweber.simsli.data.local.entity.DbCategory
import net.marvinweber.simsli.data.local.entity.DbHousehold
import net.marvinweber.simsli.data.local.entity.DbItem
import net.marvinweber.simsli.data.local.entity.DbItemStore
import net.marvinweber.simsli.data.local.entity.DbListEntry
import net.marvinweber.simsli.data.local.entity.DbStore
import net.marvinweber.simsli.data.local.entity.DbStoreCategory
import net.marvinweber.simsli.di.IoDispatcher
import net.marvinweber.simsli.domain.model.ItemType
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug builds only: resets the local database and inserts a demo dataset for
 * the offline / signed-out use case.
 *
 * Inserts go through the DAOs directly and deliberately bypass the outbox, so
 * the data stays local — seeding never pushes anything on its own. (Signing in
 * afterwards still adopts the demo household server-side and uploads the rows
 * from scratch — that is the offline-adoption flow doing its job, and a handy
 * way to test it with realistic data.)
 *
 * The server-side twin of this dataset lives in server/internal/service/seed.go — keep the
 * two in mind when changing either.
 */
@Singleton
class DemoDataSeeder @Inject constructor(
    private val db: SimsliDatabase,
    private val householdDao: HouseholdDao,
    private val storeDao: StoreDao,
    private val itemDao: ItemDao,
    private val itemStoreDao: ItemStoreDao,
    private val listEntryDao: ListEntryDao,
    private val categoryDao: CategoryDao,
    private val storeCategoryDao: StoreCategoryDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /** Wipes all local data (including outbox and sync watermarks) and inserts the demo dataset. */
    suspend fun seed(): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            // clearAllTables() is blocking and must not run inside a transaction;
            // the inserts below are atomic.
            db.clearAllTables()

            val now = Instant.now()

            fun id(n: Int) = "dd000000-0000-0000-0000-%012d".format(n)
            fun hoursAgo(h: Long) = now.minus(Duration.ofHours(h))
            fun daysAgo(d: Long) = now.minus(Duration.ofDays(d))

            val householdId = id(1)
            val rewe = id(2)
            val aldi = id(3)
            val dm = id(4)

            // Categories — global order; mirrors the seed.sql set. Entry ids go up
            // to 30, so the category indices start at 31.
            val obst = id(31)
            val milchprodukte = id(32)
            val backwaren = id(33)
            val grundnahrung = id(34)
            val haushalt = id(35)
            val sonstiges = id(36)

            // (id index, name, notes, type, category id, store ids, defaultUnit) — indices into id() keep the UUIDs fixed.
            val itemDefs = listOf(
                ItemDef(1, "Milch", null, ItemType.PERMANENT, milchprodukte, listOf(rewe, aldi), defaultUnit = "l"),
                ItemDef(2, "Brot", null, ItemType.PERMANENT, backwaren, listOf(rewe, aldi), defaultUnit = "pcs"),
                ItemDef(3, "Butter", null, ItemType.PERMANENT, milchprodukte, listOf(rewe, aldi), defaultUnit = "pack"),
                ItemDef(4, "Haferdrink", "Barista-Edition", ItemType.PERMANENT, milchprodukte, listOf(rewe), defaultUnit = "l"),
                ItemDef(5, "Käse", null, ItemType.PERMANENT, milchprodukte, listOf(rewe, aldi), defaultUnit = "g"),
                ItemDef(6, "Eier", "Freilandhaltung", ItemType.PERMANENT, milchprodukte, listOf(rewe, aldi), defaultUnit = "pack"),
                ItemDef(7, "Äpfel", null, ItemType.PERMANENT, obst, listOf(rewe, aldi), defaultUnit = "kg"),
                ItemDef(8, "Bananen", null, ItemType.PERMANENT, obst, listOf(aldi), defaultUnit = "kg"),
                ItemDef(9, "Kaffee", "Bohnen, dunkle Röstung", ItemType.PERMANENT, grundnahrung, listOf(rewe, aldi), defaultUnit = "pack"),
                ItemDef(10, "Nudeln", null, ItemType.PERMANENT, grundnahrung, listOf(aldi), defaultUnit = "pack"),
                ItemDef(11, "Tomatenpassata", null, ItemType.PERMANENT, grundnahrung, listOf(rewe, aldi), defaultUnit = "ml"),
                ItemDef(12, "Spülmittel", null, ItemType.PERMANENT, haushalt, listOf(rewe, dm), defaultUnit = "pcs"),
                ItemDef(13, "Toilettenpapier", "dreilagig", ItemType.PERMANENT, haushalt, listOf(aldi, dm), defaultUnit = "pack"),
                ItemDef(14, "Geburtstagskerzen", null, ItemType.ONE_TIME, sonstiges, listOf(dm)),
                // Uncategorized on purpose — exercises the implicit "Uncategorized" group (LIST-6).
                ItemDef(15, "Geschenkpapier", null, ItemType.ONE_TIME, null, listOf(dm))
            )

            db.withTransaction {
                householdDao.insert(
                    DbHousehold(
                        id = householdId,
                        name = "Testhaushalt",
                        createdAt = daysAgo(30),
                        updatedAt = daysAgo(30)
                    )
                )

                storeDao.insertAll(
                    listOf(
                        DbStore(rewe, householdId, "REWE", 1f, daysAgo(30), daysAgo(12)),
                        DbStore(aldi, householdId, "Aldi", 2f, daysAgo(30), daysAgo(12)),
                        DbStore(dm, householdId, "DM", 3f, daysAgo(30), daysAgo(12))
                    )
                )

                categoryDao.insertAll(
                    listOf(
                        DbCategory(obst, householdId, "Obst & Gemüse", "🍎", 1f, daysAgo(11), daysAgo(6)),
                        DbCategory(milchprodukte, householdId, "Milchprodukte", "🥛", 2f, daysAgo(11), daysAgo(6)),
                        DbCategory(backwaren, householdId, "Backwaren", "🥖", 3f, daysAgo(11), daysAgo(6)),
                        DbCategory(grundnahrung, householdId, "Grundnahrungsmittel", "🍝", 4f, daysAgo(11), daysAgo(6)),
                        DbCategory(haushalt, householdId, "Haushalt", "🧻", 5f, daysAgo(11), daysAgo(6)),
                        DbCategory(sonstiges, householdId, "Sonstiges", "📦", 6f, daysAgo(11), daysAgo(6))
                    )
                )

                itemDao.insertAll(
                    itemDefs.mapIndexed { index, def ->
                        DbItem(
                            id = id(def.idIndex),
                            householdId = householdId,
                            name = def.name,
                            notes = def.notes,
                            type = def.type,
                            categoryId = def.categoryId,
                            defaultUnit = def.defaultUnit,
                            // Staggered like real usage: earlier catalog entries updated longer ago.
                            sortOrder = def.idIndex.toFloat(),
                            createdAt = daysAgo((20 - index).toLong()),
                            updatedAt = daysAgo(((14 - index) / 2 + 1).toLong())
                        )
                    }
                )

                itemStoreDao.insertAll(
                    itemDefs.flatMap { def ->
                        def.storeIds.map { storeId ->
                            DbItemStore(itemId = id(def.idIndex), storeId = storeId, createdAt = daysAgo(12))
                        }
                    }
                )

                // Per-store aisle order: REWE full explicit (differs from global),
                // Aldi partial (rest falls back), DM none (pure fallback) — like seed.sql.
                storeCategoryDao.insertAll(
                    listOf(
                        DbStoreCategory(rewe, milchprodukte, 1f, daysAgo(11)),
                        DbStoreCategory(rewe, backwaren, 2f, daysAgo(11)),
                        DbStoreCategory(rewe, obst, 3f, daysAgo(11)),
                        DbStoreCategory(rewe, grundnahrung, 4f, daysAgo(11)),
                        DbStoreCategory(rewe, haushalt, 5f, daysAgo(11)),
                        DbStoreCategory(aldi, obst, 1f, daysAgo(11)),
                        DbStoreCategory(aldi, milchprodukte, 2f, daysAgo(11))
                    )
                )

                // Active entries (done = false) …
                val activeDefs = listOf(
                    EntryDef(1, 2.0, "Liter", null),
                    EntryDef(2, 1.0, null, "das mit den Körnern"),
                    EntryDef(4, 2.0, "Liter", null),
                    EntryDef(6, 1.0, "Packung", null),
                    EntryDef(9, 1.0, null, "keine Pads"),
                    EntryDef(12, 1.0, null, null),
                    EntryDef(7, 1.0, "kg", "säuerliche"),
                    EntryDef(13, 10.0, "Rollen", null)
                )
                // … and three recently checked off — well within the 24 h TTL, so the
                // "Recently checked" section has content that survives garbage collection.
                val doneDefs = listOf(
                    DoneEntryDef(3, 1.0, null, null, 2L),
                    DoneEntryDef(10, 2.0, "Packung", null, 3L),
                    DoneEntryDef(5, 1.0, "Stück", "Gouda, mittelalt", 2L)
                )

                listEntryDao.insertAll(
                    activeDefs.mapIndexed { index, def ->
                        DbListEntry(
                            id = id(20 + index),
                            householdId = householdId,
                            itemId = id(def.itemIndex),
                            quantity = def.quantity,
                            unit = def.unit,
                            comment = def.comment,
                            done = false,
                            completedAt = null,
                            createdAt = daysAgo((1 + index / 2).toLong()),
                            updatedAt = daysAgo((1 + index / 2).toLong())
                        )
                    } + doneDefs.mapIndexed { index, def ->
                        DbListEntry(
                            id = id(20 + activeDefs.size + index),
                            householdId = householdId,
                            itemId = id(def.itemIndex),
                            quantity = def.quantity,
                            unit = def.unit,
                            comment = def.comment,
                            done = true,
                            completedAt = hoursAgo(def.checkedOffHoursAgo),
                            createdAt = daysAgo(5),
                            updatedAt = hoursAgo(def.checkedOffHoursAgo)
                        )
                    }
                )
            }
        }
    }

    private data class ItemDef(
        val idIndex: Int,
        val name: String,
        val notes: String?,
        val type: ItemType,
        val categoryId: String?,
        val storeIds: List<String>,
        val defaultUnit: String? = null
    )

    private data class EntryDef(
        val itemIndex: Int,
        val quantity: Double?,
        val unit: String?,
        val comment: String?
    )

    private data class DoneEntryDef(
        val itemIndex: Int,
        val quantity: Double?,
        val unit: String?,
        val comment: String?,
        val checkedOffHoursAgo: Long
    )
}
