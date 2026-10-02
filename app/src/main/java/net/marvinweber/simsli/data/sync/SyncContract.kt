package net.marvinweber.simsli.data.sync

import java.time.Duration

/** Shared identifiers for the outbox queue and the per-table sync watermarks. */
object SyncContract {

    /**
     * How long a checked-off entry stays visible under "Recently checked" before
     * any device's garbage collection removes it locally and on the server.
     */
    val RECENTLY_CHECKED_TTL: Duration = Duration.ofHours(24)
    // Outbox entity types
    const val ENTITY_HOUSEHOLD = "household"
    const val ENTITY_ITEM = "item"
    const val ENTITY_STORE = "store"
    const val ENTITY_ITEM_STORE = "item_store"
    const val ENTITY_LIST_ENTRY = "list_entry"
    const val ENTITY_CATEGORY = "category"
    const val ENTITY_STORE_CATEGORY = "store_category"

    // Outbox operations
    const val OP_UPSERT = "UPSERT"
    const val OP_DELETE = "DELETE"

    // Watermark keys — mirror the PostgREST table names
    const val TABLE_HOUSEHOLDS = "households"
    const val TABLE_STORES = "stores"
    const val TABLE_ITEMS = "items"
    const val TABLE_LIST_ENTRIES = "list_entries"
    const val TABLE_CATEGORIES = "categories"

    // Marker key to ensure initial local upload is only performed once
    const val KEY_INITIAL_UPLOAD_DONE = "_initial_upload_done"

    fun itemStoreEntityId(itemId: String, storeId: String): String = "$itemId:$storeId"

    fun parseItemStoreEntityId(entityId: String): Pair<String, String>? {
        val parts = entityId.split(':', limit = 2)
        return if (parts.size == 2) parts[0] to parts[1] else null
    }

    fun storeCategoryEntityId(storeId: String, categoryId: String): String = "$storeId:$categoryId"

    fun parseStoreCategoryEntityId(entityId: String): Pair<String, String>? {
        val parts = entityId.split(':', limit = 2)
        return if (parts.size == 2) parts[0] to parts[1] else null
    }
}
