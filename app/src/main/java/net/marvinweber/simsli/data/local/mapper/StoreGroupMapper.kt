package net.marvinweber.simsli.data.local.mapper

import net.marvinweber.simsli.data.local.entity.DbStoreGroup
import net.marvinweber.simsli.domain.model.StoreGroup

fun DbStoreGroup.toDomain(): StoreGroup = StoreGroup(
    id = id,
    householdId = householdId,
    name = name,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun StoreGroup.toDb(): DbStoreGroup = DbStoreGroup(
    id = id,
    householdId = householdId,
    name = name,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = null
)
