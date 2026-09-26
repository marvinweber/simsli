package net.marvinweber.simsli.data.local.mapper

import net.marvinweber.simsli.data.local.entity.DbItemStore
import net.marvinweber.simsli.domain.model.ItemStore

fun DbItemStore.toDomain(): ItemStore = ItemStore(
    itemId = itemId,
    storeId = storeId,
    createdAt = createdAt
)

fun ItemStore.toDb(): DbItemStore = DbItemStore(
    itemId = itemId,
    storeId = storeId,
    createdAt = createdAt
)
