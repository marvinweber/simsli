package net.marvinweber.simsli.data.local.mapper

import net.marvinweber.simsli.data.local.entity.DbItem
import net.marvinweber.simsli.domain.model.Item

fun DbItem.toDomain(): Item = Item(
    id = id,
    householdId = householdId,
    name = name,
    notes = notes,
    type = type,
    categoryId = categoryId,
    sortOrder = sortOrder,
    links = links,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Item.toDb(): DbItem = DbItem(
    id = id,
    householdId = householdId,
    name = name,
    notes = notes,
    type = type,
    categoryId = categoryId,
    sortOrder = sortOrder,
    links = links,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = null
)
