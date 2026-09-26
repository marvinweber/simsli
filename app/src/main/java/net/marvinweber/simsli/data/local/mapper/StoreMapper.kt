package net.marvinweber.simsli.data.local.mapper

import net.marvinweber.simsli.data.local.entity.DbStore
import net.marvinweber.simsli.domain.model.Store

fun DbStore.toDomain(): Store = Store(
    id = id,
    householdId = householdId,
    name = name,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Store.toDb(): DbStore = DbStore(
    id = id,
    householdId = householdId,
    name = name,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = null
)
