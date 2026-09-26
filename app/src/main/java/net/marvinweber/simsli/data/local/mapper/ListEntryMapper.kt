package net.marvinweber.simsli.data.local.mapper

import net.marvinweber.simsli.data.local.entity.DbListEntry
import net.marvinweber.simsli.domain.model.ListEntry

fun DbListEntry.toDomain(): ListEntry = ListEntry(
    id = id,
    householdId = householdId,
    itemId = itemId,
    quantity = quantity,
    unit = unit,
    comment = comment,
    done = done,
    completedAt = completedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    createdBy = createdBy
)

fun ListEntry.toDb(): DbListEntry = DbListEntry(
    id = id,
    householdId = householdId,
    itemId = itemId,
    quantity = quantity,
    unit = unit,
    comment = comment,
    done = done,
    completedAt = completedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    createdBy = createdBy
)
