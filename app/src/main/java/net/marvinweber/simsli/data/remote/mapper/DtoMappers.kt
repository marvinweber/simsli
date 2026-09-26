package net.marvinweber.simsli.data.remote.mapper

import net.marvinweber.simsli.data.local.entity.DbHousehold
import net.marvinweber.simsli.data.local.entity.DbItem
import net.marvinweber.simsli.data.local.entity.DbItemStore
import net.marvinweber.simsli.data.local.entity.DbListEntry
import net.marvinweber.simsli.data.local.entity.DbStore
import net.marvinweber.simsli.data.remote.dto.HouseholdDto
import net.marvinweber.simsli.data.remote.dto.ItemDto
import net.marvinweber.simsli.data.remote.dto.ItemStoreDto
import net.marvinweber.simsli.data.remote.dto.ListEntryDto
import net.marvinweber.simsli.data.remote.dto.StoreDto
import net.marvinweber.simsli.domain.model.ItemType
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

// PostgREST serializes timestamptz as ISO-8601 strings with a numeric offset
// (e.g. "2026-09-19T13:37:00.123456+00:00"). Outgoing timestamps use Instant.toString().

private fun String?.toInstantOrNull(): Instant? = this?.let {
    try {
        OffsetDateTime.parse(it).toInstant()
    } catch (e: DateTimeParseException) {
        Instant.parse(it)
    }
}

private fun String.toInstantOrEpoch(): Instant = toInstantOrNull() ?: Instant.EPOCH

private fun ItemType.toDtoName(): String = name

private fun String.toItemTypeOrDefault(): ItemType =
    ItemType.entries.firstOrNull { it.name == this } ?: ItemType.PERMANENT

fun HouseholdDto.toDb(): DbHousehold = DbHousehold(
    id = id,
    name = name,
    createdAt = createdAt.toInstantOrEpoch(),
    updatedAt = updatedAt.toInstantOrEpoch(),
    deletedAt = deletedAt.toInstantOrNull()
)

fun DbHousehold.toDto(): HouseholdDto = HouseholdDto(
    id = id,
    name = name,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    deletedAt = deletedAt?.toString()
)

fun StoreDto.toDb(): DbStore = DbStore(
    id = id,
    householdId = householdId,
    name = name,
    sortOrder = sortOrder.toFloat(),
    createdAt = createdAt.toInstantOrEpoch(),
    updatedAt = updatedAt.toInstantOrEpoch(),
    deletedAt = deletedAt.toInstantOrNull()
)

fun DbStore.toDto(): StoreDto = StoreDto(
    id = id,
    householdId = householdId,
    name = name,
    sortOrder = sortOrder.toDouble(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    deletedAt = deletedAt?.toString()
)

fun ItemDto.toDb(): DbItem = DbItem(
    id = id,
    householdId = householdId,
    name = name,
    notes = notes,
    type = type.toItemTypeOrDefault(),
    sortOrder = sortOrder.toFloat(),
    createdAt = createdAt.toInstantOrEpoch(),
    updatedAt = updatedAt.toInstantOrEpoch(),
    deletedAt = deletedAt.toInstantOrNull()
)

fun DbItem.toDto(): ItemDto = ItemDto(
    id = id,
    householdId = householdId,
    name = name,
    notes = notes,
    type = type.toDtoName(),
    sortOrder = sortOrder.toDouble(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    deletedAt = deletedAt?.toString()
)

fun ItemStoreDto.toDb(): DbItemStore = DbItemStore(
    itemId = itemId,
    storeId = storeId,
    createdAt = createdAt.toInstantOrEpoch()
)

fun DbItemStore.toDto(): ItemStoreDto = ItemStoreDto(
    itemId = itemId,
    storeId = storeId,
    createdAt = createdAt.toString()
)

fun ListEntryDto.toDb(): DbListEntry = DbListEntry(
    id = id,
    householdId = householdId,
    itemId = itemId,
    quantity = quantity,
    unit = unit,
    comment = comment,
    done = done,
    completedAt = completedAt.toInstantOrNull(),
    createdAt = createdAt.toInstantOrEpoch(),
    updatedAt = updatedAt.toInstantOrEpoch(),
    createdBy = createdBy
)

/** [fallbackCreatedBy] fills the server's `created_by` (uuid of an auth user) when unknown locally. */
fun DbListEntry.toDto(fallbackCreatedBy: String? = null): ListEntryDto = ListEntryDto(
    id = id,
    householdId = householdId,
    itemId = itemId,
    quantity = quantity,
    unit = unit,
    comment = comment,
    done = done,
    completedAt = completedAt?.toString(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    createdBy = createdBy ?: fallbackCreatedBy
)
