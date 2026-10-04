package net.marvinweber.simsli.data.local.entity

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.marvinweber.simsli.domain.model.ItemLink
import net.marvinweber.simsli.domain.model.ItemType
import java.time.Instant

class Converters {
    @TypeConverter
    fun fromItemType(value: ItemType): String = value.name

    @TypeConverter
    fun toItemType(value: String): ItemType = ItemType.valueOf(value)

    @TypeConverter
    fun fromItemLinkList(value: List<ItemLink>?): String {
        return Json.encodeToString(value ?: emptyList())
    }

    @TypeConverter
    fun toItemLinkList(value: String?): List<ItemLink> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            Json.decodeFromString(value)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Stored as epoch microseconds, not milliseconds: delta-sync watermarks are
     * compared against Postgres timestamptz (microsecond resolution). Millisecond
     * truncation made `updated_at > watermark` match the same row forever —
     * an infinite pull/realtime loop.
     */
    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.let {
        Math.addExact(Math.multiplyExact(it.epochSecond, 1_000_000L), (it.nano / 1_000).toLong())
    }

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let {
        Instant.ofEpochSecond(Math.floorDiv(it, 1_000_000L), Math.floorMod(it, 1_000_000L) * 1_000)
    }
}
